package dev.handheld.launcher.shell

import dev.handheld.launcher.contract.StatusPresentation
import dev.handheld.launcher.core.data.android.status.DeviceStatusSnapshot
import dev.handheld.launcher.core.data.android.status.StorageVolumeKind
import dev.handheld.launcher.core.data.android.status.StorageVolumeStatus
import dev.handheld.launcher.core.designsystem.glyphs.LauncherStatusGlyph
import dev.handheld.launcher.core.domain.model.StatusValue
import java.util.Locale

/** Pure status formatting. No sensor, permission or filesystem work belongs in composition. */
fun DeviceStatusSnapshot.toShellStatus(): LauncherShellStatus {
    val temperature = (batteryTemperatureCelsius as? StatusValue.Available)?.value?.takeIf(Float::isFinite)
    val percent = (batteryPercent as? StatusValue.Available)?.value?.takeIf { it in 0..100 }
    val used = (usedMemoryBytes as? StatusValue.Available)?.value
    val total = (totalMemoryBytes as? StatusValue.Available)?.value
    val memoryKnown = used != null && total != null && used >= 0 && total > 0 && used <= total
    val memory = if (memoryKnown) StatusValue.Available(
        "${decimal(used!! / GIB)}/${decimal(total!! / GIB, 0)}G",
    ) else if (usedMemoryBytes == StatusValue.Unsupported || totalMemoryBytes == StatusValue.Unsupported) StatusValue.Unsupported
        else StatusValue.Unavailable
    val memoryDescription = if (memoryKnown) "Memory used: ${decimal(used!! / GIB)} of ${decimal(total!! / GIB)} GiB"
        else "Memory used: unavailable"
    val batteryDescription = buildString {
        append("Battery: "); append(percent?.let { "$it%" } ?: "unavailable")
        if (batteryCharging == StatusValue.Available(true)) append(", charging")
    }
    return LauncherShellStatus(
        readings = listOf(
            ShellStatusReading(StatusPresentation("Battery temperature",
                temperature?.let { StatusValue.Available("${decimal(it.toDouble(), 0)}°C") }
                    ?: if (batteryTemperatureCelsius == StatusValue.Unsupported) StatusValue.Unsupported else StatusValue.Unavailable,
                "Battery temperature"), ShellStatusGlyph.Temperature,
                when {
                    temperature == null -> LauncherStatusGlyph.Temperature
                    temperature < 15 -> LauncherStatusGlyph.TemperatureLow
                    temperature >= 45 -> LauncherStatusGlyph.TemperatureHigh
                    else -> LauncherStatusGlyph.Temperature
                }, tint = temperatureTint(temperature)),
            ShellStatusReading(StatusPresentation("Memory used", memory, "Memory used"), ShellStatusGlyph.Memory,
                if (memoryKnown && used!!.toDouble() / total!! >= .9) LauncherStatusGlyph.MemoryHigh else LauncherStatusGlyph.Memory,
                tint = ShellStatusTint.Memory, accessibilityDescription = memoryDescription),
        ) + storageReadings(),
        rightReadings = listOf(
            ShellStatusReading(StatusPresentation("Wi-Fi", wifiEnabled.format { if (it) "Enabled" else "Disabled" }, "Wi-Fi"),
                ShellStatusGlyph.Wifi, if (wifiEnabled == StatusValue.Available(false)) LauncherStatusGlyph.WifiOff else LauncherStatusGlyph.Wifi,
                tint = if (wifiEnabled == StatusValue.Available(false)) ShellStatusTint.Muted else ShellStatusTint.Neutral),
            ShellStatusReading(StatusPresentation("Battery", percent?.let { StatusValue.Available("$it%") }
                ?: if (batteryPercent == StatusValue.Unsupported) StatusValue.Unsupported else StatusValue.Unavailable,
                "Battery"), ShellStatusGlyph.Battery, batterySymbol(percent), tint = batteryTint(percent, batteryCharging),
                accessibilityDescription = batteryDescription),
        ),
        bluetoothEnabled = bluetoothEnabled == StatusValue.Available(true),
        notificationsPresent = notificationsPresent == StatusValue.Available(true),
    )
}

internal fun temperatureTint(celsius: Float?): ShellStatusTint = when {
    celsius == null || !celsius.isFinite() -> ShellStatusTint.Muted
    celsius < 15 -> ShellStatusTint.Cold
    celsius >= 45 -> ShellStatusTint.Hot
    else -> ShellStatusTint.Neutral
}

internal fun batteryTint(percent: Int?, charging: StatusValue<Boolean>): ShellStatusTint = when {
    charging == StatusValue.Available(true) -> ShellStatusTint.Charging
    percent == null || percent !in 0..100 -> ShellStatusTint.Muted
    percent <= 10 -> ShellStatusTint.Hot
    percent < 15 -> ShellStatusTint.Warning
    else -> ShellStatusTint.Neutral
}

private fun DeviceStatusSnapshot.storageReadings(): List<ShellStatusReading> {
    val volumes = (storageVolumes as? StatusValue.Available)?.value
    val internal = volumes?.firstOrNull { it.kind == StorageVolumeKind.INTERNAL }
        ?: StorageVolumeStatus("internal", StorageVolumeKind.INTERNAL, freeStorageBytes)
    val external = volumes?.filter { it.kind == StorageVolumeKind.EXTERNAL }.orEmpty()
    return buildList {
        add(storageReading("INT", listOf(internal)))
        when {
            external.isNotEmpty() -> add(storageReading(if (external.size == 1) "EXT" else "EXT×${external.size}", external))
            storageVolumes == StatusValue.Unavailable -> add(ShellStatusReading(
                StatusPresentation("External storage available", StatusValue.Unavailable, "External storage available"),
                ShellStatusGlyph.Storage, LauncherStatusGlyph.Storage, ShellStatusTint.Muted,
                displayText = "EXT —", accessibilityDescription = "External storage availability is unknown"))
        }
    }
}

private fun storageReading(label: String, volumes: List<StorageVolumeStatus>): ShellStatusReading {
    val known = volumes.mapNotNull { (it.availableBytes as? StatusValue.Available)?.value?.takeIf { value -> value >= 0 } }
    val total = known.fold(0L as Long?) { sum, bytes ->
        if (sum == null || Long.MAX_VALUE - sum < bytes) null else sum + bytes
    }
    val hasUnknown = known.size != volumes.size || total == null
    val compact = when {
        known.isEmpty() || total == null -> "$label —"
        hasUnknown -> "$label ${compactBytes(total)}+?"
        else -> "$label ${compactBytes(total)}"
    }
    val description = volumes.mapIndexed { index, volume ->
        val name = if (volume.kind == StorageVolumeKind.INTERNAL) "Internal storage" else if (volumes.size == 1) "External storage" else "External storage ${index + 1}"
        val bytes = (volume.availableBytes as? StatusValue.Available)?.value?.takeIf { it >= 0 }
        "$name available: ${bytes?.let { "${decimal(it / GIB)} GiB" } ?: "unavailable"}${if (volume.readOnly) ", read-only" else ""}"
    }.joinToString("; ")
    val low = known.any { it < GIB }
    return ShellStatusReading(
        StatusPresentation(if (volumes.first().kind == StorageVolumeKind.INTERNAL) "Internal storage available" else "External storage available",
            if (known.isEmpty() || total == null) StatusValue.Unavailable else StatusValue.Available(compact), description),
        ShellStatusGlyph.Storage, if (low) LauncherStatusGlyph.StorageLow else LauncherStatusGlyph.Storage,
        tint = if (low) ShellStatusTint.Hot else ShellStatusTint.Muted,
        displayText = compact, accessibilityDescription = description,
    )
}

private fun compactBytes(bytes: Long): String = when {
    bytes == 0L -> "0G"
    bytes >= GIB -> "${(bytes / GIB).toLong()}G"
    bytes >= 1_048_576L -> "${bytes / 1_048_576L}M"
    else -> "<1M"
}

private fun batterySymbol(percent: Int?): LauncherStatusGlyph = when (percent) {
    null -> LauncherStatusGlyph.BatteryUnknown
    in 0..14 -> LauncherStatusGlyph.Battery0
    in 15..28 -> LauncherStatusGlyph.Battery1
    in 29..42 -> LauncherStatusGlyph.Battery2
    in 43..56 -> LauncherStatusGlyph.Battery3
    in 57..70 -> LauncherStatusGlyph.Battery4
    in 71..85 -> LauncherStatusGlyph.Battery5
    in 86..97 -> LauncherStatusGlyph.Battery6
    else -> LauncherStatusGlyph.BatteryFull
}

private fun <T : Any> StatusValue<T>.format(formatter: (T) -> String): StatusValue<String> = when (this) {
    is StatusValue.Available -> StatusValue.Available(formatter(value))
    StatusValue.Unavailable -> StatusValue.Unavailable
    StatusValue.Unsupported -> StatusValue.Unsupported
}

private fun decimal(value: Double, places: Int = 1): String = String.format(Locale.getDefault(), "%.${places}f", value)
private const val GIB = 1_073_741_824.0
