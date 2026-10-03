package dev.cluvex.zedsecure.domain.config

import dev.cluvex.zedsecure.domain.config.DeepLinkParser.decode
import java.net.URI

object AetherLink {
    const val SCHEME = "aether://"

    fun isAetherLink(text: String): Boolean = text.trim().startsWith(SCHEME, ignoreCase = true)

    fun parse(uriString: String): Pair<String, AetherProfile>? {
        val trimmed = uriString.trim()
        if (!isAetherLink(trimmed)) return null
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null

        val name = uri.fragment?.let(::decode)?.ifBlank { "Aether" } ?: "Aether"
        val queryMap = uri.rawQuery.orEmpty().split('&').mapNotNull {
            val idx = it.indexOf('=')
            if (idx > 0) it.substring(0, idx) to decode(it.substring(idx + 1)) else null
        }.toMap()

        val host = uri.host.orEmpty()
        val port = if (uri.port > 0) uri.port else null
        val peer = if (host.isNotBlank()) if (port != null) "$host:$port" else host else ""

        val protocol = queryMap["protocol"] ?: AetherProfile.PROTOCOL_MASQUE
        val profile = AetherProfile(
            protocol = protocol,
            transport = queryMap["transport"] ?: AetherProfile.TRANSPORT_H3,
            scanMode = queryMap["scan"] ?: AetherProfile.SCAN_BALANCED,
            obfuscation = queryMap["noize"] ?: AetherProfile.NOISE_AUTO,
            ipVersion = queryMap["ip"] ?: AetherProfile.IP_V4,
            server = if (protocol in listOf(AetherProfile.PROTOCOL_GOOL, AetherProfile.PROTOCOL_MIM)) "" else peer,
            wiwOuter = queryMap["outer"] ?: "",
            wiwInner = queryMap["inner"] ?: "",
            ech = queryMap["ech"] == "1" || queryMap["ech"] == "true",
            echDns = queryMap["ech_dns"] ?: "",
            echDomain = queryMap["ech_domain"] ?: "",
            fragment = queryMap["fragment"] == "1" || queryMap["fragment"] == "true",
            fragmentSize = queryMap["fragment_size"] ?: "16-32",
            fragmentDelay = queryMap["fragment_delay"] ?: "2-10",
            dns = queryMap["dns"] ?: "1.1.1.1,1.0.0.1",
            exitLoc = queryMap["exit_loc"] ?: "",
            psiphonMode = queryMap["psiphon"] ?: AetherProfile.CARRIER_OFF,
            psiphonTactics = queryMap["psiphon_mode"] ?: "auto",
            psiphonRegion = queryMap["region"] ?: "",
            psiphonCdnIps = queryMap["cdn_ips"] ?: "",
            psiphonCdnSni = queryMap["cdn_sni"] ?: "",
            torMode = queryMap["tor"] ?: AetherProfile.CARRIER_OFF,
            torBridges = queryMap["tor_bridges"] ?: "auto",
            torRelays = queryMap["tor_relays"] ?: "auto",
            torBridgeLines = queryMap["bridges"]?.replace(';', '\n') ?: "",
        )
        return name to profile
    }

    fun build(name: String, profile: AetherProfile): String {
        val query = linkedMapOf(
            "protocol" to profile.protocol,
            "scan" to profile.scanMode,
            "ip" to profile.ipVersion,
        )
        if (profile.obfuscation != AetherProfile.NOISE_AUTO) query["noize"] = profile.obfuscation
        if (profile.dns.isNotBlank()) query["dns"] = profile.dns
        if (profile.exitLoc.isNotBlank()) query["exit_loc"] = profile.exitLoc

        if (profile.isOverMasque) {
            query["transport"] = profile.transport
            if (profile.ech) {
                query["ech"] = "1"
                if (profile.echDns.isNotBlank()) query["ech_dns"] = profile.echDns
                if (profile.echDomain.isNotBlank()) query["ech_domain"] = profile.echDomain
            }
            if (profile.fragment && profile.transport == AetherProfile.TRANSPORT_H2) {
                query["fragment"] = "1"
                query["fragment_size"] = profile.fragmentSize
                query["fragment_delay"] = profile.fragmentDelay
            }
        }

        if (profile.isTwoHops) {
            if (profile.wiwOuter.isNotBlank()) query["outer"] = profile.wiwOuter
            if (profile.wiwInner.isNotBlank()) query["inner"] = profile.wiwInner
        }

        if (profile.psiphonMode != AetherProfile.CARRIER_OFF) {
            query["psiphon"] = profile.psiphonMode
            if (profile.psiphonTactics != "auto") query["psiphon_mode"] = profile.psiphonTactics
            if (profile.psiphonRegion.isNotBlank()) query["region"] = profile.psiphonRegion
            if (profile.psiphonCdnIps.isNotBlank()) query["cdn_ips"] = profile.psiphonCdnIps
            if (profile.psiphonCdnSni.isNotBlank()) query["cdn_sni"] = profile.psiphonCdnSni
        }

        if (profile.torMode != AetherProfile.CARRIER_OFF) {
            query["tor"] = profile.torMode
            query["tor_bridges"] = profile.torBridges
            query["tor_relays"] = profile.torRelays
            if (profile.torBridgeLines.isNotBlank()) {
                query["bridges"] = profile.torBridgeLines.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(";")
            }
        }

        val authority = if (!profile.isTwoHops && profile.server.isNotBlank()) profile.server else ""
        val queryString = query.entries.joinToString("&") { "${it.key}=${it.value}" }
        return "$SCHEME$authority?$queryString#$name"
    }
}
