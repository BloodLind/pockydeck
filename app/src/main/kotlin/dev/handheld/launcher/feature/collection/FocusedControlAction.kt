package dev.handheld.launcher.feature.collection

import dev.handheld.launcher.contract.LauncherActionDescriptor
import dev.handheld.launcher.core.domain.model.ItemId

/** Binds the footer's typed prompt to the focused production control's exact activation. */
data class FocusedControlAction(
    val descriptor: LauncherActionDescriptor,
    val onActivate: (() -> Unit)?,
    val itemId: ItemId? = null,
    val onAdjust: ((Int) -> Unit)? = null,
    val onBack: (() -> Unit)? = null,
    val backLabel: String = "Done",
    val onAdjustVertical: ((Int) -> Unit)? = null,
)

typealias OnFocusedAction = (FocusedControlAction?) -> Unit
