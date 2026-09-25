package dev.handheld.launcher.audio

import android.media.AudioManager
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.handheld.launcher.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/** Isolated native playback checks work even when the Android mixer is muted; no preference or volume changes. */
@RunWith(AndroidJUnit4::class)
class ControllerSoundEffectsDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun mutedSoundsStillVibrateOnlyAtBurstEdgesAndCancelPendingEndOnDisableOrPause() {
        var vibrationEnabled = true
        val pulses = mutableListOf<ControllerSoundCue>()
        lateinit var sounds: ControllerSoundEffects
        instrumentation.runOnMainSync {
            sounds = ControllerSoundEffects(context, hapticsEnabled = { vibrationEnabled },
                hapticFeedback = { cue ->
                    assertEquals(android.os.Looper.getMainLooper(), android.os.Looper.myLooper())
                    pulses += cue
                    true
                }, enabled = { false })
            sounds.setActive(true)
            sounds.onRepeatHoldChanged(true)
            sounds.dispatch(dev.handheld.launcher.contract.SemanticInputAction.NAVIGATE_RIGHT) { true }
            assertEquals(1, pulses.size)
        }
        try {
            SystemClock.sleep(700) // No ending pulse during a held trigger's initial repeat delay.
            repeat(20) {
                instrumentation.runOnMainSync {
                    sounds.dispatch(dev.handheld.launcher.contract.SemanticInputAction.NAVIGATE_RIGHT) { true }
                    sounds.onItemSelected() // The later focus callback is audio-only, never another pulse.
                    assertEquals("A held burst has no middle vibrations", 1, pulses.size)
                }
                SystemClock.sleep(55)
            }
            instrumentation.runOnMainSync { sounds.onRepeatHoldChanged(false) }
            SystemClock.sleep(240)
            instrumentation.runOnMainSync {
                assertEquals("The repeated burst ends with one pulse", 2, pulses.size)
                sounds.dispatch(dev.handheld.launcher.contract.SemanticInputAction.CONFIRM) { false }
                assertEquals("Unhandled actions stay silent", 2, pulses.size)
                repeat(20) { sounds.onTouchActivation() }
                assertEquals("Rapid touch clicks also have one start pulse", 3, pulses.size)
            }
            SystemClock.sleep(240)
            instrumentation.runOnMainSync {
                assertEquals(4, pulses.size)
                sounds.onTouchActivation()
                sounds.onTouchActivation()
                vibrationEnabled = false
                sounds.cancelHaptics()
                vibrationEnabled = true
            }
            SystemClock.sleep(240)
            instrumentation.runOnMainSync {
                assertEquals("Disable/re-enable cannot revive the ending pulse", 5, pulses.size)
                sounds.onTouchActivation()
                sounds.onTouchActivation()
                sounds.setActive(false)
                sounds.setActive(true)
            }
            SystemClock.sleep(240)
            instrumentation.runOnMainSync {
                assertEquals("Pause/resume cannot revive the ending pulse", 6, pulses.size)
                sounds.onTouchActivation()
            }
            SystemClock.sleep(240)
            instrumentation.runOnMainSync { assertEquals("One isolated action gives just one pulse", 7, pulses.size) }
        } finally { instrumentation.runOnMainSync { sounds.release() } }
    }

    @Test fun fixedVolumeRepeatsAreQuieterThanTheIsolatedClickWithoutGainPumping() {
        val audio = requireNotNull(context.getSystemService(AudioManager::class.java))
        val systemVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val systemMuted = audio.isStreamMute(AudioManager.STREAM_MUSIC)
        val starts = java.util.concurrent.ConcurrentLinkedQueue<Pair<Long, Float>>()
        lateinit var sounds: ControllerSoundEffects
        instrumentation.runOnMainSync {
            sounds = ControllerSoundEffects(context, volumePercent = { 40 },
                onSampleStarted = { _, gain -> starts += SystemClock.uptimeMillis() to gain }, enabled = { true })
            sounds.prepare()
            sounds.setActive(true)
        }
        try {
            val deadline = SystemClock.uptimeMillis() + 5_000
            while (starts.isEmpty() && SystemClock.uptimeMillis() < deadline) {
                instrumentation.runOnMainSync { sounds.onItemSelected() }
                SystemClock.sleep(10)
            }
            assertTrue("The native sample must start", starts.isNotEmpty())
            // Cross the former 80ms gate, then sustain the actual 55ms maximum hold rate.
            val intervals = listOf(360L) + (115 downTo 55 step 3).map(Int::toLong) + List(36) { 55L }
            for (interval in intervals) {
                SystemClock.sleep(interval)
                instrumentation.runOnMainSync { sounds.onItemSelected() }
            }
            SystemClock.sleep(100)
            val played = starts.toList()
            assertTrue("Accelerated input must not halve the audible rate", played.size >= (intervals.size + 1) * .95)
            played.drop(1).forEach { (_, gain) ->
                assertEquals("Every repeat stays at the same quieter level", played.first().second * .45f, gain, 0f)
            }
            played.zipWithNext().forEach { (a, b) ->
                assertTrue("Feedback must not overlap the short navigation PCM", b.first - a.first >= 48)
            }
            assertTrue("Full-speed navigation keeps its click rhythm", played.takeLast(30).zipWithNext()
                .count { (a, b) -> b.first - a.first < 90 } >= 27)
            val bytes = context.resources.openRawResource(R.raw.ui_select).use { it.readBytes() }
            val values = ByteBuffer.wrap(bytes, 44, bytes.size - 44).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val pcm = DoubleArray(values.remaining()) { values.get() / 32768.0 }
            val repeatedEnergy = integratedPeak(pcm, played)
            val isolatedEnergy = integratedPeak(pcm, listOf(played.first()))
            val sustainedRepeatEnergy = integratedPeak(pcm, played.takeLast(30))
            assertTrue("A sustained fast scroll is quieter than an isolated click in the fast RMS model",
                sustainedRepeatEnergy < isolatedEnergy)
            assertTrue("The burst never rises above the initial click in the fast RMS model",
                repeatedEnergy <= isolatedEnergy * 1.001)
            val uncompensatedEnergy = integratedPeak(pcm, played.map { it.first to .2f })
            val rmsCeiling = Math.pow(10.0, -36.0 / 20) * .4
            val peakCeiling = Math.pow(10.0, -24.0 / 20) * .4
            assertTrue("The uncompensated restored burst would exceed the chosen RMS cap",
                uncompensatedEnergy > rmsCeiling * rmsCeiling)
            assertTrue("Reconstructed native burst stays below the digital RMS cap",
                repeatedEnergy <= rmsCeiling * rmsCeiling)
            assertTrue("Each native click stays below the digital peak cap",
                pcm.maxOf { kotlin.math.abs(it) } * played.maxOf { it.second } <= peakCeiling)
            SystemClock.sleep(650)
            instrumentation.runOnMainSync { sounds.onItemSelected() }
            val resumeDeadline = SystemClock.uptimeMillis() + 2_000
            while (starts.size == played.size && SystemClock.uptimeMillis() < resumeDeadline) SystemClock.sleep(10)
            assertEquals("A pause restores the isolated click level", played.first().second, starts.last().second, .000001f)
            assertEquals(systemVolume, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
            assertEquals(systemMuted, audio.isStreamMute(AudioManager.STREAM_MUSIC))
            android.util.Log.i("PockyDeckSoundTest", "Quieter-repeat native burst: ${played.size} cues; gain ${played.first().second} to ${played.last().second}; reconstructed fast RMS dBFS ${10 * kotlin.math.log10(repeatedEnergy)}; sustained ${10 * kotlin.math.log10(sustainedRepeatEnergy)}; isolated ${10 * kotlin.math.log10(isolatedEnergy)}; system muted $systemMuted; uncompensated ${10 * kotlin.math.log10(uncompensatedEnergy)}")
        } finally { instrumentation.runOnMainSync { sounds.release() } }
    }

    /** Reconstructs packaged PCM at native callback times; this does not record the speaker or mixer. */
    private fun integratedPeak(pcm: DoubleArray, starts: List<Pair<Long, Float>>): Double {
        val sampleRate = 44_100.0
        val first = starts.first().first
        val output = DoubleArray(((starts.last().first - first) * sampleRate / 1_000).toInt() + pcm.size + 1)
        for ((time, gain) in starts) {
            val offset = ((time - first) * sampleRate / 1_000).toInt()
            pcm.forEachIndexed { index, value -> output[offset + index] += value * gain }
        }
        val decay = kotlin.math.exp(-1.0 / (sampleRate * .125))
        var energy = 0.0
        var peak = 0.0
        for (value in output) {
            energy = energy * decay + value * value * (1 - decay)
            peak = maxOf(peak, energy)
        }
        return peak
    }

    @Test fun rapidAlternatingCuesAndVolumeChangesKeepOneCadenceWithoutChangingMediaVolume() {
        val audio = requireNotNull(context.getSystemService(AudioManager::class.java))
        val systemVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val systemMuted = audio.isStreamMute(AudioManager.STREAM_MUSIC)
        var volume = 40
        var enabled = true
        lateinit var sounds: ControllerSoundEffects
        val starts = mutableListOf<Long>()
        val callDurations = mutableListOf<Long>()
        val acceptedDurations = mutableListOf<Long>()
        instrumentation.runOnMainSync {
            sounds = ControllerSoundEffects(context, volumePercent = { volume }, enabled = { enabled })
            sounds.prepare()
            sounds.setActive(true)
        }
        try {
            val deadline = SystemClock.uptimeMillis() + 5_000
            val ready = AtomicBoolean(false)
            while (!ready.get() && SystemClock.uptimeMillis() < deadline) {
                instrumentation.runOnMainSync { ready.set(sounds.onItemSelected()) }
                if (!ready.get()) SystemClock.sleep(10)
            }
            assertTrue("Preloaded PCM must be ready", ready.get())
            repeat(400) { index ->
                instrumentation.runOnMainSync {
                    volume = listOf(40, 100, 20, 0, 80, 10, 60)[index % 7]
                    enabled = index % 13 != 0
                    sounds.onPreferencesChanged()
                    val time = SystemClock.uptimeMillis()
                    val started = if (index % 2 == 0) sounds.onItemSelected() else sounds.onTouchActivation()
                    callDurations += SystemClock.uptimeMillis() - time
                    if (started) {
                        acceptedDurations += callDurations.last()
                        assertTrue(enabled && volume > 0)
                        starts += time
                        assertFalse("A second action cannot stack a cue after a ${callDurations.last()}ms native call", sounds.onTouchActivation())
                    }
                    if (!enabled || volume == 0) assertFalse(sounds.onItemSelected())
                }
                SystemClock.sleep(4)
            }
            assertTrue("Stress run must exercise native playback", starts.size >= 10)
            val p95 = acceptedDurations.sorted()[acceptedDurations.size * 95 / 100]
            assertTrue("Audio submissions must not block a UI frame: p95=${p95}ms", p95 < 16)
            assertTrue("Submissions observe the shortest navigation rate limit", starts.zipWithNext().all { (a, b) -> b - a >= 40 })
            instrumentation.runOnMainSync {
                volume = 0
                sounds.onPreferencesChanged()
                repeat(100) { assertFalse(sounds.onItemSelected()) }
                enabled = false
                volume = 100
                sounds.onPreferencesChanged()
                assertFalse("Increasing volume cannot override the sound toggle", sounds.onTouchActivation())
                sounds.setActive(false)
                enabled = true
                assertFalse("Nothing replays after leaving the launcher", sounds.onItemSelected())
            }
            assertEquals("Launcher feedback must never change Android media volume", systemVolume,
                audio.getStreamVolume(AudioManager.STREAM_MUSIC))
            assertEquals("Launcher feedback must preserve Android media mute", systemMuted,
                audio.isStreamMute(AudioManager.STREAM_MUSIC))
            android.util.Log.i("PockyDeckSoundTest", "400 rapid input/volume changes; ${starts.size} accepted cues; p95 ${p95}ms; max call ${callDurations.maxOrNull()}ms; media volume unchanged")
        } finally { instrumentation.runOnMainSync { sounds.release() } }
    }

    @Test fun loadedSelectionSkipsBusyPlaybackAndRespectsEnableLifecycleAndRelease() {
        var enabled = false // In-memory toggle only; the user's preference is untouched.
        lateinit var sounds: ControllerSoundEffects
        instrumentation.runOnMainSync {
            sounds = ControllerSoundEffects(context) { enabled }
        }
        try {
            instrumentation.runOnMainSync {
                sounds.prepare()
                sounds.setActive(true)
                assertFalse(sounds.onItemSelected())
                enabled = true
            }
            val completed = AtomicBoolean(false)
            val deadline = SystemClock.uptimeMillis() + 5_000
            while (!completed.get() && SystemClock.uptimeMillis() < deadline) {
                instrumentation.runOnMainSync {
                    if (sounds.onItemSelected()) {
                        // Assert in the same main-thread call; a delayed test thread must
                        // not mistake a normally completed cue for a missing busy guard.
                        assertFalse("A second selection cannot interrupt the active cue", sounds.onItemSelected())
                        enabled = false
                        sounds.stop()
                        assertFalse("Disabled feedback stays silent after loading", sounds.onItemSelected())
                        enabled = true
                        sounds.setActive(false)
                        assertFalse("Paused feedback stays silent", sounds.onItemSelected())
                        sounds.setActive(true)
                        completed.set(true)
                    }
                }
                if (!completed.get()) SystemClock.sleep(20)
            }
            assertTrue("The packaged SELECT sample must load and start within5 seconds", completed.get())
            // The worker may still be retiring the pre-pause voice. Fresh requests can
            // be dropped while it is busy; nothing is queued or resumed automatically.
            val resumed = AtomicBoolean(false)
            val resumeDeadline = SystemClock.uptimeMillis() + 5_000
            while (!resumed.get() && SystemClock.uptimeMillis() < resumeDeadline) {
                instrumentation.runOnMainSync { resumed.set(sounds.onItemSelected()) }
                if (!resumed.get()) SystemClock.sleep(20)
            }
            assertTrue("A fresh post-resume event can play when the worker is ready", resumed.get())
            instrumentation.runOnMainSync {
                sounds.release()
                sounds.prepare()
                sounds.setActive(true)
                assertFalse("Released SoundPool cannot be reactivated", sounds.onItemSelected())
            }
        } finally { instrumentation.runOnMainSync { sounds.release() } }
    }

    @Test fun navigationRestoresTheOriginalRoundedSamplesExactly() {
        val originals = mapOf(
            R.raw.ui_move to "9ee8b50af1f4b9a855b190e9923046e80af43232afddb0a850ef32bbebe84c02",
            R.raw.ui_select to "90fb97f7045198d3c1dbad64a6dedbedc58cad6c5050e630489378278c9c6273",
        )
        for ((resource, originalHash) in originals) {
            val bytes = context.resources.openRawResource(resource).use { it.readBytes() }
            val hash = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            assertEquals("Restore the pre-fix waveform, not a new replacement sound", originalHash, hash)
        }
    }

    @Test fun packagedPcmHeadersAndDurationsMatchEveryBusyWindow() {
        val resources = mapOf(
            ControllerSoundCue.MOVE to R.raw.ui_move,
            ControllerSoundCue.SELECT to R.raw.ui_select,
            ControllerSoundCue.CONFIRM to R.raw.ui_confirm,
            ControllerSoundCue.BACK to R.raw.ui_back,
            ControllerSoundCue.PAGE to R.raw.ui_page,
            ControllerSoundCue.FILTER to R.raw.ui_filter,
        )
        assertEquals(ControllerSoundCue.entries.toSet(), resources.keys)
        for ((cue, resource) in resources) {
            DataInputStream(context.resources.openRawResource(resource)).use { input ->
                val header = ByteArray(44)
                input.readFully(header)
                val fields = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                fun tag(offset: Int) = String(header, offset, 4, Charsets.US_ASCII)
                assertEquals("$cue RIFF", "RIFF", tag(0))
                assertEquals("$cue WAVE", "WAVE", tag(8))
                assertEquals("$cue format chunk", "fmt ", tag(12))
                assertEquals("$cue PCM format size", 16, fields.getInt(16))
                assertEquals("$cue PCM encoding", 1, fields.getShort(20).toInt())
                assertEquals("$cue mono", 1, fields.getShort(22).toInt())
                val sampleRate = fields.getInt(24)
                val bytesPerSecond = fields.getInt(28)
                assertEquals("$cue sample rate", 44_100, sampleRate)
                assertEquals("$cue PCM byte rate", sampleRate * 2, bytesPerSecond)
                assertEquals("$cue frame alignment", 2, fields.getShort(32).toInt())
                assertEquals("$cue sample precision", 16, fields.getShort(34).toInt())
                assertEquals("$cue audio chunk", "data", tag(36))
                val dataBytes = fields.getInt(40)
                assertTrue("$cue is a short complete PCM sample", dataBytes in 2..64_000 && dataBytes % 2 == 0)
                assertEquals("$cue complete RIFF size", dataBytes + 36, fields.getInt(4))
                val pcm = ByteArray(dataBytes)
                input.readFully(pcm)
                val shorts = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                val values = DoubleArray(shorts.remaining()) { shorts.get() / 32768.0 }
                val rms = kotlin.math.sqrt(values.sumOf { it * it } / values.size)
                assertEquals("$cue matches the other cues' RMS level", .044, rms, .0001)
                assertTrue("$cue leaves ample peak headroom", values.maxOf { kotlin.math.abs(it) } <= .201)
                assertTrue("$cue has no DC offset", kotlin.math.abs(values.average()) < .0001)
                assertTrue("$cue starts and ends silently without a hard edge", values.first() == 0.0 && values.last() == 0.0)
                assertEquals("$cue has no unaccounted audio tail", -1, input.read())
                val actualDurationMillis = dataBytes * 1_000.0 / bytesPerSecond
                assertEquals("$cue busy duration matches the actual PCM frames",
                    cue.durationMillis.toDouble(), actualDurationMillis, 1_000.0 / sampleRate)
            }
        }
    }
}
