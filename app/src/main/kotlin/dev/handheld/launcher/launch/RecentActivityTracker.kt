package dev.handheld.launcher.launch

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import dev.handheld.launcher.core.domain.model.ItemId
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/** App-private, bounded recency history. Usage access is optional and is never requested here. */
class RecentActivityTracker(context: Context, private val scope: CoroutineScope) {
    private val context = context.applicationContext
    private val preferences by lazy { this.context.getSharedPreferences("recent_activity_v1", Context.MODE_PRIVATE) }
    private val mutex = Mutex()
    private val refreshPending = AtomicBoolean(false)
    private var loaded = false
    private var history = RecentActivityHistory()
    private var observedApps: Map<String, Long> = emptyMap()
    private var usageAccessGranted = false
    private var polling: Job? = null
    private val mutableState = MutableStateFlow(RecentActivitySnapshot())
    val state: StateFlow<RecentActivitySnapshot> = mutableState.asStateFlow()

    init {
        refresh()
    }

    @Synchronized
    fun start() {
        if (polling?.isActive == true) return
        refresh()
        polling = scope.launch {
            while (isActive) {
                delay(60_000L)
                refresh()
            }
        }
    }

    @Synchronized
    fun stop() {
        polling?.cancel()
        polling = null
    }

    fun recordApp(packageName: String) {
        if (!validPackage(packageName)) return
        val dispatchedAt = System.currentTimeMillis()
        scope.launch(Dispatchers.IO) { mutex.withLock {
            val now = System.currentTimeMillis()
            load(now)
            history = RecentActivityPolicy.recordApp(history, packageName, dispatchedAt, now)
            persist()
            publish(now)
        } }
    }

    fun recordRom(itemId: ItemId, packageName: String, emulatorLabel: String) {
        if (!validPackage(packageName) || emulatorLabel.isBlank() || itemId.value.length > 4096) return
        val dispatchedAt = System.currentTimeMillis()
        scope.launch(Dispatchers.IO) { mutex.withLock {
            val now = System.currentTimeMillis()
            load(now)
            history = RecentActivityPolicy.recordRom(history,
                RecentRomActivity(itemId, packageName, emulatorLabel.trim().take(80), dispatchedAt), now)
            persist()
            publish(now)
        } }
    }

    fun refresh() {
        if (!refreshPending.compareAndSet(false, true)) return
        scope.launch(Dispatchers.IO) {
            try { mutex.withLock {
                val now = System.currentTimeMillis()
                load(now)
                val trimmed = RecentActivityPolicy.prune(history, now)
                if (trimmed != history) { history = trimmed; persist() }
                usageAccessGranted = hasUsageAccess()
                observedApps = if (usageAccessGranted) queryRecentApps(now) else emptyMap()
                // Permission can change while Android serves the query.
                if (!hasUsageAccess()) { usageAccessGranted = false; observedApps = emptyMap() }
                publish(System.currentTimeMillis())
            } } finally { refreshPending.set(false) }
        }
    }

    private fun hasUsageAccess(): Boolean = try {
        context.getSystemService(AppOpsManager::class.java)?.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
        ) == AppOpsManager.MODE_ALLOWED
    } catch (_: RuntimeException) { false }

    private suspend fun queryRecentApps(now: Long): Map<String, Long> {
        return try {
            val manager = context.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
            val events = manager.queryEvents((now - RecentActivityPolicy.WINDOW_MILLIS).coerceAtLeast(0L), now)
                ?: return emptyMap()
            val result = mutableMapOf<String, Long>()
            val event = UsageEvents.Event()
            var count = 0
            while (events.hasNextEvent() && count++ < 20_000) {
                if (count % 64 == 0) currentCoroutineContext().ensureActive()
                if (!events.getNextEvent(event)) break
                val name = event.packageName ?: continue
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED && validPackage(name) &&
                    name != context.packageName && RecentActivityPolicy.isRecent(event.timeStamp, now)) {
                    result[name] = maxOf(result[name] ?: 0L, event.timeStamp)
                    if (result.size > RecentActivityPolicy.MAX_APPS) {
                        result.minByOrNull { it.value }?.key?.let(result::remove)
                    }
                }
            }
            result
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: SecurityException) { usageAccessGranted = false; emptyMap() }
        catch (_: RuntimeException) { emptyMap() }
    }

    private fun publish(now: Long) {
        mutableState.value = RecentActivityPolicy.snapshot(history, observedApps, usageAccessGranted, now)
    }

    private fun load(now: Long) {
        if (loaded) return
        loaded = true
        val encoded = runCatching { preferences.getString("history", null) }.getOrNull()
            ?.takeIf { it.length <= 262_144 } ?: return
        history = runCatching {
            val root = JSONObject(encoded)
            if (root.optInt("version") != 1) return@runCatching RecentActivityHistory()
            val apps = buildMap {
                val rows = root.optJSONArray("apps") ?: JSONArray()
                for (index in 0 until minOf(rows.length(), RecentActivityPolicy.MAX_APPS)) {
                    val row = rows.optJSONObject(index) ?: continue
                    val name = row.optString("package")
                    if (validPackage(name)) put(name, row.optLong("at"))
                }
            }
            val roms = buildMap {
                val rows = root.optJSONArray("roms") ?: JSONArray()
                for (index in 0 until minOf(rows.length(), RecentActivityPolicy.MAX_EMULATORS)) {
                    val row = rows.optJSONObject(index) ?: continue
                    val name = row.optString("package")
                    val id = row.optString("item")
                    val label = row.optString("label").trim().take(80)
                    if (validPackage(name) && id.isNotBlank() && id.length <= 4096 && label.isNotBlank())
                        put(name, RecentRomActivity(ItemId(id), name, label, row.optLong("at")))
                }
            }
            RecentActivityPolicy.prune(RecentActivityHistory(apps, roms), now)
        }.getOrDefault(RecentActivityHistory())
    }

    private fun persist() {
        val apps = JSONArray()
        history.apps.forEach { (name, at) -> apps.put(JSONObject().put("package", name).put("at", at)) }
        val roms = JSONArray()
        history.roms.values.forEach { record -> roms.put(JSONObject().put("package", record.packageName)
            .put("item", record.itemId.value).put("label", record.emulatorLabel).put("at", record.launchedAtMillis)) }
        val encoded = JSONObject().put("version", 1).put("apps", apps).put("roms", roms).toString()
        // This tiny history is written off-main; a disk failure never changes launch behavior.
        runCatching { preferences.edit().putString("history", encoded).commit() }
    }

    private fun validPackage(value: String): Boolean = value.isNotBlank() && value.length <= 255 &&
        value.none { it.isWhitespace() || it == '/' }
}
