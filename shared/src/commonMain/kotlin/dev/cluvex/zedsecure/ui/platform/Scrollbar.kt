package dev.cluvex.zedsecure.ui.platform

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun GridScrollbar(state: LazyGridState, modifier: Modifier = Modifier)
