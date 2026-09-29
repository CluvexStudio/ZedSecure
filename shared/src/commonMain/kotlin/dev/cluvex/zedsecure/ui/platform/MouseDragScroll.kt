package dev.cluvex.zedsecure.ui.platform

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier

expect fun Modifier.mouseDragScroll(state: ScrollableState): Modifier

fun Modifier.draggableHorizontalScroll(state: ScrollState): Modifier =
    mouseDragScroll(state).horizontalScroll(state)
