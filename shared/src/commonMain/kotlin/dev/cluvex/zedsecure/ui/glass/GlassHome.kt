package dev.cluvex.zedsecure.ui.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cluvex.zedsecure.data.net.NetworkInfo
import dev.cluvex.zedsecure.domain.model.ConnectionState
import dev.cluvex.zedsecure.domain.model.GlassConnect
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.action_connect
import dev.cluvex.zedsecure.shared.resources.action_disconnect
import dev.cluvex.zedsecure.shared.resources.glass_choose_server
import dev.cluvex.zedsecure.shared.resources.glass_connection
import dev.cluvex.zedsecure.shared.resources.glass_ip_address
import dev.cluvex.zedsecure.shared.resources.glass_local_proxy
import dev.cluvex.zedsecure.shared.resources.glass_measuring
import dev.cluvex.zedsecure.shared.resources.glass_not_connected_card
import dev.cluvex.zedsecure.shared.resources.glass_slide_connect
import dev.cluvex.zedsecure.shared.resources.glass_slide_disconnect
import dev.cluvex.zedsecure.shared.resources.glass_tap_to_connect
import dev.cluvex.zedsecure.shared.resources.glass_test
import dev.cluvex.zedsecure.shared.resources.glass_testing
import dev.cluvex.zedsecure.shared.resources.glass_timeout
import dev.cluvex.zedsecure.shared.resources.glass_updated_min
import dev.cluvex.zedsecure.shared.resources.glass_updated_now
import dev.cluvex.zedsecure.shared.resources.home_download
import dev.cluvex.zedsecure.shared.resources.home_ping
import dev.cluvex.zedsecure.shared.resources.home_upload
import dev.cluvex.zedsecure.shared.resources.info_location
import dev.cluvex.zedsecure.shared.resources.map_title
import dev.cluvex.zedsecure.shared.resources.nav_logs
import dev.cluvex.zedsecure.shared.resources.state_connected
import dev.cluvex.zedsecure.shared.resources.state_connecting
import dev.cluvex.zedsecure.shared.resources.state_disconnecting
import dev.cluvex.zedsecure.shared.resources.state_error
import dev.cluvex.zedsecure.shared.resources.state_idle
import dev.cluvex.zedsecure.shared.resources.state_reconnecting
import dev.cluvex.zedsecure.shared.resources.title_speedtest
import dev.cluvex.zedsecure.ui.bento.BentoActiveServer
import dev.cluvex.zedsecure.ui.bento.BentoFlag
import dev.cluvex.zedsecure.ui.connection.ConnectionUiState
import dev.cluvex.zedsecure.ui.format.ltrIsolate
import dev.cluvex.zedsecure.ui.platform.appVerticalScroll
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

private const val SparkPoints = 24

fun glassPhaseOf(state: ConnectionState): GlassZPhase = when (state) {
    ConnectionState.Connected -> GlassZPhase.On
    ConnectionState.Connecting, ConnectionState.Reconnecting, ConnectionState.Disconnecting -> GlassZPhase.Busy
    else -> GlassZPhase.Off
}

@Composable
fun GlassHome(
    ui: ConnectionUiState,
    info: NetworkInfo,
    active: BentoActiveServer?,
    showLocation: Boolean,
    lockedNote: String?,
    localProxyAddress: String?,
    showConnectionInfo: Boolean,
    animate: Boolean,
    contentPadding: PaddingValues,
    onToggleConnection: () -> Unit,
    onBrowseConfigs: () -> Unit,
    onOpenSpeedTest: (() -> Unit)?,
    onOpenMap: (() -> Unit)?,
    onRefreshInfo: () -> Unit,
    onShowLogs: () -> Unit,
    onStatusTap: () -> Unit,
) {
    val c = Glass.colors
    val prefs = Glass.prefs
    val phase = glassPhaseOf(ui.state)
    val live = ui.state == ConnectionState.Connected
    val scroll = rememberScrollState()
    val down = remember { mutableStateListOf<Float>() }
    val up = remember { mutableStateListOf<Float>() }
    LaunchedEffect(ui.sessionId, live) {
        if (!live) {
            down.clear()
            up.clear()
        }
    }
    LaunchedEffect(ui.elapsedSeconds, live) {
        if (live) {
            down.add(ui.downloadBps.toFloat())
            up.add(ui.uploadBps.toFloat())
            while (down.size > SparkPoints) down.removeAt(0)
            while (up.size > SparkPoints) up.removeAt(0)
        }
    }

    val statusTitle = stringResource(
        when (ui.state) {
            ConnectionState.Idle -> Res.string.state_idle
            ConnectionState.Connecting -> Res.string.state_connecting
            ConnectionState.Connected -> Res.string.state_connected
            ConnectionState.Reconnecting -> Res.string.state_reconnecting
            ConnectionState.Disconnecting -> Res.string.state_disconnecting
            ConnectionState.Error -> Res.string.state_error
        },
    )

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val top = contentPadding.calculateTopPadding()
        val bottom = contentPadding.calculateBottomPadding()
        val card = prefs.homeCard && showConnectionInfo
        val rows = 2 + (if (showLocation) 1 else 0) + (if (localProxyAddress != null) 1 else 0)
        val cardSpace = if (card) 74.dp + 38.dp * rows else 0.dp
        val speedSpace = if (prefs.homeSpeed) 130.dp else 0.dp
        val fixed = top + 10.dp + 44.dp + 8.dp + 41.dp + 26.dp + cardSpace + speedSpace + bottom + 16.dp
        val hero = (maxHeight - fixed).coerceIn(206.dp, 300.dp)

        Column(
            Modifier
                .fillMaxSize()
                .appVerticalScroll(scroll, contentPadding)
                .padding(top = top + 10.dp, bottom = bottom + 16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassCircleButton(GlassIcons.Doc, stringResource(Res.string.nav_logs), onShowLogs)
                Spacer(Modifier.weight(1f))
                val mapLabel = stringResource(Res.string.map_title)
                val speedLabel = stringResource(Res.string.title_speedtest)
                val tools = buildList {
                    if (onOpenMap != null) add(GlassToolItem(GlassIcons.Map, mapLabel, onOpenMap))
                    if (onOpenSpeedTest != null) add(GlassToolItem(GlassIcons.Speed, speedLabel, onOpenSpeedTest))
                }
                if (tools.isNotEmpty()) GlassToolGroup(tools)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                statusTitle,
                style = GlassType.largeTitle(),
                color = c.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .clickable(remember { MutableInteractionSource() }, null, onClick = onStatusTap),
            )
            ServerLine(active, showLocation, onBrowseConfigs)
            if (!lockedNote.isNullOrBlank()) {
                Text(
                    lockedNote,
                    style = GlassType.footnote(),
                    color = c.label2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                )
            }

            Hero(
                phase = phase,
                ui = ui,
                height = hero,
                animate = animate,
                onToggle = onToggleConnection,
            )

            if (card) {
                ConnectionCard(
                    live = live,
                    info = info,
                    showLocation = showLocation,
                    localProxyAddress = localProxyAddress,
                    animate = animate,
                    onTest = onRefreshInfo,
                )
                Spacer(Modifier.height(12.dp))
            }
            if (prefs.homeSpeed) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SpeedModule(
                        label = stringResource(Res.string.home_download),
                        icon = GlassIcons.Down,
                        bps = if (live) ui.downloadBps else 0L,
                        history = down,
                        color = c.tint,
                        modifier = Modifier.weight(1f),
                    )
                    SpeedModule(
                        label = stringResource(Res.string.home_upload),
                        icon = GlassIcons.Up,
                        bps = if (live) ui.uploadBps else 0L,
                        history = up,
                        color = c.green,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerLine(active: BentoActiveServer?, showLocation: Boolean, onBrowse: () -> Unit) {
    val c = Glass.colors
    Row(
        Modifier
            .padding(horizontal = 20.dp)
            .padding(top = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onBrowse)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (active == null) {
            Text(stringResource(Res.string.glass_choose_server), style = GlassType.subhead().copy(fontWeight = FontWeight.SemiBold), color = c.tint, maxLines = 1)
        } else {
            if (showLocation && active.country != null) {
                BentoFlag(active.country, size = 17.dp, corner = 9.dp, container = c.fill2, content = c.label)
            }
            Text(
                active.name,
                style = GlassType.subhead().copy(fontWeight = FontWeight.SemiBold),
                color = c.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            val detail = active.detail?.takeIf { it.isNotBlank() }
            if (detail != null) {
                Text(
                    "· $detail",
                    style = GlassType.subhead(),
                    color = c.label2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
        Icon(GlassIcons.ChevronRight, contentDescription = null, tint = c.label2, modifier = Modifier.size(11.dp))
    }
}

@Composable
private fun Hero(
    phase: GlassZPhase,
    ui: ConnectionUiState,
    height: Dp,
    animate: Boolean,
    onToggle: () -> Unit,
) {
    val c = Glass.colors
    val prefs = Glass.prefs
    val on = phase == GlassZPhase.On
    val timer = ltrIsolate(glassElapsed(ui.elapsedSeconds))
    val connectLabel = stringResource(Res.string.action_connect)
    val disconnectLabel = stringResource(Res.string.action_disconnect)
    val connectingLabel = stringResource(Res.string.state_connecting)
    Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
        when (prefs.connect) {
            GlassConnect.Z -> {
                val zWidth = (height - 70.dp).coerceIn(140.dp, 176.dp)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GlassZControl(
                        phase = phase,
                        onClick = onToggle,
                        label = if (on) disconnectLabel else connectLabel,
                        animate = animate,
                        width = zWidth,
                    )
                    Box(Modifier.height(24.dp), contentAlignment = Alignment.Center) {
                        when {
                            on && prefs.homeTimer -> Text(
                                timer,
                                style = GlassType.headline().copy(fontWeight = FontWeight.Medium, letterSpacing = 2.5.sp),
                                color = c.label,
                                maxLines = 1,
                            )
                            on -> Unit
                            phase == GlassZPhase.Busy -> Text(connectingLabel, style = GlassType.footnote(), color = c.label2, maxLines = 1)
                            else -> Text(stringResource(Res.string.glass_tap_to_connect), style = GlassType.footnote(), color = c.label2, maxLines = 1)
                        }
                    }
                }
            }
            else -> {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    BoxWithConstraints(
                        Modifier.weight(1f).fillMaxWidth().clipToBounds(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (on && prefs.homeTimer) {
                            HeroDigits(timer, ui, maxHeight)
                        }
                    }
                    when (prefs.connect) {
                        GlassConnect.Button -> GlassConnectButton(
                            phase = phase,
                            connectLabel = connectLabel,
                            connectingLabel = connectingLabel,
                            disconnectLabel = disconnectLabel,
                            animate = animate,
                            onClick = onToggle,
                        )
                        GlassConnect.Switch -> GlassVpnCard(
                            phase = phase,
                            title = "VPN",
                            subtitle = when (phase) {
                                GlassZPhase.On -> stringResource(Res.string.state_connected) + " · " + timer
                                GlassZPhase.Busy -> connectingLabel
                                GlassZPhase.Off -> stringResource(Res.string.state_idle)
                            },
                            onToggle = onToggle,
                        )
                        else -> GlassSlideControl(
                            phase = phase,
                            text = when (phase) {
                                GlassZPhase.On -> stringResource(Res.string.glass_slide_disconnect)
                                GlassZPhase.Busy -> connectingLabel
                                GlassZPhase.Off -> stringResource(Res.string.glass_slide_connect)
                            },
                            label = if (on) disconnectLabel else connectLabel,
                            animate = animate && phase != GlassZPhase.Busy,
                            onToggle = onToggle,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun HeroDigits(timer: String, ui: ConnectionUiState, room: Dp) {
    val c = Glass.colors
    val density = androidx.compose.ui.platform.LocalDensity.current
    val showSpeeds = room > 110.dp
    val digitsRoom = if (showSpeeds) room - 28.dp else room
    val size = (digitsRoom.value * 0.62f / density.fontScale).coerceIn(26f, 72f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            timer,
            style = GlassType.largeTitle().copy(
                fontSize = size.sp,
                lineHeight = (size * 1.1f).sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = (-size / 36f).sp,
            ),
            color = c.label,
            maxLines = 1,
            softWrap = false,
        )
        if (showSpeeds) {
            val (d, du) = glassRate(ui.downloadBps)
            val (u, uu) = glassRate(ui.uploadBps)
            Text(
                ltrIsolate("↓ $d $du   ↑ $u $uu"),
                style = GlassType.subhead(),
                color = c.label2,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun ConnectionCard(
    live: Boolean,
    info: NetworkInfo,
    showLocation: Boolean,
    localProxyAddress: String?,
    animate: Boolean,
    onTest: () -> Unit,
) {
    val c = Glass.colors
    val testing = live && (info.loading || info.pingLoading || info.placeLoading)
    var updatedAt by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(testing, info.ipv4, info.pingMs) {
        if (!testing && live && (info.ipv4 != null || info.pingMs != null)) updatedAt = System.currentTimeMillis()
    }
    LaunchedEffect(live) {
        if (!live) updatedAt = 0L
        while (live) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }
    val updated = when {
        testing -> stringResource(Res.string.glass_measuring)
        !live -> stringResource(Res.string.glass_not_connected_card)
        updatedAt == 0L -> ""
        now - updatedAt < 60_000 -> stringResource(Res.string.glass_updated_now)
        else -> stringResource(Res.string.glass_updated_min, ((now - updatedAt) / 60_000).toInt())
    }
    val spin = if (testing && animate) {
        rememberInfiniteTransition(label = "glass-test").animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "glass-test-spin")
    } else {
        null
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .glassModule(c, GlassShapes.cell)
            .padding(top = 10.dp, bottom = 6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(start = 16.dp, end = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(GlassIcons.Shield, contentDescription = null, tint = c.label2, modifier = Modifier.size(13.dp))
                    Text(
                        stringResource(Res.string.glass_connection).uppercase(),
                        style = GlassType.footnote().copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
                        color = c.label2,
                        maxLines = 1,
                    )
                }
                if (updated.isNotEmpty()) {
                    Text(updated, style = GlassType.caption(), color = c.label2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            GlassCapsuleButton(
                text = stringResource(if (testing) Res.string.glass_testing else Res.string.glass_test),
                onClick = onTest,
                icon = GlassIcons.Refresh,
                iconModifier = if (spin != null) Modifier.graphicsLayer { rotationZ = spin.value } else Modifier,
                enabled = live && !testing,
            )
        }
        val ip = info.ipv4 ?: info.ipv6
        CardLine(
            label = stringResource(Res.string.glass_ip_address),
            value = if (live) ip?.let { ltrIsolate(it) } else null,
            loading = testing && ip == null || (live && info.loading),
            animate = animate,
            divider = true,
        )
        if (showLocation) {
            val place = listOfNotNull(info.city?.takeIf { it.isNotBlank() }, info.country?.takeIf { it.isNotBlank() }).joinToString(", ")
            CardLine(
                label = stringResource(Res.string.info_location),
                value = if (live) place.ifBlank { null } else null,
                flag = if (live) info.countryCode else null,
                loading = live && (info.placeLoading || info.loading),
                animate = animate,
                divider = true,
            )
        }
        PingLine(
            live = live,
            ms = info.pingMs,
            failed = live && info.fault != null,
            loading = live && info.pingLoading,
            animate = animate,
            divider = localProxyAddress != null,
        )
        if (localProxyAddress != null) {
            CardLine(
                label = stringResource(Res.string.glass_local_proxy),
                value = ltrIsolate(localProxyAddress),
                loading = false,
                animate = animate,
                divider = false,
            )
        }
    }
}

@Composable
private fun CardLine(
    label: String,
    value: String?,
    loading: Boolean,
    animate: Boolean,
    divider: Boolean,
    flag: String? = null,
) {
    val c = Glass.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(38.dp)
            .then(if (divider) Modifier.hairline(c.separator) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = GlassType.subhead(), color = c.label2, maxLines = 1)
        Spacer(Modifier.weight(1f))
        when {
            loading -> Skeleton(animate)
            value == null -> Text("—", style = GlassType.subhead().copy(fontWeight = FontWeight.SemiBold), color = c.label3)
            else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                if (flag != null) BentoFlag(flag, size = 16.dp, corner = 8.dp, container = c.fill2, content = c.label)
                Text(
                    value,
                    style = GlassType.subhead().copy(fontWeight = FontWeight.SemiBold),
                    color = c.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PingLine(live: Boolean, ms: Int?, failed: Boolean, loading: Boolean, animate: Boolean, divider: Boolean) {
    val c = Glass.colors
    val value = when {
        !live -> null
        failed -> -1
        else -> ms
    }
    val quality = when {
        value == null -> 0
        value <= 0 -> 0
        value < 110 -> 4
        value < 200 -> 3
        value < 400 -> 2
        else -> 1
    }
    val good = when {
        quality >= 3 -> c.green
        quality == 2 -> c.orange
        else -> c.red
    }
    val off = if (c.dark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.12f)
    Row(
        Modifier
            .fillMaxWidth()
            .height(38.dp)
            .then(if (divider) Modifier.hairline(c.separator) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(Res.string.home_ping), style = GlassType.subhead(), color = c.label2, maxLines = 1)
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(14.dp)) {
            listOf(5.dp, 8.dp, 11.dp, 14.dp).forEachIndexed { i, h ->
                val color by animateColorAsState(if (!loading && quality > i) good else off, label = "glass-bar-$i")
                Box(Modifier.width(3.5.dp).height(h).clip(RoundedCornerShape(1.5.dp)).background(color))
            }
        }
        when {
            loading -> Skeleton(animate, width = 54.dp)
            value == null -> Text("—", style = GlassType.subhead().copy(fontWeight = FontWeight.SemiBold), color = c.label3)
            value <= 0 -> Text(stringResource(Res.string.glass_timeout), style = GlassType.subhead().copy(fontWeight = FontWeight.SemiBold), color = c.red, maxLines = 1)
            else -> Text(ltrIsolate(String.format(java.util.Locale.US, "%d ms", value)), style = GlassType.subhead().copy(fontWeight = FontWeight.SemiBold), color = c.pingColor(value), maxLines = 1)
        }
    }
}

@Composable
private fun Skeleton(animate: Boolean, width: Dp = 96.dp) {
    val c = Glass.colors
    val shift = if (animate) {
        rememberInfiniteTransition(label = "glass-skeleton").animateFloat(-1f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "glass-skeleton-x")
    } else {
        null
    }
    Box(
        Modifier
            .width(width)
            .height(14.dp)
            .clip(RoundedCornerShape(6.dp))
            .drawBehind {
                val x = (shift?.value ?: 0f) * size.width
                drawRect(
                    Brush.horizontalGradient(
                        0f to c.fill2,
                        0.5f to c.fill,
                        1f to c.fill2,
                        startX = x - size.width,
                        endX = x + size.width,
                    ),
                )
            },
    )
}

private fun Modifier.hairline(color: Color): Modifier = drawBehind {
    val stroke = maxOf(1f, 0.5.dp.toPx())
    val inset = 16.dp.toPx()
    drawRect(color, topLeft = Offset(inset, size.height - stroke), size = androidx.compose.ui.geometry.Size(size.width - inset * 2, stroke))
}

@Composable
private fun SpeedModule(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    bps: Long,
    history: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val c = Glass.colors
    val (value, unit) = glassRate(bps)
    Box(
        modifier
            .height(118.dp)
            .glassModule(c, GlassShapes.cell),
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .align(Alignment.BottomCenter),
        ) {
            val points = history
            val peak = (points.maxOrNull() ?: 0f).coerceAtLeast(1f)
            val n = if (points.size < 2) 12 else points.size
            val line = Path()
            val top = 6.dp.toPx()
            val usable = size.height - top
            for (i in 0 until n) {
                val v = if (points.size < 2) 0.04f else (points[i] / peak).coerceIn(0f, 1f) * 0.92f + 0.04f
                val x = size.width * i / (n - 1).coerceAtLeast(1)
                val y = top + usable - v * usable
                if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
            }
            val area = Path().apply {
                addPath(line)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.3f), color.copy(alpha = 0f))))
            drawPath(line, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Column(Modifier.fillMaxSize().padding(start = 14.dp, end = 14.dp, top = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                }
                Text(
                    label,
                    style = GlassType.subhead().copy(fontWeight = FontWeight.SemiBold),
                    color = c.label2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    ltrIsolate(value),
                    style = GlassType.largeTitle().copy(fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
                    color = c.label,
                    maxLines = 1,
                )
                Text(unit, style = GlassType.subhead(), color = c.label2, maxLines = 1, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}

fun glassRate(bytesPerSecond: Long): Pair<String, String> {
    val us = java.util.Locale.US
    return when {
        bytesPerSecond < 1_024 -> String.format(us, "%d", bytesPerSecond.coerceAtLeast(0)) to "B/s"
        bytesPerSecond < 1_048_576 -> {
            val kb = bytesPerSecond / 1024.0
            (if (kb < 10) String.format(us, "%.1f", kb) else String.format(us, "%d", kb.toInt())) to "KB/s"
        }
        bytesPerSecond < 1_073_741_824 -> String.format(us, "%.1f", bytesPerSecond / 1_048_576.0) to "MB/s"
        else -> String.format(us, "%.2f", bytesPerSecond / 1_073_741_824.0) to "GB/s"
    }
}

fun glassElapsed(totalSeconds: Int): String {
    val us = java.util.Locale.US
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val sec = totalSeconds % 60
    return if (h > 0) String.format(us, "%d:%02d:%02d", h, m, sec) else String.format(us, "%02d:%02d", m, sec)
}
