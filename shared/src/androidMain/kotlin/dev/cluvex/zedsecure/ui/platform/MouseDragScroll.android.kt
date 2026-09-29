package dev.cluvex.zedsecure.ui.platform

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.ui.Modifier

actual fun Modifier.mouseDragScroll(state: ScrollableState): Modifier = this
