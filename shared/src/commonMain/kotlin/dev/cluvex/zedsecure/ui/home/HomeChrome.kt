@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.cluvex.zedsecure.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import dev.cluvex.zedsecure.domain.model.ConnectionState
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

internal val SearchShapes: List<RoundedPolygon> = listOf(
    MaterialShapes.SoftBurst,
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Pentagon,
    MaterialShapes.Pill,
    MaterialShapes.Sunny,
    MaterialShapes.Cookie4Sided,
    MaterialShapes.Oval,
)

internal const val SEARCH_STEP_SECONDS = 0.72f

@Composable
internal fun rememberPortalClock(running: Boolean): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val base = time.floatValue
        var start = -1L
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                if (start < 0L) start = now
                time.floatValue = base + (now - start) / 1_000_000_000f
            }
        }
    }
    return time
}

@Composable
internal fun PortalTopBar(
    state: ConnectionState,
    animate: Boolean,
    onSecretUnlocked: () -> Unit,
    onShowLogs: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var taps by remember { mutableIntStateOf(0) }
    var lastTap by remember { mutableLongStateOf(0L) }
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusPill(state, animate)
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(Res.string.app_name),
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = cs.onSurface,
            maxLines = 1,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    val now = System.currentTimeMillis()
                    taps = if (now - lastTap < PORTAL_TAP_WINDOW_MS) taps + 1 else 1
                    lastTap = now
                    if (taps >= PORTAL_TAPS_TO_UNLOCK) {
                        taps = 0
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSecretUnlocked()
                    } else if (taps > 1) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
        IconButton(onClick = onShowLogs) {
            Icon(
                painterResource(Res.drawable.ic_description),
                contentDescription = stringResource(Res.string.logs_title),
                tint = cs.onSurfaceVariant,
            )
        }
    }
}

private const val PORTAL_TAPS_TO_UNLOCK = 5
private const val PORTAL_TAP_WINDOW_MS = 1_200L

@Composable
private fun StatusPill(state: ConnectionState, animate: Boolean) {
    val cs = MaterialTheme.colorScheme
    val label = stringResource(
        when (state) {
            ConnectionState.Connected -> Res.string.state_connected
            ConnectionState.Connecting, ConnectionState.Reconnecting -> Res.string.state_connecting
            ConnectionState.Error -> Res.string.state_error
            ConnectionState.Disconnecting -> Res.string.state_disconnecting
            ConnectionState.Idle -> Res.string.state_idle
        },
    )
    val dot by animateColorAsState(
        when (state) {
            ConnectionState.Connected -> cs.primary
            ConnectionState.Connecting, ConnectionState.Reconnecting -> cs.tertiary
            ConnectionState.Error -> cs.error
            else -> cs.outline
        },
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "status-dot",
    )
    val searching = state == ConnectionState.Connecting || state == ConnectionState.Reconnecting
    val pulse = if (searching && animate) {
        rememberInfiniteTransition(label = "status-pulse").animateFloat(
            initialValue = 1f,
            targetValue = 1.7f,
            animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
            label = "status-pulse",
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            Modifier.padding(start = 12.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .graphicsLayer {
                        scaleX = pulse.value
                        scaleY = pulse.value
                    }
                    .clip(CircleShape)
                    .background(dot),
            )
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = cs.onSurface, maxLines = 1)
        }
    }
}
