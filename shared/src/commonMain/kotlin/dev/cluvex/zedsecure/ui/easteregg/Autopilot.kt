package dev.cluvex.zedsecure.ui.easteregg

import kotlin.math.min
import kotlin.math.sign

class Autopilot(private val ship: Spacecraft, private val universe: Universe) : Entity {
    var enabled = false

    var target: Planet? = null
        private set

    var brakingDistance = 0f
        private set

    var landingAltitude = 0f
        private set

    var leadPoint: Vec2 = Vec2.Zero
        private set

    private var strategy = Strategy.Selecting
    private var detail = ""
    private var nextStrategyTime = 0f

    val telemetry: String
        get() = listOf(
            "---- AUTOPILOT ENGAGED ----",
            "TGT: ${target?.name?.uppercase() ?: "SELECTING…"}",
            "EXE: ${strategy.label}" + if (detail.isNotEmpty()) " ($detail)" else "",
        ).joinToString("\n")

    override fun update(sim: Simulator, dt: Float) {
        if (!enabled || sim.now < nextStrategyTime) return
        val previous = strategy

        if (ship.landing != null) {
            if (target != null) {
                strategy = Strategy.Landed
                detail = ""
                target = null
                landingAltitude = 0f
                nextStrategyTime = sim.now + SIGHTSEEING_TIME
            } else {
                strategy = Strategy.Launching
                detail = ""
                ship.thrust = vecFromAngle(ship.angle, 1f)
                nextStrategyTime = sim.now + LAUNCH_THRUST_TIME
            }
            return
        }

        val goal = target ?: pickTarget(sim).also { target = it; brakingDistance = 0f } ?: return

        val toTarget = goal.pos - ship.pos
        val altitude = toTarget.mag() - goal.radius
        landingAltitude = min(goal.radius, 100f)

        val relativeVelocity = ship.velocity - goal.velocity
        val closingSign = relativeVelocity.dot(toTarget / toTarget.mag()).sign
        val closingSpeed = relativeVelocity.mag() * closingSign
        val timeToTarget = if (closingSpeed != 0f) altitude / closingSpeed else 1_000f

        brakingDistance = expSmooth(
            current = brakingDistance,
            target = BRAKING_TIME * if (closingSpeed > 0f) closingSpeed else MAIN_ENGINE_ACCEL,
            dt = sim.dt,
        )

        leadPoint = goal.pos + vecFromAngle(goal.velocity.angle(), min(altitude / 2f, goal.velocity.mag()))

        when {
            altitude < landingAltitude -> {
                strategy = Strategy.Landing
                ship.angle = (ship.pos - goal.pos).angle()
                ship.thrust = Vec2.Zero
            }
            closingSpeed < 0f || altitude > brakingDistance -> {
                strategy = Strategy.Chasing
                ship.angle = (leadPoint - ship.pos).angle()
                ship.thrust = vecFromAngle(ship.angle, 1f)
            }
            else -> {
                strategy = Strategy.Approaching
                ship.angle = (-ship.velocity).angle()
                val decel = closingSpeed / timeToTarget / MAIN_ENGINE_ACCEL * 0.9f
                ship.thrust = vecFromAngle(ship.angle, decel)
            }
        }
        detail = "DV=%.0f D=%.0f T%+.1f".format(closingSpeed, altitude, timeToTarget)

        if (strategy != previous) nextStrategyTime = sim.now + STRATEGY_MIN_TIME
    }

    fun disengage() {
        enabled = false
        target = null
        ship.thrust = Vec2.Zero
        strategy = Strategy.Selecting
        detail = ""
    }

    private fun pickTarget(sim: Simulator): Planet? = universe.planets
        .filter { !it.explored }
        .minByOrNull { (it.pos - ship.pos).mag() }
        ?: universe.planets.randomOrNull(sim.rng)

    private enum class Strategy(val label: String) {
        Selecting("SELECTING"),
        Chasing("CHASING"),
        Approaching("APPROACHING"),
        Landing("LANDING"),
        Landed("LANDED"),
        Launching("LAUNCHING"),
    }

    private companion object {
        const val BRAKING_TIME = 5f
        const val SIGHTSEEING_TIME = 15f
        const val LAUNCH_THRUST_TIME = 5f
        const val STRATEGY_MIN_TIME = 0.5f
    }
}
