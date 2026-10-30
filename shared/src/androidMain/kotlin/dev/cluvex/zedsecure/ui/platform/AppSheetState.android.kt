@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.cluvex.zedsecure.ui.platform

import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable

@Composable
actual fun rememberAppSheetState(): SheetState = rememberModalBottomSheetState()
