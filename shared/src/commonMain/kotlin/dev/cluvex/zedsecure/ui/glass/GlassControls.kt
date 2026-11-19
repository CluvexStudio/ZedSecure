package dev.cluvex.zedsecure.ui.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun GlassSpinner(color: Color, modifier: Modifier = Modifier, animate: Boolean = true) {
    val turn = if (animate) {
        rememberInfiniteTransition(label = "glass-spin").animateFloat(
            0f,
            360f,
            infiniteRepeatable(tween(900, easing = LinearEasing)),
            label = "glass-spin-angle",
        )
    } else {
        null
    }
    Canvas(modifier.size(17.dp)) {
        val w = 2.2.dp.toPx()
        rotate(turn?.value ?: 0f) {
            drawCircle(color.copy(alpha = 0.35f), radius = (size.minDimension - w) / 2f, style = Stroke(w))
            drawArc(color, -90f, 90f, useCenter = false, style = Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round), topLeft = Offset(w / 2f, w / 2f), size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w))
        }
    }
}

@Composable
fun GlassConnectButton(
    phase: GlassZPhase,
    connectLabel: String,
    connectingLabel: String,
    disconnectLabel: String,
    animate: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Glass.colors
    val source = remember { MutableInteractionSource() }
    val on = phase == GlassZPhase.On
    val busy = phase == GlassZPhase.Busy
    val shape = GlassShapes.capsule
    val labelColor by animateColorAsState(if (on) c.red else Color.White, label = "glass-btn-label")
    Row(
        modifier
            .glassPress(source)
            .fillMaxWidth()
            .height(56.dp)
            .then(
                if (on) {
                    Modifier.glassChrome(c, shape)
                } else {
                    Modifier
                        .shadow(14.dp, shape, clip = false, ambientColor = c.tint.copy(alpha = 0.5f), spotColor = c.tint.copy(alpha = 0.5f))
                        .clip(shape)
                        .background(if (busy) c.tint.copy(alpha = 0.72f) else c.tint)
                        .background(Brush.verticalGradient(0f to Color.White.copy(alpha = if (busy) 0.18f else 0.26f), 0.52f to Color.Transparent))
                        .glassHighlight(shape, Color.White.copy(alpha = 0.55f))
                },
            )
            .clickable(source, null, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) GlassSpinner(Color.White, animate = animate)
        Text(
            when {
                on -> disconnectLabel
                busy -> connectingLabel
                else -> connectLabel
            },
            style = GlassType.headline(),
            color = labelColor,
            maxLines = 1,
        )
    }
}

@Composable
fun GlassVpnCard(
    phase: GlassZPhase,
    title: String,
    subtitle: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Glass.colors
    val on = phase == GlassZPhase.On
    val source = remember { MutableInteractionSource() }
    val iconBg by animateColorAsState(if (on) c.green else c.fill, label = "glass-vpn-icon")
    Row(
        modifier
            .fillMaxWidth()
            .height(74.dp)
            .glassModule(c, GlassShapes.cell)
            .clickable(source, glassRowIndication(), role = Role.Switch, onClick = onToggle)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(iconBg), contentAlignment = Alignment.Center) {
            Icon(GlassIcons.Shield, contentDescription = null, tint = if (on) Color.White else c.label, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = GlassType.headline(), color = c.label, maxLines = 1)
            Text(subtitle, style = GlassType.subhead(), color = c.label2, maxLines = 1)
        }
        GlassSwitch(checked = phase != GlassZPhase.Off, onCheckedChange = null, interactionSource = source)
    }
}

@Composable
fun GlassSlideControl(
    phase: GlassZPhase,
    text: String,
    label: String,
    animate: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Glass.colors
    val on = phase == GlassZPhase.On
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) }
    val shimmer = if (animate) {
        rememberInfiniteTransition(label = "glass-slide").animateFloat(
            -1f,
            2f,
            infiniteRepeatable(tween(2600, easing = LinearEasing)),
            label = "glass-slide-shine",
        )
    } else {
        null
    }
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(74.dp)
            .glassChrome(c, GlassShapes.capsule)
            .semantics {
                contentDescription = label
                onClick { onToggle(); true }
            },
    ) {
        val knob = 64.dp
        val travel = with(density) { (maxWidth - knob - 10.dp).toPx() }.coerceAtLeast(1f)
        val base = if (on) travel else 0f
        LaunchedEffect(on) { drag.snapTo(0f) }
        val textColor = c.label
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = if (on) 0.dp else 72.dp, end = if (on) 72.dp else 0.dp)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val p = shimmer?.value
                    if (p != null) {
                        val w = size.width
                        drawRect(
                            Brush.horizontalGradient(
                                0f to textColor.copy(alpha = 0.35f),
                                0.45f to textColor,
                                0.9f to textColor.copy(alpha = 0.35f),
                                startX = w * (p - 1f),
                                endX = w * p,
                            ),
                            blendMode = BlendMode.SrcIn,
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = GlassType.body().copy(fontWeight = FontWeight.Medium), color = if (shimmer == null) c.label2 else textColor, maxLines = 1)
        }
        Box(
            Modifier
                .offset {
                    val x = (base + drag.value).coerceIn(0f, travel)
                    val pad = 5.dp.roundToPx()
                    IntOffset(x.roundToInt() + pad, pad)
                }
                .size(knob)
                .shadow(6.dp, CircleShape, clip = false, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
                .clip(CircleShape)
                .background(Color.White)
                .pointerInput(on, travel, rtl) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val total = base + drag.value
                            val flipped = if (on) total < travel * 0.3f else total > travel * 0.7f
                            scope.launch {
                                if (flipped) {
                                    drag.animateTo((if (on) 0f else travel) - base, spring(stiffness = Spring.StiffnessMedium))
                                    onToggle()
                                } else {
                                    drag.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow))
                                }
                            }
                        },
                        onDragCancel = { scope.launch { drag.animateTo(0f) } },
                    ) { change, amount ->
                        change.consume()
                        val delta = if (rtl) -amount else amount
                        scope.launch { drag.snapTo((drag.value + delta).coerceIn(-base, travel - base)) }
                    }
                }
                .clickable(remember { MutableInteractionSource() }, null, role = Role.Button) { onToggle() },
            contentAlignment = Alignment.Center,
        ) {
            GlassZMark(if (on) Color(0xFFB06CFF) else c.tint, Modifier.size(30.dp))
        }
    }
}
