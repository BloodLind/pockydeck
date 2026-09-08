package dev.handheld.launcher.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class ControllerSelectionFeedbackTest {
    @Test fun `only actual changed focus from navigation sounds once`() {
        var now = 0L
        var expectations = 0
        var selections = 0
        val feedback = ControllerSelectionFeedback({ expectations++ }, { selections++ }, { now })
        feedback.focus("first") // Initial restoration is silent.
        feedback.navigate(fromItem = true)
        now += 90
        feedback.focus("second")
        feedback.focus("second") // Republished footer/metadata is silent.
        feedback.navigate(fromItem = true)
        feedback.focus("second") // A bounded edge did not select another card.
        assertEquals(1, selections)
        assertEquals(2, expectations)
        feedback.navigate(fromItem = true)
        feedback.focus(null) // Moving out to a header is not a card selection.
        feedback.navigate(fromItem = false)
        feedback.focus("second")
        assertEquals(1, selections)
        assertEquals(3, expectations)
    }

    @Test fun `touch page changes and delayed restoration cannot replay pending selection audio`() {
        var now = 0L
        var selections = 0
        val feedback = ControllerSelectionFeedback({}, { selections++ }, { now })
        feedback.focus("first")
        feedback.navigate(fromItem = true)
        feedback.reset()
        feedback.focus("touched")
        feedback.navigate(fromItem = true)
        now += 251
        feedback.focus("restored")
        assertEquals(0, selections)
    }

    @Test fun `delayed header restoration keeps just its original movement cue`() {
        var now = 0L
        val played = mutableListOf<ControllerSoundCue>()
        val sounds = ControllerSoundPolicy({ true }, { true }, { now }, played::add)
        val feedback = ControllerSelectionFeedback(sounds::expectItemSelection, { sounds.onItemSelected() }, { now })
        sounds.setActive(true)
        feedback.focus(null)
        sounds.dispatch(dev.handheld.launcher.contract.SemanticInputAction.NAVIGATE_DOWN) {
            feedback.navigate(fromItem = false); true
        }
        now = 90 // MOVE has ended, but a second sound is still not warranted.
        feedback.focus("card")
        assertEquals(listOf(ControllerSoundCue.MOVE), played)
    }

    @Test fun `metadata republishing the old card cannot consume a deferred selection cue`() {
        var now = 0L
        var selections = 0
        val feedback = ControllerSelectionFeedback({}, { selections++ }, { now })
        feedback.focus("first")
        feedback.navigate(fromItem = true)
        feedback.focus("first")
        now = 90
        feedback.focus("second")
        assertEquals(1, selections)
    }
}
