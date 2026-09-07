package dev.handheld.launcher.core.domain.model

enum class ControllerFaceButton {
    A,
    B,
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
