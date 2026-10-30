@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.cluvex.zedsecure.ui.platform

import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable

@Composable
actual fun rememberAppSheetState(): SheetState =
    rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))
