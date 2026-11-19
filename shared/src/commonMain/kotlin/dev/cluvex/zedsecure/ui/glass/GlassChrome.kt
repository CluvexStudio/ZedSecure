package dev.cluvex.zedsecure.ui.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.isRenderEffectSupported

@Stable
class GlassChromeBackdrops(val wall: LayerBackdrop, val page: LayerBackdrop, val combined: Backdrop)

@Composable
fun rememberGlassChrome(): GlassChromeBackdrops {
    val wall = rememberLayerBackdrop()
    val page = rememberLayerBackdrop()
    val combined = rememberCombinedBackdrop(wall, page)
    return remember(wall, page, combined) { GlassChromeBackdrops(wall, page, combined) }
}

@Composable
fun GlassWallpaperLayer(backdrop: LayerBackdrop, modifier: Modifier = Modifier) {
    val c = Glass.colors
    Box(
        modifier
            .fillMaxSize()
            .layerBackdrop(backdrop)
            .drawBehind { drawGlassWallpaper(c.wallpaper, c.dark) },
    )
}

@Composable
fun GlassTopEdge(backdrop: Backdrop, modifier: Modifier = Modifier) {
    val c = Glass.colors
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    if (top <= 0.dp) return
    val height = top + 14.dp
    val tone = GlassPalette.wallpaperTop(c.wallpaper, c.dark)
    if (c.reduced || !isRenderEffectSupported()) {
        Box(
            modifier
                .fillMaxWidth()
                .height(height)
                .drawBehind {
                    drawRect(
                        Brush.verticalGradient(
                            0f to tone.copy(alpha = 0.96f),
                            0.55f to tone.copy(alpha = 0.9f),
                            1f to tone.copy(alpha = 0f),
                        ),
                    )
                },
        )
        return
    }
    val fade = Brush.verticalGradient(0f to Color.Black, 0.55f to Color.Black, 1f to Color.Transparent)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RectangleShape },
                effects = { blur(10.dp.toPx()) },
                highlight = null,
                shadow = null,
                onDrawSurface = { drawRect(tone.copy(alpha = 0.35f)) },
                onDrawFront = { drawRect(fade, blendMode = BlendMode.DstIn) },
            ),
    )
}
