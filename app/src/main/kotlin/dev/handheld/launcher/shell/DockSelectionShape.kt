package dev.handheld.launcher.shell

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import kotlin.math.abs

/** One silhouette for the white fill and icon contrast mask, including mirrored movement. */
internal fun Path.setDockSelectionShape(headX: Float, tailX: Float, centerY: Float, radius: Float) {
    reset()
    if (radius <= 0f) return
    val lag = (headX - tailX).coerceIn(-radius * 1.1f, radius * 1.1f)
    val stretch = abs(lag)
    if (stretch < .01f) {
        addOval(Rect(headX - radius, centerY - radius, headX + radius, centerY + radius))
        return
    }
    val direction = if (lag > 0f) 1f else -1f
    val amount = stretch / (radius * 1.1f)
    val height = radius * (1f - .08f * amount)
    val curve = .55228475f
    val tip = -radius - stretch
    val tailRoundness = curve * height * (1f - .75f * amount)
    fun x(offset: Float) = headX + offset * direction

    // A round leading half flows into a narrow, softly rounded trailing tip.
    // As the lag closes, these controls converge to the original circle.
    moveTo(x(radius), centerY)
    cubicTo(x(radius), centerY - curve * height, x(curve * radius), centerY - height, x(0f), centerY - height)
    cubicTo(x(-curve * radius - stretch * .1f), centerY - height,
        x(tip), centerY - tailRoundness, x(tip), centerY)
    cubicTo(x(tip), centerY + tailRoundness,
        x(-curve * radius - stretch * .1f), centerY + height, x(0f), centerY + height)
    cubicTo(x(curve * radius), centerY + height, x(radius), centerY + curve * height, x(radius), centerY)
    close()
}
