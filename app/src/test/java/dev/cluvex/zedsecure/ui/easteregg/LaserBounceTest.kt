package dev.cluvex.zedsecure.ui.easteregg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LaserBounceTest {
    private val w = 1080f
    private val h = 2400f

    @Test
    fun `beam starts on the bottom edge and exits above the top`() {
        val pts = LaserFlight.random(1).points(w, h)
        assertEquals("starts at the bottom", h, pts.first().y, 0.01f)
        assertTrue("last point must be above the top edge", pts.last().y < 0f)
    }

    @Test
    fun `every bounce alternates between the two walls`() {
        val flight = LaserFlight.random(7)
        val pts = flight.points(w, h)

        val walls = pts.subList(1, pts.size - 1)
        assertTrue("expected at least two bounces", walls.size >= 2)
        walls.forEachIndexed { i, p ->
            val onLeft = p.x < w / 2f
            val expectLeft = if (flight.startFromLeft) i % 2 == 0 else i % 2 == 1
            assertEquals("bounce $i is on the wrong wall", expectLeft, onLeft)
        }
    }

    @Test
    fun `the beam only ever climbs`() {
        val pts = LaserFlight.random(3).points(w, h)
        for (i in 1 until pts.size) {
            assertTrue(
                "leg $i went downwards (${pts[i - 1].y} -> ${pts[i].y})",
                pts[i].y < pts[i - 1].y,
            )
        }
    }

    @Test
    fun `wall hits land inside the screen, not clipped by it`() {
        val pts = LaserFlight.random(11).points(w, h)
        pts.subList(1, pts.size - 1).forEach {
            assertTrue("x=${it.x} outside 0..$w", it.x in 0f..w)
        }
    }

    @Test
    fun `flights are deterministic per trigger but differ between triggers`() {
        assertEquals(LaserFlight.random(5), LaserFlight.random(5))
        assertNotEquals(LaserFlight.random(5), LaserFlight.random(6))
    }
}
