package dev.cluvex.zedsecure.ui.zxps

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import dev.cluvex.zedsecure.domain.model.AppDesign
import dev.cluvex.zedsecure.domain.model.ZxPsPalette
import dev.cluvex.zedsecure.domain.model.ZxPsPrefs
import dev.cluvex.zedsecure.domain.model.ZxPsTile
import dev.cluvex.zedsecure.ui.theme.LocalAppDesign

@Immutable
class ZxPsColors(
    val dark: Boolean,
    val ground: Color,
    val surface: Color,
    val surface2: Color,
    val line: Color,
    val ink: Color,
    val muted: Color,
    val a: Color,
    val b: Color,
    val c: Color,
    val onA: Color,
    val onB: Color,
    val onC: Color,
    val good: Color,
    val danger: Color,
)

@Immutable
class ZxPsStyle(val colors: ZxPsColors, val prefs: ZxPsPrefs)

val LocalZxPsStyle = staticCompositionLocalOf { ZxPsStyle(ZxPsPalettes.of(ZxPsPalette.Aurora, true), ZxPsPrefs()) }

object ZxPs {
    val colors: ZxPsColors
        @Composable @ReadOnlyComposable get() = LocalZxPsStyle.current.colors

    val prefs: ZxPsPrefs
        @Composable @ReadOnlyComposable get() = LocalZxPsStyle.current.prefs

    val active: Boolean
        @Composable @ReadOnlyComposable get() = LocalAppDesign.current == AppDesign.ZxPs
}

private class Seed(val aN: Long, val bN: Long, val cN: Long, val aD: Long, val bD: Long, val cD: Long)

object ZxPsPalettes {
    private val seeds = mapOf(
        ZxPsPalette.Aurora to Seed(0xFF9B8CFF, 0xFF2BD9C5, 0xFFFF7AC6, 0xFF5B3FE0, 0xFF008F86, 0xFFD02A8E),
        ZxPsPalette.Graphite to Seed(0xFFE6E8EE, 0xFF8AA4FF, 0xFFF5C26B, 0xFF2B2F3A, 0xFF3F5BD9, 0xFFB57400),
        ZxPsPalette.Sakura to Seed(0xFFFF7FB0, 0xFFB58CFF, 0xFFFFC38A, 0xFFC4206B, 0xFF7A3FD0, 0xFFC26A00),
        ZxPsPalette.Mint to Seed(0xFF3DDC97, 0xFF4CC3FF, 0xFFE8FF6A, 0xFF00875A, 0xFF0A78B5, 0xFF7A8A00),
        ZxPsPalette.Ember to Seed(0xFFFF7A45, 0xFFFF3D6E, 0xFFFFC24A, 0xFFC9420D, 0xFFC4113F, 0xFFB57A00),
    )

    private fun on(c: Color) = if (c.luminance() > 0.45f) Color(0xFF101014) else Color.White

    fun of(palette: ZxPsPalette, dark: Boolean): ZxPsColors {
        val s = seeds.getValue(palette)
        return if (dark) {
            val a = Color(s.aN)
            val b = Color(s.bN)
            val c = Color(s.cN)
            val ground = lerp(Color(0xFF05050A), a, 0.06f)
            val ink = lerp(Color.White, a, 0.05f)
            ZxPsColors(
                dark = true,
                ground = ground,
                surface = lerp(ground, a, 0.10f),
                surface2 = lerp(ground, a, 0.18f),
                line = lerp(ground, a, 0.32f),
                ink = ink,
                muted = lerp(ink, ground, 0.45f),
                a = a, b = b, c = c,
                onA = on(a), onB = on(b), onC = on(c),
                good = Color(0xFF4DFFA0),
                danger = Color(0xFFFF5470),
            )
        } else {
            val a = Color(s.aD)
            val b = Color(s.bD)
            val c = Color(s.cD)
            val ink = lerp(Color.Black, a, 0.12f)
            ZxPsColors(
                dark = false,
                ground = lerp(Color.White, a, 0.07f),
                surface = lerp(Color.White, a, 0.025f),
                surface2 = lerp(Color.White, a, 0.12f),
                line = lerp(Color.White, a, 0.26f),
                ink = ink,
                muted = lerp(ink, Color.White, 0.42f),
                a = a, b = b, c = c,
                onA = on(a), onB = on(b), onC = on(c),
                good = Color(0xFF0A9B54),
                danger = Color(0xFFD7123A),
            )
        }
    }
}

/** The corner radius family used by every ZX PS surface. */
fun zxPsShapes(tile: ZxPsTile): Shapes {
    val base = when (tile) {
        ZxPsTile.Soft -> 30
        ZxPsTile.Squircle -> 18
        ZxPsTile.Petal -> 24
    }
    fun r(f: Float) = RoundedCornerShape((base * f).dp)
    return Shapes(
        extraSmall = r(0.30f),
        small = r(0.45f),
        medium = r(0.65f),
        large = r(0.85f),
        largeIncreased = r(1.0f),
        extraLarge = r(1.15f),
        extraLargeIncreased = r(1.3f),
        extraExtraLarge = r(1.5f),
    )
}

/** Shape for the tile at [index]. Petal alternates asymmetric leaf corners, like a Pixel widget wall. */
fun zxPsTileShape(tile: ZxPsTile, index: Int, compact: Boolean = false): CornerBasedShape {
    val k = if (compact) 0.75f else 1f
    return when (tile) {
        ZxPsTile.Soft -> RoundedCornerShape((30 * k).dp)
        ZxPsTile.Squircle -> RoundedCornerShape((18 * k).dp)
        ZxPsTile.Petal -> if (index % 2 == 0) {
            RoundedCornerShape(topStart = (34 * k).dp, topEnd = (10 * k).dp, bottomEnd = (34 * k).dp, bottomStart = (10 * k).dp)
        } else {
            RoundedCornerShape(topStart = (10 * k).dp, topEnd = (34 * k).dp, bottomEnd = (10 * k).dp, bottomStart = (34 * k).dp)
        }
    }
}

fun zxPsColorScheme(c: ZxPsColors): ColorScheme {
    fun mix(neon: Color, f: Float) = lerp(c.ground, neon, f)
    val low = lerp(c.ground, c.surface, 0.5f)
    val high = lerp(c.surface, c.surface2, 0.5f)
    return if (c.dark) {
        darkColorScheme(
            primary = c.a, onPrimary = c.onA,
            primaryContainer = mix(c.a, 0.28f), onPrimaryContainer = c.a,
            inversePrimary = c.b,
            secondary = c.b, onSecondary = c.onB,
            secondaryContainer = mix(c.b, 0.26f), onSecondaryContainer = c.b,
            tertiary = c.c, onTertiary = c.onC,
            tertiaryContainer = mix(c.c, 0.24f), onTertiaryContainer = c.c,
            background = c.ground, onBackground = c.ink,
            surface = c.ground, onSurface = c.ink,
            surfaceVariant = c.surface2, onSurfaceVariant = c.muted,
            surfaceTint = c.a,
            inverseSurface = c.ink, inverseOnSurface = c.ground,
            error = c.danger, onError = Color.Black,
            errorContainer = mix(c.danger, 0.28f), onErrorContainer = c.danger,
            outline = c.muted, outlineVariant = c.line,
            scrim = Color.Black,
            surfaceBright = c.surface2, surfaceDim = c.ground,
            surfaceContainerLowest = c.ground, surfaceContainerLow = low,
            surfaceContainer = c.surface, surfaceContainerHigh = high, surfaceContainerHighest = c.surface2,
        )
    } else {
        lightColorScheme(
            primary = c.a, onPrimary = c.onA,
            primaryContainer = lerp(c.surface, c.a, 0.18f), onPrimaryContainer = c.a,
            inversePrimary = c.b,
            secondary = c.b, onSecondary = c.onB,
            secondaryContainer = lerp(c.surface, c.b, 0.16f), onSecondaryContainer = c.b,
            tertiary = c.c, onTertiary = c.onC,
            tertiaryContainer = lerp(c.surface, c.c, 0.22f), onTertiaryContainer = c.c,
            background = c.ground, onBackground = c.ink,
            surface = c.ground, onSurface = c.ink,
            surfaceVariant = c.surface2, onSurfaceVariant = c.muted,
            surfaceTint = c.a,
            inverseSurface = c.ink, inverseOnSurface = c.ground,
            error = c.danger, onError = Color.White,
            errorContainer = lerp(c.surface, c.danger, 0.16f), onErrorContainer = c.danger,
            outline = c.muted, outlineVariant = c.line,
            scrim = Color.Black,
            surfaceBright = c.surface, surfaceDim = c.surface2,
            surfaceContainerLowest = Color.White, surfaceContainerLow = low,
            surfaceContainer = c.surface, surfaceContainerHigh = high, surfaceContainerHighest = c.surface2,
        )
    }
}
