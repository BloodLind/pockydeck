package dev.handheld.launcher.audio

import android.media.AudioManager
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.handheld.launcher.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/** Isolated audio fixtures: no persistent preference, volume, or catalog changes. */
@RunWith(AndroidJUnit4::class)
class ControllerSoundEffectsDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun loadedSelectionSkipsBusyPlaybackAndRespectsEnableLifecycleAndRelease() {
        val audio = context.getSystemService(AudioManager::class.java)
        assumeTrue("Keep the user's media mute/volume unchanged",
            audio != null && !audio.isStreamMute(AudioManager.STREAM_MUSIC) &&
                audio.getStreamVolume(AudioManager.STREAM_MUSIC) > 0)
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
                        assertTrue("Explicit stop clears the busy guard", sounds.onItemSelected())
                        sounds.setActive(false)
                        assertFalse("Paused feedback stays silent", sounds.onItemSelected())
                        sounds.setActive(true)
                        assertTrue("Resume permits new events without replaying old ones", sounds.onItemSelected())
                        sounds.release()
                        sounds.prepare()
                        sounds.setActive(true)
                        assertFalse("Released SoundPool cannot be reactivated", sounds.onItemSelected())
                        completed.set(true)
                    }
                }
                if (!completed.get()) SystemClock.sleep(20)
            }
            assertTrue("The packaged SELECT sample must load and start within5 seconds", completed.get())
        } finally { instrumentation.runOnMainSync { sounds.release() } }
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
                input.readFully(ByteArray(dataBytes))
                assertEquals("$cue has no unaccounted audio tail", -1, input.read())
                val actualDurationMillis = dataBytes * 1_000.0 / bytesPerSecond
                assertEquals("$cue busy duration matches the actual PCM frames",
                    cue.durationMillis.toDouble(), actualDurationMillis, 1_000.0 / sampleRate)
            }
        }
    }
}
