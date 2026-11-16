@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package dev.cluvex.zedsecure.ui.zxps

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cluvex.zedsecure.data.net.NetworkInfo
import dev.cluvex.zedsecure.domain.model.ConnectionState
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.app_name
import dev.cluvex.zedsecure.shared.resources.bento_connected_for
import dev.cluvex.zedsecure.shared.resources.bento_current_server
import dev.cluvex.zedsecure.shared.resources.bento_data_session
import dev.cluvex.zedsecure.shared.resources.bento_fastest
import dev.cluvex.zedsecure.shared.resources.bento_no_server
import dev.cluvex.zedsecure.shared.resources.bento_tap_cancel
import dev.cluvex.zedsecure.shared.resources.bento_tap_connect
import dev.cluvex.zedsecure.shared.resources.bento_tap_disconnect
import dev.cluvex.zedsecure.shared.resources.copied
import dev.cluvex.zedsecure.shared.resources.action_retry
import dev.cluvex.zedsecure.shared.resources.fault_unknown
import dev.cluvex.zedsecure.shared.resources.home_download
import dev.cluvex.zedsecure.shared.resources.home_local_proxy
import dev.cluvex.zedsecure.shared.resources.home_upload
import dev.cluvex.zedsecure.shared.resources.ic_description
import dev.cluvex.zedsecure.shared.resources.ic_fill_power
import dev.cluvex.zedsecure.shared.resources.logs_title
import dev.cluvex.zedsecure.shared.resources.map_title
import dev.cluvex.zedsecure.shared.resources.state_connected
import dev.cluvex.zedsecure.shared.resources.state_connecting
import dev.cluvex.zedsecure.shared.resources.state_disconnecting
import dev.cluvex.zedsecure.shared.resources.state_error
import dev.cluvex.zedsecure.shared.resources.state_idle
import dev.cluvex.zedsecure.shared.resources.state_reconnecting
import dev.cluvex.zedsecure.shared.resources.title_speedtest
import dev.cluvex.zedsecure.ui.bento.BentoActiveServer
import dev.cluvex.zedsecure.ui.bento.BentoServerPick
import dev.cluvex.zedsecure.ui.components.FlagBadge
import dev.cluvex.zedsecure.ui.components.NoteText
import dev.cluvex.zedsecure.ui.connection.ConnectionUiState
import dev.cluvex.zedsecure.ui.format.formatBytes
import dev.cluvex.zedsecure.ui.format.formatElapsed
import dev.cluvex.zedsecure.ui.format.formatRate
import dev.cluvex.zedsecure.ui.format.ltrIsolate
import dev.cluvex.zedsecure.ui.onboarding.TourTargets
import dev.cluvex.zedsecure.ui.onboarding.tourTarget
import dev.cluvex.zedsecure.ui.platform.LocalPlatform
import dev.cluvex.zedsecure.ui.platform.appVerticalScroll
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val POINTS = 30
private const val TITLE_TAPS = 5
private const val BURST_TAPS = 3
private const val TAP_WINDOW_MS = 1_200L

/**
 * ZX PS home: a Pixel-style cookie hero over a Bento-style board of glass tiles.
 * Tile corners come from the chosen tile family (Soft / Squircle / Petal).
 */
@Composable
internal fun ZxPsHome(
    ui: ConnectionUiState,
    info: NetworkInfo,
    active: BentoActiveServer?,
    picks: List<BentoServerPick>,
    lockedNote: String?,
    localProxyAddress: String?,
    showConnectionInfo: Boolean,
    reduceMotion: Boolean,
    contentPadding: PaddingValues,
    onToggleConnection: () -> Unit,
    onBrowseConfigs: () -> Unit,
    onPickServer: (String) -> Unit,
    onOpenSpeedTest: (() -> Unit)?,
    onOpenMap: (() -> Unit)?,
    onRefreshInfo: () -> Unit,
    onSecretUnlocked: () -> Unit,
    onShowLogs: () -> Unit,
    onBurst: () -> Unit,
    onStatusTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ZxPs.colors
    val p = ZxPs.prefs
    val live = ui.state == ConnectionState.Connected
    val animate = !reduceMotion &&
        dev.cluvex.zedsecure.ui.theme.LocalMotionBudget.current != dev.cluvex.zedsecure.ui.theme.MotionBudget.Paused
    val haptics = LocalHapticFeedback.current
    val scroll = rememberScrollState()

    var downHistory by remember(ui.sessionId) { mutableStateOf(emptyList<Float>()) }
    var upHistory by remember(ui.sessionId) { mutableStateOf(emptyList<Float>()) }
    LaunchedEffect(ui.elapsedSeconds, live) {
        if (!live) return@LaunchedEffect
        downHistory = (downHistory + ui.downloadBps.toFloat()).takeLast(POINTS)
        upHistory = (upHistory + ui.uploadBps.toFloat()).takeLast(POINTS)
    }
    var titleTaps by remember { mutableIntStateOf(0) }
    var titleLast by remember { mutableLongStateOf(0L) }
    val intensity by animateFloatAsState(if (live) 1f else 0.6f, tween(700), label = "zxps-intensity")
    fun shape(i: Int) = zxPsTileShape(p.tile, i, p.compact)
    val gap = if (p.compact) 8.dp else 12.dp

    Box(modifier.fillMaxSize()) {
        ZxPsBackdrop(animate = animate, intensity = intensity)
        Column(Modifier.fillMaxSize().padding(contentPadding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(Res.string.app_name),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = c.ink,
                    modifier = Modifier.weight(1f).clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                    ) {
                        val now = dev.cluvex.zedsecure.platform.currentTimeMillis()
                        titleTaps = if (now - titleLast < TAP_WINDOW_MS) titleTaps + 1 else 1
                        titleLast = now
                        if (titleTaps >= TITLE_TAPS) {
                            titleTaps = 0
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSecretUnlocked()
                        } else if (titleTaps > 1) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (titleTaps == BURST_TAPS) onBurst()
                        }
                    },
                )
                Box(
                    Modifier.size(40.dp).zxPsGlass(CircleShape, p.glass, p.sheen, c.dark)
                        .clickable(role = Role.Button, onClick = onShowLogs),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(Res.drawable.ic_description), stringResource(Res.string.logs_title), tint = c.ink, modifier = Modifier.size(20.dp))
                }
            }

            Column(
                Modifier.fillMaxWidth().weight(1f).appVerticalScroll(scroll).padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                Spacer(Modifier.height(4.dp))
                Hero(ui.state, animate, onToggleConnection, Modifier.tourTarget(TourTargets.CORE))
                StatusLine(ui.state, ui.elapsedSeconds, onStatusTap)

                Column(Modifier.fillMaxWidth().widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
                    if (ui.state == ConnectionState.Error) {
                        ZxPsCard(shape(1), tint = c.danger) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(ui.error ?: stringResource(Res.string.fault_unknown), color = c.ink, style = MaterialTheme.typography.bodyMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Pill(stringResource(Res.string.action_retry), c.danger, onToggleConnection)
                                    Pill(stringResource(Res.string.logs_title), c.muted, onShowLogs)
                                }
                            }
                        }
                    }

                    ZxPsCard(
                        shape(0), tint = c.a, strong = true,
                        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onBrowseConfigs).tourTarget(TourTargets.CONFIG_CARD),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FlagBadge(countryCode = active?.country ?: info.countryCode, size = 34.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(Res.string.bento_current_server), style = MaterialTheme.typography.labelSmall, color = c.muted)
                                Text(
                                    active?.name ?: stringResource(Res.string.bento_no_server),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                val sub = listOfNotNull(active?.engine, active?.detail).joinToString(" · ")
                                if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (showConnectionInfo && live) {
                                Column(horizontalAlignment = Alignment.End) {
                                    info.ipv4?.let { Text(ltrIsolate(it), style = MaterialTheme.typography.labelMedium, color = c.b) }
                                    info.pingMs?.let { Text("$it ms", style = MaterialTheme.typography.labelMedium, color = c.good) }
                                }
                            }
                        }
                    }

                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).tourTarget(TourTargets.TRAFFIC), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        SpeedTile(stringResource(Res.string.home_download), if (live) ui.downloadBps else 0, downHistory, c.a, live, shape(2), Modifier.weight(1f).fillMaxHeight())
                        SpeedTile(stringResource(Res.string.home_upload), if (live) ui.uploadBps else 0, upHistory, c.b, live, shape(3), Modifier.weight(1f).fillMaxHeight())
                    }

                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        FactTile(stringResource(Res.string.bento_connected_for), if (live) formatElapsed(ui.elapsedSeconds) else "--:--", c.c, shape(4), Modifier.weight(1f).fillMaxHeight())
                        FactTile(stringResource(Res.string.bento_data_session), formatBytes(ui.totalDownload + ui.totalUpload), c.a, shape(5), Modifier.weight(1f).fillMaxHeight())
                    }

                    if (onOpenSpeedTest != null || onOpenMap != null) {
                        FlowRow(Modifier.fillMaxWidth().tourTarget(TourTargets.HOME_TOOLS), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (onOpenSpeedTest != null && live) Pill(stringResource(Res.string.title_speedtest), c.c, onOpenSpeedTest)
                            if (onOpenMap != null) Pill(stringResource(Res.string.map_title), c.b, onOpenMap)
                        }
                    }
                    if (localProxyAddress != null && live) {
                        val platform = LocalPlatform.current
                        val copied = stringResource(Res.string.copied)
                        Pill(stringResource(Res.string.home_local_proxy, localProxyAddress), c.muted) {
                            platform.copyToClipboard(localProxyAddress)
                            platform.toast(copied)
                        }
                    }
                    if (lockedNote != null) {
                        val noteScroll = rememberScrollState()
                        ZxPsCard(shape(6), contentPadding = PaddingValues(0.dp), modifier = Modifier.fillMaxWidth()) {
                            NoteText(
                                text = lockedNote,
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.muted,
                                modifier = Modifier.fillMaxWidth().heightIn(max = 140.dp).appVerticalScroll(noteScroll).padding(horizontal = 14.dp, vertical = 12.dp),
                            )
                        }
                    }
                    if (picks.isNotEmpty()) {
                        Text(stringResource(Res.string.bento_fastest), style = MaterialTheme.typography.labelLarge, color = c.muted, modifier = Modifier.padding(start = 6.dp, top = 4.dp))
                        picks.forEachIndexed { i, pick ->
                            ZxPsCard(shape(7 + i), modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onPickServer(pick.id) }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FlagBadge(countryCode = pick.country, size = 24.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(pick.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(pick.detail, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    pick.pingMs?.let { Text("$it ms", style = MaterialTheme.typography.labelMedium, color = c.good) }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun Hero(state: ConnectionState, animate: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val c = ZxPs.colors
    val busy = state == ConnectionState.Connecting || state == ConnectionState.Reconnecting || state == ConnectionState.Disconnecting
    val live = state == ConnectionState.Connected
    val spin = rememberInfiniteTransition(label = "zxps-hero")
    val angle by spin.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(if (busy) 3_000 else 22_000, easing = LinearEasing), RepeatMode.Restart),
        label = "zxps-hero-angle",
    )
    val breathe by spin.animateFloat(
        0.97f, 1.03f,
        infiniteRepeatable(tween(2_400), RepeatMode.Reverse),
        label = "zxps-hero-breathe",
    )
    val glow by animateFloatAsState(if (live) 1f else if (busy) 0.7f else 0.25f, tween(600), label = "zxps-hero-glow")
    val press by animateFloatAsState(1f, spring(dampingRatio = 0.5f), label = "zxps-hero-press")
    val fill = if (state == ConnectionState.Error) Brush.linearGradient(listOf(c.danger, c.c))
    else if (live) Brush.linearGradient(listOf(c.a, c.b)) else Brush.linearGradient(listOf(c.surface2, c.surface))
    val iconTint = if (live) c.onA else c.ink
    Box(modifier.size(220.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                Brush.radialGradient(listOf(c.a.copy(alpha = 0.40f * glow), c.b.copy(alpha = 0.16f * glow), Color.Transparent)),
                radius = size.minDimension / 2f,
            )
        }
        Box(
            Modifier
                .size(170.dp)
                .graphicsLayer {
                    val s = if (animate) breathe * press else press
                    scaleX = s; scaleY = s
                    rotationZ = if (animate) angle else 0f
                }
                .clip(MaterialShapes.Cookie12Sided.toShape())
                .background(fill),
        )
        Box(
            Modifier.size(128.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(Res.drawable.ic_fill_power), null, tint = iconTint, modifier = Modifier.size(54.dp))
        }
    }
}

@Composable
private fun StatusLine(state: ConnectionState, elapsed: Int, onTap: () -> Unit) {
    val c = ZxPs.colors
    val res = when (state) {
        ConnectionState.Idle -> Res.string.state_idle
        ConnectionState.Connecting -> Res.string.state_connecting
        ConnectionState.Connected -> Res.string.state_connected
        ConnectionState.Reconnecting -> Res.string.state_reconnecting
        ConnectionState.Disconnecting -> Res.string.state_disconnecting
        ConnectionState.Error -> Res.string.state_error
    }
    val hint = when (state) {
        ConnectionState.Idle, ConnectionState.Error -> Res.string.bento_tap_connect
        ConnectionState.Connected -> Res.string.bento_tap_disconnect
        else -> Res.string.bento_tap_cancel
    }
    val tone = when (state) {
        ConnectionState.Connected -> c.good
        ConnectionState.Error -> c.danger
        else -> c.ink
    }
    Column(
        Modifier.clickable(
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            indication = null,
            onClick = onTap,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(res), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = tone, textAlign = TextAlign.Center)
        Text(
            if (state == ConnectionState.Connected) formatElapsed(elapsed) else stringResource(hint),
            style = MaterialTheme.typography.bodyMedium, color = c.muted,
        )
    }
}

@Composable
private fun Pill(text: String, tone: Color, onClick: () -> Unit) {
    val p = ZxPs.prefs
    val c = ZxPs.colors
    Box(
        Modifier.zxPsGlass(CircleShape, p.glass, p.sheen, c.dark, tone).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = tone, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FactTile(label: String, value: String, tone: Color, shape: androidx.compose.ui.graphics.Shape, modifier: Modifier) {
    val c = ZxPs.colors
    ZxPsCard(shape, modifier, tint = tone) {
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp), color = c.ink, maxLines = 1)
        }
    }
}

@Composable
private fun SpeedTile(label: String, bps: Long, history: List<Float>, tone: Color, live: Boolean, shape: androidx.compose.ui.graphics.Shape, modifier: Modifier) {
    val c = ZxPs.colors
    val (num, unit) = formatRate(bps)
    ZxPsCard(shape, modifier, tint = tone) {
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = c.muted)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(num, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = if (live) tone else c.muted)
                Spacer(Modifier.width(4.dp))
                Text(unit, style = MaterialTheme.typography.labelMedium, color = c.muted, modifier = Modifier.padding(bottom = 3.dp))
            }
            Spacer(Modifier.height(8.dp))
            Sparkline(history, tone, Modifier.fillMaxWidth().height(34.dp))
        }
    }
}

@Composable
private fun Sparkline(values: List<Float>, tone: Color, modifier: Modifier) {
    Canvas(modifier) {
        if (values.size < 2) {
            drawLine(tone.copy(alpha = 0.3f), Offset(0f, size.height - 2f), Offset(size.width, size.height - 2f), 2.dp.toPx(), StrokeCap.Round)
            return@Canvas
        }
        val max = (values.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val step = size.width / (POINTS - 1)
        val x0 = size.width - step * (values.size - 1)
        fun pt(i: Int) = Offset(x0 + step * i, size.height - (values[i] / max) * (size.height - 4.dp.toPx()) - 2.dp.toPx())
        val line = Path().apply {
            moveTo(pt(0).x, pt(0).y)
            for (i in 1 until values.size) {
                val a = pt(i - 1); val b = pt(i)
                val mx = (a.x + b.x) / 2f
                cubicTo(mx, a.y, mx, b.y, b.x, b.y)
            }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(pt(values.size - 1).x, size.height)
            lineTo(pt(0).x, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(tone.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(line, tone, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
