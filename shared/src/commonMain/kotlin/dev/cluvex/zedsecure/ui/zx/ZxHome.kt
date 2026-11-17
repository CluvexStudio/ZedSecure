@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package dev.cluvex.zedsecure.ui.zx

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import dev.cluvex.zedsecure.shared.resources.zx_act_cancel
import dev.cluvex.zedsecure.shared.resources.zx_act_connect
import dev.cluvex.zedsecure.shared.resources.zx_act_disconnect
import dev.cluvex.zedsecure.shared.resources.zx_act_switch
import dev.cluvex.zedsecure.shared.resources.zx_tab_nodes
import dev.cluvex.zedsecure.shared.resources.zx_tab_stats
import dev.cluvex.zedsecure.shared.resources.zx_tab_tools
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cluvex.zedsecure.data.net.NetworkInfo
import dev.cluvex.zedsecure.domain.model.ConnectionState
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.action_retry
import dev.cluvex.zedsecure.shared.resources.app_name
import dev.cluvex.zedsecure.shared.resources.bento_current_server
import dev.cluvex.zedsecure.shared.resources.bento_data_session
import dev.cluvex.zedsecure.shared.resources.bento_connected_for
import dev.cluvex.zedsecure.shared.resources.bento_fastest
import dev.cluvex.zedsecure.shared.resources.bento_no_server
import dev.cluvex.zedsecure.shared.resources.bento_no_server_hint
import dev.cluvex.zedsecure.shared.resources.bento_tap_cancel
import dev.cluvex.zedsecure.shared.resources.bento_tap_connect
import dev.cluvex.zedsecure.shared.resources.bento_tap_disconnect
import dev.cluvex.zedsecure.shared.resources.copied
import dev.cluvex.zedsecure.shared.resources.fault_open_logs
import dev.cluvex.zedsecure.shared.resources.fault_unknown
import dev.cluvex.zedsecure.shared.resources.home_download
import dev.cluvex.zedsecure.shared.resources.home_local_proxy
import dev.cluvex.zedsecure.shared.resources.home_upload
import dev.cluvex.zedsecure.shared.resources.ic_description
import dev.cluvex.zedsecure.shared.resources.ic_lock
import dev.cluvex.zedsecure.shared.resources.logs_title
import dev.cluvex.zedsecure.shared.resources.map_title
import dev.cluvex.zedsecure.shared.resources.pixel_other_server
import dev.cluvex.zedsecure.shared.resources.state_connected
import dev.cluvex.zedsecure.shared.resources.state_connecting
import dev.cluvex.zedsecure.shared.resources.state_disconnecting
import dev.cluvex.zedsecure.shared.resources.state_error
import dev.cluvex.zedsecure.shared.resources.state_idle
import dev.cluvex.zedsecure.shared.resources.state_reconnecting
import dev.cluvex.zedsecure.shared.resources.title_speedtest
import dev.cluvex.zedsecure.shared.resources.zx_node_device
import dev.cluvex.zedsecure.shared.resources.zx_node_internet
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
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val SPARK_POINTS = 28
private const val TITLE_TAPS = 5
private const val BURST_TAPS = 3
private const val TAP_WINDOW_MS = 1_200L

private enum class DeckTab { Nodes, Stats, Tools }

private class DeckCard(
    val id: String?,
    val name: String,
    val country: String?,
    val engine: String?,
    val detail: String?,
    val pingMs: Int?,
    val isActive: Boolean,
)

@Composable
internal fun ZxHome(
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
    val c = Zx.colors
    val fx = if (reduceMotion) ZxFx.Min else Zx.fx
    val live = ui.state == ConnectionState.Connected
    val haptics = LocalHapticFeedback.current
    val scroll = rememberScrollState()

    var downHistory by remember(ui.sessionId) { mutableStateOf(emptyList<Float>()) }
    var upHistory by remember(ui.sessionId) { mutableStateOf(emptyList<Float>()) }
    LaunchedEffect(ui.elapsedSeconds, live) {
        if (!live) return@LaunchedEffect
        downHistory = (downHistory + ui.downloadBps.toFloat()).takeLast(SPARK_POINTS)
        upHistory = (upHistory + ui.uploadBps.toFloat()).takeLast(SPARK_POINTS)
    }

    var titleTaps by remember { mutableIntStateOf(0) }
    var titleLast by remember { mutableLongStateOf(0L) }
    var tab by remember { mutableStateOf(DeckTab.Nodes) }
    var selected by remember { mutableIntStateOf(0) }

    val cards = remember(active, picks) {
        buildList {
            if (active != null) add(DeckCard(null, active.name, active.country, active.engine, active.detail, null, true))
            picks.filter { it.name != active?.name }.forEach {
                add(DeckCard(it.id, it.name, it.country, null, it.detail, it.pingMs, false))
            }
        }
    }
    val sel = selected.coerceIn(0, (cards.size - 1).coerceAtLeast(0))
    val accents = listOf(c.neonA, c.neonB, c.neonC)
    val accent by animateColorAsState(
        if (cards.isEmpty()) c.neonA else accents[sel % accents.size],
        tween(600),
        label = "zx-deck-accent",
    )
    val intensity by animateFloatAsState(if (live) 1f else 0.45f, tween(700), label = "zx-intensity")

    Box(modifier.fillMaxSize().background(c.ground)) {
        ZxBackdrop(fx = fx, intensity = intensity)
        // PlayStation-style dynamic background: the selected card tints the whole screen.
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    listOf(accent.copy(alpha = if (c.dark) 0.26f else 0.16f), Color.Transparent),
                    center = Offset(Float.POSITIVE_INFINITY, 0f),
                    radius = 1400f,
                ),
            ),
        )

        Column(Modifier.fillMaxSize().padding(contentPadding)) {
            ZxTopBar(
                title = stringResource(Res.string.app_name),
                onTitleClick = {
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
                onLogs = onShowLogs,
            )

            DeckTabs(tab, accent) { tab = it; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }

            Column(
                Modifier.fillMaxWidth().weight(1f).appVerticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Hero: status on the left, reactor on the right.
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        ZxStatusLeft(ui.state, ui.elapsedSeconds, fx, onStatusTap)
                    }
                    ZxCore(
                        state = ui.state,
                        fx = fx,
                        onToggle = onToggleConnection,
                        modifier = Modifier.tourTarget(TourTargets.CORE),
                        diameter = 132.dp,
                    )
                }

                if (ui.state == ConnectionState.Error) {
                    ZxErrorPanel(
                        error = ui.error,
                        onRetry = onToggleConnection,
                        onOtherServer = onBrowseConfigs,
                        onShowLogs = onShowLogs,
                    )
                }

                when (tab) {
                    DeckTab.Nodes -> {
                        if (cards.isEmpty()) {
                            ZxPanel(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp).tourTarget(TourTargets.CONFIG_CARD),
                                onClick = onBrowseConfigs,
                            ) {
                                Text(stringResource(Res.string.bento_no_server).uppercase(), style = zxNumber(16.sp), color = c.ink)
                            }
                        } else {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp)
                                    .tourTarget(TourTargets.CONFIG_CARD),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                cards.forEachIndexed { i, card ->
                                    DeckCardView(
                                        card = card,
                                        index = i,
                                        selected = i == sel,
                                        live = live && card.isActive,
                                        tone = accents[i % accents.size],
                                        onClick = {
                                            if (selected != i) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            selected = i
                                        },
                                    )
                                }
                            }
                            val card = cards[sel]
                            DeckHub(
                                card = card,
                                tone = accent,
                                live = live,
                                state = ui.state,
                                info = info,
                                showInfo = showConnectionInfo,
                                onConnect = { if (card.isActive || card.id == null) onToggleConnection() else onPickServer(card.id) },
                                onMap = onOpenMap,
                                onSpeed = if (live) onOpenSpeedTest else null,
                                onLogs = onShowLogs,
                                fx = fx,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            ZxChip(
                                text = stringResource(Res.string.bento_fastest),
                                tone = c.muted,
                                onClick = onBrowseConfigs,
                            )
                        }
                    }

                    DeckTab.Stats -> Column(
                        Modifier.padding(horizontal = 16.dp).widthIn(max = 640.dp).tourTarget(TourTargets.TRAFFIC),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (active != null) {
                            ZxChain(
                                state = ui.state,
                                deviceLabel = stringResource(Res.string.zx_node_device),
                                engine = active.engine,
                                netLabel = stringResource(Res.string.zx_node_internet),
                                fx = fx,
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ZxSpeedPanel(stringResource(Res.string.home_download), if (live) ui.downloadBps else 0, downHistory, c.neonA, live, fx != ZxFx.Min, Modifier.weight(1f))
                            ZxSpeedPanel(stringResource(Res.string.home_upload), if (live) ui.uploadBps else 0, upHistory, c.neonB, live, fx != ZxFx.Min, Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ZxFactPanel(stringResource(Res.string.bento_connected_for), if (live) formatElapsed(ui.elapsedSeconds) else "--:--", c.neonC, Modifier.weight(1f))
                            ZxFactPanel(stringResource(Res.string.bento_data_session), formatBytes(ui.totalDownload + ui.totalUpload), c.neonA, Modifier.weight(1f))
                        }
                        ZxServerPanel(
                            active = active,
                            info = info,
                            live = live,
                            showInfo = showConnectionInfo,
                            onClick = onBrowseConfigs,
                            onRefreshInfo = onRefreshInfo,
                        )
                        if (lockedNote != null) {
                            val noteScroll = rememberScrollState()
                            ZxPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                                NoteText(
                                    text = lockedNote,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = c.muted,
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 140.dp).appVerticalScroll(noteScroll)
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                )
                            }
                        }
                    }

                    DeckTab.Tools -> Column(
                        Modifier.padding(horizontal = 16.dp).widthIn(max = 640.dp).tourTarget(TourTargets.HOME_TOOLS),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val platform = LocalPlatform.current
                        val copied = stringResource(Res.string.copied)
                        ToolTile(PsGlyph.Triangle, stringResource(Res.string.map_title), c.neonB, onOpenMap != null) { onOpenMap?.invoke() }
                        ToolTile(PsGlyph.Square, stringResource(Res.string.title_speedtest), c.neonC, onOpenSpeedTest != null && live) { onOpenSpeedTest?.invoke() }
                        ToolTile(PsGlyph.Circle, stringResource(Res.string.logs_title), c.neonA, true, onShowLogs)
                        ToolTile(PsGlyph.Cross, stringResource(Res.string.bento_current_server), c.neonA, true, onBrowseConfigs)
                        if (localProxyAddress != null && live) {
                            ToolTile(PsGlyph.Circle, stringResource(Res.string.home_local_proxy, localProxyAddress), c.muted, true) {
                                platform.copyToClipboard(localProxyAddress)
                                platform.toast(copied)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(110.dp))
            }
        }
    }
}

private enum class PsGlyph { Cross, Circle, Triangle, Square }

/** The four action-button shapes, drawn as simple strokes. */
@Composable
private fun GlyphMark(glyph: PsGlyph, tone: Color, modifier: Modifier = Modifier.size(20.dp)) {
    Canvas(modifier) {
        val s = size.minDimension
        val sw = 2.2.dp.toPx()
        val m = s * 0.14f
        when (glyph) {
            PsGlyph.Cross -> {
                drawLine(tone, Offset(m, m), Offset(s - m, s - m), sw, StrokeCap.Round)
                drawLine(tone, Offset(s - m, m), Offset(m, s - m), sw, StrokeCap.Round)
            }
            PsGlyph.Circle -> drawCircle(tone, radius = s / 2f - m, style = Stroke(sw))
            PsGlyph.Square -> drawRect(tone, Offset(m, m), androidx.compose.ui.geometry.Size(s - 2 * m, s - 2 * m), style = Stroke(sw, join = StrokeJoin.Round))
            PsGlyph.Triangle -> {
                val p = Path().apply {
                    moveTo(s / 2f, m * 0.8f); lineTo(s - m, s - m); lineTo(m, s - m); close()
                }
                drawPath(p, tone, style = Stroke(sw, join = StrokeJoin.Round))
            }
        }
    }
}

@Composable
private fun DeckTabs(current: DeckTab, accent: Color, onSelect: (DeckTab) -> Unit) {
    val c = Zx.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        DeckTab.entries.forEach { t ->
            val on = t == current
            val label = stringResource(
                when (t) {
                    DeckTab.Nodes -> Res.string.zx_tab_nodes
                    DeckTab.Stats -> Res.string.zx_tab_stats
                    DeckTab.Tools -> Res.string.zx_tab_tools
                },
            )
            val size by animateFloatAsState(if (on) 17f else 13f, spring(dampingRatio = 0.6f), label = "zx-tab-size")
            val bar by animateFloatAsState(if (on) 1f else 0f, tween(260), label = "zx-tab-bar")
            Column(
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Tab,
                ) { onSelect(t) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(label.uppercase(), style = zxNumber(size.sp), color = if (on) c.ink else c.muted, maxLines = 1)
                Spacer(Modifier.height(3.dp))
                Box(Modifier.height(3.dp).fillMaxWidth(bar).background(accent))
            }
        }
    }
}

@Composable
private fun ZxStatusLeft(state: ConnectionState, elapsedSeconds: Int, fx: ZxFx, onTap: () -> Unit) {
    val c = Zx.colors
    val tone = zxStateTone(state)
    val label = stringResource(
        when (state) {
            ConnectionState.Idle -> Res.string.state_idle
            ConnectionState.Connecting -> Res.string.state_connecting
            ConnectionState.Connected -> Res.string.state_connected
            ConnectionState.Reconnecting -> Res.string.state_reconnecting
            ConnectionState.Disconnecting -> Res.string.state_disconnecting
            ConnectionState.Error -> Res.string.state_error
        },
    )
    Column(Modifier.clip(CutCornerShape(8.dp)).clickable(role = Role.Button, onClick = onTap).padding(vertical = 4.dp)) {
        Text(
            label.uppercase(),
            style = zxNumber(26.sp).copy(shadow = if (fx == ZxFx.Max && c.dark) Shadow(tone.copy(alpha = 0.9f), blurRadius = 22f) else null),
            color = tone,
            maxLines = 1,
        )
        Text(
            if (state == ConnectionState.Connected) formatElapsed(elapsedSeconds) else "--:--:--",
            style = zxNumber(15.sp, FontWeight.Medium),
            color = c.muted,
        )
    }
}

@Composable
private fun DeckCardView(
    card: DeckCard,
    index: Int,
    selected: Boolean,
    live: Boolean,
    tone: Color,
    onClick: () -> Unit,
) {
    val c = Zx.colors
    val spec = spring<androidx.compose.ui.unit.Dp>(dampingRatio = 0.7f, stiffness = 380f)
    val w by animateDpAsState(if (selected) 172.dp else 128.dp, spec, label = "zx-card-w")
    val h by animateDpAsState(if (selected) 236.dp else 188.dp, spec, label = "zx-card-h")
    val shape = CutCornerShape(topStart = 0.dp, topEnd = 22.dp, bottomEnd = 0.dp, bottomStart = 22.dp)
    val border by animateColorAsState(if (selected) tone else c.line, tween(300), label = "zx-card-border")
    Box(
        Modifier
            .width(w).height(h)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(tone.copy(alpha = if (selected) 0.30f else 0.12f), c.surface.copy(alpha = 0.92f))))
            .border(if (selected) 2.dp else 1.dp, border, shape)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Text(
            (index + 1).toString().padStart(2, '0'),
            style = zxNumber(54.sp),
            color = tone.copy(alpha = 0.14f),
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
        )
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            FlagBadge(countryCode = card.country, size = if (selected) 40.dp else 32.dp)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (live) Text("● LIVE", style = zxCaption(), color = c.good)
                Text(card.name, style = zxNumber(if (selected) 16.sp else 13.sp), color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val sub = listOfNotNull(card.engine, card.detail).joinToString(" · ")
                if (sub.isNotBlank()) Text(sub, style = zxCaption(), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                card.pingMs?.let { ms ->
                    Box(Modifier.fillMaxWidth().height(3.dp).background(c.line)) {
                        Box(Modifier.fillMaxWidth((1f - ms / 600f).coerceIn(0.08f, 1f)).height(3.dp).background(tone))
                    }
                    Text("$ms ms", style = zxCaption(), color = tone)
                }
            }
        }
    }
}

@Composable
private fun DeckHub(
    card: DeckCard,
    tone: Color,
    live: Boolean,
    state: ConnectionState,
    info: NetworkInfo,
    showInfo: Boolean,
    onConnect: () -> Unit,
    onMap: (() -> Unit)?,
    onSpeed: (() -> Unit)?,
    onLogs: () -> Unit,
    fx: ZxFx,
    modifier: Modifier = Modifier,
) {
    val c = Zx.colors
    val thisLive = live && card.isActive
    ZxPanel(modifier.fillMaxWidth(), accent = tone, glow = thisLive) {
        Text(card.name, style = zxNumber(20.sp), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val sub = listOfNotNull(card.engine, card.detail).joinToString(" · ")
        if (sub.isNotBlank()) Text(sub, style = zxCaption(), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (thisLive && showInfo) {
            Spacer(Modifier.height(6.dp))
            val line = listOfNotNull(info.ipv4?.let { ltrIsolate(it) }, info.pingMs?.let { "$it ms" }).joinToString("  ·  ")
            if (line.isNotBlank()) Text(line, style = zxNumber(13.sp, FontWeight.Medium), color = c.neonA)
        }
        Spacer(Modifier.height(12.dp))
        val connectLabel = stringResource(
            when {
                state.isTransitioning && card.isActive -> Res.string.zx_act_cancel
                thisLive -> Res.string.zx_act_disconnect
                card.isActive || card.id == null -> Res.string.zx_act_connect
                else -> Res.string.zx_act_switch
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val shape = CutCornerShape(topStart = 0.dp, topEnd = 12.dp, bottomEnd = 0.dp, bottomStart = 12.dp)
            Row(
                Modifier.weight(1f).clip(shape).background(tone).clickable(role = Role.Button, onClick = onConnect)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GlyphMark(PsGlyph.Cross, c.ground)
                Text(connectLabel.uppercase(), style = zxNumber(14.sp), color = c.ground, maxLines = 1)
            }
            MiniAction(PsGlyph.Triangle, c.neonB, onMap != null) { onMap?.invoke() }
            MiniAction(PsGlyph.Square, c.neonC, onSpeed != null) { onSpeed?.invoke() }
            MiniAction(PsGlyph.Circle, c.neonA, true, onLogs)
        }
    }
}

@Composable
private fun MiniAction(glyph: PsGlyph, tone: Color, enabled: Boolean, onClick: () -> Unit) {
    val shape = CutCornerShape(topStart = 0.dp, topEnd = 10.dp, bottomEnd = 0.dp, bottomStart = 10.dp)
    val t = if (enabled) tone else tone.copy(alpha = 0.3f)
    Box(
        Modifier.size(46.dp).clip(shape).background(t.copy(alpha = 0.10f)).border(1.dp, t, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { GlyphMark(glyph, t) }
}

@Composable
private fun ToolTile(glyph: PsGlyph, label: String, tone: Color, enabled: Boolean, onClick: () -> Unit) {
    val c = Zx.colors
    ZxPanel(
        Modifier.fillMaxWidth().then(if (enabled) Modifier else Modifier.graphicsLayer { alpha = 0.45f }),
        accent = tone,
        onClick = if (enabled) onClick else null,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            GlyphMark(glyph, tone, Modifier.size(26.dp))
            Text(label.uppercase(), style = zxNumber(15.sp), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ZxTopBar(title: String, onTitleClick: () -> Unit, onLogs: () -> Unit) {
    val c = Zx.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .clip(CutCornerShape(topStart = 0.dp, topEnd = 10.dp, bottomEnd = 0.dp, bottomStart = 10.dp))
                .clickable(role = Role.Button, onClick = onTitleClick)
                .padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            Text(
                title.uppercase(),
                style = zxNumber(22.sp).copy(shadow = if (c.dark) Shadow(c.neonA.copy(alpha = 0.8f), blurRadius = 18f) else null),
                color = c.ink,
                maxLines = 1,
            )
        }
        Spacer(Modifier.weight(1f))
        ZxIconButton(Res.drawable.ic_description, stringResource(Res.string.logs_title), onLogs)
    }
}

@Composable
private fun ZxIconButton(icon: DrawableResource, label: String, onClick: () -> Unit) {
    val c = Zx.colors
    val shape = CutCornerShape(topStart = 0.dp, topEnd = 10.dp, bottomEnd = 0.dp, bottomStart = 10.dp)
    Box(
        Modifier
            .size(42.dp)
            .clip(shape)
            .background(c.surface.copy(alpha = 0.85f))
            .border(1.dp, c.line, shape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = label, tint = c.neonA, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ZxStatus(state: ConnectionState, elapsedSeconds: Int, fx: ZxFx, onTap: () -> Unit) {
    val c = Zx.colors
    val tone = zxStateTone(state)
    val label = stringResource(
        when (state) {
            ConnectionState.Idle -> Res.string.state_idle
            ConnectionState.Connecting -> Res.string.state_connecting
            ConnectionState.Connected -> Res.string.state_connected
            ConnectionState.Reconnecting -> Res.string.state_reconnecting
            ConnectionState.Disconnecting -> Res.string.state_disconnecting
            ConnectionState.Error -> Res.string.state_error
        },
    )
    val hint = stringResource(
        when {
            state.isTransitioning -> Res.string.bento_tap_cancel
            state == ConnectionState.Connected -> Res.string.bento_tap_disconnect
            else -> Res.string.bento_tap_connect
        },
    )
    Column(
        Modifier.clip(CutCornerShape(8.dp)).clickable(role = Role.Button, onClick = onTap).padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            label.uppercase(),
            style = zxNumber(20.sp).copy(
                shadow = if (fx == ZxFx.Max && c.dark) Shadow(tone.copy(alpha = 0.9f), blurRadius = 22f) else null,
            ),
            color = tone,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            if (state == ConnectionState.Connected) formatElapsed(elapsedSeconds) else hint,
            style = zxCaption(),
            color = c.muted,
            maxLines = 1,
        )
    }
}

@Composable
private fun ZxServerPanel(
    active: BentoActiveServer?,
    info: NetworkInfo,
    live: Boolean,
    showInfo: Boolean,
    onClick: () -> Unit,
    onRefreshInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Zx.colors
    ZxPanel(modifier.fillMaxWidth(), accent = if (live) c.neonA else c.line, glow = live, onClick = onClick) {
        Text(stringResource(Res.string.bento_current_server).uppercase(), style = zxCaption(), color = c.muted)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val country = (if (live && showInfo) info.countryCode else null) ?: active?.country
            if (country != null || active == null) {
                FlagBadge(countryCode = country, size = 30.dp)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    active?.name ?: stringResource(Res.string.bento_no_server),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val detail = active?.detail ?: if (active == null) stringResource(Res.string.bento_no_server_hint) else ""
                if (detail.isNotEmpty()) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (active?.locked == true) {
                Icon(painterResource(Res.drawable.ic_lock), contentDescription = null, tint = c.neonC, modifier = Modifier.size(18.dp))
            }
        }
        if (active != null) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (live && showInfo) {
                    val ping = info.pingMs
                    val pingTone = when {
                        ping == null -> c.muted
                        ping < 300 -> c.good
                        ping < 800 -> c.neonC
                        else -> c.danger
                    }
                    Box(Modifier.clip(CutCornerShape(6.dp)).clickable(role = Role.Button, onClick = onRefreshInfo)) {
                        ZxTag(ping?.let { ltrIsolate("%d ms".format(it)) } ?: "…", pingTone)
                    }
                }
                ZxTag(active.engine, c.neonA)
                val ip = if (live && showInfo) info.ipv4 ?: info.ipv6 else null
                if (ip != null) ZxTag(ip, c.muted)
            }
        }
    }
}

@Composable
private fun ZxTag(text: String, tone: Color) {
    val shape = CutCornerShape(topStart = 0.dp, topEnd = 6.dp, bottomEnd = 0.dp, bottomStart = 6.dp)
    Box(
        Modifier
            .clip(shape)
            .background(tone.copy(alpha = 0.12f))
            .border(1.dp, tone.copy(alpha = 0.6f), shape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, style = zxCaption(), color = tone, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ZxSpeedPanel(
    label: String,
    bps: Long,
    history: List<Float>,
    tone: Color,
    live: Boolean,
    glow: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = Zx.colors
    val (value, unit) = formatRate(bps)
    ZxPanel(modifier, accent = if (live) tone else c.line, glow = live) {
        Text(label.uppercase(), style = zxCaption(), color = tone, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                value,
                style = zxNumber(28.sp).copy(shadow = if (live && glow && c.dark) Shadow(tone.copy(alpha = 0.7f), blurRadius = 14f) else null),
                color = if (live) c.ink else c.muted,
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
            Text(unit, style = zxCaption(), color = c.muted, maxLines = 1, modifier = Modifier.alignByBaseline())
        }
        Spacer(Modifier.height(8.dp))
        ZxScope(history, tone, glow, Modifier.fillMaxWidth().height(24.dp))
    }
}

@Composable
private fun ZxFactPanel(label: String, value: String, tone: Color, modifier: Modifier = Modifier) {
    val c = Zx.colors
    ZxPanel(modifier) {
        Text(label.uppercase(), style = zxCaption(), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text(value, style = zxNumber(20.sp), color = tone, maxLines = 1)
    }
}

@Composable
private fun ZxChip(text: String, tone: Color, onClick: () -> Unit, mono: Boolean = false) {
    val shape = CutCornerShape(topStart = 0.dp, topEnd = 10.dp, bottomEnd = 0.dp, bottomStart = 10.dp)
    Box(
        Modifier
            .clip(shape)
            .background(tone.copy(alpha = 0.10f))
            .border(1.dp, tone.copy(alpha = 0.7f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(
            text,
            style = if (mono) zxCaption() else MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
            color = tone,
            maxLines = 1,
        )
    }
}

@Composable
private fun ZxPickRow(pick: BentoServerPick, onClick: () -> Unit) {
    val c = Zx.colors
    ZxPanel(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FlagBadge(countryCode = pick.country, size = 22.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(pick.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(pick.detail, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val ping = pick.pingMs
            if (ping != null) {
                val tone = when {
                    ping < 300 -> c.good
                    ping < 800 -> c.neonC
                    else -> c.danger
                }
                Text(ltrIsolate("%d ms".format(ping)), style = zxCaption(), color = tone, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ZxErrorPanel(error: String?, onRetry: () -> Unit, onOtherServer: () -> Unit, onShowLogs: () -> Unit) {
    val c = Zx.colors
    ZxPanel(Modifier.fillMaxWidth(), accent = c.danger, glow = true) {
        Text(
            (error ?: stringResource(Res.string.fault_unknown)),
            style = MaterialTheme.typography.bodyMedium,
            color = c.danger,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ZxChip(stringResource(Res.string.action_retry), c.danger, onRetry)
            ZxChip(stringResource(Res.string.pixel_other_server), c.neonA, onOtherServer)
            ZxChip(stringResource(Res.string.fault_open_logs), c.muted, onShowLogs)
        }
    }
}
