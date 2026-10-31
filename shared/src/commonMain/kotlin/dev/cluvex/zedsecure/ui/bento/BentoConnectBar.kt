package dev.cluvex.zedsecure.ui.bento

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cluvex.zedsecure.domain.model.BentoConnect
import dev.cluvex.zedsecure.domain.model.ConnectionState
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.action_cancel
import dev.cluvex.zedsecure.shared.resources.action_connect
import dev.cluvex.zedsecure.shared.resources.action_disconnect
import dev.cluvex.zedsecure.shared.resources.bento_slide_off
import dev.cluvex.zedsecure.shared.resources.bento_slide_on
import dev.cluvex.zedsecure.shared.resources.bento_slide_retry
import dev.cluvex.zedsecure.shared.resources.bento_tap_cancel
import dev.cluvex.zedsecure.shared.resources.ic_fill_power
import dev.cluvex.zedsecure.shared.resources.state_connected
import dev.cluvex.zedsecure.shared.resources.state_connecting
import dev.cluvex.zedsecure.shared.resources.state_disconnecting
import dev.cluvex.zedsecure.shared.resources.state_error
import dev.cluvex.zedsecure.shared.resources.state_idle
import dev.cluvex.zedsecure.shared.resources.state_reconnecting
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

private const val THRESHOLD = 0.72f

@Composable
fun BentoConnectControl(
    state: ConnectionState,
    onToggle: () -> Unit,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    when (Bento.prefs.connect) {
        BentoConnect.Slide -> BentoConnectBar(state, onToggle, animate, modifier)
        BentoConnect.Button -> BentoConnectButton(state, onToggle, modifier)
    }
}

@Composable
private fun stateLabel(state: ConnectionState): String = stringResource(
    when (state) {
        ConnectionState.Connected -> Res.string.state_connected
        ConnectionState.Connecting -> Res.string.state_connecting
        ConnectionState.Reconnecting -> Res.string.state_reconnecting
        ConnectionState.Disconnecting -> Res.string.state_disconnecting
        ConnectionState.Error -> Res.string.state_error
        ConnectionState.Idle -> Res.string.state_idle
    },
)

@Composable
private fun actionLabel(state: ConnectionState): String = stringResource(
    when (state) {
        ConnectionState.Connected -> Res.string.action_disconnect
        ConnectionState.Connecting, ConnectionState.Reconnecting -> Res.string.action_cancel
        else -> Res.string.action_connect
    },
)

@Composable
fun BentoConnectBar(
    state: ConnectionState,
    onToggle: () -> Unit,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = Bento.colors
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val busy = state == ConnectionState.Connecting || state == ConnectionState.Reconnecting
    val engaged = busy || state == ConnectionState.Connected
    val locked = state == ConnectionState.Disconnecting
    val rest = if (engaged) 1f else 0f

    var position by remember { mutableFloatStateOf(rest) }
    var dragging by remember { mutableStateOf(false) }
    var settle by remember { mutableStateOf<Job?>(null) }

    fun settleTo(target: Float) {
        settle?.cancel()
        settle = scope.launch {
            animate(position, target, animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) { v, _ ->
                position = v
            }
        }
    }

    LaunchedEffect(rest) { if (!dragging) settleTo(rest) }

    val hint = when {
        busy -> stringResource(Res.string.bento_tap_cancel)
        state == ConnectionState.Connected -> stringResource(Res.string.bento_slide_off)
        state == ConnectionState.Error -> stringResource(Res.string.bento_slide_retry)
        else -> stringResource(Res.string.bento_slide_on)
    }
    val label = stateLabel(state)
    val action = actionLabel(state)
    val dot by animateColorAsState(
        when (state) {
            ConnectionState.Connected -> c.stage
            ConnectionState.Error -> c.bad
            ConnectionState.Connecting, ConnectionState.Reconnecting -> c.stage.copy(alpha = 0.65f)
            else -> c.onLime.copy(alpha = 0.4f)
        },
        label = "connect-dot",
    )

    Column(
        modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(BentoShapes.block())
            .background(c.lime)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                stateDescription = label
                onClick(action) {
                    if (!locked) onToggle()
                    true
                }
            }
            .padding(start = 14.dp, end = 14.dp, top = 9.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                color = c.onLime,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                hint,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                color = c.onLime.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(40.dp)) {
            val density = LocalDensity.current
            val knob = 40.dp
            val range = with(density) { (maxWidth - knob).toPx().coerceAtLeast(1f) }
            val draggableState = rememberDraggableState { delta ->
                if (!busy && !locked) position = (position + delta / range).coerceIn(0f, 1f)
            }
            val phase = if (busy && animate) wavePhase() else null
            val tap = rememberUpdatedState {
                when {
                    locked -> Unit
                    busy -> {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggle()
                    }
                    else -> {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val nudge = if (engaged) rest - 0.14f else rest + 0.14f
                        settle?.cancel()
                        settle = scope.launch {
                            animate(position, nudge, animationSpec = tween(140)) { v, _ -> position = v }
                            animate(position, rest, animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)) { v, _ -> position = v }
                        }
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures(onTap = { tap.value() }) }
                    .draggable(
                        state = draggableState,
                        orientation = Orientation.Horizontal,
                        enabled = !busy && !locked,
                        reverseDirection = rtl,
                        onDragStarted = {
                            settle?.cancel()
                            dragging = true
                        },
                        onDragStopped = {
                            dragging = false
                            val crossed = if (engaged) position < 1f - THRESHOLD else position > THRESHOLD
                            if (crossed) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                settleTo(1f - rest)
                                onToggle()
                            } else {
                                settleTo(rest)
                            }
                        },
                    ),
            ) {
                if (Bento.prefs.wave) {
                    WaveTrack(
                        progress = { position },
                        color = c.onLime,
                        knob = with(density) { knob.toPx() },
                        rtl = rtl,
                        phase = phase,
                        connected = state == ConnectionState.Connected,
                    )
                }
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset { IntOffset((position * range).roundToInt(), 0) }
                        .size(knob)
                        .clip(CircleShape)
                        .background(c.onLime),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(Res.drawable.ic_fill_power), contentDescription = null, tint = c.lime, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun wavePhase(): State<Float> {
    val transition = rememberInfiniteTransition(label = "connect-wave")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "connect-wave-phase",
    )
}

@Composable
private fun WaveTrack(
    progress: () -> Float,
    color: Color,
    knob: Float,
    rtl: Boolean,
    phase: State<Float>?,
    connected: Boolean,
) {
    val path = remember { Path() }
    Canvas(Modifier.fillMaxSize()) {
        val p = progress()
        val width = size.width
        val mid = size.height / 2f
        val amp = 4.5.dp.toPx()
        val length = 32.dp.toPx()
        val stroke = 4.5.dp.toPx()
        val knobCenter = knob / 2f + p * (width - knob)
        fun build(from: Float, to: Float) {
            path.reset()
            var x = from
            path.moveTo(x, mid + amp * sin(2f * PI.toFloat() * x / length))
            while (x < to) {
                x = (x + 2f).coerceAtMost(to)
                path.lineTo(x, mid + amp * sin(2f * PI.toFloat() * x / length))
            }
        }
        scale(if (rtl) -1f else 1f, 1f, pivot = Offset(width / 2f, mid)) {
            val start = knob / 2f
            val end = width - knob / 2f
            if (knobCenter < end - 1f) {
                build(knobCenter, end)
                val dash = phase?.value
                drawPath(
                    path,
                    color.copy(alpha = if (dash != null) 0.9f else 0.3f),
                    style = Stroke(
                        width = stroke,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = dash?.let { PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 9.dp.toPx()), -it * 19.dp.toPx()) },
                    ),
                )
            }
            if (knobCenter > start + 1f) {
                build(start, knobCenter)
                drawPath(
                    path,
                    color.copy(alpha = if (connected) 1f else 0.85f),
                    style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}

@Composable
fun BentoConnectButton(
    state: ConnectionState,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Bento.colors
    val haptics = LocalHapticFeedback.current
    val connected = state == ConnectionState.Connected
    val busy = state == ConnectionState.Connecting || state == ConnectionState.Reconnecting
    val bg by animateColorAsState(if (connected) c.stage else c.lime, label = "connect-btn-bg")
    val fg by animateColorAsState(if (connected) c.onStage else c.onLime, label = "connect-btn-fg")
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val squash by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.6f), label = "connect-btn-squash")
    val label = stateLabel(state)
    val action = actionLabel(state)
    Row(
        modifier
            .fillMaxWidth()
            .height(72.dp)
            .graphicsLayer {
                scaleX = squash
                scaleY = squash
            }
            .clip(BentoShapes.block())
            .background(bg)
            .semantics { stateDescription = label }
            .clickable(source, ripple(color = fg), enabled = state != ConnectionState.Disconnecting, role = Role.Button, onClickLabel = action) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggle()
            }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(fg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(Res.drawable.ic_fill_power), contentDescription = null, tint = bg, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                action,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = fg.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (busy) {
            androidx.compose.material3.CircularProgressIndicator(Modifier.size(22.dp), color = fg, strokeWidth = 2.5.dp)
        }
    }
}
