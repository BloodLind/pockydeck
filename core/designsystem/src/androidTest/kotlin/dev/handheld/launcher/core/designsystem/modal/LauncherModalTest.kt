package dev.handheld.launcher.core.designsystem.modal

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.unit.dp
import dev.handheld.launcher.core.designsystem.contract.ModalFocusLifecycle
import dev.handheld.launcher.core.designsystem.controls.LauncherButton
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalComposeUiApi::class, ExperimentalTestApi::class)
class LauncherModalTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun modalFocusAndTouchesStayInsideAndLifecycleDoesNotRestartOnCallbackChange() {
        var visible by mutableStateOf(false)
        var revision by mutableStateOf(0)
        var shown = 0
        var dismissed = 0
        var dismissedRevision = -1
        var backgroundClicks = 0
        var actionClicks = 0
        lateinit var inputMode: InputModeManager
        lateinit var focus: FocusManager
        compose.setContent {
            LauncherTheme(referenceScale = 2f / 3f) {
                inputMode = LocalInputModeManager.current
                focus = LocalFocusManager.current
                Box(Modifier.fillMaxSize()) {
                    LauncherButton("Behind", { backgroundClicks++ }, Modifier.testTag("behind"))
                    val callbackRevision = revision
                    LauncherDialog("Item actions", { visible = false }, visible = visible,
                        lifecycle = ModalFocusLifecycle(
                            { shown++ },
                            { dismissed++; dismissedRevision = callbackRevision },
                        )) {
                        ItemActionList(listOf(
                            ItemAction("Unavailable action", false) {},
                            ItemAction("Open", onActivate = { actionClicks++ }),
                        ))
                    }
                }
            }
        }
        compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard); visible = true }
        compose.onNodeWithText("Open").assertIsFocused()
        // A physical touch at the background button's bounds must hit the backdrop.
        compose.onNodeWithTag("behind").performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, backgroundClicks) }
        // The panel's child gesture must still arrive, unlike an ancestor gesture sink.
        compose.onNodeWithText("Open").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, actionClicks); revision = 1 }
        compose.runOnIdle { assertEquals(1, shown); assertEquals(0, dismissed) }
        compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard); focus.moveFocus(FocusDirection.Enter) }
        listOf(FocusDirection.Left, FocusDirection.Right, FocusDirection.Up, FocusDirection.Down,
            FocusDirection.Next, FocusDirection.Previous).forEach { direction ->
            compose.runOnIdle { repeat(5) { focus.moveFocus(direction) } }
            compose.onNodeWithTag("behind").assertIsNotFocused()
            compose.onAllNodes(isFocused()).assertCountEquals(1)
        }
        compose.onNodeWithText("Close").performClick()
        compose.runOnIdle {
            assertEquals(1, shown)
            assertEquals(1, dismissed)
            assertEquals(1, dismissedRevision)
        }
    }

    @Test
    fun emptyModalFocusesCloseAndLongActionsScrollWithoutHidingClose() {
        var visible by mutableStateOf(false)
        var longContent by mutableStateOf(false)
        lateinit var inputMode: InputModeManager
        compose.setContent {
            LauncherTheme(referenceScale = 2f / 3f) {
                inputMode = LocalInputModeManager.current
                LauncherDialog("Available actions", {}, Modifier.testTag("panel"), visible) {
                    if (longContent) ItemActionList((0..25).map {
                        ItemAction("Action $it with a long explanatory label", onActivate = {})
                    })
                }
            }
        }
        compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard); visible = true }
        compose.onNodeWithText("Close").assertIsFocused().assertIsDisplayed()
        compose.runOnIdle { longContent = true }
        compose.onNodeWithText("Action 25 with a long explanatory label")
            .performScrollTo()
        compose.onNodeWithText("Action 25 with a long explanatory label").assertIsDisplayed()
        compose.onNodeWithText("Close").assertIsDisplayed()
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val panel = compose.onNodeWithTag("panel").fetchSemanticsNode().boundsInRoot
        assertTrue(panel.top >= root.top && panel.bottom <= root.bottom)
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
        File(dir, "us009-dialog.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun compactCloseIsAFullActionAndDismissalRestoresTheOriginOnce() {
        var visible by mutableStateOf(false)
        var dismissed by mutableStateOf(0)
        val origin = FocusRequester()
        lateinit var inputMode: InputModeManager
        compose.setContent {
            LauncherTheme(referenceScale = 2f / 3f) {
                inputMode = LocalInputModeManager.current
                Box(Modifier.fillMaxSize()) {
                    LauncherButton("Open chooser", { visible = true }, Modifier.focusRequester(origin))
                    LauncherDialog("Choose", { visible = false }, visible = visible,
                        compactDismiss = true, compactDismissScale = .85f,
                        lifecycle = ModalFocusLifecycle({}, { dismissed++ })) {}
                }
                LaunchedEffect(dismissed) {
                    if (dismissed > 0) { withFrameNanos { }; origin.requestFocus() }
                }
            }
        }
        compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard); origin.requestFocus() }
        compose.onNodeWithText("Open chooser").performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithText("Close").assertIsFocused().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(80.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithText("Open chooser").assertIsFocused()
        compose.runOnIdle { assertEquals(1, dismissed) }
    }
}
