package dev.handheld.launcher.core.data.discovery

import dev.handheld.launcher.core.data.android.apps.AndroidPackageChangeMonitor
import dev.handheld.launcher.core.domain.model.CatalogInventory
import dev.handheld.launcher.core.domain.model.CatalogSnapshot
import dev.handheld.launcher.core.domain.model.IncompleteInventoryReason
import dev.handheld.launcher.core.domain.model.InventoryScope
import dev.handheld.launcher.core.domain.repository.CatalogRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AndroidCatalogRefreshTrigger {
    STARTUP,
    RESUME,
    PACKAGE_CHANGED,
    EXPLICIT,
}

sealed interface AndroidCatalogRefreshState {
    data object Idle : AndroidCatalogRefreshState

    data class Refreshing(
        val trigger: AndroidCatalogRefreshTrigger,
    ) : AndroidCatalogRefreshState

    data class Ready(
        val trigger: AndroidCatalogRefreshTrigger,
    ) : AndroidCatalogRefreshState

    data class Error(
        val trigger: AndroidCatalogRefreshTrigger,
        val reason: IncompleteInventoryReason,
    ) : AndroidCatalogRefreshState
}

/** One cache snapshot paired with the independently changing background refresh state. */
data class AndroidCatalogState(
    val snapshot: CatalogSnapshot,
    val refreshState: AndroidCatalogRefreshState,
)

/**
 * Keeps Room observable before discovery and funnels refresh requests through one serial worker.
 *
 * Lifecycle stop only disables package callbacks. The application-owned worker remains the sole
 * reconciliation owner, so an immediate stop/start cannot race two scans or status writes.
 */
class AndroidCatalogRefreshCoordinator(
    catalogRepository: CatalogRepository,
    private val discovery: AndroidAppDiscovery,
    private val packageChanges: AndroidPackageChangeMonitor,
    scope: CoroutineScope,
) {
    val catalog: Flow<CatalogSnapshot> = catalogRepository.snapshot

    private val mutableRefreshState =
        MutableStateFlow<AndroidCatalogRefreshState>(AndroidCatalogRefreshState.Idle)
    val refreshState: StateFlow<AndroidCatalogRefreshState> = mutableRefreshState.asStateFlow()

    val state: Flow<AndroidCatalogState> = combine(catalog, refreshState, ::AndroidCatalogState)

    private val repository = catalogRepository
    private val triggers = Channel<AndroidCatalogRefreshTrigger>(Channel.CONFLATED)
    private val lifecycleLock = Any()
    private var packageMonitorStarted = false
    private var startupRequested = false
    private var suppressNextResume = false

    init {
        scope.launch {
            for (trigger in triggers) {
                reconcile(trigger)
            }
        }
    }

    /** Registers active package callbacks and requests the process's first inventory scan once. */
    fun start() {
        val requestStartup: Boolean
        synchronized(lifecycleLock) {
            if (!packageMonitorStarted) {
                packageChanges.start(::onPackageChanged)
                packageMonitorStarted = true
            }
            requestStartup = !startupRequested
            if (requestStartup) {
                startupRequested = true
                suppressNextResume = true
            }
        }
        if (requestStartup) request(AndroidCatalogRefreshTrigger.STARTUP)
    }

    /** Stops package callbacks; an already-running scan remains serialized by the application worker. */
    fun stop() {
        synchronized(lifecycleLock) {
            if (packageMonitorStarted) {
                packageChanges.stop()
                packageMonitorStarted = false
            }
            suppressNextResume = false
        }
    }

    /** Repairs missed inactive package callbacks; the immediate startup/resume pair is coalesced. */
    fun onResume() {
        val shouldRequest = synchronized(lifecycleLock) {
            if (suppressNextResume) {
                suppressNextResume = false
                false
            } else {
                true
            }
        }
        if (shouldRequest) request(AndroidCatalogRefreshTrigger.RESUME)
    }

    fun refresh() {
        request(AndroidCatalogRefreshTrigger.EXPLICIT)
    }

    private fun onPackageChanged() {
        request(AndroidCatalogRefreshTrigger.PACKAGE_CHANGED)
    }

    private fun request(trigger: AndroidCatalogRefreshTrigger) {
        triggers.trySend(trigger)
    }

    private suspend fun reconcile(trigger: AndroidCatalogRefreshTrigger) {
        mutableRefreshState.value = AndroidCatalogRefreshState.Refreshing(trigger)
        try {
            if (trigger == AndroidCatalogRefreshTrigger.STARTUP) {
                repository.snapshot.first()
            }
            val inventory = discovery.discover()
            repository.applyInventory(inventory)
            mutableRefreshState.value = inventory.toRefreshState(trigger)
        } catch (_: CancellationException) {
            withContext(NonCancellable) {
                recordIncomplete(IncompleteInventoryReason.CANCELLED)
                mutableRefreshState.value = AndroidCatalogRefreshState.Error(
                    trigger,
                    IncompleteInventoryReason.CANCELLED,
                )
            }
            currentCoroutineContext().ensureActive()
        } catch (_: Exception) {
            recordIncomplete(IncompleteInventoryReason.FAILED)
            mutableRefreshState.value = AndroidCatalogRefreshState.Error(
                trigger,
                IncompleteInventoryReason.FAILED,
            )
        }
    }

    private suspend fun recordIncomplete(reason: IncompleteInventoryReason) {
        try {
            repository.applyInventory(
                CatalogInventory.Incomplete(
                    scope = InventoryScope.CurrentUserAndroid,
                    observedItems = emptyList(),
                    reason = reason,
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The refresh state still reports the failure when persistence itself is unavailable.
        }
    }
}

private fun CatalogInventory.toRefreshState(
    trigger: AndroidCatalogRefreshTrigger,
): AndroidCatalogRefreshState = when (this) {
    is CatalogInventory.Complete -> AndroidCatalogRefreshState.Ready(trigger)
    is CatalogInventory.Incomplete -> AndroidCatalogRefreshState.Error(trigger, reason)
}
