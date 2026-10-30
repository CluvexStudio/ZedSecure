package dev.cluvex.zedsecure.ui.platform

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

expect fun Modifier.mouseDragScroll(state: ScrollableState): Modifier

expect fun Modifier.mouseWheelScroll(state: ScrollableState): Modifier

expect fun Modifier.mouseDragScrollVertical(state: ScrollableState, reverse: Boolean = false): Modifier

expect fun Modifier.desktopScrollbar(state: ScrollState, padding: PaddingValues = PaddingValues(0.dp)): Modifier

fun Modifier.draggableHorizontalScroll(state: ScrollState): Modifier =
    mouseDragScroll(state).horizontalScroll(state)

fun Modifier.appVerticalScroll(state: ScrollState, barPadding: PaddingValues = PaddingValues(0.dp)): Modifier =
    desktopScrollbar(state, barPadding).mouseDragScrollVertical(state).verticalScroll(state)
