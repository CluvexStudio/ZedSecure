package dev.cluvex.zedsecure.ui.easteregg

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

const val UNIVERSE_RANGE = 200_000f

private val PLANET_COUNT_RANGE = 2..9
private val STAR_RADIUS_RANGE = 1_000f..8_000f
private val PLANET_RADIUS_RANGE = 50f..2_000f
private val PLANET_ORBIT_RANGE = 16_000f..150_000f

private const val GRAVITATION = 7.5e-2f
private const val KEPLER_CONSTANT = 50f
private const val PLANETARY_DENSITY = 2.5f
private const val STELLAR_DENSITY = 0.5f
private const val SPHERE_VOLUME = 4f / 3f * PIf

const val SPACECRAFT_MASS = 10f
const val SPACECRAFT_RADIUS = 12f
const val MAIN_ENGINE_ACCEL = 1_000f
private const val CRAFT_SPEED_LIMIT = 5_000f

private const val LAUNCH_MECO = 2f

private const val LAUNCH_HOLD = 1f

private const val LANDING_LIFETIME = 15 * 60f

enum class StarClass { O, B, A, F, G, K, M }

fun starColor(cls: StarClass): Color = when (cls) {
    StarClass.O -> Color(0xFF6666FF)
    StarClass.B -> Color(0xFFCCCCFF)
    StarClass.A -> Color(0xFFEEEEFF)
    StarClass.F -> Color(0xFFFFFFFF)
    StarClass.G -> Color(0xFFFFFF66)
    StarClass.K -> Color(0xFFFFCC33)
    StarClass.M -> Color(0xFFFF8800)
}

open class Planet(
    val orbitCenter: Vec2,
    radius: Float,
    pos: Vec2,

    var speed: Float,
    var color: Color = EggColors.PlanetRim,
) : Body() {
    val orbitRadius: Float = pos.distanceTo(orbitCenter)

    var description = ""
    var atmosphere = ""
    var flora = ""
    var fauna = ""
    var explored = false

    var flagAngle: Float? = null

    init {
        this.radius = radius
        this.pos = pos
        mass = SPHERE_VOLUME * radius.pow(3) * PLANETARY_DENSITY
    }

    override fun update(sim: Simulator, dt: Float) {
        velocity = vecFromAngle((pos - orbitCenter).angle() + PIf / 2f, speed)
        super.update(sim, dt)
    }

    override fun postUpdate(sim: Simulator, dt: Float) {
        pos = orbitCenter + vecFromAngle((pos - orbitCenter).angle(), orbitRadius)
        super.postUpdate(sim, dt)
    }
}

class Star(val cls: StarClass, radius: Float) :
    Planet(orbitCenter = Vec2.Zero, radius = radius, pos = Vec2.Zero, speed = 0f) {
    var anim = 0f
        private set

    init {
        mass = SPHERE_VOLUME * radius.pow(3) * STELLAR_DENSITY
        color = starColor(cls)
        collides = false
    }

    override fun update(sim: Simulator, dt: Float) {
        anim += dt
    }

    override fun postUpdate(sim: Simulator, dt: Float) = Unit
}

class Landing(
    var ship: Spacecraft?,
    val planet: Planet,
    val angle: Float,
    val activity: String,
) : Constraint, Removable {
    private val fuse = Fuse(LANDING_LIFETIME)
    override val expired: Boolean get() = fuse.expired

    override fun solve(sim: Simulator, dt: Float) {
        ship?.let { craft ->

            craft.pos = planet.pos + vecFromAngle(angle, craft.radius + planet.radius)
            craft.angle = angle
        }
        fuse.burn(dt)
    }
}

class Spark(
    ttl: Float,
    val style: Style,
    val color: Color,
    val size: Float,
    collides: Boolean = false,
    mass: Float = 0f,
) : Body("spark"), Removable {
    enum class Style { Dot, Ring, Line }

    private val fuse = Fuse(ttl)
    override val expired: Boolean get() = fuse.expired
    val life: Float get() = fuse.burnt

    init {
        this.collides = collides
        this.mass = mass
    }

    override fun update(sim: Simulator, dt: Float) {
        super.update(sim, dt)
        fuse.burn(dt)
    }
}

class Track(private val capacity: Int = 2_000) {
    private val _positions = ArrayDeque<Vec2>(capacity)
    val positions: List<Vec2> get() = _positions

    fun add(p: Vec2) {
        while (_positions.size >= capacity - 1) {
            _positions.removeFirst()
            _positions.removeFirst()
        }
        _positions.addLast(p)
    }

    fun clear() = _positions.clear()
}

class Spacecraft : Body("ship") {
    var thrust: Vec2 = Vec2.Zero
    var landing: Landing? = null

    var transit = false

    var launchClock = 0f
    val track = Track()

    init {
        mass = SPACECRAFT_MASS
        radius = SPACECRAFT_RADIUS
    }

    override fun update(sim: Simulator, dt: Float) {
        val power = thrust.mag().coerceIn(0f, 1f)
        if (power > 0f) {
            var deltaV = MAIN_ENGINE_ACCEL * power * dt
            landing?.let { site ->
                if (launchClock == 0f) launchClock = sim.now + LAUNCH_HOLD
                if (sim.now > launchClock) {
                    site.ship = null
                    landing = null
                } else {
                    deltaV = 0f
                }
            }
            velocity += vecFromAngle(angle, deltaV)
        } else {
            launchClock = 0f
        }

        if (velocity.mag() > CRAFT_SPEED_LIMIT) {
            velocity = vecFromAngle(velocity.angle(), CRAFT_SPEED_LIMIT)
        }

        super.update(sim, dt)
    }

    override fun postUpdate(sim: Simulator, dt: Float) {
        super.postUpdate(sim, dt)
        track.add(pos)

        val power = thrust.mag()
        if (power > 0f && sim.rng.nextFloat() < power) {
            sim.add(
                Spark(
                    ttl = sim.rng.nextFloat() * 0.5f + 0.5f,
                    style = Spark.Style.Ring,
                    color = Color(0x40FFFFFF),
                    size = 1f,
                    collides = true,
                    mass = 1f,
                ).also { spark ->
                    spark.pos = pos
                    spark.previousPos = pos
                    spark.velocity = velocity + vecFromAngle(
                        angle + (sim.rng.nextFloat() - 0.5f) * 0.4f,
                        -MAIN_ENGINE_ACCEL * power * 10f * dt,
                    )
                }
            )
        }
    }
}

class Universe(private val namer: Namer, seed: Long) : Simulator(seed) {
    lateinit var star: Star
        private set
    lateinit var ship: Spacecraft
        private set

    val planets = mutableListOf<Planet>()

    private val bodies = mutableListOf<Planet>()

    var latestDiscovery: Planet? = null
        private set

    val exploredCount: Int get() = planets.count { it.explored }

    fun initRandom() {
        val systemName = namer.nameSystem(rng)
        star = Star(
            cls = StarClass.entries[rng.nextInt(StarClass.entries.size)],
            radius = rng.nextFloatIn(STAR_RADIUS_RANGE),
        ).apply { name = systemName }

        repeat(rng.nextInt(PLANET_COUNT_RANGE.first, PLANET_COUNT_RANGE.last + 1)) {
            val radius = rng.nextFloatIn(PLANET_RADIUS_RANGE)
            val orbitRadius = rng.nextFloatIn(PLANET_ORBIT_RANGE)

            val period = sqrt(orbitRadius.pow(3f) / star.mass) * KEPLER_CONSTANT
            planets += Planet(
                orbitCenter = star.pos,
                radius = radius,
                pos = star.pos + vecFromAngle(rng.nextFloat() * PI2f, orbitRadius),
                speed = PI2f * orbitRadius / period,
            ).apply {
                description = namer.describePlanet(rng)
                atmosphere = namer.describeAtmosphere(rng)
                flora = namer.describeLife(rng)
                fauna = namer.describeLife(rng)
            }
        }

        planets.sortBy { it.orbitRadius }
        planets.forEachIndexed { index, planet -> planet.name = "$systemName ${index + 1}" }
        planets.forEach { add(it) }
        add(star)
        bodies += planets
        bodies += star

        ship = Spacecraft().apply {
            pos = vecFromAngle(rng.nextFloat() * PI2f, rng.nextFloatIn(PLANET_ORBIT_RANGE))
            angle = rng.nextFloat() * PI2f
        }
        add(ship)
        add(Ringfence(UNIVERSE_RANGE, ship))
    }

    fun closestPlanet(): Planet = bodies.minBy { (it.pos - ship.pos).mag() }

    override fun updateAll(dt: Float, snapshot: List<Entity>) {
        ship.transit = false
        bodies.forEach { body ->
            val toBody = body.pos - ship.pos
            val d = toBody.mag()
            when {
                d < body.radius -> if (body is Star) ship.transit = true

                now > ship.launchClock + LAUNCH_MECO ->
                    ship.velocity += vecFromAngle(toBody.angle(), GRAVITATION * body.mass / d.pow(2)) * dt
            }
        }
        super.updateAll(dt, snapshot)
    }

    override fun solveAll(dt: Float, snapshot: List<Constraint>) {
        if (ship.landing == null) {
            val planet = closestPlanet()
            if (planet.collides) {
                val outward = ship.pos - planet.pos
                val surfaceAngle = outward.angle()
                val gap = outward.mag() - ship.radius - planet.radius
                if (gap < 0f) touchdown(planet, surfaceAngle, gap)
            }
        }
        super.solveAll(dt, snapshot)
    }

    private fun touchdown(planet: Planet, surfaceAngle: Float, gap: Float) {
        val attitude = abs(normalizeAngle(ship.angle - surfaceAngle))
        if (attitude < PIf / 4f) {
            val site = Landing(ship, planet, surfaceAngle, namer.describeActivity(rng, planet))
            ship.landing = site
            ship.velocity = planet.velocity
            add(site)

            if (!planet.explored) {
                planet.explored = true
                planet.flagAngle = surfaceAngle
                latestDiscovery = planet
            }
        } else {
            val impact = planet.pos + vecFromAngle(surfaceAngle, planet.radius)
            ship.pos = planet.pos + vecFromAngle(surfaceAngle, planet.radius + ship.radius - gap)
            repeat(10) {
                add(
                    Spark(
                        ttl = rng.nextFloat() * 1.5f + 0.5f,
                        style = Spark.Style.Dot,
                        color = Color.White,
                        size = 1f,
                    ).also { spark ->
                        spark.pos = impact
                        spark.previousPos = impact
                        spark.velocity = ship.velocity * 0.8f +
                            vecFromAngle(rng.nextFloat() * PI2f, rng.nextFloat() * 0.4f + 0.1f)
                    }
                )
            }
        }
    }
}

private fun kotlin.random.Random.nextFloatIn(range: ClosedFloatingPointRange<Float>): Float =
    range.start + nextFloat() * (range.endInclusive - range.start)

fun Planet.rangeForGravity(force: Float): Float =
    sqrt(SPACECRAFT_MASS * GRAVITATION * mass / force)
