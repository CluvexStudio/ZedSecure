package dev.cluvex.zedsecure.domain

import dev.cluvex.zedsecure.domain.model.AppSettings
import dev.cluvex.zedsecure.domain.model.ScreenTransition
import dev.cluvex.zedsecure.ui.motion.screenTransition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTransitionTest {
    @Test
    fun `every style produces a transition, forward and back`() {
        for (style in ScreenTransition.entries) {
            for (forward in listOf(true, false)) {
                for (direction in listOf(1, -1)) {
                    assertNotNull(
                        "$style must animate (forward=$forward, direction=$direction)",
                        screenTransition(style, forward, direction),
                    )
                }
            }
        }
    }

    @Test
    fun `a stored style comes back as itself`() {
        assertEquals(
            listOf("Fade", "Slide", "Push", "Depth", "Elastic", "Curtain", "Flip"),
            ScreenTransition.entries.map { it.name },
        )
    }

    @Test
    fun `the default is a directional style, not a cut`() {
        assertEquals(ScreenTransition.Push, AppSettings().screenTransition)
    }

    @Test
    fun `there are enough styles to be worth choosing between`() {
        assertTrue(ScreenTransition.entries.size >= 5)
    }
}
