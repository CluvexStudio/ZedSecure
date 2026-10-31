package dev.cluvex.zedsecure.ui.bento

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import dev.cluvex.zedsecure.domain.model.AppDesign
import dev.cluvex.zedsecure.domain.model.BentoPrefs
import dev.cluvex.zedsecure.ui.theme.LocalAppDesign

@Immutable
class BentoStyle(val colors: BentoColors, val prefs: BentoPrefs, val latin: Boolean)

val LocalBentoStyle = staticCompositionLocalOf { BentoStyle(BentoPalettes.BrandDark, BentoPrefs(), latin = true) }

object Bento {
    val colors: BentoColors
        @Composable @ReadOnlyComposable get() = LocalBentoStyle.current.colors

    val prefs: BentoPrefs
        @Composable @ReadOnlyComposable get() = LocalBentoStyle.current.prefs

    val latin: Boolean
        @Composable @ReadOnlyComposable get() = LocalBentoStyle.current.latin

    val active: Boolean
        @Composable @ReadOnlyComposable get() = LocalAppDesign.current == AppDesign.Bento
}

fun bentoColorScheme(c: BentoColors): ColorScheme {
    val low = lerp(c.ground, c.surface, 0.5f)
    val highest = lerp(c.surface2, c.line, 0.25f)
    return if (c.dark) {
        darkColorScheme(
            primary = c.lime,
            onPrimary = c.onLime,
            primaryContainer = c.stage,
            onPrimaryContainer = c.onStage,
            inversePrimary = c.stage,
            secondary = c.violet.content,
            onSecondary = c.violet.container,
            secondaryContainer = c.violet.container,
            onSecondaryContainer = c.violet.content,
            tertiary = c.cyan.content,
            onTertiary = c.cyan.container,
            tertiaryContainer = c.cyan.container,
            onTertiaryContainer = c.cyan.content,
            background = c.ground,
            onBackground = c.ink,
            surface = c.ground,
            onSurface = c.ink,
            surfaceVariant = c.surface2,
            onSurfaceVariant = c.muted,
            surfaceTint = c.lime,
            inverseSurface = c.ink,
            inverseOnSurface = c.ground,
            error = c.danger,
            onError = c.onDanger,
            errorContainer = c.coral.container,
            onErrorContainer = c.coral.content,
            outline = c.muted.copy(alpha = 0.7f).compositeOver(c.ground),
            outlineVariant = c.line,
            scrim = Color.Black,
            surfaceBright = c.surface2,
            surfaceDim = c.ground,
            surfaceContainerLowest = c.ground,
            surfaceContainerLow = low,
            surfaceContainer = c.surface,
            surfaceContainerHigh = c.surface,
            surfaceContainerHighest = highest,
        )
    } else {
        lightColorScheme(
            primary = c.stage,
            onPrimary = c.onStage,
            primaryContainer = c.lime,
            onPrimaryContainer = c.onLime,
            inversePrimary = c.lime,
            secondary = c.violet.content,
            onSecondary = c.violet.container,
            secondaryContainer = c.violet.container,
            onSecondaryContainer = c.violet.content,
            tertiary = c.cyan.content,
            onTertiary = c.cyan.container,
            tertiaryContainer = c.cyan.container,
            onTertiaryContainer = c.cyan.content,
            background = c.ground,
            onBackground = c.ink,
            surface = c.ground,
            onSurface = c.ink,
            surfaceVariant = c.surface2,
            onSurfaceVariant = c.muted,
            surfaceTint = c.stage,
            inverseSurface = c.ink,
            inverseOnSurface = c.ground,
            error = c.bad,
            onError = Color.White,
            errorContainer = c.danger,
            onErrorContainer = c.onDanger,
            outline = c.muted.copy(alpha = 0.7f).compositeOver(c.ground),
            outlineVariant = c.line,
            scrim = Color.Black,
            surfaceBright = c.ground,
            surfaceDim = c.surface2,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = low,
            surfaceContainer = c.surface,
            surfaceContainerHigh = c.surface,
            surfaceContainerHighest = c.surface2,
        )
    }
}

private fun Color.compositeOver(background: Color): Color {
    val a = alpha
    return Color(
        red = red * a + background.red * (1 - a),
        green = green * a + background.green * (1 - a),
        blue = blue * a + background.blue * (1 - a),
        alpha = 1f,
    )
}
