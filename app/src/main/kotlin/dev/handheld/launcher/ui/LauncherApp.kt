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
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.handheld.launcher.contract.*
import dev.handheld.launcher.core.data.android.status.DeviceStatusSnapshot
import dev.handheld.launcher.core.data.discovery.AndroidCatalogRefreshState
import dev.handheld.launcher.core.designsystem.contract.ModalFocusLifecycle
import dev.handheld.launcher.core.designsystem.controls.LauncherButton
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
import dev.handheld.launcher.feature.settings.*
import dev.handheld.launcher.launch.*
import dev.handheld.launcher.navigation.*
import dev.handheld.launcher.platform.home.HomeRoleRequestState
import dev.handheld.launcher.platform.system.SupportedSystemAction
import dev.handheld.launcher.shell.*
import kotlinx.coroutines.launch
import java.util.Locale

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
) {
    val location by app.navigation.location.collectAsStateWithLifecycle()
    val mapping by app.mapping.collectAsStateWithLifecycle()
    val appError by app.error.collectAsStateWithLifecycle()
    val homeState by home.state.collectAsStateWithLifecycle()
    val launchState by container.launchCoordinator.state.collectAsStateWithLifecycle()
    val roleState by container.homeRoleRequests.state.collectAsStateWithLifecycle()
    val status by container.deviceStatus.status.collectAsStateWithLifecycle(DeviceStatusSnapshot())
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
    val pageFocus = remember { FocusRequester() }
    val shellFocus = remember { FocusRequester() }
    val saveablePages = rememberSaveableStateHolder()
    var focused by remember(location) { mutableStateOf<FocusedControlAction?>(null) }
    var focusedDock by remember(location) { mutableStateOf<LauncherDestination?>(null) }
    var menuVisible by remember { mutableStateOf(false) }
    var menuItemId by remember { mutableStateOf<ItemId?>(null) }
    var modalOrigin by remember { mutableStateOf<LauncherLocation?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var queryFocusRequest by remember { mutableIntStateOf(0) }
    var settingsSection by rememberSaveable { mutableStateOf("Launcher") }
    val searchableActions = remember(container) {
        container.systemActions.actions + listOf(
            SupportedSystemAction("launcher-controls", "Confirm and Back buttons", "Choose the launcher's A/B controller mapping", "launcher:controls"),
            SupportedSystemAction("launcher-home", "Default Home launcher", "Choose which launcher opens with the Home button", "launcher:home"),
        )
    }
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) { onImeVisibilityChanged(imeVisible) }
    val modalVisible = menuVisible || errorMessage != null

    fun snapshot(): DestinationSnapshot = if (destination == LauncherDestination.HOME) home.state.value.snapshot()
        else pageModels[destination]?.state?.value?.snapshot() ?: DestinationSnapshot(destination)
    fun selectDestination(value: LauncherDestination) {
        keyboard?.hide()
        focused = null
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
            shellFocus.saveFocusedChild()
            modalOrigin = location
        }
        errorMessage = message
    }
    fun openSystem(key: String) {
        when (key) {
            "launcher-controls", "launcher-home" -> {
                settingsSection = if (key == "launcher-controls") "Controls" else "Launcher"
                selectDestination(LauncherDestination.SETTINGS)
            }
            else -> if (!container.systemActions.open(key)) showError("This Android setting is unavailable.")
        }
    }
    fun goBack() {
        when {
            imeVisible -> keyboard?.hide()
            errorMessage != null -> { errorMessage = null; app.error.value = null; dataActions.clearError(); container.launchCoordinator.clearResult() }
            menuVisible -> menuVisible = false
            else -> { focused = null; app.navigation.back() }
        }
    }
    fun openSearch() {
        if (destination != LauncherDestination.SEARCH) {
            pageFocus.saveFocusedChild()
            focused = null
            app.navigation.openShortcutSearch(destination)
        }
        queryFocusRequest++
    }
    fun showMenu() {
        shellFocus.saveFocusedChild()
        menuItemId = focused?.itemId ?: (location as? LauncherLocation.ItemDetails)?.itemId
        modalOrigin = location
        menuVisible = true
    }
    val selectedItemId = focused?.itemId ?: (location as? LauncherLocation.ItemDetails)?.itemId
    val selectedItem = library.allItems.find { it.id == selectedItemId }
    val primary = if (modalVisible) LauncherActionDescriptor(SemanticInputAction.CONFIRM, LauncherActionMeaning.ACTIVATE, "Select")
        else focused?.descriptor?.copy(input = SemanticInputAction.CONFIRM)
            ?: LauncherActionDescriptor(SemanticInputAction.CONFIRM, LauncherActionMeaning.ACTIVATE, "Select", false)
    val footer = ControllerActionFooter(buildList {
        add(primary)
        add(LauncherActionDescriptor(SemanticInputAction.BACK, LauncherActionMeaning.GO_BACK, "Back"))
        if (!modalVisible) {
            add(LauncherActionDescriptor(SemanticInputAction.SECONDARY, LauncherActionMeaning.OPEN_SEARCH, "Search"))
            if (selectedItem != null && location !is LauncherLocation.ItemDetails)
                add(LauncherActionDescriptor(SemanticInputAction.TERTIARY, LauncherActionMeaning.OPEN_DETAILS, "Details"))
            add(LauncherActionDescriptor(SemanticInputAction.MENU, LauncherActionMeaning.OPEN_MENU, "Menu"))
        }
    })
    val actionPort = SemanticActionPort { descriptor ->
        if (!descriptor.enabled || descriptor !in footer.actions) false else {
            when (descriptor.input) {
                SemanticInputAction.CONFIRM -> nativeConfirm()
                SemanticInputAction.BACK -> { goBack(); true }
                SemanticInputAction.SECONDARY -> { openSearch(); true }
                SemanticInputAction.TERTIARY -> { selectedItemId?.let(::openDetails); selectedItemId != null }
                SemanticInputAction.MENU -> { showMenu(); true }
                else -> false
            }
        }
    }
    SideEffect {
        bindInput { action ->
            inputMode.requestInputMode(InputMode.Keyboard)
            val direction = when (action) {
                SemanticInputAction.NAVIGATE_UP -> FocusDirection.Up
                SemanticInputAction.NAVIGATE_DOWN -> FocusDirection.Down
                SemanticInputAction.NAVIGATE_LEFT -> FocusDirection.Left
                SemanticInputAction.NAVIGATE_RIGHT -> FocusDirection.Right
                else -> null
            }
            when {
                direction != null -> focusManager.moveFocus(direction)
                action == SemanticInputAction.BACK -> { goBack(); true }
                modalVisible -> if (action == SemanticInputAction.CONFIRM) nativeConfirm() else true
                action == SemanticInputAction.PREVIOUS_DESTINATION || action == SemanticInputAction.NEXT_DESTINATION -> {
                    val order = LauncherDestination.dockOrder
                    val delta = if (action == SemanticInputAction.NEXT_DESTINATION) 1 else -1
                    val activeDestination = app.navigation.location.value.selectedDockDestination()
                    selectDestination(order[(order.indexOf(activeDestination) + delta + order.size) % order.size]); true
                }
                action == SemanticInputAction.PREVIOUS_FILTER || action == SemanticInputAction.NEXT_FILTER -> {
                    val filters = if (destination in pageModels) collectionFilterKeys(destination) else emptyList()
                    if (filters.isEmpty()) false else {
                        val vm = pageModels.getValue(destination)
                        val delta = if (action == SemanticInputAction.NEXT_FILTER) 1 else -1
                        val index = filters.indexOf(vm.state.value.filter).coerceAtLeast(0)
                        vm.filter(filters[(index + delta + filters.size) % filters.size]); true
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
    LaunchedEffect(launchState) {
        (launchState as? LaunchCoordinatorState.Failed)?.let { showError(it.reason.message()) }
    }
    LaunchedEffect(modalVisible) {
        if (!modalVisible && modalOrigin != null) {
            withFrameNanos { }
            if (modalOrigin == location) runCatching { shellFocus.restoreFocusedChild() }
            modalOrigin = null
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val metrics = remember(maxWidth, maxHeight, density.density, density.fontScale) {
            with(density) { ShellMetrics.calculate(ShellMetricsInput(maxWidth.roundToPx(), maxHeight.roundToPx(), this.density, fontScale)) }
        }
        val imeBottom = with(density) { WindowInsets.ime.getBottom(this).toDp() }
        LauncherTheme(reducedMotion, metrics.referenceScale) {
            val registry = LauncherRouteRegistry(LauncherDestination.dockOrder.map { route ->
                LauncherDestinationRoute(route) { context ->
                    val bounds = context.modifier.focusRequester(pageFocus).focusGroup()
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
                                    onFocusedAction = { focused = it },
                                ), container.iconLoader)
                            else EmptyState("Item unavailable", "This item is no longer in the catalog.", "Back", ::goBack, bounds)
                        } else when (route) {
                            LauncherDestination.HOME -> HomeRoute(home, metrics, container.iconLoader, bounds,
                                onOpenLibrary = { selectDestination(LauncherDestination.LIBRARY) },
                                onOpenDetails = ::openDetails,
                                allowFocusRequest = focusedDock == null && !modalVisible,
                                onFocusedActionChanged = { focused = it?.let { value -> FocusedControlAction(value.descriptor, value.onActivate, value.itemId) } })
                            LauncherDestination.SETTINGS -> SettingsScreen(
                                SettingsScreenState(mapping, homeRoleSummary(homeRoleHeld, roleState),
                                    listOf(LibraryCategory.GAME, LibraryCategory.EMULATOR, LibraryCategory.OTHER).map { category ->
                                        CategorySummary(category, library.allItems.count { it.availability == Availability.Available && (library.overrides[it.id]?.category ?: it.category) == category })
                                    }), bounds, SettingsCallbacks(
                                    onSetConfirmBackMapping = app::setMapping,
                                    onRequestDefaultHome = { container.homeRoleRequests.requestSelection() },
                                    onOpenSystemAction = ::openSystem,
                                    onOpenCategory = { category ->
                                        pageModels.getValue(LauncherDestination.APPS).filter(when (category) {
                                            LibraryCategory.GAME -> "games"; LibraryCategory.EMULATOR -> "emulators"; else -> "other"
                                        }); selectDestination(LauncherDestination.APPS)
                                    }, onFocusedAction = { focused = it },
                                ), container.systemActions.actions, initialSection = settingsSection)
                            else -> {
                                val vm = pageModels.getValue(route)
                                val current = pageStates.getValue(route)
                                val callbacks = CollectionScreenCallbacks(vm::select,
                                    onOpen = { container.launchCoordinator.submit(it, vm.state.value.snapshot().copy(selectedItemId = it)) },
                                    onOpenDetails = ::openDetails, onFavorite = vm::setFavorite,
                                    onFilter = vm::filter, onToggleSort = vm::toggleSort,
                                    onRememberAnchor = vm::rememberAnchor,
                                    onRetry = { vm.clearError(); container.androidCatalog.refresh() },
                                    onOpenSystemAction = ::openSystem,
                                    onOpenLibrary = { selectDestination(LauncherDestination.LIBRARY) },
                                    onFocusedAction = { focused = it })
                                when (route) {
                                    LauncherDestination.LIBRARY -> LibraryScreen(current, bounds, callbacks, searchableActions, container.iconLoader)
                                    LauncherDestination.APPS -> AppsScreen(current, bounds, callbacks, container.iconLoader)
                                    LauncherDestination.FAVORITES -> FavoritesScreen(current, bounds, callbacks, container.iconLoader)
                                    LauncherDestination.SEARCH -> SearchScreen(current, bounds, callbacks, vm::query,
                                        searchableActions, container.iconLoader, queryFocusRequest)
                                    else -> Unit
                                }
                            }
                        }
                    }
                }
            })
            LauncherShell(metrics, LauncherShellState(destination, focusedDock, status.toShellStatus(), footer),
                actionPort, mapping, LauncherShellInsets(imeBottom),
                modifier = Modifier.focusRequester(shellFocus).focusGroup(),
                onDestinationSelected = ::selectDestination,
                onDestinationFocused = { value ->
                    focusedDock = value
                    if (value != null) focused = FocusedControlAction(
                        LauncherActionDescriptor(SemanticInputAction.CONFIRM, LauncherActionMeaning.CHANGE_DESTINATION,
                            value.persistedKey.replaceFirstChar { it.uppercase() }), { selectDestination(value) })
                }, content = { registry.Render(location, it) }, overlay = {
                    if (menuVisible) LauncherDialog("Menu", { menuVisible = false }) {
                        library.allItems.find { it.id == menuItemId }?.let { item ->
                            LauncherButton(if (item.id in library.favorites) "Remove favorite" else "Add favorite", {
                                dataActions.setFavorite(item.id, item.id !in library.favorites); menuVisible = false
                            }, Modifier.fillMaxWidth())
                            LauncherButton("Item details", { menuVisible = false; openDetails(item.id) }, Modifier.fillMaxWidth())
                        }
                        LauncherButton("Refresh library", { container.androidCatalog.refresh(); menuVisible = false }, Modifier.fillMaxWidth())
                        LauncherButton("Settings", { menuVisible = false; selectDestination(LauncherDestination.SETTINGS) }, Modifier.fillMaxWidth())
                    }
                    errorMessage?.let { message ->
                        LauncherDialog("Unable to complete action", { errorMessage = null; app.error.value = null; dataActions.clearError(); container.launchCoordinator.clearResult() }) {
                            LauncherText(message)
                        }
                    }
                })
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

private fun LaunchCoordinatorFailure.message(): String = when (this) {
    LaunchCoordinatorFailure.TARGET_UNAVAILABLE -> "The selected app is unavailable. Refresh the library and try again."
    LaunchCoordinatorFailure.REJECTED -> "Android rejected this launch. Check that the app is enabled."
    LaunchCoordinatorFailure.SNAPSHOT_FAILED -> "Could not save your position. The app was not opened."
    LaunchCoordinatorFailure.RECENCY_WRITE_FAILED -> "The app opened, but its launch history could not be saved."
    else -> "Android could not open the app. Your library order was preserved."
}

private fun DeviceStatusSnapshot.toShellStatus(): LauncherShellStatus {
    fun <T : Any> StatusValue<T>.format(format: (T) -> String): StatusValue<String> = when (this) {
        is StatusValue.Available -> StatusValue.Available(format(value))
        StatusValue.Unavailable -> StatusValue.Unavailable
        StatusValue.Unsupported -> StatusValue.Unsupported
    }
    fun reading(label: String, value: StatusValue<String>, glyph: ShellStatusGlyph) =
        ShellStatusReading(StatusPresentation(label, value, label), glyph)
    val usedMemory = usedMemoryBytes
    val totalMemory = totalMemoryBytes
    val memory = if (usedMemory is StatusValue.Available && totalMemory is StatusValue.Available)
        StatusValue.Available(String.format(Locale.getDefault(), "%.1f / %.0f GB", usedMemory.value / 1_073_741_824.0, totalMemory.value / 1_073_741_824.0))
        else StatusValue.Unavailable
    return LauncherShellStatus(readings = listOf(
        reading("Battery temperature", batteryTemperatureCelsius.format { String.format(Locale.getDefault(), "%.0f°C", it) }, ShellStatusGlyph.Temperature),
        reading("Memory used", memory, ShellStatusGlyph.Memory),
        reading("Storage available", freeStorageBytes.format { "${it / 1_073_741_824} GB free" }, ShellStatusGlyph.Storage),
    ), rightReadings = listOf(
        reading("Wi-Fi", wifiConnected.format { if (it) "On" else "Off" }, ShellStatusGlyph.Wifi),
        reading("Battery", batteryPercent.format { "$it%" }, ShellStatusGlyph.Battery),
    ))
}
