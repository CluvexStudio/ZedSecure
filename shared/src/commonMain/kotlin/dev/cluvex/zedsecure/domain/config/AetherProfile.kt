package dev.cluvex.zedsecure.domain.config

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Aether censorship-tunnel engine (CluvexStudio/Aether v2.3.0): automatic
 * route discovery over MASQUE/WireGuard/gool/mim, Tor/Psiphon overlays,
 * local SOCKS5 + HTTP frontends. Self-contained section: bundled binary,
 * own profile model, own markers â€” stock engines untouched.
 *
 * Flag model mirrors the reference GUI (profiles.rs as_args): same modes,
 * same fixed exits (Tor 1820 / Psiphon 1821 unless overridden), same
 * per-mode HTTP flags, same gating. Empty extras forward nothing.
 */
data class AetherProfile(
    val protocol: String = "auto",
    val scanMode: String = "balanced",
    val ipVersion: String = "v4",
    val quickReconnect: Boolean = true,
    val noize: String = "firewall",
    val bind: String = "127.0.0.1:1819",
    val httpPort: String = "",
    val extraTransport: String = "none",
    val torBind: String = "",
    val torBridges: Boolean = false,
    val torRelays: String = "",
    val torBridge: String = "",
    val torBridgeFile: String = "",
    val psiphonBind: String = "",
    val psiphonRegion: String = "",
    val psiphonMode: String = "auto",
    val psiphonConfig: String = "",
    val psiphonCdnIps: String = "",
    val psiphonCdnSni: String = "",
    val psiphonCdnSets: String = "",
    val psiphonServerEntries: String = "",
    val upstream: String = "",
    val dns: String = "",
    val exitLoc: String = "",
    val masqueHttp2: Boolean = false,
    val zeroTrustTeam: String = "",
    val zeroTrustGateway: Boolean = false,
    val routeBlock: String = "",
    val routeDirect: String = "",
    val routesFile: String = "",
    val peer: String = "",
    val wgPeer: String = "",
    val goolPeer: String = "",
    val wiwOuter: String = "",
    val wiwInner: String = "",
    val mimOuter: String = "",
    val mimInner: String = "",
    val keepalive: String = "",
    val perf: String = "",
    val apiFragment: Boolean = false,
    val noQuicV2: Boolean = false,
    val h2Peer: String = "",
    val noDataCheck: Boolean = false,
    val noProfileRetry: Boolean = false,
    val ech: String = "",
    val echDns: String = "",
    val echDomain: String = "",
    val tlsCiphers: String = "",
    val tlsGroups: String = "",
    val disableGrease: Boolean = false,
) {
    companion object {
        const val TOR_EXIT = "127.0.0.1:1820"
        const val PSI_EXIT = "127.0.0.1:1821"

        /** Manual JSON mapping (desktop module has no serialization plugin). */
        fun fromJson(o: JsonObject): AetherProfile {
            fun s(k: String) = (o[k] as? JsonPrimitive)?.content ?: ""
            fun b(k: String, d: Boolean) = (o[k] as? JsonPrimitive)?.booleanOrNull ?: d
            return AetherProfile(
                protocol = s("protocol").ifBlank { "auto" },
                scanMode = s("scanMode").ifBlank { "balanced" },
                ipVersion = s("ipVersion").ifBlank { "v4" },
                quickReconnect = b("quickReconnect", true),
                noize = s("noize").ifBlank { "firewall" },
                bind = s("bind").ifBlank { "127.0.0.1:1819" },
                httpPort = s("httpPort"),
                extraTransport = s("extraTransport").ifBlank { "none" },
                torBind = s("torBind"), torBridges = b("torBridges", false),
                torRelays = s("torRelays"), torBridge = s("torBridge"),
                torBridgeFile = s("torBridgeFile"),
                psiphonBind = s("psiphonBind"), psiphonRegion = s("psiphonRegion"),
                psiphonMode = s("psiphonMode").ifBlank { "auto" },
                psiphonConfig = s("psiphonConfig"), psiphonCdnIps = s("psiphonCdnIps"),
                psiphonCdnSni = s("psiphonCdnSni"), psiphonCdnSets = s("psiphonCdnSets"),
                psiphonServerEntries = s("psiphonServerEntries"),
                upstream = s("upstream"), dns = s("dns"), exitLoc = s("exitLoc"),
                masqueHttp2 = b("masqueHttp2", false),
                zeroTrustTeam = s("zeroTrustTeam"), zeroTrustGateway = b("zeroTrustGateway", false),
                routeBlock = s("routeBlock"), routeDirect = s("routeDirect"), routesFile = s("routesFile"),
                peer = s("peer"), wgPeer = s("wgPeer"), goolPeer = s("goolPeer"),
                wiwOuter = s("wiwOuter"), wiwInner = s("wiwInner"),
                mimOuter = s("mimOuter"), mimInner = s("mimInner"),
                keepalive = s("keepalive"), perf = s("perf"),
                apiFragment = b("apiFragment", false), noQuicV2 = b("noQuicV2", false),
                h2Peer = s("h2Peer"),
                noDataCheck = b("noDataCheck", false), noProfileRetry = b("noProfileRetry", false),
                ech = s("ech"), echDns = s("echDns"), echDomain = s("echDomain"),
                tlsCiphers = s("tlsCiphers"), tlsGroups = s("tlsGroups"),
                disableGrease = b("disableGrease", false),
            )
        }
    }

    private fun isTor() = extraTransport == "tor" || extraTransport == "tor_reverse" || extraTransport == "tor_only"
    private fun isPsi() = extraTransport == "psiphon" || extraTransport == "psiphon_reverse" || extraTransport == "psiphon_only"
    private fun isTorChain() = extraTransport == "tor" || extraTransport == "tor_reverse"
    private fun isPsiChain() = extraTransport == "psiphon" || extraTransport == "psiphon_reverse"

    /** Reverse modes carry TCP only: WireGuard/gool refused by the core. */
    fun effectiveProtocol(): String {
        if ((extraTransport == "tor_reverse" || extraTransport == "psiphon_reverse") &&
            (protocol == "wireguard" || protocol == "gool" || protocol == "gool-classic")
        ) return "masque"
        return protocol
    }

    fun extraWaitSecs(): Long = when {
        isTor() -> 480L
        isPsi() -> 200L
        else -> 0L
    }

    private fun parsePort(addr: String): Int? {
        val p = addr.substringAfterLast(':', "").toIntOrNull() ?: return null
        return if (p in 1..65535) p else null
    }

    private fun loopback(addr: String): String {
        val host = addr.substringBeforeLast(':').trim('[', ']').trim()
        val port = parsePort(addr) ?: return addr
        val h = if (host.isEmpty() || host == "0.0.0.0" || host == "::") "127.0.0.1" else host
        return "$h:$port"
    }

    /** Effective Tor exit (explicit or 1820), loopback-mapped, chain only. */
    fun torExit(): String? {
        if (!isTorChain()) return null
        val raw = torBind.trim().ifEmpty { TOR_EXIT }
        return if (parsePort(raw) != null) loopback(raw) else loopback(TOR_EXIT)
    }

    fun psiExit(): String? {
        if (!isPsiChain()) return null
        val raw = psiphonBind.trim().ifEmpty { PSI_EXIT }
        return if (parsePort(raw) != null) loopback(raw) else loopback(PSI_EXIT)
    }

    fun bindPort(): Int = parsePort(bind) ?: 1819

    /** HTTP frontend port: custom wins unless colliding (SOCKS/exits),
     * else SOCKS+1 (1822 for Tor chains), bumped past taken ports. */
    fun frontendHttpPort(): Int {
        val socks = bindPort()
        val taken = mutableSetOf(socks)
        torExit()?.let { parsePort(it)?.let(taken::add) }
        psiExit()?.let { parsePort(it)?.let(taken::add) }
        httpPort.trim().toIntOrNull()?.let { p ->
            if (p in 1..65535 && p !in taken) return p
        }
        var h = if (isTorChain()) 1822 else if (socks >= 65535) 1820 else socks + 1
        repeat(4) {
            if (h !in taken) return h
            h = if (h >= 65535) 1820 else h + 1
        }
        return h
    }

    /** User-facing SOCKS: the Tor/Psiphon exit in chain modes. */
    fun primaryAddr(): String = torExit() ?: psiExit() ?: loopback(bind)

    fun httpFrontFlag(): String = when {
        extraTransport == "psiphon" || extraTransport == "psiphon_reverse" || extraTransport == "psiphon_only" -> "--psiphon-http"
        isTor() -> "--tor-http"
        else -> "--http-proxy"
    }

    fun flagHttpAddr(): String {
        val port = frontendHttpPort()
        val host = bind.substringBeforeLast(':').trim('[', ']').ifBlank { "127.0.0.1" }
        return "$host:$port"
    }

    /** CLI flags â€” the whole profile up front so prompts never appear. */
    fun toArgv(): List<String> {
        val args = ArrayList<String>()
        when (effectiveProtocol()) {
            "masque" -> args.add("--masque")
            "wireguard" -> args.add("--wg")
            "gool" -> args.add("--gool")
            "gool-classic" -> args.add("--gool-classic")
            "mim" -> args.add("--mim")
        }
        args.add("--$scanMode")
        args.add(when (ipVersion) { "v6" -> "-6"; "both" -> "--dual"; else -> "-4" })
        args.add(if (quickReconnect) "--quick-reconnect" else "--no-quick-reconnect")
        args.add("--noize")
        args.add(noize.ifBlank { "firewall" })
        if (bind != "127.0.0.1:1819" && parsePort(bind) != null) {
            args.add("--bind"); args.add(bind)
        }
        args.add(httpFrontFlag()); args.add(flagHttpAddr())
        if (upstream.isNotBlank()) { args.add("--upstream"); args.add(upstream.trim()) }
        when (extraTransport) {
            "tor" -> args.add("--tor")
            "tor_reverse" -> args.add("--tor-reverse")
            "tor_only" -> args.add("--tor-only")
            "psiphon" -> args.add("--psiphon")
            "psiphon_reverse" -> args.add("--psiphon-reverse")
            "psiphon_only" -> args.add("--psiphon-only")
        }
        if (torBridges && isTor()) args.add("--tor-bridges")
        if (isPsi()) {
            val region = psiphonRegion.trim().uppercase()
            if (region.length == 2 && region.all { it.isLetter() }) {
                args.add("--psiphon-region"); args.add(region)
            }
            if (psiphonMode == "cdn") { args.add("--psiphon-mode"); args.add("cdn") }
            if (psiphonMode == "direct") { args.add("--psiphon-mode"); args.add("direct") }
        }
        if (dns.isNotBlank()) { args.add("--dns"); args.add(dns.trim()) }
        if (exitLoc.isNotBlank()) { args.add("--exit-loc"); args.add(exitLoc.trim()) }
        if (isTor()) {
            if (torRelays.isNotBlank()) { args.add("--tor-relays"); args.add(torRelays.trim()) }
            torBridge.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                args.add("--tor-bridge"); args.add(it)
            }
            if (torBridgeFile.isNotBlank()) { args.add("--tor-bridge-file"); args.add(torBridgeFile.trim()) }
            if (torBind.isNotBlank()) { args.add("--tor-bind"); args.add(torBind.trim()) }
        }
        if (isPsi()) {
            mapOf(
                "--psiphon-config" to psiphonConfig, "--psiphon-cdn-ips" to psiphonCdnIps,
                "--psiphon-cdn-sni" to psiphonCdnSni, "--psiphon-cdn-sets" to psiphonCdnSets,
                "--psiphon-server-entries" to psiphonServerEntries, "--psiphon-bind" to psiphonBind,
            ).forEach { (flag, v) -> if (v.isNotBlank()) { args.add(flag); args.add(v.trim()) } }
        }
        if (zeroTrustTeam.isNotBlank()) {
            args.add("--team"); args.add(zeroTrustTeam.trim())
            if (zeroTrustGateway) args.add("--gateway")
        }
        if (routeBlock.isNotBlank()) { args.add("--route-block"); args.add(routeBlock.trim()) }
        if (routeDirect.isNotBlank()) { args.add("--route-direct"); args.add(routeDirect.trim()) }
        if (routesFile.isNotBlank()) { args.add("--routes"); args.add(routesFile.trim()) }
        listOf(
            "--peer" to peer, "--wg-peer" to wgPeer, "--gool-peer" to goolPeer,
            "--wiw-outer" to wiwOuter, "--wiw-inner" to wiwInner,
            "--mim-outer" to mimOuter, "--mim-inner" to mimInner,
        ).forEach { (flag, v) -> if (v.isNotBlank()) { args.add(flag); args.add(v.trim()) } }
        if (apiFragment) args.add("--api-fragment")
        if (noQuicV2) args.add("--no-quic-v2")
        if (h2Peer.isNotBlank()) { args.add("--h2-peer"); args.add(h2Peer.trim()) }
        if (noDataCheck) args.add("--no-data-check")
        if (noProfileRetry) args.add("--no-profile-retry")
        if (keepalive.isNotBlank()) { args.add("--keepalive"); args.add(keepalive.trim()) }
        if (perf.isNotBlank()) { args.add("--perf"); args.add(perf.trim()) }
        if (ech.isNotBlank()) { args.add("--ech"); args.add(ech.trim()) }
        if (echDns.isNotBlank()) { args.add("--ech-dns"); args.add(echDns.trim()) }
        if (echDomain.isNotBlank()) { args.add("--ech-domain"); args.add(echDomain.trim()) }
        if (tlsCiphers.isNotBlank()) { args.add("--tls-ciphers"); args.add(tlsCiphers.trim()) }
        if (tlsGroups.isNotBlank()) { args.add("--tls-groups"); args.add(tlsGroups.trim()) }
        if (disableGrease) args.add("--disable-grease")
        return args
    }

    fun env(): Map<String, String> = mapOf(
        "AETHER_MASQUE_HTTP2" to if (masqueHttp2) "1" else "0",
    )

    fun toJson(): JsonObject = buildJsonObject {
        put("protocol", protocol); put("scanMode", scanMode); put("ipVersion", ipVersion)
        put("quickReconnect", quickReconnect); put("noize", noize)
        put("bind", bind); put("httpPort", httpPort); put("extraTransport", extraTransport)
        put("torBind", torBind); put("torBridges", torBridges); put("torRelays", torRelays)
        put("torBridge", torBridge); put("torBridgeFile", torBridgeFile)
        put("psiphonBind", psiphonBind); put("psiphonRegion", psiphonRegion)
        put("psiphonMode", psiphonMode); put("psiphonConfig", psiphonConfig)
        put("psiphonCdnIps", psiphonCdnIps); put("psiphonCdnSni", psiphonCdnSni)
        put("psiphonCdnSets", psiphonCdnSets); put("psiphonServerEntries", psiphonServerEntries)
        put("upstream", upstream); put("dns", dns); put("exitLoc", exitLoc)
        put("masqueHttp2", masqueHttp2)
        put("zeroTrustTeam", zeroTrustTeam); put("zeroTrustGateway", zeroTrustGateway)
        put("routeBlock", routeBlock); put("routeDirect", routeDirect); put("routesFile", routesFile)
        put("peer", peer); put("wgPeer", wgPeer); put("goolPeer", goolPeer)
        put("wiwOuter", wiwOuter); put("wiwInner", wiwInner)
        put("mimOuter", mimOuter); put("mimInner", mimInner)
        put("keepalive", keepalive); put("perf", perf)
        put("apiFragment", apiFragment); put("noQuicV2", noQuicV2); put("h2Peer", h2Peer)
        put("noDataCheck", noDataCheck); put("noProfileRetry", noProfileRetry)
        put("ech", ech); put("echDns", echDns); put("echDomain", echDomain)
        put("tlsCiphers", tlsCiphers); put("tlsGroups", tlsGroups); put("disableGrease", disableGrease)
    }
}
