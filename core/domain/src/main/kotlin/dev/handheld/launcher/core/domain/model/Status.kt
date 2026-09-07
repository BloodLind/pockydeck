package dev.handheld.launcher.core.domain.model

/** Truthful capability state. Unsupported optional capabilities are omitted by presentation. */
sealed interface StatusValue<out T : Any> {
    data class Available<T : Any>(val value: T) : StatusValue<T>
    data object Unavailable : StatusValue<Nothing>
    data object Unsupported : StatusValue<Nothing>
}
