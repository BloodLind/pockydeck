package dev.handheld.launcher.core.data.android.status

import android.bluetooth.BluetoothManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.handheld.launcher.core.domain.model.StatusValue
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Reads existing device state only: no grants, enabling, scanning or connections. */
@RunWith(AndroidJUnit4::class)
class BluetoothStatusReadInstrumentedTest {
    @Test fun powerStateUsesThePublicPermissionlessApiOrReportsNoHardware() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val reading = readBluetoothRadioEnabled {
            context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled
        }
        assertTrue("Public Bluetooth power-state read failed: $reading",
            reading is StatusValue.Available || reading == StatusValue.Unsupported)
    }
}
