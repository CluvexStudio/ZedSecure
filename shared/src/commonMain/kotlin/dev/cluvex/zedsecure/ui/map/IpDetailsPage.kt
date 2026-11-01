@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package dev.cluvex.zedsecure.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cluvex.zedsecure.data.net.DnsCheck
import dev.cluvex.zedsecure.data.net.IpDetails
import dev.cluvex.zedsecure.data.net.IpFlags
import dev.cluvex.zedsecure.data.net.IpRegistry
import dev.cluvex.zedsecure.data.net.IpUsage
import dev.cluvex.zedsecure.platform.currentTimeMillis
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.*
import dev.cluvex.zedsecure.ui.bento.Bento
import dev.cluvex.zedsecure.ui.bento.BentoCard
import dev.cluvex.zedsecure.ui.bento.BentoFlag
import dev.cluvex.zedsecure.ui.bento.BentoMetrics
import dev.cluvex.zedsecure.ui.bento.BentoPageHeader
import dev.cluvex.zedsecure.ui.bento.BentoPill
import dev.cluvex.zedsecure.ui.bento.BentoShapes
import dev.cluvex.zedsecure.ui.bento.BentoTone
import dev.cluvex.zedsecure.ui.bento.BentoType
import dev.cluvex.zedsecure.ui.format.ltrIsolate
import dev.cluvex.zedsecure.ui.platform.appVerticalScroll
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@Composable
internal fun IpDetailsPage(
    contentPadding: PaddingValues,
    details: IpDetails,
    originCode: String,
    connected: Boolean,
    serverName: String?,
    pingMs: Int?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
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
        BentoPageHeader(
            title = stringResource(Res.string.ipd_title),
            subtitle = stringResource(Res.string.ipd_subtitle),
            art = null,
            scroll = scroll,
        )
        Column(
            Modifier.padding(horizontal = BentoMetrics.gutter),
            verticalArrangement = Arrangement.spacedBy(BentoMetrics.gap()),
        ) {
            Hero(details)
            if (details.failed && !details.loading) {
                Text(stringResource(Res.string.ipd_failed), style = BentoType.body(), color = c.danger, modifier = Modifier.padding(horizontal = 6.dp))
            }
            if (details.countryCode != null || details.timezone != null) LocationRow(details)
            TimezoneNote(details.timezone)
            NetworkCard(details)
            details.flags?.let { ReputationCard(it) }
            details.registry?.takeUnless { it.isEmpty }?.let { RegistryCard(it, showRange = details.route == null) }
            DnsCard(details.dns, connected, details.loading, originCode, details.countryCode)
            ConnectionRow(pingMs.takeIf { connected }, serverName.takeIf { connected }, details.ipv6)
            if (details.sources.isNotEmpty()) {
                Text(
                    stringResource(
                        if (details.viaTunnel) Res.string.ipd_sources_tunnel else Res.string.ipd_sources,
                        details.sources.joinToString(" · "),
                    ),
                    style = BentoType.body(),
                    color = c.muted,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(27.dp))
                    .background(if (details.loading) c.surface2 else c.lime)
                    .clickable(enabled = !details.loading, role = Role.Button, onClick = onRefresh),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                val ink = if (details.loading) c.muted else c.onLime
                Icon(painterResource(Res.drawable.ic_restart_alt), contentDescription = null, tint = ink, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.map_refresh), style = MaterialTheme.typography.titleMedium, color = ink)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun Hero(details: IpDetails) {
    val c = Bento.colors
    BentoCard(tone = c.stageTone, contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.ipd_public_ip).uppercase(),
                style = BentoType.caption(),
                color = c.onStage.copy(alpha = 0.85f),
                modifier = Modifier.weight(1f),
            )
            if (details.loading) {
                LoadingIndicator(Modifier.size(30.dp), color = c.lime)
            } else if (details.ip != null) {
                if (details.viaTunnel) BentoPill(stringResource(Res.string.ipd_via_tunnel), c.lime, c.onLime)
                else BentoPill(stringResource(Res.string.ipd_direct), c.chipOnStage, c.onStage)
            }
        }
        val ip = details.ip
        Text(
            ltrIsolate(ip ?: "—"),
            style = BentoType.number(if (ip != null && ip.contains(':')) 20.sp else 32.sp, FontWeight.Bold),
            color = c.onStage,
            maxLines = 2,
        )
        details.ipv6?.let {
            Text(ltrIsolate(it), style = BentoType.number(13.sp), color = c.onStage.copy(alpha = 0.85f), maxLines = 2)
        }
        InfoRow(stringResource(Res.string.ipd_hostname), details.hostname, c.onStage, c.onStage.copy(alpha = 0.8f), numeric = true)
    }
}

@Composable
private fun LocationRow(details: IpDetails) {
    val c = Bento.colors
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(BentoMetrics.gap())) {
        Tile(c.blue, stringResource(Res.string.ipd_country), Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                details.countryCode?.let {
                    BentoFlag(it, size = 26.dp, corner = 8.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    countryLabel(details.countryCode, details.country),
                    style = BentoType.title(18.sp),
                    color = c.blue.content,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val place = listOfNotNull(details.region, details.city, details.postal).distinct().joinToString(" · ")
            if (place.isNotBlank()) Text(place, style = BentoType.body(), color = c.blue.content.copy(alpha = 0.85f))
        }
        Tile(c.violet, stringResource(Res.string.ipd_timezone), Modifier.weight(1f)) {
            Text(
                ltrIsolate(details.timezone ?: "—"),
                style = BentoType.title(16.sp),
                color = c.violet.content,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            zoneOf(details.timezone)?.let { zone ->
                Text(
                    stringResource(Res.string.ipd_local_time, ltrIsolate(clockIn(zone))),
                    style = BentoType.body(),
                    color = c.violet.content.copy(alpha = 0.85f),
                )
            }
        }
    }
}

@Composable
private fun TimezoneNote(timezone: String?) {
    val c = Bento.colors
    val device = remember { TimeZone.getDefault() }
    val zone = zoneOf(timezone) ?: return
    val now = currentTimeMillis()
    if (zone.getOffset(now) == device.getOffset(now)) return
    BentoCard(tone = c.coral, shape = BentoShapes.small(), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Text(stringResource(Res.string.ipd_tz_mismatch, ltrIsolate(device.id)), style = BentoType.body(), color = c.coral.content)
    }
}

@Composable
private fun NetworkCard(details: IpDetails) {
    val c = Bento.colors
    val rows = buildList {
        add(stringResource(Res.string.ipd_isp) to (details.isp to false))
        details.org?.takeIf { it != details.isp }?.let { add(stringResource(Res.string.ipd_org) to (it to false)) }
        details.asn?.let { asn ->
            add(stringResource(Res.string.ipd_asn) to (listOfNotNull("AS$asn", details.asnDomain).joinToString(" · ") to true))
        }
        details.route?.let { add(stringResource(Res.string.ipd_range) to (it to true)) }
        if (details.usage != IpUsage.Unknown) add(stringResource(Res.string.ipd_usage) to (usageLabel(details.usage) to false))
        val lat = details.latitude
        val lon = details.longitude
        if (lat != null && lon != null) {
            add(stringResource(Res.string.ipd_coords) to ("%.4f, %.4f".format(Locale.ROOT, lat, lon) to true))
        }
    }
    BentoCard(tone = c.surfaceTone, shape = BentoShapes.small(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.ipd_network), style = BentoType.caption(), color = c.muted)
        rows.forEach { (label, value) -> InfoRow(label, value.first, c.ink, c.muted, numeric = value.second) }
    }
}

@Composable
private fun ReputationCard(flags: IpFlags) {
    val c = Bento.colors
    val chips = listOf(
        flags.vpn to if (flags.vpn) {
            flags.vpnService?.let { stringResource(Res.string.ipd_vpn_named, it) } ?: stringResource(Res.string.ipd_vpn_yes)
        } else {
            stringResource(Res.string.ipd_vpn_no)
        },
        flags.proxy to stringResource(if (flags.proxy) Res.string.ipd_proxy_yes else Res.string.ipd_proxy_no),
        flags.tor to stringResource(if (flags.tor) Res.string.ipd_tor_yes else Res.string.ipd_tor_no),
        flags.datacenter to stringResource(if (flags.datacenter) Res.string.ipd_dc_yes else Res.string.ipd_dc_no),
        flags.abuser to stringResource(if (flags.abuser) Res.string.ipd_abuse_yes else Res.string.ipd_abuse_no),
    )
    BentoCard(tone = c.surfaceTone, shape = BentoShapes.small(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(Res.string.ipd_reputation), style = BentoType.caption(), color = c.muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            chips.forEach { (bad, label) ->
                val tone = if (bad) c.coral else c.cyan
                BentoPill(label, tone.container, tone.content)
            }
        }
        Text(stringResource(Res.string.ipd_reputation_note), style = BentoType.body(), color = c.muted)
    }
}

@Composable
private fun RegistryCard(registry: IpRegistry, showRange: Boolean) {
    val c = Bento.colors
    BentoCard(tone = c.surfaceTone, shape = BentoShapes.small(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.ipd_registry), style = BentoType.caption(), color = c.muted)
        registry.name?.let { InfoRow(stringResource(Res.string.ipd_reg_name), it, c.ink, c.muted, numeric = true) }
        registry.owner?.let { InfoRow(stringResource(Res.string.ipd_reg_owner), it, c.ink, c.muted) }
        if (showRange) registry.range?.let { InfoRow(stringResource(Res.string.ipd_range), it, c.ink, c.muted, numeric = true) }
        registry.registered?.let { InfoRow(stringResource(Res.string.ipd_reg_date), it, c.ink, c.muted, numeric = true) }
        registry.rir?.let { InfoRow(stringResource(Res.string.ipd_reg_rir), it, c.ink, c.muted, numeric = true) }
    }
}

@Composable
private fun DnsCard(dns: DnsCheck?, connected: Boolean, loading: Boolean, originCode: String, exitCode: String?) {
    val c = Bento.colors
    val tone = c.cyan
    BentoCard(tone = tone, shape = BentoShapes.small(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.ipd_dns), style = BentoType.caption(), color = tone.content.copy(alpha = 0.85f))
        when {
            !connected -> Text(stringResource(Res.string.ipd_dns_offline), style = BentoType.body(), color = tone.content)
            dns == null -> Text(
                stringResource(if (loading) Res.string.map_locating else Res.string.ipd_dns_failed),
                style = BentoType.body(),
                color = tone.content,
            )
            dns.fake -> {
                InfoRow(stringResource(Res.string.ipd_dns_answer), dns.resolverIp, tone.content, tone.content.copy(alpha = 0.8f), numeric = true)
                BentoPill(stringResource(Res.string.ipd_dns_fake_chip), c.lime, c.onLime)
                Text(stringResource(Res.string.ipd_dns_fake), style = BentoType.body(), color = tone.content)
            }
            else -> {
                val server = listOfNotNull(dns.resolverIp, dns.isp).joinToString(" · ")
                InfoRow(stringResource(Res.string.ipd_dns_resolver), server, tone.content, tone.content.copy(alpha = 0.8f), numeric = true)
                if (dns.countryCode != null || dns.country != null) {
                    InfoRow(stringResource(Res.string.ipd_dns_country), countryLabel(dns.countryCode, dns.country), tone.content, tone.content.copy(alpha = 0.8f))
                }
                val origin = originCode.trim().uppercase().takeIf { it.length == 2 }
                val leak = origin != null && dns.countryCode == origin && exitCode != origin
                val sameAsExit = dns.countryCode != null && dns.countryCode == exitCode
                when {
                    leak -> BentoPill(stringResource(Res.string.ipd_dns_leak, countryLabel(origin, null)), c.danger, c.onDanger)
                    origin != null || sameAsExit -> BentoPill(stringResource(Res.string.ipd_dns_ok), c.lime, c.onLime)
                    else -> BentoPill(stringResource(Res.string.ipd_dns_elsewhere), c.surface, c.ink)
                }
            }
        }
    }
}

@Composable
private fun ConnectionRow(pingMs: Int?, serverName: String?, ipv6: String?) {
    val c = Bento.colors
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(BentoMetrics.gap())) {
        Tile(c.surfaceTone, stringResource(Res.string.map_ping), Modifier.weight(1f)) {
            Text(ltrIsolate(pingMs?.let { "$it ms" } ?: "—"), style = BentoType.number(18.sp, FontWeight.Bold), color = c.ink, maxLines = 1)
        }
        Tile(c.surfaceTone, stringResource(Res.string.map_server), Modifier.weight(1f)) {
            Text(serverName ?: "—", style = BentoType.title(15.sp), color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Tile(c.surfaceTone, stringResource(Res.string.ipd_ipv6), Modifier.weight(1f)) {
            Text(
                stringResource(if (ipv6 != null) Res.string.ipd_ipv6_on else Res.string.ipd_ipv6_off),
                style = BentoType.title(15.sp),
                color = c.ink,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun Tile(tone: BentoTone, caption: String, modifier: Modifier, content: @Composable () -> Unit) {
    BentoCard(
        tone = tone,
        modifier = modifier.fillMaxHeight(),
        shape = BentoShapes.small(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(caption, style = BentoType.caption(), color = tone.content.copy(alpha = 0.8f), maxLines = 1)
        content()
    }
}

@Composable
private fun InfoRow(label: String, value: String?, ink: Color, muted: Color, numeric: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = BentoType.body(), color = muted, modifier = Modifier.weight(0.42f))
        val shown = value?.takeIf { it.isNotBlank() } ?: "—"
        Text(
            if (numeric) ltrIsolate(shown) else shown,
            style = BentoType.body().copy(fontWeight = FontWeight.SemiBold).let { if (numeric) it.copy(fontFeatureSettings = "tnum") else it },
            color = ink,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.58f),
        )
    }
}

@Composable
private fun usageLabel(usage: IpUsage): String = stringResource(
    when (usage) {
        IpUsage.Hosting -> Res.string.ipd_usage_hosting
        IpUsage.Isp -> Res.string.ipd_usage_isp
        IpUsage.Mobile -> Res.string.ipd_usage_mobile
        IpUsage.Business -> Res.string.ipd_usage_business
        IpUsage.Education -> Res.string.ipd_usage_education
        IpUsage.Government -> Res.string.ipd_usage_government
        IpUsage.Unknown -> Res.string.map_unknown
    },
)

private fun countryLabel(code: String?, fallback: String?): String {
    val iso = code?.trim()?.uppercase()?.takeIf { it.length == 2 }
    val localized = iso?.let { dev.cluvex.zedsecure.ui.servers.countryName(it) }?.takeIf { it != iso }
    return localized ?: fallback ?: iso ?: "—"
}

private fun zoneOf(id: String?): TimeZone? {
    val clean = id?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return TimeZone.getTimeZone(clean).takeIf { it.id == clean }
}

private fun clockIn(zone: TimeZone): String {
    val now = Calendar.getInstance(zone)
    return "%02d:%02d".format(Locale.ROOT, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
}
