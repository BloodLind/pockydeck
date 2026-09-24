package dev.handheld.launcher.audio

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Nonblocking producer, one audio worker, and no FIFO of input sounds. */
internal class ControllerSoundDispatcher(
    private val post: (() -> Unit) -> Boolean,
    private val now: () -> Long,
    private val ready: (ControllerSoundCue) -> Boolean,
    private val effectiveVolume: () -> Int,
    private val playback: ControllerSoundPlayback,
) {
    private val busy = AtomicBoolean(false)
    private val refreshing = AtomicBoolean(false)
    private val revision = AtomicLong(0)
    private val nextStart = AtomicLong(0)
    private val pendingVolumeFloor = AtomicInteger(100)

    fun request(cue: ControllerSoundCue): Boolean {
        val requestedAt = now()
        if (effectiveVolume() <= 0 || !ready(cue) || requestedAt < nextStart.get() ||
            !busy.compareAndSet(false, true)) return false
        val requestedRevision = revision.get()
        val accepted = post {
            try {
                if (revision.get() == requestedRevision && now() - requestedAt <= 80 && effectiveVolume() > 0) {
                    val started = playback.play(cue, effectiveVolume())
                    // A mute or reduction received during a slow native call also applies
                    // if the user has already raised the slider again by the time it returns.
                    refreshVolume()
                    if (started) nextStart.set(now() + cue.minimumIntervalMillis)
                }
            } catch (_: RuntimeException) {
                playback.stop()
            } finally { busy.set(false) }
        }
        if (!accepted) busy.set(false)
        return accepted
    }

    fun preferencesChanged() = refresh(effectiveVolume())

    fun stop() {
        nextStart.set(0)
        refresh(0)
    }

    private fun refresh(volume: Int) {
        revision.incrementAndGet() // Invalidates a cue that has not reached the driver yet.
        pendingVolumeFloor.getAndUpdate { minOf(it, volume.coerceIn(0, 100)) }
        if (refreshing.compareAndSet(false, true)) {
            if (!post {
                refreshing.set(false)
                refreshVolume()
            }) refreshing.set(false)
        }
    }

    private fun refreshVolume() {
        playback.updateVolume(minOf(effectiveVolume(), pendingVolumeFloor.getAndSet(100)))
    }
}
