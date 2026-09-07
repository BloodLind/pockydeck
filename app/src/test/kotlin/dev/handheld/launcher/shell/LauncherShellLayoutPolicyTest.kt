package dev.handheld.launcher.shell

import androidx.compose.ui.unit.dp
import dev.handheld.launcher.core.designsystem.foundation.ShellMetrics
import dev.handheld.launcher.core.designsystem.foundation.ShellMetricsInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherShellLayoutPolicyTest {
    private val physicalMetrics = ShellMetrics.calculate(
        ShellMetricsInput(widthPx = 1920, heightPx = 1080, density = 2.25f),
    )

    @Test
    fun normalLayoutUsesTheRootMetricBoundsWithoutPageSpecificOffsets() {
        val layout = LauncherShellLayoutPolicy.calculate(physicalMetrics)

        assertEquals(physicalMetrics.statusBounds, layout.statusBounds)
        assertEquals(physicalMetrics.contentBounds, layout.contentBounds)
        assertEquals(physicalMetrics.dockBounds, layout.dockBounds)
        assertEquals(physicalMetrics.footerBounds, layout.footerBounds)
        assertEquals(physicalMetrics.dockCenterY, layout.dockCenterY)
        assertEquals(physicalMetrics.footerDividerY, layout.footerDividerY)
        assertTrue(!layout.imeVisible)
    }

    @Test
    fun tallLandscapeKeyboardPreservesQueryAndResultsBeforeChrome() {
        val layout = LauncherShellLayoutPolicy.calculate(physicalMetrics, LauncherShellInsets(280.dp))
        assertEquals(0.dp, layout.dockBounds.height)
        assertEquals(0.dp, layout.footerBounds.height)
        assertTrue(layout.contentBounds.height >= 180.dp)
        assertTrue(layout.contentBounds.bottom <= 200.dp)
        assertEquals(physicalMetrics.contentBounds,
            LauncherShellLayoutPolicy.calculate(physicalMetrics).contentBounds)
    }

    @Test
    fun imeMovesBottomShellAboveObstructionAndKeepsAValidContentSurface() {
        val imeBottom = 180.dp
        val layout = LauncherShellLayoutPolicy.calculate(physicalMetrics, LauncherShellInsets(imeBottom))

        assertTrue(layout.imeVisible)
        assertEquals(physicalMetrics.windowHeight - imeBottom, layout.footerBounds.bottom)
        assertTrue(layout.contentBounds.top >= layout.statusBounds.bottom)
        assertTrue(layout.contentBounds.bottom <= layout.dockBounds.top)
        assertTrue(layout.dockBounds.bottom <= layout.footerBounds.top)
        assertTrue(layout.dockCenterY >= layout.dockBounds.top)
        assertTrue(layout.dockCenterY <= layout.dockBounds.bottom)
    }
}
