package dev.handheld.launcher.core.designsystem.theme

import android.graphics.Matrix
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.handheld.launcher.core.designsystem.R

/** Semantic roles derived from the Home reference. Keep essential labels on primary/secondary. */
@Immutable
data class LauncherColors(
    val backgroundTop: Color = Color(0xFF313042),
    val backgroundMiddle: Color = Color(0xFF282736),
    val backgroundBottom: Color = Color(0xFF232230),
    val surfaceCard: Color = Color(0xFF252831),
    val surfaceApp: Color = Color(0xFF23262F),
    val surfaceArtwork: Color = Color(0xFF1C1E24),
    val surfaceDock: Color = Color(0xFF1E2229),
    val dockInactive: Color = Color(0x33CBD5E1),
    val destinationSelected: Color = Color(0xFFE2E8F0),
    val destinationSelectedContent: Color = Color(0xFF1E2229),
    val focus: Color = Color(0xFFE5A01A),
    val focusLowerEdge: Color = Color(0xFF996409),
    val textPrimary: Color = Color(0xFFF4F5F8),
    val textSecondary: Color = Color(0xFFA6ACB8),
    val textMuted: Color = Color(0xFF78889B),
    val confirm: Color = Color(0xFF3EC07D),
    val cancel: Color = Color(0xFFE05244),
    val borderSubtle: Color = Color(0x0DFFFFFF),
    val borderEmphasis: Color = Color(0x1AFFFFFF),
) {
    /**
     * The single shell background recipe; callers should apply it once at the shell root.
     * Recreates Home's ellipse: 120% width by 100% height, centered at 50% / 0%.
     * ShaderBrush supplies actual pixel bounds, including after a window-size change.
     */
    fun backgroundGradient(): Brush = object : ShaderBrush() {
        override fun createShader(size: Size): Shader = RadialGradient(
            0f,
            0f,
            1f,
            intArrayOf(backgroundTop.toArgb(), backgroundMiddle.toArgb(), backgroundBottom.toArgb()),
            floatArrayOf(0f, .55f, 1f),
            Shader.TileMode.CLAMP,
        ).apply {
            setLocalMatrix(Matrix().apply {
                setScale((size.width * 1.2f).coerceAtLeast(1f), size.height.coerceAtLeast(1f))
                postTranslate(size.width * .5f, 0f)
            })
        }
    }
}

@Immutable
data class LauncherTypography(
    val homeTitle: TextStyle,
    val pageTitle: TextStyle,
    val tileTitleLarge: TextStyle,
    val clock: TextStyle,
    val actionPrimary: TextStyle,
    val actionLabel: TextStyle,
    val controlLabel: TextStyle,
    val statusValue: TextStyle,
    val platformLabel: TextStyle,
    val badgeLabel: TextStyle,
    val tileTitle: TextStyle,
    val tileSubtitle: TextStyle,
    val body: TextStyle,
    val settingLabel: TextStyle,
)

private val PlusJakartaSans = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
    // Explicit 800 is the initial heavy style; avoid synthesized 900.
    Font(R.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold),
)

private fun TextStyle.role(size: Int, weight: FontWeight, lineHeight: Int = size + 4) = copy(
    fontFamily = PlusJakartaSans,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
)

// Provisional native defaults: the source is approximately a 1280-unit design width;
// at 240 dpi Android reports 1280 dp for the 1920 px target. US-005/device calibration
// must confirm these values before they become accepted device defaults.
private val DefaultTypography = TextStyle(fontFamily = PlusJakartaSans).let { base ->
    LauncherTypography(
        homeTitle = base.role(34, FontWeight.ExtraBold, 40),
        pageTitle = base.role(24, FontWeight.Bold, 30),
        tileTitleLarge = base.role(18, FontWeight.ExtraBold, 24),
        clock = base.role(14, FontWeight.ExtraBold, 18),
        actionPrimary = base.role(13, FontWeight.Bold, 18),
        actionLabel = base.role(12, FontWeight.Medium, 16),
        controlLabel = base.role(12, FontWeight.Bold, 16),
        statusValue = base.role(11, FontWeight.Bold, 14),
        platformLabel = base.role(11, FontWeight.ExtraBold, 14),
        badgeLabel = base.role(10, FontWeight.ExtraBold, 13),
        tileTitle = base.role(14, FontWeight.Bold, 18),
        tileSubtitle = base.role(12, FontWeight.Medium, 16),
        body = base.role(16, FontWeight.Normal, 22),
        settingLabel = base.role(14, FontWeight.Medium, 20),
    )
}

@Immutable
data class LauncherSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 48.dp,
)

@Immutable
data class LauncherShapes(
    val homeOuter: Dp = 24.dp,
    val homeInner: Dp = 16.dp,
    val smallControl: Dp = 8.dp,
    val dock: Dp = 999.dp,
)

@Immutable
data class LauncherDepth(
    val cardElevation: Dp = 4.dp,
    val focusedElevation: Dp = 8.dp,
    val dockElevation: Dp = 2.dp,
    val focusLift: Dp = 4.dp,
)

@Immutable
data class LauncherMotion(
    val reducedMotion: Boolean = false,
) {
    val focusDurationMillis: Int get() = if (reducedMotion) 0 else 200
    val pressedDurationMillis: Int get() = if (reducedMotion) 0 else 100
}

val LocalLauncherColors = staticCompositionLocalOf { LauncherColors() }
val LocalLauncherTypography = staticCompositionLocalOf { DefaultTypography }
val LocalLauncherSpacing = staticCompositionLocalOf { LauncherSpacing() }
val LocalLauncherShapes = staticCompositionLocalOf { LauncherShapes() }
val LocalLauncherDepth = staticCompositionLocalOf { LauncherDepth() }
val LocalLauncherMotion = staticCompositionLocalOf { LauncherMotion() }

/** Shared visual language. Font sizes use sp and therefore follow the system font scale. */
object LauncherTheme {
    @Composable
    operator fun invoke(
        reducedMotion: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalLauncherColors provides LauncherColors(),
            LocalLauncherTypography provides DefaultTypography,
            LocalLauncherSpacing provides LauncherSpacing(),
            LocalLauncherShapes provides LauncherShapes(),
            LocalLauncherDepth provides LauncherDepth(),
            LocalLauncherMotion provides LauncherMotion(reducedMotion = reducedMotion),
            content = content,
        )
    }

    val colors: LauncherColors @Composable get() = LocalLauncherColors.current
    val typography: LauncherTypography @Composable get() = LocalLauncherTypography.current
    val spacing: LauncherSpacing @Composable get() = LocalLauncherSpacing.current
    val shapes: LauncherShapes @Composable get() = LocalLauncherShapes.current
    val depth: LauncherDepth @Composable get() = LocalLauncherDepth.current
    val motion: LauncherMotion @Composable get() = LocalLauncherMotion.current
}
