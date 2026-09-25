package dev.handheld.launcher.shell

import dev.handheld.launcher.contract.SemanticInputAction
import dev.handheld.launcher.core.domain.model.ConfirmBackMapping
import dev.handheld.launcher.core.domain.model.ControllerButtonLayout
import dev.handheld.launcher.core.domain.model.ControllerFaceButton
import dev.handheld.launcher.input.ControllerButton
import dev.handheld.launcher.input.ControllerInputEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ControllerButtonLayoutTest {
    @Test fun `layout relabels all face buttons without moving actions or overriding confirm choice`() = runTest {
        val actions = listOf(SemanticInputAction.CONFIRM, SemanticInputAction.BACK,
            SemanticInputAction.SECONDARY, SemanticInputAction.TERTIARY)
        for (mapping in listOf(ConfirmBackMapping.Default,
            ConfirmBackMapping(ControllerFaceButton.B, ControllerFaceButton.A))) {
            for ((layout, labels) in listOf(
                ControllerButtonLayout.XBOX to listOf("A", "B", "X", "Y"),
                ControllerButtonLayout.NINTENDO to listOf("B", "A", "Y", "X"),
            )) {
                val swapped = mapping.confirm == ControllerFaceButton.B
                val expectedLabels = if (swapped) listOf(labels[1], labels[0], labels[2], labels[3]) else labels
                assertEquals(expectedLabels, actions.map { it.footerLegend(mapping, layout) })
                val received = mutableListOf<SemanticInputAction>()
                val engine = ControllerInputEngine(this, { mapping }, { false }) { received += it; true }
                listOf(ControllerButton.A, ControllerButton.B, ControllerButton.X, ControllerButton.Y)
                    .forEachIndexed { index, button ->
                        engine.onButtonDown(button, false, index * 100L)
                        engine.onButtonUp(button)
                    }
                val expectedActions = if (swapped) listOf(actions[1], actions[0], actions[2], actions[3]) else actions
                assertEquals("$layout preserves the physical bindings", expectedActions, received)
                assertEquals("SELECT", SemanticInputAction.ITEM_DETAILS.footerLegend(mapping, layout))
                assertEquals("START", SemanticInputAction.MENU.footerLegend(mapping, layout))
            }
        }
    }
}
