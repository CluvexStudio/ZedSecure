package dev.cluvex.zedsecure.ui.easteregg

import kotlin.random.Random

interface Entity {
    fun update(sim: Simulator, dt: Float)
    fun postUpdate(sim: Simulator, dt: Float) {}
}

interface Constraint {
    fun solve(sim: Simulator, dt: Float)
}

interface Removable {
    val expired: Boolean
}

class Fuse(private val lifetime: Float) : Removable {
    var remaining: Float = lifetime
        private set

    val burnt: Float get() = (1f - remaining / lifetime).coerceIn(0f, 1f)

    override val expired: Boolean get() = remaining < 0f

    fun burn(dt: Float) {
        remaining -= dt
    }
}

open class Body(var name: String = "") : Entity {
    var pos: Vec2 = Vec2.Zero

    var previousPos: Vec2 = Vec2.Zero
    var velocity: Vec2 = Vec2.Zero
    var mass = 0f
    var radius = 0f

    var angle = 0f
    var collides = true

    override fun update(sim: Simulator, dt: Float) {
        if (dt <= 0f) return
        previousPos = pos
        pos += velocity * dt
    }

    override fun postUpdate(sim: Simulator, dt: Float) {
        if (dt <= 0f) return
        velocity = (pos - previousPos) / dt
    }
}

class Ringfence(private val radius: Float, private val body: Body) : Constraint {
    override fun solve(sim: Simulator, dt: Float) {
        if (body.pos.mag() + body.radius > radius) {
            body.pos = vecFromAngle(body.pos.angle(), radius - body.radius)
        }
    }
}

open class Simulator(val seed: Long) {
    val rng = Random(seed)

    var now = 0f
        private set

    var dt = 0f
        private set

    val entities = mutableListOf<Entity>()
    val constraints = mutableListOf<Constraint>()

    private var lastFrameNanos = 0L

    fun add(entity: Entity) {
        if (entity !in entities) entities += entity
    }

    fun remove(entity: Entity) {
        entities -= entity
    }

    fun add(constraint: Constraint) {
        if (constraint !in constraints) constraints += constraint
    }

    fun remove(constraint: Constraint) {
        constraints -= constraint
    }

    protected open fun updateAll(dt: Float, snapshot: List<Entity>) {
        snapshot.forEach { it.update(this, dt) }
    }

    protected open fun solveAll(dt: Float, snapshot: List<Constraint>) {
        snapshot.forEach { it.solve(this, dt) }
    }

    protected open fun postUpdateAll(dt: Float, snapshot: List<Entity>) {
        snapshot.forEach { it.postUpdate(this, dt) }
    }

    fun step(nanos: Long) {
        val firstFrame = lastFrameNanos == 0L
        dt = (nanos - lastFrameNanos) / 1_000_000_000f
        lastFrameNanos = nanos
        if (firstFrame || dt > MAX_STEP_SECONDS || dt <= 0f) return

        now += dt

        val entitySnapshot = entities.toList()
        val constraintSnapshot = constraints.toList()

        updateAll(dt, entitySnapshot)
        solveAll(dt, constraintSnapshot)
        postUpdateAll(dt, entitySnapshot)

        entities.removeAll { it is Removable && it.expired }
        constraints.removeAll { it is Removable && it.expired }
    }

    private companion object {
        const val MAX_STEP_SECONDS = 1f
    }
}
