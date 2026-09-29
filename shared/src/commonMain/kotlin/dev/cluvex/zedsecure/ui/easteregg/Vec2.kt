package dev.cluvex.zedsecure.ui.easteregg

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

typealias Vec2 = Offset

fun Vec2(x: Float, y: Float): Vec2 = Offset(x, y)

const val PIf = PI.toFloat()
const val PI2f = (2 * PI).toFloat()

fun Vec2.mag(): Float = getDistance()

fun Vec2.angle(): Float = atan2(y, x)

fun Vec2.dot(other: Vec2): Float = x * other.x + y * other.y

fun Vec2.distanceTo(other: Vec2): Float = (this - other).mag()

fun vecFromAngle(angle: Float, mag: Float): Vec2 = Vec2(mag * cos(angle), mag * sin(angle))

fun normalizeAngle(a: Float): Float {
    var x = a % PI2f
    if (x > PIf) x -= PI2f
    if (x < -PIf) x += PI2f
    return x
}

fun expSmooth(current: Float, target: Float, dt: Float, speed: Float = 5f): Float =
    current + (target - current) * (1f - kotlin.math.exp(-dt * speed))

fun unlerp(start: Float, end: Float, value: Float): Float = (value - start) / (end - start)
