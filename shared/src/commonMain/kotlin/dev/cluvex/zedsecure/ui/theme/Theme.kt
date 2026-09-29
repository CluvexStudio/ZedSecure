package dev.cluvex.zedsecure.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ZedSecureTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,

    accentColor: Int = 0,
    amoledBlack: Boolean = false,

    languageTag: String = "en",

    fontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val base: ColorScheme =
        (if (dynamicColor) dynamicColorSchemeOrNull(darkTheme) else null)
            ?: if (darkTheme) DarkColors else LightColors

    val colorScheme = (if (dynamicColor) base else base.withAccent(accentColor, darkTheme))
        .let { if (amoledBlack) it.amoled(darkTheme) else it }

    val density = LocalDensity.current
    val scaled = remember(density, fontScale) {
        if (fontScale == 1f) density else Density(density.density, density.fontScale * fontScale)
    }
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = ZedShapes,
        typography = typographyFor(languageTag),
    ) {
        CompositionLocalProvider(LocalDensity provides scaled, content = content)
    }
}

@Composable
expect fun dynamicColorSchemeOrNull(darkTheme: Boolean): ColorScheme?
