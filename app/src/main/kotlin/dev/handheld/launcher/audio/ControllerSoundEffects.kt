package dev.handheld.launcher.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap
import dev.handheld.launcher.R
import dev.handheld.launcher.contract.SemanticInputAction

/** Activity-owned feedback. Native audio calls run on one worker, never on the UI thread. */
internal class ControllerSoundEffects(
    context: Context,
    private val volumePercent: () -> Int = { 40 },
    private val onSampleStarted: (ControllerSoundCue, Float) -> Unit = { _, _ -> },
    hapticsEnabled: () -> Boolean = { true },
    hapticFeedback: (ControllerSoundCue) -> Boolean = { false },
    private val enabled: () -> Boolean,
) {
    private val context = context.applicationContext
    // Android's mixer already applies media mute, volume and routing to USAGE_GAME.
    // Avoid synchronous AudioManager binder queries on every navigation event.
    private val policy = ControllerSoundPolicy(enabled, { true }, play = { dispatcher.request(it) })
    private val hapticHandler = Handler(Looper.getMainLooper())
    private val haptics = ControllerHapticFeedback(hapticsEnabled,
        schedule = { delay, action ->
            val task = Runnable { action() }
            hapticHandler.postDelayed(task, delay)
            val cancel: () -> Unit = { hapticHandler.removeCallbacks(task) }
            cancel
        }, pulse = hapticFeedback)
    private var pool: SoundPool? = null
    private val samples = mutableMapOf<ControllerSoundCue, Int>()
    private val loaded = ConcurrentHashMap.newKeySet<ControllerSoundCue>()
    private val playback = ControllerSoundPlayback(::playSample,
        stopStream = { pool?.stop(it) },
        setStreamVolume = { stream, gain -> pool?.setVolume(stream, gain, gain) },
        monotonicTimeMillis = SystemClock::uptimeMillis)
    private val audioThread = HandlerThread("PockyDeckAudio").apply { start() }
    private val worker = Handler(audioThread.looper)
    @Volatile private var released = false
    @Volatile private var active = false
    private var prepared = false
    private val dispatcher = ControllerSoundDispatcher(
        post = { worker.post(it) }, now = SystemClock::uptimeMillis,
        ready = loaded::contains,
        effectiveVolume = { if (active && !released && enabled()) volumePercent().coerceIn(0, 100) else 0 },
        playback = playback)

    fun prepare() {
        if (prepared || released) return
        prepared = true
        worker.post { prepareOnWorker() }
    }

    private fun prepareOnWorker() {
        if (released) return
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
                if (pool === completedPool && status == 0)
                    samples.entries.firstOrNull { it.value == sampleId }?.key?.let(loaded::add)
            }
            for ((cue, resource) in resources) samples[cue] = created.load(context, resource, 1)
        } catch (_: RuntimeException) {
            disposePool()
        }
    }

    fun dispatch(action: SemanticInputAction, handle: (SemanticInputAction) -> Boolean): Boolean =
        policy.dispatch(action) {
            val handled = handle(it)
            if (handled) haptics.onAction(it.feedbackCue())
            handled
        }

    fun expectItemSelection() = policy.expectItemSelection()

    fun onItemSelected() = policy.onItemSelected()
    fun onTouchActivation(): Boolean {
        val vibrated = haptics.onAction(ControllerSoundCue.CONFIRM)
        val sounded = policy.onTouchActivation()
        return vibrated || sounded
    }

    fun onRepeatHoldChanged(held: Boolean) = haptics.setHeld(held)
    fun cancelHaptics() = haptics.cancel()

    /** Never replays a cue or clears its rate limit when settings change rapidly. */
    fun onPreferencesChanged() = dispatcher.preferencesChanged()

    fun setActive(active: Boolean) {
        this.active = active && !released
        policy.setActive(active && !released)
        haptics.setActive(active && !released)
        if (!active) stop()
    }

    /** Stop tails when paused, unfocused, or disabled. Old clips are never resumed. */
    fun stop() {
        haptics.cancel()
        policy.stop()
        dispatcher.stop()
    }

    fun release() {
        if (released) return
        released = true
        active = false
        policy.setActive(false)
        haptics.setActive(false)
        dispatcher.stop()
        worker.post {
            disposePool()
            audioThread.quitSafely()
        }
    }

    private fun disposePool() {
        playback.stop()
        val previous = pool
        pool = null // Ignore a load callback already queued before release.
        samples.clear()
        loaded.clear()
        try { previous?.release() } catch (_: RuntimeException) { }
    }

    private fun playSample(cue: ControllerSoundCue, gain: Float): Int {
        val current = pool ?: return 0
        val sample = samples[cue]?.takeIf { cue in loaded } ?: return 0
        // Never queue input behind asynchronous decoding. A missing cue simply stays silent.
        val stream = current.play(sample, gain, gain, 1, 0, 1f)
        if (stream != 0) onSampleStarted(cue, gain)
        return stream
    }

    private companion object {
        const val MAX_STREAMS = 1
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
