package dev.handheld.launcher.shell

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.handheld.launcher.core.designsystem.foundation.ShellBounds
import dev.handheld.launcher.core.designsystem.foundation.ShellMetrics

/** Activity/window code reports only the IME obstruction; it does not alter route state. */
@Immutable
data class LauncherShellInsets(
    val imeBottom: Dp = 0.dp,
) {
    init {
        require(imeBottom.value.isFinite() && imeBottom >= 0.dp) { "IME bottom inset must be finite and non-negative" }
    }
}

/** Resolved shell geometry after the central IME exception is applied. */
@Immutable
data class LauncherShellLayout(
    val statusBounds: ShellBounds,
    val contentBounds: ShellBounds,
    val dockBounds: ShellBounds,
    val footerBounds: ShellBounds,
    val dockCenterY: Dp,
    val footerDividerY: Dp,
    val imeVisible: Boolean,
)

/**
 * Preserves the root metric anchors whenever the IME is absent. Under an IME, the content ends
 * before the shifted dock/footer group, leaving query content and a scrollable result region.
 */
object LauncherShellLayoutPolicy {
    fun calculate(metrics: ShellMetrics, insets: LauncherShellInsets = LauncherShellInsets()): LauncherShellLayout {
        if (insets.imeBottom == 0.dp) {
            return LauncherShellLayout(
                statusBounds = metrics.statusBounds,
                contentBounds = metrics.contentBounds,
                dockBounds = metrics.dockBounds,
                footerBounds = metrics.footerBounds,
                dockCenterY = metrics.dockCenterY,
                footerDividerY = metrics.footerDividerY,
                imeVisible = false,
            )
        }

        val usableBottom = (metrics.windowHeight - insets.imeBottom).coerceIn(0.dp, metrics.windowHeight)
        // Landscape keyboards can leave only ~200dp. Preserve the query/results before chrome.
        val showStatus = usableBottom >= 280.dp
        val status = if (showStatus) metrics.statusBounds else ShellBounds(0.dp, 0.dp, metrics.windowWidth, 0.dp)
        val contentTop = if (showStatus) metrics.contentBounds.top else minOf(8.dp, usableBottom)
        val fullBottomHeight = metrics.dockBounds.height + metrics.footerBounds.height
        if (usableBottom - contentTop < 208.dp + fullBottomHeight) {
            val emptyBottom = ShellBounds(0.dp, usableBottom, metrics.windowWidth, usableBottom)
            return LauncherShellLayout(
                statusBounds = status,
                contentBounds = ShellBounds(metrics.contentBounds.left, contentTop, metrics.contentBounds.right,
                    (usableBottom - 8.dp).coerceAtLeast(contentTop)),
                dockBounds = emptyBottom,
                footerBounds = emptyBottom,
                dockCenterY = usableBottom,
                footerDividerY = usableBottom,
                imeVisible = true,
            )
        }
        val footerHeight = metrics.footerBounds.height.coerceAtMost(usableBottom - metrics.statusBounds.bottom)
        val footerTop = (usableBottom - footerHeight).coerceAtLeast(metrics.statusBounds.bottom)
        val dockHeight = metrics.dockBounds.height.coerceAtMost(footerTop - metrics.statusBounds.bottom)
        val dockTop = (footerTop - dockHeight).coerceAtLeast(metrics.statusBounds.bottom)
        val contentBottom = dockTop.coerceAtLeast(metrics.contentBounds.top)
        val dividerOffset = (metrics.footerDividerY - metrics.footerBounds.top)
            .coerceIn(0.dp, footerHeight)
        return LauncherShellLayout(
            statusBounds = metrics.statusBounds,
            contentBounds = ShellBounds(
                metrics.contentBounds.left,
                metrics.contentBounds.top,
                metrics.contentBounds.right,
                contentBottom,
            ),
            dockBounds = ShellBounds(0.dp, dockTop, metrics.windowWidth, footerTop),
            footerBounds = ShellBounds(0.dp, footerTop, metrics.windowWidth, usableBottom),
            dockCenterY = dockTop + dockHeight / 2f,
            footerDividerY = footerTop + dividerOffset,
            imeVisible = true,
        )
    }
}
