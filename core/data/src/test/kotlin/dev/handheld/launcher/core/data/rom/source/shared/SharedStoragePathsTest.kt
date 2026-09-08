package dev.handheld.launcher.core.data.rom.source.shared

import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Assume.assumeNoException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SharedStoragePathsTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun volumeIdentityUsesReturnedDirectoryAndSurvivesMountPathChange() {
        val original = temporary.newFolder("original").canonicalFile
        val remounted = temporary.newFolder("remounted").canonicalFile
        val relative = "ROMs/GBA/Game.gba"
        File(original,relative).apply { parentFile.mkdirs(); writeBytes(byteArrayOf(1)) }
        val moved = File(remounted,relative).apply { parentFile.mkdirs(); writeBytes(byteArrayOf(2)) }
        var volume = SharedStorageVolume("abcd-1234",original,"SD card")
        val paths = SharedStoragePaths({ true }, { listOf(volume) })
        assertEquals(File(original,relative).canonicalFile,paths.resolve("ABCD-1234:$relative"))
        volume = volume.copy(directory=remounted)
        assertEquals(moved.canonicalFile,paths.resolve("abcd-1234:$relative"))
    }

    @Test fun rejectsTraversalPrivateFoldersAndUnknownVolumesWithoutReadingThem() {
        val root = temporary.newFolder("volume").canonicalFile
        File(root,"Android/data/gba").mkdirs()
        File(root,".private").mkdirs()
        val paths = SharedStoragePaths({ true }, { listOf(SharedStorageVolume("primary",root,"Internal")) })
        listOf("primary:../outside", "primary:/absolute", "primary:a/../../outside", "primary:a\\outside",
            "primary:Android/data/gba", "primary:.private", "other:").forEach { id ->
            assertTrue("Accepted $id", runCatching { paths.resolve(id) }.exceptionOrNull() is IOException)
        }
        assertEquals(root,paths.resolve("primary:"))
    }

    @Test fun revokedPermissionAndDisconnectedVolumeDoNotLookLikeEmptyFolders() {
        val root = temporary.newFolder("volume").canonicalFile
        var granted = true
        var mounted = true
        val paths = SharedStoragePaths({ granted }, { if (mounted) listOf(SharedStorageVolume("primary",root,"Internal")) else emptyList() })
        assertTrue(paths.children("primary:").isEmpty())
        granted = false
        assertTrue(paths.volumes().isEmpty())
        assertTrue(runCatching { paths.children("primary:") }.exceptionOrNull() is SecurityException)
        granted = true
        mounted = false
        assertTrue(runCatching { paths.children("primary:") }.exceptionOrNull() is IOException)
    }

    @Test fun skipsPrivateChildrenAndBoundsLargeDirectoryListings() {
        val root = temporary.newFolder("volume").canonicalFile
        File(root,"GBA").mkdir()
        File(root,"Android").mkdir()
        File(root,".private").mkdir()
        val paths = SharedStoragePaths({ true }, { listOf(SharedStorageVolume("primary",root,"Internal")) })
        assertEquals(listOf("primary:GBA"),paths.children("primary:").map { it.first })
        assertTrue(runCatching { paths.children("primary:",maxEntries=2) }.exceptionOrNull() is IOException)
    }

    @Test fun listingReusesOneVolumeSnapshotInsteadOfEnumeratingVolumesForEveryFile() {
        val root = temporary.newFolder("volume").canonicalFile
        repeat(300) { File(root,"Game$it.gba").writeBytes(byteArrayOf(1)) }
        var volumeReads = 0
        val paths = SharedStoragePaths({ true }, {
            volumeReads++
            listOf(SharedStorageVolume("primary",root,"Internal"))
        })
        assertEquals(300,paths.children("primary:").size)
        assertEquals(1,volumeReads)
    }

    @Test fun rejectsLinkedFilesAndFoldersEvenWhenTheirTargetIsInsideVolume() {
        val root = temporary.newFolder("volume").canonicalFile
        val target = File(root,"real").apply { mkdir() }
        File(target,"Game.gba").writeBytes(byteArrayOf(1))
        val outside = temporary.newFolder("outside").canonicalFile
        try {
            Files.createSymbolicLink(File(root,"alias").toPath(),target.toPath())
            Files.createSymbolicLink(File(root,"escape").toPath(),outside.toPath())
        } catch (error: Exception) { assumeNoException("Host does not permit symlink fixtures",error) }
        val paths = SharedStoragePaths({ true }, { listOf(SharedStorageVolume("primary",root,"Internal")) })
        assertTrue(runCatching { paths.resolve("primary:alias/Game.gba") }.exceptionOrNull() is IOException)
        assertTrue(runCatching { paths.resolve("primary:escape") }.exceptionOrNull() is IOException)
        assertEquals(listOf("primary:real"),paths.children("primary:").map { it.first })
    }
}
