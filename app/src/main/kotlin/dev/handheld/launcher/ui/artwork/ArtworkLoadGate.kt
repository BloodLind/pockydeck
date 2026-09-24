package dev.handheld.launcher.ui.artwork

import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Defers uncached cover work on large scrolling surfaces; never delays installed-app icons. */
val LocalArtworkLoadingAllowed = compositionLocalOf { true }

/** Pause new work immediately; never reopen between individual steps of a held direction. */
internal class ArtworkLoadGate(private val scope: CoroutineScope) {
    private val mutableAllowed = MutableStateFlow(true)
    val allowed = mutableAllowed.asStateFlow()
    private var scrolling = false
    private var resume: Job? = null

    fun onNavigation() {
        mutableAllowed.value = false
        resume?.cancel()
        if (!scrolling) settle()
    }

    fun onScrollChanged(value: Boolean) {
        if (scrolling == value) return
        scrolling = value
        mutableAllowed.value = false
        resume?.cancel()
        if (!value) settle()
    }

    private fun settle() {
        resume = scope.launch {
            delay(SETTLE_MILLIS)
            mutableAllowed.value = true
        }
    }

    companion object { const val SETTLE_MILLIS = 180L }
}

@Composable
internal fun rememberArtworkLoadGate(isScrolling: () -> Boolean): ArtworkLoadGate {
    val scope = rememberCoroutineScope()
    val gate = remember(scope) { ArtworkLoadGate(scope) }
    val currentScrolling by rememberUpdatedState(isScrolling)
    LaunchedEffect(gate) { snapshotFlow { currentScrolling() }.collect(gate::onScrollChanged) }
    return gate
}
