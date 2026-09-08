package dev.handheld.launcher.core.data.android.status

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.handheld.launcher.core.domain.model.StatusValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Existing device metadata only; never mounts storage, writes files or changes battery/radio state. */
@RunWith(AndroidJUnit4::class)
class DeviceTelemetryReadInstrumentedTest {
    @Test fun mountedVolumeSnapshotKeepsInternalAndNeverPublishesNegativeFreeBytes() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val reading = withContext(Dispatchers.IO) { readStorageTelemetry(context) }
        assertTrue("Internal StatFs failed: ${reading.internalAvailableBytes}",
            reading.internalAvailableBytes is StatusValue.Available)
        assertTrue((reading.internalAvailableBytes as StatusValue.Available).value >= 0)
        val volumes = (reading.volumes as? StatusValue.Available)?.value
        assertTrue("Public StorageManager enumeration failed: ${reading.volumes}", volumes != null)
        assertEquals(1, volumes!!.count { it.kind == StorageVolumeKind.INTERNAL })
        assertEquals(volumes.size, volumes.map { it.id }.distinct().size)
        assertEquals(reading.internalAvailableBytes, volumes.single { it.kind == StorageVolumeKind.INTERNAL }.availableBytes)
        volumes.forEach { volume ->
            val available = volume.availableBytes as? StatusValue.Available
            assertTrue("Invalid free bytes for a mounted volume", available == null || available.value >= 0)
        }
    }

    @Test fun foregroundSourcePublishesChargingWithTheBatteryBroadcastAndStorageSample() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val snapshot = withTimeout(10_000) {
            AndroidDeviceStatusSource(context).status.first {
                it.batteryPercent is StatusValue.Available && it.storageVolumes is StatusValue.Available
            }
        }
        assertTrue(snapshot.batteryCharging is StatusValue.Available || snapshot.batteryCharging == StatusValue.Unavailable)
        assertTrue((snapshot.batteryPercent as StatusValue.Available).value in 0..100)
        assertTrue((snapshot.storageVolumes as StatusValue.Available).value.any { it.kind == StorageVolumeKind.INTERNAL })
    }
}
