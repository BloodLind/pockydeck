package dev.handheld.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.handheld.launcher.contract.*
import dev.handheld.launcher.audio.ControllerSelectionFeedback
import dev.handheld.launcher.core.data.android.status.DeviceStatusSnapshot
import dev.handheld.launcher.core.data.discovery.AndroidCatalogRefreshState
import dev.handheld.launcher.core.designsystem.contract.ModalFocusLifecycle
import dev.handheld.launcher.core.designsystem.contract.LocalControlFocusRestoration
import dev.handheld.launcher.core.designsystem.contract.LocalControllerInput
import dev.handheld.launcher.input.LocalPageNavigation
import dev.handheld.launcher.input.PageNavigation
import dev.handheld.launcher.core.designsystem.glyphs.LauncherStatusGlyph
import dev.handheld.launcher.core.designsystem.controls.LauncherButton
import dev.handheld.launcher.core.designsystem.controls.FilterChip
import dev.handheld.launcher.core.designsystem.foundation.*
import dev.handheld.launcher.core.designsystem.layout.EmptyState
import dev.handheld.launcher.core.designsystem.modal.LauncherDialog
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import dev.handheld.launcher.core.domain.model.*
import dev.handheld.launcher.di.AppContainer
import dev.handheld.launcher.di.LauncherAppViewModel
import dev.handheld.launcher.feature.apps.AppsScreen
import dev.handheld.launcher.feature.collection.*
import dev.handheld.launcher.feature.details.*
import dev.handheld.launcher.feature.favorites.FavoritesScreen
import dev.handheld.launcher.feature.home.*
import dev.handheld.launcher.feature.library.LibraryScreen
import dev.handheld.launcher.feature.search.SearchScreen
import dev.handheld.launcher.feature.search.SearchEditorActions
import dev.handheld.launcher.feature.search.SearchPageActions
import dev.handheld.launcher.feature.settings.*
import dev.handheld.launcher.feature.settings.sources.RomSourcesCallbacks
import dev.handheld.launcher.feature.settings.emulators.EmulatorSettingsCallbacks
import dev.handheld.launcher.feature.settings.emulators.RomChoiceDialog
import dev.handheld.launcher.feature.settings.emulators.RomChoiceDialogCallbacks
import dev.handheld.launcher.launch.*
import dev.handheld.launcher.navigation.*
import dev.handheld.launcher.platform.home.HomeRoleRequestState
import dev.handheld.launcher.platform.system.SupportedSystemAction
import dev.handheld.launcher.shell.*
import kotlinx.coroutines.launch
import java.util.Locale
import dev.handheld.launcher.ui.artwork.enriched.LocalEnrichedArtworkLoader
import dev.handheld.launcher.feature.settings.metadata.ArtworkSettingsCallbacks
import dev.handheld.launcher.core.data.metadata.ArtworkSummary

@OptIn(ExperimentalComposeUiApi::class, ExperimentalLayoutApi::class)
@Composable
fun LauncherApp(
    container: AppContainer,
    app: LauncherAppViewModel,
    home: HomeViewModel,
    reducedMotion: Boolean,
    homeRoleHeld: Boolean,
    bindInput: ((SemanticInputAction) -> Boolean) -> Unit,
    nativeConfirm: () -> Boolean,
    onImeVisibilityChanged: (Boolean) -> Unit = {},
    onPickRomFolder: () -> Unit = {},
    onSetupStorageAccess: () -> Unit = {},
    onSearchEditorActiveChanged: (Boolean) -> Unit = {},
    bindTouchInput: (() -> Unit) -> Unit = {},
    onSearchClearEnabledChanged: (Boolean) -> Unit = {},
    notificationAccessGranted: Boolean = false,
    onSetupNotificationAccess: () -> Unit = {},
    controllerSoundsEnabled: Boolean = true,
    onSetControllerSoundsEnabled: (Boolean) -> Unit = {},
    onExpectItemSelection: () -> Unit = {},
    onItemSelected: () -> Unit = {},
) {
    val location by app.navigation.location.collectAsStateWithLifecycle()
    val mapping by app.mapping.collectAsStateWithLifecycle()
    val display by app.display.collectAsStateWithLifecycle()
    val appError by app.error.collectAsStateWithLifecycle()
    val homeState by home.state.collectAsStateWithLifecycle()
    val launchState by container.launchCoordinator.state.collectAsStateWithLifecycle()
    val roleState by container.homeRoleRequests.state.collectAsStateWithLifecycle()
    val romSources by container.romController.sourcesState.collectAsStateWithLifecycle()
    val romEmulators by container.romController.emulatorsState.collectAsStateWithLifecycle()
    val romChoice by container.romController.choice.collectAsStateWithLifecycle()
    val romProgress by container.romController.progress.collectAsStateWithLifecycle()
    val romMessage by container.romController.message.collectAsStateWithLifecycle()
    val artworkSummary by container.artworkRepository.summary.collectAsStateWithLifecycle(ArtworkSummary())
    val status by container.deviceStatus.status.collectAsStateWithLifecycle(DeviceStatusSnapshot())
    val runningApps by container.runningApps.state.collectAsStateWithLifecycle(dev.handheld.launcher.runtime.RunningAppState())
    val pageModels = listOf(LauncherDestination.LIBRARY, LauncherDestination.APPS,
        LauncherDestination.FAVORITES, LauncherDestination.SEARCH).associateWith { destination ->
        viewModel<CollectionViewModel>(key = "collection.${destination.persistedKey}",
            factory = remember(destination) { container.collectionViewModelFactory(destination) })
    }
    val pageStates = pageModels.mapValues { (_, vm) -> vm.state.collectAsStateWithLifecycle().value }
    val destination = location.selectedDockDestination()
    val library = pageStates.getValue(LauncherDestination.LIBRARY)
    val dataActions = pageModels.getValue(LauncherDestination.LIBRARY)
    val focusManager = LocalFocusManager.current
    val inputMode = LocalInputModeManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val expectSelection by rememberUpdatedState(onExpectItemSelection)
    val playSelection by rememberUpdatedState(onItemSelected)
    val selectionFeedback = remember {
        ControllerSelectionFeedback({ expectSelection() }, { playSelection() }, android.os.SystemClock::uptimeMillis)
    }
    LaunchedEffect(container) { container.startArtwork() }
    val pageFocus = remember { FocusRequester() }
    val shellFocus = remember { FocusRequester() }
    val saveablePages = rememberSaveableStateHolder()
    var focused by remember(location) { mutableStateOf<FocusedControlAction?>(null) }
    var focusedDock by remember(location) { mutableStateOf<LauncherDestination?>(null) }
    var searchEditorActions by remember(location) { mutableStateOf<SearchEditorActions?>(null) }
    var searchPageActions by remember(location) { mutableStateOf<SearchPageActions?>(null) }
    var menuVisible by remember { mutableStateOf(false) }
    var filterMenuDestination by remember { mutableStateOf<LauncherDestination?>(null) }
    var sortMenuDestination by remember { mutableStateOf<LauncherDestination?>(null) }
    var controllerInput by remember { mutableStateOf(false) }
    var dockFocusAllowed by remember { mutableStateOf(false) }
    var pageNavigation by remember { mutableStateOf<PageNavigation?>(null) }
    val publishPageNavigation = remember { { value: PageNavigation? -> pageNavigation = value } }
    var menuItemId by remember { mutableStateOf<ItemId?>(null) }
    var modalOrigin by remember { mutableStateOf<LauncherLocation?>(null) }
    var modalOriginWasDock by remember { mutableStateOf(false) }
    var controlRestoreFocus by remember(location) { mutableStateOf<(() -> Unit)?>(null) }
    var modalRestoreFocus by remember { mutableStateOf<(() -> Unit)?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var queryFocusRequest by rememberSaveable { mutableIntStateOf(0) }
    var pageActivationRequest by remember { mutableIntStateOf(1) }
    var settingsSection by rememberSaveable { mutableStateOf("Launcher") }
    LaunchedEffect(location) {
        if (location == LauncherLocation.Destination(LauncherDestination.HOME)) home.returnToStart()
        // Details reached from Search belong to the same session. Actual page closure
        // clears both persisted query/filter state and the editor's saveable local state.
        if (!location.hasSearchSession()) {
            pageModels.getValue(LauncherDestination.SEARCH).clearSearch()
            saveablePages.removeState(LauncherDestination.SEARCH.persistedKey)
            queryFocusRequest = 0
        }
    }
    val searchableActions = remember(container) {
        container.systemActions.actions + listOf(
            SupportedSystemAction("launcher-controls", "Confirm and Back buttons", "Choose the launcher's A/B controller mapping", "launcher:controls"),
            SupportedSystemAction("launcher-display", "UI scale and motion", "Adjust launcher controls, text size and animation", "launcher:display"),
            SupportedSystemAction("launcher-home", "Default Home launcher", "Choose which launcher opens with the Home button", "launcher:home"),
            SupportedSystemAction("launcher-rom-folders", "ROM folders", "Add, scan or restore your game folders and manage the extraction cache", "launcher:rom-folders"),
            SupportedSystemAction("launcher-emulators", "Emulators", "Choose a preferred emulator app and RetroArch core for each console", "launcher:emulators"),
            SupportedSystemAction("launcher-artwork", "Artwork", "Manage automatic missing-artwork downloads and retry matches", "launcher:artwork"),
        )
    }
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) { onImeVisibilityChanged(imeVisible) }
    val modalVisible = menuVisible || filterMenuDestination != null || sortMenuDestination != null || errorMessage != null || romChoice != null || romProgress != null

    fun publishFocus(value: FocusedControlAction?) {
        focused = value
        if (value != null) {
            if (controllerInput && !modalVisible && location !is LauncherLocation.ItemDetails)
                selectionFeedback.focus(value.itemId?.value)
            else selectionFeedback.reset()
        }
    }

    fun snapshot(): DestinationSnapshot = if (destination == LauncherDestination.HOME) home.state.value.snapshot()
        else pageModels[destination]?.state?.value?.snapshot() ?: DestinationSnapshot(destination)
    fun selectDestination(value: LauncherDestination) {
        selectionFeedback.reset()
        keyboard?.hide()
        dockFocusAllowed = false
        focusManager.clearFocus(force = true)
        focused = null
        focusedDock = null
        pageActivationRequest++
        if (value == LauncherDestination.HOME) home.returnToStart()
        app.navigation.selectDestination(value)
    }
    fun openDetails(id: ItemId) {
        val origin = when (val current = location) {
            is LauncherLocation.ShortcutSearch -> NavigationOrigin.ShortcutSearch(current.returnDestination)
            is LauncherLocation.ItemDetails -> current.origin
            is LauncherLocation.Destination -> NavigationOrigin.Destination(current.destination)
        }
        pageFocus.saveFocusedChild()
        focused = null
        app.navigation.openItemDetails(id, origin)
    }
    fun showError(message: String) {
        if (!modalVisible) {
            pageFocus.saveFocusedChild()
            shellFocus.saveFocusedChild()
            modalRestoreFocus = controlRestoreFocus
            modalOriginWasDock = focusedDock != null
            modalOrigin = location
        }
        errorMessage = message
    }
    fun rememberModalOrigin() {
        if (modalOrigin == null) {
            // The shell and page are separate focus groups. Save the page's child as well,
            // otherwise restoring the shell re-enters the page at its first control.
            pageFocus.saveFocusedChild()
            shellFocus.saveFocusedChild()
            modalRestoreFocus = controlRestoreFocus
            modalOriginWasDock = focusedDock != null
            modalOrigin = location
        }
        keyboard?.hide()
    }
    fun clearError() {
        errorMessage = null
        app.error.value = null
        dataActions.clearError()
        container.romController.clearMessage()
        container.launchCoordinator.clearResult()
    }
    fun artworkAction(action: suspend () -> Unit) {
        scope.launch {
            try { action() }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { showError("Could not update artwork settings. Try again.") }
        }
    }
    fun openSystem(key: String) {
        when (key) {
            "launcher-controls", "launcher-display", "launcher-home", "launcher-rom-folders", "launcher-emulators", "launcher-artwork" -> {
                settingsSection = when (key) {
                    "launcher-controls" -> "Controls"
                    "launcher-display" -> "Display"
                    "launcher-rom-folders" -> "ROM folders"
                    "launcher-emulators" -> "Emulators"
                    "launcher-artwork" -> "Artwork"
                    else -> "Launcher"
                }
                selectDestination(LauncherDestination.SETTINGS)
            }
            else -> if (!container.systemActions.open(key)) showError("This Android setting is unavailable.")
        }
    }
    fun goBack() {
        when {
            !modalVisible && searchEditorActions != null -> searchEditorActions?.cancel?.invoke()
            imeVisible -> keyboard?.hide()
            romChoice != null -> container.romController.dismissChoice()
            romProgress != null -> container.romController.cancelPreparation()
            errorMessage != null -> clearError()
            filterMenuDestination != null -> filterMenuDestination = null
            sortMenuDestination != null -> sortMenuDestination = null
            menuVisible -> menuVisible = false
            else -> {
                val previousLocation = app.navigation.location.value
                app.navigation.back()
                if (app.navigation.location.value != previousLocation) focused = null
            }
        }
    }
    fun openSearch() {
        if (location is LauncherLocation.ItemDetails && location.hasSearchSession()) {
            focused = null
            app.navigation.back()
        } else if (destination != LauncherDestination.SEARCH) {
            pageFocus.saveFocusedChild()
            focused = null
            app.navigation.openShortcutSearch(destination)
        }
        queryFocusRequest++
    }
    fun showMenu() {
        pageFocus.saveFocusedChild()
        shellFocus.saveFocusedChild()
        modalRestoreFocus = controlRestoreFocus
        modalOriginWasDock = focusedDock != null
        menuItemId = focused?.itemId ?: (location as? LauncherLocation.ItemDetails)?.itemId
        modalOrigin = location
        menuVisible = true
    }
    val selectedItemId = focused?.itemId ?: (location as? LauncherLocation.ItemDetails)?.itemId
    val selectedItem = library.allItems.find { it.id == selectedItemId }
    val collectionUpdating = pageStates[destination]?.searching == true
    val searchCanClear = destination == LauncherDestination.SEARCH && searchPageActions?.hasQuery == true
    val primary = if (modalVisible) LauncherActionDescriptor(SemanticInputAction.CONFIRM, LauncherActionMeaning.ACTIVATE, "Select")
        else if (searchEditorActions != null) LauncherActionDescriptor(SemanticInputAction.CONFIRM, LauncherActionMeaning.ACTIVATE, "Apply")
        else focused?.descriptor?.let { it.copy(input = SemanticInputAction.CONFIRM, enabled = it.enabled && !collectionUpdating) }
            ?: LauncherActionDescriptor(SemanticInputAction.CONFIRM, LauncherActionMeaning.ACTIVATE, "Select", false)
    val footer = ControllerActionFooter(buildList {
        add(primary)
        if (modalVisible || searchEditorActions != null || imeVisible || location !is LauncherLocation.Destination ||
            destination == LauncherDestination.SETTINGS || destination == LauncherDestination.SEARCH)
            add(LauncherActionDescriptor(SemanticInputAction.BACK, LauncherActionMeaning.GO_BACK,
                if (!modalVisible && searchEditorActions != null) "Cancel" else "Back"))
        if (!modalVisible && searchEditorActions == null) {
            add(LauncherActionDescriptor(SemanticInputAction.SECONDARY, LauncherActionMeaning.OPEN_SEARCH, "Search"))
            if (!searchCanClear && selectedItem != null && location !is LauncherLocation.ItemDetails)
                add(LauncherActionDescriptor(SemanticInputAction.TERTIARY, LauncherActionMeaning.OPEN_DETAILS, "Details"))
            add(LauncherActionDescriptor(SemanticInputAction.MENU, LauncherActionMeaning.OPEN_MENU, "Menu"))
        }
        if (!modalVisible && searchCanClear)
            add(LauncherActionDescriptor(SemanticInputAction.TERTIARY, LauncherActionMeaning.CLEAR_SEARCH, "Clear"))
    })
    val actionPort = SemanticActionPort { descriptor ->
        if (!descriptor.enabled || descriptor !in footer.actions) false else {
            when (descriptor.input) {
                SemanticInputAction.CONFIRM -> if (!modalVisible && searchEditorActions != null) {
                    searchEditorActions?.apply?.invoke(); true
                } else if (!modalVisible && focused?.onActivate != null) { focused?.onActivate?.invoke(); true }
                else nativeConfirm()
                SemanticInputAction.BACK -> { goBack(); true }
                SemanticInputAction.SECONDARY -> { openSearch(); true }
                SemanticInputAction.TERTIARY -> if (descriptor.meaning == LauncherActionMeaning.CLEAR_SEARCH) {
                    searchPageActions?.clear?.invoke(); true
                } else { selectedItemId?.let(::openDetails); selectedItemId != null }
                SemanticInputAction.MENU -> { showMenu(); true }
                else -> false
            }
        }
    }
    SideEffect {
        bindTouchInput {
            selectionFeedback.reset()
            if (controllerInput) {
                controllerInput = false
                dockFocusAllowed = false
                inputMode.requestInputMode(InputMode.Touch)
                if (searchEditorActions == null) {
                    focusManager.clearFocus(force = true)
                    focused = null
                    focusedDock = null
                }
            }
        }
        onSearchEditorActiveChanged(searchEditorActions != null && !modalVisible)
        onSearchClearEnabledChanged(searchCanClear && !modalVisible)
        bindInput { action ->
            val enteringControllerMode = !controllerInput
            val changingDestination = action == SemanticInputAction.PREVIOUS_DESTINATION || action == SemanticInputAction.NEXT_DESTINATION
            if ((changingDestination && !modalVisible) || enteringControllerMode) dockFocusAllowed = false
            controllerInput = true
            inputMode.requestInputMode(InputMode.Keyboard)
            val direction = when (action) {
                SemanticInputAction.NAVIGATE_UP -> FocusDirection.Up
                SemanticInputAction.NAVIGATE_DOWN -> FocusDirection.Down
                SemanticInputAction.NAVIGATE_LEFT -> FocusDirection.Left
                SemanticInputAction.NAVIGATE_RIGHT -> FocusDirection.Right
                else -> null
            }
            if (direction != null && !enteringControllerMode && !modalVisible) dockFocusAllowed = true
            val homeCardStep = destination == LauncherDestination.HOME &&
                action in setOf(SemanticInputAction.PREVIOUS_FILTER, SemanticInputAction.NEXT_FILTER)
            if (!enteringControllerMode && !modalVisible && searchEditorActions == null &&
                location !is LauncherLocation.ItemDetails && (direction != null || homeCardStep))
                selectionFeedback.navigate(fromItem = selectionFeedback.hasItemFocus)
            else selectionFeedback.reset()
            when {
                enteringControllerMode && !modalVisible && (direction != null || action == SemanticInputAction.CONFIRM) && searchEditorActions == null -> {
                    pageActivationRequest++; true
                }
                direction != null -> if (!modalVisible && pageNavigation?.move(direction) == true) true else focusManager.moveFocus(direction)
                action == SemanticInputAction.BACK -> { goBack(); true }
                modalVisible -> if (action == SemanticInputAction.CONFIRM) nativeConfirm() else true
                action == SemanticInputAction.PREVIOUS_DESTINATION || action == SemanticInputAction.NEXT_DESTINATION -> {
                    val order = LauncherDestination.dockOrder
                    val delta = if (action == SemanticInputAction.NEXT_DESTINATION) 1 else -1
                    val activeDestination = app.navigation.location.value.selectedDockDestination()
                    selectDestination(order[(order.indexOf(activeDestination) + delta + order.size) % order.size]); true
                }
                action == SemanticInputAction.PREVIOUS_FILTER || action == SemanticInputAction.NEXT_FILTER -> {
                    val filters = pageModels[destination]?.state?.value?.filterKeys.orEmpty()
                    if (filters.isEmpty()) {
                        if (destination == LauncherDestination.HOME) {
                            if (enteringControllerMode) { pageActivationRequest++; true }
                            else focusManager.moveFocus(if (action == SemanticInputAction.NEXT_FILTER) FocusDirection.Right else FocusDirection.Left)
                        } else false
                    } else {
                        val vm = pageModels.getValue(destination)
                        val delta = if (action == SemanticInputAction.NEXT_FILTER) 1 else -1
                        val changed = vm.cycleFilter(delta)
                        if (changed || enteringControllerMode) pageActivationRequest++
                        true
                    }
                }
                else -> footer.actions.find { it.input == action }?.let(actionPort::dispatch) ?: false
            }
        }
    }
    BackHandler { goBack() }
    LaunchedEffect(appError) { appError?.let(::showError) }
    LaunchedEffect(homeState.refreshState) {
        if (homeState.refreshState is AndroidCatalogRefreshState.Ready) container.iconLoader.clear()
    }
    LaunchedEffect(library.error) { library.error?.let(::showError) }
    LaunchedEffect(romMessage) { romMessage?.let(::showError) }
    LaunchedEffect(launchState) {
        (launchState as? LaunchCoordinatorState.Failed)?.let {
            if (it.reason == LaunchCoordinatorFailure.CANCELLED) container.launchCoordinator.clearResult()
            else showError(container.romController.message.value ?: it.reason.message())
        }
    }
    LaunchedEffect(modalVisible) {
        if (!modalVisible && modalOrigin != null) {
            withFrameNanos { }
            if (modalOrigin == location && controllerInput) {
                inputMode.requestInputMode(InputMode.Keyboard)
                // Compose 1.7 group restoration can stop at an implicit scroll target.
                // The opener supplies its exact leaf requester; groups are only fallbacks
                // for controls that have disappeared or do not publish a restoration hook.
                val leafRestored = modalRestoreFocus?.let { runCatching(it).isSuccess } == true
                if (!leafRestored) {
                    val pageRestored = !modalOriginWasDock && runCatching { pageFocus.restoreFocusedChild() }.getOrDefault(false)
                    if (!pageRestored) runCatching { shellFocus.restoreFocusedChild() }
                }
            }
            modalOrigin = null
            modalRestoreFocus = null
        }
    }

    val systemDensity = LocalDensity.current
    val uiDensity = remember(systemDensity, display.uiScaleFactor) {
        Density(systemDensity.density * display.uiScaleFactor, systemDensity.fontScale)
    }
    CompositionLocalProvider(LocalDensity provides uiDensity) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val metrics = remember(constraints.maxWidth, constraints.maxHeight, density.density, density.fontScale, display.uiScaleFactor) {
            // Constraints are already physical pixels. A scope's cached dp bounds must not
            // be converted back using the newly selected UI density during live scaling.
            ShellMetrics.calculate(ShellMetricsInput(constraints.maxWidth, constraints.maxHeight,
                density.density, density.fontScale, display.uiScaleFactor))
        }
        val imeBottom = with(density) { WindowInsets.ime.getBottom(this).toDp() }
        LauncherTheme(reducedMotion || display.reduceMotion, metrics.referenceScale, uiScaleFactor = display.uiScaleFactor) {
            val pageMotion = pageTransition(location, reducedMotion || display.reduceMotion)
            val registry = LauncherRouteRegistry(LauncherDestination.dockOrder.map { route ->
                LauncherDestinationRoute(route) { context ->
                    val bounds = context.modifier.then(pageMotion).focusRequester(pageFocus).focusGroup()
                    saveablePages.SaveableStateProvider(route.persistedKey) {
                        val detailsLocation = location as? LauncherLocation.ItemDetails
                        if (detailsLocation != null) {
                            val item = library.allItems.find { it.id == detailsLocation.itemId }
                            if (item != null) ItemDetailsScreen(item, library.overrides[item.id], item.id in library.favorites,
                                bounds, ItemDetailsCallbacks(
                                    onOpen = { container.launchCoordinator.submit(it.id, snapshot().copy(selectedItemId = it.id)) },
                                    onFavorite = { value, favorite -> dataActions.setFavorite(value.id, favorite) },
                                    onAppInfo = { if (!container.systemActions.openAppInfo(it.componentId.packageName)) showError("App info is unavailable.") },
                                    onCategoryChange = { value, category -> dataActions.setCategory(value.id, category) },
                                    onFocusedAction = ::publishFocus,
                                    onChooseRomConsole = { rememberModalOrigin(); container.romController.chooseItemPlatform(it) },
                                    onChooseRomEmulator = { rememberModalOrigin(); container.romController.chooseItemEmulator(it) },
                                    onOpenRomFolders = { openSystem("launcher-rom-folders") },
                                ), container.iconLoader)
                            else EmptyState("Item unavailable", "This item is no longer in the catalog.", "Back", ::goBack, bounds)
                        } else when (route) {
                            LauncherDestination.HOME -> CompositionLocalProvider(
                                dev.handheld.launcher.runtime.LocalRunningLabels provides runningApps.labels,
                            ) {
                                HomeRoute(home, metrics, container.iconLoader, bounds,
                                    onOpenLibrary = { selectDestination(LauncherDestination.LIBRARY) },
                                    onOpenDetails = ::openDetails,
                                    allowFocusRequest = !modalVisible && controllerInput,
                                    pageActivationRequest = pageActivationRequest,
                                    onFocusedActionChanged = { publishFocus(it?.let { value -> FocusedControlAction(value.descriptor, value.onActivate, value.itemId) }) })
                            }
                            LauncherDestination.SETTINGS -> SettingsScreen(
                                SettingsScreenState(mapping, homeRoleSummary(homeRoleHeld, roleState),
                                    listOf(LibraryCategory.GAME, LibraryCategory.EMULATOR, LibraryCategory.OTHER).map { category ->
                                        CategorySummary(category, collectionCategoryCount(category, library.allItems, library.overrides))
                                    }, romSources, romEmulators, artworkSummary,
                                    uiScalePercent = display.uiScalePercent, reduceMotion = display.reduceMotion,
                                    gridSizePercent = display.gridSizePercent,
                                    notificationAccessGranted = notificationAccessGranted,
                                    controllerSoundsEnabled = controllerSoundsEnabled,
                                    runningIndicatorsEnabled = runningApps.enabled,
                                    runningStatusSummary = runningApps.summary), bounds, SettingsCallbacks(
                                    onSetControllerSoundsEnabled = onSetControllerSoundsEnabled,
                                    onSetRunningIndicators = container.runningApps::setEnabled,
                                    onSetupRunningStatus = { if (!container.runningApps.requestSetup()) showError("Shizuku setup is unavailable.") },
                                    onSetupNotificationAccess = onSetupNotificationAccess,
                                    onSetUiScalePercent = app::setUiScalePercent,
                                    onSetGridSizePercent = app::setGridSizePercent,
                                    onSetReduceMotion = app::setReduceMotion,
                                    onSetConfirmBackMapping = app::setMapping,
                                    artwork = ArtworkSettingsCallbacks(
                                        onSetPaused = { artworkAction { container.artworkRepository.setPaused(it) } },
                                        onRetry = { artworkAction { container.artworkRepository.retryMissing() } },
                                    ),
                                    onRequestDefaultHome = { container.homeRoleRequests.requestSelection() },
                                    onOpenSystemAction = ::openSystem,
                                    onOpenCategory = { category ->
                                        val categoryDestination = collectionCategoryDestination(category)
                                        pageModels.getValue(categoryDestination).filter(when (category) {
                                            LibraryCategory.GAME -> "all"; LibraryCategory.EMULATOR -> "emulators"; else -> "other"
                                        }); selectDestination(categoryDestination)
                                    }, onFocusedAction = ::publishFocus,
                                    romSources = RomSourcesCallbacks(
                                        onAddSource = { keyboard?.hide(); onPickRomFolder() },
                                        onRescanSource = { container.romController.rescan() },
                                        onRemoveSource = { rememberModalOrigin(); container.romController.removeSource(it) },
                                        onRegrantSource = { id ->
                                            keyboard?.hide()
                                            val source = romSources.sources.firstOrNull { it.id == id }
                                            if (source?.accessKind == dev.handheld.launcher.core.domain.rom.RomSourceAccessKind.SHARED_STORAGE) {
                                                container.romController.restoreSource(id)
                                                if (!romSources.storageAccessGranted) onSetupStorageAccess()
                                            } else onPickRomFolder()
                                        },
                                        onChooseSourcePlatform = { rememberModalOrigin(); container.romController.chooseSourcePlatform(it) },
                                        onIdentifySourceItems = { rememberModalOrigin(); container.romController.identifySourceItems(it) },
                                        onChooseCacheLimit = { rememberModalOrigin(); container.romController.chooseCacheLimit() },
                                        onClearCache = { rememberModalOrigin(); container.romController.clearCache() },
                                        onSetupStorageAccess = { keyboard?.hide(); onSetupStorageAccess() },
                                        onSetAutomaticDiscovery = container.romController::setAutomaticDiscovery,
                                        onDiscoverFolders = { container.romController.rescan() },
                                    ),
                                    emulators = EmulatorSettingsCallbacks(
                                        onChooseEmulator = { rememberModalOrigin(); container.romController.chooseConsoleEmulator(it) },
                                        onChooseCore = { rememberModalOrigin(); container.romController.chooseCore(it) },
                                        onAddSource = { keyboard?.hide(); onPickRomFolder() },
                                    ),
                                ), container.systemActions.actions, initialSection = settingsSection,
                                    restoreFocusRequest = pageActivationRequest)
                            else -> {
                                val vm = pageModels.getValue(route)
                                val current = pageStates.getValue(route)
                                val callbacks = CollectionScreenCallbacks(vm::select,
                                    onOpen = { if (vm.canOpenItem(it))
                                        container.launchCoordinator.submit(it, vm.state.value.snapshot().copy(selectedItemId = it)) },
                                    onOpenDetails = ::openDetails, onFavorite = vm::setFavorite,
                                    onFilter = { vm.filter(it); pageActivationRequest++ }, onToggleSort = vm::toggleSort,
                                    onRememberAnchor = vm::rememberAnchor,
                                    onRetry = { vm.clearError(); container.androidCatalog.refresh(); container.romController.rescan() },
                                    onOpenSystemAction = ::openSystem,
                                    onOpenLibrary = { selectDestination(LauncherDestination.LIBRARY) },
                                    onFocusedAction = ::publishFocus,
                                    onOpenFilters = { rememberModalOrigin(); filterMenuDestination = route },
                                    onOpenSort = { rememberModalOrigin(); sortMenuDestination = route })
                                when (route) {
                                    LauncherDestination.LIBRARY -> LibraryScreen(current, bounds, callbacks, searchableActions, container.iconLoader, pageActivationRequest, !modalVisible && controllerInput,
                                        isList = route in display.listDestinations, onLayoutChange = { app.setCollectionListMode(route, it) }, gridSizePercent = display.gridSizePercent)
                                    LauncherDestination.APPS -> AppsScreen(current, bounds, callbacks, container.iconLoader, pageActivationRequest, !modalVisible && controllerInput,
                                        isList = route in display.listDestinations, onLayoutChange = { app.setCollectionListMode(route, it) }, gridSizePercent = display.gridSizePercent)
                                    LauncherDestination.FAVORITES -> FavoritesScreen(current, bounds, callbacks, container.iconLoader, pageActivationRequest, !modalVisible && controllerInput,
                                        isList = route in display.listDestinations, onLayoutChange = { app.setCollectionListMode(route, it) }, gridSizePercent = display.gridSizePercent)
                                    LauncherDestination.SEARCH -> SearchScreen(current, bounds, callbacks, vm::query,
                                        searchableActions, container.iconLoader, queryFocusRequest,
                                        restoreFocusRequest = pageActivationRequest, allowFocusRequest = !modalVisible,
                                        onEditorActionsChanged = { searchEditorActions = it },
                                        onPageActionsChanged = { searchPageActions = it }, onClearSearch = vm::clearSearch)
                                    else -> Unit
                                }
                            }
                        }
                    }
                }
            })
            CompositionLocalProvider(LocalControlFocusRestoration provides { controlRestoreFocus = it },
                LocalControllerInput provides controllerInput,
                LocalPageNavigation provides publishPageNavigation,
                LocalEnrichedArtworkLoader provides container.enrichedArtworkLoader) {
            LauncherShell(metrics, LauncherShellState(destination, focusedDock, status.toShellStatus(), footer,
                dockFocusEnabled = controllerInput && dockFocusAllowed && !modalVisible),
                actionPort, mapping, LauncherShellInsets(imeBottom),
                modifier = Modifier.focusRequester(shellFocus).focusGroup(),
                onDestinationSelected = ::selectDestination,
                onDestinationFocused = { value ->
                    focusedDock = value.takeIf { dockFocusAllowed }
                    if (value != null && dockFocusAllowed) publishFocus(FocusedControlAction(
                        LauncherActionDescriptor(SemanticInputAction.CONFIRM, LauncherActionMeaning.CHANGE_DESTINATION,
                            value.persistedKey.replaceFirstChar { it.uppercase() }), { selectDestination(value) }))
                }, content = { registry.Render(location, it) }, overlay = {
                    val romModalLifecycle = ModalFocusLifecycle(
                        onModalShown = ::rememberModalOrigin,
                        onModalDismissed = {},
                    )
                    when {
                    romChoice != null -> romChoice?.let { choice ->
                        RomChoiceDialog(
                            choice,
                            RomChoiceDialogCallbacks(
                                onSelect = container.romController::selectChoice,
                                onDismiss = container.romController::dismissChoice,
                                onRememberSelection = container.romController::setChoiceRemember,
                            ),
                            lifecycle = romModalLifecycle,
                        )
                    }
                    romProgress != null -> LauncherDialog(
                        "Preparing game",
                        container.romController::cancelPreparation,
                        lifecycle = romModalLifecycle,
                    ) {
                        LauncherText(romProgress.orEmpty())
                        LauncherButton("Cancel preparation", container.romController::cancelPreparation, Modifier.fillMaxWidth())
                    }
                    errorMessage != null -> errorMessage?.let { message ->
                        LauncherDialog("Unable to complete action", ::clearError) {
                            LauncherText(message)
                            if (romMessage != null) LauncherButton("ROM folders", {
                                clearError()
                                openSystem("launcher-rom-folders")
                            }, Modifier.fillMaxWidth())
                        }
                    }
                    filterMenuDestination != null -> filterMenuDestination?.let { filterDestination ->
                        val filterState = pageStates.getValue(filterDestination)
                        val filterOptions = remember(filterState.allItems, filterState.overrides, filterState.favorites, filterDestination) {
                            collectionFilterOptions(filterState)
                        }
                        val filterRequesters = remember(filterOptions.map { it.key }) { filterOptions.map { FocusRequester() } }
                        LauncherDialog("All filters", { filterMenuDestination = null }) {
                            filterOptions.chunked(3).forEachIndexed { row, rowOptions ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LauncherTheme.spacing.sm)) {
                                    rowOptions.forEachIndexed { column, option ->
                                        val index = row * 3 + column
                                        FilterChip(option.label, option.key == filterState.filter, {
                                            pageModels.getValue(filterDestination).filter(option.key)
                                            modalOrigin = null
                                            modalRestoreFocus = null
                                            pageActivationRequest++
                                            filterMenuDestination = null
                                        }, Modifier.weight(1f).height(64.dp).focusRequester(filterRequesters[index]).focusProperties {
                                            if (index > 0) left = filterRequesters[index - 1]
                                            if (index < filterRequesters.lastIndex) right = filterRequesters[index + 1]
                                        }, compact = false)
                                    }
                                    repeat(3 - rowOptions.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                        LaunchedEffect(filterDestination, controllerInput) {
                            if (controllerInput && filterRequesters.isNotEmpty()) {
                                inputMode.requestInputMode(InputMode.Keyboard)
                                withFrameNanos { }; withFrameNanos { }
                                filterRequesters[filterOptions.indexOfFirst { it.key == filterState.filter }.coerceAtLeast(0)].requestFocus()
                            }
                        }
                    }
                    sortMenuDestination != null -> sortMenuDestination?.let { sortDestination ->
                        val sortState = pageStates.getValue(sortDestination)
                        LauncherDialog("Sort by", { sortMenuDestination = null }, compactDismiss = true, compactDismissScale = 1.1f) {
                            listOf("recent" to "Recent", "title" to "Title").forEach { (key, label) ->
                                FilterChip(label, sortState.sort == key, {
                                    pageModels.getValue(sortDestination).sort(key)
                                    modalOrigin = null
                                    modalRestoreFocus = null
                                    pageActivationRequest++
                                    sortMenuDestination = null
                                }, Modifier.fillMaxWidth().height(48.dp), compact = false, visualScale = .9f)
                            }
                        }
                    }
                    menuVisible -> LauncherDialog("Menu", { menuVisible = false }) {
                        library.allItems.find { it.id == menuItemId }?.let { item ->
                            LauncherButton(if (item.id in library.favorites) "Remove favorite" else "Add favorite", {
                                dataActions.setFavorite(item.id, item.id !in library.favorites); menuVisible = false
                            }, Modifier.fillMaxWidth())
                            LauncherButton("Item details", { menuVisible = false; openDetails(item.id) }, Modifier.fillMaxWidth())
                            if (item is LibraryItem.RomGame) {
                                LauncherButton("Choose console", {
                                    menuVisible = false
                                    container.romController.chooseItemPlatform(item.id)
                                }, Modifier.fillMaxWidth())
                                LauncherButton("Choose emulator", {
                                    menuVisible = false
                                    container.romController.chooseItemEmulator(item.id)
                                }, Modifier.fillMaxWidth())
                            }
                        }
                        LauncherButton("Refresh library", {
                            container.androidCatalog.refresh()
                            container.romController.rescan()
                            menuVisible = false
                        }, Modifier.fillMaxWidth())
                        LauncherButton("ROM folders", { menuVisible = false; openSystem("launcher-rom-folders") }, Modifier.fillMaxWidth())
                        LauncherButton("Emulators", { menuVisible = false; openSystem("launcher-emulators") }, Modifier.fillMaxWidth())
                        LauncherButton("Settings", { menuVisible = false; selectDestination(LauncherDestination.SETTINGS) }, Modifier.fillMaxWidth())
                    }
                    }
                })
            }
        }
    }
}
}

private fun homeRoleSummary(held: Boolean, state: HomeRoleRequestState): String = when {
    held -> "Handheld Launcher is your default Home app"
    state is HomeRoleRequestState.Requested -> "Waiting for Android selection"
    state is HomeRoleRequestState.Declined -> "Not selected. You can keep using the launcher normally."
    state is HomeRoleRequestState.Unsupported -> "Home selection is unavailable on this device"
    state is HomeRoleRequestState.Failed -> state.reason
    else -> "Choose the app opened by the Home button"
}

private fun LauncherLocation.hasSearchSession(): Boolean = when (this) {
    is LauncherLocation.ShortcutSearch -> true
    is LauncherLocation.Destination -> destination == LauncherDestination.SEARCH
    is LauncherLocation.ItemDetails -> when (val source = origin) {
        is NavigationOrigin.ShortcutSearch -> true
        is NavigationOrigin.Destination -> source.destination == LauncherDestination.SEARCH
    }
}

private fun LaunchCoordinatorFailure.message(): String = when (this) {
    LaunchCoordinatorFailure.TARGET_UNAVAILABLE -> "The selected item is unavailable. Check its ROM folder or refresh the library and try again."
    LaunchCoordinatorFailure.REJECTED -> "Android rejected this launch. Check that the app or emulator is enabled."
    LaunchCoordinatorFailure.SNAPSHOT_FAILED -> "Could not save your position. The app was not opened."
    LaunchCoordinatorFailure.RECENCY_WRITE_FAILED -> "The app opened, but its launch history could not be saved."
    else -> "Android could not open the item. Your library order was preserved."
}
