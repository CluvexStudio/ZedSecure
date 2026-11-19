package dev.cluvex.zedsecure.ui.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.isRenderEffectSupported

private const val ZX = 98.5f
private const val ZY = 111f
private const val ZW = 315f
private const val ZH = 290f

private val ZedPath: Path by lazy { PathParser().parsePathString(GlassIcons.ZedMark).toPath() }

private val ZedLength: Float by lazy {
    val measure = PathMeasure()
    measure.setPath(ZedPath, false)
    measure.length
}

val GlassZShape = GenericShape { size, _ ->
    val sx = size.width / ZW
    val sy = size.height / ZH
    val src = ZedPath
    addPath(src, Offset(-ZX, -ZY))
    transform(scaleMatrix(sx, sy))
}

private fun scaleMatrix(sx: Float, sy: Float): androidx.compose.ui.graphics.Matrix =
    androidx.compose.ui.graphics.Matrix().apply { scale(sx, sy, 1f) }

enum class GlassZPhase { Off, Busy, On }

@Immutable
private data class ZLook(
    val a: Color,
    val b: Color,
    val hi: Float,
    val lo: Float,
    val shade: Float,
    val spec: Float,
    val rim: Color,
    val shadow: Color,
    val caust: Float,
    val shine: Float,
    val glowA: Color,
    val glowB: Color,
    val glowC: Color,
    val glow: Float,
)

private fun zLook(phase: GlassZPhase, dark: Boolean, tint: Color): ZLook = when (phase) {
    GlassZPhase.On -> ZLook(
        a = if (dark) Color(196, 140, 255).copy(alpha = 0.55f) else Color(190, 135, 255).copy(alpha = 0.8f),
        b = if (dark) Color(255, 100, 180).copy(alpha = 0.24f) else Color(255, 105, 180).copy(alpha = 0.52f),
        hi = 1f,
        lo = 0.6f,
        shade = if (dark) 0.1f else 0.16f,
        spec = 0.6f,
        rim = if (dark) Color.White.copy(alpha = 0.78f) else Color(120, 60, 180).copy(alpha = 0.42f),
        shadow = if (dark) Color(180, 80, 230).copy(alpha = 0.26f) else Color(170, 70, 220).copy(alpha = 0.32f),
        caust = 0.18f,
        shine = 0.85f,
        glowA = Color(0xFFB06CFF),
        glowB = Color(0xFFE05BE6),
        glowC = Color(0xFFFF6FB5),
        glow = 1f,
    )
    GlassZPhase.Busy -> ZLook(
        a = tint.copy(alpha = if (dark) 0.36f else 0.52f),
        b = tint.copy(alpha = if (dark) 0.12f else 0.22f),
        hi = 0.95f,
        lo = 0.6f,
        shade = if (dark) 0.12f else 0.18f,
        spec = 0.5f,
        rim = if (dark) Color.White.copy(alpha = 0.65f) else tint.copy(alpha = 0.5f),
        shadow = tint.copy(alpha = 0.22f),
        caust = 0.28f,
        shine = 0.75f,
        glowA = tint.copy(alpha = 0.75f),
        glowB = tint.copy(alpha = 0.75f),
        glowC = tint.copy(alpha = 0.75f),
        glow = 0.7f,
    )
    GlassZPhase.Off -> if (dark) {
        ZLook(
            a = Color.White.copy(alpha = 0.26f),
            b = Color.White.copy(alpha = 0.07f),
            hi = 0.85f,
            lo = 0.35f,
            shade = 0.35f,
            spec = 0.45f,
            rim = Color.White.copy(alpha = 0.5f),
            shadow = Color.Black.copy(alpha = 0.55f),
            caust = 0.16f,
            shine = 0.6f,
            glowA = Color.Transparent,
            glowB = Color.Transparent,
            glowC = Color.Transparent,
            glow = 0f,
        )
    } else {
        ZLook(
            a = Color.White.copy(alpha = 0.84f),
            b = Color(158, 172, 200).copy(alpha = 0.52f),
            hi = 1f,
            lo = 0.85f,
            shade = 0.22f,
            spec = 0.72f,
            rim = Color(40, 55, 85).copy(alpha = 0.32f),
            shadow = Color(30, 40, 80).copy(alpha = 0.2f),
            caust = 0.42f,
            shine = 0.95f,
            glowA = Color.Transparent,
            glowB = Color.Transparent,
            glowC = Color.Transparent,
            glow = 0f,
        )
    }
}

private val ShineEasing = CubicBezierEasing(0.45f, 0f, 0.2f, 1f)

private val CaustStops = arrayOf(
    0f to Color(0xFF00C3FF),
    0.25f to Color(0xFF9A7CFF),
    0.5f to Color(0xFFC7F24E),
    0.75f to Color(0xFFFF8DC7),
    1f to Color(0xFF00C3FF),
)

@Composable
fun GlassZControl(
    phase: GlassZPhase,
    onClick: () -> Unit,
    label: String,
    animate: Boolean,
    modifier: Modifier = Modifier,
    width: Dp = 176.dp,
) {
    val c = Glass.colors
    val look = zLook(phase, c.dark, c.tint)
    val spec = tween<Color>(450)
    val bodyA by animateColorAsState(look.a, spec, label = "z-a")
    val bodyB by animateColorAsState(look.b, spec, label = "z-b")
    val rim by animateColorAsState(look.rim, spec, label = "z-rim")
    val shadowColor by animateColorAsState(look.shadow, spec, label = "z-shadow")
    val glowA by animateColorAsState(look.glowA, spec, label = "z-ga")
    val glowB by animateColorAsState(look.glowB, spec, label = "z-gb")
    val glowC by animateColorAsState(look.glowC, spec, label = "z-gc")
    val fspec = tween<Float>(450)
    val hi by animateFloatAsState(look.hi, fspec, label = "z-hi")
    val lo by animateFloatAsState(look.lo, fspec, label = "z-lo")
    val shade by animateFloatAsState(look.shade, fspec, label = "z-shade")
    val specular by animateFloatAsState(look.spec, fspec, label = "z-spec")
    val caust by animateFloatAsState(look.caust, fspec, label = "z-caust")
    val shine by animateFloatAsState(look.shine, fspec, label = "z-shine")
    val glow by animateFloatAsState(look.glow, tween(500), label = "z-glow")
    val busy = phase == GlassZPhase.Busy
    val busyAlpha by animateFloatAsState(if (busy) 1f else 0f, tween(300), label = "z-busy")

    val motion = rememberZMotion(animate, busy)

    val height = width * (162f / 176f)
    val source = remember { MutableInteractionSource() }
    val blurOk = isRenderEffectSupported()
    Box(
        modifier
            .size(width + 44.dp, height + 48.dp)
            .glassPress(source, 1.06f)
            .semantics { contentDescription = label }
            .clickable(source, null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (blurOk) {
            Canvas(
                Modifier
                    .size(width + 44.dp, height + 48.dp)
                    .graphicsLayer {
                        renderEffect = BlurEffect(22.dp.toPx(), 22.dp.toPx(), TileMode.Decal)
                    },
            ) {
                val inset = Offset(22.dp.toPx(), 24.dp.toPx())
                val zSize = Size(size.width - inset.x * 2, size.height - inset.y * 2)
                inZ(inset + Offset(0f, 14.dp.toPx()), zSize) {
                    drawPath(ZedPath, shadowColor)
                }
                val g = glow * motion.breathe.value
                if (g > 0.001f) {
                    inZ(inset, zSize) {
                        drawPath(
                            ZedPath,
                            Brush.linearGradient(
                                0f to glowA,
                                0.5f to glowB,
                                1f to glowC,
                                start = Offset(ZX, ZY),
                                end = Offset(ZX + ZW, ZY + ZH),
                            ),
                            alpha = g,
                        )
                    }
                }
            }
        }
        Canvas(
            Modifier
                .size(width, height)
                .then(
                    if (blurOk) Modifier
                    else Modifier.shadow(14.dp, GlassZShape, clip = false, ambientColor = shadowColor, spotColor = shadowColor),
                ),
        ) {
            inZ(Offset.Zero, size) {
                drawPath(
                    ZedPath,
                    Brush.linearGradient(listOf(bodyA, bodyB), start = Offset(ZX, ZY), end = Offset(ZX + ZW, ZY + ZH)),
                )
                clipPath(ZedPath) {
                    val cx = -531.5f + motion.caust.value
                    drawRect(
                        Brush.horizontalGradient(*CaustStops, startX = cx, endX = cx + 1260f),
                        topLeft = Offset(ZX, ZY),
                        size = Size(ZW, ZH),
                        alpha = caust,
                    )
                    val sx = motion.shine.value
                    if (sx > -379f && sx < 379f) {
                        drawRect(
                            Brush.linearGradient(
                                0f to Color.Transparent,
                                0.4f to Color.Transparent,
                                0.5f to Color.White.copy(alpha = shine),
                                0.6f to Color.Transparent,
                                1f to Color.Transparent,
                                start = Offset(ZX + sx, ZY),
                                end = Offset(ZX + ZW + sx, ZY + 0.4f * ZH),
                            ),
                            topLeft = Offset(ZX, ZY),
                            size = Size(ZW, ZH),
                        )
                    }
                    if (busyAlpha > 0.001f) {
                        val sy = ZY + motion.scan.value
                        drawRect(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.5f to Color.White.copy(alpha = 0.7f),
                                1f to Color.Transparent,
                                startY = sy,
                                endY = sy + 64f,
                            ),
                            topLeft = Offset(ZX, sy),
                            size = Size(ZW, 64f),
                            alpha = busyAlpha,
                        )
                    }
                    drawRect(
                        Brush.radialGradient(
                            0f to Color.White.copy(alpha = specular),
                            1f to Color.White.copy(alpha = 0f),
                            center = Offset(ZX + 0.28f * ZW, ZY + 0.08f * ZH),
                            radius = 0.6f * (ZW + ZH) / 2f,
                        ),
                        topLeft = Offset(ZX, ZY),
                        size = Size(ZW, ZH),
                    )
                    drawPath(
                        ZedPath,
                        Brush.linearGradient(
                            0.45f to Color.Black.copy(alpha = 0f),
                            1f to Color.Black.copy(alpha = shade),
                            start = Offset(ZX, ZY),
                            end = Offset(ZX + ZW, ZY + ZH),
                        ),
                        style = Stroke(width = 18f, join = StrokeJoin.Round),
                    )
                    drawPath(
                        ZedPath,
                        Brush.linearGradient(
                            0f to Color.White.copy(alpha = hi),
                            0.42f to Color.White.copy(alpha = 0f),
                            0.72f to Color.White.copy(alpha = 0f),
                            1f to Color.White.copy(alpha = lo),
                            start = Offset(ZX, ZY),
                            end = Offset(ZX + ZW, ZY + ZH),
                        ),
                        style = Stroke(width = 12f, join = StrokeJoin.Round),
                    )
                }
                val unit = ZW / size.width
                drawPath(ZedPath, rim, style = Stroke(width = 1.3.dp.toPx() * unit, join = StrokeJoin.Round))
                if (busyAlpha > 0.001f) {
                    val length = ZedLength
                    drawPath(
                        ZedPath,
                        Brush.linearGradient(listOf(Color.White, c.tint), start = Offset(ZX, ZY), end = Offset(ZX + ZW, ZY + ZH)),
                        alpha = busyAlpha,
                        style = Stroke(
                            width = 6f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(length * 0.19f, length * 0.81f),
                                length * (1f - motion.trace.value),
                            ),
                        ),
                    )
                }
            }
        }
    }
}

private inline fun DrawScope.inZ(origin: Offset, target: Size, block: DrawScope.() -> Unit) {
    val sx = target.width / ZW
    val sy = target.height / ZH
    withTransform({
        translate(origin.x, origin.y)
        scale(sx, sy, Offset.Zero)
        translate(-ZX, -ZY)
    }, block)
}

private class ZMotion(
    val caust: State<Float>,
    val shine: State<Float>,
    val scan: State<Float>,
    val trace: State<Float>,
    val breathe: State<Float>,
)

@Composable
private fun rememberZMotion(animate: Boolean, busy: Boolean): ZMotion {
    if (!animate) {
        return remember {
            ZMotion(
                caust = mutableFloatStateOf(157f),
                shine = mutableFloatStateOf(-400f),
                scan = mutableFloatStateOf(113f),
                trace = mutableFloatStateOf(0f),
                breathe = mutableFloatStateOf(0.75f),
            )
        }
    }
    val transition = rememberInfiniteTransition(label = "glass-z")
    val caust = transition.animateFloat(
        0f,
        315f,
        infiniteRepeatable(tween(7000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "z-caustics",
    )
    val slow = transition.animateFloat(0f, 1f, infiniteRepeatable(tween(4400, easing = LinearEasing)), label = "z-shine-slow")
    val fast = transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1700, easing = LinearEasing)), label = "z-shine-fast")
    val scan = transition.animateFloat(
        -20f,
        250f,
        infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "z-scan",
    )
    val trace = transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "z-trace")
    val breathe = transition.animateFloat(
        0.45f,
        0.9f,
        infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "z-breathe",
    )
    val shine = remember(busy) {
        androidx.compose.runtime.derivedStateOf {
            val t = (if (busy) fast else slow).value
            if (t < 0.55f) -380f + 760f * ShineEasing.transform(t / 0.55f) else 380f
        }
    }
    return remember(caust, shine, scan, trace, breathe) { ZMotion(caust, shine, scan, trace, breathe) }
}

@Composable
fun GlassZMark(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = minOf(size.width / ZW, size.height / ZH)
        val dx = (size.width - ZW * s) / 2f
        val dy = (size.height - ZH * s) / 2f
        withTransform({
            translate(dx, dy)
            scale(s, s, Offset.Zero)
            translate(-ZX, -ZY)
        }) {
            drawPath(ZedPath, color)
        }
    }
}
