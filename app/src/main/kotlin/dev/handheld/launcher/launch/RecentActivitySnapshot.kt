package dev.handheld.launcher.launch

import dev.handheld.launcher.core.domain.model.Availability
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.model.LibraryItem

data class RecentRomActivity(
    val itemId: ItemId,
    val packageName: String,
    val emulatorLabel: String,
    val launchedAtMillis: Long,
)

/** Local observations of recent activity, never a claim that an app or game is still running. */
data class RecentActivitySnapshot(
    val usageAccessGranted: Boolean = false,
    val appLastActive: Map<String, Long> = emptyMap(),
    val lastRomPerEmulator: Map<String, RecentRomActivity> = emptyMap(),
    val evaluatedAtMillis: Long = 0L,
) {
    fun labels(items: List<LibraryItem>, nowMillis: Long = System.currentTimeMillis()): Map<ItemId, String> {
        val recentRoms = lastRomPerEmulator.values
            .filter { RecentActivityPolicy.isRecent(it.launchedAtMillis, nowMillis) }
            .groupBy { it.itemId }
            .mapValues { (_, records) -> records.maxBy { it.launchedAtMillis } }
        return buildMap {
            items.filter { it.availability == Availability.Available }.forEach { item ->
                when (item) {
                    is LibraryItem.AndroidApp -> if (appLastActive[item.componentId.packageName]?.let {
                        RecentActivityPolicy.isRecent(it, nowMillis)
                    } == true) put(item.id, "Recently active")
                    is LibraryItem.RomGame -> recentRoms[item.id]?.let {
                        put(item.id, "${it.emulatorLabel} · Recently active")
                    }
                    else -> Unit
                }
            }
        }
    }
}

internal data class RecentActivityHistory(
    val apps: Map<String, Long> = emptyMap(),
    val roms: Map<String, RecentRomActivity> = emptyMap(),
)

internal object RecentActivityPolicy {
    const val WINDOW_MILLIS = 30L * 60L * 1_000L
    const val MAX_APPS = 200
    const val MAX_EMULATORS = 32

    fun isRecent(timestamp: Long, now: Long): Boolean =
        timestamp > 0L && now >= timestamp && now - timestamp < WINDOW_MILLIS

    fun prune(history: RecentActivityHistory, now: Long): RecentActivityHistory = RecentActivityHistory(
        history.apps.filter { (name, at) -> name.isNotBlank() && isRecent(at, now) }
            .entries.sortedByDescending { it.value }.take(MAX_APPS).associate { it.toPair() },
        history.roms.filter { (name, record) -> name == record.packageName && name.isNotBlank() &&
            record.emulatorLabel.isNotBlank() && isRecent(record.launchedAtMillis, now) }
            .entries.sortedByDescending { it.value.launchedAtMillis }.take(MAX_EMULATORS).associate { it.toPair() },
    )

    fun recordApp(history: RecentActivityHistory, packageName: String, at: Long, now: Long = at): RecentActivityHistory {
        val retained = prune(history, now)
        if (!isRecent(at, now)) return retained
        return prune(retained.copy(apps = retained.apps + (packageName to maxOf(retained.apps[packageName] ?: 0L, at))), now)
    }

    fun recordRom(history: RecentActivityHistory, record: RecentRomActivity, now: Long = record.launchedAtMillis): RecentActivityHistory {
        val retained = recordApp(history, record.packageName, record.launchedAtMillis, now)
        if (!isRecent(record.launchedAtMillis, now) ||
            (retained.roms[record.packageName]?.launchedAtMillis ?: 0L) > record.launchedAtMillis) return retained
        return prune(retained.copy(roms = retained.roms + (record.packageName to record)), now)
    }

    fun snapshot(history: RecentActivityHistory, observedApps: Map<String, Long>, accessGranted: Boolean,
        now: Long): RecentActivitySnapshot {
        val retained = prune(history, now)
        val merged = retained.apps.toMutableMap()
        if (accessGranted) observedApps.forEach { (name, at) ->
            if (name.isNotBlank() && isRecent(at, now)) merged[name] = maxOf(merged[name] ?: 0L, at)
        }
        return RecentActivitySnapshot(accessGranted,
            merged.entries.sortedByDescending { it.value }.take(MAX_APPS).associate { it.toPair() },
            retained.roms, now)
    }
}
