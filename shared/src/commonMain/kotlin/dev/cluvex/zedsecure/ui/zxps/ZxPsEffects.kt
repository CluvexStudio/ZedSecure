package dev.cluvex.zedsecure.ui.zxps

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Slow 0..1 loop that drives the aurora; frozen at 0 when [enabled] is false. */
@Composable
internal fun rememberZxPsPhase(periodMs: Int, enabled: Boolean): State<Float> {
    val still = remember { mutableStateOf(0f) }
    if (!enabled) return still
    val transition = rememberInfiniteTransition(label = "zxps-phase")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart),
        label = "zxps-phase-value",
    )
}

/** Three drifting colour blobs behind everything; this is what the glass surfaces pick up. */
@Composable
internal fun ZxPsBackdrop(animate: Boolean, intensity: Float = 1f, modifier: Modifier = Modifier) {
    val c = ZxPs.colors
    val phase = rememberZxPsPhase(26_000, animate)
    Canvas(modifier.fillMaxSize().background(c.ground)) {
        val p = phase.value * 2f * PI.toFloat()
        val w = size.width
        val h = size.height
        val base = if (c.dark) 0.34f else 0.30f
        fun blob(color: Color, cx: Float, cy: Float, r: Float) {
            drawCircle(
                Brush.radialGradient(
                    listOf(color.copy(alpha = base * intensity), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = r,
                ),
                radius = r,
                center = Offset(cx, cy),
            )
        }
        blob(c.a, w * (0.25f + 0.12f * cos(p)), h * (0.18f + 0.06f * sin(p)), w * 0.95f)
        blob(c.b, w * (0.85f + 0.08f * sin(p + 2f)), h * (0.45f + 0.10f * cos(p + 1f)), w * 0.85f)
        blob(c.c, w * (0.30f + 0.15f * sin(p + 4f)), h * (0.88f + 0.05f * cos(p + 3f)), w * 0.9f)
    }
}

/** Glass fill: translucent gradient, hairline rim and an optional top sheen. */
internal fun Modifier.zxPsGlass(
    shape: Shape,
    glass: Int,
    sheen: Boolean,
    dark: Boolean,
    tint: Color = Color.Unspecified,
    strong: Boolean = false,
): Modifier {
    val clamped = glass.coerceIn(0, 100) / 100f
    val alpha = ((if (strong) 0.94f else 0.86f) - 0.5f * clamped).coerceIn(0.32f, 0.96f)
    val base = if (dark) Color(0xFF12121C) else Color.White
    val top = if (tint.isSpecified) androidx.compose.ui.graphics.lerp(base, tint, 0.16f) else base
    val rimHi = if (dark) Color.White.copy(alpha = 0.34f) else Color.White.copy(alpha = 0.95f)
    val rimLo = if (dark) Color.White.copy(alpha = 0.06f) else Color(0xFF8A8AA8).copy(alpha = 0.18f)
    return this
        .clip(shape)
        .background(Brush.verticalGradient(listOf(top.copy(alpha = alpha), base.copy(alpha = alpha * 0.82f))))
        .border(1.dp, Brush.verticalGradient(listOf(rimHi, rimLo)), shape)
        .drawWithContent {
            drawContent()
            if (sheen) {
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = if (dark) 0.10f else 0.45f), Color.Transparent),
                        endY = size.height * 0.45f,
                    ),
                )
            }
        }
}

private val Color.isSpecified get() = this != Color.Unspecified

@Composable
internal fun ZxPsCard(
    shape: Shape,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    strong: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val p = ZxPs.prefs
    val c = ZxPs.colors
    Box(
        modifier.zxPsGlass(shape, p.glass, p.sheen, c.dark, tint, strong).padding(contentPadding),
        content = content,
    )
}
