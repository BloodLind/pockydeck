package dev.handheld.launcher.input

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import dev.handheld.launcher.contract.SemanticInputAction
import dev.handheld.launcher.core.domain.model.ConfirmBackMapping
import dev.handheld.launcher.core.domain.model.ControllerButtonRole
import dev.handheld.launcher.core.domain.model.ControllerFaceButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

/** Activity-local adapter. Unknown and Android-reserved keys always remain with the system. */
class ControllerInputHandler(
    scope: CoroutineScope,
    mapping: () -> ConfirmBackMapping,
    imeVisible: () -> Boolean,
    private val imeFaceActionsEnabled: () -> Boolean = { false },
    dispatch: (SemanticInputAction) -> Boolean,
) {
    private val engine = ControllerInputEngine(scope, mapping, imeVisible, imeFaceActionsEnabled, dispatch)

    fun onKeyEvent(event: KeyEvent): Boolean {
        val button = event.keyCode.toControllerButton() ?: return false
        val keyboardArrow = button in setOf(ControllerButton.DpadUp, ControllerButton.DpadDown,
            ControllerButton.DpadLeft, ControllerButton.DpadRight) &&
            event.isFromSource(InputDevice.SOURCE_KEYBOARD) &&
            !event.isFromSource(InputDevice.SOURCE_DPAD) && !event.isFromSource(InputDevice.SOURCE_GAMEPAD) &&
            !event.isFromSource(InputDevice.SOURCE_JOYSTICK)
        // A hardware keyboard still owns caret navigation when the soft keyboard is hidden.
        // UP follows any previously recorded launcher DOWN, including a focus transition.
        if (event.action == KeyEvent.ACTION_DOWN && imeFaceActionsEnabled() && keyboardArrow) return false
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> engine.onButtonDown(button, event.repeatCount > 0, event.eventTime)
            KeyEvent.ACTION_UP -> engine.onButtonUp(button)
            else -> false
        }
    }

    fun onMotionEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_MOVE ||
            !(event.isFromSource(InputDevice.SOURCE_JOYSTICK) ||
                event.isFromSource(InputDevice.SOURCE_GAMEPAD))
        ) return false
        return engine.onAxes(
            ControllerAxes(
                hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X),
                hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y),
                stickX = event.getAxisValue(MotionEvent.AXIS_X),
                stickY = event.getAxisValue(MotionEvent.AXIS_Y),
                leftTrigger = max(
                    event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
                    event.getAxisValue(MotionEvent.AXIS_BRAKE),
                ),
                rightTrigger = max(
                    event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
                    event.getAxisValue(MotionEvent.AXIS_GAS),
                ),
                eventTimeMillis = event.eventTime,
            ),
        )
    }

    /** Call from Activity.onPause; no held input may survive a foreground transition. */
    fun onPause() = engine.reset()

    /** Call when IME visibility changes so an already-held repeat stops immediately. */
    fun onImeVisibilityChanged(visible: Boolean) {
        if (visible) engine.onImeShown()
    }

    fun reset() = engine.reset()
}

internal data class ControllerAxes(
    val hatX: Float = 0f,
    val hatY: Float = 0f,
    val stickX: Float = 0f,
    val stickY: Float = 0f,
    val leftTrigger: Float = 0f,
    val rightTrigger: Float = 0f,
    val eventTimeMillis: Long,
)

internal enum class ControllerButton {
    DpadUp, DpadDown, DpadLeft, DpadRight,
    A, B, X, Y, Start, LeftShoulder, RightShoulder, LeftTrigger, RightTrigger,
}

/** Android-free state machine used by both key and axis input, and directly by JVM tests. */
internal class ControllerInputEngine(
    private val scope: CoroutineScope,
    private val mapping: () -> ConfirmBackMapping,
    private val imeVisible: () -> Boolean,
    private val imeFaceActionsEnabled: () -> Boolean = { false },
    private val dispatch: (SemanticInputAction) -> Boolean,
) {
    private val heldButtons = linkedSetOf<ControllerButton>()
    private val launcherOwnedDownButtons = mutableMapOf<ControllerButton, Boolean>()
    private var hat = Direction.None
    private var stick = Direction.None
    private var direction = Direction.None
    private var repeatJob: Job? = null
    private var leftTriggerHeld = false
    private var rightTriggerHeld = false
    private val lastActionTime = mutableMapOf<SemanticInputAction, Long>()

    fun onButtonDown(
        button: ControllerButton,
        repeatedByAndroid: Boolean,
        eventTimeMillis: Long,
    ): Boolean {
        launcherOwnedDownButtons[button]?.let { launcherOwned -> return launcherOwned }
        // BUTTON_A/B are gamepad keycodes, distinct from keyboard letters, Enter and Backspace.
        val editorFaceAction = (button == ControllerButton.A || button == ControllerButton.B) && imeFaceActionsEnabled()
        if (imeVisible() && !editorFaceAction) {
            clearActiveState(clearDownOwnership = false)
            launcherOwnedDownButtons[button] = false
            return false
        }
        launcherOwnedDownButtons[button] = true
        if (repeatedByAndroid || !heldButtons.add(button)) return true
        if (button.direction != null) updateDirection()
        else emit(button.semantic(mapping()), eventTimeMillis)
        return true
    }

    fun onButtonUp(button: ControllerButton): Boolean {
        val launcherOwned = launcherOwnedDownButtons.remove(button) ?: return false
        if (!launcherOwned) return false
        heldButtons.remove(button)
        if (button.direction != null) updateDirection()
        return true
    }

    fun onAxes(axes: ControllerAxes): Boolean {
        if (imeVisible()) {
            clearActiveState(clearDownOwnership = false)
            return false
        }
        hat = Direction.fromAxes(axes.hatX, axes.hatY, HAT_THRESHOLD)
        stick = Direction.fromAxes(
            axes.stickX,
            axes.stickY,
            if (stick == Direction.None) STICK_ENTER_THRESHOLD else STICK_EXIT_THRESHOLD,
        )
        updateDirection()
        val left = axes.leftTrigger.normalizedTrigger()
        val right = axes.rightTrigger.normalizedTrigger()
        if (!leftTriggerHeld && left > TRIGGER_ENTER_THRESHOLD) {
            emit(SemanticInputAction.PREVIOUS_FILTER, axes.eventTimeMillis)
        }
        if (!rightTriggerHeld && right > TRIGGER_ENTER_THRESHOLD) {
            emit(SemanticInputAction.NEXT_FILTER, axes.eventTimeMillis)
        }
        leftTriggerHeld = if (leftTriggerHeld) left > TRIGGER_EXIT_THRESHOLD
            else left > TRIGGER_ENTER_THRESHOLD
        rightTriggerHeld = if (rightTriggerHeld) right > TRIGGER_EXIT_THRESHOLD
            else right > TRIGGER_ENTER_THRESHOLD
        return true
    }

    fun onImeShown() {
        clearActiveState(clearDownOwnership = false)
    }

    fun reset() {
        clearActiveState(clearDownOwnership = true)
    }

    private fun clearActiveState(clearDownOwnership: Boolean) {
        heldButtons.clear()
        if (clearDownOwnership) launcherOwnedDownButtons.clear()
        hat = Direction.None
        stick = Direction.None
        direction = Direction.None
        leftTriggerHeld = false
        rightTriggerHeld = false
        repeatJob?.cancel()
        repeatJob = null
        lastActionTime.clear()
    }

    private fun updateDirection() {
        val keyDirection = heldButtons.lastOrNull { it.direction != null }?.direction
        // A physical D-pad can report both a key and HAT axis. One resolved direction owns
        // the initial event and repeat job no matter how many sources agree.
        val next = keyDirection ?: hat.takeUnless { it == Direction.None } ?: stick
        if (next == direction) return
        direction = next
        repeatJob?.cancel()
        repeatJob = null
        val action = next.action ?: return
        dispatch(action)
        repeatJob = scope.launch {
            delay(INITIAL_REPEAT_DELAY_MILLIS)
            while (isActive && direction == next && !imeVisible()) {
                dispatch(action)
                delay(REPEAT_INTERVAL_MILLIS)
            }
        }
    }

    private fun emit(action: SemanticInputAction, eventTimeMillis: Long) {
        // Complete key DOWN/UP pairs are separate presses. Only triggers can report the
        // same activation through both a key and an analog axis and need time deduplication.
        if (action != SemanticInputAction.PREVIOUS_FILTER && action != SemanticInputAction.NEXT_FILTER) {
            dispatch(action)
            return
        }
        // Trigger key and analog reports can describe the same physical edge.
        val previous = lastActionTime[action]
        if (previous != null && eventTimeMillis >= previous &&
            eventTimeMillis - previous < CROSS_SOURCE_DEDUP_MILLIS
        ) return
        lastActionTime[action] = eventTimeMillis
        dispatch(action)
    }

    private enum class Direction(val action: SemanticInputAction?) {
        None(null), Up(SemanticInputAction.NAVIGATE_UP), Down(SemanticInputAction.NAVIGATE_DOWN),
        Left(SemanticInputAction.NAVIGATE_LEFT), Right(SemanticInputAction.NAVIGATE_RIGHT),
        ;

        companion object {
            fun fromAxes(x: Float, y: Float, threshold: Float): Direction {
                if (!x.isFinite() || !y.isFinite() || max(abs(x), abs(y)) < threshold) return None
                return if (abs(x) >= abs(y)) {
                    if (x < 0) Left else Right
                } else {
                    if (y < 0) Up else Down
                }
            }
        }
    }

    private val ControllerButton.direction: Direction?
        get() = when (this) {
            ControllerButton.DpadUp -> Direction.Up
            ControllerButton.DpadDown -> Direction.Down
            ControllerButton.DpadLeft -> Direction.Left
            ControllerButton.DpadRight -> Direction.Right
            else -> null
        }

    private fun ControllerButton.semantic(mapping: ConfirmBackMapping): SemanticInputAction = when (this) {
        ControllerButton.A -> mapping.faceButtonAction(ControllerFaceButton.A)
        ControllerButton.B -> mapping.faceButtonAction(ControllerFaceButton.B)
        ControllerButton.X -> SemanticInputAction.SECONDARY
        ControllerButton.Y -> SemanticInputAction.TERTIARY
        ControllerButton.Start -> SemanticInputAction.MENU
        ControllerButton.LeftShoulder -> SemanticInputAction.PREVIOUS_DESTINATION
        ControllerButton.RightShoulder -> SemanticInputAction.NEXT_DESTINATION
        ControllerButton.LeftTrigger -> SemanticInputAction.PREVIOUS_FILTER
        ControllerButton.RightTrigger -> SemanticInputAction.NEXT_FILTER
        ControllerButton.DpadUp -> SemanticInputAction.NAVIGATE_UP
        ControllerButton.DpadDown -> SemanticInputAction.NAVIGATE_DOWN
        ControllerButton.DpadLeft -> SemanticInputAction.NAVIGATE_LEFT
        ControllerButton.DpadRight -> SemanticInputAction.NAVIGATE_RIGHT
    }

    private fun ConfirmBackMapping.faceButtonAction(button: ControllerFaceButton): SemanticInputAction =
        when (roleFor(button)) {
            ControllerButtonRole.CONFIRM -> SemanticInputAction.CONFIRM
            ControllerButtonRole.BACK -> SemanticInputAction.BACK
        }

    private fun Float.normalizedTrigger(): Float = if (isFinite()) coerceIn(0f, 1f) else 0f

    private companion object {
        const val HAT_THRESHOLD = .45f
        const val STICK_ENTER_THRESHOLD = .5f
        const val STICK_EXIT_THRESHOLD = .3f
        const val TRIGGER_ENTER_THRESHOLD = .55f
        const val TRIGGER_EXIT_THRESHOLD = .25f
        const val INITIAL_REPEAT_DELAY_MILLIS = 360L
        const val REPEAT_INTERVAL_MILLIS = 115L
        const val CROSS_SOURCE_DEDUP_MILLIS = 65L
    }
}

private fun Int.toControllerButton(): ControllerButton? = when (this) {
    KeyEvent.KEYCODE_DPAD_UP -> ControllerButton.DpadUp
    KeyEvent.KEYCODE_DPAD_DOWN -> ControllerButton.DpadDown
    KeyEvent.KEYCODE_DPAD_LEFT -> ControllerButton.DpadLeft
    KeyEvent.KEYCODE_DPAD_RIGHT -> ControllerButton.DpadRight
    KeyEvent.KEYCODE_BUTTON_A -> ControllerButton.A
    KeyEvent.KEYCODE_BUTTON_B -> ControllerButton.B
    KeyEvent.KEYCODE_BUTTON_X -> ControllerButton.X
    KeyEvent.KEYCODE_BUTTON_Y -> ControllerButton.Y
    KeyEvent.KEYCODE_BUTTON_START -> ControllerButton.Start
    KeyEvent.KEYCODE_BUTTON_L1 -> ControllerButton.LeftShoulder
    KeyEvent.KEYCODE_BUTTON_R1 -> ControllerButton.RightShoulder
    KeyEvent.KEYCODE_BUTTON_L2 -> ControllerButton.LeftTrigger
    KeyEvent.KEYCODE_BUTTON_R2 -> ControllerButton.RightTrigger
    else -> null
}
