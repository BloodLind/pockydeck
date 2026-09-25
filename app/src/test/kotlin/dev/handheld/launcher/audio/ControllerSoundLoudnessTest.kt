package dev.handheld.launcher.audio

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt
import org.junit.Assert.*
import org.junit.Test

class ControllerSoundLoudnessTest {
    @Test fun `fastest repeated navigation PCM stays quieter than its isolated click`() {
        val sampleRate = 44_100.0
        val decay = exp(-1 / (sampleRate * .125))
        for (cue in listOf(ControllerSoundCue.MOVE, ControllerSoundCue.SELECT,
            ControllerSoundCue.PAGE, ControllerSoundCue.FILTER)) {
            val bytes = File("src/main/res/raw/ui_${cue.name.lowercase()}.wav").readBytes()
            val shorts = ByteBuffer.wrap(bytes, 44, bytes.size - 44).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val pcm = DoubleArray(shorts.remaining()) { shorts.get() / 32768.0 }
            var now = 0L
            var gain = 0f
            val playback = ControllerSoundPlayback({ _, level -> gain = level; 1 }, {}, { _, _ -> }, { now })
            var power = 0.0
            var isolatedPeak = 0.0
            var finalRepeatPeak = 0.0
            val gapFrames = (cue.minimumIntervalMillis * sampleRate / 1_000).toLong() - pcm.size
            repeat(300) { index ->
                assertTrue(playback.play(cue, 40))
                var clickPeak = 0.0
                for (sample in pcm) {
                    val output = sample * gain
                    power = power * decay + output * output * (1 - decay)
                    clickPeak = maxOf(clickPeak, power)
                }
                if (index == 0) isolatedPeak = clickPeak
                else assertTrue("$cue cannot build above its first click at the fastest permitted cadence",
                    clickPeak <= isolatedPeak * 1.001)
                finalRepeatPeak = clickPeak
                power *= decay.pow(gapFrames.toDouble())
                now += cue.minimumIntervalMillis
            }
            assertTrue("$cue sustained fast RMS must be quieter than a single action", finalRepeatPeak < isolatedPeak)
        }
    }

    @Test fun `every cue has a fixed level with linear volume and no recovery boost`() {
        val loudness = ControllerSoundLoudness()
        for (cue in ControllerSoundCue.entries) {
            val full = loudness.gain(cue, 100)
            assertTrue(full > 0f && full <= .5f)
            repeat(10_000) { assertEquals(full, loudness.gain(cue, 100), 0f) }
            assertEquals(full * .4f, loudness.gain(cue, 40), .000001f)
            assertEquals(0f, loudness.gain(cue, -1), 0f)
            assertEquals(full, loudness.gain(cue, 200), 0f)
        }
    }

    @Test fun `packaged PCM stays below peak and fast RMS ceilings through sustained and mixed repeats`() {
        val loudness = ControllerSoundLoudness()
        val clips = ControllerSoundCue.entries.associateWith { cue ->
            val bytes = File("src/main/res/raw/ui_${cue.name.lowercase()}.wav").readBytes()
            val shorts = ByteBuffer.wrap(bytes, 44, bytes.size - 44).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            DoubleArray(shorts.remaining()) { shorts.get() / 32768.0 }.also { pcm ->
                assertTrue("Asset RMS is within the limiter's source bound", sqrt(pcm.sumOf { it * it } / pcm.size) <= .0441)
                assertTrue("Asset peak is within the limiter's source bound", pcm.maxOf(::abs) <= .201)
            }
        }
        val sampleRate = 44_100.0
        val decay = exp(-1 / (sampleRate * .125))
        val peakLimit = 10.0.pow(-24.0 / 20)
        val rmsLimit = 10.0.pow(-36.0 / 20)
        for (volume in listOf(10, 40, 100)) {
            for (fixed in ControllerSoundCue.entries + listOf(null)) {
                var power = 0.0
                var previousEnd = 0L
                var nextStart = 0L
                var highestRms = 0.0
                repeat(3_000) { index ->
                    val cue = fixed ?: ControllerSoundCue.entries[(index * 17) % ControllerSoundCue.entries.size]
                    val pcm = clips.getValue(cue)
                    power *= decay.pow((nextStart - previousEnd).toDouble())
                    val gain = loudness.gain(cue, volume)
                    for (value in pcm) {
                        val output = value * gain
                        assertTrue("$cue peak at $volume%", abs(output) <= peakLimit * volume / 100)
                        power = power * decay + output * output * (1 - decay)
                        highestRms = maxOf(highestRms, sqrt(power))
                    }
                    previousEnd = nextStart + pcm.size
                    // Exact fastest guard, jitter, and quiet gaps. Fixed runs sustain indefinitely.
                    val gap = if (fixed != null) 0L else listOf(0L, 1L, 15L, 350L, 900L)[index % 5]
                    nextStart += ((cue.minimumIntervalMillis + gap) * sampleRate / 1_000).toLong()
                    assertTrue("Busy guard covers the restored clip", nextStart >= previousEnd)
                }
                assertTrue("$fixed RMS at $volume%: $highestRms", highestRms <= rmsLimit * volume / 100)
                assertTrue("Feedback remains audible, not accidentally suppressed", highestRms > rmsLimit * volume / 400)
            }
        }
    }
}
