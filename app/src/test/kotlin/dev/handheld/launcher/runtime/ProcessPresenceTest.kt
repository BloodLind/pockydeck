package dev.handheld.launcher.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProcessPresenceTest {
    @Test fun onlyExactCurrentUserPackagesOrTheirChildProcessesAreReported() {
        val requested = setOf("org.ppsspp.ppsspp", "com.retroarch.aarch64", "example.stopped")
        val lines = sequenceOf("UID NAME", "10105 org.ppsspp.ppsspp", "10110 com.retroarch.aarch64:worker",
            "10111 org.ppsspp.ppsspp.fake", "10111 example.stoppedFake", "110111 example.stopped", "0 system_server")
        assertEquals(setOf("org.ppsspp.ppsspp", "com.retroarch.aarch64"), processPresence(lines, requested, 0))
    }

    @Test fun anEmptyLiveReadingDoesNotTurnRoutingHistoryIntoLiveness() {
        assertEquals(emptySet<String>(), processPresence(sequenceOf("UID NAME", "bad output"), setOf("com.retroarch.aarch64"), 0))
    }

    @Test fun candidateNamesCannotBecomeCommandsOrPaths() {
        listOf("", "/system/bin/sh", "a;id", "$(id)", "example.app\nother", "example.app:worker", "../app").forEach {
            assertFalse(validProcessPackage(it))
        }
    }
}
