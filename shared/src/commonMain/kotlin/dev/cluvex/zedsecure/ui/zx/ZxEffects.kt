package dev.cluvex.zedsecure.ui.zx

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cluvex.zedsecure.domain.model.ConnectionState

@Composable
internal fun zxCaption(): TextStyle =
    MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)

@Composable
internal fun zxNumber(size: TextUnit, weight: FontWeight = FontWeight.Bold): TextStyle =
    TextStyle(fontFamily = FontFamily.Monospace, fontWeight = weight, fontSize = size)

/**
 * A looping 0..1 value. Returns a State so the value is read inside draw lambdas only and never
 * triggers recomposition. When disabled it stays at zero and no transition is created.
 */
@Composable
internal fun rememberZxPhase(periodMs: Int, enabled: Boolean): State<Float> {
    val still = remember { mutableStateOf(0f) }
    if (!enabled) return still
    val transition = rememberInfiniteTransition(label = "zx-phase")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing)),
        label = "zx-phase-value",
    )
}

/** Animated cyberpunk backdrop: glows, a scrolling perspective grid, scanlines and digital rain. */
@Composable
internal fun ZxBackdrop(fx: ZxFx, intensity: Float, modifier: Modifier = Modifier) {
    val c = Zx.colors
    val animated = fx != ZxFx.Min
    val gridPhase = rememberZxPhase(5200, animated)
    val rainPhase = rememberZxPhase(9000, fx == ZxFx.Max)
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val horizon = h * 0.40f
        val glowA = if (c.dark) 0.24f else 0.12f

        drawRect(
            Brush.radialGradient(
                listOf(c.neonB.copy(alpha = glowA * (0.5f + 0.5f * intensity)), Color.Transparent),
                center = Offset(w * 0.12f, h * 0.02f),
                radius = w * 0.95f,
            ),
        )
        drawRect(
            Brush.radialGradient(
                listOf(c.neonA.copy(alpha = glowA * 0.8f * (0.4f + 0.6f * intensity)), Color.Transparent),
                center = Offset(w * 0.98f, h * 0.30f),
                radius = w * 0.85f,
            ),
        )

        val gridAlpha = (if (c.dark) 0.22f else 0.16f) * (0.55f + 0.45f * intensity)
        val gridStroke = 1.dp.toPx()
        val vx = w / 2f
        val spread = w / 9f
        for (i in -14..14) {
            drawLine(
                color = c.grid.copy(alpha = gridAlpha * 0.8f),
                start = Offset(vx + i * spread * 0.07f, horizon),
                end = Offset(vx + i * spread, h),
                strokeWidth = gridStroke,
            )
        }
        val rows = 14
        val p = gridPhase.value
        for (k in 0 until rows) {
            val t = (k + p) / rows
            val y = horizon + (h - horizon) * t * t
            drawLine(
                color = c.grid.copy(alpha = gridAlpha * (0.25f + 0.75f * t)),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = gridStroke,
            )
        }

        if (fx != ZxFx.Min) {
            val step = 5.dp.toPx()
            val lineColor = c.ink.copy(alpha = if (c.dark) 0.035f else 0.03f)
            var y = 0f
            while (y < h) {
                drawLine(lineColor, Offset(0f, y), Offset(w, y), 1f)
                y += step
            }
        }

        if (fx == ZxFx.Max) {
            val streaks = 20
            val rp = rainPhase.value
            for (i in 0 until streaks) {
                val seed = ((i * 7919) % 97) / 97f
                val speed = 0.6f + ((i * 31) % 17) / 17f * 0.9f
                val len = (60 + ((i * 13) % 11) * 10).dp.toPx()
                val x = ((i * 53) % 101) / 101f * w
                val travel = h + len
                val y = (((rp * speed) + seed) % 1f) * travel - len
                val color = (if (i % 2 == 0) c.neonA else c.neonB).copy(alpha = if (c.dark) 0.55f else 0.35f)
                drawLine(
                    brush = Brush.verticalGradient(listOf(Color.Transparent, color), startY = y, endY = y + len),
                    start = Offset(x, y),
                    end = Offset(x, y + len),
                    strokeWidth = 1.5.dp.toPx(),
                )
            }
        }
    }
}

private val PanelShape = CutCornerShape(topStart = 0.dp, topEnd = 14.dp, bottomEnd = 0.dp, bottomStart = 14.dp)

/** A HUD style panel: cut corners, hairline neon border and corner brackets. */
@Composable
internal fun ZxPanel(
    modifier: Modifier = Modifier,
    accent: Color = Zx.colors.line,
    glow: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Zx.colors
    val border by animateColorAsState(accent.copy(alpha = if (glow) 0.95f else 0.55f), tween(350), label = "zx-panel-border")
    Column(
        modifier
            .clip(PanelShape)
            .background(c.surface.copy(alpha = 0.88f))
            .border(1.dp, border, PanelShape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .drawWithContent {
                drawContent()
                val arm = 12.dp.toPx()
                val sw = 2.dp.toPx()
                drawLine(border, Offset(0f, 0f), Offset(arm, 0f), sw)
                drawLine(border, Offset(0f, 0f), Offset(0f, arm), sw)
                drawLine(border, Offset(size.width, size.height), Offset(size.width - arm, size.height), sw)
                drawLine(border, Offset(size.width, size.height), Offset(size.width, size.height - arm), sw)
            }
            .padding(contentPadding),
        content = content,
    )
}

/** A small oscilloscope line, with a soft glow underneath when [glow] is on. */
@Composable
internal fun ZxScope(values: List<Float>, color: Color, glow: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (values.size < 2) {
            drawLine(color.copy(alpha = 0.3f), Offset(0f, h / 2f), Offset(w, h / 2f), 1.dp.toPx())
            return@Canvas
        }
        val max = maxOf(values.maxOrNull() ?: 1f, 1f)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = w * i / (values.size - 1)
            val y = h - (v / max) * (h - 3.dp.toPx()) - 1.5.dp.toPx()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        if (glow) {
            drawPath(path, color.copy(alpha = 0.25f), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        drawPath(path, color, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
internal fun zxStateTone(state: ConnectionState): Color {
    val c = Zx.colors
    return when {
        state == ConnectionState.Error -> c.danger
        state.isTransitioning -> c.neonC
        state == ConnectionState.Connected -> c.neonA
        else -> c.muted
    }
}

/** The connection chain drawn as a circuit: device, engine, internet, with current flowing through it. */
@Composable
internal fun ZxChain(
    state: ConnectionState,
    deviceLabel: String,
    engine: String,
    netLabel: String,
    fx: ZxFx,
    modifier: Modifier = Modifier,
) {
    val tone = zxStateTone(state)
    val flow = rememberZxPhase(1700, fx != ZxFx.Min && state != ConnectionState.Idle && state != ConnectionState.Error)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ZxNode(deviceLabel, tone)
        ZxLink(state, tone, flow, 0f, Modifier.weight(1f))
        ZxNode(engine, tone, Modifier.widthIn(max = 116.dp))
        ZxLink(state, tone, flow, 0.5f, Modifier.weight(1f))
        ZxNode(netLabel, tone)
    }
}

@Composable
private fun ZxNode(text: String, tone: Color, modifier: Modifier = Modifier) {
    val shape = CutCornerShape(topStart = 0.dp, topEnd = 8.dp, bottomEnd = 0.dp, bottomStart = 8.dp)
    val color by animateColorAsState(tone, tween(350), label = "zx-node")
    Box(
        modifier
            .clip(shape)
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.8f), shape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = zxCaption(), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ZxLink(state: ConnectionState, tone: Color, flow: State<Float>, offset: Float, modifier: Modifier) {
    val c = Zx.colors
    val color by animateColorAsState(tone, tween(350), label = "zx-link")
    Canvas(modifier.height(24.dp)) {
        val y = size.height / 2f
        val w = size.width
        val sw = 2.dp.toPx()
        when {
            state == ConnectionState.Error -> {
                val gap = 10.dp.toPx()
                drawLine(color.copy(alpha = 0.7f), Offset(0f, y), Offset(w / 2f - gap, y), sw)
                drawLine(color.copy(alpha = 0.7f), Offset(w / 2f + gap, y), Offset(w, y), sw)
                val x = w / 2f
                val d = 4.dp.toPx()
                drawLine(color, Offset(x - d, y - d), Offset(x + d, y + d), sw)
                drawLine(color, Offset(x - d, y + d), Offset(x + d, y - d), sw)
            }
            state == ConnectionState.Idle || state == ConnectionState.Disconnecting -> {
                drawLine(
                    c.line,
                    Offset(0f, y),
                    Offset(w, y),
                    sw,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())),
                )
            }
            else -> {
                drawLine(color.copy(alpha = 0.35f), Offset(0f, y), Offset(w, y), sw)
                val p = (flow.value + offset) % 1f
                val dots = 3
                for (i in 0 until dots) {
                    val t = (p + i / dots.toFloat()) % 1f
                    val x = w * t
                    val a = 1f - kotlin.math.abs(t - 0.5f) * 1.2f
                    drawCircle(color.copy(alpha = 0.25f * a), 7.dp.toPx(), Offset(x, y))
                    drawCircle(color.copy(alpha = a.coerceIn(0.2f, 1f)), 3.dp.toPx(), Offset(x, y))
                }
            }
        }
    }
}

/** The reactor style connect button. */
@Composable
internal fun ZxCore(
    state: ConnectionState,
    fx: ZxFx,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 236.dp,
) {
    val c = Zx.colors
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        if (pressed) 0.92f else 1f,
        spring(dampingRatio = 0.45f, stiffness = 500f),
        label = "zx-core-press",
    )
    val live = state == ConnectionState.Connected
    val busy = state.isTransitioning
    val toneA by animateColorAsState(zxStateTone(state), tween(450), label = "zx-core-a")
    val toneB by animateColorAsState(
        when {
            state == ConnectionState.Error -> c.danger
            live || busy -> c.neonB
            else -> c.line
        },
        tween(450),
        label = "zx-core-b",
    )
    val spinning = fx != ZxFx.Min
    val slow = rememberZxPhase(11000, spinning)
    val fast = rememberZxPhase(1500, spinning)
    val pulse = rememberZxPhase(2200, fx != ZxFx.Min && live)

    val glitch = remember { Animatable(0f) }
    LaunchedEffect(state, fx) {
        if (fx != ZxFx.Min) {
            glitch.snapTo(1f)
            glitch.animateTo(0f, tween(480))
        }
    }

    Canvas(
        modifier
            .size(diameter)
            .graphicsLayer {
                scaleX = press
                scaleY = press
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggle()
            },
    ) {
        val r = size.minDimension / 2f
        val ctr = center
        val g = glitch.value
        val spin = if (busy) fast.value else slow.value

        drawCircle(
            Brush.radialGradient(
                listOf(toneA.copy(alpha = if (live || busy) 0.34f else 0.12f), Color.Transparent),
                center = ctr,
                radius = r,
            ),
            radius = r,
            center = ctr,
        )

        if (live && fx != ZxFx.Min) {
            val pp = pulse.value
            drawCircle(
                toneA.copy(alpha = (1f - pp) * 0.45f),
                radius = r * (0.70f + 0.30f * pp),
                center = ctr,
                style = Stroke(2.dp.toPx()),
            )
        }

        rotate(spin * 360f, ctr) {
            drawCircle(
                toneA,
                radius = r * 0.95f,
                center = ctr,
                style = Stroke(
                    width = 3.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(16.dp.toPx(), 9.dp.toPx())),
                ),
            )
        }

        val arcR = r * 0.82f
        rotate(-spin * 360f * 1.4f, ctr) {
            for (i in 0 until 3) {
                drawArc(
                    color = toneB,
                    startAngle = i * 120f + 12f,
                    sweepAngle = 72f,
                    useCenter = false,
                    topLeft = Offset(ctr.x - arcR, ctr.y - arcR),
                    size = Size(arcR * 2f, arcR * 2f),
                    style = Stroke(6.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }

        if (g > 0f) {
            val shift = 9.dp.toPx() * g
            translate(left = shift) {
                drawCircle(c.neonB.copy(alpha = 0.7f * g), r * 0.66f, ctr, style = Stroke(2.dp.toPx()))
            }
            translate(left = -shift) {
                drawCircle(c.neonA.copy(alpha = 0.7f * g), r * 0.66f, ctr, style = Stroke(2.dp.toPx()))
            }
        }

        drawCircle(
            Brush.linearGradient(
                listOf(c.surface2, c.surface),
                start = Offset(ctr.x - r, ctr.y - r),
                end = Offset(ctr.x + r, ctr.y + r),
            ),
            radius = r * 0.66f,
            center = ctr,
        )
        drawCircle(toneA, radius = r * 0.66f, center = ctr, style = Stroke(2.dp.toPx()))

        val gr = r * 0.26f
        val gs = 5.dp.toPx()
        drawArc(
            color = toneA,
            startAngle = -60f,
            sweepAngle = 300f,
            useCenter = false,
            topLeft = Offset(ctr.x - gr, ctr.y - gr),
            size = Size(gr * 2f, gr * 2f),
            style = Stroke(gs, cap = StrokeCap.Round),
        )
        drawLine(
            toneA,
            Offset(ctr.x, ctr.y - gr * 1.25f),
            Offset(ctr.x, ctr.y - gr * 0.1f),
            gs,
            StrokeCap.Round,
        )
    }
}
