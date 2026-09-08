package dev.handheld.launcher.core.data.rom.emulator

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidEmulatorContractTest {
    @Test fun gameNativeReceivesTypedSteamIdWithoutFilesOrContainerOverrides() {
        val profile = EmulatorRegistry.profiles.first { it.id == "gamenative" }
        val intent = RomIntentFactory.build(profile, input("windows", "steam"), null, null, "/storage/emulated/0", 220)
        assertEquals("app.gamenative.LAUNCH_GAME", intent.action)
        assertEquals("app.gamenative.MainActivity", intent.component!!.className)
        assertEquals("app.gamenative", intent.component!!.packageName)
        assertEquals(220, intent.getIntExtra("app_id", -1))
        assertEquals("STEAM", intent.getStringExtra("game_source"))
        assertEquals(setOf("app_id", "game_source"), intent.extras!!.keySet())
        assertNull(intent.data)
        assertNull(intent.clipData)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, intent.flags)
    }

    @Test fun gameHubReceivesOnlySteamIdAndAutostart() {
        for (id in listOf("gamehub.lite", "emuready.gamehub.lite")) {
            val profile = EmulatorRegistry.profiles.first { it.id == id }
            val intent = RomIntentFactory.build(profile, input("windows", "steam"), null, null, "/storage/emulated/0", 219990)
            assertEquals(id, intent.component!!.packageName)
            assertEquals("gamehub.lite.LAUNCH_GAME", intent.action)
            assertEquals("219990", intent.getStringExtra("steamAppId"))
            assertTrue(intent.getBooleanExtra("autoStartGame", false))
            assertEquals(setOf("steamAppId", "autoStartGame"), intent.extras!!.keySet())
            assertNull(intent.data)
            assertNull(intent.clipData)
        }
    }

    @Test fun gameNativeSourceComesFromTheExportFormat() {
        val profile = EmulatorRegistry.profiles.first { it.id == "gamenative" }
        val expected = mapOf("steam" to "STEAM", "epic" to "EPIC", "gog" to "GOG", "amazon" to "AMAZON", "pcgame" to "CUSTOM_GAME")
        expected.forEach { (extension, source) ->
            val intent = RomIntentFactory.build(profile, input("windows", extension), null, null, "/storage/emulated/0", 123)
            assertEquals(source, intent.getStringExtra("game_source"))
            assertEquals(123, intent.getIntExtra("app_id", -1))
        }
    }

    private val tree = DocumentsContract.buildTreeDocumentUri("example.documents", "primary:ROMs")
    private fun input(platform: String, extension: String) = RomLaunchInput(
        platform, extension,
        DocumentsContract.buildDocumentUriUsingTree(tree, "primary:ROMs/Games/Test.$extension").toString(),
        treeUri = tree.toString(), relativePath = "Games/Test.$extension",
    )
    private fun build(id: String, input: RomLaunchInput) = RomIntentFactory.build(
        EmulatorRegistry.profiles.first { it.id == id }, input, tree,
        "/data/user/0/com.retroarch.aarch64", "/storage/emulated/0",
    )

    @Test fun viewContractPreservesOpaqueContentUriAndUsesReadOnlyTemporaryGrant() {
        val input = input("psp", "iso")
        val intent = build("ppsspp", input)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(input.documentUri, intent.dataString)
        assertEquals("org.ppsspp.ppsspp.PpssppActivity", intent.component!!.className)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, intent.flags and (Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        assertFalse(intent.hasExtra("IME"))
    }

    @Test fun ps2AndDuckStationUseVerifiedBootPathExtra() {
        val input = input("ps2", "chd")
        for (id in listOf("aethersx2", "nethersx2-turnip", "duckstation")) {
            val intent = build(id, input)
            assertEquals(Intent.ACTION_MAIN, intent.action)
            assertEquals(input.documentUri, intent.getStringExtra("bootPath"))
            assertFalse(intent.hasExtra("ROM_PATH"))
        }
    }

    @Test fun melonDsCarriesBothFreshAndSavedStateUriContracts() {
        val input = input("nds", "nds")
        val intent = build("melonds", input)
        assertEquals("me.magnum.melonds.LAUNCH_ROM", intent.action)
        assertEquals(input.documentUri, intent.dataString)
        assertEquals(input.documentUri, intent.getStringExtra("uri"))
    }

    @Test fun dolphinNeverReceivesPermissionOnlyTreeOrCompanionAsAnotherDisc() {
        val input = input("gamecube", "rvz").copy(companionUris = listOf("content://example.documents/document/sidecar"))
        val intent = build("dolphin", input)
        assertEquals("org.dolphinemu.dolphinemu.ui.main.MainActivity", intent.component!!.className)
        assertEquals(1, intent.clipData!!.itemCount)
        assertEquals(input.documentUri, intent.clipData!!.getItemAt(0).uri.toString())
        assertEquals(0, intent.flags and Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
    }

    @Test fun retroArchUsesSafSerializationAndChosenCoreWithoutChangingIme() {
        val input = input("snes", "sfc").copy(coreId = "snes9x")
        val intent = build("retroarch-64", input)
        assertEquals(RomLaunchPolicy.retroArchPath(tree.toString(), input.relativePath!!), intent.getStringExtra("ROM"))
        assertEquals("/data/user/0/com.retroarch.aarch64/cores/snes9x_libretro_android.so", intent.getStringExtra("LIBRETRO"))
        assertEquals("/storage/emulated/0/Android/data/com.retroarch.aarch64/files/retroarch.cfg", intent.getStringExtra("CONFIGFILE"))
        assertEquals(tree, intent.clipData!!.getItemAt(1).uri)
        assertTrue(intent.flags and Intent.FLAG_GRANT_PREFIX_URI_PERMISSION != 0)
        assertFalse(intent.hasExtra("IME"))
    }

    @Test fun hierarchyValidationRejectsSpoofedProviderAndMismatchedDocument() {
        val resolver = AndroidEmulatorResolver(ApplicationProvider.getApplicationContext())
        val valid = input("nes", "nes")
        assertNotNull(resolver.validatedTree(valid))
        assertNull(resolver.validatedTree(valid.copy(relativePath = "../outside.nes")))
        assertNull(resolver.validatedTree(valid.copy(relativePath = "other.nes")))
        assertNull(resolver.validatedTree(valid.copy(documentUri = valid.documentUri.replace("example.documents", "another.documents"))))
        assertNull(resolver.validatedTree(valid.copy(documentUri = "content://example.documents/document/opaque-id")))
    }

    @Test fun preparedCacheDocumentsUseTheSameValidatedTreeContract() {
        val resolver = AndroidEmulatorResolver(ApplicationProvider.getApplicationContext())
        val cacheTree = DocumentsContract.buildTreeDocumentUri("dev.handheld.launcher.romcache", "cache:abc123")
        val prepared = RomLaunchInput("gba", "gba", DocumentsContract.buildDocumentUriUsingTree(cacheTree, "cache:abc123/Game.gba").toString(), treeUri = cacheTree.toString(), relativePath = "Game.gba")
        assertEquals(cacheTree, resolver.validatedTree(prepared))
        assertNull(resolver.validatedTree(prepared.copy(treeUri = Uri.parse("file:///storage/cache").toString())))
    }

    @Test fun discoveredSharedFolderKeepsVerifiedRetroArchAndCompanionReadGrants() {
        val resolver = AndroidEmulatorResolver(ApplicationProvider.getApplicationContext())
        val shared = DocumentsContract.buildTreeDocumentUri("dev.handheld.launcher.sharedroms", "primary:ROMs/PSX")
        val cue = DocumentsContract.buildDocumentUriUsingTree(shared, "primary:ROMs/PSX/Game/game.cue").toString()
        val track = DocumentsContract.buildDocumentUriUsingTree(shared, "primary:ROMs/PSX/Game/track.bin").toString()
        val game = RomLaunchInput("psx", "cue", cue, treeUri = shared.toString(), relativePath = "Game/game.cue",
            companionUris = listOf(track), coreId = "pcsx_rearmed")
        assertEquals(shared, resolver.validatedTree(game))
        val profile = EmulatorRegistry.profiles.first { it.id == "retroarch-64" }
        val intent = RomIntentFactory.build(profile, game, resolver.validatedTree(game),
            "/data/user/0/com.retroarch.aarch64", "/storage/emulated/0")
        assertEquals(RomLaunchPolicy.retroArchPath(shared.toString(), game.relativePath!!), intent.getStringExtra("ROM"))
        val granted = (0 until intent.clipData!!.itemCount).map { intent.clipData!!.getItemAt(it).uri.toString() }.toSet()
        assertEquals(setOf(cue, track, shared.toString()), granted)
        assertTrue(intent.flags and Intent.FLAG_GRANT_PREFIX_URI_PERMISSION != 0)
        assertEquals(0, intent.flags and (Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION))
    }
}
