package dev.handheld.launcher.core.designsystem.contract

import androidx.compose.runtime.compositionLocalOf

/** Input intent is separate from Android's keyboard mode (an IME also needs keyboard focus). */
val LocalControllerInput = compositionLocalOf { true }
