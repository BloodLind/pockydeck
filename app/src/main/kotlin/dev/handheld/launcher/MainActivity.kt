package dev.handheld.launcher

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.view.inputmethod.EditorInfo
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.handheld.launcher.audio.ControllerSoundEffects
import dev.handheld.launcher.audio.ControllerSoundPreferences
import dev.handheld.launcher.contract.ActivityRequestAcknowledgement
import dev.handheld.launcher.contract.SemanticInputAction
import dev.handheld.launcher.core.domain.model.LauncherDestination
import dev.handheld.launcher.core.data.android.status.NotificationStatusAccess
import dev.handheld.launcher.di.LauncherAppViewModel
import dev.handheld.launcher.di.LauncherApplication
import dev.handheld.launcher.feature.home.HomeViewModel
import dev.handheld.launcher.input.ControllerInputHandler
import dev.handheld.launcher.platform.home.HomeRoleActivityRequestHandler
import dev.handheld.launcher.platform.home.RequestHomeRoleSelection
import dev.handheld.launcher.ui.LauncherApp
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
class MainActivity : ComponentActivity() {
    private val container get() = (application as LauncherApplication).appContainer
    private val appViewModel: LauncherAppViewModel by viewModels { container.launcherViewModelFactory() }
    private val homeViewModel: HomeViewModel by viewModels { container.homeViewModelFactory() }
    private var reducedMotion by mutableStateOf(false)
    private var homeRoleHeld by mutableStateOf(false)
    private var notificationAccessGranted by mutableStateOf(false)
    private var dispatchSemantic: (SemanticInputAction) -> Boolean = { false }
    private var touchInput: () -> Unit = {}
    private var searchEditorActive = false
    private var searchClearEnabled = false
    private var foregroundResumed = false
    private val soundPreferences by lazy { ControllerSoundPreferences(this) }
    private val controllerSounds by lazy { ControllerSoundEffects(this) { soundPreferences.enabled.value } }
    private val notificationAccess by lazy { NotificationStatusAccess(this) }
    private lateinit var inputHost: FrameLayout
    private val nativeFocusFallback = ViewTreeObserver.OnGlobalFocusChangeListener { _, next ->
        if (next == null) inputHost.post { retainNativeInputFocus() }
    }
    private val inlineTextInput = PlatformTextInputInterceptor { request, next ->
        next.startInputMethod { attributes ->
            request.createInputConnection(attributes).also {
                // Keep Search and its Apply/Cancel footer visible in landscape keyboards.
                attributes.imeOptions = attributes.imeOptions or EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            }
        }
    }
    private lateinit var roleHandler: HomeRoleActivityRequestHandler
    private val controller by lazy {
        ControllerInputHandler(lifecycleScope, { appViewModel.mapping.value }, {
            ViewCompat.getRootWindowInsets(window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }, imeFaceActionsEnabled = { searchEditorActive },
            onSearchClearEnabled = { searchClearEnabled },
            dispatch = { controllerSounds.dispatch(it, dispatchSemantic) })
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
    private val storageAccessSettings = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        container.romController.refreshStorageAccess()
        container.romScanner.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = AudioManager.STREAM_MUSIC
        controllerSounds.prepare()
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
        // Android's early post-IME stage can consume the first directional key solely to
        // leave touch mode. Own launcher controls before that stage and keep a native focus
        // target when touch clears the Compose leaf; text/caret keys still pass to the IME.
        inputHost = object : FrameLayout(this) {
            override fun dispatchKeyEventPreIme(event: KeyEvent): Boolean =
                if (controller.onKeyEvent(event)) true else super.dispatchKeyEventPreIme(event)
        }.apply {
            isSoundEffectsEnabled = false
            isFocusableInTouchMode = true
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val content = ComposeView(this).apply {
            isSoundEffectsEnabled = false
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
        inputHost.addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(inputHost)
        inputHost.viewTreeObserver.addOnGlobalFocusChangeListener(nativeFocusFallback)
        content.setContent {
          val controllerSoundsEnabled by soundPreferences.enabled.collectAsStateWithLifecycle()
          InterceptPlatformTextInput(inlineTextInput) {
            LauncherApp(container, appViewModel, homeViewModel, reducedMotion, homeRoleHeld,
                bindInput = { dispatchSemantic = it }, nativeConfirm = ::activateNativeFocusedControl,
                bindTouchInput = { touchInput = it },
                onImeVisibilityChanged = controller::onImeVisibilityChanged,
                onSearchEditorActiveChanged = { searchEditorActive = it },
                onSearchClearEnabledChanged = { searchClearEnabled = it },
                notificationAccessGranted = notificationAccessGranted,
                onSetupNotificationAccess = ::setupNotificationAccess,
                onPickRomFolder = ::pickRomFolder,
                onSetupStorageAccess = ::setupStorageAccess,
                controllerSoundsEnabled = controllerSoundsEnabled,
                onSetControllerSoundsEnabled = ::setControllerSoundsEnabled,
                onExpectItemSelection = controllerSounds::expectItemSelection,
                onItemSelected = { controllerSounds.onItemSelected() })
          }
        }
        // The inner AndroidComposeView can emit a native DPAD click itself. Scope its
        // suppression to this app's view tree so our Confirm cue remains a single sound.
        disableNativeClickSounds(inputHost)
        inputHost.post { disableNativeClickSounds(inputHost) }
    }

    // This is the public Android Window.Callback hook. Core ComponentActivity's inherited
    // library-group annotation is not an application-level restriction on overriding it.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        if (controller.onKeyEvent(event)) true else super.dispatchKeyEvent(event)

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        if (controller.onMotionEvent(event)) true else super.dispatchGenericMotionEvent(event)

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            controller.reset()
            touchInput()
            if (!searchEditorActive) inputHost.requestFocus()
        }
        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP) inputHost.post { retainNativeInputFocus() }
        return handled
    }

    private fun retainNativeInputFocus() {
        if (!searchEditorActive && inputHost.hasWindowFocus() && currentFocus == null) {
            // A route can clear Compose/native focus after the touch-down fallback. Keep
            // pre-IME dispatch reachable without implicitly selecting a Compose control.
            inputHost.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            try { inputHost.requestFocus() }
            finally { inputHost.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS }
        }
    }

    private fun disableNativeClickSounds(view: View) {
        view.isSoundEffectsEnabled = false
        if (view is ViewGroup) for (index in 0 until view.childCount) disableNativeClickSounds(view.getChildAt(index))
    }

    private fun setControllerSoundsEnabled(enabled: Boolean) {
        soundPreferences.setEnabled(enabled)
        if (!enabled) controllerSounds.stop()
    }

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
        foregroundResumed = true
        controllerSounds.setActive(window.decorView.hasWindowFocus())
        reducedMotion = !ValueAnimator.areAnimatorsEnabled()
        homeRoleHeld = roleHandler.isHomeRoleHeld()
        notificationAccessGranted = notificationAccess.isAccessGranted()
        if (appViewModel.navigation.location.value == dev.handheld.launcher.core.domain.model.LauncherLocation.Destination(LauncherDestination.HOME))
            homeViewModel.returnToStart()
        container.androidCatalog.onResume()
        container.romController.refreshStorageAccess()
        container.romScanner.refresh()
        container.romController.refreshEmulators()
        immersiveWindow()
    }
    override fun onPause() {
        foregroundResumed = false
        controllerSounds.setActive(false)
        controller.reset()
        super.onPause()
    }
    override fun onStop() { container.androidCatalog.stop(); super.onStop() }
    override fun onDestroy() {
        controllerSounds.release()
        if (::inputHost.isInitialized && inputHost.viewTreeObserver.isAlive)
            inputHost.viewTreeObserver.removeOnGlobalFocusChangeListener(nativeFocusFallback)
        super.onDestroy()
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        controllerSounds.setActive(foregroundResumed && hasFocus)
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

    private fun setupStorageAccess() {
        val appSettings = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:$packageName"))
        try {
            storageAccessSettings.launch(appSettings)
        } catch (_: RuntimeException) {
            try { storageAccessSettings.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
            catch (_: RuntimeException) {
                appViewModel.error.value = "Android could not open storage access settings. You can still add ROM folders manually."
            }
        }
    }

    private fun setupNotificationAccess() {
        val intent = notificationAccess.settingsIntent()
        if (intent == null) {
            appViewModel.error.value = "Notification access settings are unavailable on this device."
            return
        }
        try { startActivity(intent) }
        catch (_: RuntimeException) {
            appViewModel.error.value = "Android could not open Notification access settings."
        }
    }
}
