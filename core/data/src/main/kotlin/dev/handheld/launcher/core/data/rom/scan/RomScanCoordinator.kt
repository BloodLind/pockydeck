package dev.handheld.launcher.core.data.rom.scan

import dev.handheld.launcher.core.data.rom.repository.*
import dev.handheld.launcher.core.data.rom.source.RomSourceAccess
import dev.handheld.launcher.core.data.rom.source.shared.AndroidSharedRomDiscovery
import dev.handheld.launcher.core.data.rom.source.shared.SharedDiscoveryPolicy
import dev.handheld.launcher.core.domain.model.CatalogSourceId
import dev.handheld.launcher.core.domain.rom.scan.RomScanPlanner
import dev.handheld.launcher.core.domain.rom.scan.RomScanRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import dev.handheld.launcher.core.domain.rom.RomSourceStatus

data class RomScanState(val busy:Boolean=false,val sourceName:String?=null,val documentsSeen:Int=0,val error:String?=null)

class RomScanCoordinator(private val repository:RoomRomLibraryRepository,private val access:RomSourceAccess,scope:CoroutineScope,
    private val discovery:AndroidSharedRomDiscovery?=null) {
    private val requests=Channel<Unit>(Channel.CONFLATED)
    private val fullScanRequested=AtomicBoolean(false)
    private val mutex=Mutex()
    private val mutableState=MutableStateFlow(RomScanState())
    val state:StateFlow<RomScanState> = mutableState.asStateFlow()
    init { scope.launch { for(ignored in requests) reconcile(scanExistingSources=fullScanRequested.getAndSet(false)) } }
    fun refresh() { fullScanRequested.set(true); requests.trySend(Unit) }
    suspend fun reconcile(scanExistingSources:Boolean=true, continueInProcess:Boolean=true) = mutex.withLock {
        try { discovery?.discover() }
        catch(cancelled:CancellationException) { throw cancelled }
        catch(error:Exception) { mutableState.value=RomScanState(error=error.message?.take(240) ?: "Shared storage discovery could not finish; existing sources were kept.") }
        val sources=repository.allSources()
        for(registered in sources.filter { it.enabled && (scanExistingSources || it.status == RomSourceStatus.NOT_SCANNED) }) {
            currentCoroutineContext().ensureActive()
            val revision=repository.beginScan(registered.id) ?: continue
            val current=repository.findSource(registered.id) ?: continue
            val source=current.copy(excludedPhysicalRootKeys=SharedDiscoveryPolicy.exclusions(current,repository.allSources()))
            mutableState.value=RomScanState(true,source.name)
            try {
                val result=access.enumerate(source) { count -> mutableState.value=RomScanState(true,source.name,count) }
                val plan=withContext(Dispatchers.Default) { RomScanPlanner().plan(RomScanRequest(result.documents,source.name,source.defaultPlatformId,descriptorText=result.descriptorText)) }
                repository.commitScan(source,revision,result.documents,plan)
            } catch(cancelled:CancellationException) {
                withContext(NonCancellable) { repository.failScan(source.id,revision,"Scan interrupted. Existing games were kept.",false) }
                throw cancelled
            } catch(_:ScanSupersededException) { requests.trySend(Unit) }
            catch(error:Exception) {
                val message=error.message?.take(240) ?: "Could not read this ROM folder. Try again."
                repository.failScan(source.id,revision,message,!access.isAvailable(source))
                mutableState.value=RomScanState(error=message)
            } finally { mutableState.update { it.copy(busy=false,sourceName=null) } }
        }
        if (continueInProcess && discovery?.state?.value?.hasMore == true) requests.trySend(Unit)
    }
}
