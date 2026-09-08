package dev.handheld.launcher.rom

import android.net.Uri
import dev.handheld.launcher.core.data.rom.archive.PreparedRomCache
import dev.handheld.launcher.core.data.rom.emulator.*
import dev.handheld.launcher.core.data.rom.repository.RoomRomLibraryRepository
import dev.handheld.launcher.core.data.rom.scan.RomScanCoordinator
import dev.handheld.launcher.core.data.rom.source.RoutingRomSourceAccess
import dev.handheld.launcher.core.data.rom.source.shared.SharedStoragePaths
import dev.handheld.launcher.core.data.rom.source.shared.AndroidSharedRomDiscovery
import dev.handheld.launcher.core.domain.model.*
import dev.handheld.launcher.core.domain.repository.LaunchDispatcher
import dev.handheld.launcher.core.domain.rom.*
import dev.handheld.launcher.core.domain.rom.scan.*
import dev.handheld.launcher.feature.settings.emulators.*
import dev.handheld.launcher.feature.settings.sources.RomSourcesScreenState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import java.io.IOException

/** One ephemeral interaction at a time; only acknowledged settings choices are durable. */
class RomFeatureController(
    private val repository:RoomRomLibraryRepository,
    private val access:RoutingRomSourceAccess,
    private val scanner:RomScanCoordinator,
    private val resolver:AndroidEmulatorResolver,
    private val launcher:AndroidRomLauncher,
    private val cache:PreparedRomCache,
    private val scope:CoroutineScope,
    private val sharedStorage:SharedStoragePaths,
    private val discovery:AndroidSharedRomDiscovery,
    private val onGameDispatched: (itemId: ItemId, packageName: String, emulatorLabel: String) -> Unit = { _, _, _ -> },
) : LaunchDispatcher {
    private val interaction=Mutex()
    private val decisionGuard=Any()
    private var pendingDecision:CompletableDeferred<Choice?>?=null
    private val mutableChoice=MutableStateFlow<RomChoiceDialogState?>(null)
    val choice:StateFlow<RomChoiceDialogState?> = mutableChoice.asStateFlow()
    private val mutableProgress=MutableStateFlow<String?>(null)
    val progress:StateFlow<String?> = mutableProgress.asStateFlow()
    private val mutableMessage=MutableStateFlow<String?>(null)
    val message:StateFlow<String?> = mutableMessage.asStateFlow()
    private val refresh=MutableStateFlow(0)
    private val cacheUsage=MutableStateFlow(0L)
    @Volatile private var preparation:Job?=null

    private val sourceList=repository.sources.stateIn(scope,SharingStarted.Eagerly,emptyList())
    private val entryList=repository.entries.stateIn(scope,SharingStarted.Eagerly,emptyList())
    private val defaults=repository.consoleEmulatorDefaults.stateIn(scope,SharingStarted.Eagerly,emptyMap())
    private val itemDefaults=repository.itemEmulatorOverrides.stateIn(scope,SharingStarted.Eagerly,emptyMap())
    private val cores=repository.consoleCores.stateIn(scope,SharingStarted.Eagerly,emptyMap())
    private val limit=repository.cacheLimitBytes.stateIn(scope,SharingStarted.Eagerly,RoomRomLibraryRepository.DEFAULT_CACHE_LIMIT)
    private val storageAccess=MutableStateFlow(sharedStorage.hasAccess())
    private val discoverNewFolders=repository.sharedDiscoveryEnabled.stateIn(scope,SharingStarted.Eagerly,true)

    private val sourceStatus = combine(sourceList,scanner.state,limit,cacheUsage,progress) { sources,scan,budget,usage,preparing ->
        RomSourcesScreenState(sources,scan.busy,scan.error,"${budget/RoomRomLibraryRepository.GIB} GiB",
            "${usage/(1024*1024)} MiB used",preparing)
    }
    val sourcesState:StateFlow<RomSourcesScreenState> = combine(sourceStatus,storageAccess,discoverNewFolders,discovery.state) { state,granted,enabled,discovery ->
        state.copy(storageAccessGranted=granted,automaticDiscoveryEnabled=enabled,
            discoveryBusy=discovery.busy,discoveryFoldersVisited=discovery.foldersVisited,
            busy=state.busy || discovery.busy,message=state.message ?: discovery.error)
    }.stateIn(scope,SharingStarted.Eagerly,RomSourcesScreenState())

    private data class ConsoleInputs(val entries:List<RomEntry>,val sources:List<RomSource>,val defaults:Map<String,String>,val cores:Map<String,String>)
    @OptIn(ExperimentalCoroutinesApi::class)
    val emulatorsState:StateFlow<EmulatorSettingsScreenState> = combine(entryList,sourceList,defaults,cores) { entries,sources,defaults,cores -> ConsoleInputs(entries,sources,defaults,cores) }
        .combine(refresh) { input,_ -> input }.mapLatest { input ->
            val enabled=input.sources.filter { it.enabled && it.status!=RomSourceStatus.UNAVAILABLE }.map { it.id }.toSet()
            val consoles=input.entries.filter { it.present && it.sourceId in enabled && it.platformId!=null }.groupBy { it.platformId!! }
            EmulatorSettingsScreenState(consoles.map { (platform,entries) ->
                val installed=resolver.installedForPlatform(platform)
                val candidates=installed.filter { it.launchSupport!=EmulatorLaunchSupport.UNSUPPORTED }
                val preferred=input.defaults[platform]
                val selected=installed.find { it.id==preferred }
                ConsoleEmulatorRow(platform,consoleName(platform),entries.size,
                    if(preferred!=null) selected?.displayName ?: "Saved app unavailable" else when(candidates.size) { 0 -> "No compatible app"; 1 -> "Automatic · ${candidates.single().displayName}"; else -> "Ask when opening" },
                    candidates.size,
                    if(installed.any { it.cores.isNotEmpty() }) EmulatorRegistry.coresForPlatform(platform).find { it.id==input.cores[platform] }?.displayName ?: "Choose installed RetroArch core" else null)
            }.sortedBy { it.consoleName })
        }.stateIn(scope,SharingStarted.Eagerly,EmulatorSettingsScreenState())

    init { scope.launch { cacheUsage.value=cache.usageBytes() } }
    fun refreshEmulators() { refresh.update { it+1 } }
    fun refreshStorageAccess() { storageAccess.value=sharedStorage.hasAccess() }
    fun rescan() { scanner.refresh() }
    fun setAutomaticDiscovery(enabled:Boolean)=action {
        repository.setSharedDiscoveryEnabled(enabled)
        if(enabled) scanner.refresh()
    }
    fun restoreSource(id:CatalogSourceId)=action { repository.restoreSource(id); scanner.refresh() }
    fun clearMessage() { mutableMessage.value=null }
    fun cancelPreparation() { preparation?.cancel(); dismissChoice() }

    fun selectChoice(id:String) = synchronized(decisionGuard) {
        val current=mutableChoice.value ?: return@synchronized
        if(current.options.none { it.id==id && it.enabled }) return@synchronized
        pendingDecision?.complete(Choice(id,current.rememberSelection))
    }
    fun setChoiceRemember(value:Boolean) { mutableChoice.update { it?.copy(rememberSelection=value) } }
    fun dismissChoice() = synchronized(decisionGuard) { pendingDecision?.complete(null); Unit }

    fun onTreeSelected(uri:Uri,grantedFlags:Int) = action {
        val selected=access.acceptTree(uri,grantedFlags,repository.sources.first())
        val id=repository.addSource(selected.uri,selected.documentId,selected.name)
        val source=repository.findSource(id)
        if(source?.defaultPlatformId==null && RomPlatforms.matchingFolder(selected.name)==null) {
            val platform=choosePlatform("Console for ${selected.name}",null,true)
            if(platform!=null && platform!="automatic") repository.setSourcePlatform(id,platform)
        }
        scanner.refresh()
    }
    fun removeSource(id:CatalogSourceId)=action {
        val source=repository.findSource(id) ?: return@action
        val answer=ask(RomChoiceDialogState("Remove ${source.name}?",listOf(RomChoiceOption("remove","Remove folder")),
            "ROM files stay on storage. Favorites and history are retained. ${if(source.automaticallyDiscovered) "Automatic discovery will skip this folder until you add it again." else "Selecting this same folder again restores its entries."}"))
        if(answer?.id=="remove") repository.removeSource(id)
    }
    fun chooseSourcePlatform(id:CatalogSourceId)=action {
        val source=repository.findSource(id) ?: return@action
        val selected=choosePlatform("Folder console",source.defaultPlatformId,true) ?: return@action
        repository.setSourcePlatform(id,selected.takeUnless { it=="automatic" }); scanner.refresh()
    }
    fun chooseItemPlatform(id:ItemId)=action {
        val entry=repository.findEntry(id) ?: return@action
        val selected=choosePlatform("Console for ${entry.title}",entry.platformId,true) ?: return@action
        repository.setItemPlatform(id,selected.takeUnless { it=="automatic" }); scanner.refresh()
    }
    fun identifySourceItems(id:CatalogSourceId)=action {
        val source=repository.findSource(id)?.takeIf { it.enabled } ?: return@action
        val choices=RomIdentificationChoices.forSource(repository.entries.first(),id)
        if(choices.total==0) { mutableMessage.value="All games in ${source.name} have a console."; return@action }
        val shown=choices.entries
        val selected=ask(RomChoiceDialogState("Identify games in ${source.name}",shown.map { RomChoiceOption(it.itemId.value,it.title,it.relativePath) },
            if(choices.total>shown.size) "Showing ${shown.size} of ${choices.total} unidentified games. Assigned games leave this list; reopen it to continue with the next games."
            else "Choose a game and its console. Original files, favorites and history are kept.")) ?: return@action
        val entry=shown.firstOrNull { it.itemId.value==selected.id } ?: return@action
        val platform=choosePlatform("Console for ${entry.title}",null,false) ?: return@action
        repository.setItemPlatform(entry.itemId,platform)
        scanner.refresh()
    }
    fun chooseConsoleEmulator(platform:String)=action {
        val installed=resolver.installedForPlatform(platform)
        val selected=ask(emulatorDialog(platform,installed,defaults.value[platform],includeAutomatic=true)) ?: return@action
        repository.setConsoleEmulator(platform,selected.id.takeUnless { it=="automatic" })
    }
    fun chooseItemEmulator(id:ItemId)=action {
        val entry=repository.findEntry(id) ?: return@action
        val platform=entry.platformId ?: choosePlatform("Choose this game's console",null,false)?.also { repository.setItemPlatform(id,it) } ?: return@action
        val installed=resolver.installedForPlatform(platform)
        val selected=ask(emulatorDialog(platform,installed,itemDefaults.value[id],includeAutomatic=true).copy(title="Emulator for ${entry.title}")) ?: return@action
        repository.setItemEmulator(id,selected.id.takeUnless { it=="automatic" })
    }
    fun chooseCore(platform:String)=action { selectCore(platform) }
    fun chooseCacheLimit()=action {
        val selected=ask(RomChoiceDialogState("Game cache limit",RoomRomLibraryRepository.CACHE_LIMITS.map { RomChoiceOption(it.toString(),"${it/RoomRomLibraryRepository.GIB} GiB") },
            "Includes compressed working files and extracted copies. Reserved game copies are never removed automatically.",limit.value.toString())) ?: return@action
        repository.setCacheLimit(selected.id.toLong())
    }
    fun clearCache()=action {
        val selected=ask(RomChoiceDialogState("Clear game cache",listOf(
            RomChoiceOption("unused","Clear unused copies","Retain copies reserved by game launches"),
            RomChoiceOption("all","Clear all copies — emulators closed","Original ROMs and archives are kept")),
            "Close emulators before clearing all copies. An emulator may still need the extracted files after you return Home.")) ?: return@action
        cache.clear(selected.id=="all"); cacheUsage.value=cache.usageBytes()
    }

    override suspend fun dispatch(request:LaunchRequest):LaunchAcknowledgement {
        if(!interaction.tryLock()) return LaunchAcknowledgement.Failed(request.operationId,LaunchFailureReason.CANCELLED)
        mutableMessage.value=null
        try {
            var entry=repository.findEntry(request.itemId) ?: throw IOException("This ROM is no longer indexed. Rescan its folder.")
            val source=repository.findSource(entry.sourceId)?.takeIf { it.enabled } ?: throw IOException("This ROM folder is unavailable. Select it again in Settings.")
            if(!entry.present) throw IOException("This ROM is no longer in its folder. Rescan or restore the file.")
            if(entry.requiresRepair) throw IOException(entry.issue ?: "This game is missing required files. Repair the set and rescan.")
            val platform=entry.platformId ?: choosePlatform("Choose the console for ${entry.title}",null,false)?.also {
                repository.setItemPlatform(entry.itemId,it)
            } ?: throw UserCancelled()
            entry=entry.copy(platformId=platform)
            var input=RomLaunchInput(platform,entry.format,access.documentUri(source,entry.documentId),treeUri=source.treeUri,relativePath=entry.relativePath,
                companionUris=entry.companionDocumentIds.map { access.documentUri(source,it) },coreId=repository.consoleCores.first()[platform])
            val archive=RomArchiveSelection.isArchive(entry.format)
            val preserveArchive=RomArchiveSelection.preserveNativeContainer(platform,entry.format)
            val installed=if(archive && !preserveArchive) resolver.installedForPlatform(platform)
                else resolver.resolve(input).let { it.candidates+it.detectedUnsupported }
            val candidates=installed.filter { it.launchSupport!=EmulatorLaunchSupport.UNSUPPORTED }
            if(candidates.isEmpty()) throw IOException(installed.firstOrNull()?.reason ?: "No compatible emulator is installed for ${consoleName(platform)}. Choose or install an emulator, then open this game again.")
            val itemPreference=repository.itemEmulatorOverrides.first()[entry.itemId]
            val preferred=itemPreference ?: repository.consoleEmulatorDefaults.first()[platform]
            var selected=if(preferred!=null) candidates.find { it.id==preferred } else candidates.singleOrNull()
            if(selected==null) {
                val answer=ask(emulatorDialog(platform,installed,preferred,false).copy(
                    message=if(preferred!=null) "The saved app cannot open this game. Choose an available app." else "Choose the app to open this game.",
                    rememberLabel=if(itemPreference!=null) "Use this app for this game" else "Use this app for ${consoleName(platform)}")) ?: throw UserCancelled()
                selected=candidates.first { it.id==answer.id }
                if(answer.remember) {
                    if(itemPreference!=null) repository.setItemEmulator(entry.itemId,selected.id)
                    else repository.setConsoleEmulator(platform,selected.id)
                }
            }
            if(selected.cores.isNotEmpty() && input.coreId==null) input=input.copy(coreId=selectCore(platform) ?: throw UserCancelled())
            if(entry.format=="rar") throw IOException("RAR archives are detected but cannot be extracted by this build. Unpack this archive or use ZIP/7Z, then rescan the folder.")
            var preparedKey:String?=null
            // Inspect generic containers before selecting a game; native arcade/DOS sets stay intact.
            if(RomArchiveSelection.requiresPreparation(platform,entry.format)) {
                input=coroutineScope {
                    val task=async {
                        val prepared=cache.prepare(input.documentUri,entry.format,repository.cacheLimitBytes.first(),{ mutableProgress.value=it },entry.relativePath.substringAfterLast('/'))
                        preparedKey=prepared.key
                        val plan=withContext(Dispatchers.Default) { RomScanPlanner().plan(RomScanRequest(prepared.documents,
                            rootDisplayName=consoleName(platform),assignedPlatformId=platform,descriptorText=prepared.descriptorText)) }
                        mutableProgress.value=null
                        val games=RomArchiveSelection.selectableGames(plan)
                        val game=if(games.size==1) games.single() else {
                            val choice=ask(RomChoiceDialogState("Choose a game from the archive",games.map { RomChoiceOption(it.documentId,it.title,it.relativePath) })) ?: throw UserCancelled()
                            games.first { it.documentId==choice.id }
                        }
                        RomLaunchInput(platform,game.format,prepared.uri(game.documentId),treeUri=prepared.treeUri,relativePath=game.relativePath,
                            companionUris=game.companionDocumentIds.map(prepared::uri),coreId=input.coreId)
                    }
                    preparation=task
                    try { task.await() } catch(cancelled:CancellationException) { throw UserCancelled() }
                    finally { preparation=null; mutableProgress.value=null; cacheUsage.value=cache.usageBytes() }
                }
            }
            // Revalidate after dialogs/preparation; never switch a stored choice silently.
            if(resolver.resolve(input).candidates.none { it.id==selected.id }) throw IOException("${selected.displayName} cannot open this game's format or folder. Choose another emulator in this game's details.")
            val newlyReserved=preparedKey?.let { cache.reserveForLaunch(it) } ?: false
            var dispatched=false
            val result=try { launcher.dispatch(input,selected.id).also { dispatched=it===RomDispatchResult.Started } }
                finally { if(!dispatched && preparedKey!=null) withContext(NonCancellable) { cache.releaseFailedReservation(preparedKey!!,newlyReserved) } }
            return when(result) {
                RomDispatchResult.Started -> {
                    runCatching { onGameDispatched(request.itemId,selected.packageName,selected.displayName) }
                    LaunchAcknowledgement.Dispatched(request.operationId)
                }
                else -> { mutableMessage.value=result.message(); LaunchAcknowledgement.Failed(request.operationId,LaunchFailureReason.DISPATCH_FAILED) }
            }
        } catch(_:UserCancelled) { return LaunchAcknowledgement.Failed(request.operationId,LaunchFailureReason.CANCELLED) }
        catch(cancelled:CancellationException) { throw cancelled }
        catch(error:Exception) {
            mutableMessage.value=error.message ?: "This ROM could not be opened. Your library order was preserved."
            return LaunchAcknowledgement.Failed(request.operationId,LaunchFailureReason.DISPATCH_FAILED)
        } finally { mutableProgress.value=null; interaction.unlock() }
    }

    private fun action(block:suspend ()->Unit) { scope.launch {
        if(!interaction.tryLock()) return@launch
        try { mutableMessage.value=null; block() }
        catch(cancelled:CancellationException) { throw cancelled }
        catch(error:Exception) { mutableMessage.value=error.message ?: "Could not update ROM settings. Try again." }
        finally { interaction.unlock() }
    } }
    private suspend fun ask(dialog:RomChoiceDialogState):Choice? {
        val deferred=CompletableDeferred<Choice?>()
        synchronized(decisionGuard) { check(pendingDecision==null); pendingDecision=deferred; mutableChoice.value=dialog }
        try { return deferred.await() }
        finally { synchronized(decisionGuard) { if(pendingDecision===deferred) { pendingDecision=null; mutableChoice.value=null } } }
    }
    private suspend fun choosePlatform(title:String,current:String?,automatic:Boolean):String? = ask(RomChoiceDialogState(title,
        (if(automatic) listOf(RomChoiceOption("automatic","Detect from folders and formats")) else emptyList())+
            RomPlatforms.all.sortedBy { it.displayName }.map { RomChoiceOption(it.id,it.displayName) },
        "Shared formats such as ISO, CHD and BIN need a console folder or an explicit selection.",current))?.id
    private suspend fun selectCore(platform:String):String? {
        val options=EmulatorRegistry.coresForPlatform(platform)
        val selected=ask(RomChoiceDialogState("RetroArch core for ${consoleName(platform)}",options.map { RomChoiceOption(it.id,it.displayName) },
            "Choose a core you have installed in RetroArch. Android does not let this launcher inspect RetroArch's private core files.",cores.value[platform])) ?: return null
        repository.setConsoleCore(platform,selected.id); return selected.id
    }
    private fun emulatorDialog(platform:String,installed:List<InstalledEmulator>,selected:String?,includeAutomatic:Boolean)=RomChoiceDialogState(
        "Emulator for ${consoleName(platform)}",(if(includeAutomatic) listOf(RomChoiceOption("automatic","Automatic / ask when several apps match")) else emptyList())+
            installed.map { RomChoiceOption(it.id,it.displayName,it.reason ?: it.versionName,it.launchSupport!=EmulatorLaunchSupport.UNSUPPORTED) },
        if(installed.isEmpty()) "No supported emulator is installed for this console." else null,selected)
    private data class Choice(val id:String,val remember:Boolean)
    private class UserCancelled:Exception()
    companion object {
        private fun consoleName(id:String)=RomPlatforms.byId(id)?.displayName ?: id
    }
}

private fun RomDispatchResult.message():String = when(this) {
    RomDispatchResult.Started -> ""
    is RomDispatchResult.Unavailable -> reason
    is RomDispatchResult.Unreadable -> reason
    is RomDispatchResult.Unsupported -> reason
    is RomDispatchResult.Rejected -> reason
    is RomDispatchResult.Failed -> reason
}
