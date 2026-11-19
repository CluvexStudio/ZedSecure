package dev.cluvex.zedsecure.ui.map

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.cluvex.zedsecure.data.map.WorldMap
import dev.cluvex.zedsecure.data.net.NetworkInfo
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.*
import dev.cluvex.zedsecure.ui.bento.BentoFlag
import dev.cluvex.zedsecure.ui.format.ltrIsolate
import dev.cluvex.zedsecure.ui.glass.Glass
import dev.cluvex.zedsecure.ui.glass.GlassBadge
import dev.cluvex.zedsecure.ui.glass.GlassFooter
import dev.cluvex.zedsecure.ui.glass.GlassGroup
import dev.cluvex.zedsecure.ui.glass.GlassIcons
import dev.cluvex.zedsecure.ui.glass.GlassRow
import dev.cluvex.zedsecure.ui.glass.GlassSettingsHeader
import dev.cluvex.zedsecure.ui.glass.GlassShapes
import dev.cluvex.zedsecure.ui.glass.GlassSpinner
import dev.cluvex.zedsecure.ui.glass.GlassType
import dev.cluvex.zedsecure.ui.glass.IosColors
import dev.cluvex.zedsecure.ui.glass.glassHighlight
import dev.cluvex.zedsecure.ui.glass.glassModule
import dev.cluvex.zedsecure.ui.glass.glassPress
import dev.cluvex.zedsecure.ui.platform.appVerticalScroll
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun GlassMapLayout(
    contentPadding: PaddingValues,
    modifier: Modifier,
    countries: List<WorldMap.Country>,
    origin: MapPoint?,
    exit: MapPoint?,
    labels: List<MapLabel>,
    reduceMotion: Boolean,
    vpnOn: Boolean,
    connected: Boolean,
    info: NetworkInfo,
    originCode: String,
    originLabel: String,
    exitCode: String,
    exitLabel: String,
    savedOrigin: Boolean,
    needsVpnOff: Boolean,
    note: StringResource?,
    serverName: String?,
    onRefresh: () -> Unit,
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
        GlassSettingsHeader(stringResource(Res.string.map_title))
        Box(
            Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
                .height(300.dp)
                .glassModule(c, GlassShapes.cell),
        ) {
            WorldMapCanvas(
                countries = countries,
                origin = origin,
                exit = exit,
                accent = c.tint,
                originColor = c.label,
                landColor = c.label.copy(alpha = if (c.dark) 0.85f else 0.7f),
                reduceMotion = reduceMotion,
                labels = labels,
                modifier = Modifier.fillMaxSize(),
            )
            Row(
                Modifier.align(Alignment.TopStart).fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(Res.string.map_route).uppercase(),
                    style = GlassType.footnote().copy(fontWeight = FontWeight.SemiBold),
                    color = c.label2,
                    modifier = Modifier.weight(1f),
                )
                val ping = info.pingMs
                if (connected && ping != null) {
                    MapChip(ltrIsolate(String.format(java.util.Locale.US, "%d ms", ping)), dot = c.pingColor(ping))
                }
            }
            if (info.loading) {
                GlassSpinner(c.tint, Modifier.align(Alignment.Center))
            }
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (originLabel.isNotBlank()) MapChip(originLabel.substringBefore(','), dot = null)
                if (exitLabel.isNotBlank()) {
                    Icon(GlassIcons.ChevronRight, contentDescription = null, tint = c.tint, modifier = Modifier.size(14.dp))
                    MapChip(exitLabel.substringBefore(','), dot = c.tint)
                }
            }
        }
        GlassGroup(inset = 58.dp) {
            if (vpnOn) {
                val exitValue = exitLabel.ifBlank { stringResource(if (info.loading) Res.string.map_locating else Res.string.map_unknown) }
                InfoRow(stringResource(Res.string.map_exit), exitValue, IosColors.blue(false), GlassIcons.Pin, flag = exitCode.takeIf { it.isNotBlank() })
                info.isp?.takeIf { it.isNotBlank() }?.let { isp ->
                    InfoRow(stringResource(Res.string.map_network), isp, IosColors.teal(false), GlassIcons.Antenna)
                }
                InfoRow(stringResource(Res.string.map_ip), ltrIsolate(info.ipv4 ?: info.ipv6 ?: "—"), IosColors.indigo(false), GlassIcons.Globe)
                if (connected) {
                    InfoRow(
                        stringResource(Res.string.map_ping),
                        info.pingMs?.let { ltrIsolate(String.format(java.util.Locale.US, "%d ms", it)) } ?: "—",
                        IosColors.orange(false),
                        GlassIcons.Bolt,
                    )
                    InfoRow(stringResource(Res.string.map_server), serverName ?: "—", IosColors.gray(false), GlassIcons.Server)
                }
            }
            InfoRow(
                stringResource(if (vpnOn && savedOrigin) Res.string.map_you_saved else Res.string.map_you),
                originLabel.ifBlank { stringResource(if (vpnOn) Res.string.map_no_origin else Res.string.map_unknown) },
                IosColors.green(false),
                GlassIcons.House,
                flag = originCode.takeIf { it.isNotBlank() },
            )
        }
        if (needsVpnOff) {
            Text(
                stringResource(Res.string.map_vpn_warning),
                style = GlassType.footnote(),
                color = c.red,
                modifier = Modifier.padding(start = 36.dp, end = 36.dp, top = 8.dp),
            )
        }
        note?.let { GlassFooter(stringResource(it)) }
        Spacer(Modifier.height(20.dp))
        RefreshButton(onRefresh)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MapChip(text: String, dot: Color?) {
    val c = Glass.colors
    Row(
        Modifier
            .height(30.dp)
            .clip(GlassShapes.capsule)
            .background(c.sheet)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot != null) Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Text(text, style = GlassType.footnote().copy(fontWeight = FontWeight.SemiBold), color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun InfoRow(title: String, value: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, flag: String? = null) {
    val c = Glass.colors
    GlassRow(
        title = title,
        value = value,
        leading = { GlassBadge(color = color, icon = icon) },
        trailing = flag?.let { code -> { BentoFlag(code, size = 22.dp, corner = 11.dp, container = c.fill2, content = c.label) } },
    )
}

@Composable
private fun RefreshButton(onRefresh: () -> Unit) {
    val c = Glass.colors
    val source = remember { MutableInteractionSource() }
    val shape = GlassShapes.capsule
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .glassPress(source)
            .fillMaxWidth()
            .height(54.dp)
            .shadow(12.dp, shape, clip = false, ambientColor = c.tint.copy(alpha = 0.5f), spotColor = c.tint.copy(alpha = 0.5f))
            .clip(shape)
            .background(c.tint)
            .background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.24f), 0.52f to Color.Transparent))
            .glassHighlight(shape, Color.White.copy(alpha = 0.55f))
            .clickable(source, null, role = Role.Button, onClick = onRefresh),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(GlassIcons.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Text(stringResource(Res.string.map_refresh), style = GlassType.headline(), color = Color.White)
    }
}
