@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.cluvex.zedsecure.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.ui.graphics.Color

private val Iris = Color(0xFF5646D6)

val LightColors: ColorScheme = expressiveLightColorScheme().copy(
    primary = Iris,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE5DEFF),
    onPrimaryContainer = Color(0xFF150067),
    inversePrimary = Color(0xFFC7BFFF),

    secondary = Color(0xFF00897E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF9FF2E6),
    onSecondaryContainer = Color(0xFF00201D),

    tertiary = Color(0xFFC4326B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9E2),
    onTertiaryContainer = Color(0xFF3E0021),
)

val ZedLime = Color(0xFFC7F24E)
val ZedOnLime = Color(0xFF1A2200)
val ZedHotPink = Color(0xFFFF5FA2)
val ZedViolet = Color(0xFF7A5CFF)
val ZedDeepViolet = Color(0xFF2A1361)
val ZedCyan = Color(0xFF37E0D8)

val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFC7BFFF),
    onPrimary = Color(0xFF2A0A93),
    primaryContainer = Color(0xFF3E2FB0),
    onPrimaryContainer = Color(0xFFE5DEFF),
    inversePrimary = Iris,

    secondary = Color(0xFF52DDCF),
    onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF005048),
    onSecondaryContainer = Color(0xFF9FF2E6),

    tertiary = Color(0xFFFFB0C8),
    onTertiary = Color(0xFF5E1136),
    tertiaryContainer = Color(0xFF7B2953),
    onTertiaryContainer = Color(0xFFFFD9E2),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Color(0xFF131318),
    onBackground = Color(0xFFE5E1E9),
    surface = Color(0xFF131318),
    onSurface = Color(0xFFE5E1E9),
    surfaceVariant = Color(0xFF47464F),
    onSurfaceVariant = Color(0xFFC8C5D0),
    surfaceTint = Color(0xFFC7BFFF),

    inverseSurface = Color(0xFFE5E1E9),
    inverseOnSurface = Color(0xFF303036),

    outline = Color(0xFF928F9A),
    outlineVariant = Color(0xFF47464F),
    scrim = Color(0xFF000000),

    surfaceBright = Color(0xFF3A393F),
    surfaceDim = Color(0xFF131318),
    surfaceContainerLowest = Color(0xFF0E0E13),
    surfaceContainerLow = Color(0xFF1B1B21),
    surfaceContainer = Color(0xFF1F1F25),
    surfaceContainerHigh = Color(0xFF2A2930),
    surfaceContainerHighest = Color(0xFF35343B),
)
