package dev.cluvex.zedsecure.ui.platform

import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
actual fun GridScrollbar(state: LazyGridState, modifier: Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(state),
        modifier = modifier,
        style = ScrollbarStyle(
            minimalHeight = 32.dp,
            thickness = 6.dp,
            shape = RoundedCornerShape(3.dp),
            hoverDurationMillis = 250,
            unhoverColor = ink.copy(alpha = 0.18f),
            hoverColor = ink.copy(alpha = 0.42f),
        ),
    )
}
