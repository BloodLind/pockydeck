package dev.handheld.launcher.feature.collection

import dev.handheld.launcher.core.domain.model.Availability
import dev.handheld.launcher.core.domain.model.CatalogSourceId
import dev.handheld.launcher.core.domain.model.CurrentUserAndroidComponentId
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.model.LauncherDestination
import dev.handheld.launcher.core.domain.model.LibraryCategory
import dev.handheld.launcher.core.domain.model.LibraryItem
import dev.handheld.launcher.core.domain.model.UnavailabilityReason
import dev.handheld.launcher.core.domain.model.UserItemOverrides
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CollectionFiltersTest {
    @Test
    fun `console pills share compact card labels while preserving readable unassigned recovery`() {
        assertEquals("GBA", collectionFilterLabel("console:gba"))
        assertEquals("PSX", collectionFilterLabel("console:psx"))
        assertEquals("GCN", collectionFilterLabel("console:gamecube"))
        assertEquals("Unassigned", collectionFilterLabel("console:unassigned"))
    }

    @Test
    fun `Library filters describe games and available consoles only`() {
        val gba = rom("gba", "gba")
        val unassigned = rom("unassigned", null)
        val missing = rom("missing", "psx").copy(
            availability = Availability.Unavailable(UnavailabilityReason.SOURCE_UNAVAILABLE),
        )
        val items = listOf(gba, unassigned, missing, app("game", LibraryCategory.GAME),
            app("emulator", LibraryCategory.EMULATOR), app("app", LibraryCategory.OTHER))
        val overrides = mapOf(gba.id to UserItemOverrides(category = LibraryCategory.EMULATOR))

        assertEquals(listOf("all", "android", "console:gba", "console:unassigned"),
            collectionFilterKeys(LauncherDestination.LIBRARY, items, overrides))
        assertEquals(3, collectionCategoryCount(LibraryCategory.GAME, items, overrides))
        assertEquals(1, collectionCategoryCount(LibraryCategory.EMULATOR, items, overrides))
        assertEquals(1, collectionCategoryCount(LibraryCategory.OTHER, items, overrides))
    }

    @Test
    fun `Library omits Android pill when only non-game Android apps are present`() {
        val gba = rom("gba", "gba")
        val android = app("app", LibraryCategory.OTHER)
        assertEquals(listOf("all", "console:gba"),
            collectionFilterKeys(LauncherDestination.LIBRARY, listOf(gba, android)))
        assertEquals(listOf("all", "android", "console:gba"), collectionFilterKeys(
            LauncherDestination.LIBRARY, listOf(gba, android),
            mapOf(android.id to UserItemOverrides(category = LibraryCategory.GAME)),
        ))
    }

    @Test
    fun `Apps keys and category counts follow effective Android overrides`() {
        val declaredGame = app("declared-game", LibraryCategory.GAME)
        val declaredApp = app("declared-app", LibraryCategory.OTHER)
        val emulator = app("emulator", LibraryCategory.EMULATOR)
        val items = listOf(declaredGame, declaredApp, emulator, rom("gba", "gba"))
        val overrides = mapOf(
            declaredGame.id to UserItemOverrides(category = LibraryCategory.OTHER),
            declaredApp.id to UserItemOverrides(category = LibraryCategory.GAME),
            emulator.id to UserItemOverrides(category = LibraryCategory.GAME),
        )

        assertEquals(listOf("all", "other"), collectionFilterKeys(LauncherDestination.APPS, items, overrides))
        assertEquals(3, collectionCategoryCount(LibraryCategory.GAME, items, overrides))
        assertEquals(0, collectionCategoryCount(LibraryCategory.EMULATOR, items, overrides))
        assertEquals(1, collectionCategoryCount(LibraryCategory.OTHER, items, overrides))
        assertEquals(listOf(declaredGame.id),
            collectionDestinationItems(LauncherDestination.APPS, items, overrides).map { it.id })
    }

    @Test
    fun `Favorites filter choices count only active favorites across consoles and apps`() {
        val gba = rom("gba", "gba")
        val psx = rom("psx", "psx")
        val emulator = app("emulator", LibraryCategory.EMULATOR)
        val missing = app("missing", LibraryCategory.OTHER).copy(
            availability = Availability.Unavailable(UnavailabilityReason.REMOVED),
        )
        val items = listOf(gba, psx, emulator, missing)
        assertEquals(listOf("all"), collectionFilterKeys(LauncherDestination.FAVORITES, items))
        assertEquals(listOf("all", "games"), collectionFilterKeys(LauncherDestination.FAVORITES, items,
            favoriteIds = setOf(gba.id, psx.id, missing.id)))
        assertEquals(listOf("all", "games", "apps"), collectionFilterKeys(LauncherDestination.FAVORITES, items,
            favoriteIds = setOf(gba.id, psx.id, emulator.id)))
        assertEquals(setOf(gba.id, psx.id, emulator.id), collectionDestinationItems(LauncherDestination.FAVORITES, items,
            favoriteIds = setOf(gba.id, psx.id, emulator.id, missing.id)).map { it.id }.toSet())
    }

    @Test
    fun `category route opens the destination that contains its matching items`() {
        val items = listOf(app("game", LibraryCategory.GAME), app("emulator", LibraryCategory.EMULATOR),
            app("other", LibraryCategory.OTHER), rom("gba", "gba"))
        listOf(LibraryCategory.GAME to "all", LibraryCategory.EMULATOR to "emulators", LibraryCategory.OTHER to "other")
            .forEach { (category, filter) ->
                val destination = collectionCategoryDestination(category)
                assertEquals(category == LibraryCategory.GAME, destination == LauncherDestination.LIBRARY)
                assertFalse(collectionDestinationItems(destination, items).filter {
                    collectionFilterMatches(it, filter, emptyMap())
                }.isEmpty())
                assertEquals(true, filter in collectionFilterKeys(destination, items))
            }
    }

    @Test
    fun `Search category filters partition games from non-game apps without hiding any all results`() {
        val items = listOf(app("game", LibraryCategory.GAME), app("emulator", LibraryCategory.EMULATOR),
            app("other", LibraryCategory.OTHER), rom("gba", "gba"))
        val scoped = collectionDestinationItems(LauncherDestination.SEARCH, items)
        assertEquals(items, scoped)
        val games = scoped.filter { collectionFilterMatches(it, "games", emptyMap()) }
        val apps = scoped.filter { collectionFilterMatches(it, "apps", emptyMap()) }
        assertEquals(items.toSet(), (games + apps).toSet())
        assertEquals(emptySet<LibraryItem>(), games.toSet().intersect(apps.toSet()))
        assertEquals(listOf("all", "games", "apps", "system"),
            collectionFilterKeys(LauncherDestination.SEARCH, emptyList()))
    }

    private fun app(id: String, category: LibraryCategory) = LibraryItem.AndroidApp(
        CurrentUserAndroidComponentId("example.$id", "example.$id.Main"), id, category,
        Availability.Available, emptySet(),
    )

    private fun rom(id: String, platform: String?) = LibraryItem.RomGame(
        ItemId("rom:$id"), id, CatalogSourceId("source:rom"), Availability.Available, emptySet(), platformId = platform,
    )
}
