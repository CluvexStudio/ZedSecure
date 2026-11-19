package dev.cluvex.zedsecure.ui.glass

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import dev.cluvex.zedsecure.domain.model.AppDesign
import dev.cluvex.zedsecure.domain.model.GlassMaterial
import dev.cluvex.zedsecure.domain.model.GlassPrefs
import dev.cluvex.zedsecure.domain.model.GlassTint
import dev.cluvex.zedsecure.domain.model.GlassWallpaper
import dev.cluvex.zedsecure.ui.theme.LocalAppDesign

@Immutable
data class GlassColors(
    val dark: Boolean,
    val tint: Color,
    val label: Color,
    val label2: Color,
    val label3: Color,
    val separator: Color,
    val fill: Color,
    val fill2: Color,
    val page: Color,
    val cell: Color,
    val solidCell: Color,
    val chrome: Color,
    val chromeSolid: Color,
    val module: Color,
    val rimHi: Color,
    val rimMid: Color,
    val rimLo: Color,
    val highlight: Color,
    val platter: Color,
    val thumb: Color,
    val dim: Color,
    val sheet: Color,
    val menu: Color,
    val green: Color,
    val red: Color,
    val orange: Color,
    val shadow: Color,
    val translucent: Boolean,
    val reduced: Boolean,
    val wallpaper: GlassWallpaper,
) {
    fun pingColor(ms: Int?): Color = when {
        ms == null -> label3
        ms <= 0 -> red
        ms < 200 -> green
        ms < 400 -> orange
        else -> red
    }
}

object IosColors {
    fun red(dark: Boolean) = if (dark) Color(0xFFFF4245) else Color(0xFFFF383C)
    fun orange(dark: Boolean) = if (dark) Color(0xFFFF9230) else Color(0xFFFF8D28)
    fun yellow(dark: Boolean) = if (dark) Color(0xFFFFD600) else Color(0xFFFFCC00)
    fun green(dark: Boolean) = if (dark) Color(0xFF30D158) else Color(0xFF34C759)
    fun mint(dark: Boolean) = if (dark) Color(0xFF00DAC3) else Color(0xFF00C8B3)
    fun teal(dark: Boolean) = if (dark) Color(0xFF00D2E0) else Color(0xFF00C3D0)
    fun cyan(dark: Boolean) = if (dark) Color(0xFF3CD3FE) else Color(0xFF00C0E8)
    fun blue(dark: Boolean) = if (dark) Color(0xFF0091FF) else Color(0xFF0088FF)
    fun indigo(dark: Boolean) = if (dark) Color(0xFF6D7CFF) else Color(0xFF6155F5)
    fun purple(dark: Boolean) = if (dark) Color(0xFFDB34F2) else Color(0xFFCB30E0)
    fun pink(dark: Boolean) = if (dark) Color(0xFFFF375F) else Color(0xFFFF2D55)
    fun brown(dark: Boolean) = if (dark) Color(0xFFB78A66) else Color(0xFFAC7F5E)
    fun gray(dark: Boolean) = if (dark) Color(0xFF98989D) else Color(0xFF8E8E93)

    fun tint(tint: GlassTint, dark: Boolean): Color = when (tint) {
        GlassTint.Blue -> blue(dark)
        GlassTint.Indigo -> indigo(dark)
        GlassTint.Purple -> purple(dark)
        GlassTint.Pink -> pink(dark)
        GlassTint.Red -> red(dark)
        GlassTint.Orange -> orange(dark)
        GlassTint.Green -> green(dark)
        GlassTint.Mint -> mint(dark)
        GlassTint.Teal -> teal(dark)
        GlassTint.Cyan -> cyan(dark)
        GlassTint.Brown -> brown(dark)
        GlassTint.Graphite -> gray(dark)
    }
}

object GlassPalette {
    fun of(dark: Boolean, prefs: GlassPrefs): GlassColors {
        val tinted = prefs.material == GlassMaterial.Tinted
        val reduced = prefs.reduceTransparency
        val solidCell = if (dark) Color(0xFF1C1C1E) else Color.White
        val module = when {
            reduced -> solidCell
            dark && tinted -> Color(0xDB28282C)
            dark -> Color(0x612C2C30)
            tinted -> Color(0xD6FFFFFF)
            else -> Color(0x80FFFFFF)
        }
        val chromeSolid = if (dark) Color(0xFF2C2C2E) else Color(0xFFF9F9FB)
        val chrome = when {
            reduced -> chromeSolid
            dark && tinted -> Color(0xDB242426)
            dark -> Color(0x661E1E20)
            tinted -> Color(0xCCFFFFFF)
            else -> Color(0x61FFFFFF)
        }
        return GlassColors(
            dark = dark,
            tint = IosColors.tint(prefs.tint, dark),
            label = if (dark) Color.White else Color.Black,
            label2 = if (dark) Color(0x9EEBEBF5) else Color(0x9E3C3C43),
            label3 = if (dark) Color(0x4DEBEBF5) else Color(0x4D3C3C43),
            separator = if (dark) Color(0x1FFFFFFF) else Color(0x293C3C43),
            fill = if (dark) Color(0x5C787880) else Color(0x33787880),
            fill2 = if (dark) Color(0x3D767680) else Color(0x1F767680),
            page = if (dark) Color.Black else Color(0xFFF2F2F7),
            cell = if (prefs.glassLists) module else solidCell,
            solidCell = solidCell,
            chrome = chrome,
            chromeSolid = chromeSolid,
            module = module,
            rimHi = if (dark) Color(0x94FFFFFF) else Color.White,
            rimMid = if (dark) Color(0x12FFFFFF) else Color(0x47FFFFFF),
            rimLo = if (dark) Color(0x42FFFFFF) else Color(0xCCFFFFFF),
            highlight = if (dark) Color(0x38FFFFFF) else Color(0xF2FFFFFF),
            platter = if (dark) Color(0x1CFFFFFF) else Color(0x12000000),
            thumb = if (dark) Color(0x38FFFFFF) else Color(0xF5FFFFFF),
            dim = if (dark) Color(0x6B000000) else Color(0x2E000000),
            sheet = if (dark) Color(0xF51E1E20) else Color(0xF5F9F9FB),
            menu = if (dark) Color(0xF528282A) else Color(0xF5FCFCFD),
            green = IosColors.green(dark),
            red = IosColors.red(dark),
            orange = IosColors.orange(dark),
            shadow = if (dark) Color(0x80000000) else Color(0x1F000000),
            translucent = prefs.glassLists && !reduced,
            reduced = reduced,
            wallpaper = prefs.wallpaper,
        )
    }

    fun wallpaperTop(kind: GlassWallpaper, dark: Boolean): Color = when (kind) {
        GlassWallpaper.Live -> if (dark) Color(0xFF17191E) else Color(0xFFE4E8EF)
        GlassWallpaper.Lagoon -> if (dark) Color(0xFF031327) else Color(0xFFE2EFFF)
        GlassWallpaper.Dusk -> if (dark) Color(0xFF1A0712) else Color(0xFFFFF0E6)
        GlassWallpaper.Graphite -> if (dark) Color(0xFF121317) else Color(0xFFE1E5EC)
        GlassWallpaper.Plain -> if (dark) Color.Black else Color(0xFFF2F2F7)
    }
}

fun DrawScope.drawGlassWallpaper(kind: GlassWallpaper, dark: Boolean) {
    when (kind) {
        GlassWallpaper.Live -> drawRect(
            Brush.verticalGradient(
                0f to (if (dark) Color(0xFF17191E) else Color(0xFFE4E8EF)),
                0.52f to (if (dark) Color(0xFF0B0C0F) else Color(0xFFECEEF3)),
                1f to (if (dark) Color.Black else Color(0xFFF2F2F7)),
            ),
        )
        GlassWallpaper.Lagoon -> {
            drawRect(Brush.verticalGradient(listOf(if (dark) Color(0xFF031327) else Color(0xFFE2EFFF), if (dark) Color(0xFF010812) else Color(0xFFEEF5FA))))
            ellipseGlow(0.82f, 0.06f, 0.85f, 0.42f, if (dark) Color(0x730091FF) else Color(0x5C0088FF))
            ellipseGlow(0.08f, 0.58f, 0.80f, 0.40f, if (dark) Color(0x4700D2E0) else Color(0x4700C3D0))
        }
        GlassWallpaper.Dusk -> {
            drawRect(Brush.verticalGradient(listOf(if (dark) Color(0xFF1A0712) else Color(0xFFFFF0E6), if (dark) Color(0xFF0A0410) else Color(0xFFF6EDFB))))
            ellipseGlow(0.20f, 0.06f, 0.85f, 0.42f, if (dark) Color(0x61FF9230) else Color(0x5CFF8D28))
            ellipseGlow(0.90f, 0.60f, 0.80f, 0.40f, if (dark) Color(0x52DB34F2) else Color(0x3DCB30E0))
        }
        GlassWallpaper.Graphite -> {
            drawRect(Brush.verticalGradient(listOf(if (dark) Color(0xFF121317) else Color(0xFFE1E5EC), if (dark) Color(0xFF050506) else Color(0xFFECEEF2))))
            ellipseGlow(0.80f, 0.08f, 0.85f, 0.42f, if (dark) Color(0x3D98989D) else Color(0xE6FFFFFF))
        }
        GlassWallpaper.Plain -> drawRect(if (dark) Color.Black else Color(0xFFF2F2F7))
    }
}

private fun DrawScope.ellipseGlow(cx: Float, cy: Float, rx: Float, ry: Float, color: Color) {
    val radiusX = size.width * rx
    val radiusY = size.height * ry
    if (radiusX <= 0f || radiusY <= 0f) return
    val center = Offset(size.width * cx, size.height * cy)
    scale(scaleX = radiusX / radiusY, scaleY = 1f, pivot = center) {
        drawCircle(
            Brush.radialGradient(
                0f to color,
                0.72f to color.copy(alpha = 0f),
                center = center,
                radius = radiusY,
            ),
            radius = radiusY,
            center = center,
        )
    }
}

fun glassColorScheme(c: GlassColors): ColorScheme {
    val white = Color.White
    return if (c.dark) {
        darkColorScheme(
            primary = c.tint,
            onPrimary = white,
            primaryContainer = lerp(Color(0xFF1C1C1E), c.tint, 0.32f),
            onPrimaryContainer = lerp(c.tint, white, 0.55f),
            inversePrimary = lerp(c.tint, Color.Black, 0.2f),
            secondary = c.tint,
            onSecondary = white,
            secondaryContainer = Color(0xFF2C2C2E),
            onSecondaryContainer = white,
            tertiary = c.green,
            onTertiary = Color.Black,
            tertiaryContainer = lerp(Color(0xFF1C1C1E), c.green, 0.3f),
            onTertiaryContainer = lerp(c.green, white, 0.5f),
            background = Color.Black,
            onBackground = white,
            surface = Color.Black,
            onSurface = white,
            surfaceVariant = Color(0xFF3A3A3C),
            onSurfaceVariant = Color(0xFF8D8D93),
            surfaceTint = c.tint,
            inverseSurface = Color(0xFFF2F2F7),
            inverseOnSurface = Color.Black,
            error = c.red,
            onError = white,
            errorContainer = lerp(Color(0xFF1C1C1E), c.red, 0.3f),
            onErrorContainer = lerp(c.red, white, 0.5f),
            outline = Color(0xFF545458),
            outlineVariant = Color(0xFF38383A),
            scrim = Color.Black,
            surfaceBright = Color(0xFF2C2C2E),
            surfaceDim = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF1C1C1E),
            surfaceContainer = Color(0xFF1C1C1E),
            surfaceContainerHigh = Color(0xFF2C2C2E),
            surfaceContainerHighest = Color(0xFF3A3A3C),
        )
    } else {
        lightColorScheme(
            primary = c.tint,
            onPrimary = white,
            primaryContainer = lerp(white, c.tint, 0.16f),
            onPrimaryContainer = lerp(c.tint, Color.Black, 0.45f),
            inversePrimary = lerp(c.tint, white, 0.35f),
            secondary = c.tint,
            onSecondary = white,
            secondaryContainer = Color(0xFFE5E5EA),
            onSecondaryContainer = Color.Black,
            tertiary = c.green,
            onTertiary = white,
            tertiaryContainer = lerp(white, c.green, 0.18f),
            onTertiaryContainer = lerp(c.green, Color.Black, 0.5f),
            background = Color(0xFFF2F2F7),
            onBackground = Color.Black,
            surface = Color(0xFFF2F2F7),
            onSurface = Color.Black,
            surfaceVariant = Color(0xFFE5E5EA),
            onSurfaceVariant = Color(0xFF8A8A8E),
            surfaceTint = c.tint,
            inverseSurface = Color(0xFF1C1C1E),
            inverseOnSurface = white,
            error = c.red,
            onError = white,
            errorContainer = lerp(white, c.red, 0.15f),
            onErrorContainer = lerp(c.red, Color.Black, 0.45f),
            outline = Color(0xFFC6C6C8),
            outlineVariant = Color(0xFFD8D8DC),
            scrim = Color.Black,
            surfaceBright = white,
            surfaceDim = Color(0xFFE5E5EA),
            surfaceContainerLowest = white,
            surfaceContainerLow = white,
            surfaceContainer = white,
            surfaceContainerHigh = Color(0xFFF9F9FB),
            surfaceContainerHighest = Color(0xFFE5E5EA),
        )
    }
}

@Immutable
class GlassLook(val colors: GlassColors, val prefs: GlassPrefs, val latin: Boolean)

val LocalGlassLook = staticCompositionLocalOf { GlassLook(GlassPalette.of(false, GlassPrefs()), GlassPrefs(), latin = true) }

object Glass {
    val colors: GlassColors
        @Composable @ReadOnlyComposable get() = LocalGlassLook.current.colors

    val prefs: GlassPrefs
        @Composable @ReadOnlyComposable get() = LocalGlassLook.current.prefs

    val latin: Boolean
        @Composable @ReadOnlyComposable get() = LocalGlassLook.current.latin

    val active: Boolean
        @Composable @ReadOnlyComposable get() = LocalAppDesign.current == AppDesign.Glass
}

object GlassType {
    @Composable
    @ReadOnlyComposable
    private fun tracking(latinValue: TextUnit): TextUnit = if (Glass.latin) latinValue else 0.sp

    @Composable
    @ReadOnlyComposable
    fun largeTitle(): TextStyle = MaterialTheme.typography.displaySmall.copy(
        fontSize = 34.sp,
        lineHeight = 41.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = tracking((-0.6).sp),
    )

    @Composable
    @ReadOnlyComposable
    fun title2(): TextStyle = MaterialTheme.typography.titleLarge.copy(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = tracking((-0.3).sp),
    )

    @Composable
    @ReadOnlyComposable
    fun headline(): TextStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = tracking((-0.25).sp),
    )

    @Composable
    @ReadOnlyComposable
    fun body(): TextStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = tracking((-0.25).sp),
    )

    @Composable
    @ReadOnlyComposable
    fun subhead(): TextStyle = MaterialTheme.typography.bodyMedium.copy(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = tracking((-0.15).sp),
    )

    @Composable
    @ReadOnlyComposable
    fun footnote(): TextStyle = MaterialTheme.typography.bodySmall.copy(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = tracking((-0.05).sp),
    )

    @Composable
    @ReadOnlyComposable
    fun caption(): TextStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 12.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
    )

    @Composable
    @ReadOnlyComposable
    fun tabLabel(): TextStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 10.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp,
    )
}

@Composable
@ReadOnlyComposable
fun glassSurface(default: Color): Color = if (Glass.active) Glass.colors.cell else default
