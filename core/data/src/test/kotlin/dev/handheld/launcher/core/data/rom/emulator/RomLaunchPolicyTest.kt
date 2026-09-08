package dev.handheld.launcher.core.data.rom.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RomLaunchPolicyTest {
    @Test fun pcAppsAcceptSteamShortcutsAndKeepExplicitChoiceWhenSeveralAreInstalled() {
        val native = profile("gamenative")
        val gamehub = profile("gamehub.lite")
        assertNull(RomLaunchPolicy.unsupportedReason(native, input("windows", "steam")))
        assertNull(RomLaunchPolicy.unsupportedReason(gamehub, input("windows", "steam")))
        assertNull(RomLaunchPolicy.unsupportedReason(native, input("windows", "gog")))
        assertNotNull(RomLaunchPolicy.unsupportedReason(gamehub, input("windows", "gog")))
        assertNotNull(RomLaunchPolicy.unsupportedReason(native, input("windows", "exe")))
        assertNotNull(RomLaunchPolicy.unsupportedReason(native, input("windows", "steam").copy(companionUris = listOf("content://other"))))
        val choices = EmulatorResolution(listOf(installed("gamenative"), installed("gamehub.lite")), emptyList())
        assertNull(choices.automaticChoice(null))
        assertEquals("gamenative", choices.automaticChoice("gamenative")?.id)
    }

    @Test fun gameNativeVersionProtectsSourceAwareLaunches() {
        assertFalse(AndroidEmulatorResolver.supportsGameNative(null))
        assertFalse(AndroidEmulatorResolver.supportsGameNative("1.1.9"))
        assertTrue(AndroidEmulatorResolver.supportsGameNative("1.2.0"))
        assertTrue(AndroidEmulatorResolver.supportsGameNative("1.3.0"))
    }

    private fun profile(id: String) = EmulatorRegistry.profiles.first { it.id == id }
    private fun input(platform: String, extension: String, core: String? = null) =
        RomLaunchInput(platform, extension, "content://test/document/game.$extension", coreId = core)

    @Test fun ambiguousDiscSuffixDoesNotCrossConsoleBoundary() {
        assertNotNull(RomLaunchPolicy.unsupportedReason(profile("ppsspp"), input("ps2", "iso")))
        assertNull(RomLaunchPolicy.unsupportedReason(profile("ppsspp"), input("psp", "ISO")))
        assertNull(RomLaunchPolicy.unsupportedReason(profile("dolphin"), input("gamecube", "rvz")))
    }

    @Test fun unsupportedArchiveIsPreparedInsteadOfPassedAsAnIso() {
        assertTrue(RomLaunchPolicy.unsupportedReason(profile("ppsspp"), input("psp", "7z"))!!.contains("prepared"))
        assertFalse("7z" in RomLaunchPolicy.extensions(profile("ppsspp"), "psp", null))
    }

    @Test fun nativeArcadeArchivesRemainWhole() {
        assertNull(RomLaunchPolicy.unsupportedReason(profile("retroarch-64"), input("arcade", "zip", "fbneo")))
        assertNull(RomLaunchPolicy.unsupportedReason(profile("retroarch-64"), input("arcade", "7z", "fbneo")))
        assertNull(RomLaunchPolicy.unsupportedReason(profile("flycast"), input("naomi", "zip")))
    }

    @Test fun archivePreparationFollowsActualCoreSupportAndExtractorFormats() {
        assertTrue(RomLaunchPolicy.unsupportedReason(profile("retroarch-64"), input("psx", "zip", "pcsx_rearmed"))!!.contains("prepared"))
        assertTrue(RomLaunchPolicy.unsupportedReason(profile("retroarch-64"), input("gba", "zip", "mgba"))!!.contains("prepared"))
        assertTrue(RomLaunchPolicy.unsupportedReason(profile("ppsspp"), input("psp", "tar.gz"))!!.contains("prepared"))
        assertFalse("rar" in RomLaunchPolicy.archiveExtensions)
        assertTrue("tar.xz" in RomLaunchPolicy.archiveExtensions)
        assertTrue("gzip" in RomLaunchPolicy.archiveExtensions)
    }

    @Test fun coreMustBelongToTheSelectedConsole() {
        assertNotNull(RomLaunchPolicy.unsupportedReason(profile("retroarch-64"), input("snes", "sfc", "mgba")))
        assertTrue(RomLaunchPolicy.extensions(profile("retroarch-64"), "snes", "mgba").isEmpty())
        assertNull(RomLaunchPolicy.unsupportedReason(profile("retroarch-64"), input("snes", "sfc", "snes9x")))
    }

    @Test fun coreFileNameCannotComeFromUnvalidatedUserText() {
        assertNotNull(RomLaunchPolicy.unsupportedReason(profile("retroarch"), input("nes", "nes", "../../elsewhere")))
        assertTrue(EmulatorRegistry.retroArchCores.all { it.id.matches(Regex("[a-z0-9_]+")) })
    }

    @Test fun companionFormatsRequireTheirContainingFolder() {
        assertNotNull(RomLaunchPolicy.unsupportedReason(profile("duckstation"), input("psx", "cue")))
        assertNull(RomLaunchPolicy.unsupportedReason(profile("duckstation"), input("psx", "cue").copy(treeUri = "content://test/tree/root", relativePath = "Game/game.cue")))
    }

    @Test fun retiredOrInstallOnlyFormatsDoNotBecomeFalseLaunchTargets() {
        assertNotNull(RomLaunchPolicy.unsupportedReason(profile("azahar"), input("3ds", "cia")))
        assertNotNull(RomLaunchPolicy.unsupportedReason(profile("azahar"), input("3ds", "3ds")))
        assertNotNull(RomLaunchPolicy.unsupportedReason(profile("vita3k"), input("psvita", "zip")))
    }

    @Test fun relativePathRejectsTraversalAbsolutePathsAndControlCharacters() {
        for (path in listOf("../game.nes", "/game.nes", "folder/../../game.nes", "folder//game.nes", "folder/./game.nes", "C:/game.nes", "folder\\game.nes", "game\u0000.nes")) {
            assertFalse(path, RomLaunchPolicy.validRelativePath(path))
        }
        assertTrue(RomLaunchPolicy.validRelativePath("NES/Gáme #1 (USA).nes"))
    }

    @Test fun safSerializationPreservesGameNameAndEscapesOnlyTreeUri() {
        assertEquals(
            "saf://content%3A%2F%2Fexample%2Ftree%2Fprimary%253AROMs/NES/Gáme #1.nes",
            RomLaunchPolicy.retroArchPath("content://example/tree/primary%3AROMs", "NES/Gáme #1.nes"),
        )
        assertNull(RomLaunchPolicy.retroArchPath("file:///storage/ROMs", "game.nes"))
    }

    @Test fun severalCandidatesNeverChooseByRegistryOrder() {
        val first = installed("ppsspp")
        val second = installed("ppsspp-gold")
        val result = EmulatorResolution(listOf(first, second), emptyList())
        assertNull(result.automaticChoice(null))
        assertNull(result.automaticChoice("removed"))
        assertEquals(second, result.automaticChoice(second.id))
    }

    @Test fun oneCandidateIsAutomaticAndRemovedDefaultIsIgnored() {
        val candidate = installed("ppsspp")
        assertEquals(candidate, EmulatorResolution(listOf(candidate), emptyList()).automaticChoice("removed"))
        assertNull(EmulatorResolution(emptyList(), listOf(candidate)).automaticChoice(candidate.id))
    }

    @Test fun detectionOnlyEntriesCannotAdvertiseDirectExtensions() {
        for (entry in EmulatorRegistry.profiles.filter { it.contract == EmulatorContract.DETECTION_ONLY }) {
            assertTrue(entry.extensions.isEmpty())
            assertFalse(entry.unavailableReason.isNullOrBlank())
            assertNull(entry.activityName)
        }
    }

    @Test fun registryIdentifiersAreUniqueAndModernInstalledConsolesAreCovered() {
        assertEquals(EmulatorRegistry.profiles.size, EmulatorRegistry.profiles.map { it.id }.toSet().size)
        assertEquals(EmulatorRegistry.profiles.size, EmulatorRegistry.profiles.map { it.packageName }.toSet().size)
        assertEquals(EmulatorRegistry.retroArchCores.size, EmulatorRegistry.retroArchCores.map { it.id }.toSet().size)
        for (platform in listOf("psp", "psx", "ps2", "nds", "3ds", "gamecube", "wii", "wiiu", "switch", "n64", "dreamcast")) {
            assertTrue(platform, EmulatorRegistry.profiles.any { platform in it.platforms && it.contract != EmulatorContract.DETECTION_ONLY })
        }
    }

    @Test fun retroArchVersionBoundaryIsExplicit() {
        assertFalse(AndroidEmulatorResolver.supportsRetroArchSaf(null))
        assertFalse(AndroidEmulatorResolver.supportsRetroArchSaf("nightly"))
        assertFalse(AndroidEmulatorResolver.supportsRetroArchSaf("1.21.0"))
        assertFalse(AndroidEmulatorResolver.supportsRetroArchSaf("1.22.1"))
        assertTrue(AndroidEmulatorResolver.supportsRetroArchSaf("1.22.2_GIT"))
        assertTrue(AndroidEmulatorResolver.supportsRetroArchSaf("1.23.0"))
    }

    private fun installed(id: String): InstalledEmulator = profile(id).let {
        InstalledEmulator(it.id, it.packageName, it.displayName, "test", it.platforms, it.extensions, EmulatorLaunchSupport.DIRECT)
    }
}
