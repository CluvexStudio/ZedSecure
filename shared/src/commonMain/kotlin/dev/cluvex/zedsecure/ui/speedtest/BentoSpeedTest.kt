@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.cluvex.zedsecure.ui.speedtest

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cluvex.zedsecure.data.net.SpeedGrade
import dev.cluvex.zedsecure.data.net.SpeedTestEngine
import dev.cluvex.zedsecure.data.net.SpeedTestPhase
import dev.cluvex.zedsecure.data.net.SpeedTestState
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.map_ip
import dev.cluvex.zedsecure.shared.resources.map_network
import dev.cluvex.zedsecure.shared.resources.speedtest_data_full
import dev.cluvex.zedsecure.shared.resources.speedtest_data_used
import dev.cluvex.zedsecure.shared.resources.speedtest_download
import dev.cluvex.zedsecure.shared.resources.speedtest_gaming
import dev.cluvex.zedsecure.shared.resources.speedtest_grade_average
import dev.cluvex.zedsecure.shared.resources.speedtest_grade_bad
import dev.cluvex.zedsecure.shared.resources.speedtest_grade_good
import dev.cluvex.zedsecure.shared.resources.speedtest_grade_great
import dev.cluvex.zedsecure.shared.resources.speedtest_grade_poor
import dev.cluvex.zedsecure.shared.resources.speedtest_jitter
import dev.cluvex.zedsecure.shared.resources.speedtest_loaded_down
import dev.cluvex.zedsecure.shared.resources.speedtest_loaded_up
import dev.cluvex.zedsecure.shared.resources.speedtest_not_connected
import dev.cluvex.zedsecure.shared.resources.speedtest_ping
import dev.cluvex.zedsecure.shared.resources.speedtest_quality
import dev.cluvex.zedsecure.shared.resources.speedtest_restart
import dev.cluvex.zedsecure.shared.resources.speedtest_rtc
import dev.cluvex.zedsecure.shared.resources.speedtest_save_data
import dev.cluvex.zedsecure.shared.resources.speedtest_save_data_sum
import dev.cluvex.zedsecure.shared.resources.speedtest_server
import dev.cluvex.zedsecure.shared.resources.speedtest_start
import dev.cluvex.zedsecure.shared.resources.speedtest_stop
import dev.cluvex.zedsecure.shared.resources.speedtest_streaming
import dev.cluvex.zedsecure.shared.resources.speedtest_steps
import dev.cluvex.zedsecure.shared.resources.speedtest_subtitle
import dev.cluvex.zedsecure.shared.resources.speedtest_title
import dev.cluvex.zedsecure.shared.resources.speedtest_unsupported_dns
import dev.cluvex.zedsecure.shared.resources.speedtest_upload
import dev.cluvex.zedsecure.shared.resources.speedtest_via_default_route
import dev.cluvex.zedsecure.shared.resources.speedtest_via_direct
import dev.cluvex.zedsecure.shared.resources.speedtest_via_tunnel
import dev.cluvex.zedsecure.ui.bento.Bento
import dev.cluvex.zedsecure.ui.bento.BentoCard
import dev.cluvex.zedsecure.ui.bento.BentoMetrics
import dev.cluvex.zedsecure.ui.bento.BentoPill
import dev.cluvex.zedsecure.ui.bento.BentoShapes
import dev.cluvex.zedsecure.ui.bento.BentoTone
import dev.cluvex.zedsecure.ui.bento.BentoType
import dev.cluvex.zedsecure.ui.format.ltrIsolate
import dev.cluvex.zedsecure.ui.platform.appVerticalScroll
import dev.cluvex.zedsecure.ui.settings.SettingsArts
import dev.cluvex.zedsecure.ui.settings.SettingsHeader
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private val STEP_SIZES = listOf(100_000L, 1_000_000L, 10_000_000L, 25_000_000L, 100_000_000L)

@Composable
internal fun BentoSpeedTestLayout(
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
    val c = Bento.colors
    val scroll = rememberScrollState()
    Column(
        modifier
            .fillMaxSize()
            .background(c.ground)
            .appVerticalScroll(scroll, contentPadding)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(BentoMetrics.gap()),
    ) {
        Spacer(Modifier.height(8.dp))
        SettingsHeader(
            title = stringResource(Res.string.speedtest_title),
            subtitle = stringResource(Res.string.speedtest_subtitle),
            art = SettingsArts.SpeedTest,
            scroll = scroll,
        )
        Column(Modifier.padding(horizontal = BentoMetrics.gutter), verticalArrangement = Arrangement.spacedBy(BentoMetrics.gap())) {
            if (unsupported) {
                Text(stringResource(Res.string.speedtest_unsupported_dns), style = BentoType.body(), color = c.muted)
                return@Column
            }
            RouteRow(st, connected, viaDefaultRoute)
            if (!connected) Text(stringResource(Res.string.speedtest_not_connected), style = BentoType.body(), color = c.muted)
            PhaseChips(st)
            LiveCard(st)
            if (st.running || st.phase == SpeedTestPhase.Done || st.phase == SpeedTestPhase.Stopped) StepTrack(st)
            if (st.hasResults) Results(st)
            st.scores?.let { Quality(it) }
            MetaCard(st)
            SaveDataRow(saveData, enabled = !st.running, onChange = onSaveData)
            ActionPill(st, onStart, onStop)
            Text(
                if (st.bytesUsed > 0) {
                    stringResource(Res.string.speedtest_data_used, mb(st.bytesUsed)) + " · " + ltrIsolate(seconds(st.elapsedMs))
                } else {
                    stringResource(Res.string.speedtest_data_full)
                },
                style = BentoType.body().copy(fontSize = 12.5.sp),
                color = c.muted,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun RouteRow(st: SpeedTestState, connected: Boolean, viaDefaultRoute: Boolean) {
    val c = Bento.colors
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        BentoPill(
            stringResource(
                when {
                    viaDefaultRoute -> Res.string.speedtest_via_default_route
                    connected -> Res.string.speedtest_via_tunnel
                    else -> Res.string.speedtest_via_direct
                },
            ),
            if (connected) c.lime else c.surface2,
            if (connected) c.onLime else c.ink,
            dot = if (connected) c.stage else c.bad,
        )
        val meta = st.meta
        val colo = meta?.colo
        if (colo != null) {
            BentoPill(listOfNotNull(colo, meta.coloCity).joinToString(" · "), c.surface2, c.ink, numeric = true)
        }
    }
}

@Composable
private fun PhaseChips(st: SpeedTestState) {
    val c = Bento.colors
    val order = listOf(SpeedTestPhase.Ping, SpeedTestPhase.Download, SpeedTestPhase.Upload)
    val at = order.indexOf(st.phase)
    val finished = st.phase == SpeedTestPhase.Done
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        order.forEachIndexed { i, phase ->
            val done = finished || (at >= 0 && i < at)
            val now = i == at
            val label = when (phase) {
                SpeedTestPhase.Ping -> stringResource(Res.string.speedtest_ping) + (st.pingMs?.let { " · " + ltrIsolate("${it.roundToInt()} ms") } ?: "")
                SpeedTestPhase.Download -> stringResource(Res.string.speedtest_download)
                else -> stringResource(Res.string.speedtest_upload)
            }
            BentoPill(
                text = (if (done) "✓ " else "") + label,
                container = when {
                    now -> c.lime
                    done -> c.surface2
                    else -> c.surface
                },
                content = when {
                    now -> c.onLime
                    done -> c.ink
                    else -> c.muted
                },
                dot = if (now) c.onLime else null,
            )
        }
    }
}

@Composable
private fun LiveCard(st: SpeedTestState) {
    val c = Bento.colors
    val tone = when (st.phase) {
        SpeedTestPhase.Download -> c.blue
        SpeedTestPhase.Upload -> c.violet
        else -> c.stageTone
    }
    val container by animateColorAsState(tone.container, label = "live-bg")
    val content by animateColorAsState(tone.content, label = "live-ink")
    val pinging = st.phase == SpeedTestPhase.Ping
    val value = when {
        st.running -> st.liveMbps
        st.downloadMbps != null -> st.downloadMbps
        else -> 0.0
    }
    val shown by animateFloatAsState(value.toFloat(), label = "live-value")
    val samples = when (st.phase) {
        SpeedTestPhase.Upload -> st.upSamples
        else -> st.downSamples
    }.map { it.mbps.toFloat() }
    BentoCard(
        tone = BentoTone(container, content),
        shape = BentoShapes.card(),
        motif = MaterialShapes.Slanted,
        motifSize = 180.dp,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(
                    when (st.phase) {
                        SpeedTestPhase.Upload -> Res.string.speedtest_upload
                        SpeedTestPhase.Ping -> Res.string.speedtest_ping
                        else -> Res.string.speedtest_download
                    },
                ).uppercase(),
                style = BentoType.caption(),
                color = content.copy(alpha = 0.85f),
                modifier = Modifier.weight(1f),
            )
            st.step?.let { step ->
                Text(ltrIsolate("${size(step.bytes)} · ${step.done}/${step.count}"), style = BentoType.number(13.sp), color = content.copy(alpha = 0.85f))
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                ltrIsolate(if (pinging) "${shown.roundToInt()}" else mbps(shown.toDouble())),
                style = BentoType.number(64.sp),
                color = content,
                maxLines = 1,
            )
            Spacer(Modifier.width(8.dp))
            Text(if (pinging) "ms" else "Mbps", style = BentoType.title(20.sp), color = content, modifier = Modifier.padding(bottom = 10.dp))
        }
        if (samples.isNotEmpty()) {
            val peak = (samples.maxOrNull() ?: 1f).coerceAtLeast(1f)
            Canvas(Modifier.fillMaxWidth().height(64.dp)) {
                val shownBars = samples.takeLast(28)
                val gap = 4.dp.toPx()
                val bar = ((size.width - gap * (shownBars.size - 1)) / shownBars.size).coerceAtLeast(2f)
                shownBars.forEachIndexed { i, v ->
                    val h = (v / peak * size.height).coerceAtLeast(6.dp.toPx())
                    drawRoundRect(
                        color = if (i == shownBars.lastIndex) c.lime else content.copy(alpha = 0.42f),
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
private fun StepTrack(st: SpeedTestState) {
    val c = Bento.colors
    val upload = st.step?.upload ?: (st.phase == SpeedTestPhase.Upload)
    val sizes = if (upload) STEP_SIZES.map { if (it == 100_000_000L) 50_000_000L else it } else STEP_SIZES
    val current = st.step?.bytes
    BentoCard(tone = c.surfaceTone, shape = BentoShapes.small(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(Res.string.speedtest_steps), style = BentoType.caption(), color = c.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            sizes.forEach { bytes ->
                val active = bytes == current
                val passed = current != null && bytes < current
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            when {
                                active -> c.lime
                                passed -> c.cyan.container
                                else -> c.surface2
                            },
                        )
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        ltrIsolate(size(bytes)),
                        style = BentoType.number(12.sp),
                        color = when {
                            active -> c.onLime
                            passed -> c.cyan.content
                            else -> c.muted
                        },
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun Results(st: SpeedTestState) {
    val c = Bento.colors
    Row(horizontalArrangement = Arrangement.spacedBy(BentoMetrics.gap())) {
        BigStat(c.blue, "↓ " + stringResource(Res.string.speedtest_download), st.downloadMbps?.let(::mbps) ?: "—", "Mbps", Modifier.weight(1f))
        BigStat(c.violet, "↑ " + stringResource(Res.string.speedtest_upload), st.uploadMbps?.let(::mbps) ?: "—", "Mbps", Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(BentoMetrics.gap())) {
        SmallStat(stringResource(Res.string.speedtest_ping), st.pingMs, Modifier.weight(1f))
        SmallStat(stringResource(Res.string.speedtest_jitter), st.jitterMs, Modifier.weight(1f))
    }
    if (st.downLoadedPingMs != null || st.upLoadedPingMs != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(BentoMetrics.gap())) {
            SmallStat(stringResource(Res.string.speedtest_loaded_down), st.downLoadedPingMs, Modifier.weight(1f))
            SmallStat(stringResource(Res.string.speedtest_loaded_up), st.upLoadedPingMs, Modifier.weight(1f))
        }
    }
}

@Composable
private fun BigStat(tone: BentoTone, caption: String, value: String, unit: String, modifier: Modifier) {
    BentoCard(tone = tone, modifier = modifier, shape = BentoShapes.small(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(caption, style = BentoType.caption(), color = tone.content.copy(alpha = 0.85f), maxLines = 1)
        Text(ltrIsolate(value), style = BentoType.number(36.sp), color = tone.content, maxLines = 1)
        Text(unit, style = BentoType.body(), color = tone.content.copy(alpha = 0.85f))
    }
}

@Composable
private fun SmallStat(caption: String, ms: Double?, modifier: Modifier) {
    val c = Bento.colors
    BentoCard(tone = c.surfaceTone, modifier = modifier, shape = BentoShapes.small(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(caption, style = BentoType.body().copy(fontSize = 12.5.sp), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(ltrIsolate(ms?.let { "%.1f ms".format(java.util.Locale.ROOT, it) } ?: "—"), style = BentoType.number(22.sp), color = c.ink, maxLines = 1)
    }
}

@Composable
private fun Quality(scores: dev.cluvex.zedsecure.data.net.SpeedScores) {
    val c = Bento.colors
    BentoCard(tone = c.stageTone, shape = BentoShapes.card(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(Res.string.speedtest_quality).uppercase(), style = BentoType.caption(), color = c.onStage.copy(alpha = 0.85f))
        listOf(
            stringResource(Res.string.speedtest_streaming) to scores.streaming,
            stringResource(Res.string.speedtest_gaming) to scores.gaming,
            stringResource(Res.string.speedtest_rtc) to scores.rtc,
        ).forEach { (label, grade) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = BentoType.body(), color = c.onStage, modifier = Modifier.width(118.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val level = grade?.ordinal ?: -1
                    repeat(5) { i ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (i <= level) c.lime else c.chipOnStage),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(gradeLabel(grade), style = BentoType.title(14.sp), color = c.lime, modifier = Modifier.width(64.dp), maxLines = 1)
            }
        }
    }
}

@Composable
private fun gradeLabel(grade: SpeedGrade?): String = when (grade) {
    SpeedGrade.Bad -> stringResource(Res.string.speedtest_grade_bad)
    SpeedGrade.Poor -> stringResource(Res.string.speedtest_grade_poor)
    SpeedGrade.Average -> stringResource(Res.string.speedtest_grade_average)
    SpeedGrade.Good -> stringResource(Res.string.speedtest_grade_good)
    SpeedGrade.Great -> stringResource(Res.string.speedtest_grade_great)
    null -> "—"
}

@Composable
private fun MetaCard(st: SpeedTestState) {
    val c = Bento.colors
    val meta = st.meta ?: return
    BentoCard(tone = c.surfaceTone, shape = BentoShapes.small(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MetaRow(stringResource(Res.string.speedtest_server), listOfNotNull(meta.colo, meta.coloCity).joinToString(" · ").ifBlank { "—" })
        meta.clientIp?.let { MetaRow(stringResource(Res.string.map_ip), it) }
        val network = listOfNotNull(meta.network, meta.asn?.let { "AS$it" }).joinToString(" · ")
        if (network.isNotBlank()) MetaRow(stringResource(Res.string.map_network), network)
    }
}

@Composable
private fun MetaRow(caption: String, value: String) {
    val c = Bento.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(caption, style = BentoType.body(), color = c.muted, modifier = Modifier.weight(0.4f))
        Text(
            ltrIsolate(value),
            style = BentoType.number(14.sp),
            color = c.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.6f),
        )
    }
}

@Composable
private fun SaveDataRow(saveData: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val c = Bento.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(BentoShapes.small())
            .background(c.surface)
            .clickable(enabled = enabled, role = Role.Switch) { onChange(!saveData) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(Res.string.speedtest_save_data), style = MaterialTheme.typography.bodyLargeEmphasized, color = c.ink)
            Text(
                stringResource(Res.string.speedtest_save_data_sum, (SpeedTestEngine.SAVER_LIMIT_BYTES / 1_000_000).toString()),
                style = BentoType.body().copy(fontSize = 12.5.sp),
                color = c.muted,
            )
        }
        Switch(
            checked = saveData,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = c.lime, checkedThumbColor = c.onLime),
        )
    }
}

@Composable
private fun ActionPill(st: SpeedTestState, onStart: () -> Unit, onStop: () -> Unit) {
    val c = Bento.colors
    val running = st.running
    Box(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(29.dp))
            .background(if (running) c.surface2 else c.lime)
            .clickable(role = Role.Button) { if (running) onStop() else onStart() },
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
            style = MaterialTheme.typography.titleMedium,
            color = if (running) c.ink else c.onLime,
        )
    }
}

private fun mbps(value: Double): String = when {
    value >= 100 -> value.roundToInt().toString()
    value >= 10 -> "%.1f".format(java.util.Locale.ROOT, value)
    else -> "%.2f".format(java.util.Locale.ROOT, value)
}

private fun size(bytes: Long): String = when {
    bytes >= 1_000_000 -> "${bytes / 1_000_000} MB"
    else -> "${bytes / 1_000} KB"
}

private fun mb(bytes: Long): String = "%.1f".format(java.util.Locale.ROOT, bytes / 1_000_000.0)

private fun seconds(ms: Long): String = "${ms / 1000} s"
