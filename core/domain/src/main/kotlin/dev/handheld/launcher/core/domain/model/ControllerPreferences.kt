package dev.handheld.launcher.core.domain.model

enum class ControllerFaceButton {
    A,
    B,
}

/** Printed labels for Android's standard face-button positions; input bindings stay unchanged. */
enum class ControllerButtonLayout(
    val persistedKey: String,
    val bottomLabel: String,
    val rightLabel: String,
    val leftLabel: String,
    val topLabel: String,
) {
    XBOX("xbox", "A", "B", "X", "Y"),
    NINTENDO("nintendo", "B", "A", "Y", "X");

    fun labelFor(button: ControllerFaceButton): String = when (button) {
        ControllerFaceButton.A -> bottomLabel
        ControllerFaceButton.B -> rightLabel
    }

    companion object {
        val Default = XBOX
        fun fromPersistedKey(value: String?): ControllerButtonLayout =
            entries.firstOrNull { it.persistedKey == value } ?: Default
    }
}

enum class ControllerButtonRole {
    CONFIRM,
    BACK,
}

data class ConfirmBackMapping(
    val confirm: ControllerFaceButton,
    val back: ControllerFaceButton,
) {
    init {
        require(confirm != back) { "Confirm and Back must use different face buttons" }
    }

    companion object {
        val Default = ConfirmBackMapping(
            confirm = ControllerFaceButton.A,
            back = ControllerFaceButton.B,
        )
    }

    fun buttonFor(role: ControllerButtonRole): ControllerFaceButton = when (role) {
        ControllerButtonRole.CONFIRM -> confirm
        ControllerButtonRole.BACK -> back
    }

    fun roleFor(button: ControllerFaceButton): ControllerButtonRole = when (button) {
        confirm -> ControllerButtonRole.CONFIRM
        back -> ControllerButtonRole.BACK
        else -> error("ConfirmBackMapping must cover both A and B")
    }
}
