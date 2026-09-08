package dev.handheld.launcher.core.data.metadata

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ArtworkMatchingTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun `ES-DE sibling headers and archive names resolve without losing metadata`() {
        val games = EsDeGamelist.parse("""<?xml version="1.0"?><alternativeEmulator><label>Standalone</label></alternativeEmulator>
            <gameList><game><path>./nested/Example (USA).7z</path><name>Example &amp; Friends</name>
            <image>./images/example.png</image></game></gameList>""")
        assertEquals(1, games.size)
        assertEquals("Example & Friends", games.single().name)
        assertEquals("nested/Example (USA)", EsDeGamelist.stem(games.single().path))
        assertEquals(EsDeGamelist.stem("nested/Example (USA).gba"), EsDeGamelist.stem(games.single().path))
    }

    @Test fun `external entities are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            EsDeGamelist.parse("<!DOCTYPE gameList [<!ENTITY remote SYSTEM 'file:///private'>]><gameList>&remote;</gameList>")
        }
    }

    @Test fun `gamelist lookup preserves nested identity and rejects ambiguous leaf names`() {
        val first = EsDeGame("./one/Game (USA).gba", "First", null)
        val second = EsDeGame("./two/Game (USA).7z", "Second", null)
        val index = EsDeGamelist.Index(listOf(first, second, EsDeGame("./Unique.gba", "Unique", null)))
        assertEquals(first, index.find("ONE/Game (USA).7z"))
        assertEquals(second, index.find("two/Game (USA).gba"))
        assertNull(index.find("Game (USA).gba"))
        assertEquals("Unique", index.find("nested/Unique.7z")?.name)
    }

    @Test fun `indexes decode filenames and reject paths and remote links`() {
        val names = ThumbnailNames.parseIndex("""<a href="Game%20%2B%20One%20%28USA%29.png">x</a>
            <a href="../private.png">x</a><a href="https://elsewhere/image.png">x</a><a href="Game%20%2B%20One%20%28USA%29.png">x</a>""")
        assertEquals(listOf("Game + One (USA).png"), names)
    }

    @Test fun `matching retains exact region and never substitutes sequels or prototypes`() {
        val names = listOf("Game (Europe).png", "Game (USA).png", "Game (USA) (Proto).png", "Game 2 (USA).png")
        assertEquals("Game (Europe).png", ThumbnailNames.match(listOf("Game (Europe)"), names))
        assertEquals("Game (USA).png", ThumbnailNames.match(listOf("Game"), names))
        assertEquals("Game (USA) (Proto).png", ThumbnailNames.match(listOf("Game (Proto)"), names))
        assertNull(ThumbnailNames.match(listOf("Game 3"), names))
        assertNull(ThumbnailNames.match(listOf("Game (Demo)"), names))
    }

    @Test fun `provider reuses disk index and sends only matched public thumbnail names`() = runBlocking {
        val directory = folder.newFolder()
        val calls = mutableListOf<String>()
        val http = ArtworkHttp { url, _ ->
            calls += url
            if (url.endsWith('/')) "<a href=\"Game%20%28USA%29.png\">Game</a>".toByteArray() else byteArrayOf(1, 2, 3)
        }
        val first = LibretroArtworkProvider(directory, http).find("gba", listOf("Game"))
        assertNotNull(first)
        assertTrue(first!!.url.endsWith("Named_Boxarts/Game%20%28USA%29.png"))
        assertEquals(2, calls.size)
        calls.clear()
        val second = LibretroArtworkProvider(directory, http).find("gba", listOf("Game"))
        assertNotNull(second)
        assertEquals(listOf(first.url), calls)
        assertNull(LibretroArtworkProvider(directory, http).find("unknown", listOf("Game")))
        assertEquals(1, calls.size)
    }

    @Test fun `transient failures propagate instead of poisoning a no-match cache`() = runBlocking {
        val directory = folder.newFolder()
        val provider = LibretroArtworkProvider(directory, ArtworkHttp { _, _ -> throw ArtworkRequestFailure(120_000) })
        var failure: ArtworkRequestFailure? = null
        try { provider.find("gba", listOf("Game")) } catch (error: ArtworkRequestFailure) { failure = error }
        assertEquals(120_000L, failure?.retryAfterMillis)
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }
}
