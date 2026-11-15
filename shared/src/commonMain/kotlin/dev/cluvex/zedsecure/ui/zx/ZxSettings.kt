package dev.cluvex.zedsecure.ui.zx

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.cluvex.zedsecure.domain.model.AppSettings
import dev.cluvex.zedsecure.domain.model.ZxAnim
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.zx_anim_auto
import dev.cluvex.zedsecure.shared.resources.zx_anim_balanced
import dev.cluvex.zedsecure.shared.resources.zx_anim_hint
import dev.cluvex.zedsecure.shared.resources.zx_anim_max
import dev.cluvex.zedsecure.shared.resources.zx_anim_min
import dev.cluvex.zedsecure.shared.resources.zx_anim_title
import dev.cluvex.zedsecure.shared.resources.zx_group
import dev.cluvex.zedsecure.ui.settings.SettingsGroup
import dev.cluvex.zedsecure.ui.settings.SettingsInfoRow
import dev.cluvex.zedsecure.ui.settings.SettingsListRow
import org.jetbrains.compose.resources.stringResource

/** Settings shown on the UI page while the ZX design is selected. */
@Composable
fun ZxLookSection(settings: AppSettings, onUpdate: ((AppSettings) -> AppSettings) -> Unit) {
    SettingsGroup(stringResource(Res.string.zx_group)) {
        SettingsListRow(
            title = stringResource(Res.string.zx_anim_title),
            options = listOf(
                ZxAnim.Auto to stringResource(Res.string.zx_anim_auto),
                ZxAnim.Min to stringResource(Res.string.zx_anim_min),
                ZxAnim.Balanced to stringResource(Res.string.zx_anim_balanced),
                ZxAnim.Max to stringResource(Res.string.zx_anim_max),
            ),
            selected = settings.zxAnim,
            onSelected = { v -> onUpdate { it.copy(zxAnim = v) } },
        )
        SettingsInfoRow(
            title = stringResource(Res.string.zx_anim_title),
            body = stringResource(Res.string.zx_anim_hint),
        )
    }
}

/** Static miniature of the ZX home, used by the design picker. It always shows the night palette. */
@Composable
fun ZxDesignPreview() {
    val c = ZxPalettes.Night
    Box(Modifier.fillMaxSize().background(c.ground)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val horizon = h * 0.55f

            drawRect(
                Brush.radialGradient(
                    listOf(c.neonB.copy(alpha = 0.30f), Color.Transparent),
                    center = Offset(w * 0.1f, 0f),
                    radius = w * 1.1f,
                ),
            )

            val grid = c.grid.copy(alpha = 0.35f)
            for (i in -5..5) {
                drawLine(grid, Offset(w / 2f + i * w * 0.015f, horizon), Offset(w / 2f + i * w * 0.14f, h), 1.dp.toPx())
            }
            for (k in 0 until 5) {
                val t = (k + 1) / 5f
                val y = horizon + (h - horizon) * t * t
                drawLine(grid.copy(alpha = 0.12f + 0.25f * t), Offset(0f, y), Offset(w, y), 1.dp.toPx())
            }

            val ctr = Offset(w / 2f, h * 0.34f)
            val r = w * 0.30f
            drawCircle(
                Brush.radialGradient(listOf(c.neonA.copy(alpha = 0.35f), Color.Transparent), center = ctr, radius = r * 1.5f),
                radius = r * 1.5f,
                center = ctr,
            )
            drawCircle(
                c.neonA,
                radius = r,
                center = ctr,
                style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 4.dp.toPx()))),
            )
            val arcR = r * 0.82f
            for (i in 0 until 3) {
                drawArc(
                    color = c.neonB,
                    startAngle = i * 120f + 12f,
                    sweepAngle = 70f,
                    useCenter = false,
                    topLeft = Offset(ctr.x - arcR, ctr.y - arcR),
                    size = Size(arcR * 2f, arcR * 2f),
                    style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            val gr = r * 0.30f
            drawArc(
                color = c.neonA,
                startAngle = -60f,
                sweepAngle = 300f,
                useCenter = false,
                topLeft = Offset(ctr.x - gr, ctr.y - gr),
                size = Size(gr * 2f, gr * 2f),
                style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round),
            )
            drawLine(c.neonA, Offset(ctr.x, ctr.y - gr * 1.25f), Offset(ctr.x, ctr.y - gr * 0.1f), 2.5.dp.toPx(), StrokeCap.Round)

            val barY = h - 12.dp.toPx()
            drawLine(c.neonC.copy(alpha = 0.9f), Offset(w * 0.2f, barY), Offset(w * 0.8f, barY), 3.dp.toPx(), StrokeCap.Round)
        }
    }
}
