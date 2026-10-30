package dev.cluvex.zedsecure.ui.platform

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

actual fun Modifier.mouseDragScroll(state: ScrollableState): Modifier = composed {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    pointerInput(state, rtl) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (down.type != PointerType.Mouse) return@awaitEachGesture
            var dragging = false
            var pending = 0f
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val dx = change.position.x - change.previousPosition.x
                if (!dragging) {
                    pending += dx
                    if (abs(pending) < viewConfiguration.touchSlop) continue
                    dragging = true
                    state.dispatchRawDelta(if (rtl) pending else -pending)
                } else {
                    state.dispatchRawDelta(if (rtl) dx else -dx)
                }
                change.consume()
            }
        }
    }
}

actual fun Modifier.mouseWheelScroll(state: ScrollableState): Modifier =
    pointerInput(state) {
        val step = WHEEL_STEP.toPx()
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type != PointerEventType.Scroll) continue
                val change = event.changes.firstOrNull() ?: continue
                val delta = change.scrollDelta
                val notches = if (delta.x != 0f) delta.x else delta.y
                if (notches == 0f) continue
                if (state.dispatchRawDelta(notches * step) != 0f) change.consume()
            }
        }
    }

actual fun Modifier.mouseDragScrollVertical(state: ScrollableState, reverse: Boolean): Modifier =
    pointerInput(state, reverse) {
        val decay = exponentialDecay<Float>()
        coroutineScope {
            var fling: Job? = null
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (down.type != PointerType.Mouse || !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
                fling?.cancel()
                val tracker = VelocityTracker()
                tracker.addPosition(down.uptimeMillis, down.position)
                var dragging = false
                var pendingX = 0f
                var pendingY = 0f
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        if (dragging) {
                            change.consume()
                            val velocity = tracker.calculateVelocity().y
                            val start = if (reverse) velocity else -velocity
                            if (abs(start) > MIN_FLING_VELOCITY) {
                                fling = launch {
                                    state.scroll {
                                        var last = 0f
                                        AnimationState(0f, start).animateDecay(decay) {
                                            val delta = value - last
                                            last = value
                                            if (abs(delta - scrollBy(delta)) > 0.5f) cancelAnimation()
                                        }
                                    }
                                }
                            }
                        }
                        break
                    }
                    if (!dragging && change.isConsumed) break
                    tracker.addPosition(change.uptimeMillis, change.position)
                    val dx = change.position.x - change.previousPosition.x
                    val dy = change.position.y - change.previousPosition.y
                    if (!dragging) {
                        pendingX += dx
                        pendingY += dy
                        if (abs(pendingY) < viewConfiguration.touchSlop) continue
                        if (abs(pendingY) < abs(pendingX)) break
                        dragging = true
                        state.dispatchRawDelta(if (reverse) pendingY else -pendingY)
                    } else {
                        state.dispatchRawDelta(if (reverse) dy else -dy)
                    }
                    change.consume()
                }
            }
        }
    }

actual fun Modifier.desktopScrollbar(state: ScrollState, padding: PaddingValues): Modifier = composed {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val ink = MaterialTheme.colorScheme.onSurface
    var hot by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (hot) HOT_ALPHA else IDLE_ALPHA, tween(HOVER_MS), label = "scrollbar")
    val topPad = padding.calculateTopPadding()
    val bottomPad = padding.calculateBottomPadding()
    drawWithContent {
        drawContent()
        val thumb = thumbOf(state, size.height, topPad.toPx(), bottomPad.toPx(), MIN_THUMB.toPx()) ?: return@drawWithContent
        val thickness = THICKNESS.toPx()
        val x = if (rtl) EDGE.toPx() else size.width - EDGE.toPx() - thickness
        drawRoundRect(
            color = ink.copy(alpha = alpha),
            topLeft = Offset(x, thumb.top),
            size = Size(thickness, thumb.height),
            cornerRadius = CornerRadius(thickness / 2f),
        )
    }.pointerInput(state, rtl, topPad, bottomPad) {
        val strip = STRIP.toPx()
        fun inStrip(x: Float): Boolean = if (rtl) x <= strip else x >= size.width - strip
        fun currentThumb(): Thumb? =
            thumbOf(state, size.height.toFloat(), topPad.toPx(), bottomPad.toPx(), MIN_THUMB.toPx())
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull() ?: continue
                val thumb = currentThumb()
                val over = thumb != null && inStrip(change.position.x)
                if (event.type == PointerEventType.Exit) {
                    hot = false
                    continue
                }
                if (event.type != PointerEventType.Press || !over || thumb == null || !event.buttons.isPrimaryPressed) {
                    if (hot != over) hot = over
                    continue
                }
                change.consume()
                hot = true
                val y = change.position.y
                val grabbed = y >= thumb.top && y <= thumb.top + thumb.height
                if (!grabbed) {
                    val page = size.height * PAGE_FRACTION
                    state.dispatchRawDelta(if (y < thumb.top) -page else page)
                }
                var lastX = change.position.x
                var lastY = y
                while (true) {
                    val moved = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == change.id } ?: break
                    moved.consume()
                    if (!moved.pressed) break
                    if (grabbed) {
                        val now = currentThumb() ?: break
                        val free = now.track - now.height
                        if (free > 0f) state.dispatchRawDelta((moved.position.y - lastY) * state.maxValue / free)
                    }
                    lastX = moved.position.x
                    lastY = moved.position.y
                }
                hot = inStrip(lastX)
            }
        }
    }
}

private class Thumb(val top: Float, val height: Float, val track: Float)

private fun thumbOf(state: ScrollState, viewport: Float, top: Float, bottom: Float, minHeight: Float): Thumb? {
    val max = state.maxValue.toFloat()
    if (max <= 0f || max >= Int.MAX_VALUE.toFloat() || viewport <= 0f) return null
    val track = viewport - top - bottom
    if (track <= 0f) return null
    val height = (track * viewport / (viewport + max)).coerceIn(minOf(minHeight, track), track)
    return Thumb(top + (track - height) * (state.value / max).coerceIn(0f, 1f), height, track)
}

private val WHEEL_STEP = 64.dp
private val THICKNESS = 6.dp
private val EDGE = 2.dp
private val STRIP = 14.dp
private val MIN_THUMB = 32.dp
private const val IDLE_ALPHA = 0.18f
private const val HOT_ALPHA = 0.42f
private const val HOVER_MS = 250
private const val PAGE_FRACTION = 0.9f
private const val MIN_FLING_VELOCITY = 400f
