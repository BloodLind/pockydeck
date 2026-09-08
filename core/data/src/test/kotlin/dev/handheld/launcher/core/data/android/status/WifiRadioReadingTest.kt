package dev.handheld.launcher.core.data.android.status

import dev.handheld.launcher.core.domain.model.StatusValue
import org.junit.Assert.assertEquals
import org.junit.Test

class WifiRadioReadingTest {
    @Test fun enabledButDisconnectedRadioRemainsEnabled() {
        val snapshot = DeviceStatusSnapshot(wifiConnected = StatusValue.Available(false),
            wifiEnabled = readWifiRadioEnabled { true })
        assertEquals(StatusValue.Available(true), snapshot.wifiEnabled)
        assertEquals(StatusValue.Available(false), snapshot.wifiConnected)
    }

    @Test fun disabledRadioRemainsDistinctFromUnreadableOrMissingService() {
        assertEquals(StatusValue.Available(false), readWifiRadioEnabled { false })
        assertEquals(StatusValue.Unavailable, readWifiRadioEnabled { throw SecurityException("No access") })
        assertEquals(StatusValue.Unavailable, readWifiRadioEnabled { throw IllegalStateException("Service unavailable") })
        assertEquals(StatusValue.Unsupported, readWifiRadioEnabled(null))
    }

    @Test fun eachNotificationReadsTheCurrentRadioValue() {
        var enabled = false
        val read = { enabled }
        assertEquals(StatusValue.Available(false), readWifiRadioEnabled(read))
        enabled = true
        assertEquals(StatusValue.Available(true), readWifiRadioEnabled(read))
    }
}
