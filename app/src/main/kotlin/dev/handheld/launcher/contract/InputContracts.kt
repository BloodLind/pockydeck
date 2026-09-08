package dev.handheld.launcher.contract

/** Logical input after physical controller/touch normalization. */
enum class SemanticInputAction {
    NAVIGATE_UP,
    NAVIGATE_DOWN,
    NAVIGATE_LEFT,
    NAVIGATE_RIGHT,
    CONFIRM,
    BACK,
    SECONDARY,
    TERTIARY,
    MENU,
    PREVIOUS_DESTINATION,
    NEXT_DESTINATION,
    PREVIOUS_FILTER,
    NEXT_FILTER,
}

/** Meaning rendered by the footer and invoked by the semantic input path. */
enum class LauncherActionMeaning {
    ACTIVATE,
    GO_BACK,
    OPEN_DETAILS,
    TOGGLE_FAVORITE,
    OPEN_SEARCH,
    CLEAR_SEARCH,
    OPEN_SETTINGS,
    OPEN_MENU,
    DISMISS,
    CHANGE_DESTINATION,
    CHANGE_FILTER,
}

data class LauncherActionDescriptor(
    val input: SemanticInputAction,
    val meaning: LauncherActionMeaning,
    val label: String,
    val enabled: Boolean = true,
) {
    init {
        require(label.isNotBlank()) { "Action labels must not be blank" }
    }
}

/** The footer renders these exact descriptors; it does not infer another action meaning. */
data class ControllerActionFooter(
    val actions: List<LauncherActionDescriptor>,
) {
    init {
        require(actions.map { it.input }.distinct().size == actions.size) {
            "A footer cannot assign two meanings to the same semantic input"
        }
    }
}

fun interface SemanticActionPort {
    /** Returns true only when this exact descriptor was handled. */
    fun dispatch(action: LauncherActionDescriptor): Boolean
}

enum class InputDispatchOwner {
    IME,
    MODAL,
    FOCUSED_PAGE,
    SHELL,
}

/** A normalized input has one owner; handling stops at the first owner that accepts it. */
object InputDispatchPrecedence {
    val orderedOwners: List<InputDispatchOwner> = listOf(
        InputDispatchOwner.IME,
        InputDispatchOwner.MODAL,
        InputDispatchOwner.FOCUSED_PAGE,
        InputDispatchOwner.SHELL,
    )
}
