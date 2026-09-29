package dev.cluvex.zedsecure.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toPath
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MorphingBlob(
    progress: Float,
    brush: Brush,
    modifier: Modifier = Modifier,
    startShape: RoundedPolygon = MaterialShapes.Cookie9Sided,
    endShape: RoundedPolygon = MaterialShapes.SoftBurst,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val morph = remember(startShape, endShape) { Morph(startShape, endShape) }
    val work = remember { Path() }
    Box(
        modifier = modifier.drawBehind {
            work.rewind()
            val path = morph.toPath(progress.coerceIn(0f, 1f), work)
            path.transform(Matrix().apply { scale(size.width, size.height) })
            val bounds = path.getBounds()
            path.translate(Offset(size.width / 2f, size.height / 2f) - bounds.center)
            drawPath(path, brush = brush)
        },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OrganicSurface(
    brush: Brush,
    modifier: Modifier = Modifier,
    shape: RoundedPolygon = MaterialShapes.Cookie7Sided,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier = modifier
            .clip(shape.toShape())
            .background(brush),
        contentAlignment = Alignment.Center,
        content = content,
    )
}
