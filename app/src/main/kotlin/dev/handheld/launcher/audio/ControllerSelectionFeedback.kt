package dev.handheld.launcher.audio

/** Selection audio follows a changed card focus, including next-frame lazy-grid placement. */
internal class ControllerSelectionFeedback(
    private val expectItemSelection: () -> Unit,
    private val playSelection: () -> Unit,
    private val now: () -> Long,
) {
    private var lastItem: String? = null
    private var armedUntil: Long? = null
    val hasItemFocus: Boolean get() = lastItem != null

    fun navigate(fromItem: Boolean) {
        // Header navigation already receives MOVE. Do not add a delayed second cue
        // when that action asynchronously restores a card farther down the page.
        armedUntil = if (fromItem) now() + 250L else null
        if (fromItem) expectItemSelection()
    }

    /** Call for focused controls only; a card's intervening blur is not a new target. */
    fun focus(item: String?) {
        val changed = item != null && item != lastItem
        val armed = armedUntil
        if (item != null && item == lastItem) {
            // Metadata may republish the old focused action while a lazy scroll is
            // placing its destination. It must not consume that navigation's cue.
            if (armed != null && now() > armed) armedUntil = null
            return
        }
        lastItem = item
        armedUntil = null
        if (changed && armed != null && now() <= armed) playSelection()
    }

    fun reset() { armedUntil = null }
}
