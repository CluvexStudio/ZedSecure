package dev.cluvex.zedsecure.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember

@Composable
actual fun rememberGlassTilt(enabled: Boolean): State<Float> = remember { mutableFloatStateOf(GlassLightAngle) }
