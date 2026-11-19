package dev.cluvex.zedsecure.ui.speedtest

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cluvex.zedsecure.data.net.SpeedGrade
import dev.cluvex.zedsecure.data.net.SpeedTestEngine
import dev.cluvex.zedsecure.data.net.SpeedTestPhase
import dev.cluvex.zedsecure.data.net.SpeedTestState
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.*
import dev.cluvex.zedsecure.ui.format.ltrIsolate
import dev.cluvex.zedsecure.ui.glass.Glass
import dev.cluvex.zedsecure.ui.glass.GlassFooter
import dev.cluvex.zedsecure.ui.glass.GlassGroup
import dev.cluvex.zedsecure.ui.glass.GlassIcons
import dev.cluvex.zedsecure.ui.glass.GlassRow
import dev.cluvex.zedsecure.ui.glass.GlassSectionHeader
import dev.cluvex.zedsecure.ui.glass.GlassSettingsHeader
import dev.cluvex.zedsecure.ui.glass.GlassShapes
import dev.cluvex.zedsecure.ui.glass.GlassSwitchRow
import dev.cluvex.zedsecure.ui.glass.GlassType
import dev.cluvex.zedsecure.ui.glass.glassHighlight
import dev.cluvex.zedsecure.ui.glass.glassModule
import dev.cluvex.zedsecure.ui.glass.glassPress
import dev.cluvex.zedsecure.ui.platform.appVerticalScroll
import org.jetbrains.compose.resources.stringResource
import java.util.Locale
import kotlin.math.log10
import kotlin.math.roundToInt

private fun glassMbps(value: Double): String = when {
    value >= 100 -> value.roundToInt().toString()
    value >= 10 -> String.format(Locale.US, "%.1f", value)
    else -> String.format(Locale.US, "%.2f", value)
}

private fun glassMs(value: Double?): String = value?.let { String.format(Locale.US, "%.1f ms", it) } ?: "—"

@Composable
internal fun GlassSpeedTestLayout(
    st: SpeedTestState,
    contentPadding: PaddingValues,
    modifier: Modifier,
    connected: Boolean,
    viaDefaultRoute: Boolean,
    unsupported: Boolean,
    saveData: Boolean,
    onSaveData: (Boolean) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val c = Glass.colors
    val scroll = rememberScrollState()
    Column(
        modifier
            .fillMaxSize()
            .appVerticalScroll(scroll, contentPadding)
            .padding(contentPadding),
    ) {
        Spacer(Modifier.height(8.dp))
        GlassSettingsHeader(stringResource(Res.string.speedtest_title))
        if (unsupported) {
            GlassFooter(stringResource(Res.string.speedtest_unsupported_dns), Modifier.padding(top = 12.dp))
            return@Column
        }
        RouteChips(st, connected, viaDefaultRoute)
        Gauge(st)
        PhaseRow(st)
        if (st.hasResults) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ResultTile(stringResource(Res.string.speedtest_download), GlassIcons.Down, st.downloadMbps, c.tint, Modifier.weight(1f))
                ResultTile(stringResource(Res.string.speedtest_upload), GlassIcons.Up, st.uploadMbps, c.green, Modifier.weight(1f))
            }
            GlassGroup {
                GlassRow(title = stringResource(Res.string.speedtest_ping), value = ltrIsolate(glassMs(st.pingMs)))
                GlassRow(title = stringResource(Res.string.speedtest_jitter), value = ltrIsolate(glassMs(st.jitterMs)))
                if (st.downLoadedPingMs != null) {
                    GlassRow(title = stringResource(Res.string.speedtest_loaded_down), value = ltrIsolate(glassMs(st.downLoadedPingMs)))
                }
                if (st.upLoadedPingMs != null) {
                    GlassRow(title = stringResource(Res.string.speedtest_loaded_up), value = ltrIsolate(glassMs(st.upLoadedPingMs)))
                }
            }
        }
        st.scores?.let { scores ->
            GlassSectionHeader(stringResource(Res.string.speedtest_quality))
            GlassGroup {
                QualityRow(stringResource(Res.string.speedtest_streaming), scores.streaming)
                QualityRow(stringResource(Res.string.speedtest_gaming), scores.gaming)
                QualityRow(stringResource(Res.string.speedtest_rtc), scores.rtc)
            }
        }
        st.meta?.let { meta ->
            GlassSectionHeader(stringResource(Res.string.speedtest_server))
            GlassGroup {
                GlassRow(
                    title = stringResource(Res.string.speedtest_server),
                    value = listOfNotNull(meta.colo, meta.coloCity).joinToString(" · ").ifBlank { "—" },
                )
                meta.clientIp?.let { GlassRow(title = stringResource(Res.string.map_ip), value = ltrIsolate(it)) }
                val network = listOfNotNull(meta.network, meta.asn?.let { "AS$it" }).joinToString(" · ")
                if (network.isNotBlank()) GlassRow(title = stringResource(Res.string.map_network), value = network)
            }
        }
        Spacer(Modifier.height(24.dp))
        GlassGroup {
            GlassSwitchRow(
                title = stringResource(Res.string.speedtest_save_data),
                subtitle = stringResource(Res.string.speedtest_save_data_sum, (SpeedTestEngine.SAVER_LIMIT_BYTES / 1_000_000).toString()),
                checked = saveData,
                enabled = !st.running,
                onCheckedChange = onSaveData,
            )
        }
        Spacer(Modifier.height(20.dp))
        ActionButton(st, onStart, onStop)
        GlassFooter(
            if (st.bytesUsed > 0) {
                stringResource(Res.string.speedtest_data_used, String.format(Locale.US, "%.1f", st.bytesUsed / 1_000_000.0)) +
                    " · " + ltrIsolate("${st.elapsedMs / 1000} s")
            } else {
                stringResource(Res.string.speedtest_data_full)
            },
            Modifier.padding(top = 4.dp),
        )
        if (!connected) GlassFooter(stringResource(Res.string.speedtest_not_connected))
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RouteChips(st: SpeedTestState, connected: Boolean, viaDefaultRoute: Boolean) {
    val c = Glass.colors
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Chip(
            text = stringResource(
                when {
                    viaDefaultRoute -> Res.string.speedtest_via_default_route
                    connected -> Res.string.speedtest_via_tunnel
                    else -> Res.string.speedtest_via_direct
                },
            ),
            dot = if (connected) c.green else c.orange,
        )
        val meta = st.meta
        val colo = meta?.colo
        if (colo != null) Chip(text = listOfNotNull(colo, meta.coloCity).joinToString(" · "), dot = null)
    }
}

@Composable
private fun Chip(text: String, dot: Color?) {
    val c = Glass.colors
    Row(
        Modifier
            .height(32.dp)
            .glassModule(c, GlassShapes.capsule)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot != null) Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Text(text, style = GlassType.footnote().copy(fontWeight = FontWeight.SemiBold), color = c.label, maxLines = 1)
    }
}

@Composable
private fun Gauge(st: SpeedTestState) {
    val c = Glass.colors
    val pinging = st.phase == SpeedTestPhase.Ping
    val value = when {
        st.running -> if (pinging) st.pingMs ?: 0.0 else st.liveMbps
        st.downloadMbps != null -> st.downloadMbps
        else -> 0.0
    }
    val fraction = if (pinging) {
        (value / 300.0).coerceIn(0.0, 1.0).toFloat()
    } else {
        (log10(1.0 + value) / log10(1001.0)).coerceIn(0.0, 1.0).toFloat()
    }
    val sweep by animateFloatAsState(fraction, label = "glass-gauge")
    val shown by animateFloatAsState(value.toFloat(), label = "glass-gauge-value")
    val color = when (st.phase) {
        SpeedTestPhase.Upload -> c.green
        SpeedTestPhase.Ping -> c.orange
        else -> c.tint
    }
    val samples = when (st.phase) {
        SpeedTestPhase.Upload -> st.upSamples
        else -> st.downSamples
    }.map { it.mbps.toFloat() }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .glassModule(c, GlassShapes.cell)
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(232.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 16.dp.toPx()
                val inset = stroke / 2f + 2.dp.toPx()
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                drawArc(
                    color = c.fill,
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                if (sweep > 0.002f) {
                    drawArc(
                        brush = Brush.sweepGradient(listOf(color.copy(alpha = 0.55f), color, color)),
                        startAngle = 135f,
                        sweepAngle = 270f * sweep,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(
                        when (st.phase) {
                            SpeedTestPhase.Upload -> Res.string.speedtest_upload
                            SpeedTestPhase.Ping -> Res.string.speedtest_ping
                            else -> Res.string.speedtest_download
                        },
                    ),
                    style = GlassType.subhead().copy(fontWeight = FontWeight.SemiBold),
                    color = color,
                    maxLines = 1,
                )
                Text(
                    ltrIsolate(if (pinging) shown.roundToInt().toString() else glassMbps(shown.toDouble())),
                    style = GlassType.largeTitle().copy(fontSize = 54.sp, lineHeight = 60.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.5).sp),
                    color = c.label,
                    maxLines = 1,
                    softWrap = false,
                )
                Text(if (pinging) "ms" else "Mbps", style = GlassType.headline(), color = c.label2, maxLines = 1)
                st.step?.let { step ->
                    val size = if (step.bytes >= 1_000_000) "${step.bytes / 1_000_000} MB" else "${step.bytes / 1_000} KB"
                    Text(
                        ltrIsolate("$size · ${step.done}/${step.count}"),
                        style = GlassType.footnote(),
                        color = c.label2,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        if (samples.isNotEmpty()) {
            val peak = (samples.maxOrNull() ?: 1f).coerceAtLeast(1f)
            Canvas(Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 20.dp)) {
                val shownBars = samples.takeLast(30)
                val gap = 3.dp.toPx()
                val bar = ((size.width - gap * (shownBars.size - 1)) / shownBars.size).coerceAtLeast(2f)
                shownBars.forEachIndexed { i, v ->
                    val h = (v / peak * size.height).coerceAtLeast(4.dp.toPx())
                    drawRoundRect(
                        color = if (i == shownBars.lastIndex) color else color.copy(alpha = 0.35f),
                        topLeft = Offset(i * (bar + gap), size.height - h),
                        size = Size(bar, h),
                        cornerRadius = CornerRadius(bar / 2f, bar / 2f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PhaseRow(st: SpeedTestState) {
    val c = Glass.colors
    val order = listOf(SpeedTestPhase.Ping, SpeedTestPhase.Download, SpeedTestPhase.Upload)
    val at = order.indexOf(st.phase)
    val finished = st.phase == SpeedTestPhase.Done
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        order.forEachIndexed { i, phase ->
            val done = finished || (at >= 0 && i < at)
            val now = i == at
            val label = when (phase) {
                SpeedTestPhase.Ping -> stringResource(Res.string.speedtest_ping)
                SpeedTestPhase.Download -> stringResource(Res.string.speedtest_download)
                else -> stringResource(Res.string.speedtest_upload)
            }
            Row(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .then(if (now) Modifier.clip(GlassShapes.capsule).background(c.tint) else Modifier.glassModule(c, GlassShapes.capsule)),
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (done) Icon(GlassIcons.Check, contentDescription = null, tint = c.green, modifier = Modifier.size(13.dp))
                Text(
                    label,
                    style = GlassType.footnote().copy(fontWeight = FontWeight.SemiBold),
                    color = if (now) Color.White else if (done) c.label else c.label2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ResultTile(label: String, icon: ImageVector, mbps: Double?, color: Color, modifier: Modifier) {
    val c = Glass.colors
    Column(
        modifier
            .glassModule(c, GlassShapes.cell)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(26.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
            }
            Text(label, style = GlassType.subhead().copy(fontWeight = FontWeight.SemiBold), color = c.label2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                ltrIsolate(mbps?.let(::glassMbps) ?: "—"),
                style = GlassType.largeTitle().copy(fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
                color = c.label,
                maxLines = 1,
            )
            Text("Mbps", style = GlassType.subhead(), color = c.label2, modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}

@Composable
private fun QualityRow(label: String, grade: SpeedGrade?) {
    val c = Glass.colors
    val level = grade?.ordinal ?: -1
    val color = when {
        level >= 3 -> c.green
        level == 2 -> c.orange
        level >= 0 -> c.red
        else -> c.fill
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = GlassType.body(), color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(110.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(5) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(GlassShapes.capsule)
                        .background(if (i <= level) color else c.fill),
                )
            }
        }
        Text(
            when (grade) {
                SpeedGrade.Bad -> stringResource(Res.string.speedtest_grade_bad)
                SpeedGrade.Poor -> stringResource(Res.string.speedtest_grade_poor)
                SpeedGrade.Average -> stringResource(Res.string.speedtest_grade_average)
                SpeedGrade.Good -> stringResource(Res.string.speedtest_grade_good)
                SpeedGrade.Great -> stringResource(Res.string.speedtest_grade_great)
                null -> "—"
            },
            style = GlassType.subhead().copy(fontWeight = FontWeight.SemiBold),
            color = if (level >= 0) color else c.label3,
            maxLines = 1,
            modifier = Modifier.width(64.dp),
        )
    }
}

@Composable
private fun ActionButton(st: SpeedTestState, onStart: () -> Unit, onStop: () -> Unit) {
    val c = Glass.colors
    val running = st.running
    val source = remember { MutableInteractionSource() }
    val shape = GlassShapes.capsule
    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .glassPress(source)
            .fillMaxWidth()
            .height(56.dp)
            .then(
                if (running) {
                    Modifier.glassModule(c, shape)
                } else {
                    Modifier
                        .shadow(12.dp, shape, clip = false, ambientColor = c.tint.copy(alpha = 0.5f), spotColor = c.tint.copy(alpha = 0.5f))
                        .clip(shape)
                        .background(c.tint)
                        .background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.24f), 0.52f to Color.Transparent))
                        .glassHighlight(shape, Color.White.copy(alpha = 0.55f))
                },
            )
            .clickable(source, null, role = Role.Button) { if (running) onStop() else onStart() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(
                when {
                    running -> Res.string.speedtest_stop
                    st.hasResults -> Res.string.speedtest_restart
                    else -> Res.string.speedtest_start
                },
            ),
            style = GlassType.headline(),
            color = if (running) c.red else Color.White,
        )
    }
}
