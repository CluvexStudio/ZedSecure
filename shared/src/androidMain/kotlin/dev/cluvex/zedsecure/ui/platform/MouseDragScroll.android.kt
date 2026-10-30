package dev.cluvex.zedsecure.ui.platform

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Modifier

actual fun Modifier.mouseDragScroll(state: ScrollableState): Modifier = this

actual fun Modifier.mouseWheelScroll(state: ScrollableState): Modifier = this

actual fun Modifier.mouseDragScrollVertical(state: ScrollableState, reverse: Boolean): Modifier = this

actual fun Modifier.desktopScrollbar(state: ScrollState, padding: PaddingValues): Modifier = this
