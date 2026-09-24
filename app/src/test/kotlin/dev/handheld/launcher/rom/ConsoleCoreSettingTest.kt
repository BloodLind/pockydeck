package dev.handheld.launcher.rom

import dev.handheld.launcher.core.data.rom.emulator.*
import org.junit.Assert.*
import org.junit.Test

class ConsoleCoreSettingTest {
    private val core = RetroArchCore("mgba", "mGBA", setOf("gba"), setOf("gba"))
    private val retroarch = InstalledEmulator("retroarch", "org.libretro", "RetroArch", "1.22.2",
        setOf("gba"), setOf("gba"), EmulatorLaunchSupport.NEEDS_CORE, cores = listOf(core))
    private val standalone = retroarch.copy(id = "standalone", displayName = "Standalone",
        launchSupport = EmulatorLaunchSupport.DIRECT, cores = emptyList())

    @Test fun standalonePreferenceAndAskOnOpenOmitIrrelevantCores() {
        val apps = listOf(retroarch, standalone)
        assertNull(consoleCoreLabel(apps, standalone.id, core.id))
        assertNull(consoleCoreLabel(apps, null, core.id))
        assertNull(consoleCoreLabel(apps, "removed-emulator", core.id))
    }

    @Test fun selectedOrSoleRetroarchExposesItsOwnCore() {
        assertEquals("mGBA", consoleCoreLabel(listOf(retroarch, standalone), retroarch.id, core.id))
        assertEquals("mGBA", consoleCoreLabel(listOf(retroarch), null, core.id))
        assertEquals("Choose installed core", consoleCoreLabel(listOf(retroarch), null, "another-platform-core"))
        assertNull(consoleCoreLabel(listOf(retroarch.copy(launchSupport = EmulatorLaunchSupport.UNSUPPORTED)), retroarch.id, core.id))
    }
}
