package dev.handheld.launcher.rom

import dev.handheld.launcher.core.data.rom.emulator.*
import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionEmulatorLabelTest {
    private val first = InstalledEmulator("first", "example.first", "First emulator", "1",
        setOf("ps2"), setOf("iso"), EmulatorLaunchSupport.DIRECT)
    private val second = first.copy(id = "second", displayName = "Second emulator")

    @Test fun gameOverrideTakesPrecedenceWithoutSilentlyReplacingAnUnavailableChoice() {
        val installed = listOf(first, second)
        assertEquals("Second emulator", collectionEmulatorLabel(installed, second.id, first.id))
        assertEquals("First emulator", collectionEmulatorLabel(installed, null, first.id))
        assertEquals("Saved emulator unavailable", collectionEmulatorLabel(installed, "removed", first.id))
        assertEquals("First emulator · unavailable", collectionEmulatorLabel(
            listOf(first.copy(launchSupport = EmulatorLaunchSupport.UNSUPPORTED), second), null, first.id))
    }

    @Test fun automaticChoiceDistinguishesSoleCompatibleAppFromPromptAndMissingApp() {
        assertEquals("First emulator", collectionEmulatorLabel(listOf(first), null, null))
        assertEquals("Choose when opening", collectionEmulatorLabel(listOf(first, second), null, null))
        assertEquals("No compatible emulator", collectionEmulatorLabel(emptyList(), null, null))
        assertEquals("No compatible emulator", collectionEmulatorLabel(
            listOf(first.copy(launchSupport = EmulatorLaunchSupport.UNSUPPORTED)), null, null))
    }
}
