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
    onSearchClearEnabled: () -> Boolean = { false },
    dispatch: (SemanticInputAction) -> Boolean,
) {
    private val engine = ControllerInputEngine(scope, mapping, imeVisible, imeFaceActionsEnabled,
        onSearchClearEnabled = onSearchClearEnabled, dispatch = dispatch)

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
            KeyEvent.ACTION_DOWN -> engine.onButtonDown(button, event.repeatCount > 0, event.eventTime, event.deviceId)
            KeyEvent.ACTION_UP -> engine.onButtonUp(button, event.deviceId, event.eventTime)
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
                deviceId = event.deviceId,
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
    val deviceId: Int = 0,
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
    private val onSearchClearEnabled: () -> Boolean = { false },
    private val monotonicTimeMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    private val dispatch: (SemanticInputAction) -> Boolean,
) {
    private class DeviceState {
        val downOwners = mutableMapOf<ControllerButton, Boolean>()
        val heldButtons = linkedMapOf<ControllerButton, Long>()
        val keyEventTimes = mutableMapOf<ControllerButton, Long>()
        var axisEventTime = Long.MIN_VALUE
        var hat = Direction.None
        var stick = Direction.None
        var axisOrder = 0L
        var leftAnalog = false
        var rightAnalog = false
        var suppressHat = false
        var suppressStick = false
        var suppressLeft = false
        var suppressRight = false

        val idle get() = downOwners.isEmpty() && heldButtons.isEmpty() && hat == Direction.None &&
            stick == Direction.None && !leftAnalog && !rightAnalog
    }

    private class RepeatSession(val action: SemanticInputAction, val startedAt: Long, val holdDelayMillis: Long) {
        var job: Job? = null
    }

    private val devices = linkedMapOf<Int, DeviceState>()
    private var inputOrder = 0L
    private var generation = 0L
    private var direction = Direction.None
    private var directionRepeat: RepeatSession? = null
    private var leftRepeat: RepeatSession? = null
    private var rightRepeat: RepeatSession? = null
    private var leftTriggerHeld = false
    private var rightTriggerHeld = false

    private fun device(id: Int): DeviceState? {
        devices[id]?.let { return it }
        if (devices.size >= MAX_TRACKED_DEVICES) {
            val idle = devices.entries.firstOrNull { it.value.idle } ?: return null
            devices.remove(idle.key)
        }
        return DeviceState().also { devices[id] = it }
    }

    fun onButtonDown(
        button: ControllerButton,
        repeatedByAndroid: Boolean,
        eventTimeMillis: Long,
        deviceId: Int = 0,
    ): Boolean {
        val state = device(deviceId) ?: return false
        if (eventTimeMillis < (state.keyEventTimes[button] ?: Long.MIN_VALUE)) return state.downOwners[button] ?: true
        state.keyEventTimes[button] = eventTimeMillis
        // BUTTON_A/B are gamepad keycodes, distinct from keyboard letters, Enter and Backspace.
        val editorAction = (button == ControllerButton.A || button == ControllerButton.B) && imeFaceActionsEnabled() ||
            button == ControllerButton.Y && onSearchClearEnabled()
        if (imeVisible()) {
            clearActiveState(clearDownOwnership = false)
            state.downOwners[button]?.let { return it }
            if (!editorAction) {
                state.downOwners[button] = false
                return false
            }
        }
        state.downOwners[button]?.let { return it }
        state.downOwners[button] = true
        // Android repeats never create/restart an engagement, including a repeat arriving after
        // reset without a new physical DOWN. Its matching UP still belongs to the launcher.
        if (repeatedByAndroid) return true
        state.heldButtons[button] = ++inputOrder
        when {
            button.direction != null -> updateDirection()
            button == ControllerButton.LeftTrigger || button == ControllerButton.RightTrigger -> updateTriggers()
            else -> dispatch(button.semantic(mapping()))
        }
        return true
    }

    fun onButtonUp(button: ControllerButton, deviceId: Int = 0, eventTimeMillis: Long? = null): Boolean {
        val state = devices[deviceId] ?: return false
        if (eventTimeMillis != null) {
            if (eventTimeMillis < (state.keyEventTimes[button] ?: Long.MIN_VALUE)) return state.downOwners[button] ?: true
            state.keyEventTimes[button] = eventTimeMillis
        }
        val launcherOwned = state.downOwners.remove(button) ?: return false
        if (!launcherOwned) return false
        state.heldButtons.remove(button)
        if (imeVisible()) clearActiveState(clearDownOwnership = false)
        else if (button.direction != null) updateDirection()
        else if (button == ControllerButton.LeftTrigger || button == ControllerButton.RightTrigger) updateTriggers()
        return true
    }

    fun onAxes(axes: ControllerAxes): Boolean {
        val state = device(axes.deviceId) ?: return false
        if (axes.eventTimeMillis < state.axisEventTime) return !imeVisible()
        state.axisEventTime = axes.eventTimeMillis
        val hat = Direction.fromAxes(axes.hatX, axes.hatY, state.hat, HAT_THRESHOLD, HAT_EXIT_THRESHOLD)
        val stick = Direction.fromAxes(axes.stickX, axes.stickY, state.stick, STICK_ENTER_THRESHOLD, STICK_EXIT_THRESHOLD)
        if (hat != state.hat || stick != state.stick) state.axisOrder = ++inputOrder
        state.hat = hat
        state.stick = stick
        val left = axes.leftTrigger.normalizedTrigger()
        val right = axes.rightTrigger.normalizedTrigger()
        state.leftAnalog = left > if (state.leftAnalog) TRIGGER_EXIT_THRESHOLD else TRIGGER_ENTER_THRESHOLD
        state.rightAnalog = right > if (state.rightAnalog) TRIGGER_EXIT_THRESHOLD else TRIGGER_ENTER_THRESHOLD
        // A held axis from an old page/IME/lifecycle cannot re-engage until it returns neutral.
        if (hat == Direction.None) state.suppressHat = false
        if (stick == Direction.None) state.suppressStick = false
        if (!state.leftAnalog) state.suppressLeft = false
        if (!state.rightAnalog) state.suppressRight = false
        if (imeVisible()) {
            clearActiveState(clearDownOwnership = false)
            return false
        }
        updateDirection()
        updateTriggers()
        return true
    }

    fun onImeShown() {
        clearActiveState(clearDownOwnership = false)
    }

    fun reset() {
        clearActiveState(clearDownOwnership = true)
    }

    private fun clearActiveState(clearDownOwnership: Boolean) {
        generation++
        devices.values.forEach { state ->
            state.suppressHat = state.suppressHat || state.hat != Direction.None || state.heldButtons.keys.any { it.direction != null }
            state.suppressStick = state.suppressStick || state.stick != Direction.None
            state.suppressLeft = state.suppressLeft || state.leftAnalog || ControllerButton.LeftTrigger in state.heldButtons
            state.suppressRight = state.suppressRight || state.rightAnalog || ControllerButton.RightTrigger in state.heldButtons
            state.heldButtons.clear()
            if (clearDownOwnership) state.downOwners.clear()
        }
        direction = Direction.None
        leftTriggerHeld = false
        rightTriggerHeld = false
        directionRepeat?.job?.cancel()
        leftRepeat?.job?.cancel()
        rightRepeat?.job?.cancel()
        directionRepeat = null
        leftRepeat = null
        rightRepeat = null
    }

    private fun updateDirection() {
        val keyDirection = devices.values.flatMap { it.heldButtons.entries }.filter { it.key.direction != null }
            .maxByOrNull { it.value }?.key?.direction
        // A physical D-pad can report both a key and HAT axis. One resolved direction owns
        // the initial event and repeat job no matter how many sources agree.
        val hatDirection = devices.values.filter { !it.suppressHat && it.hat != Direction.None }.maxByOrNull { it.axisOrder }?.hat
        val stickDirection = devices.values.filter { !it.suppressStick && it.stick != Direction.None }.maxByOrNull { it.axisOrder }?.stick
        val next = keyDirection ?: hatDirection ?: stickDirection ?: Direction.None
        if (next == direction) return
        direction = next
        directionRepeat?.job?.cancel()
        directionRepeat = null
        val action = next.action ?: return
        val session = RepeatSession(action, monotonicTimeMillis(), DIRECTION_REPEAT_DELAY_MILLIS)
        directionRepeat = session
        engage(session) { directionRepeat === session }
    }

    private fun updateTriggers() {
        // A digital edge and any pressure sample describe the same logical hold, even when
        // they arrive hundreds of milliseconds apart. Only a fully released hold can re-arm.
        val left = devices.values.any { ControllerButton.LeftTrigger in it.heldButtons || it.leftAnalog && !it.suppressLeft }
        val right = devices.values.any { ControllerButton.RightTrigger in it.heldButtons || it.rightAnalog && !it.suppressRight }
        val startedGeneration = generation
        if (left != leftTriggerHeld) {
            leftTriggerHeld = left
            leftRepeat?.job?.cancel()
            leftRepeat = null
            if (left) {
                val session = RepeatSession(SemanticInputAction.PREVIOUS_FILTER, monotonicTimeMillis(), TRIGGER_REPEAT_DELAY_MILLIS)
                leftRepeat = session
                engage(session) { leftRepeat === session }
            }
        }
        if (generation != startedGeneration) return
        if (right != rightTriggerHeld) {
            rightTriggerHeld = right
            rightRepeat?.job?.cancel()
            rightRepeat = null
            if (right) {
                val session = RepeatSession(SemanticInputAction.NEXT_FILTER, monotonicTimeMillis(), TRIGGER_REPEAT_DELAY_MILLIS)
                rightRepeat = session
                engage(session) { rightRepeat === session }
            }
        }
    }

    private fun engage(session: RepeatSession, stillOwner: () -> Boolean) {
        dispatch(session.action)
        // Dispatch can synchronously open the IME or reset the handler while changing pages.
        if (!stillOwner()) return
        if (imeVisible()) { clearActiveState(clearDownOwnership = false); return }
        session.job = scope.launch {
            delay((session.holdDelayMillis - (monotonicTimeMillis() - session.startedAt)).coerceAtLeast(0))
            while (isActive && stillOwner()) {
                if (imeVisible()) { clearActiveState(clearDownOwnership = false); break }
                dispatch(session.action)
                if (!stillOwner()) break
                val heldMillis = (monotonicTimeMillis() - session.startedAt).coerceAtLeast(0)
                val accelerated = (heldMillis - session.holdDelayMillis).coerceIn(0, ACCELERATION_MILLIS)
                val interval = REPEAT_INTERVAL_MILLIS -
                    (REPEAT_INTERVAL_MILLIS - MIN_REPEAT_INTERVAL_MILLIS) * accelerated / ACCELERATION_MILLIS
                delay(interval)
            }
        }
    }

    private enum class Direction(val action: SemanticInputAction?) {
        None(null), Up(SemanticInputAction.NAVIGATE_UP), Down(SemanticInputAction.NAVIGATE_DOWN),
        Left(SemanticInputAction.NAVIGATE_LEFT), Right(SemanticInputAction.NAVIGATE_RIGHT),
        ;

        companion object {
            fun fromAxes(x: Float, y: Float, previous: Direction, enter: Float, exit: Float): Direction {
                if (!x.isFinite() || !y.isFinite()) return None
                val previousAmount = when (previous) { Left -> -x; Right -> x; Up -> -y; Down -> y; None -> 0f }
                val otherAmount = when (previous) { Left, Right -> abs(y); Up, Down -> abs(x); None -> 0f }
                if (previous != None && previousAmount >= exit && otherAmount <= previousAmount + DIRECTION_SWITCH_MARGIN) return previous
                if (max(abs(x), abs(y)) < enter) return None
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
        const val HAT_EXIT_THRESHOLD = .25f
        const val DIRECTION_SWITCH_MARGIN = .12f
        const val STICK_ENTER_THRESHOLD = .5f
        const val STICK_EXIT_THRESHOLD = .3f
        const val TRIGGER_ENTER_THRESHOLD = .55f
        const val TRIGGER_EXIT_THRESHOLD = .25f
        const val DIRECTION_REPEAT_DELAY_MILLIS = 360L
        const val TRIGGER_REPEAT_DELAY_MILLIS = 650L
        const val REPEAT_INTERVAL_MILLIS = 115L
        const val MIN_REPEAT_INTERVAL_MILLIS = 55L
        const val ACCELERATION_MILLIS = 2_500L
        const val MAX_TRACKED_DEVICES = 32
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
