package dev.cluvex.zedsecure.ui.platform

import androidx.compose.runtime.Composable

@Composable
expect fun BackHandler(enabled: Boolean = true, onBack: () -> Unit)
