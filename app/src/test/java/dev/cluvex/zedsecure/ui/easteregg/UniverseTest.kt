package dev.cluvex.zedsecure.ui.easteregg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class UniverseTest {
    private fun universe(seed: Long) = Universe(Namer(), seed).apply { initRandom() }

    private fun Universe.fly(seconds: Float, fps: Int = 60) {
        val stepNanos = 1_000_000_000L / fps
        var clock = stepNanos
        step(clock)
        repeat((seconds * fps).toInt()) {
            clock += stepNanos
            step(clock)
        }
    }

    @Test
    fun `the same seed always builds the same system`() {
        val a = universe(20260903L)
        val b = universe(20260903L)

        assertEquals(a.star.name, b.star.name)
        assertEquals(a.star.cls, b.star.cls)
        assertEquals(a.planets.size, b.planets.size)
        a.planets.zip(b.planets).forEach { (left, right) ->
            assertEquals(left.name, right.name)
            assertEquals(left.orbitRadius, right.orbitRadius, 0f)
            assertEquals(left.description, right.description)
        }
    }

    @Test
    fun `planets hold their orbits`() {
        val u = universe(7L)
        val before = u.planets.map { it.orbitRadius }
        u.fly(seconds = 300f)
        u.planets.forEachIndexed { i, planet ->

            assertEquals(before[i], planet.pos.mag(), before[i] * 0.001f)
        }
    }

    @Test
    fun `a belly-down approach lands and plants a flag`() {
        val u = universe(11L)
        val target = u.planets.first()

        val approach = (u.ship.pos - target.pos).angle()
        u.ship.pos = target.pos + vecFromAngle(approach, target.radius + SPACECRAFT_RADIUS + 5f)
        u.ship.angle = approach
        u.ship.velocity = vecFromAngle(approach, -50f)

        u.fly(seconds = 3f)

        assertNotNull("expected a landing", u.ship.landing)
        assertEquals(target.name, u.ship.landing?.planet?.name)
        assertTrue(target.explored)
        assertNotNull(target.flagAngle)
        assertTrue("landing should describe an activity", u.ship.landing!!.activity.isNotBlank())
    }

    @Test
    fun `a sideways approach bounces off instead of landing`() {
        val u = universe(11L)
        val target = u.planets.first()
        val approach = (u.ship.pos - target.pos).angle()
        u.ship.pos = target.pos + vecFromAngle(approach, target.radius + SPACECRAFT_RADIUS + 5f)

        u.ship.angle = approach + PIf / 2f
        u.ship.velocity = vecFromAngle(approach, -400f)

        u.fly(seconds = 1f)

        assertNull(u.ship.landing)
        assertTrue("should not have been surveyed", !target.explored)

        assertTrue((u.ship.pos - target.pos).mag() >= target.radius)
    }

    @Test
    fun `a landed ship rides along with its planet and launches on a held burn`() {
        val u = universe(11L)
        val target = u.planets.first()
        val approach = (u.ship.pos - target.pos).angle()
        u.ship.pos = target.pos + vecFromAngle(approach, target.radius + SPACECRAFT_RADIUS + 5f)
        u.ship.angle = approach
        u.ship.velocity = vecFromAngle(approach, -50f)
        u.fly(seconds = 3f)
        assertNotNull(u.ship.landing)

        u.fly(seconds = 20f)
        val altitude = (u.ship.pos - target.pos).mag() - target.radius
        assertTrue("drifted off the surface: $altitude", abs(altitude - SPACECRAFT_RADIUS) < 2f)

        u.ship.thrust = vecFromAngle(u.ship.angle, 1f)
        u.fly(seconds = 5f)
        assertNull("should have left the pad", u.ship.landing)
        assertTrue((u.ship.pos - target.pos).mag() > target.radius + 200f)
    }

    @Test
    fun `the autopilot surveys the system on its own`() {
        val u = universe(20260903L)
        u.add(Autopilot(u.ship, u).apply { enabled = true })

        u.fly(seconds = 900f)

        assertTrue(
            "autopilot explored ${u.exploredCount} of ${u.planets.size} in 15 minutes",
            u.exploredCount >= 2,
        )
    }

    @Test
    fun `the ship cannot leave the universe`() {
        val u = universe(3L)

        u.ship.pos = vecFromAngle(0f, UNIVERSE_RANGE * 0.9f)
        u.ship.angle = 0f
        u.ship.thrust = vecFromAngle(0f, 1f)

        u.fly(seconds = 300f)

        assertTrue("escaped to ${u.ship.pos.mag()}", u.ship.pos.mag() <= UNIVERSE_RANGE)
    }
}
