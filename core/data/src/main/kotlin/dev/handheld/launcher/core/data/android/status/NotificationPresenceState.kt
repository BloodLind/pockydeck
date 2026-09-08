package dev.handheld.launcher.core.data.android.status

import dev.handheld.launcher.core.domain.model.StatusValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-local presence only. No notification identifiers, text, payloads or disk state. */
internal class NotificationPresenceState {
    private var owner: Any? = null
    private val mutableState = MutableStateFlow<StatusValue<Boolean>>(StatusValue.Unavailable)
    val state: StateFlow<StatusValue<Boolean>> = mutableState.asStateFlow()

    @Synchronized fun connected(token: Any) {
        owner = token
        mutableState.value = StatusValue.Unavailable
    }

    @Synchronized fun update(token: Any, present: Boolean?) {
        if (owner === token) {
            mutableState.value = present?.let { StatusValue.Available(it) } ?: StatusValue.Unavailable
        }
    }

    @Synchronized fun disconnected(token: Any) {
        if (owner === token) {
            owner = null
            mutableState.value = StatusValue.Unavailable
        }
    }
}

internal object NotificationPresenceStore {
    val presence = NotificationPresenceState()
}

/** Revocation or a disconnected listener is unknown, never an empty notification tray. */
internal fun notificationPresenceForAccess(
    access: StatusValue<Boolean>,
    presence: StatusValue<Boolean>,
): StatusValue<Boolean> = when (access) {
    is StatusValue.Available -> if (access.value) presence else StatusValue.Unavailable
    StatusValue.Unavailable -> StatusValue.Unavailable
    StatusValue.Unsupported -> StatusValue.Unsupported
}
