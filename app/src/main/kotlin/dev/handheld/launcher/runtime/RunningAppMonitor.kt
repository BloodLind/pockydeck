package dev.handheld.launcher.runtime

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.IBinder
import android.os.Process
import dev.handheld.launcher.core.data.rom.emulator.AndroidEmulatorResolver
import dev.handheld.launcher.core.data.rom.emulator.EmulatorLaunchSupport
import dev.handheld.launcher.core.data.rom.repository.RoomRomLibraryRepository
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.model.LibraryItem
import dev.handheld.launcher.core.domain.repository.CatalogRepository
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import rikka.shizuku.Shizuku

enum class RunningAccess { OFF, INSTALL, START, ALLOW, CONNECTING, READY, UNAVAILABLE }
data class RunningAppState(
    val access: RunningAccess = RunningAccess.OFF,
    val labels: Map<ItemId, String> = emptyMap(),
) {
    val enabled: Boolean get() = access != RunningAccess.OFF
    val summary: String get() = when (access) {
        RunningAccess.OFF -> "Show verified app and emulator status on Home"
        RunningAccess.INSTALL -> "Install Shizuku to enable live process readings"
        RunningAccess.START -> "Start Shizuku; it needs restarting after a device reboot"
        RunningAccess.ALLOW -> "Allow this launcher in Shizuku"
        RunningAccess.CONNECTING -> "Connecting to Shizuku…"
        RunningAccess.READY -> "Live status on Home · An emulator may be paused or in its menu"
        RunningAccess.UNAVAILABLE -> "Process reading unavailable · Open Shizuku to reconnect"
    }
}

private data class ProcessTarget(val packageName: String, val label: String)

// Architecture belongs in emulator selection; compact card status keeps the emulator name readable.
private fun emulatorRunningLabel(name: String): String =
    "${name.removeSuffix(" (64-bit)").removeSuffix(" (32-bit)")} running"

/** Foreground-only presence sampling. Persisted ROM handlers are routing history, never liveness. */
class RunningAppMonitor(
    context: Context,
    private val catalog: CatalogRepository,
    private val roms: RoomRomLibraryRepository,
    private val scope: CoroutineScope,
) {
    private val context = context.applicationContext
    private val preferences = this.context.getSharedPreferences("running-indicators", Context.MODE_PRIVATE)
    private val enabled = MutableStateFlow(preferences.getBoolean("enabled", false))
    private val historyWrites = Mutex()
    private val handlers = MutableStateFlow(preferences.all.mapNotNull { (key, value) ->
        if (!key.startsWith("rom:") || value !is String) null else {
            val pieces = value.split('\n', limit = 2)
            if (pieces.size == 2 && validProcessPackage(pieces[0])) ItemId(key.removePrefix("rom:")) to ProcessTarget(pieces[0], pieces[1]) else null
        }
    }.toMap())
    private val resolver = AndroidEmulatorResolver(this.context)

    fun setEnabled(value: Boolean) {
        enabled.value = value
        preferences.edit().putBoolean("enabled", value).apply()
    }

    fun recordEmulator(itemId: ItemId, packageName: String, displayName: String) {
        if (!validProcessPackage(packageName)) return
        val next = LinkedHashMap(handlers.value)
        next.remove(itemId)
        next[itemId] = ProcessTarget(packageName, displayName.replace('\n', ' ').take(80))
        while (next.size > 256) next.remove(next.keys.first())
        handlers.value = next
        scope.launch(Dispatchers.IO) {
            historyWrites.withLock {
                val edit = preferences.edit()
                preferences.all.keys.filter { it.startsWith("rom:") }.forEach(edit::remove)
                handlers.value.forEach { (id, target) ->
                    edit.putString("rom:${id.value}", "${target.packageName}\n${target.label}")
                }
                edit.apply()
            }
        }
    }

    /** Only the explicit Settings action can request permission or open the helper/download page. */
    fun requestSetup(): Boolean = runCatching {
        when {
            Shizuku.pingBinder() && Shizuku.getVersion() >= 13 &&
                Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED && !Shizuku.shouldShowRequestPermissionRationale() ->
                Shizuku.requestPermission(PERMISSION_REQUEST)
            else -> {
                val intent = context.packageManager.getLaunchIntentForPackage(MANAGER_PACKAGE)
                    ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }.isSuccess

    val state: Flow<RunningAppState> = callbackFlow {
        val closed = AtomicBoolean(false)
        val remote = AtomicReference<IProcessStatusService?>(null)
        val targets = AtomicReference<Map<ItemId, ProcessTarget>>(emptyMap())
        var bound = false
        var bindStartedAt = 0L
        val userId = Process.myUid() / 100_000
        val args = Shizuku.UserServiceArgs(ComponentName(context, ProcessStatusService::class.java))
            .daemon(false).processNameSuffix("running-status-$userId").tag("running-status-$userId").version(1)
        fun access(): RunningAccess = when {
            !enabled.value -> RunningAccess.OFF
            !Shizuku.pingBinder() -> if (context.packageManager.getLaunchIntentForPackage(MANAGER_PACKAGE) == null) RunningAccess.INSTALL else RunningAccess.START
            runCatching { Shizuku.getVersion() < 13 }.getOrDefault(true) -> RunningAccess.UNAVAILABLE
            runCatching { Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED }.getOrDefault(true) -> RunningAccess.ALLOW
            remote.get() == null -> RunningAccess.CONNECTING
            else -> RunningAccess.READY
        }
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (closed.get()) return
                if (enabled.value && binder?.pingBinder() == true) remote.set(IProcessStatusService.Stub.asInterface(binder))
                trySend(RunningAppState(access()))
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                if (closed.get()) return
                remote.set(null)
                bound = false
                trySend(RunningAppState(access()))
            }
        }
        fun disconnect() {
            remote.set(null)
            if (bound) runCatching { Shizuku.unbindUserService(args, connection, true) }
            bound = false
        }
        fun reconnect() {
            if (closed.get()) return
            val current = access()
            if (current == RunningAccess.CONNECTING && !bound) {
                try {
                    bound = true
                    bindStartedAt = android.os.SystemClock.elapsedRealtime()
                    Shizuku.bindUserService(args, connection)
                }
                catch (_: RuntimeException) { bound = false; trySend(RunningAppState(RunningAccess.UNAVAILABLE)); return }
            } else if (current !in setOf(RunningAccess.CONNECTING, RunningAccess.READY)) disconnect()
            trySend(RunningAppState(access()))
        }
        val received = Shizuku.OnBinderReceivedListener { reconnect() }
        val dead = Shizuku.OnBinderDeadListener { if (!closed.get()) { disconnect(); trySend(RunningAppState(access())) } }
        val permission = Shizuku.OnRequestPermissionResultListener { code, _ -> if (code == PERMISSION_REQUEST) reconnect() }
        Shizuku.addBinderReceivedListenerSticky(received)
        Shizuku.addBinderDeadListener(dead)
        Shizuku.addRequestPermissionResultListener(permission)
        val enabledJob = launch { enabled.collectLatest { reconnect() } }
        val targetJob = launch(Dispatchers.IO) {
            enabled.collectLatest { active ->
                if (!active) { targets.set(emptyMap()); return@collectLatest }
                combine(catalog.snapshot, roms.consoleEmulatorDefaults, roms.itemEmulatorOverrides, handlers) { snapshot, defaults, overrides, history ->
                    TargetInput(snapshot.items, defaults, overrides, history)
                }.distinctUntilChanged().collectLatest { input ->
                    val result = linkedMapOf<ItemId, ProcessTarget>()
                    input.items.filterIsInstance<LibraryItem.AndroidApp>().forEach {
                        result[it.id] = ProcessTarget(it.componentId.packageName, "Running")
                    }
                    val platforms = input.items.filterIsInstance<LibraryItem.RomGame>().mapNotNull { it.platformId }.distinct()
                    val installed = platforms.associateWith { platform ->
                        resolver.installedForPlatform(platform).filter { it.launchSupport != EmulatorLaunchSupport.UNSUPPORTED }
                    }
                    input.items.filterIsInstance<LibraryItem.RomGame>().forEach { item ->
                        val last = input.history[item.id]
                        if (last != null) result[item.id] = last.copy(label = emulatorRunningLabel(last.label))
                        else {
                            val candidates = installed[item.platformId].orEmpty()
                            val preferred = input.overrides[item.id] ?: input.defaults[item.platformId]
                            val emulator = if (preferred == null) candidates.singleOrNull() else candidates.firstOrNull { it.id == preferred }
                            if (emulator != null) result[item.id] = ProcessTarget(emulator.packageName, emulatorRunningLabel(emulator.displayName))
                        }
                    }
                    targets.set(result)
                }
            }
        }
        val reconnectJob = launch {
            while (isActive) {
                delay(5_000)
                if (access() == RunningAccess.CONNECTING) {
                    if (bound && android.os.SystemClock.elapsedRealtime() - bindStartedAt >= 5_000) {
                        disconnect()
                        trySend(RunningAppState(RunningAccess.UNAVAILABLE))
                        delay(25_000)
                    }
                    reconnect()
                }
            }
        }
        val polling = launch(Dispatchers.IO) {
            while (isActive) {
                val current = access()
                if (current == RunningAccess.READY) {
                    val service = remote.get()
                    val currentTargets = targets.get()
                    try {
                        val packages = currentTargets.values.map { it.packageName }.distinct().take(4096)
                        val running = service?.runningPackages(packages.toTypedArray(), Process.myUid() / 100_000)?.toSet()
                        // A disconnect/revocation during the read must not republish old presence.
                        if (!closed.get() && running != null && remote.get() === service && access() == RunningAccess.READY) {
                            trySend(RunningAppState(RunningAccess.READY,
                                currentTargets.filterValues { it.packageName in running }.mapValues { it.value.label }))
                        } else trySend(RunningAppState(access()))
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) {
                        if (!closed.get()) {
                            val now = access()
                            trySend(RunningAppState(if (now == RunningAccess.READY && remote.get() === service)
                                RunningAccess.UNAVAILABLE else now))
                        }
                    }
                } else trySend(RunningAppState(current))
                delay(3_000)
            }
        }
        awaitClose {
            closed.set(true)
            enabledJob.cancel(); targetJob.cancel(); polling.cancel(); reconnectJob.cancel()
            Shizuku.removeBinderReceivedListener(received)
            Shizuku.removeBinderDeadListener(dead)
            Shizuku.removeRequestPermissionResultListener(permission)
            disconnect()
        }
    }.buffer(Channel.CONFLATED)

    private data class TargetInput(val items: List<LibraryItem>, val defaults: Map<String, String>,
        val overrides: Map<ItemId, String>, val history: Map<ItemId, ProcessTarget>)

    companion object {
        const val MANAGER_PACKAGE = "moe.shizuku.privileged.api"
        private const val PERMISSION_REQUEST = 901
    }
}
