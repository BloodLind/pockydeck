package dev.handheld.launcher.rom

import dev.handheld.launcher.core.data.rom.emulator.EmulatorLaunchSupport
import dev.handheld.launcher.core.data.rom.emulator.InstalledEmulator

/** Describes the configured app; launch still validates the particular ROM and folder. */
internal fun collectionEmulatorLabel(
    installed: List<InstalledEmulator>, itemPreference: String?, consolePreference: String?,
): String {
    val preferred = itemPreference ?: consolePreference
    if (preferred != null) {
        val saved = installed.firstOrNull { it.id == preferred } ?: return "Saved emulator unavailable"
        return if (saved.launchSupport == EmulatorLaunchSupport.UNSUPPORTED) "${saved.displayName} · unavailable"
        else saved.displayName
    }
    val candidates = installed.filter { it.launchSupport != EmulatorLaunchSupport.UNSUPPORTED }
    return when (candidates.size) {
        0 -> "No compatible emulator"
        1 -> candidates.single().displayName
        else -> "Choose when opening"
    }
}
