package dev.handheld.launcher.core.designsystem.foundation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.unit.dp

class ShellMetricsTest {
    @Test
    fun standardAndCompactInputsKeepNonOverlappingAnchors() {
        listOf(
            ShellMetrics.calculate(ShellMetricsInput(1920, 1080, 1.5f)),
            ShellMetrics.calculate(ShellMetricsInput(800, 480, 1f, 1.3f)),
        ).forEach { metrics ->
            assertTrue(metrics.statusBounds.bottom <= metrics.contentBounds.top)
            assertTrue(metrics.contentBounds.bottom <= metrics.dockBounds.top)
            assertTrue(metrics.dockBounds.bottom <= metrics.footerBounds.top)
            assertTrue(metrics.footerBounds.bottom <= metrics.windowHeight)
            assertTrue(metrics.homeCardAllocatedSize >= metrics.homeCardArtworkSize)
            assertTrue(metrics.homeCardAllocatedBounds.bottom <= metrics.contentBounds.bottom)
        }
    }

    @Test
    fun wideShortWindowDisablesCardWhenStageCannotFit() {
        val metrics = ShellMetrics.calculate(ShellMetricsInput(1920, 300, 1.5f))
        assertTrue(!metrics.hasUsableHomeCard)
        assertEquals(0.dp, metrics.focusFrameReservation)
        assertTrue(metrics.controlsCanReachMinimumTouchTarget)
        assertTrue(metrics.metadataReservation > 0.dp)
    }

    @Test
    fun largerFontScaleReservesMoreMetadataAndKeepsCardInsideStage() {
        val normal = ShellMetrics.calculate(ShellMetricsInput(1920, 1080, 1.5f, 1f))
        val large = ShellMetrics.calculate(ShellMetricsInput(1920, 1080, 1.5f, 2f))
        assertTrue(large.metadataReservation > normal.metadataReservation)
        assertTrue(large.homeCardAllocatedBounds.bottom <= large.contentBounds.bottom)
    }

    @Test
    fun malformedAndSubnormalDensityRemainFinite() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.MIN_VALUE).forEach { density ->
            val metrics = ShellMetrics.calculate(ShellMetricsInput(1920, 1080, density))
            assertTrue(metrics.windowWidth.value.isFinite())
            assertTrue(metrics.windowHeight.value.isFinite())
        }
    }

    @Test
    fun emptyWindowProducesFiniteNonNegativeGeometry() {
        val metrics = ShellMetrics.calculate(ShellMetricsInput(0, 0, 0f, 0f))
        assertEquals(0.dp, metrics.windowWidth)
        assertEquals(0.dp, metrics.windowHeight)
        assertTrue(metrics.contentBounds.width >= 0.dp)
        assertTrue(metrics.contentBounds.height >= 0.dp)
        assertTrue(metrics.homeCardAllocatedSize >= 0.dp)
    }

    @Test
    fun narrowOrTinyBandsCannotClaimUsableTouchTargets() {
        assertTrue(!ShellMetrics.calculate(ShellMetricsInput(10, 1080, 1f)).controlsCanReachMinimumTouchTarget)
        assertTrue(!ShellMetrics.calculate(ShellMetricsInput(800, 50, 1f)).controlsCanReachMinimumTouchTarget)
        assertTrue(!ShellMetrics.calculate(ShellMetricsInput(10, 1080, 1f)).hasUsableHomeCard)
    }

    @Test
    fun standardArtworkAndShellFollowReferenceProportions() {
        val metrics = ShellMetrics.calculate(ShellMetricsInput(1920, 1080, 1.5f))
        assertEquals(.356f, metrics.homeCardAllocatedBounds.top / metrics.windowHeight, .002f)
        assertEquals(.187f, (metrics.homeCardArtworkSize + metrics.focusFrameReservation * 2f) / metrics.windowWidth, .002f)
        assertTrue(metrics.dockBounds.height >= 64.dp)
        assertTrue(metrics.footerBounds.height >= 48.dp)
        assertEquals(.852f, metrics.dockCenterY / metrics.windowHeight, .002f)
        assertEquals(.928f, metrics.footerDividerY / metrics.windowHeight, .002f)
        assertTrue(metrics.homeCardAllocatedBounds.bottom <= metrics.dockBounds.top)
    }

    @Test
    fun physicalFlip2DensityKeepsArtworkAndReadableChrome() {
        val physical = ShellMetrics.calculate(ShellMetricsInput(1920, 1080, 2.25f))
        val emulator = ShellMetrics.calculate(ShellMetricsInput(1920, 1080, 1.5f))
        assertTrue(!physical.compact)
        assertEquals(2f / 3f, physical.referenceScale, .001f)
        assertEquals(.149f, physical.homeMetadataTop / physical.windowHeight, .002f)
        assertEquals(.356f, physical.homeCardAllocatedBounds.top / physical.windowHeight, .002f)
        assertTrue(physical.controlsCanReachMinimumTouchTarget)
        assertTrue(physical.statusBounds.height >= 48.dp)
        assertTrue(physical.dockCenterY - 24.dp >= physical.dockBounds.top)
        assertTrue(physical.dockCenterY + 24.dp <= physical.footerBounds.top)
        assertTrue(physical.footerBounds.height >= 48.dp)
        assertEquals(.928f, physical.footerDividerY / physical.windowHeight, .002f)
        assertEquals(
            (emulator.homeCardArtworkSize + emulator.focusFrameReservation * 2f).value * emulator.density,
            (physical.homeCardArtworkSize + physical.focusFrameReservation * 2f).value * physical.density,
            .1f,
        )
    }

    @Test
    fun compactLargeTextKeepsArtworkSquareAndFocusInsideContent() {
        val metrics = ShellMetrics.calculate(ShellMetricsInput(800, 480, 1f, 1.3f))
        assertTrue(metrics.hasUsableHomeCard)
        assertTrue(metrics.homeCardArtworkSize >= metrics.minimumTouchTarget)
        assertEquals(metrics.homeCardAllocatedBounds.width.value, metrics.homeCardAllocatedBounds.height.value, .001f)
        assertTrue(metrics.homeCardAllocatedBounds.bottom <= metrics.contentBounds.bottom)
        assertTrue(metrics.homeCardAllocatedBounds.right <= metrics.contentBounds.right)
        assertTrue(metrics.controlsCanReachMinimumTouchTarget)
    }

    @Test
    fun unusualPositiveScalesStayFiniteWithoutPretendingTheFontIsSmaller() {
        val metrics = ShellMetrics.calculate(ShellMetricsInput(Int.MAX_VALUE, Int.MAX_VALUE, 1f, Float.MAX_VALUE))
        assertEquals(Float.MAX_VALUE, metrics.fontScale)
        listOf(metrics.contentBounds, metrics.dockBounds, metrics.footerBounds, metrics.homeCardAllocatedBounds).forEach {
            assertTrue(it.top.value.isFinite() && it.bottom.value.isFinite())
            assertTrue(it.left.value.isFinite() && it.right.value.isFinite())
        }
    }
}
