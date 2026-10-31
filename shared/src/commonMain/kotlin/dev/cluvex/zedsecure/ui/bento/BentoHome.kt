@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.cluvex.zedsecure.ui.bento

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cluvex.zedsecure.core.ConnectionFault
import dev.cluvex.zedsecure.data.net.NetworkInfo
import dev.cluvex.zedsecure.domain.config.VpnProfile
import dev.cluvex.zedsecure.domain.model.ConnectionState
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.action_retry
import dev.cluvex.zedsecure.shared.resources.app_name
import dev.cluvex.zedsecure.shared.resources.bento_connected_for
import dev.cluvex.zedsecure.shared.resources.bento_current_server
import dev.cluvex.zedsecure.shared.resources.bento_data_session
import dev.cluvex.zedsecure.shared.resources.bento_fastest
import dev.cluvex.zedsecure.shared.resources.bento_no_server
import dev.cluvex.zedsecure.shared.resources.bento_no_server_hint
import dev.cluvex.zedsecure.shared.resources.copied
import dev.cluvex.zedsecure.shared.resources.fault_open_logs
import dev.cluvex.zedsecure.shared.resources.fault_unknown
import dev.cluvex.zedsecure.shared.resources.home_download
import dev.cluvex.zedsecure.shared.resources.home_local_proxy
import dev.cluvex.zedsecure.shared.resources.home_upload
import dev.cluvex.zedsecure.shared.resources.ic_add
import dev.cluvex.zedsecure.shared.resources.ic_chevron_right
import dev.cluvex.zedsecure.shared.resources.ic_description
import dev.cluvex.zedsecure.shared.resources.ic_download
import dev.cluvex.zedsecure.shared.resources.ic_fill_map
import dev.cluvex.zedsecure.shared.resources.ic_fill_speed
import dev.cluvex.zedsecure.shared.resources.ic_lock
import dev.cluvex.zedsecure.shared.resources.ic_upload
import dev.cluvex.zedsecure.shared.resources.logs_title
import dev.cluvex.zedsecure.shared.resources.map_title
import dev.cluvex.zedsecure.shared.resources.pixel_other_server
import dev.cluvex.zedsecure.shared.resources.servers_add
import dev.cluvex.zedsecure.shared.resources.state_error
import dev.cluvex.zedsecure.shared.resources.title_speedtest
import dev.cluvex.zedsecure.ui.components.NoteText
import dev.cluvex.zedsecure.ui.connection.ConnectionUiState
import dev.cluvex.zedsecure.ui.format.formatBytes
import dev.cluvex.zedsecure.ui.format.formatElapsed
import dev.cluvex.zedsecure.ui.format.formatRate
import dev.cluvex.zedsecure.ui.home.faultHint
import dev.cluvex.zedsecure.ui.home.faultTitle
import dev.cluvex.zedsecure.ui.onboarding.TourTargets
import dev.cluvex.zedsecure.ui.onboarding.tourTarget
import dev.cluvex.zedsecure.ui.platform.LocalPlatform
import dev.cluvex.zedsecure.ui.platform.appVerticalScroll
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Immutable
data class BentoActiveServer(
    val name: String,
    val detail: String?,
    val country: String?,
    val engine: String,
    val locked: Boolean,
)

@Immutable
data class BentoServerPick(
    val id: String,
    val name: String,
    val detail: String,
    val country: String?,
    val pingMs: Int?,
)

fun bentoEngineOf(profile: VpnProfile): String = when {
    profile.isSingBox || profile.isSingBoxConfig -> "sing-box"
    profile.isTor -> "Tor"
    profile.isPsiphon -> "Psiphon"
    profile.isDnsTunnel -> "DNSTT"
    profile.isMasterDns -> "MasterDNS"
    profile.isSsh -> "SSH"
    profile.isOpenConnect -> "OpenConnect"
    else -> "Xray"
}

private const val SPARK_POINTS = 24
private const val TITLE_TAPS = 5
private const val TAP_WINDOW_MS = 1_200L

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BentoHome(
    ui: ConnectionUiState,
    info: NetworkInfo,
    active: BentoActiveServer?,
    picks: List<BentoServerPick>,
    lockedNote: String?,
    localProxyAddress: String?,
    showConnectionInfo: Boolean,
    animate: Boolean,
    contentPadding: PaddingValues,
    onToggleConnection: () -> Unit,
    onBrowseConfigs: () -> Unit,
    onPickServer: (String) -> Unit,
    onAddServer: () -> Unit,
    onOpenSpeedTest: (() -> Unit)?,
    onOpenMap: (() -> Unit)?,
    onRefreshInfo: () -> Unit,
    onSecretUnlocked: () -> Unit,
    onShowLogs: () -> Unit,
    onBurst: () -> Unit,
    onStatusTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Bento.colors
    val prefs = Bento.prefs
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

    Box(modifier.fillMaxSize().background(c.ground)) {
        Column(Modifier.fillMaxSize().padding(contentPadding)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .appVerticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(BentoMetrics.gap() + 2.dp),
            ) {
                BentoTopBar(
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
                        }
                    },
                ) {
                    BentoIconButton(Res.drawable.ic_description, stringResource(Res.string.logs_title), onShowLogs)
                    MotifButton(
                        shape = MaterialShapes.Clover4Leaf,
                        container = c.lime,
                        content = c.onLime,
                        icon = Res.drawable.ic_add,
                        label = stringResource(Res.string.servers_add),
                        onClick = onAddServer,
                        onLongClick = onBurst,
                    )
                }

                Column(
                    Modifier.fillMaxWidth().widthIn(max = 640.dp).padding(horizontal = BentoMetrics.gutter).align(Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(BentoMetrics.gap()),
                ) {
                    val health = if (live) info.fault else null
                    AnimatedVisibility(visible = health != null) {
                        health?.let { BentoFaultStrip(it, onShowLogs) }
                    }
                    if (ui.state == ConnectionState.Error) {
                        BentoErrorCard(ui.error, onRetry = onToggleConnection, onOtherServer = onBrowseConfigs, onShowLogs = onShowLogs)
                    }

                    ServerCard(
                        active = active,
                        info = info,
                        live = live,
                        showInfo = showConnectionInfo,
                        onClick = onBrowseConfigs,
                        onRefreshInfo = onRefreshInfo,
                        modifier = Modifier.tourTarget(TourTargets.CONFIG_CARD),
                    )

                    if (prefs.homeSpeed) {
                        Row(
                            Modifier.fillMaxWidth().tourTarget(TourTargets.TRAFFIC),
                            horizontalArrangement = Arrangement.spacedBy(BentoMetrics.gap()),
                        ) {
                            SpeedTile(
                                label = stringResource(Res.string.home_download),
                                icon = Res.drawable.ic_download,
                                bps = if (live) ui.downloadBps else 0,
                                history = downHistory,
                                tone = c.blue,
                                live = live,
                                modifier = Modifier.weight(1f),
                            )
                            SpeedTile(
                                label = stringResource(Res.string.home_upload),
                                icon = Res.drawable.ic_upload,
                                bps = if (live) ui.uploadBps else 0,
                                history = upHistory,
                                tone = c.violet,
                                live = live,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    if (prefs.homeSession) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(BentoMetrics.gap())) {
                            FactTile(
                                label = stringResource(Res.string.bento_connected_for),
                                value = if (live) formatElapsed(ui.elapsedSeconds) else "--:--",
                                onClick = onStatusTap,
                                modifier = Modifier.weight(1f),
                            )
                            FactTile(
                                label = stringResource(Res.string.bento_data_session),
                                value = formatBytes(ui.totalDownload + ui.totalUpload),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    if (prefs.homeChips && (onOpenSpeedTest != null || onOpenMap != null)) {
                        FlowRow(
                            Modifier.fillMaxWidth().tourTarget(TourTargets.HOME_TOOLS),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (onOpenSpeedTest != null && live) {
                                ActionChip(stringResource(Res.string.title_speedtest), Res.drawable.ic_fill_speed, onOpenSpeedTest)
                            }
                            if (onOpenMap != null) {
                                ActionChip(stringResource(Res.string.map_title), Res.drawable.ic_fill_map, onOpenMap)
                            }
                        }
                    }

                    if (localProxyAddress != null && live) {
                        val platform = LocalPlatform.current
                        val copied = stringResource(Res.string.copied)
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(c.surface)
                                .clickable(role = Role.Button) {
                                    platform.copyToClipboard(localProxyAddress)
                                    platform.toast(copied)
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Text(
                                stringResource(Res.string.home_local_proxy, localProxyAddress),
                                style = BentoType.number(13.sp, FontWeight.Medium),
                                color = c.muted,
                                maxLines = 1,
                            )
                        }
                    }

                    if (lockedNote != null) {
                        val noteScroll = rememberScrollState()
                        Box(Modifier.fillMaxWidth().clip(BentoShapes.small()).background(c.surface)) {
                            NoteText(
                                text = lockedNote,
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.muted,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 140.dp)
                                    .appVerticalScroll(noteScroll)
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                    }

                    if (prefs.homeQuick && picks.isNotEmpty()) {
                        BentoCaption(stringResource(Res.string.bento_fastest))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            picks.forEachIndexed { i, pick ->
                                PickRow(pick, i, picks.size) { onPickServer(pick.id) }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            BentoConnectControl(
                state = ui.state,
                onToggle = onToggleConnection,
                animate = animate,
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .align(Alignment.CenterHorizontally)
                    .padding(start = BentoMetrics.gutter, end = BentoMetrics.gutter, top = 6.dp, bottom = 14.dp)
                    .tourTarget(TourTargets.CORE),
            )
        }
    }
}

@Composable
private fun ServerCard(
    active: BentoActiveServer?,
    info: NetworkInfo,
    live: Boolean,
    showInfo: Boolean,
    onClick: () -> Unit,
    onRefreshInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Bento.colors
    val tone = c.stageTone
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    BentoCard(
        tone = tone,
        modifier = modifier.fillMaxWidth(),
        shape = BentoShapes.card(),
        onClick = onClick,
        motif = MaterialShapes.Flower,
        motifSize = 168.dp,
        motifRotation = 16f,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Text(stringResource(Res.string.bento_current_server).uppercase(), style = BentoType.caption(), color = tone.content.copy(alpha = 0.8f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val country = (if (live && showInfo) info.countryCode else null) ?: active?.country
            BentoFlag(
                countryCode = country,
                size = 50.dp,
                corner = 17.dp,
                fallback = active?.name?.take(2)?.uppercase(),
                container = c.chipOnStage,
                content = tone.content,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    active?.name ?: stringResource(Res.string.bento_no_server),
                    style = BentoType.title(27.sp),
                    color = tone.content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    active?.detail ?: if (active == null) stringResource(Res.string.bento_no_server_hint) else "",
                    style = BentoType.body(),
                    color = tone.content.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (active?.locked == true) {
                Icon(painterResource(Res.drawable.ic_lock), contentDescription = null, tint = tone.content, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
            }
            Icon(
                painterResource(Res.drawable.ic_chevron_right),
                contentDescription = null,
                tint = tone.content,
                modifier = Modifier.size(22.dp).graphicsLayer { scaleX = if (rtl) -1f else 1f },
            )
        }
        if (active != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (live && showInfo) {
                    val ping = info.pingMs
                    Box(Modifier.clip(RoundedCornerShape(15.dp)).clickable(role = Role.Button, onClick = onRefreshInfo)) {
                        BentoPill(
                            text = ping?.let { "%d ms".format(it) } ?: "…",
                            container = c.chipOnStage,
                            content = tone.content,
                            dot = when {
                                ping == null -> tone.content.copy(alpha = 0.4f)
                                ping < 300 -> c.goodOnStage
                                ping < 800 -> c.mid
                                else -> c.bad
                            },
                            numeric = true,
                        )
                    }
                }
                BentoPill(active.engine, c.lime, c.onLime)
                val ip = if (live && showInfo) info.ipv4 ?: info.ipv6 else null
                if (ip != null) {
                    BentoPill(ip, c.chipOnStage, tone.content, numeric = true)
                }
            }
        }
    }
}

@Composable
private fun SpeedTile(
    label: String,
    icon: org.jetbrains.compose.resources.DrawableResource,
    bps: Long,
    history: List<Float>,
    tone: BentoTone,
    live: Boolean,
    modifier: Modifier = Modifier,
) {
    val (value, unit) = formatRate(bps)
    Column(
        modifier
            .height(122.dp)
            .clip(BentoShapes.block())
            .background(tone.container)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(painterResource(icon), contentDescription = null, tint = tone.content, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = tone.content, maxLines = 1)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                value,
                style = BentoType.number(36.sp),
                color = tone.content.copy(alpha = if (live) 1f else 0.6f),
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                unit,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = tone.content.copy(alpha = 0.8f),
                modifier = Modifier.alignByBaseline(),
            )
        }
        Sparkline(history, tone.content, Modifier.fillMaxWidth().height(22.dp))
    }
}

@Composable
private fun Sparkline(values: List<Float>, color: Color, modifier: Modifier) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val path = remember { Path() }
    val fill = remember { Path() }
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val max = values.max()
        val min = values.min()
        val span = (max - min).takeIf { it > 0f } ?: 1f
        val stepX = size.width / (values.size - 1)
        val pad = 3.dp.toPx()
        path.reset()
        fill.reset()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = if (max <= 0f) size.height - pad else size.height - pad - (v - min) / span * (size.height - pad * 2)
            if (i == 0) {
                path.moveTo(x, y)
                fill.moveTo(x, size.height)
                fill.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fill.lineTo(x, y)
            }
        }
        fill.lineTo(size.width, size.height)
        fill.close()
        scale(if (rtl) -1f else 1f, 1f, pivot = Offset(size.width / 2f, size.height / 2f)) {
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.18f), color.copy(alpha = 0f))))
            drawPath(path, color.copy(alpha = 0.6f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
private fun FactTile(label: String, value: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val c = Bento.colors
    Column(
        modifier
            .height(70.dp)
            .clip(BentoShapes.small())
            .background(c.surface)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = BentoType.body().copy(fontSize = 12.5.sp), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = BentoType.number(20.sp), color = c.ink, maxLines = 1)
    }
}

@Composable
private fun ActionChip(label: String, icon: org.jetbrains.compose.resources.DrawableResource, onClick: () -> Unit) {
    val c = Bento.colors
    Row(
        Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(c.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = c.ink, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = c.ink, maxLines = 1)
    }
}

@Composable
private fun PickRow(pick: BentoServerPick, index: Int, count: Int, onClick: () -> Unit) {
    val c = Bento.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = BentoMetrics.rowMin())
            .clip(BentoShapes.row(index, count))
            .background(c.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BentoFlag(pick.country, size = 38.dp, corner = 13.dp, fallback = pick.name.take(2).uppercase())
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(pick.name, style = BentoType.rowTitle(), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(pick.detail, style = BentoType.body().copy(fontSize = 12.5.sp), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        pick.pingMs?.let { ms ->
            Spacer(Modifier.width(8.dp))
            Text(if (ms > 0) "%d ms".format(ms) else "—", style = BentoType.number(13.5.sp), color = c.pingColor(ms))
        }
    }
}

@Composable
private fun BentoFaultStrip(fault: ConnectionFault, onShowLogs: () -> Unit) {
    val c = Bento.colors
    val known = fault.cause != ConnectionFault.Cause.Unknown
    Row(
        Modifier
            .fillMaxWidth()
            .clip(BentoShapes.small())
            .background(c.coral.container)
            .clickable(role = Role.Button, onClick = onShowLogs)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(if (known) faultTitle(fault.cause) else Res.string.state_error),
                style = BentoType.rowTitle(),
                color = c.coral.content,
            )
            Text(
                stringResource(if (known) faultHint(fault.cause) else Res.string.fault_unknown),
                style = BentoType.body(),
                color = c.coral.content.copy(alpha = 0.85f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(painterResource(Res.drawable.ic_description), contentDescription = stringResource(Res.string.fault_open_logs), tint = c.coral.content, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun BentoErrorCard(
    error: String?,
    onRetry: () -> Unit,
    onOtherServer: () -> Unit,
    onShowLogs: () -> Unit,
) {
    val c = Bento.colors
    val fault = error?.takeIf { it.isNotBlank() }?.let { ConnectionFault.classify(it) }
    val known = fault != null && fault.cause != ConnectionFault.Cause.Unknown
    val shake = remember { Animatable(0f) }
    var rawOpen by remember(error) { mutableStateOf(false) }
    LaunchedEffect(error) { for (x in listOf(10f, -8f, 6f, -3f, 1.5f, 0f)) shake.animateTo(x, tween(55)) }
    val density = LocalDensity.current
    BentoCard(
        tone = c.coral,
        modifier = Modifier.fillMaxWidth().graphicsLayer { translationX = shake.value * density.density },
        shape = BentoShapes.hero(),
        motif = MaterialShapes.Burst,
        motifSize = 150.dp,
        motifRotation = -12f,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(if (known) faultTitle(fault!!.cause) else Res.string.state_error),
            style = BentoType.title(24.sp),
            color = c.coral.content,
        )
        Text(
            stringResource(if (known) faultHint(fault!!.cause) else Res.string.fault_unknown),
            style = BentoType.body(),
            color = c.coral.content.copy(alpha = 0.85f),
        )
        if (fault != null && fault.raw.isNotBlank()) {
            Text(
                fault.raw,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr),
                color = c.coral.content.copy(alpha = 0.7f),
                maxLines = if (rawOpen) 10 else 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
                modifier = Modifier.clickable { rawOpen = !rawOpen },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton(stringResource(Res.string.action_retry), c.lime, c.onLime, onRetry)
            PillButton(stringResource(Res.string.pixel_other_server), Color.Transparent, c.coral.content, onOtherServer, outline = c.coral.content.copy(alpha = 0.4f))
            Spacer(Modifier.weight(1f))
            BentoIconButton(
                Res.drawable.ic_description,
                stringResource(Res.string.fault_open_logs),
                onShowLogs,
                container = Color.Transparent,
                content = c.coral.content,
                size = 40.dp,
            )
        }
    }
}

@Composable
private fun PillButton(label: String, container: Color, content: Color, onClick: () -> Unit, outline: Color? = null) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier
            .height(44.dp)
            .clip(shape)
            .background(container)
            .then(if (outline != null) Modifier.border(1.dp, outline, shape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = content, maxLines = 1)
    }
}
