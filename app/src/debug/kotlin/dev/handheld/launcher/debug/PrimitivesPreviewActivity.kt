package dev.handheld.launcher.debug

import android.animation.ValueAnimator
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.handheld.launcher.core.designsystem.foundation.LauncherPrimitivesPreviewContent
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme

/** Native visual evidence consumer; registered only in the debug manifest. */
class PrimitivesPreviewActivity : ComponentActivity() {
    private var reducedMotion by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LauncherTheme(reducedMotion = reducedMotion) {
                Box(
                    Modifier.fillMaxSize()
                        .background(LauncherTheme.colors.backgroundGradient())
                        .verticalScroll(rememberScrollState()),
                ) {
                    LauncherPrimitivesPreviewContent(reducedMotion = reducedMotion)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        reducedMotion = !ValueAnimator.areAnimatorsEnabled()
    }
}
