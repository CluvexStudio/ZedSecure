@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.cluvex.zedsecure.ui.speedtest

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cluvex.zedsecure.core.VpnManager
import dev.cluvex.zedsecure.data.net.SpeedTestEngine
import dev.cluvex.zedsecure.data.net.speedTestBudgetMb
import dev.cluvex.zedsecure.data.net.SpeedTestPhase
import dev.cluvex.zedsecure.data.net.SpeedSample
import dev.cluvex.zedsecure.domain.model.ConnectionState
import dev.cluvex.zedsecure.ui.components.PageHeader
import dev.cluvex.zedsecure.ui.format.ltrIsolate
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.*
import kotlin.math.roundToInt

@Composable
fun SpeedTestScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,

    unsupported: Boolean = false,
) {
    val status by VpnManager.status.collectAsStateWithLifecycle()
    val connected = status.state == ConnectionState.Connected

    val socksPort = if (connected) VpnManager.activeSocksPort else null
    val viaDefaultRoute = connected && socksPort == null

    val engine = remember(connected, socksPort) { SpeedTestEngine(socksPort) }
    val st by engine.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var job by remember(engine) { mutableStateOf<Job?>(null) }
    val running = st.running

    DisposableEffect(engine) {
        onDispose {
            engine.cancel()
            job?.cancel()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
    ) {
        val availableWidth = maxWidth
        val availableHeight = maxHeight
        val landscape = availableWidth > availableHeight

        if (unsupported) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(8.dp))
                PageHeader(
                    title = stringResource(Res.string.speedtest_title),
                    subtitle = stringResource(Res.string.speedtest_subtitle),
                )
                Spacer(Modifier.height(28.dp))
                Text(
                    text = stringResource(Res.string.speedtest_unsupported_dns),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            return@BoxWithConstraints
        }

        val action: @Composable () -> Unit = {
            ActionButton(
                running = running,
                hasResults = st.hasResults,
                onStart = { job = scope.launch { engine.run() } },
                onStop = { engine.cancel() },
            )
        }
        val footerNote: @Composable () -> Unit = {
            Text(
                text = if (st.bytesUsed > 0) {
                    stringResource(Res.string.speedtest_data_used, ltrIsolate(fmtMb(st.bytesUsed)))
                } else {
                    stringResource(Res.string.speedtest_data_note, ltrIsolate(MAX_RUN_MB.toString()))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        if (landscape) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(
                    Modifier.weight(0.44f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    RouteChip(connected, viaDefaultRoute)
                    Spacer(Modifier.height(10.dp))
                    Gauge(
                        phase = st.phase,
                        progress = st.progress,
                        centerValue = gaugeValue(st),
                        centerUnit = gaugeUnit(st.phase),
                        phaseLabel = phaseLabel(st.phase),
                        modifier = Modifier.size((availableHeight * 0.52f).coerceAtMost(availableWidth * 0.34f)),
                    )
                    Spacer(Modifier.height(12.dp))
                    action()
                    Spacer(Modifier.height(6.dp))
                    footerNote()
                }
                Column(
                    Modifier.weight(0.56f).fillMaxHeight().verticalScroll(rememberScrollState()),
                ) {
                    Spacer(Modifier.height(8.dp))
                    if (!connected) { NotConnectedNote(); Spacer(Modifier.height(10.dp)) }
                    DetailPane(st)
                    Spacer(Modifier.height(12.dp))
                }
            }
        } else {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(8.dp))
                PageHeader(
                    title = stringResource(Res.string.speedtest_title),
                    subtitle = stringResource(Res.string.speedtest_subtitle),
                )
                Spacer(Modifier.height(10.dp))
                RouteChip(connected, viaDefaultRoute)
                if (!connected) {
                    Spacer(Modifier.height(8.dp))
                    NotConnectedNote()
                }
                Spacer(Modifier.height(12.dp))

                Gauge(
                    phase = st.phase,
                    progress = st.progress,
                    centerValue = gaugeValue(st),
                    centerUnit = gaugeUnit(st.phase),
                    phaseLabel = phaseLabel(st.phase),
                    modifier = Modifier.size((availableWidth * 0.66f).coerceAtMost(availableHeight * 0.30f)),
                )

                Spacer(Modifier.height(14.dp))

                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                ) {
                    DetailPane(st)
                }

                Spacer(Modifier.height(14.dp))
                action()
                Spacer(Modifier.height(8.dp))
                footerNote()
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun RouteChip(connected: Boolean, viaDefaultRoute: Boolean) {
    val chipColor = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Surface(shape = RoundedCornerShape(50), color = chipColor.copy(alpha = 0.14f)) {
        Text(
            text = stringResource(
                when {
                    viaDefaultRoute -> Res.string.speedtest_via_default_route
                    connected -> Res.string.speedtest_via_tunnel
                    else -> Res.string.speedtest_via_direct
                },
            ),
            color = chipColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun NotConnectedNote() {
    Text(
        text = stringResource(Res.string.speedtest_not_connected),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}

@Composable
private fun ActionButton(
    running: Boolean,
    hasResults: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    if (running) {
        FilledTonalButton(onClick = onStop, modifier = Modifier.fillMaxWidth(0.72f).height(52.dp)) {
            Icon(painterResource(Res.drawable.ic_close), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.speedtest_stop), fontWeight = FontWeight.SemiBold)
        }
    } else {
        Button(
            onClick = onStart,
            colors = ButtonDefaults.buttonColors(),
            modifier = Modifier.fillMaxWidth(0.72f).height(52.dp),
        ) {
            Icon(painterResource(Res.drawable.ic_speed), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(if (hasResults) Res.string.speedtest_restart else Res.string.speedtest_start),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun DetailPane(st: dev.cluvex.zedsecure.data.net.SpeedTestState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile(stringResource(Res.string.speedtest_ping), fmt(st.pingMs), stringResource(Res.string.speedtest_ms), Modifier.weight(1f))
        StatTile(stringResource(Res.string.speedtest_jitter), fmt(st.jitterMs), stringResource(Res.string.speedtest_ms), Modifier.weight(1f))
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile(stringResource(Res.string.speedtest_download), fmt(st.downloadMbps), stringResource(Res.string.speedtest_mbps), Modifier.weight(1f))
        StatTile(stringResource(Res.string.speedtest_upload), fmt(st.uploadMbps), stringResource(Res.string.speedtest_mbps), Modifier.weight(1f))
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile(
            stringResource(Res.string.speedtest_loaded_ping),
            fmt(st.loadedPingMs),
            stringResource(Res.string.speedtest_ms),
            Modifier.weight(1f),
        )
        StatTile(
            stringResource(Res.string.speedtest_bufferbloat),
            fmt(st.bufferbloatMs),
            stringResource(Res.string.speedtest_ms),
            Modifier.weight(1f),
            accent = bufferbloatColor(st.bufferbloatMs),
        )
    }

    val trace = if (st.phase == SpeedTestPhase.Upload || st.upSamples.isNotEmpty()) st.upSamples else st.downSamples
    if (trace.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        ThroughputGraph(
            samples = trace,
            color = if (st.phase == SpeedTestPhase.Upload) MaterialTheme.colorScheme.secondary
            else MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().height(72.dp),
        )
    }
}

@Composable
private fun Gauge(
    phase: SpeedTestPhase,
    progress: Float,
    centerValue: String,
    centerUnit: String,
    phaseLabel: String,
    modifier: Modifier = Modifier,
) {
    val sweep by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(500),
        label = "gauge-sweep",
    )
    val track = MaterialTheme.colorScheme.surfaceVariant
    val arcColor by animateColorAsState(
        targetValue = when (phase) {
            SpeedTestPhase.Ping -> MaterialTheme.colorScheme.tertiary
            SpeedTestPhase.Download -> MaterialTheme.colorScheme.primary
            SpeedTestPhase.Upload -> MaterialTheme.colorScheme.secondary
            SpeedTestPhase.Done -> MaterialTheme.colorScheme.primary
            SpeedTestPhase.Stopped -> MaterialTheme.colorScheme.outline
            SpeedTestPhase.Error -> MaterialTheme.colorScheme.error
            SpeedTestPhase.Idle -> MaterialTheme.colorScheme.outline
        },
        label = "gauge-color",
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(10.dp)) {
            val stroke = Stroke(width = size.minDimension * 0.07f, cap = StrokeCap.Round)
            val startAngle = 135f
            val maxSweep = 270f
            val inset = stroke.width / 2f
            val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke.width, size.height - stroke.width)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(
                color = track,
                startAngle = startAngle,
                sweepAngle = maxSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke,
            )
            drawArc(
                color = arcColor,
                startAngle = startAngle,
                sweepAngle = maxSweep * sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                centerValue,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                centerUnit,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                phaseLabel,
                style = MaterialTheme.typography.labelLarge,
                color = arcColor,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    accent: Color? = null,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Column(Modifier.padding(vertical = 16.dp, horizontal = 16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        value,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = accent ?: MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        unit,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }
        }
    }
}

private fun gaugeValue(st: dev.cluvex.zedsecure.data.net.SpeedTestState): String = when (st.phase) {
    SpeedTestPhase.Idle -> "—"
    SpeedTestPhase.Ping -> fmt(st.pingMs)
    SpeedTestPhase.Download, SpeedTestPhase.Upload -> fmt(st.liveMbps)
    SpeedTestPhase.Done, SpeedTestPhase.Stopped -> fmt(st.downloadMbps)
    SpeedTestPhase.Error -> "!"
}

@Composable
private fun gaugeUnit(phase: SpeedTestPhase): String = when (phase) {
    SpeedTestPhase.Ping -> stringResource(Res.string.speedtest_ms)
    SpeedTestPhase.Idle, SpeedTestPhase.Error -> ""
    else -> stringResource(Res.string.speedtest_mbps)
}

@Composable
private fun phaseLabel(phase: SpeedTestPhase): String = stringResource(
    when (phase) {
        SpeedTestPhase.Idle -> Res.string.speedtest_phase_idle
        SpeedTestPhase.Ping -> Res.string.speedtest_phase_ping
        SpeedTestPhase.Download -> Res.string.speedtest_phase_download
        SpeedTestPhase.Upload -> Res.string.speedtest_phase_upload
        SpeedTestPhase.Done -> Res.string.speedtest_phase_done
        SpeedTestPhase.Stopped -> Res.string.speedtest_phase_stopped
        SpeedTestPhase.Error -> Res.string.speedtest_phase_done
    },
)

private fun fmt(v: Double?): String {
    if (v == null) return "—"
    val s = if (v >= 100) v.roundToInt().toString()
    else ((v * 10).roundToInt() / 10.0).toString()

    return ltrIsolate(s)
}

@Composable
private fun ThroughputGraph(samples: List<SpeedSample>, color: Color, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    Surface(shape = RoundedCornerShape(16.dp), color = track, modifier = modifier) {
        Canvas(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp)) {
            if (samples.size < 2) return@Canvas
            val peak = samples.maxOf { it.mbps }.coerceAtLeast(0.001)
            val dx = size.width / (samples.size - 1)
            fun px(i: Int) = i * dx
            fun py(v: Double) = size.height * (1f - (v / peak).toFloat()).coerceIn(0f, 1f)

            val line = Path().apply {
                moveTo(px(0), py(samples[0].mbps))
                for (i in 1 until samples.size) lineTo(px(i), py(samples[i].mbps))
            }

            val fill = Path().apply {
                addPath(line)
                lineTo(px(samples.size - 1), size.height)
                lineTo(px(0), size.height)
                close()
            }
            drawPath(fill, color = color.copy(alpha = 0.18f))
            drawPath(line, color = color, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun bufferbloatColor(ms: Double?): Color? = when {
    ms == null -> null
    ms < 30 -> MaterialTheme.colorScheme.primary
    ms < 100 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}

private val MAX_RUN_MB: Int = speedTestBudgetMb

private fun fmtMb(bytes: Long): String {
    val mb = bytes / 1_000_000.0
    return if (mb >= 10) mb.roundToInt().toString() else ((mb * 10).roundToInt() / 10.0).toString()
}
