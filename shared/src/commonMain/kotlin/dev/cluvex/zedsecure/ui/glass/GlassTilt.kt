package dev.cluvex.zedsecure.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State

const val GlassLightAngle = 45f

@Composable
expect fun rememberGlassTilt(enabled: Boolean): State<Float>
