package dev.handheld.launcher.core.data.android.status

import android.os.BatteryManager
import dev.handheld.launcher.core.domain.model.StatusValue
import java.io.File
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageTelemetryTest {
    private val internalRoot = File("telemetry-fixtures/internal")

    @Test fun internalAndEachMountedExternalAreReadWithoutCountingPrimaryEmulatedStorageTwice() {
        val primary = volume("primary", "emulated", primary = true, emulated = true)
        val external = listOf(volume("card", "card"), volume("usb", "usb", readOnly = true))
        val reads = mutableListOf<File>()
        val result = storageVolumeReadings(internalRoot, StatusValue.Available(100L),
            listOf(primary) + external + volume("offline", "offline", mounted = false)) { reads += it; 200L }

        assertEquals(listOf(StorageVolumeKind.INTERNAL, StorageVolumeKind.EXTERNAL, StorageVolumeKind.EXTERNAL), result.map { it.kind })
        assertEquals(StatusValue.Available(100L), result.first().availableBytes)
        assertEquals(external.map { it.directory }, reads)
        assertEquals(listOf("internal", "card", "usb"), result.map { it.id })
        assertTrue(result.last().readOnly)
    }

    @Test fun physicalPrimaryVolumeIsRetained() {
        val result = storageVolumeReadings(internalRoot, StatusValue.Available(100L),
            listOf(volume("physical-primary", "physical", primary = true))) { 200L }
        assertEquals(2, result.size)
        assertEquals("physical-primary", result.last().id)
    }

    @Test fun canonicalRootsAndCaseInsensitiveVolumeIdsAreDeduplicatedBeforeStat() {
        val reads = mutableListOf<File>()
        val result = storageVolumeReadings(internalRoot, StatusValue.Available(100L), listOf(
            volume("card", "card"),
            volume("CARD", "another-alias"),
            volume("other-name", "card/../card"),
            volume("internal-alias", "internal"),
        )) { reads += it; 200L }
        assertEquals(2, result.size)
        assertEquals(1, reads.size)
        assertEquals(StatusValue.Available(200L), result.last().availableBytes)
    }

    @Test fun mountedButUnreadableVolumesRemainUnknownAndDoNotEraseReadableVolumes() {
        val result = storageVolumeReadings(internalRoot, StatusValue.Available(100L), listOf(
            volume("denied", "denied"),
            volume("negative", "negative"),
            volume("ok", "ok"),
            volume("unresolved", null),
        )) {
            when (it.name) {
                "denied" -> throw SecurityException("No access")
                "negative" -> -1L
                else -> 200L
            }
        }.associateBy { it.id }
        assertEquals(StatusValue.Available(100L), result.getValue("internal").availableBytes)
        assertEquals(StatusValue.Available(200L), result.getValue("ok").availableBytes)
        listOf("denied", "negative", "unresolved").forEach {
            assertEquals(StatusValue.Unavailable, result.getValue(it).availableBytes)
        }
    }

    @Test fun readableDuplicateWinsOverVolumeWithoutAnAccessibleDirectory() {
        val result = storageVolumeReadings(internalRoot, StatusValue.Unavailable,
            listOf(volume("same", null), volume("same", "ok"))) { 42L }
        assertEquals(2, result.size)
        assertEquals(StatusValue.Available(42L), result.last().availableBytes)
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsNotPublishedAsUnavailableStorage() {
        storageVolumeReadings(internalRoot, StatusValue.Available(100L), listOf(volume("card", "card"))) {
            throw CancellationException("Foreground stopped")
        }
    }

    @Test fun connectedVolumeListIsNotTruncatedToOneExternalDrive() {
        val volumes = (1..12).map { volume("volume-$it", "volume-$it") }
        val result = storageVolumeReadings(internalRoot, StatusValue.Available(100L), volumes) { 200L }
        assertEquals(13, result.size)
        assertEquals(12, result.count { it.kind == StorageVolumeKind.EXTERNAL })
    }

    @Test fun onlyAnExplicitChargingStatusIsCharging() {
        assertEquals(StatusValue.Available(true), batteryChargingFromStatus(BatteryManager.BATTERY_STATUS_CHARGING))
        listOf(BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_STATUS_NOT_CHARGING,
            BatteryManager.BATTERY_STATUS_FULL).forEach {
            assertEquals(StatusValue.Available(false), batteryChargingFromStatus(it))
        }
        assertEquals(StatusValue.Unavailable, batteryChargingFromStatus(BatteryManager.BATTERY_STATUS_UNKNOWN))
        assertEquals(StatusValue.Unavailable, batteryChargingFromStatus(-1))
    }

    private fun volume(id: String, directory: String?, primary: Boolean = false, emulated: Boolean = false,
        mounted: Boolean = true, readOnly: Boolean = false) = StorageVolumeCandidate(id,
        directory?.let { File("telemetry-fixtures/$it") }, primary, emulated, mounted, readOnly)
}
