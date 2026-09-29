package dev.cluvex.zedsecure.ui

import androidx.compose.ui.geometry.Rect
import dev.cluvex.zedsecure.ui.onboarding.tourCaptionPlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TourCaptionPlacementTest {
    private val screen = 2400f
    private val top = 120f
    private val bottom = 2340f
    private val arm = 138f
    private val caption = 520f

    private fun place(hole: Rect?, captionHeight: Float = caption) = tourCaptionPlacement(
        hole = hole,
        captionHeight = captionHeight,
        overlayHeight = screen,
        topLimit = top,
        bottomLimit = bottom,
        arm = arm,
    )

    @Test
    fun `a target near the top puts the caption under it`() {
        val hole = Rect(80f, 300f, 1000f, 700f)
        val p = place(hole)

        assertTrue("there is room below, so the caption goes below", p.below)
        assertEquals(hole.bottom + arm, p.top, 0.5f)
    }

    @Test
    fun `the navigation bar gets the caption above it, not off the screen`() {
        val hole = Rect(120f, 2130f, 400f, 2300f)
        val p = place(hole)

        assertFalse("nothing fits under the tab bar", p.below)
        assertTrue("the caption stays on screen", p.top >= top)
        assertTrue("and clear of the target", p.top + caption <= hole.top)
    }

    @Test
    fun `a target too tall for either side still leaves the caption on screen`() {
        val hole = Rect(40f, 260f, 1040f, 2200f)
        val p = place(hole)

        assertTrue("never above the status bar", p.top >= top)
        assertTrue("never past the gesture bar", p.top + caption <= bottom)
    }

    @Test
    fun `a step whose control is missing centres the caption`() {
        val p = place(null)

        assertEquals((screen - caption) / 2f, p.top, 0.5f)
    }

    @Test
    fun `the roomier side wins when neither side fits the caption`() {
        val hole = Rect(80f, 1300f, 1000f, 1900f)
        val p = place(hole)

        assertFalse(p.below)
        assertTrue(p.top >= top)
    }

    @Test
    fun `a taller caption is still placed from its own height`() {
        val tall = 900f
        val hole = Rect(80f, 1500f, 1000f, 1700f)
        val p = place(hole, captionHeight = tall)

        assertTrue("on screen at the top", p.top >= top)
        assertTrue("and at the bottom", p.top + tall <= bottom)
    }
}
