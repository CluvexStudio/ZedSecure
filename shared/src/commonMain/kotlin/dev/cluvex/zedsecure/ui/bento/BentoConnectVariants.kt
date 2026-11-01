@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.cluvex.zedsecure.ui.bento

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cluvex.zedsecure.domain.model.ConnectionState
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.bento_hold_connect
import dev.cluvex.zedsecure.shared.resources.bento_hold_disconnect
import dev.cluvex.zedsecure.shared.resources.bento_hold_note
import dev.cluvex.zedsecure.shared.resources.bento_tap_cancel
import dev.cluvex.zedsecure.shared.resources.bento_tap_connect
import dev.cluvex.zedsecure.shared.resources.bento_tap_disconnect
import dev.cluvex.zedsecure.shared.resources.ic_fill_power
import dev.cluvex.zedsecure.ui.components.MorphingBlob
import dev.cluvex.zedsecure.ui.format.formatElapsed
import dev.cluvex.zedsecure.ui.format.ltrIsolate
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.sin

private const val HOLD_MS = 650

private val ConnectionState.busy: Boolean
    get() = this == ConnectionState.Connecting || this == ConnectionState.Reconnecting

@Composable
private fun tapHint(state: ConnectionState): String = stringResource(
    when {
        state.busy -> Res.string.bento_tap_cancel
        state == ConnectionState.Connected -> Res.string.bento_tap_disconnect
        else -> Res.string.bento_tap_connect
    },
)

@Composable
private fun PowerGlyph(tint: androidx.compose.ui.graphics.Color, size: androidx.compose.ui.unit.Dp) {
    Icon(painterResource(Res.drawable.ic_fill_power), contentDescription = null, tint = tint, modifier = Modifier.size(size))
}

@Composable
private fun spinning(active: Boolean, millis: Int): Float {
    if (!active) return 0f
    val transition = rememberInfiniteTransition(label = "connect-spin")
    val turn by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(millis, easing = LinearEasing), RepeatMode.Restart), label = "connect-spin-turn")
    return turn
}

@Composable
fun BentoConnectMorph(
    state: ConnectionState,
    onToggle: () -> Unit,
    animate: Boolean,
    elapsedSeconds: Int,
    serverName: String?,
    modifier: Modifier = Modifier,
) {
    val c = Bento.colors
    val haptics = LocalHapticFeedback.current
    val connected = state == ConnectionState.Connected
    val container by animateColorAsState(if (connected) c.stage else c.surface, label = "morph-bg")
    val ink by animateColorAsState(if (connected) c.onStage else c.ink, label = "morph-ink")
    val morph by animateFloatAsState(
        if (connected || state.busy) 1f else 0f,
        spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow),
        label = "morph-progress",
    )
    val turn = spinning(animate && state.busy, 2400)
    val drift = spinning(animate && connected && Bento.prefs.playfulTouch, 24000)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val squash by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = 0.5f), label = "morph-press")
    val label = stateLabel(state)
    Row(
        modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(BentoShapes.block())
            .background(container)
            .semantics { stateDescription = label }
            .clickable(source, ripple(color = ink), enabled = state != ConnectionState.Disconnecting, role = Role.Button, onClickLabel = actionLabel(state)) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggle()
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MorphingBlob(
            progress = morph,
            brush = SolidColor(c.lime),
            startShape = MaterialShapes.Cookie9Sided,
            endShape = MaterialShapes.SoftBurst,
            modifier = Modifier
                .size(90.dp)
                .graphicsLayer {
                    scaleX = squash
                    scaleY = squash
                    rotationZ = turn + drift
                },
        ) {
            Box(Modifier.graphicsLayer { rotationZ = -(turn + drift) }) { PowerGlyph(c.onLime, 34.dp) }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp, fontWeight = FontWeight.Bold),
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (connected) ltrIsolate(formatElapsed(elapsedSeconds)) else serverName ?: tapHint(state),
                style = BentoType.number(15.sp),
                color = ink.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (connected || state.busy) {
                Text(tapHint(state), style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.65f), maxLines = 1)
            }
        }
    }
}

@Composable
fun BentoConnectRail(
    state: ConnectionState,
    onToggle: () -> Unit,
    animate: Boolean,
    elapsedSeconds: Int,
    modifier: Modifier = Modifier,
) {
    val c = Bento.colors
    val haptics = LocalHapticFeedback.current
    val on = state == ConnectionState.Connected || state.busy
    val track by animateColorAsState(if (on) c.lime else c.surface2, label = "rail-track")
    val thumb by animateColorAsState(if (on) c.onLime else c.lime, label = "rail-thumb")
    val glyph by animateColorAsState(if (on) c.lime else c.onLime, label = "rail-glyph")
    val travel by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow), label = "rail-travel")
    val phase = spinning(animate && state.busy, 900) / 360f
    val label = stateLabel(state)
    val action = actionLabel(state)
    val text = when {
        state == ConnectionState.Connected -> formatElapsed(elapsedSeconds)
        state.busy -> label
        else -> tapHint(state)
    }
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(40.dp))
            .background(track)
            .semantics(mergeDescendants = true) {
                role = Role.Switch
                stateDescription = label
                onClick(action) {
                    if (state != ConnectionState.Disconnecting) onToggle()
                    true
                }
            }
            .clickable(enabled = state != ConnectionState.Disconnecting, role = Role.Switch, onClickLabel = action) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggle()
            }
            .padding(8.dp),
    ) {
        val knob = 64.dp
        val distance = (maxWidth - knob).coerceAtLeast(0.dp)
        if (on && Bento.prefs.wave) {
            Canvas(Modifier.fillMaxSize().padding(horizontal = 74.dp)) {
                val mid = size.height / 2f
                val amp = 6.dp.toPx()
                val length = 30.dp.toPx()
                val shift = phase * length
                val wave = androidx.compose.ui.graphics.Path()
                var x = 0f
                wave.moveTo(0f, mid + amp * sin(2f * PI.toFloat() * shift / length))
                while (x < size.width) {
                    x = (x + 2f).coerceAtMost(size.width)
                    wave.lineTo(x, mid + amp * sin(2f * PI.toFloat() * (x + shift) / length))
                }
                drawPath(wave, c.onLime.copy(alpha = if (state.busy) 0.55f else 0.9f), style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
            }
        } else {
            Text(
                text,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = if (on) c.onLime else c.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 72.dp),
            )
        }
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = distance * travel)
                .size(knob)
                .clip(CircleShape)
                .background(thumb),
            contentAlignment = Alignment.Center,
        ) {
            PowerGlyph(glyph, 28.dp)
        }
    }
}

@Composable
fun BentoConnectHold(
    state: ConnectionState,
    onToggle: () -> Unit,
    animate: Boolean,
    elapsedSeconds: Int,
    serverName: String?,
    modifier: Modifier = Modifier,
) {
    val c = Bento.colors
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val connected = state == ConnectionState.Connected
    val container by animateColorAsState(if (connected) c.stage else c.lime, label = "hold-bg")
    val ink by animateColorAsState(if (connected) c.onStage else c.onLime, label = "hold-ink")
    val knobFill by animateColorAsState(if (connected) c.lime else c.onLime, label = "hold-knob")
    val glyph by animateColorAsState(if (connected) c.onLime else c.lime, label = "hold-glyph")
    val fill = remember { Animatable(0f) }
    val turn = spinning(animate && state.busy, 1100)
    val toggle by rememberUpdatedState(onToggle)
    val current by rememberUpdatedState(state)
    val label = stateLabel(state)
    val action = actionLabel(state)
    val title = when {
        connected -> stringResource(Res.string.bento_hold_disconnect)
        state.busy -> label
        else -> stringResource(Res.string.bento_hold_connect)
    }
    Row(
        modifier
            .fillMaxWidth()
            .height(104.dp)
            .clip(BentoShapes.block())
            .background(container)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                stateDescription = label
                onClick(action) {
                    if (state != ConnectionState.Disconnecting) onToggle()
                    true
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    if (current == ConnectionState.Disconnecting) return@awaitEachGesture
                    if (current.busy) {
                        val up = waitForUpOrCancellation()
                        if (up != null) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            toggle()
                        }
                        return@awaitEachGesture
                    }
                    val run = scope.launch {
                        fill.snapTo(0f)
                        fill.animateTo(1f, tween(HOLD_MS, easing = LinearEasing))
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        toggle()
                    }
                    waitForUpOrCancellation()
                    if (run.isActive) {
                        run.cancel()
                        scope.launch { fill.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                    } else {
                        scope.launch { fill.snapTo(0f) }
                    }
                }
            }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = turn }) {
                val stroke = 6.dp.toPx()
                val inset = stroke / 2f
                val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                drawCircle(knobFill, radius = size.minDimension / 2f - stroke)
                drawArc(ink.copy(alpha = 0.22f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                val sweep = when {
                    connected -> 360f
                    state.busy -> 90f
                    else -> 360f * fill.value
                }
                if (sweep > 0f) {
                    drawArc(if (connected) c.lime else ink, -90f, sweep, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
            PowerGlyph(glyph, 30.dp)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (connected) {
                Text(ltrIsolate(formatElapsed(elapsedSeconds)), style = BentoType.number(26.sp), color = ink, maxLines = 1)
            } else {
                Text(
                    if (state.busy) stringResource(Res.string.bento_tap_cancel) else serverName ?: stringResource(Res.string.bento_hold_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ink.copy(alpha = 0.78f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun BentoConnectTile(
    state: ConnectionState,
    onToggle: () -> Unit,
    animate: Boolean,
    elapsedSeconds: Int,
    serverName: String?,
    modifier: Modifier = Modifier,
) {
    val c = Bento.colors
    val haptics = LocalHapticFeedback.current
    val connected = state == ConnectionState.Connected
    val container by animateColorAsState(if (connected) c.lime else c.stage, label = "tile-bg")
    val ink by animateColorAsState(if (connected) c.onLime else c.onStage, label = "tile-ink")
    val badge by animateColorAsState(if (connected) c.onLime else c.lime, label = "tile-badge")
    val badgeInk by animateColorAsState(if (connected) c.lime else c.onLime, label = "tile-badge-ink")
    val morph by animateFloatAsState(if (connected) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow), label = "tile-morph")
    val turn = spinning(animate && state.busy, 1800)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val squash by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.55f), label = "tile-press")
    val label = stateLabel(state)
    Box(
        modifier
            .fillMaxWidth()
            .height(132.dp)
            .graphicsLayer {
                scaleX = squash
                scaleY = squash
            }
            .clip(BentoShapes.tile(true))
            .background(container)
            .bentoMotif(MaterialShapes.Flower, ink, 190.dp, 18f, enabled = Bento.prefs.motifs, alpha = 0.09f)
            .semantics { stateDescription = label }
            .clickable(source, ripple(color = ink), enabled = state != ConnectionState.Disconnecting, role = Role.Button, onClickLabel = actionLabel(state)) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggle()
            }
            .padding(18.dp),
    ) {
        Column(Modifier.align(Alignment.TopStart).padding(end = 72.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = BentoType.caption(), color = ink.copy(alpha = 0.8f), maxLines = 1)
            if (connected) {
                Text(ltrIsolate(formatElapsed(elapsedSeconds)), style = BentoType.number(40.sp), color = ink, maxLines = 1)
            } else {
                Text(
                    actionLabel(state),
                    style = BentoType.title(34.sp),
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            if (connected || state.busy) tapHint(state) else serverName ?: tapHint(state),
            style = MaterialTheme.typography.bodyMedium,
            color = ink.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(end = 72.dp),
        )
        MorphingBlob(
            progress = morph,
            brush = SolidColor(badge),
            startShape = MaterialShapes.Clover4Leaf,
            endShape = MaterialShapes.Cookie6Sided,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(64.dp)
                .graphicsLayer { rotationZ = turn },
        ) {
            Box(Modifier.graphicsLayer { rotationZ = -turn }) { PowerGlyph(badgeInk, 26.dp) }
        }
    }
}
