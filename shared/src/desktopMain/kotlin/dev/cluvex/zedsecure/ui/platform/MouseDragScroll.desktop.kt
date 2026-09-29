package dev.cluvex.zedsecure.ui.platform

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
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
