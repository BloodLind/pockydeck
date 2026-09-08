package dev.handheld.launcher.launch

import dev.handheld.launcher.core.domain.model.Availability
import dev.handheld.launcher.core.domain.model.CatalogSourceId
import dev.handheld.launcher.core.domain.model.CurrentUserAndroidComponentId
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.model.LibraryCategory
import dev.handheld.launcher.core.domain.model.LibraryItem
import dev.handheld.launcher.core.domain.model.SupportedItemAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentActivityPolicyTest {
    private val now = 50_000_000L
    private val first = rom("first")
    private val second = rom("second")
    private val app = app("fixture.app")

    @Test fun appActivityExpiresAtThirtyMinutesAndNeverAcceptsFutureClockValues() {
        val inside = RecentActivitySnapshot(appLastActive = mapOf("fixture.app" to now - RecentActivityPolicy.WINDOW_MILLIS + 1))
        assertEquals(mapOf(app.id to "Recently active"), inside.labels(listOf(app), now))
        assertTrue(inside.labels(listOf(app), now + 1).isEmpty())
        assertTrue(RecentActivitySnapshot(appLastActive = mapOf("fixture.app" to now + 1)).labels(listOf(app), now).isEmpty())
        assertFalse(RecentActivityPolicy.isRecent(Long.MIN_VALUE, Long.MAX_VALUE))
    }

    @Test fun permissionRevocationRemovesQueriedOnlyAppsButKeepsExplicitLaunches() {
        val other = app("fixture.other")
        val local = RecentActivityPolicy.recordApp(RecentActivityHistory(), "fixture.app", now)
        val granted = RecentActivityPolicy.snapshot(local, mapOf("fixture.other" to now), true, now)
        assertEquals(2, granted.labels(listOf(app, other), now).size)
        val revoked = RecentActivityPolicy.snapshot(local, mapOf("fixture.other" to now), false, now)
        assertFalse(revoked.usageAccessGranted)
        assertEquals(mapOf(app.id to "Recently active"), revoked.labels(listOf(app, other), now))
    }

    @Test fun onlyMostRecentlyLaunchedRomPerEmulatorHasAnHonestLabel() {
        val initial = RecentActivityPolicy.recordRom(RecentActivityHistory(), record(first, "emulator.one", now - 1))
        val next = RecentActivityPolicy.recordRom(initial, record(second, "emulator.one", now))
        val labels = RecentActivityPolicy.snapshot(next, emptyMap(), false, now).labels(listOf(first, second), now)
        assertEquals(mapOf(second.id to "Test emulator · Recently active"), labels)
        assertTrue(labels.values.none { "Running" in it })
        assertEquals(second.id, next.roms.getValue("emulator.one").itemId)
    }

    @Test fun subsequentEmulatorUsageDoesNotExtendOrInferRomRecency() {
        val past = now - RecentActivityPolicy.WINDOW_MILLIS
        val local = RecentActivityHistory(roms = mapOf("emulator.one" to record(first, "emulator.one", past)))
        val observed = RecentActivityPolicy.snapshot(local, mapOf("emulator.one" to now), true, now)
        assertTrue(observed.labels(listOf(first), now).isEmpty())
        assertEquals("Recently active", observed.labels(listOf(app("emulator.one")), now).values.single())
    }

    @Test fun independentEmulatorsKeepTheirOwnLastRomAndLateWritesCannotReplaceNewerLaunches() {
        var local = RecentActivityPolicy.recordRom(RecentActivityHistory(), record(first, "emulator.one", now))
        local = RecentActivityPolicy.recordRom(local, record(second, "emulator.two", now))
        local = RecentActivityPolicy.recordRom(local, record(second, "emulator.one", now - 1), now)
        val labels = RecentActivityPolicy.snapshot(local, emptyMap(), false, now).labels(listOf(first, second), now)
        assertEquals(setOf(first.id, second.id), labels.keys)
        assertEquals(first.id, local.roms.getValue("emulator.one").itemId)
    }

    @Test fun pruningAfterRestartDropsExpiredOrFutureHistoryAndBoundsRecentEntries() {
        val apps = (0..250).associate { "fixture.$it" to now - it } + mapOf(
            "expired" to now - RecentActivityPolicy.WINDOW_MILLIS, "future" to now + 1,
        )
        val roms = (0..40).associate { "emulator.$it" to record(first, "emulator.$it", now - it) }
        val restored = RecentActivityPolicy.prune(RecentActivityHistory(apps, roms), now)
        assertEquals(200, restored.apps.size)
        assertEquals(32, restored.roms.size)
        assertTrue("fixture.0" in restored.apps)
        assertTrue("fixture.250" !in restored.apps && "future" !in restored.apps && "expired" !in restored.apps)
        val unavailable = app.copy(availability = Availability.Unavailable(dev.handheld.launcher.core.domain.model.UnavailabilityReason.REMOVED))
        assertTrue(RecentActivitySnapshot(appLastActive = mapOf("fixture.app" to now)).labels(listOf(unavailable), now).isEmpty())
    }

    private fun record(item: LibraryItem.RomGame, packageName: String, at: Long) =
        RecentRomActivity(item.id, packageName, "Test emulator", at)

    private fun rom(name: String) = LibraryItem.RomGame(ItemId("rom:$name"), name, CatalogSourceId("fixture:roms"),
        Availability.Available, setOf(SupportedItemAction.OPEN), "gba", "gba")

    private fun app(name: String) = LibraryItem.AndroidApp(CurrentUserAndroidComponentId(name, "$name.Main"), name,
        LibraryCategory.OTHER, Availability.Available, setOf(SupportedItemAction.OPEN))
}
