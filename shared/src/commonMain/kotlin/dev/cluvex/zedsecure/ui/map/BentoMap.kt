@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.cluvex.zedsecure.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cluvex.zedsecure.data.map.WorldMap
import dev.cluvex.zedsecure.data.net.NetworkInfo
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.ic_chevron_right
import dev.cluvex.zedsecure.shared.resources.ic_fill_public
import dev.cluvex.zedsecure.shared.resources.ic_restart_alt
import dev.cluvex.zedsecure.shared.resources.ipd_open
import dev.cluvex.zedsecure.shared.resources.ipd_open_sum
import dev.cluvex.zedsecure.shared.resources.map_exit
import dev.cluvex.zedsecure.shared.resources.map_ip
import dev.cluvex.zedsecure.shared.resources.map_locating
import dev.cluvex.zedsecure.shared.resources.map_network
import dev.cluvex.zedsecure.shared.resources.map_no_origin
import dev.cluvex.zedsecure.shared.resources.map_offline_here
import dev.cluvex.zedsecure.shared.resources.map_other_vpn
import dev.cluvex.zedsecure.shared.resources.map_ping
import dev.cluvex.zedsecure.shared.resources.map_refresh
import dev.cluvex.zedsecure.shared.resources.map_route
import dev.cluvex.zedsecure.shared.resources.map_server
import dev.cluvex.zedsecure.shared.resources.map_subtitle
import dev.cluvex.zedsecure.shared.resources.map_title
import dev.cluvex.zedsecure.shared.resources.map_unknown
import dev.cluvex.zedsecure.shared.resources.map_vpn_warning
import dev.cluvex.zedsecure.shared.resources.map_you
import dev.cluvex.zedsecure.shared.resources.map_you_saved
import dev.cluvex.zedsecure.ui.bento.Bento
import dev.cluvex.zedsecure.ui.bento.BentoCard
import dev.cluvex.zedsecure.ui.bento.BentoFlag
import dev.cluvex.zedsecure.ui.bento.BentoMetrics
import dev.cluvex.zedsecure.ui.bento.BentoPill
import dev.cluvex.zedsecure.ui.bento.BentoShapes
import dev.cluvex.zedsecure.ui.bento.BentoTone
import dev.cluvex.zedsecure.ui.bento.BentoType
import dev.cluvex.zedsecure.ui.format.ltrIsolate
import dev.cluvex.zedsecure.ui.platform.appVerticalScroll
import dev.cluvex.zedsecure.ui.settings.SettingsArts
import dev.cluvex.zedsecure.ui.settings.SettingsHeader
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun BentoMapLayout(
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
    note: org.jetbrains.compose.resources.StringResource?,
    serverName: String?,
    onRefresh: () -> Unit,
    onDetails: () -> Unit,
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
            title = stringResource(Res.string.map_title),
            subtitle = stringResource(Res.string.map_subtitle),
            art = SettingsArts.Map,
            scroll = scroll,
        )

        Box(
            Modifier
                .padding(horizontal = BentoMetrics.gutter)
                .fillMaxWidth()
                .height(300.dp)
                .clip(BentoShapes.card())
                .background(c.stage),
        ) {
            WorldMapCanvas(
                countries = countries,
                origin = origin,
                exit = exit,
                accent = c.lime,
                originColor = c.onStage,
                landColor = c.onStage,
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
                    style = BentoType.caption(),
                    color = c.onStage.copy(alpha = 0.85f),
                    modifier = Modifier.weight(1f),
                )
                val ping = info.pingMs
                if (connected && ping != null) {
                    BentoPill("$ping ms", c.chipOnStage, c.onStage, dot = if (ping < 300) c.goodOnStage else c.mid, numeric = true)
                }
            }
            if (info.loading) {
                LoadingIndicator(Modifier.align(Alignment.Center).size(56.dp), color = c.lime)
            }
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (originLabel.isNotBlank()) {
                    BentoPill(originLabel.substringBefore(','), c.chipOnStage, c.onStage)
                }
                if (exitLabel.isNotBlank()) {
                    Text("→", style = BentoType.title(18.sp), color = c.lime)
                    BentoPill(exitLabel.substringBefore(','), c.lime, c.onLime)
                }
            }
        }

        Column(
            Modifier.padding(horizontal = BentoMetrics.gutter),
            verticalArrangement = Arrangement.spacedBy(BentoMetrics.gap()),
        ) {
            if (vpnOn) {
                val exitValue = exitLabel.ifBlank { stringResource(if (info.loading) Res.string.map_locating else Res.string.map_unknown) }
                val exitFlag = exitCode.takeIf { it.isNotBlank() }
                val isp = info.isp?.takeIf { it.isNotBlank() }
                if (isp != null) {
                    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(BentoMetrics.gap())) {
                        FactTile(
                            tone = c.blue,
                            caption = stringResource(Res.string.map_exit),
                            value = exitValue,
                            flag = exitFlag,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        FactTile(
                            tone = c.cyan,
                            caption = stringResource(Res.string.map_network),
                            value = isp,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                } else {
                    FactTile(
                        tone = c.blue,
                        caption = stringResource(Res.string.map_exit),
                        value = exitValue,
                        flag = exitFlag,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                FactTile(
                    tone = c.violet,
                    caption = stringResource(Res.string.map_ip),
                    value = info.ipv4 ?: info.ipv6 ?: "—",
                    numeric = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (connected) {
                    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(BentoMetrics.gap())) {
                        FactTile(
                            tone = c.coral,
                            caption = stringResource(Res.string.map_ping),
                            value = info.pingMs?.let { "$it ms" } ?: "—",
                            numeric = true,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        FactTile(
                            tone = c.surfaceTone,
                            caption = stringResource(Res.string.map_server),
                            value = serverName ?: "—",
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
            FactTile(
                tone = c.surfaceTone,
                caption = stringResource(if (vpnOn && savedOrigin) Res.string.map_you_saved else Res.string.map_you),
                value = originLabel.ifBlank { stringResource(if (vpnOn) Res.string.map_no_origin else Res.string.map_unknown) },
                flag = originCode.takeIf { it.isNotBlank() },
                modifier = Modifier.fillMaxWidth(),
            )
            if (needsVpnOff) {
                Text(stringResource(Res.string.map_vpn_warning), style = BentoType.body(), color = c.danger, modifier = Modifier.padding(horizontal = 6.dp))
            }
            note?.let {
                Text(stringResource(it), style = BentoType.body(), color = c.muted, modifier = Modifier.padding(horizontal = 6.dp))
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(BentoShapes.small())
                    .background(c.surface)
                    .clickable(role = Role.Button, onClick = onDetails)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(c.violet.container),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(Res.drawable.ic_fill_public), contentDescription = null, tint = c.violet.content, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(stringResource(Res.string.ipd_open), style = BentoType.rowTitle(), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(Res.string.ipd_open_sum), style = BentoType.body(), color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                Icon(painterResource(Res.drawable.ic_chevron_right), contentDescription = null, tint = c.muted, modifier = Modifier.size(20.dp))
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(27.dp))
                    .background(c.lime)
                    .clickable(role = Role.Button, onClick = onRefresh),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(painterResource(Res.drawable.ic_restart_alt), contentDescription = null, tint = c.onLime, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.map_refresh), style = MaterialTheme.typography.titleMedium, color = c.onLime)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun FactTile(
    tone: BentoTone,
    caption: String,
    value: String,
    modifier: Modifier = Modifier,
    flag: String? = null,
    numeric: Boolean = false,
) {
    BentoCard(
        tone = tone,
        modifier = modifier,
        shape = BentoShapes.small(),
        contentPadding = PaddingValues(14.dp),
        motif = if (flag == null) MaterialShapes.Cookie4Sided else null,
        motifSize = 96.dp,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(caption, style = BentoType.caption(), color = tone.content.copy(alpha = 0.8f), maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (flag != null) {
                BentoFlag(flag, size = 30.dp, corner = 10.dp)
                Spacer(Modifier.width(8.dp))
            }
            val size = if (numeric && value.contains(':')) 15.sp else 19.sp
            Text(
                if (numeric) ltrIsolate(value) else value,
                style = BentoType.title(size).let { if (numeric) it.copy(fontFeatureSettings = "tnum") else it },
                color = tone.content,
                maxLines = if (numeric && !value.contains(':')) 1 else 2,
                softWrap = !numeric || value.contains(':'),
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
