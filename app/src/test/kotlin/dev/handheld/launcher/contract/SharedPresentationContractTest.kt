package dev.handheld.launcher.contract

import dev.handheld.launcher.core.domain.model.Availability
import dev.handheld.launcher.core.domain.model.ConfirmBackMapping
import dev.handheld.launcher.core.domain.model.ControllerFaceButton
import dev.handheld.launcher.core.domain.model.CurrentUserAndroidComponentId
import dev.handheld.launcher.core.domain.model.LibraryCategory
import dev.handheld.launcher.core.domain.model.LibraryItem
import dev.handheld.launcher.core.domain.model.StatusValue
import dev.handheld.launcher.core.domain.model.SupportedItemAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedPresentationContractTest {
    @Test
    fun footerAndDispatcherShareOneTypedActionMeaning() {
        val action = LauncherActionDescriptor(
            input = SemanticInputAction.CONFIRM,
            meaning = LauncherActionMeaning.ACTIVATE,
            label = "Open",
        )
        val menu = LauncherActionDescriptor(
            input = SemanticInputAction.MENU,
            meaning = LauncherActionMeaning.OPEN_MENU,
            label = "Menu",
        )
        val footer = ControllerActionFooter(listOf(action, menu))
        var received: LauncherActionDescriptor? = null
        val dispatcher = SemanticActionPort {
            received = it
            it.enabled
        }

        assertTrue(dispatcher.dispatch(footer.actions.first()))
        assertSame(action, received)
        assertEquals(LauncherActionMeaning.OPEN_MENU, footer.actions.last().meaning)
        assertEquals(ControllerFaceButton.A, ConfirmBackMapping.Default.confirm)
        assertEquals(ControllerFaceButton.B, ConfirmBackMapping.Default.back)
        assertEquals(
            listOf(
                InputDispatchOwner.IME,
                InputDispatchOwner.MODAL,
                InputDispatchOwner.FOCUSED_PAGE,
                InputDispatchOwner.SHELL,
            ),
            InputDispatchPrecedence.orderedOwners,
        )
    }

    @Test
    fun statusPresentationSeparatesAvailableUnavailableAndUnsupported() {
        val available = StatusPresentation("Battery", StatusValue.Available("75%"), "Battery 75 percent")
        val unavailable = StatusPresentation("Battery", StatusValue.Unavailable, "Battery unavailable")
        val unsupported = StatusPresentation("Temperature", StatusValue.Unsupported, "Temperature unsupported")

        assertTrue(available.shouldRender)
        assertEquals("75%", available.displayedValue)
        assertTrue(unavailable.shouldRender)
        assertEquals("—", unavailable.displayedValue)
        assertFalse(unsupported.shouldRender)
        assertNull(unsupported.displayedValue)
    }

    @Test
    fun itemCardAdapterFixtureUsesStableItemIdentity() {
        val component = CurrentUserAndroidComponentId("dev.fixture", "dev.fixture.GameActivity")
        val item = LibraryItem.AndroidApp(
            componentId = component,
            title = "Fixture Game",
            category = LibraryCategory.GAME,
            availability = Availability.Available,
            supportedActions = setOf(SupportedItemAction.OPEN),
        )
        val adapter = ItemToCardPresentationAdapter { source, _ ->
            LibraryItemCardPresentation(
                itemId = source.id,
                title = source.title,
                subtitle = "Android",
                typeLabel = "App",
                badges = listOf("Game"),
                availability = source.availability,
                supportedActions = source.supportedActions,
            )
        }

        val card = adapter.present(item, null)
        assertEquals(component.itemId, card.itemId)
        assertEquals("Fixture Game", card.title)
        assertEquals(setOf(SupportedItemAction.OPEN), card.supportedActions)
    }
}
