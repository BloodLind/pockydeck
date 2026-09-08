package dev.handheld.launcher.core.data.rom.source.shared

import org.junit.Assert.*
import org.junit.Test

class SharedDiscoveryContinuationTest {
    @Test fun completedVolumeDoesNotRestartWhileAnotherVolumeStillHasPendingDirectories() {
        val continued = SharedDiscoveryContinuation.resume(listOf("sd-1:Deep/Games"),setOf("primary","sd-1"),setOf("primary","sd-1"))
        assertEquals(listOf("sd-1:Deep/Games"),continued.pending)
        assertFalse("primary:" in continued.pending)
    }

    @Test fun newlyMountedVolumeIsAddedOnceWithoutDiscardingExistingContinuation() {
        val first = SharedDiscoveryContinuation.resume(listOf("primary:Games"),setOf("primary"),setOf("primary","sd-1"))
        assertEquals(listOf("sd-1:","primary:Games"),first.pending)
        val next = SharedDiscoveryContinuation.resume(first.pending,first.knownVolumeKeys,setOf("primary","sd-1"))
        assertEquals(first.pending,next.pending)
    }

    @Test fun disconnectedPendingVolumeCanFinishCycleWithoutRediscoveringAlreadyCompletedVolumes() {
        val continued = SharedDiscoveryContinuation.resume(listOf("sd-1:Deep/Games"),setOf("primary","sd-1"),setOf("primary"))
        assertTrue(continued.pending.isEmpty())
        val newCycle = SharedDiscoveryContinuation.resume(emptyList(),emptySet(),setOf("primary","sd-1"))
        assertEquals(listOf("primary:","sd-1:"),newCycle.pending)
    }

    @Test fun remountedVolumeRequeuesDuringContinuingCycleWithoutRestartingCompletedMountedVolume() {
        val disconnected = SharedDiscoveryContinuation.resume(
            listOf("primary:Deep/Games","sd-1:ROMs"),
            setOf("primary","sd-1","usb-1"),
            setOf("primary","usb-1"),
        )
        assertEquals(listOf("primary:Deep/Games"),disconnected.pending)
        assertEquals(setOf("primary","usb-1"),disconnected.knownVolumeKeys)

        val remounted = SharedDiscoveryContinuation.resume(
            disconnected.pending,disconnected.knownVolumeKeys,setOf("primary","sd-1","usb-1"),
        )
        assertEquals(listOf("sd-1:","primary:Deep/Games"),remounted.pending)
        assertFalse("usb-1:" in remounted.pending)
    }
}
