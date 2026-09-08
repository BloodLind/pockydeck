package dev.handheld.launcher.shell

import dev.handheld.launcher.core.data.android.status.DeviceStatusSnapshot
import dev.handheld.launcher.core.data.android.status.StorageVolumeKind
import dev.handheld.launcher.core.data.android.status.StorageVolumeStatus
import dev.handheld.launcher.core.domain.model.StatusValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceStatusPresentationTest {
    @Test fun batteryBandsUseTheRequestedBoundariesAndChargingWinsOverLowBattery() {
        val idle = StatusValue.Available(false)
        listOf(0, 9, 10).forEach { assertEquals(ShellStatusTint.Hot, batteryTint(it, idle)) }
        listOf(11, 14).forEach { assertEquals(ShellStatusTint.Warning, batteryTint(it, idle)) }
        listOf(15, 20, 100).forEach { assertEquals(ShellStatusTint.Neutral, batteryTint(it, idle)) }
        listOf(null, 0, 10, 100).forEach { assertEquals(ShellStatusTint.Charging, batteryTint(it, StatusValue.Available(true))) }
        assertEquals(ShellStatusTint.Muted, batteryTint(null, StatusValue.Unavailable))
        assertEquals(ShellStatusTint.Hot, batteryTint(10, StatusValue.Unavailable))
        assertEquals(ShellStatusTint.Neutral, batteryTint(100, StatusValue.Unavailable))
    }

    @Test fun batteryTemperatureHasColdNormalAndHighColorsWithoutClaimingCpuTelemetry() {
        assertEquals(ShellStatusTint.Cold, temperatureTint(14.9f))
        assertEquals(ShellStatusTint.Neutral, temperatureTint(15f))
        assertEquals(ShellStatusTint.Neutral, temperatureTint(44.9f))
        assertEquals(ShellStatusTint.Hot, temperatureTint(45f))
        assertEquals(ShellStatusTint.Muted, temperatureTint(Float.NaN))
        val reading = DeviceStatusSnapshot(batteryTemperatureCelsius = StatusValue.Available(45f)).toShellStatus().readings.first()
        assertEquals("Battery temperature", reading.presentation.contentDescription)
        assertEquals(ShellStatusTint.Hot, reading.tint)
    }

    @Test fun ramUsesItsOwnTintAndChargingHasAccessibleState() {
        val status = DeviceStatusSnapshot(usedMemoryBytes = StatusValue.Available(3 * GIB),
            totalMemoryBytes = StatusValue.Available(8 * GIB), batteryPercent = StatusValue.Available(10),
            batteryCharging = StatusValue.Available(true)).toShellStatus()
        val memory = status.readings.single { it.glyph == ShellStatusGlyph.Memory }
        assertEquals(ShellStatusTint.Memory, memory.tint)
        assertTrue(memory.accessibilityDescription!!.contains("GiB"))
        val battery = status.rightReadings.single { it.glyph == ShellStatusGlyph.Battery }
        assertEquals(ShellStatusTint.Charging, battery.tint)
        assertEquals("Battery: 10%, charging", battery.accessibilityDescription)
    }

    @Test fun allExternalVolumesContributeToCompactTotalAndEachKeepsSeparateAnonymousSemantics() {
        val status = snapshot(listOf(volume("private-name", 10 * GIB),
            volume("/private/mount/path", 20 * GIB, readOnly = true))).toShellStatus()
        val storage = status.readings.filter { it.glyph == ShellStatusGlyph.Storage }
        assertEquals(listOf("INT 100G", "EXT×2 30G"), storage.map { it.displayText })
        val description = storage.last().accessibilityDescription!!
        assertTrue(description.contains("External storage 1 available:"))
        assertTrue(description.contains("External storage 2 available:"))
        assertTrue(description.contains("read-only"))
        assertFalse(description.contains("private"))
        assertTrue(storage.last().displayText!!.length < 16)
    }

    @Test fun unreadableVolumeIsNotSilentlyCountedAsZeroInATotal() {
        val reading = snapshot(listOf(volume("known", 10 * GIB), volume("unknown", null))).toShellStatus()
            .readings.last()
        assertEquals("EXT×2 10G+?", reading.displayText)
        assertTrue(reading.accessibilityDescription!!.contains("External storage 2 available: unavailable"))
        val unknown = snapshot(listOf(volume("unknown", null))).toShellStatus().readings.last()
        assertEquals("EXT —", unknown.displayText)
        assertEquals(StatusValue.Unavailable, unknown.presentation.value)
    }

    @Test fun enumerationFailureIsDifferentFromNoMountedExternalVolume() {
        val failed = DeviceStatusSnapshot(freeStorageBytes = StatusValue.Available(100 * GIB)).toShellStatus()
        assertEquals(listOf("INT 100G", "EXT —"), failed.readings.filter { it.glyph == ShellStatusGlyph.Storage }.map { it.displayText })
        val empty = snapshot(emptyList()).toShellStatus()
        assertEquals(listOf("INT 100G"), empty.readings.filter { it.glyph == ShellStatusGlyph.Storage }.map { it.displayText })
    }

    @Test fun overflowAndInvalidSensorValuesDoNotBecomeValidReadings() {
        val status = snapshot(listOf(volume("one", Long.MAX_VALUE), volume("two", Long.MAX_VALUE)))
            .copy(batteryTemperatureCelsius = StatusValue.Available(Float.NaN), batteryPercent = StatusValue.Available(101),
                usedMemoryBytes = StatusValue.Available(20), totalMemoryBytes = StatusValue.Available(10)).toShellStatus()
        assertEquals("EXT×2 —", status.readings.last().displayText)
        listOf(ShellStatusGlyph.Temperature, ShellStatusGlyph.Memory).forEach { glyph ->
            assertEquals(StatusValue.Unavailable, status.readings.single { it.glyph == glyph }.presentation.value)
        }
        assertEquals(StatusValue.Unavailable, status.rightReadings.single { it.glyph == ShellStatusGlyph.Battery }.presentation.value)
    }

    @Test fun optionalIndicatorsStillRequireAnExplicitTrueReading() {
        val unknown = DeviceStatusSnapshot().toShellStatus()
        assertFalse(unknown.bluetoothEnabled)
        assertFalse(unknown.notificationsPresent)
        val available = DeviceStatusSnapshot(bluetoothEnabled = StatusValue.Available(true),
            notificationsPresent = StatusValue.Available(true)).toShellStatus()
        assertTrue(available.bluetoothEnabled)
        assertTrue(available.notificationsPresent)
    }

    @Test fun unsupportedSensorsRemainOmittedInsteadOfPretendingToBeTemporarilyUnavailable() {
        val status = DeviceStatusSnapshot(batteryTemperatureCelsius = StatusValue.Unsupported,
            batteryPercent = StatusValue.Unsupported, usedMemoryBytes = StatusValue.Unsupported,
            totalMemoryBytes = StatusValue.Unsupported).toShellStatus()
        assertFalse(status.readings.first { it.glyph == ShellStatusGlyph.Temperature }.presentation.shouldRender)
        assertFalse(status.readings.first { it.glyph == ShellStatusGlyph.Memory }.presentation.shouldRender)
        assertFalse(status.rightReadings.first { it.glyph == ShellStatusGlyph.Battery }.presentation.shouldRender)
    }

    private fun volume(id: String, bytes: Long?, readOnly: Boolean = false) = StorageVolumeStatus(id,
        StorageVolumeKind.EXTERNAL, bytes?.let { StatusValue.Available(it) } ?: StatusValue.Unavailable, readOnly)

    private fun snapshot(external: List<StorageVolumeStatus>) = DeviceStatusSnapshot(
        freeStorageBytes = StatusValue.Available(100 * GIB), storageVolumes = StatusValue.Available(
            listOf(StorageVolumeStatus("internal", StorageVolumeKind.INTERNAL, StatusValue.Available(100 * GIB))) + external))

    private companion object { const val GIB = 1_073_741_824L }
}
