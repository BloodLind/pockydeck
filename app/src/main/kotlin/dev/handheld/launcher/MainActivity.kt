package dev.handheld.launcher

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.handheld.launcher.contract.ActivityRequestAcknowledgement
import dev.handheld.launcher.contract.SemanticInputAction
import dev.handheld.launcher.core.domain.model.LauncherDestination
import dev.handheld.launcher.di.LauncherAppViewModel
import dev.handheld.launcher.di.LauncherApplication
import dev.handheld.launcher.feature.home.HomeViewModel
import dev.handheld.launcher.input.ControllerInputHandler
import dev.handheld.launcher.platform.home.HomeRoleActivityRequestHandler
import dev.handheld.launcher.platform.home.RequestHomeRoleSelection
import dev.handheld.launcher.ui.LauncherApp
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val container get() = (application as LauncherApplication).appContainer
    private val appViewModel: LauncherAppViewModel by viewModels { container.launcherViewModelFactory() }
    private val homeViewModel: HomeViewModel by viewModels { container.homeViewModelFactory() }
    private var reducedMotion by mutableStateOf(false)
    private var homeRoleHeld by mutableStateOf(false)
    private var dispatchSemantic: (SemanticInputAction) -> Boolean = { false }
    private lateinit var roleHandler: HomeRoleActivityRequestHandler
    private val controller by lazy {
        ControllerInputHandler(lifecycleScope, { appViewModel.mapping.value }, {
            ViewCompat.getRootWindowInsets(window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }, { dispatchSemantic(it) })
    }
    private val rolePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        roleHandler.completeCurrent(it.resultCode)
        homeRoleHeld = roleHandler.isHomeRoleHeld()
    }
    private val romFolderPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.let { returned ->
                returned.data?.let { uri -> container.romController.onTreeSelected(uri, returned.flags) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        immersiveWindow()
        roleHandler = HomeRoleActivityRequestHandler(this, container.activityRequestPort, container.homeRoleRequests)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.activityRequestPort.pending.filterNotNull().collect { pending ->
                    if (pending.request === RequestHomeRoleSelection) {
                        roleHandler.claim(pending)?.let { claim ->
                            try { rolePicker.launch(claim.intent) }
                            catch (_: RuntimeException) { roleHandler.fail(claim, "Android could not open Home selection.") }
                        }
                    } else {
                        container.activityRequestPort.claim(pending.id)?.let { claim ->
                            container.activityRequestPort.complete(claim, ActivityRequestAcknowledgement.Unsupported)
                        }
                    }
                }
            }
        }
        setContent {
            LauncherApp(container, appViewModel, homeViewModel, reducedMotion, homeRoleHeld,
                bindInput = { dispatchSemantic = it }, nativeConfirm = ::activateNativeFocusedControl,
                onImeVisibilityChanged = controller::onImeVisibilityChanged,
                onPickRomFolder = ::pickRomFolder)
        }
    }

    // This is the public Android Window.Callback hook. Core ComponentActivity's inherited
    // library-group annotation is not an application-level restriction on overriding it.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        if (controller.onKeyEvent(event)) true else super.dispatchKeyEvent(event)

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        if (controller.onMotionEvent(event)) true else super.dispatchGenericMotionEvent(event)

    @SuppressLint("RestrictedApi") // Continue through the same public Window.Callback path.
    private fun activateNativeFocusedControl(): Boolean {
        val down = super.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        val up = super.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
        return down || up
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            appViewModel.navigation.selectDestination(LauncherDestination.HOME)
        }
    }

    override fun onStart() {
        super.onStart()
        container.androidCatalog.start()
        container.romScanner.refresh()
        container.romController.refreshEmulators()
    }
    override fun onResume() {
        super.onResume()
        reducedMotion = !ValueAnimator.areAnimatorsEnabled()
        homeRoleHeld = roleHandler.isHomeRoleHeld()
        container.androidCatalog.onResume()
        container.romScanner.refresh()
        container.romController.refreshEmulators()
        immersiveWindow()
    }
    override fun onPause() { controller.reset(); super.onPause() }
    override fun onStop() { container.androidCatalog.stop(); super.onStop() }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersiveWindow() else controller.reset()
    }

    private fun immersiveWindow() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun pickRomFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
        )
        try {
            romFolderPicker.launch(intent)
        } catch (_: RuntimeException) {
            appViewModel.error.value = "Android could not open the folder picker. Try again from ROM folders."
        }
    }
}
