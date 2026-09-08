package dev.handheld.launcher.core.data.rom

import android.content.Context
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.handheld.launcher.core.data.local.LauncherDatabase
import dev.handheld.launcher.core.data.repository.RoomCatalogRepository
import dev.handheld.launcher.core.data.repository.RoomFavoriteRepository
import dev.handheld.launcher.core.data.repository.RoomItemOverrideRepository
import dev.handheld.launcher.core.data.repository.RoomSuccessfulOpenRepository
import dev.handheld.launcher.core.data.rom.repository.RoomRomLibraryRepository
import dev.handheld.launcher.core.data.rom.repository.ScanSupersededException
import dev.handheld.launcher.core.domain.model.*
import dev.handheld.launcher.core.domain.rom.RomEntry
import dev.handheld.launcher.core.domain.rom.RomSource
import dev.handheld.launcher.core.domain.rom.RomSourceStatus
import dev.handheld.launcher.core.domain.rom.RomSourceAccessKind
import dev.handheld.launcher.core.data.rom.source.shared.SharedDocumentId
import dev.handheld.launcher.core.data.rom.source.shared.SharedDiscoveryPolicy
import dev.handheld.launcher.core.domain.rom.scan.*
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomRomLibraryInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: LauncherDatabase
    private lateinit var repository: RoomRomLibraryRepository
    private lateinit var catalog: RoomCatalogRepository
    private lateinit var databaseName: String

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "rom-integrity-${System.nanoTime()}.db"
        database = LauncherDatabase.open(context, databaseName)
        repository = RoomRomLibraryRepository(database)
        catalog = RoomCatalogRepository(database)
    }

    @After fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test fun sourceRemovalReaddAndRenameRetainIdentityFavoritesArtworkAndHistory() = runBlocking {
        val source = source("one")
        val original = scan(source, doc("opaque-one", "GBA/First.gba")).single()
        RoomFavoriteRepository(database).setFavorite(original.itemId, true)
        val override = UserItemOverrides(artworkReference = UserArtworkReference("user-art:game"))
        RoomItemOverrideRepository(database).setOverrides(original.itemId, override)
        RoomSuccessfulOpenRepository(database).recordOnce(SuccessfulOpenCandidate(LaunchOperationId("rom-open"), original.itemId))
        repository.setItemEmulator(original.itemId, "emulator:test")

        repository.removeSource(source.id)
        assertFalse(requireNotNull(repository.findSource(source.id)).enabled)
        assertEquals(Availability.Unavailable(UnavailabilityReason.SOURCE_UNAVAILABLE), catalog.findItem(original.itemId)?.availability)
        val readded = repository.addSource(source.treeUri, source.rootDocumentId, source.name)
        assertEquals(source.id, readded)
        assertEquals(original.itemId, scan(requireNotNull(repository.findSource(readded)), doc("opaque-one", "GBA/Renamed.gba")).single().itemId)

        assertEquals("Renamed", catalog.findItem(original.itemId)?.title)
        assertEquals(Availability.Available, catalog.findItem(original.itemId)?.availability)
        assertEquals(setOf(original.itemId), RoomFavoriteRepository(database).favoriteItemIds.first())
        assertEquals(override, RoomItemOverrideRepository(database).overridesByItemId.first()[original.itemId])
        assertEquals(original.itemId, RoomSuccessfulOpenRepository(database).records.first().single().itemId)
        assertEquals("emulator:test", repository.itemEmulatorOverrides.first()[original.itemId])
        assertEquals(1, repository.sources.first().size)
    }

    @Test fun sameFilenameAndOpaqueIdInDifferentSourcesNeverMerge() = runBlocking {
        val first = scan(source("one"), doc("shared-document-id", "GBA/Game.gba")).single()
        val second = scan(source("two"), doc("shared-document-id", "GBA/Game.gba")).single()
        assertNotEquals(first.itemId, second.itemId)
        assertEquals(2, catalog.snapshot.first().activeItems.size)
        repository.removeSource(first.sourceId)
        assertEquals(listOf(second.itemId), catalog.snapshot.first().activeItems.map { it.id })
    }

    @Test fun onlyCompletedEnumerationMarksAbsentGamesMissing() = runBlocking {
        val source = source("one")
        val original = scan(source, doc("one", "GBA/One.gba"), doc("two", "GBA/Two.gba"))
        val revision = requireNotNull(repository.beginScan(source.id))
        repository.failScan(source.id, revision, "Fixture provider interrupted", unavailable = false)
        assertEquals(2, catalog.snapshot.first().activeItems.size)
        assertTrue(repository.entries.first().all { it.present })

        val cancelledRevision = requireNotNull(repository.beginScan(source.id))
        val cancelled = launch(start = CoroutineStart.UNDISPATCHED) {
            currentCoroutineContext().cancel()
            repository.commitScan(source, cancelledRevision, emptyList(), RomScanPlan(emptyList(), emptyList(), emptyList()))
        }
        cancelled.join()
        repository.failScan(source.id, cancelledRevision, "Fixture cancellation", unavailable = false)
        assertTrue(cancelled.isCancelled)
        assertTrue(repository.entries.first().all { it.present })

        scan(source, doc("one", "GBA/One.gba"))
        val omitted = original.single { it.documentId == "two" }
        assertFalse(requireNotNull(repository.findEntry(omitted.itemId)).present)
        assertEquals(Availability.Unavailable(UnavailabilityReason.REMOVED), catalog.findItem(omitted.itemId)?.availability)
        assertEquals(RomSourceStatus.READY, repository.findSource(source.id)?.status)
    }

    @Test fun unavailableSourceCannotBeMadeReadableByConsoleCorrection() = runBlocking {
        val source = source("one")
        val entry = scan(source, doc("one", "Game.iso")).single()
        val revision = requireNotNull(repository.beginScan(source.id))
        repository.failScan(source.id, revision, "Storage disconnected", unavailable = true)
        repository.setItemPlatform(entry.itemId, "ps2")

        assertEquals("ps2", repository.findEntry(entry.itemId)?.platformId)
        assertEquals(RomSourceStatus.UNAVAILABLE, repository.findSource(source.id)?.status)
        assertEquals(Availability.Unavailable(UnavailabilityReason.SOURCE_UNAVAILABLE), catalog.findItem(entry.itemId)?.availability)
        assertTrue(catalog.snapshot.first().activeItems.isEmpty())
    }

    @Test fun removalInvalidatesInFlightScanWithoutReactivatingRows() = runBlocking {
        val source = source("one")
        val entry = scan(source, doc("one", "GBA/One.gba")).single()
        val revision = requireNotNull(repository.beginScan(source.id))
        repository.removeSource(source.id)
        var superseded = false
        try {
            val docs = listOf(doc("one", "GBA/New title.gba"))
            repository.commitScan(source, revision, docs, plan(source, docs))
        } catch (_: ScanSupersededException) { superseded = true }
        assertTrue(superseded)
        assertFalse(requireNotNull(repository.findSource(source.id)).enabled)
        assertEquals("One", catalog.findItem(entry.itemId)?.title)
        assertTrue(catalog.snapshot.first().activeItems.isEmpty())
    }

    @Test fun userAssignmentDuringEnumerationSurvivesRescanAndDatabaseReopen() = runBlocking {
        val source = source("one")
        val docs = listOf(doc("one", "Game.iso"))
        val entry = scan(source, *docs.toTypedArray()).single()
        val revision = requireNotNull(repository.beginScan(source.id))
        val stalePlan = plan(source, docs)
        repository.setItemPlatform(entry.itemId, "ps2")
        assertTrue(runCatching { repository.commitScan(source, revision, docs, stalePlan) }.exceptionOrNull() is ScanSupersededException)
        val current = requireNotNull(repository.findSource(source.id))
        val corrected = RomScanPlanner().plan(RomScanRequest(docs, current.name, current.defaultPlatformId,
            documentPlatformOverrides=repository.itemPlatformAssignments(source.id)))
        repository.commitScan(current, requireNotNull(repository.beginScan(source.id)), docs, corrected)
        repository.setConsoleEmulator("ps2", "emulator:ps2")
        repository.setConsoleCore("ps2", "core:ps2")
        repository.setCacheLimit(4 * RoomRomLibraryRepository.GIB)

        database.close()
        database = LauncherDatabase.open(context, databaseName)
        repository = RoomRomLibraryRepository(database)
        catalog = RoomCatalogRepository(database)
        assertEquals("ps2", repository.findEntry(entry.itemId)?.platformId)
        assertEquals("ps2", (catalog.findItem(entry.itemId) as LibraryItem.RomGame).platformId)
        assertEquals("emulator:ps2", repository.consoleEmulatorDefaults.first()["ps2"])
        assertEquals("core:ps2", repository.consoleCores.first()["ps2"])
        assertEquals(4 * RoomRomLibraryRepository.GIB, repository.cacheLimitBytes.first())
    }

    @Test fun completedDependencyBatchesPublishBeforeCompletionAndCancellationKeepsOmissions() = runBlocking {
        val source = source("one")
        val retained = scan(source, doc("retained", "GBA/Retained.gba")).single()
        val documents = (1..260).map { doc("game:$it", "GBA/Game $it.gba") }
        val revision = requireNotNull(repository.beginScan(source.id))
        var published = 0
        val error = runCatching {
            repository.commitScan(source, revision, documents, plan(source,documents)) { count ->
                published = count
                assertEquals(129, repository.entries.first().count { it.present })
                throw kotlinx.coroutines.CancellationException("Fixture stops after first committed batch")
            }
        }.exceptionOrNull()
        assertTrue(error is kotlinx.coroutines.CancellationException)
        assertEquals(128, published)
        repository.failScan(source.id,revision,"Interrupted after a safe batch",unavailable=false)
        assertTrue(requireNotNull(repository.findEntry(retained.itemId)).present)
        assertEquals(129, catalog.snapshot.first().activeItems.size)
        assertEquals(RomSourceStatus.ERROR,repository.findSource(source.id)?.status)
    }

    @Test fun unchangedRescanAvoidsCatalogRewritesButRestoresUnavailableProjection() = runBlocking {
        val source = source("one")
        val documents = arrayOf(doc("game", "GBA/Game.gba"))
        val original = scan(source,*documents).single()
        val sql = database.openHelper.writableDatabase
        sql.execSQL("CREATE TABLE catalog_write_counter (writes INTEGER NOT NULL)")
        sql.execSQL("INSERT INTO catalog_write_counter VALUES (0)")
        sql.execSQL("CREATE TRIGGER count_catalog_updates AFTER UPDATE ON catalog_items BEGIN UPDATE catalog_write_counter SET writes=writes+1; END")
        scan(source,*documents)
        sql.query("SELECT writes FROM catalog_write_counter").use { cursor -> cursor.moveToFirst(); assertEquals(0,cursor.getInt(0)) }
        repository.failScan(source.id,requireNotNull(repository.beginScan(source.id)),"Disconnected",unavailable=true)
        assertTrue(catalog.snapshot.first().activeItems.isEmpty())
        val recovered = scan(source,*documents).single()
        assertEquals(original.itemId,recovered.itemId)
        assertEquals(listOf(original.itemId),catalog.snapshot.first().activeItems.map { it.id })
    }

    @Test fun validatedGroupHidesOldStandaloneTracksInItsOwnCommittedBatch() = runBlocking {
        val source = source("one")
        val track = doc("track","PSX/Game.bin")
        val old = scan(source,track).single()
        val cue = doc("cue","PSX/Game.cue")
        val documents = listOf(track,cue)
        val grouped = RomScanPlanner().plan(RomScanRequest(documents,descriptorText=mapOf("cue" to
            "FILE \"Game.bin\" BINARY\nTRACK 01 MODE2/2352\nINDEX 01 00:00:00")))
        val error = runCatching {
            repository.commitScan(source,requireNotNull(repository.beginScan(source.id)),documents,grouped) {
                assertFalse(requireNotNull(repository.findEntry(old.itemId)).present)
                assertEquals(listOf("cue"),repository.entries.first().filter { it.present }.map { it.documentId })
                throw kotlinx.coroutines.CancellationException("Stop after group publication")
            }
        }.exceptionOrNull()
        assertTrue(error is kotlinx.coroutines.CancellationException)
        assertNotNull(repository.findEntry(old.itemId))
        assertEquals(1,catalog.snapshot.first().activeItems.size)
    }

    @Test fun sourceCountsSeparateUnidentifiedEntriesWithoutDeletingTheirIdentities() = runBlocking {
        val source = source("one")
        val scanned = scan(source,doc("gba","GBA/Known.gba"),doc("iso","Unknown.iso"))
        val state = repository.sources.first().single()
        assertEquals(1,state.gameCount)
        assertEquals(1,state.unidentifiedCount)
        val unknown = scanned.single { it.documentId == "iso" }
        repository.setItemPlatform(unknown.itemId,"ps2")
        assertEquals(2,repository.sources.first().single().gameCount)
        assertEquals(0,repository.sources.first().single().unidentifiedCount)
        assertEquals(unknown.itemId,repository.findEntry(unknown.itemId)?.itemId)
    }

    @Test fun failedLaterBatchKeepsCompletedBatchAndNeverMarksOldRowsMissing() = runBlocking {
        val source = source("one")
        val original = scan(source, doc("original", "GBA/Original.gba")).single()
        database.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER rom_fixture_abort BEFORE INSERT ON rom_documents
            WHEN NEW.title = 'Stop here' BEGIN SELECT RAISE(ABORT, 'fixture batch failure'); END
        """.trimIndent())
        val docs = (1..129).map { doc("new-$it", "GBA/New$it.gba") }
        val entries = docs.mapIndexed { index, document ->
            PlannedRomEntry(document.documentId, document.relativePath, if (index == 128) "Stop here" else "New $index", "gba", "gba", RomEntryKind.SINGLE_FILE)
        }
        val revision = requireNotNull(repository.beginScan(source.id))
        var failed = false
        try {
            repository.commitScan(source, revision, docs, RomScanPlan(entries, emptyList(), emptyList()))
        } catch (_: Exception) { failed = true }
        assertTrue(failed)
        repository.failScan(source.id, revision, "Fixture batch failed", unavailable = false)

        assertTrue(requireNotNull(repository.findEntry(original.itemId)).present)
        assertEquals(129, catalog.snapshot.first().activeItems.size)
        assertEquals(129, repository.entries.first().count { it.present })
        assertTrue(repository.entries.first().none { it.documentId == "new-129" })
        assertTrue(catalog.snapshot.first().activeItems.all { SupportedItemAction.OPEN in it.supportedActions })
        assertEquals(RomSourceStatus.ERROR, repository.findSource(source.id)?.status)
    }

    @Test fun discoveryCannotResurrectRemovedSourceAndExplicitRestoreKeepsIdentityReferences() = runBlocking {
        val source = sharedSource("primary:GBA","GBA","gba")
        val document = doc("primary:GBA/Game.gba","Game.gba")
        val original = scan(source,document).single()
        RoomFavoriteRepository(database).setFavorite(original.itemId,true)
        RoomSuccessfulOpenRepository(database).recordOnce(SuccessfulOpenCandidate(LaunchOperationId("shared-open"),original.itemId))
        repository.removeSource(source.id)

        assertNull(repository.upsertDiscoveredSource(source.treeUri,source.rootDocumentId,source.name,"gba"))
        assertFalse(requireNotNull(repository.findSource(source.id)).enabled)
        assertTrue(catalog.snapshot.first().activeItems.isEmpty())
        repository.restoreSource(source.id)
        val restored = scan(requireNotNull(repository.findSource(source.id)),document).single()
        assertEquals(original.itemId,restored.itemId)
        assertEquals(setOf(original.itemId),RoomFavoriteRepository(database).favoriteItemIds.first())
        assertEquals(original.itemId,RoomSuccessfulOpenRepository(database).records.first().single().itemId)
    }

    @Test fun manualExactRootTakesAutomaticSourceWithoutChangingGameOrSourceIdentity() = runBlocking {
        val source = sharedSource("primary:ROMs/GBA","GBA","gba")
        val original = scan(source,doc("primary:ROMs/GBA/Game.gba","Game.gba")).single()
        RoomFavoriteRepository(database).setFavorite(original.itemId,true)
        val manualUri = DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents",source.rootDocumentId).toString()
        val manualId = repository.addSource(manualUri,source.rootDocumentId,"GBA")

        assertEquals(source.id,manualId)
        val manual = requireNotNull(repository.findSource(manualId))
        assertEquals(RomSourceAccessKind.SAF,manual.accessKind)
        assertFalse(manual.automaticallyDiscovered)
        assertEquals("primary:ROMs/GBA",manual.physicalRootKey)
        assertEquals(original.itemId,repository.entries.first().single().itemId)
        assertTrue(repository.entries.first().single().documentUri.startsWith("content://com.android.externalstorage.documents/"))
        assertEquals(setOf(original.itemId),RoomFavoriteRepository(database).favoriteItemIds.first())
        assertNull(repository.upsertDiscoveredSource(source.treeUri,source.rootDocumentId,source.name,"gba"))
    }

    @Test fun manualAncestorAbsorbsAutomaticGamesAndRetainsTheirUserReferencesAcrossRescan() = runBlocking {
        val automatic = sharedSource("primary:ROMs/GBA","GBA","gba")
        val original = scan(automatic,doc("primary:ROMs/GBA/Game.gba","Game.gba")).single()
        val artwork = UserItemOverrides(artworkReference=UserArtworkReference("user-art:shared"))
        RoomItemOverrideRepository(database).setOverrides(original.itemId,artwork)
        repository.setItemEmulator(original.itemId,"emulator:fixture")
        val manualUri = DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents","primary:ROMs").toString()
        val manualId = repository.addSource(manualUri,"primary:ROMs","ROMs")
        val moved = requireNotNull(repository.findEntry(original.itemId))
        assertEquals(manualId,moved.sourceId)
        assertEquals("GBA/Game.gba",moved.relativePath)
        assertFalse(requireNotNull(repository.findSource(automatic.id)).enabled)
        assertEquals(1,catalog.snapshot.first().activeItems.size)

        scan(requireNotNull(repository.findSource(manualId)),doc("primary:ROMs/GBA/Game.gba","GBA/Game.gba"))
        assertEquals(original.itemId,repository.entries.first().single().itemId)
        assertEquals(artwork,RoomItemOverrideRepository(database).overridesByItemId.first()[original.itemId])
        assertEquals("emulator:fixture",repository.itemEmulatorOverrides.first()[original.itemId])
        repository.removeSource(manualId)
        assertNull(repository.upsertDiscoveredSource(automatic.treeUri,automatic.rootDocumentId,automatic.name,"gba"))
    }

    @Test fun manualChildTransfersOnlyItsGamesAndAutomaticParentKeepsSiblingIdentity() = runBlocking {
        val automatic = sharedSource("primary:GBA","GBA","gba")
        val entries = scan(automatic,doc("primary:GBA/Sibling.gba","Sibling.gba"),doc("primary:GBA/Manual/Owned.gba","Manual/Owned.gba"))
        val owned = entries.single { it.title == "Owned" }
        val sibling = entries.single { it.title == "Sibling" }
        val manualUri = DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents","primary:GBA/Manual").toString()
        val manualId = repository.addSource(manualUri,"primary:GBA/Manual","Manual")
        assertEquals(manualId,repository.findEntry(owned.itemId)?.sourceId)
        assertEquals("Owned.gba",repository.findEntry(owned.itemId)?.relativePath)
        assertEquals(automatic.id,repository.findEntry(sibling.itemId)?.sourceId)
        assertTrue(requireNotNull(repository.findSource(automatic.id)).enabled)
        assertEquals(setOf("primary:GBA/Manual"),SharedDiscoveryPolicy.exclusions(
            requireNotNull(repository.findSource(automatic.id)),repository.allSources(),
        ))
        scan(requireNotNull(repository.findSource(automatic.id)),doc("primary:GBA/Sibling.gba","Sibling.gba"))
        assertEquals(setOf(owned.itemId,sibling.itemId),catalog.snapshot.first().activeItems.map { it.id }.toSet())
    }

    @Test fun discoveryPreferenceDefaultsOnAndSurvivesReopenWithoutDisablingRegisteredSources() = runBlocking {
        val source = sharedSource("primary:GBA","GBA","gba")
        assertTrue(repository.sharedDiscoveryEnabled.first())
        repository.setSharedDiscoveryEnabled(false)
        database.close()
        database = LauncherDatabase.open(context,databaseName)
        repository = RoomRomLibraryRepository(database)
        catalog = RoomCatalogRepository(database)
        assertFalse(repository.sharedDiscoveryEnabled.first())
        assertTrue(requireNotNull(repository.findSource(source.id)).enabled)
    }

    @Test fun opaqueProviderAndAutomaticSourcesCannotCreateAnUnverifiableDuplicateCatalog() = runBlocking {
        val manual = source("opaque")
        val tree = DocumentsContract.buildTreeDocumentUri(SharedDocumentId.AUTHORITY,"primary:GBA").toString()
        assertNull(repository.upsertDiscoveredSource(tree,"primary:GBA","GBA","gba"))
        repository.removeSource(manual.id)
        val automatic = sharedSource("primary:GBA","GBA","gba")
        assertTrue(runCatching { repository.addSource(manual.treeUri,manual.rootDocumentId,manual.name) }.isFailure)
        assertTrue(requireNotNull(repository.findSource(automatic.id)).enabled)
        assertFalse(requireNotNull(repository.findSource(manual.id)).enabled)
    }

    @Test fun staleAutomaticScanCannotPublishAfterManualOwnershipTransfer() = runBlocking {
        val automatic = sharedSource("primary:GBA","GBA","gba")
        val docs = listOf(doc("primary:GBA/Game.gba","Game.gba"))
        val entry = scan(automatic,*docs.toTypedArray()).single()
        val revision = requireNotNull(repository.beginScan(automatic.id))
        val manualTree = DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents","primary:GBA").toString()
        repository.addSource(manualTree,"primary:GBA","GBA")
        val error = runCatching { repository.commitScan(automatic,revision,docs,plan(automatic,docs)) }.exceptionOrNull()
        assertTrue(error is ScanSupersededException)
        assertEquals(entry.itemId,repository.entries.first().single().itemId)
        assertTrue(repository.entries.first().single().documentUri.startsWith("content://com.android.externalstorage.documents/"))
    }

    @Test fun savedDiscoveryContinuationKeepsCompletedVolumeBaselineUntilCycleCompletes() = runBlocking {
        repository.saveDiscoveryCursor(listOf("sd-1:ROMs/GBA"),setOf("primary","sd-1"))
        database.close()
        database = LauncherDatabase.open(context,databaseName)
        repository = RoomRomLibraryRepository(database)
        catalog = RoomCatalogRepository(database)
        assertEquals(listOf("sd-1:ROMs/GBA"),repository.discoveryCursor())
        assertEquals(setOf("primary","sd-1"),repository.discoveryVolumeKeys())
        repository.saveDiscoveryCursor(emptyList(),setOf("primary","sd-1"))
        assertTrue(repository.discoveryCursor().isEmpty())
        assertTrue(repository.discoveryVolumeKeys().isEmpty())
    }

    private suspend fun sharedSource(root: String, name: String, platform: String): RomSource {
        val tree = DocumentsContract.buildTreeDocumentUri(SharedDocumentId.AUTHORITY,root).toString()
        val id = requireNotNull(repository.upsertDiscoveredSource(tree,root,name,platform))
        return requireNotNull(repository.findSource(id))
    }

    private suspend fun source(suffix: String): RomSource {
        val id = repository.addSource("content://dev.fixture.documents/tree/$suffix", suffix, "ROMs $suffix")
        return requireNotNull(repository.findSource(id))
    }

    private suspend fun scan(source: RomSource, vararg documents: RomDocument): List<RomEntry> {
        val current = requireNotNull(repository.findSource(source.id))
        repository.commitScan(current, requireNotNull(repository.beginScan(source.id)), documents.toList(), plan(current, documents.toList()))
        return repository.entries.first().filter { it.sourceId == source.id && it.present }
    }

    private fun plan(source: RomSource, docs: List<RomDocument>) = RomScanPlanner().plan(
        RomScanRequest(docs, source.name, source.defaultPlatformId),
    )
    private fun doc(id: String, path: String) = RomDocument(id, path, sizeBytes = 16L)
}
