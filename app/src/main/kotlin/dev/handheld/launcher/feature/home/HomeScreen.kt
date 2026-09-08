package dev.handheld.launcher.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.handheld.launcher.contract.LauncherActionDescriptor
import dev.handheld.launcher.contract.LauncherActionMeaning
import dev.handheld.launcher.contract.SemanticInputAction
import dev.handheld.launcher.core.data.discovery.AndroidCatalogRefreshState
import dev.handheld.launcher.core.designsystem.cards.AppIconTile
import dev.handheld.launcher.core.designsystem.controls.LauncherButton
import dev.handheld.launcher.core.designsystem.controls.PlatformBadge
import dev.handheld.launcher.ui.presentation.platformAccent
import dev.handheld.launcher.core.designsystem.foundation.LauncherText
import dev.handheld.launcher.core.designsystem.foundation.ShellMetrics
import dev.handheld.launcher.core.designsystem.glyphs.LauncherGlyph
import dev.handheld.launcher.core.designsystem.glyphs.LauncherGlyphIcon
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.ui.artwork.local.AndroidIconLoader
import dev.handheld.launcher.ui.components.LibraryItemCard
import dev.handheld.launcher.ui.components.LibraryItemCardVariant
import kotlinx.coroutines.flow.distinctUntilChanged

data class HomeFocusedAction(
    val descriptor: LauncherActionDescriptor,
    val itemId: ItemId?,
    val onActivate: () -> Unit,
    val onOpenDetails: (() -> Unit)? = null,
)

@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    metrics: ShellMetrics,
    iconLoader: AndroidIconLoader,
    modifier: Modifier = Modifier,
    onOpenLibrary: () -> Unit,
    onOpenDetails: (ItemId) -> Unit,
    allowFocusRequest: Boolean = true,
    pageActivationRequest: Int = 1,
    onFocusedActionChanged: (HomeFocusedAction?) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        metrics = metrics,
        iconLoader = iconLoader,
        modifier = modifier,
        onSelect = viewModel::select,
        onActivate = { viewModel.activate(it) },
        onOpenLibrary = onOpenLibrary,
        onOpenDetails = onOpenDetails,
        allowFocusRequest = allowFocusRequest,
        pageActivationRequest = pageActivationRequest,
        onRefresh = viewModel::refresh,
        onViewportChanged = viewModel::rememberViewport,
        onFocusedActionChanged = onFocusedActionChanged,
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    metrics: ShellMetrics,
    iconLoader: AndroidIconLoader,
    modifier: Modifier = Modifier,
    onSelect: (ItemId) -> Unit,
    onActivate: (ItemId) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenDetails: (ItemId) -> Unit,
    onRefresh: () -> Unit,
    onViewportChanged: (ItemId?, Int) -> Unit,
    allowFocusRequest: Boolean = true,
    pageActivationRequest: Int = 1,
    onFocusedActionChanged: (HomeFocusedAction?) -> Unit = {},
) {
    val selected = state.selectedItem
    val rowState = rememberLazyListState()
    val itemIds = state.items.map { it.itemId }
    val requesters = remember(itemIds) { itemIds.associateWith { FocusRequester() } }
    val libraryRequester = remember { FocusRequester() }
    val recoveryRequester = remember { FocusRequester() }
    val inputMode = LocalInputModeManager.current
    var focusedTarget by remember { mutableStateOf<HomeFocusTarget?>(null) }
    var initialRestorationComplete by remember { mutableStateOf(false) }
    var handledPageActivation by remember { mutableStateOf(0) }
    var handledFocusRequestSequence by remember {
        mutableLongStateOf(state.focusRequestSequence)
    }
    val density = LocalDensity.current
    val shadowOffset = with(density) { (2.dp * metrics.referenceScale).toPx() }
    val shadowBlur = with(density) { (3.dp * metrics.referenceScale).toPx() }

    Box(modifier) {
        if (selected != null && metrics.hasUsableHomeCard) {
            Column(
                Modifier
                    .offset(y = metrics.homeMetadataTop - metrics.contentBounds.top)
                    .fillMaxWidth()
                    .height(metrics.metadataReservation),
                verticalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.xs),
            ) {
                PlatformBadge(selected.platformLabel, homeAccent = true, accentColor = selected.platformAccent)
                LauncherText(
                    text = selected.title,
                    style = LauncherTheme.typography.homeTitle.copy(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = .4f),
                            offset = Offset(0f, shadowOffset),
                            blurRadius = shadowBlur,
                        ),
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HomeRefreshLabel(state, Modifier.align(Alignment.TopEnd))
        }

        if (state.items.isEmpty()) {
            val empty = state.emptyPresentation()
            HomeRecoveryState(
                title = empty.title,
                message = empty.message,
                actionLabel = empty.actionLabel,
                onAction = if (empty.refresh) onRefresh else onOpenLibrary,
                onFocusChanged = { focused ->
                    focusedTarget = if (focused) HomeFocusTarget.Recovery else null
                },
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).focusRequester(recoveryRequester),
            )
        } else if (metrics.hasUsableHomeCard) {
            Box(
                Modifier
                    .offset(
                        x = -metrics.focusLiftReservation,
                        y = metrics.homeCardAllocatedBounds.top - metrics.contentBounds.top,
                    )
                    .wrapContentSize(Alignment.TopStart, unbounded = true),
            ) {
                LazyRow(
                    modifier = Modifier
                        .width(metrics.windowWidth - metrics.gutter + metrics.focusLiftReservation)
                        .height(metrics.homeCardAllocatedSize),
                    state = rowState,
                    horizontalArrangement = Arrangement.spacedBy(metrics.homeCardGap),
                ) {
                    items(state.items, key = { it.itemId.value }) { item ->
                        val activate = { onSelect(item.itemId); onActivate(item.itemId) }
                        LibraryItemCard(
                            model = item,
                            variant = LibraryItemCardVariant.Home,
                            selected = item.itemId == state.selectedItemId,
                            activationEnabled = item.canOpen && state.pendingLaunchItemId == null,
                            modifier = Modifier
                                .size(metrics.homeCardAllocatedSize)
                                .focusRequester(requesters.getValue(item.itemId)),
                            focusFrameWidth = metrics.focusFrameReservation,
                            focusLift = metrics.focusLiftReservation,
                            iconLoader = iconLoader,
                            onActivate = activate,
                            onFocusChanged = { focused ->
                                if (focused) {
                                    onSelect(item.itemId)
                                    focusedTarget = HomeFocusTarget.Item(item.itemId)
                                } else if (focusedTarget == HomeFocusTarget.Item(item.itemId)) {
                                    focusedTarget = null
                                }
                            },
                        )
                    }
                    item(key = LIBRARY_ACTION_KEY) {
                        val action = {
                            onOpenLibrary()
                        }
                        AppIconTile(
                            title = "Library",
                            activationEnabled = true,
                            onActivate = action,
                            onFocusChanged = { focused ->
                                if (focused) {
                                    focusedTarget = HomeFocusTarget.Library
                                } else if (focusedTarget == HomeFocusTarget.Library) {
                                    focusedTarget = null
                                }
                            },
                            modifier = Modifier
                                .size(metrics.homeCardAllocatedSize)
                                .focusRequester(libraryRequester),
                            icon = {
                                LauncherGlyphIcon(
                                    glyph = LauncherGlyph.Library,
                                    modifier = Modifier.size(32.dp * metrics.referenceScale),
                                    contentDescription = null,
                                    tint = LauncherTheme.colors.focus,
                                )
                            },
                            focusFrameWidth = metrics.focusFrameReservation,
                            focusLift = metrics.focusLiftReservation,
                        )
                    }
                }
            }
        } else {
            HomeRecoveryState(
                title = selected?.title ?: "Home",
                message = "This window is too small to show the Home row.",
                actionLabel = "Open Library",
                onAction = onOpenLibrary,
                onFocusChanged = { focused ->
                    focusedTarget = if (focused) HomeFocusTarget.Library else null
                },
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).focusRequester(recoveryRequester),
            )
        }
    }

    LaunchedEffect(focusedTarget, state.items, state.pendingLaunchItemId) {
        val action = when (val target = focusedTarget) {
                is HomeFocusTarget.Item -> state.items
                    .firstOrNull { it.itemId == target.itemId }
                    ?.let { item ->
                        HomeFocusedAction(
                            descriptor = LauncherActionDescriptor(
                                input = SemanticInputAction.CONFIRM,
                                meaning = LauncherActionMeaning.ACTIVATE,
                                label = item.primaryActionLabel,
                                enabled = item.canOpen && state.pendingLaunchItemId == null,
                            ),
                            itemId = item.itemId,
                            onActivate = { onActivate(item.itemId) },
                            onOpenDetails = { onOpenDetails(item.itemId) },
                        )
                    }
                HomeFocusTarget.Library -> HomeFocusedAction(
                    descriptor = LauncherActionDescriptor(
                        input = SemanticInputAction.CONFIRM,
                        meaning = LauncherActionMeaning.CHANGE_DESTINATION,
                        label = "Open Library",
                    ),
                    itemId = null,
                    onActivate = onOpenLibrary,
                )
                HomeFocusTarget.Recovery -> state.emptyPresentation().let { empty ->
                    HomeFocusedAction(
                        descriptor = LauncherActionDescriptor(
                            input = SemanticInputAction.CONFIRM,
                            meaning = LauncherActionMeaning.ACTIVATE,
                            label = empty.actionLabel,
                        ),
                        itemId = null,
                        onActivate = if (empty.refresh) onRefresh else onOpenLibrary,
                    )
                }
                null -> null
            }
        // Another shell control publishes its own focus action. Avoid a late null from a card
        // focus-loss callback overwriting that newer owner.
        if (action != null) onFocusedActionChanged(action)
    }

    LaunchedEffect(itemIds, state.loading, state.focusRequestSequence, pageActivationRequest, metrics.hasUsableHomeCard, allowFocusRequest) {
        if (!allowFocusRequest) return@LaunchedEffect
        if (itemIds.isEmpty() || !metrics.hasUsableHomeCard) {
            if (!state.loading && pageActivationRequest > 0 && pageActivationRequest != handledPageActivation) {
                inputMode.requestInputMode(InputMode.Keyboard)
                withFrameNanos { }
                if (runCatching { recoveryRequester.requestFocus() }.isSuccess) handledPageActivation = pageActivationRequest
            }
            return@LaunchedEffect
        }
        val selectedId = state.selectedItemId ?: return@LaunchedEffect
        val selectedIndex = itemIds.indexOf(selectedId)
        if (selectedIndex < 0) return@LaunchedEffect
        val explicitReturn = state.focusRequestSequence != handledFocusRequestSequence
        val pageActivated = pageActivationRequest > 0 && pageActivationRequest != handledPageActivation
        val initial = !initialRestorationComplete

        if (initial) {
            val anchorIndex = itemIds.indexOf(state.firstVisibleItemId)
            if (anchorIndex >= 0) {
                rowState.scrollToItem(anchorIndex, state.firstVisibleOffsetPx)
            }
            initialRestorationComplete = true
        }

        withFrameNanos { }
        val selectedVisible = rowState.layoutInfo.visibleItemsInfo.any { it.index == selectedIndex }
        val ownsContentFocus = focusedTarget is HomeFocusTarget.Item
        val shouldRequestFocus = explicitReturn || pageActivated || initial || ownsContentFocus
        if (!shouldRequestFocus) return@LaunchedEffect

        if (!selectedVisible) {
            rowState.animateScrollToItem(selectedIndex)
        }
        inputMode.requestInputMode(InputMode.Keyboard)
        withFrameNanos { }
        if (rowState.layoutInfo.visibleItemsInfo.none { it.index == selectedIndex }) return@LaunchedEffect
        val requester = requesters[selectedId] ?: return@LaunchedEffect
        if (runCatching { requester.requestFocus() }.isSuccess) {
            handledPageActivation = pageActivationRequest
            handledFocusRequestSequence = state.focusRequestSequence
        }
    }
    LaunchedEffect(rowState, itemIds, initialRestorationComplete) {
        if (itemIds.isEmpty() || !initialRestorationComplete) return@LaunchedEffect
        snapshotFlow { rowState.firstVisibleItemIndex to rowState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                onViewportChanged(itemIds.getOrNull(index), offset)
            }
    }
}

@Composable
private fun HomeRecoveryState(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(LauncherTheme.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.md),
    ) {
        LauncherText(
            text = title,
            style = LauncherTheme.typography.pageTitle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        LauncherText(
            text = message,
            color = LauncherTheme.colors.textSecondary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        LauncherButton(
            label = actionLabel,
            onActivate = onAction,
            onFocusChanged = onFocusChanged,
        )
    }
}

@Composable
private fun HomeRefreshLabel(state: HomeUiState, modifier: Modifier = Modifier) {
    val text = when (state.notice) {
        HomeNotice.CachedCatalogRefreshing -> "Refreshing apps…"
        HomeNotice.InventoryRefreshFailed -> "Could not refresh apps"
        HomeNotice.SelectedItemRemoved -> "The previous item is no longer available"
        HomeNotice.PositionSaveFailed -> "Could not save Home position"
        is HomeNotice.LaunchFailed -> "Could not open item"
        null -> if (state.refreshState is AndroidCatalogRefreshState.Refreshing) "Refreshing apps…" else null
    } ?: return
    LauncherText(
        text = text,
        modifier = modifier,
        style = LauncherTheme.typography.badgeLabel,
        color = LauncherTheme.colors.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private data class HomeEmptyPresentation(
    val title: String,
    val message: String,
    val actionLabel: String,
    val refresh: Boolean,
)

private sealed interface HomeFocusTarget {
    data class Item(val itemId: ItemId) : HomeFocusTarget
    data object Library : HomeFocusTarget
    data object Recovery : HomeFocusTarget
}

private fun HomeUiState.emptyPresentation(): HomeEmptyPresentation = when {
    loading -> HomeEmptyPresentation(
        title = "Loading your library",
        message = "Your saved apps will appear here.",
        actionLabel = "Open Library",
        refresh = false,
    )
    refreshState is AndroidCatalogRefreshState.Error || inventoryIncomplete -> HomeEmptyPresentation(
        title = "Apps unavailable",
        message = "The saved library is still safe. Try scanning installed apps again.",
        actionLabel = "Try again",
        refresh = true,
    )
    refreshState is AndroidCatalogRefreshState.Refreshing -> HomeEmptyPresentation(
        title = "Finding installed apps",
        message = "Home will update when the scan finishes.",
        actionLabel = "Open Library",
        refresh = false,
    )
    else -> HomeEmptyPresentation(
        title = "No apps yet",
        message = "Refresh installed apps or browse the complete library.",
        actionLabel = "Refresh",
        refresh = true,
    )
}

private const val LIBRARY_ACTION_KEY = "home-library-action"
