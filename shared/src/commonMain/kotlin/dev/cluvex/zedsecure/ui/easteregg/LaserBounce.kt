package dev.cluvex.zedsecure.ui.easteregg

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.min

@Composable
fun LaserBounce(
    trigger: Int,
    color: Color,
    reduceMotion: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (trigger <= 0) return

    val flight = remember(trigger) { LaserFlight.random(trigger) }
    val progress = remember(trigger) { Animatable(0f) }

    val duration = if (reduceMotion) STILL_DURATION_MS else flight.durationMs
    LaunchedEffect(trigger) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = duration, easing = LinearEasing))
    }

    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        val points = remember(flight, w, h) { flight.points(w, h) }

        Canvas(Modifier.fillMaxSize()) {
            val t = progress.value
            if (reduceMotion) {
                val alpha = if (t < 0.2f) t / 0.2f else ((1f - t) / 0.8f).coerceIn(0f, 1f)
                if (alpha <= 0f) return@Canvas
                drawLaser(points, 1f, color, alpha, tailFraction = 1f)
            } else {
                val alpha = if (t > 0.86f) ((1f - t) / 0.14f).coerceIn(0f, 1f) else 1f
                if (alpha <= 0f) return@Canvas
                drawLaser(points, t, color, alpha)
            }
        }
    }
}

data class LaserFlight(
    val startX: Float,
    val bounces: Int,
    val startFromLeft: Boolean,
    val durationMs: Int,
) {
    companion object
}

fun LaserFlight.Companion.random(seed: Int): LaserFlight {
    val r = kotlin.random.Random(seed * 7919)
    return LaserFlight(
        startX = 0.30f + r.nextFloat() * 0.40f,
        bounces = 4 + r.nextInt(3),
        startFromLeft = r.nextBoolean(),
        durationMs = 1500 + r.nextInt(500),
    )
}

fun LaserFlight.points(w: Float, h: Float): List<Offset> {
    val inset = min(w, h) * 0.012f
    val left = inset
    val right = w - inset
    val pts = ArrayList<Offset>(bounces + 2)

    var x = startX * w
    var y = h
    pts.add(Offset(x, y))

    val legRise = h / (bounces + 0.6f)
    var goingLeft = startFromLeft
    repeat(bounces) {
        val targetX = if (goingLeft) left else right
        y -= legRise
        pts.add(Offset(targetX, y))
        x = targetX
        goingLeft = !goingLeft
    }

    val lastX = if (goingLeft) left else right
    pts.add(Offset(lastX, -h * 0.08f))
    return pts
}

private fun DrawScope.drawLaser(
    points: List<Offset>,
    t: Float,
    color: Color,
    alpha: Float,

    tailFraction: Float = TAIL_FRACTION,
) {
    if (points.size < 2) return

    val legLengths = FloatArray(points.size - 1)
    var total = 0f
    for (i in 0 until points.size - 1) {
        val d = (points[i + 1] - points[i]).getDistance()
        legLengths[i] = d
        total += d
    }
    if (total <= 0f) return

    val headDist = t * total

    val tailDist = (headDist - total * tailFraction).coerceAtLeast(0f)

    val core = min(size.width, size.height) * 0.006f
    var walked = 0f

    for (i in 0 until points.size - 1) {
        val legStart = walked
        val legEnd = walked + legLengths[i]
        walked = legEnd
        if (legEnd < tailDist || legStart > headDist) continue

        val a = points[i]
        val b = points[i + 1]
        val fromT = ((tailDist - legStart) / legLengths[i]).coerceIn(0f, 1f)
        val toT = ((headDist - legStart) / legLengths[i]).coerceIn(0f, 1f)
        val p1 = lerp(a, b, fromT)
        val p2 = lerp(a, b, toT)

        drawLine(color.copy(alpha = 0.14f * alpha), p1, p2, core * 6f, StrokeCap.Round, blendMode = BlendMode.Plus)
        drawLine(color.copy(alpha = 0.34f * alpha), p1, p2, core * 2.6f, StrokeCap.Round, blendMode = BlendMode.Plus)
        drawLine(Color.White.copy(alpha = 0.92f * alpha), p1, p2, core, StrokeCap.Round, blendMode = BlendMode.Plus)
    }

    var vertexDist = 0f
    for (i in 1 until points.size - 1) {
        vertexDist += legLengths[i - 1]
        val since = headDist - vertexDist
        if (since < 0f || since > total * FLASH_FRACTION) continue
        val k = 1f - (since / (total * FLASH_FRACTION))
        val r = core * (5f + 16f * k)
        drawCircle(color.copy(alpha = 0.30f * k * alpha), r, points[i], blendMode = BlendMode.Plus)
        drawCircle(Color.White.copy(alpha = 0.55f * k * alpha), r * 0.35f, points[i], blendMode = BlendMode.Plus)
    }

    if (t < 1f) {
        val head = pointAt(points, legLengths, headDist)
        drawCircle(color.copy(alpha = 0.40f * alpha), core * 9f, head, blendMode = BlendMode.Plus)
        drawCircle(Color.White.copy(alpha = 0.95f * alpha), core * 1.7f, head, blendMode = BlendMode.Plus)
    }
}

private fun pointAt(points: List<Offset>, legLengths: FloatArray, dist: Float): Offset {
    var walked = 0f
    for (i in legLengths.indices) {
        if (dist <= walked + legLengths[i]) {
            val f = if (legLengths[i] <= 0f) 0f else (dist - walked) / legLengths[i]
            return lerp(points[i], points[i + 1], f)
        }
        walked += legLengths[i]
    }
    return points.last()
}

private fun lerp(a: Offset, b: Offset, t: Float) = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

private const val STILL_DURATION_MS = 900

private const val TAIL_FRACTION = 0.34f

private const val FLASH_FRACTION = 0.12f
