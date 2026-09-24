package dev.handheld.launcher.rom

import dev.handheld.launcher.core.data.rom.emulator.EmulatorLaunchSupport
import dev.handheld.launcher.core.data.rom.emulator.InstalledEmulator

/** Core settings belong to the effective console emulator, not every installed RetroArch app. */
internal fun consoleCoreLabel(installed: List<InstalledEmulator>, preferred: String?, coreId: String?): String? {
    val candidates = installed.filter { it.launchSupport != EmulatorLaunchSupport.UNSUPPORTED }
    val effective = if (preferred != null) candidates.find { it.id == preferred } else candidates.singleOrNull()
    val choices = effective?.cores?.takeIf { it.isNotEmpty() } ?: return null
    return choices.find { it.id == coreId }?.displayName ?: "Choose installed core"
}
