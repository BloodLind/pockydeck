package dev.handheld.launcher.feature.home

import androidx.compose.runtime.Composable
import dev.handheld.launcher.ui.artwork.RomArtworkBackdrop
import dev.handheld.launcher.ui.artwork.romBackdropEligible
import dev.handheld.launcher.ui.presentation.TileUiModel

internal fun homeBackdropEligible(model: TileUiModel?): Boolean = romBackdropEligible(model)

@Composable
internal fun HomeRomBackdrop(selected: TileUiModel?, enabled: Boolean, loadingAllowed: Boolean) =
    RomArtworkBackdrop(selected, enabled, loadingAllowed, testTag = "home-rom-backdrop")
