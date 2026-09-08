package dev.handheld.launcher.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import dev.handheld.launcher.R
import dev.handheld.launcher.contract.SemanticInputAction

/** Activity-owned, preloaded feedback. All calls and load callbacks run on the main thread. */
internal class ControllerSoundEffects(context: Context, enabled: () -> Boolean) {
    private val context = context.applicationContext
    private val audioManager = this.context.getSystemService(AudioManager::class.java)
    private val policy = ControllerSoundPolicy(enabled, ::mediaAudible, play = ::playLoaded)
    private var pool: SoundPool? = null
    private val samples = mutableMapOf<ControllerSoundCue, Int>()
    private val loaded = mutableSetOf<Int>()
    private var playingStream = 0
    private var released = false

    fun prepare() {
        if (pool != null || released) return
        try {
            val created = SoundPool.Builder()
                .setMaxStreams(MAX_STREAMS)
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                .build()
            pool = created
            created.setOnLoadCompleteListener { completedPool, sampleId, status ->
                if (pool === completedPool && status == 0) loaded += sampleId
            }
            for ((cue, resource) in resources) samples[cue] = created.load(context, resource, 1)
        } catch (_: RuntimeException) {
            disposePool()
        }
    }

    fun dispatch(action: SemanticInputAction, handle: (SemanticInputAction) -> Boolean): Boolean =
        policy.dispatch(action, handle)

    fun expectItemSelection() = policy.expectItemSelection()

    fun onItemSelected() = policy.onItemSelected()

    fun setActive(active: Boolean) {
        policy.setActive(active && !released)
        if (!active) stop()
    }

    /** Stop tails when paused, unfocused, or disabled. Old clips are never resumed. */
    fun stop() {
        policy.stop()
        val stream = playingStream
        playingStream = 0
        if (stream != 0) try { pool?.stop(stream) } catch (_: RuntimeException) { }
    }

    fun release() {
        released = true
        policy.setActive(false)
        disposePool()
    }

    private fun disposePool() {
        stop()
        val previous = pool
        pool = null // Ignore a load callback already queued before release.
        samples.clear()
        loaded.clear()
        try { previous?.release() } catch (_: RuntimeException) { }
    }

    private fun mediaAudible(): Boolean = try {
        audioManager != null && !audioManager.isStreamMute(AudioManager.STREAM_MUSIC) &&
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) > 0
        // AudioAttributes also leaves DND, routing, and media attenuation with Android.
    } catch (_: RuntimeException) { false }

    private fun playLoaded(cue: ControllerSoundCue): Boolean {
        val current = pool ?: return false
        val sample = samples[cue]?.takeIf { it in loaded } ?: return false
        // Never queue input behind asynchronous decoding. A missing cue simply stays silent.
        val stream = current.play(sample, GAIN, GAIN, 1, 0, 1f)
        if (stream != 0) playingStream = stream
        return stream != 0
    }

    private companion object {
        const val MAX_STREAMS = 1
        const val GAIN = .5f
        val resources = mapOf(
            ControllerSoundCue.MOVE to R.raw.ui_move,
            ControllerSoundCue.SELECT to R.raw.ui_select,
            ControllerSoundCue.CONFIRM to R.raw.ui_confirm,
            ControllerSoundCue.BACK to R.raw.ui_back,
            ControllerSoundCue.PAGE to R.raw.ui_page,
            ControllerSoundCue.FILTER to R.raw.ui_filter,
        )
    }
}
