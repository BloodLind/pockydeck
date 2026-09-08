package dev.handheld.launcher.core.data.android.status

import dev.handheld.launcher.core.domain.model.StatusValue
import org.junit.Assert.assertEquals
import org.junit.Test

class OptionalStatusReadingTest {
    @Test fun bluetoothOffIsDifferentFromAbsentOrUnreadableAdapter() {
        assertEquals(StatusValue.Available(false), readBluetoothRadioEnabled { false })
        assertEquals(StatusValue.Available(true), readBluetoothRadioEnabled { true })
        assertEquals(StatusValue.Unsupported, readBluetoothRadioEnabled { null })
        assertEquals(StatusValue.Unavailable, readBluetoothRadioEnabled { throw SecurityException("Denied") })
        assertEquals(StatusValue.Unavailable, readBluetoothRadioEnabled { throw IllegalStateException("Unavailable") })
    }

    @Test fun notificationPresenceNeedsAConnectedListenerAndAnActualReading() {
        val presence = NotificationPresenceState()
        val connection = Any()
        presence.update(connection, true)
        assertEquals(StatusValue.Unavailable, presence.state.value)
        presence.connected(connection)
        assertEquals(StatusValue.Unavailable, presence.state.value)
        presence.update(connection, false)
        assertEquals(StatusValue.Available(false), presence.state.value)
        presence.update(connection, true)
        assertEquals(StatusValue.Available(true), presence.state.value)
        presence.update(connection, null)
        assertEquals(StatusValue.Unavailable, presence.state.value)
        presence.update(connection, true)
        presence.disconnected(connection)
        assertEquals(StatusValue.Unavailable, presence.state.value)
    }

    @Test fun oldServiceCallbacksCannotOverwriteAReconnectedListener() {
        val presence = NotificationPresenceState()
        val oldConnection = Any()
        val newConnection = Any()
        presence.connected(oldConnection)
        presence.update(oldConnection, true)
        presence.connected(newConnection)
        assertEquals(StatusValue.Unavailable, presence.state.value)
        presence.update(newConnection, true)
        presence.update(oldConnection, false)
        presence.disconnected(oldConnection)
        assertEquals(StatusValue.Available(true), presence.state.value)
        presence.disconnected(newConnection)
        presence.update(newConnection, true)
        assertEquals(StatusValue.Unavailable, presence.state.value)
    }

    @Test fun revokedOrUnreadableAccessHidesPreviouslyObservedNotifications() {
        val present = StatusValue.Available(true)
        assertEquals(StatusValue.Unavailable, notificationPresenceForAccess(StatusValue.Available(false), present))
        assertEquals(StatusValue.Unavailable, notificationPresenceForAccess(StatusValue.Unavailable, present))
        assertEquals(StatusValue.Unsupported, notificationPresenceForAccess(StatusValue.Unsupported, present))
        assertEquals(present, notificationPresenceForAccess(StatusValue.Available(true), present))
        assertEquals(StatusValue.Available(false), notificationPresenceForAccess(StatusValue.Available(true), StatusValue.Available(false)))
        assertEquals(StatusValue.Unavailable, notificationPresenceForAccess(StatusValue.Available(true), StatusValue.Unavailable))
    }
}
