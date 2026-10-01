package dev.cluvex.zedsecure.domain.config

import dev.cluvex.zedsecure.domain.model.RulesetPresets
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object LocalProxy {
    const val SOCKS_PORT = LocalPorts.XRAY_SOCKS
}

class UnsupportedTransportException(val transport: String) :
    Exception("Transport \"$transport\" was removed from this Xray core")

class UdpHopUnsupportedException(val member: String, val previous: String) :
    Exception("$member cannot be chained after $previous, which does not carry UDP")

class ServerlessHopException(val reason: Reason) : Exception(reason.message) {
    enum class Reason(val message: String) {
        NotFirst("A serverless config can only be the first link of a chain"),
        NoServer("Add a server after the serverless config"),
        NoDirectOutbound("The serverless config has no direct outbound to reach the next server through"),
    }
}

object XrayJsonBuilder {
    private val prettyJson = Json { prettyPrint = true }
    private val leanJson = Json { ignoreUnknownKeys = true; isLenient = true }

    private val REMOVED_NETWORKS = setOf("h2", "h3", "http", "quic")

    private val PRESET_FINGERPRINTS = setOf(
        "chrome", "firefox", "safari", "ios", "android", "edge", "360", "qq",
        "random", "randomized", "randomizednoalpn", "unsafe",
    )

    private val ALLOWED_VLESS_FLOWS = setOf("xtls-rprx-vision", "xtls-rprx-vision-udp443")

    private val BUILTIN_OUTBOUND_TAGS = setOf("proxy", "direct", "block")

    const val CHAIN_OUT_TAG = "chain-out"

    private const val TAG_DNS_MODULE = "dns-module"
    private const val TAG_DOMESTIC_DNS = "domestic-dns"

    private const val TAG_SOCKS_IN = "socks-in"

    private val LOCAL_INBOUND_PROTOCOLS = setOf("socks", "mixed")

    private val DIRECT_PROTOCOLS = setOf("freedom", "direct")

    const val SERVERLESS_DIALER_TAG = "serverless"

    private val DEAD_END_PROTOCOLS = setOf("blackhole", "block", "dns")

    private val UNPROBEABLE_RULE_KEYS = setOf(
        "domain", "domains", "inboundTag", "source", "sourceIP", "sourcePort", "localIP", "localPort",
        "user", "attrs", "process", "vlessRoute",
    )

    private const val TAG_DNS_OUT = "dns-out"

    private const val FAKEDNS_POOL = "198.18.0.0/15"
    private const val FAKEDNS_POOL_SIZE = 65535

    private data class EffectiveRule(
        val domains: List<String>,
        val ips: List<String>,
        val port: String,
        val network: String,
        val protocol: List<String>,
        val outboundTag: String,
    )

    private fun effectiveRules(
        rulesets: List<dev.cluvex.zedsecure.domain.model.RulesetItem>,
        geo: Boolean,
    ): List<EffectiveRule> = rulesets.filter { it.enabled }.mapNotNull { r ->
        val domains = RuleValidation.usableDomains(r.domain, geo)
        val ips = RuleValidation.usableIps(r.ip, geo)

        if (r.domain.isNotEmpty() && domains.isEmpty()) return@mapNotNull null
        if (r.ip.isNotEmpty() && ips.isEmpty()) return@mapNotNull null
        if (domains.isEmpty() && ips.isEmpty() && r.port.isBlank() &&
            r.network.isBlank() && r.protocol.isEmpty()
        ) return@mapNotNull null
        if (!RuleValidation.isValidPort(r.port)) return@mapNotNull null
        EffectiveRule(domains, ips, r.port, r.network, r.protocol, r.outboundTag)
    }

    private fun emittedTagFor(ruleTag: String): String =
        "out-" + ruleTag.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }.joinToString("")

    fun build(
        server: ServerConfig,
        socksPort: Int = LocalProxy.SOCKS_PORT,
        options: BuildOptions = BuildOptions(),
        forSpeedtest: Boolean = false,
    ): String {
        val net = server.transport.network.lowercase()
        if (net in REMOVED_NETWORKS) throw UnsupportedTransportException(net)

        val withDns = if (server.dnsServers.isEmpty()) options else options.copy(serverDns = server.dnsServers)
        return assemble(socksPort, withDns, forSpeedtest) { add(proxyOutbound(server, withDns)) }
    }

    fun buildSingBox(
        fragment: String,
        carrier: String?,
        options: BuildOptions = BuildOptions(),
        forSpeedtest: Boolean = false,
        socksPort: Int = LocalProxy.SOCKS_PORT,
    ): String = assemble(socksPort, options, forSpeedtest) {
        add(singBoxOutbound(fragment, carrier, "proxy", entryDialer(options), options.singBox))
    }

    internal fun singBoxOutbound(
        fragment: String,
        carrier: String?,
        tag: String,
        dialerProxy: String?,
        tuning: SingBoxTuning = SingBoxTuning(),
    ): JsonObject {
        val parsed = leanJson.parseToJsonElement(Jsonc.strip(SingBoxTuning.apply(fragment, tuning))) as? JsonObject
            ?: throw IllegalArgumentException("sing-box fragment is not a JSON object")
        return buildJsonObject {
            put("tag", tag)
            put("protocol", "singbox")
            putJsonObject("settings") {
                put("config", parsed)
                if (!carrier.isNullOrBlank()) put("use", carrier)
                put("viaXray", true)
            }
            if (dialerProxy != null) {
                putJsonObject("streamSettings") {
                    putJsonObject("sockopt") { put("dialerProxy", dialerProxy) }
                }
            }
        }
    }

    fun buildChain(
        servers: List<ServerConfig>,
        socksPort: Int = LocalProxy.SOCKS_PORT,
        options: BuildOptions = BuildOptions(),
        forSpeedtest: Boolean = false,
    ): String = buildChainHops(servers.map { ChainHop.Xray(it) }, socksPort, options, forSpeedtest)

    sealed interface ChainHop {
        val label: String
        val needsUdp: Boolean
        val carriesUdp: Boolean

        data class Xray(val server: ServerConfig) : ChainHop {
            override val label: String get() = server.protocol.label
            override val needsUdp: Boolean
                get() = server.protocol.isWireguardFamily || server.protocol == Protocol.HYSTERIA
            override val carriesUdp: Boolean get() = server.protocol.carriesUdp
        }

        data class Serverless(val rawJson: String) : ChainHop {
            override val label: String get() = "Serverless"
            override val needsUdp: Boolean get() = false
            override val carriesUdp: Boolean get() = true
        }

        data class SingBox(val fragment: String, val carrier: String?, val server: SingBoxJson.Server) : ChainHop {
            override val label: String get() = server.label
            override val needsUdp: Boolean get() = server.udp

            override val carriesUdp: Boolean get() = server.type !in setOf("http", "naive", "ssh", "tor", "shadowtls")
        }
    }

    fun buildChainHops(
        hops: List<ChainHop>,
        socksPort: Int = LocalProxy.SOCKS_PORT,
        options: BuildOptions = BuildOptions(),
        forSpeedtest: Boolean = false,
    ): String {
        require(hops.isNotEmpty()) { "a proxy chain needs at least one member" }
        if (hops.drop(1).any { it is ChainHop.Serverless }) throw ServerlessHopException(ServerlessHopException.Reason.NotFirst)
        val entry = hops.first() as? ChainHop.Serverless
        val servers = if (entry != null) hops.drop(1) else hops
        if (servers.isEmpty()) throw ServerlessHopException(ServerlessHopException.Reason.NoServer)
        hops.forEach { hop ->
            if (hop is ChainHop.Xray) {
                val net = hop.server.transport.network.lowercase()
                if (net in REMOVED_NETWORKS) throw UnsupportedTransportException(net)
            }
        }

        hops.forEachIndexed { i, hop ->
            if (hop.needsUdp && i > 0 && !hops[i - 1].carriesUdp) {
                throw UdpHopUnsupportedException(member = hop.label, previous = hops[i - 1].label)
            }
        }
        val entryOutbounds = entry?.let { liftServerlessDialer(it.rawJson, udp = servers.first().needsUdp) }.orEmpty()
        val last = servers.lastIndex
        fun tagOf(i: Int) = if (i == last) "proxy" else "proxy-h$i"
        return assemble(socksPort, options, forSpeedtest) {
            for (i in last downTo 0) {
                val dialer = when {
                    i > 0 -> tagOf(i - 1)
                    entry != null -> SERVERLESS_DIALER_TAG
                    else -> entryDialer(options)
                }
                when (val hop = servers[i]) {
                    is ChainHop.Xray -> add(proxyOutbound(hop.server, options, tag = tagOf(i), dialerProxy = dialer))
                    is ChainHop.SingBox -> add(singBoxOutbound(hop.fragment, hop.carrier, tagOf(i), dialer, options.singBox))
                    is ChainHop.Serverless -> Unit
                }
            }
            entryOutbounds.forEach { add(it) }
        }
    }

    fun buildAutoSelect(
        members: List<AutoMember>,
        tuning: AutoSelectTuning,
        options: BuildOptions = BuildOptions(),
    ): AutoSelectBuild {
        val memberOutbounds = mutableListOf<JsonObject>()
        val tags = LinkedHashMap<String, String>()
        val skipped = mutableListOf<String>()
        for (m in members) {
            val tag = AutoSelectTags.member(tags.size)
            val lifted = runCatching {
                when (m) {
                    is AutoMember.Server ->
                        if (m.server.transport.network.lowercase() in REMOVED_NETWORKS) null

                        else listOf(proxyOutbound(m.server, options, tag = tag))
                    is AutoMember.Custom -> liftCustomOutbounds(m.rawJson, tag)

                    is AutoMember.SingBox ->
                        listOf(singBoxOutbound(m.fragment, m.carrier, tag, entryDialer(options), options.singBox))
                }
            }.getOrNull()
            if (lifted.isNullOrEmpty()) {
                skipped += m.profileId
            } else {
                memberOutbounds += lifted
                tags[tag] = m.profileId
            }
        }
        if (tags.isEmpty()) throw AutoSelectNoMembersException(skipped.size)
        val initialTag = tuning.initialProfileId
            ?.let { id -> tags.entries.firstOrNull { it.value == id }?.key }
        val group = buildJsonObject {
            put("tag", AutoSelectTags.GROUP)
            put("protocol", "autoselect")
            putJsonObject("settings") {
                putJsonArray("outbounds") { tags.keys.forEach { add(it) } }
                val probe = tuning.probeUrl.ifBlank { AUTO_PROBE_URL }
                put("probeURL", probe)

                if (probe != AUTO_FALLBACK_PROBE_URL) put("fallbackProbeURL", AUTO_FALLBACK_PROBE_URL)
                put("probeTimeout", "${tuning.probeTimeoutSec.coerceIn(2, 20)}s")
                put("activeInterval", "${tuning.activeIntervalSec.coerceIn(10, 600)}s")
                put("sweepInterval", "${tuning.sweepIntervalMin.coerceIn(1, 120)}m")
                put("switchRatio", tuning.switchMarginPercent.coerceIn(5, 90) / 100.0)
                put("retry", tuning.retry)
                if (initialTag != null) put("initial", initialTag)
            }
        }
        val json = assemble(LocalProxy.SOCKS_PORT, options, forSpeedtest = false) {
            add(group)
            memberOutbounds.forEach { add(it) }
        }
        return AutoSelectBuild(json = json, memberProfiles = tags, skipped = skipped)
    }

    private const val AUTO_PROBE_URL = "https://www.gstatic.com/generate_204"
    private const val AUTO_FALLBACK_PROBE_URL = "https://cp.cloudflare.com/generate_204"

    private val NON_PROXY_PROTOCOLS = setOf("freedom", "direct", "blackhole", "block", "dns", "loopback")

    internal fun liftCustomOutbounds(rawJson: String, tag: String): List<JsonObject>? {
        val root = leanJson.parseToJsonElement(Jsonc.strip(rawJson)) as? JsonObject ?: return null
        val outbounds = (root["outbounds"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: return null
        fun tagOf(o: JsonObject) = (o["tag"] as? JsonPrimitive)?.content
        fun protocolOf(o: JsonObject) = (o["protocol"] as? JsonPrimitive)?.content?.lowercase()

        val main = outbounds.firstOrNull { tagOf(it) == "proxy" && protocolOf(it) !in NON_PROXY_PROTOCOLS }
            ?: outbounds.firstOrNull { protocolOf(it).let { p -> p != null && p !in NON_PROXY_PROTOCOLS } }
            ?: return null
        return liftWithDependencies(outbounds, main, tag)
    }

    internal fun liftServerlessDialer(rawJson: String, udp: Boolean): List<JsonObject> {
        val root = leanJson.parseToJsonElement(Jsonc.strip(rawJson)) as? JsonObject
            ?: throw ServerlessHopException(ServerlessHopException.Reason.NoDirectOutbound)
        val outbounds = (root["outbounds"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
        fun tagOf(o: JsonObject) = (o["tag"] as? JsonPrimitive)?.content
        fun protocolOf(o: JsonObject) = (o["protocol"] as? JsonPrimitive)?.content?.lowercase()
        fun masked(o: JsonObject, kind: String) =
            (((o["streamSettings"] as? JsonObject)?.get("finalmask") as? JsonObject)?.get(kind) as? JsonArray)
                ?.isNotEmpty() == true
        val direct = outbounds.filter { protocolOf(it) in DIRECT_PROTOCOLS }
        val routed = if (udp) null else (root["routing"] as? JsonObject)?.let(::tlsRouteTag)
            ?.let { routeTag -> direct.firstOrNull { tagOf(it) == routeTag } }
        val chosen = routed
            ?: direct.firstOrNull { masked(it, if (udp) "udp" else "tcp") }
            ?: direct.firstOrNull()
            ?: throw ServerlessHopException(ServerlessHopException.Reason.NoDirectOutbound)
        return liftWithDependencies(outbounds, chosen, SERVERLESS_DIALER_TAG)
    }

    private fun liftWithDependencies(outbounds: List<JsonObject>, main: JsonObject, tag: String): List<JsonObject> {
        fun tagOf(o: JsonObject) = (o["tag"] as? JsonPrimitive)?.content
        fun dependenciesOf(o: JsonObject): List<String> = listOfNotNull(
            ((o["streamSettings"] as? JsonObject)?.get("sockopt") as? JsonObject)
                ?.let { (it["dialerProxy"] as? JsonPrimitive)?.content },
            (o["proxySettings"] as? JsonObject)?.let { (it["tag"] as? JsonPrimitive)?.content },
        ).filter { it.isNotBlank() }
        val byTag = outbounds.mapNotNull { o -> tagOf(o)?.let { it to o } }.toMap()

        val kept = mutableListOf(main to tag)
        var cursor = 0
        while (cursor < kept.size && kept.size <= outbounds.size) {
            val (o, _) = kept[cursor++]
            for (dependency in dependenciesOf(o)) {
                val target = byTag[dependency] ?: continue
                if (kept.any { it.first === target }) continue
                kept += target to "$tag/$dependency"
            }
        }
        val renamed = kept.associate { (o, newTag) -> tagOf(o) to newTag }
        return kept.map { (o, newTag) -> retagOutbound(sanitizeOutbound(o), newTag, renamed) }
    }

    private fun retagOutbound(o: JsonObject, newTag: String, renamed: Map<String?, String>): JsonObject {
        fun repoint(obj: JsonObject, key: String): JsonObject = buildJsonObject {
            obj.forEach { (k, v) ->
                val target = (v as? JsonPrimitive)?.content?.let { renamed[it] }
                if (k == key && target != null) put(k, target) else put(k, v)
            }
        }
        return buildJsonObject {
            put("tag", newTag)
            o.forEach { (k, v) ->
                when {
                    k == "tag" || k == "mux" -> Unit
                    k == "streamSettings" && v is JsonObject -> put(k, buildJsonObject {
                        v.forEach { (sk, sv) ->
                            if (sk == "sockopt" && sv is JsonObject) put(sk, repoint(sv, "dialerProxy")) else put(sk, sv)
                        }
                    })
                    k == "proxySettings" && v is JsonObject -> put(k, repoint(v, "tag"))
                    else -> put(k, v)
                }
            }
        }
    }

    private fun assemble(
        socksPort: Int,
        options: BuildOptions,
        forSpeedtest: Boolean,
        proxyOutbounds: JsonArrayBuilder.() -> Unit,
    ): String {
        val namedOutbounds: Map<String, ServerConfig> = if (forSpeedtest) emptyMap() else {
            options.ruleOutbounds
                .mapNotNull { (ruleTag, server) ->

                    if (server.transport.network.lowercase() in REMOVED_NETWORKS) null
                    else emittedTagFor(ruleTag) to server
                }
                .toMap()
        }
        fun resolveOutboundTag(ruleTag: String): String = when {
            ruleTag in BUILTIN_OUTBOUND_TAGS -> ruleTag
            emittedTagFor(ruleTag) in namedOutbounds -> emittedTagFor(ruleTag)
            else -> "proxy"
        }
        val config = buildJsonObject {
            putJsonObject("log") {
                put("loglevel", options.logLevel)

                put("access", if (options.logLevel == "debug" || options.logLevel == "info") "" else "none")
            }
            if (!forSpeedtest) {
                putJsonArray("inbounds") {
                    add(socksInbound(socksPort, options))
                    if (options.appendHttpProxy) add(httpInbound(socksPort + 1, options))

                    usableLanShare(options)?.let { lan ->
                        add(lanSocksInbound(lan, options))
                        if (options.appendHttpProxy) add(lanHttpInbound(lan))
                    }
                }
            } else {
                putJsonArray("inbounds") {}
            }

            if (!forSpeedtest && fakeDnsActive(options)) {
                putJsonArray("fakedns") {
                    addJsonObject {
                        put("ipPool", FAKEDNS_POOL)
                        put("poolSize", FAKEDNS_POOL_SIZE)
                    }
                }
            }
            putJsonArray("outbounds") {
                proxyOutbounds()

                namedOutbounds.forEach { (tag, server) -> add(proxyOutbound(server, options, tag = tag)) }
                add(directOutbound())
                add(blockOutbound())

                if (!forSpeedtest && localDnsActive(options)) add(dnsOutbound())

                if (options.fragmentEnabled && options.dialerSocksPort == null) {
                    add(fragmentOutbound(options))
                }

                options.dialerSocksPort?.let { add(chainOutOutbound(it)) }
            }
            val geo = options.geoAssetsAvailable

            val rules = if (forSpeedtest) emptyList() else effectiveRules(options.rulesets, geo)

            val dnsSplit = if (forSpeedtest) DnsSplit() else dnsSplit(rules, options)
            if (!forSpeedtest) put("dns", dns(options, dnsSplit))
            putJsonObject("routing") {
                put("domainStrategy", options.domainStrategy)
                putJsonArray("rules") {
                    if (forSpeedtest) return@putJsonArray

                    if (localDnsActive(options)) {
                        addJsonObject {
                            put("type", "field")
                            putJsonArray("inboundTag") { add(TAG_SOCKS_IN) }
                            put("port", "53")
                            put("outboundTag", TAG_DNS_OUT)
                        }
                    }

                    dnsRoutingRules(dnsSplit).forEach { add(it) }

                    if (options.rulesets.isNotEmpty()) {
                        rules.forEach { r ->
                            addJsonObject {
                                put("type", "field")
                                put("outboundTag", resolveOutboundTag(r.outboundTag))
                                if (r.domains.isNotEmpty()) putJsonArray("domain") { r.domains.forEach { add(it) } }
                                if (r.ips.isNotEmpty()) putJsonArray("ip") { r.ips.forEach { add(it) } }
                                if (r.port.isNotBlank()) put("port", r.port)
                                if (r.network.isNotBlank()) put("network", r.network)
                                if (r.protocol.isNotEmpty()) putJsonArray("protocol") { r.protocol.forEach { add(it) } }
                            }
                        }
                    } else {
                        customRule(options.customBlockRules, "block", geo).forEach { add(it) }
                        customRule(options.customDirectRules, "direct", geo).forEach { add(it) }
                        customRule(options.customProxyRules, "proxy", geo).forEach { add(it) }
                    }
                    if (options.blockAds && options.geoAssetsAvailable) {
                        addJsonObject {
                            put("type", "field")
                            put("outboundTag", "block")
                            putJsonArray("domain") { add("geosite:category-ads-all") }
                        }
                    }
                    if (options.bypassLan) {
                        addJsonObject {
                            put("type", "field")
                            put("outboundTag", "direct")
                            putJsonArray("ip") {
                                if (options.geoAssetsAvailable) {
                                    add("geoip:private")
                                } else {
                                    PRIVATE_RANGES.forEach { add(it) }
                                }
                            }
                        }
                    }

                    if (options.bypassIran && options.geoAssetsAvailable) {
                        addJsonObject {
                            put("type", "field")
                            put("outboundTag", "direct")
                            putJsonArray("ip") { add("geoip:ir") }
                        }
                        addJsonObject {
                            put("type", "field")
                            put("outboundTag", "direct")
                            putJsonArray("domain") { add("geosite:category-ir") }
                        }
                    }
                }
            }
            if (!forSpeedtest) {
                put("policy", statsPolicy())
                putJsonObject("stats") {}
            }
        }
        return prettyJson.encodeToString(JsonObject.serializer(), config)
    }

    data class BuildOptions(

        val singBox: SingBoxTuning = SingBoxTuning(),
        val logLevel: String = "warning",
        val domainStrategy: String = "IPIfNonMatch",
        val sniffing: Boolean = true,
        val routeOnly: Boolean = false,

        val carrier: Boolean = false,
        val bypassLan: Boolean = true,
        val bypassIran: Boolean = false,
        val blockAds: Boolean = false,

        val geoAssetsAvailable: Boolean = false,
        val customProxyRules: List<String> = emptyList(),
        val customDirectRules: List<String> = emptyList(),
        val customBlockRules: List<String> = emptyList(),

        val rulesets: List<dev.cluvex.zedsecure.domain.model.RulesetItem> = emptyList(),

        val ruleOutbounds: Map<String, ServerConfig> = emptyMap(),
        val remoteDns: String = "https://1.1.1.1/dns-query",

        val serverDns: List<String> = emptyList(),
        val directDns: String = "1.1.1.1",
        val muxEnabled: Boolean = false,
        val muxConcurrency: Int = 8,
        val fragmentEnabled: Boolean = false,
        val fragmentPackets: String = "tlshello",
        val fragmentLength: String = "10-20",
        val fragmentInterval: String = "10-20",

        val fragmentMaxSplit: Int = 0,

        val lan: LanShare? = null,

        val appendHttpProxy: Boolean = false,

        val localDns: Boolean = false,

        val fakeDns: Boolean = false,

        val outboundDomainResolve: String = "1",

        val resolvedServerHosts: Map<String, List<String>> = emptyMap(),

        val dnsHosts: String = "",

        val preferIpv6: Boolean = false,

        val muxXudpConcurrency: Int = 16,
        val muxXudpQuic: String = "reject",

        val dialerSocksPort: Int? = null,
    )

    data class LanShare(
        val port: Int,
        val username: String,
        val password: String,
        val udp: Boolean,
    )

    private fun usableLanShare(options: BuildOptions): LanShare? = options.lan?.takeIf {
        it.username.isNotBlank() && it.password.isNotBlank() &&
            it.port in 1..65534 && !LocalPorts.isInternal(it.port) && !LocalPorts.isInternal(it.port + 1)
    }

    private fun localDnsActive(options: BuildOptions): Boolean = options.localDns && !options.carrier

    private fun fakeDnsActive(options: BuildOptions): Boolean = localDnsActive(options) && options.fakeDns

    private fun outboundResolveActive(options: BuildOptions): Boolean =

        options.dialerSocksPort == null && options.outboundDomainResolve != "0" &&
            options.resolvedServerHosts.isNotEmpty()

    private fun resolvedIps(host: String, options: BuildOptions): List<String>? {
        if (!outboundResolveActive(options) || isIpLiteral(host)) return null
        return options.resolvedServerHosts[host]
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() && isIpLiteral(it) }
            ?.takeIf { it.isNotEmpty() }
    }

    private fun pinsServerHost(s: ServerConfig, options: BuildOptions): Boolean =
        options.outboundDomainResolve == "1" && resolvedIps(s.address, options) != null

    private fun dialAddress(s: ServerConfig, options: BuildOptions): String {
        if (options.outboundDomainResolve != "2") return s.address
        val ip = resolvedIps(s.address, options)?.firstOrNull() ?: return s.address
        val net = s.transport.network.lowercase()
        val hostHeader = s.transport.host?.split(",")?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        val sni = s.tls.sni?.trim()?.takeIf { it.isNotEmpty() }
        val nameIsExplicit = when {
            s.protocol == Protocol.HYSTERIA -> sni != null
            s.protocol.isWireguardFamily -> true
            else -> when (s.tls.security.lowercase()) {
                "tls" -> sni != null || hostHeader != null
                else -> true
            }
        }
        val hostHeaderSafe = net !in HOST_HEADER_TRANSPORTS || hostHeader != null
        return if (nameIsExplicit && hostHeaderSafe) ip else s.address
    }

    private val HOST_HEADER_TRANSPORTS = setOf("ws", "websocket", "httpupgrade", "xhttp", "splithttp")

    fun isIpLiteral(value: String): Boolean {
        val v = value.trim().removePrefix("[").removeSuffix("]")
        if (v.isEmpty()) return false
        val parts = v.split('.')
        if (parts.size == 4 && parts.all { p -> p.isNotEmpty() && p.length <= 3 && p.all(Char::isDigit) && p.toInt() <= 255 }) {
            return true
        }
        return v.contains(':') && v.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' || it == '.' }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putSockopt(
        s: ServerConfig,
        options: BuildOptions,
        dialerProxy: String?,
    ) {
        val pin = pinsServerHost(s, options)
        if (dialerProxy == null && !pin) return
        putJsonObject("sockopt") {
            if (dialerProxy != null) put("dialerProxy", dialerProxy)
            if (pin) {
                put("domainStrategy", "UseIP")
                putJsonObject("happyEyeballs") {
                    put("prioritizeIPv6", options.preferIpv6)
                    put("interleave", 2)
                }
            }
        }
    }

    private fun customRule(
        entries: List<String>,
        outboundTag: String,
        geoAssetsAvailable: Boolean = true,
    ): List<JsonObject> {
        val clean = entries
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
        if (clean.isEmpty()) return emptyList()

        val rawIps = clean.filter { RoutingMigration.looksLikeAddress(it) }
        val rawDomains = clean - rawIps.toSet()

        val ips = RuleValidation.usableIps(rawIps, geoAssetsAvailable)
        val domains = RuleValidation.usableDomains(rawDomains, geoAssetsAvailable)

        return buildList {
            if (domains.isNotEmpty()) add(
                buildJsonObject {
                    put("type", "field")
                    put("outboundTag", outboundTag)
                    putJsonArray("domain") { domains.forEach { add(it) } }
                },
            )
            if (ips.isNotEmpty()) add(
                buildJsonObject {
                    put("type", "field")
                    put("outboundTag", outboundTag)
                    putJsonArray("ip") { ips.forEach { add(it) } }
                },
            )
        }
    }

    private val PRIVATE_RANGES = RulesetPresets.PRIVATE_RANGES

    private data class DnsSplit(

        val domesticServers: List<Pair<String, List<String>>> = emptyList(),

        val proxyDomains: List<String> = emptyList(),

        val blockedDomains: List<String> = emptyList(),

        val enabled: Boolean = false,
    )

    private fun dnsSplit(rules: List<EffectiveRule>, options: BuildOptions): DnsSplit {
        val domestic = mutableListOf<Pair<String, List<String>>>()
        val proxied = mutableListOf<String>()
        val blocked = mutableListOf<String>()

        val localDomains = if (options.geoAssetsAvailable) listOf("geosite:private")
        else listOf("domain:localhost", "domain:local", "domain:lan")
        domestic += "${TAG_DOMESTIC_DNS}-base" to localDomains

        rules.forEachIndexed { index, r ->
            if (r.domains.isEmpty()) return@forEachIndexed
            when {
                r.outboundTag == "block" -> blocked += r.domains
                r.outboundTag == "direct" ->
                    domestic += "${TAG_DOMESTIC_DNS}-$index" to r.domains

                else -> proxied += r.domains
            }
        }
        return DnsSplit(
            domesticServers = domestic,
            proxyDomains = proxied.distinct(),
            blockedDomains = blocked.distinct(),
            enabled = true,
        )
    }

    private fun dnsRoutingRules(split: DnsSplit): List<JsonObject> {
        if (!split.enabled) return emptyList()
        return buildList {
            if (split.domesticServers.isNotEmpty()) add(
                buildJsonObject {
                    put("type", "field")
                    put("outboundTag", "direct")
                    putJsonArray("inboundTag") { split.domesticServers.forEach { add(it.first) } }
                },
            )

            add(
                buildJsonObject {
                    put("type", "field")
                    put("outboundTag", "proxy")
                    putJsonArray("inboundTag") { add(TAG_DNS_MODULE) }
                },
            )
        }
    }

    private fun dns(options: BuildOptions, split: DnsSplit) = buildJsonObject {
        val hosts = options.dnsHosts.split(',', '\n').mapNotNull {
            val e = it.trim()
            val i = e.lastIndexOf(':')
            if (i > 0) e.substring(0, i).trim() to e.substring(i + 1).trim() else null
        }.filter { it.first.isNotEmpty() && it.second.isNotEmpty() }

        val pinnedServers = if (options.outboundDomainResolve == "1") {
            options.resolvedServerHosts.keys.mapNotNull { host ->
                resolvedIps(host, options)?.let { host to it }
            }
        } else emptyList()
        if (hosts.isNotEmpty() || split.blockedDomains.isNotEmpty() || pinnedServers.isNotEmpty()) putJsonObject("hosts") {
            pinnedServers.forEach { (host, ips) ->
                if (ips.size == 1) put(host, ips[0]) else putJsonArray(host) { ips.forEach { add(it) } }
            }

            split.blockedDomains.forEach { put(it, "127.0.0.1") }

            hosts.forEach { (d, a) -> put(d, a) }
        }
        putJsonArray("servers") {
            if (fakeDnsActive(options)) add("fakedns")
            options.serverDns.forEach { add(it) }
            add(options.remoteDns)
            if (split.proxyDomains.isNotEmpty()) addJsonObject {
                put("address", options.remoteDns)
                putJsonArray("domains") { split.proxyDomains.forEach { add(it) } }
            }
            split.domesticServers.forEach { (tag, domains) ->
                addJsonObject {
                    put("address", options.directDns)
                    putJsonArray("domains") { domains.forEach { add(it) } }

                    put("skipFallback", true)
                    put("tag", tag)
                }
            }
        }

        put("tag", TAG_DNS_MODULE)

        put("queryStrategy", if (options.preferIpv6) "UseIP" else "UseIPv4")
    }

    fun normalizeRawJson(
        rawJson: String,
        socksPort: Int = LocalProxy.SOCKS_PORT,
        forSpeedtest: Boolean = false,

        geoAssetsAvailable: Boolean = true,
    ): String {
        val root = leanJson.parseToJsonElement(Jsonc.strip(rawJson)) as JsonObject

        val patched = buildJsonObject {
            root.forEach { (k, v) ->
                when (k) {
                    "inbounds" -> putJsonArray("inbounds") {
                        if (forSpeedtest) return@putJsonArray

                        var rewroteSocks = false
                        (v as? JsonArray)?.forEach { ib ->
                            if (!rewroteSocks && ib is JsonObject &&
                                (ib["protocol"] as? JsonPrimitive)?.content?.lowercase() in LOCAL_INBOUND_PROTOCOLS
                            ) {
                                add(buildJsonObject {
                                    ib.forEach { (key, x) -> if (key != "port" && key != "listen") put(key, x) }
                                    put("listen", "127.0.0.1")
                                    put("port", socksPort)
                                })
                                rewroteSocks = true
                            } else {
                                add(ib)
                            }
                        }
                        if (!rewroteSocks) add(socksInbound(socksPort))
                    }

                    "outbounds" -> putJsonArray("outbounds") {
                        val outbounds = (v as? JsonArray).orEmpty()
                        val ordered = if (forSpeedtest) probeFirst(outbounds, root["routing"] as? JsonObject) else outbounds
                        ordered.forEach { ob ->
                            add(if (ob is JsonObject) sanitizeOutbound(ob, forSpeedtest) else ob)
                        }
                    }

                    "routing" -> if (!forSpeedtest) put(
                        "routing",
                        if (geoAssetsAvailable || v !is JsonObject) v else stripGeoFromRouting(v),
                    )
                    "dns", "fakedns" -> if (!forSpeedtest) put(k, v)
                    "policy" -> if (!forSpeedtest) put("policy", withStatsPolicy(v as? JsonObject))
                    "stats" -> Unit

                    "log" -> put("log", quietAccessLog(v))
                    else -> put(k, v)
                }
            }
            if (!forSpeedtest && root["inbounds"] == null) {
                putJsonArray("inbounds") { add(socksInbound(socksPort)) }
            }
            if (forSpeedtest && root["inbounds"] == null) {
                putJsonArray("inbounds") {}
            }

            if (!forSpeedtest) {
                if (root["policy"] !is JsonObject) put("policy", statsPolicy())
                putJsonObject("stats") {}
            }
        }

        return if (forSpeedtest) leanJson.encodeToString(JsonObject.serializer(), patched)
        else prettyJson.encodeToString(JsonObject.serializer(), patched)
    }

    private fun withStatsPolicy(policy: JsonObject?): JsonObject {
        val base = statsPolicy()
        if (policy == null) return base
        return buildJsonObject {
            policy.forEach { (k, v) -> if (k != "levels" && k != "system") put(k, v) }
            putJsonObject("levels") {
                (base["levels"] as? JsonObject)?.forEach { (k, v) -> put(k, v) }
                (policy["levels"] as? JsonObject)?.forEach { (k, v) -> put(k, v) }
            }
            putJsonObject("system") {
                (policy["system"] as? JsonObject)?.forEach { (k, v) -> put(k, v) }
                put("statsOutboundUplink", true)
                put("statsOutboundDownlink", true)
            }
        }
    }

    private fun probeFirst(outbounds: List<JsonElement>, routing: JsonObject?): List<JsonElement> {
        fun protocolOf(o: JsonObject) = (o["protocol"] as? JsonPrimitive)?.content?.lowercase()
        val first = outbounds.firstOrNull() as? JsonObject ?: return outbounds
        if (protocolOf(first) !in NON_PROXY_PROTOCOLS) return outbounds
        val tag = routing?.let(::tlsRouteTag) ?: return outbounds
        val target = outbounds.firstOrNull { (it as? JsonObject)?.get("tag")?.let { t -> (t as? JsonPrimitive)?.content } == tag }
            as? JsonObject ?: return outbounds
        if (protocolOf(target) in DEAD_END_PROTOCOLS) return outbounds
        return listOf(target) + outbounds.filter { it !== target }
    }

    private fun tlsRouteTag(routing: JsonObject): String? {
        val rules = routing["rules"] as? JsonArray ?: return null
        for (element in rules) {
            val rule = element as? JsonObject ?: continue
            if (rule.keys.any { it in UNPROBEABLE_RULE_KEYS }) continue
            val network = rule["network"]
            if (network != null && "tcp" !in listValues(network)) continue
            val port = rule["port"]
            if (port != null && !portCovers(port, 443)) continue
            val protocol = rule["protocol"]
            if (protocol != null && "tls" !in listValues(protocol)) continue
            val ips = rule["ip"]
            if (ips != null && listValues(ips).none { it == "0.0.0.0/0" || it.startsWith("geoip:!") }) continue
            return (rule["outboundTag"] as? JsonPrimitive)?.content
        }
        return null
    }

    private fun listValues(value: JsonElement): List<String> = when (value) {
        is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.content }
        is JsonPrimitive -> value.content.split(',')
        else -> emptyList()
    }.map { it.trim().lowercase() }.filter { it.isNotEmpty() }

    private fun portCovers(value: JsonElement, port: Int): Boolean = listValues(value).any { part ->
        val bounds = part.split('-').mapNotNull { it.trim().toIntOrNull() }
        when (bounds.size) {
            1 -> bounds[0] == port
            2 -> port in bounds[0]..bounds[1]
            else -> false
        }
    }

    fun referencesGeoData(rawJson: String): Boolean = runCatching {
        val root = leanJson.parseToJsonElement(Jsonc.strip(rawJson)) as JsonObject
        val rules = (root["routing"] as? JsonObject)?.get("rules") as? JsonArray ?: return false
        rules.any { rule ->
            val o = rule as? JsonObject ?: return@any false
            val domains = (o["domain"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }
            val ips = (o["ip"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }
            domains.any { RuleValidation.isGeoDomain(it) } || ips.any { RuleValidation.isGeoIp(it) }
        }
    }.getOrDefault(false)

    private fun stripGeoFromRouting(routing: JsonObject): JsonObject = buildJsonObject {
        routing.forEach { (k, v) ->
            if (k != "rules" || v !is JsonArray) {
                put(k, v)
                return@forEach
            }
            putJsonArray("rules") {
                v.forEach inner@{ rule ->
                    val o = rule as? JsonObject ?: run { add(rule); return@inner }
                    val rawDomains = (o["domain"] as? JsonArray).orEmpty()
                        .mapNotNull { (it as? JsonPrimitive)?.content }
                    val rawIps = (o["ip"] as? JsonArray).orEmpty()
                        .mapNotNull { (it as? JsonPrimitive)?.content }
                    if (rawDomains.none { RuleValidation.isGeoDomain(it) } &&
                        rawIps.none { RuleValidation.isGeoIp(it) }
                    ) {
                        add(o)
                        return@inner
                    }
                    val domains = rawDomains.filterNot { RuleValidation.isGeoDomain(it) }
                    val ips = rawIps.filterNot { RuleValidation.isGeoIp(it) }
                    if (rawDomains.isNotEmpty() && domains.isEmpty()) return@inner
                    if (rawIps.isNotEmpty() && ips.isEmpty()) return@inner
                    add(
                        buildJsonObject {
                            o.forEach { (key, value) ->
                                when (key) {
                                    "domain" -> if (domains.isNotEmpty()) {
                                        putJsonArray("domain") { domains.forEach { add(it) } }
                                    }
                                    "ip" -> if (ips.isNotEmpty()) {
                                        putJsonArray("ip") { ips.forEach { add(it) } }
                                    }
                                    else -> put(key, value)
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    private fun sanitizeOutbound(ob: JsonObject, forSpeedtest: Boolean = false): JsonObject = buildJsonObject {
        val protocol = (ob["protocol"] as? JsonPrimitive)?.content?.lowercase()
        ob.forEach { (k, v) ->
            when {
                k == "mux" && forSpeedtest -> Unit
                k == "streamSettings" && v is JsonObject -> put("streamSettings", sanitizeStreamSettings(v))
                k == "settings" && v is JsonObject -> put("settings", sanitizeSettings(protocol, v))
                else -> put(k, v)
            }
        }
    }

    private fun sanitizeSettings(protocol: String?, settings: JsonObject): JsonObject = buildJsonObject {
        settings.forEach { (k, v) ->
            if (k != "servers" || v !is JsonArray) {
                put(k, v); return@forEach
            }
            putJsonArray("servers") {
                v.forEach { entry ->
                    val server = entry as? JsonObject
                    if (server == null) {
                        add(entry); return@forEach
                    }
                    if (protocol == "shadowsocks") {
                        val method = (server["method"] as? JsonPrimitive)?.content?.trim()?.lowercase()
                        if (method != null && method in UNSUPPORTED_SS_CIPHERS) {
                            throw ConfigParseException(
                                "This config uses the Shadowsocks cipher \"$method\", which this core removed",
                                ConfigParseException.Reason.UnsupportedSsCipher,
                            )
                        }
                    }
                    addJsonObject {
                        server.forEach { (sk, sv) ->

                            val dropFlow = protocol == "trojan" && sk == "flow" &&
                                (sv as? JsonPrimitive)?.content.orEmpty().isNotBlank()
                            if (!dropFlow) put(sk, sv)
                        }
                    }
                }
            }
        }
    }

    private fun sanitizeStreamSettings(ss: JsonObject): JsonObject = buildJsonObject {
        val network = (ss["network"] as? JsonPrimitive)?.content?.lowercase()

        if (network != null && network in REMOVED_NETWORKS) throw UnsupportedTransportException(network)
        ss.forEach { (k, v) ->
            if (k == "tlsSettings" && v is JsonObject) put("tlsSettings", sanitizeTlsSettings(v))
            else put(k, v)
        }
    }

    private fun sanitizeTlsSettings(tls: JsonObject): JsonObject = buildJsonObject {
        val insecure = (tls["allowInsecure"] as? JsonPrimitive)?.content == "true"
        val pinned = (tls["pinnedPeerCertSha256"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        val serverName = (tls["serverName"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        val hasVcn = (tls["verifyPeerCertByName"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } != null
        tls.forEach { (k, v) ->
            if (k == "allowInsecure") return@forEach
            put(k, v)
        }
        if (insecure && pinned == null && !hasVcn && serverName != null) {
            put("verifyPeerCertByName", serverName)
        }
    }

    private val UNSUPPORTED_SS_CIPHERS = setOf("none", "plain")

    private fun socksInbound(port: Int, options: BuildOptions = BuildOptions()) = buildJsonObject {
        put("tag", TAG_SOCKS_IN)
        put("port", port)
        put("protocol", "socks")

        put("listen", "127.0.0.1")
        putJsonObject("settings") {
            put("auth", "noauth")
            put("udp", true)
            put("userLevel", 8)
        }
        put("sniffing", sniffingBlock(options))
    }

    private fun sniffingBlock(options: BuildOptions): JsonObject = buildJsonObject {
        val fake = fakeDnsActive(options)
        put("enabled", (options.sniffing || fake) && !options.carrier)
        putJsonArray("destOverride") {
            if (options.sniffing) { add("http"); add("tls") }
            if (fake) add("fakedns")
        }
        put("routeOnly", options.routeOnly)
    }

    private fun httpInbound(port: Int, options: BuildOptions = BuildOptions()) = buildJsonObject {
        put("tag", "http-in")
        put("port", port)
        put("protocol", "http")
        put("listen", "127.0.0.1")
        putJsonObject("settings") { put("userLevel", 8) }
    }

    private fun lanSocksInbound(lan: LanShare, options: BuildOptions) = buildJsonObject {
        put("tag", "socks-lan")
        put("port", lan.port)
        put("protocol", "socks")
        put("listen", "0.0.0.0")
        putJsonObject("settings") {
            put("auth", "password")
            putJsonArray("accounts") {
                addJsonObject {
                    put("user", lan.username)
                    put("pass", lan.password)
                }
            }
            put("udp", lan.udp)
            put("userLevel", 8)
        }
        put("sniffing", sniffingBlock(options))
    }

    private fun lanHttpInbound(lan: LanShare) = buildJsonObject {
        put("tag", "http-lan")
        put("port", lan.port + 1)
        put("protocol", "http")
        put("listen", "0.0.0.0")
        putJsonObject("settings") {
            putJsonArray("accounts") {
                addJsonObject {
                    put("user", lan.username)
                    put("pass", lan.password)
                }
            }
            put("userLevel", 8)
        }
    }

    private fun dnsOutbound() = buildJsonObject {
        put("tag", TAG_DNS_OUT)
        put("protocol", "dns")
    }

    private fun directOutbound() = buildJsonObject {
        put("tag", "direct")
        put("protocol", "freedom")
        putJsonObject("settings") { put("domainStrategy", "UseIP") }
    }

    private fun blockOutbound() = buildJsonObject {
        put("tag", "block")
        put("protocol", "blackhole")
    }

private fun quietAccessLog(value: JsonElement): JsonElement {
    val log = value as? JsonObject ?: return value
    val level = (log["loglevel"] as? JsonPrimitive)?.content
    if (level == "debug" || level == "info") return log
    return JsonObject(log + ("access" to JsonPrimitive("none")))
}

private fun statsPolicy() = buildJsonObject {
        putJsonObject("levels") {
            putJsonObject("8") {
                put("connIdle", 300)
                put("downlinkOnly", 1)
                put("handshake", 4)
                put("uplinkOnly", 1)
                put("statsUserUplink", false)
                put("statsUserDownlink", false)
            }
        }
        putJsonObject("system") {
            put("statsOutboundUplink", true)
            put("statsOutboundDownlink", true)
        }
    }

    private fun proxyOutbound(
        s: ServerConfig,
        options: BuildOptions = BuildOptions(),
        tag: String = "proxy",

        dialerProxy: String? = entryDialer(options),
    ) = buildJsonObject {
        val address = dialAddress(s, options)
        put("tag", tag)
        put("protocol", s.protocol.id)
        putJsonObject("settings") {
            when (s.protocol) {
                Protocol.VLESS -> putJsonArray("vnext") {
                    addJsonObject {
                        put("address", address)
                        put("port", s.port)
                        putJsonArray("users") {
                            addJsonObject {
                                put("id", s.userId)
                                put("encryption", s.encryption.ifBlank { "none" })
                                put("level", 8)
                                val flow = s.flow?.lowercase()
                                if (flow != null && flow in ALLOWED_VLESS_FLOWS) put("flow", flow)
                            }
                        }
                    }
                }
                Protocol.VMESS -> putJsonArray("vnext") {
                    addJsonObject {
                        put("address", address)
                        put("port", s.port)
                        putJsonArray("users") {
                            addJsonObject {
                                put("id", s.userId)
                                put("alterId", s.alterId ?: 0)
                                put("security", s.vmessSecurity.ifBlank { "auto" })
                                put("level", 8)
                            }
                        }
                    }
                }

                Protocol.TROJAN -> putJsonArray("servers") {
                    addJsonObject {
                        put("address", address)
                        put("password", s.userId)
                        put("port", s.port)
                        put("level", 8)
                    }
                }
                Protocol.SHADOWSOCKS -> putJsonArray("servers") {
                    addJsonObject {
                        put("address", address)

                        put("method", s.shadowsocksMethod?.takeIf { it.isNotBlank() } ?: "aes-256-gcm")
                        put("password", s.userId)
                        put("port", s.port)
                        put("level", 8)
                    }
                }

                Protocol.SOCKS, Protocol.HTTP -> putJsonArray("servers") {
                    addJsonObject {
                        put("address", address)
                        put("port", s.port)
                        if (s.username.isNotBlank() || s.userId.isNotBlank()) {
                            putJsonArray("users") {
                                addJsonObject {
                                    put("user", s.username)
                                    put("pass", s.userId)
                                    put("level", 8)
                                }
                            }
                        }
                    }
                }
                Protocol.WIREGUARD, Protocol.AMNEZIAWG -> {
                    put("secretKey", s.secretKey)
                    putJsonArray("address") {
                        val addresses = s.localAddresses.ifEmpty { listOf("172.16.0.2/32") }
                        addresses.forEach { add(it) }
                    }
                    s.wireguardMtu?.let { put("mtu", it) }

                    s.reserved
                        ?.split(",")
                        ?.mapNotNull { it.trim().toIntOrNull() }
                        ?.takeIf { it.size == 3 && it.any { b -> b != 0 } }
                        ?.let { bytes -> putJsonArray("reserved") { bytes.forEach { add(it) } } }
                    putJsonArray("peers") {
                        addJsonObject {
                            put("publicKey", s.peerPublicKey)
                            s.preSharedKey?.takeIf { it.isNotBlank() }?.let { put("preSharedKey", it) }

                            val endpointHost =
                                if (address.contains(":") && !address.startsWith("[")) "[$address]" else address
                            put("endpoint", "$endpointHost:${s.port}")

                            s.wireguardKeepalive?.let { put("keepAlive", it) }
                            putJsonArray("allowedIPs") {
                                s.allowedIps.ifEmpty { listOf("0.0.0.0/0", "::/0") }.forEach { add(it) }
                            }
                        }
                    }

                    val awgEntries = s.awg.entries
                    if (awgEntries.isNotEmpty()) {
                        putJsonObject("awg") {
                            awgEntries.forEach { (k, v) -> put(AwgConfig.CONF_NAME[k] ?: k, v) }
                        }
                    }
                }

                Protocol.HYSTERIA -> {
                    put("address", address)
                    put("port", s.port)
                    put("version", 2)
                }
            }
        }

        if (s.protocol.supportsTransport || s.protocol == Protocol.SOCKS || s.protocol == Protocol.HTTP) {
            put("streamSettings", streamSettings(s, options, dialerProxy))

            if (tag == "proxy") {
                putJsonObject("mux") {
                    put("enabled", options.muxEnabled && s.protocol.supportsMux)
                    put("concurrency", options.muxConcurrency)
                    put("xudpConcurrency", options.muxXudpConcurrency)
                    put("xudpProxyUDP443", options.muxXudpQuic)
                }
            }
        } else if (s.protocol == Protocol.HYSTERIA) {
            put("streamSettings", hysteriaStreamSettings(s, options, dialerProxy))
        } else if (s.protocol.isWireguardFamily && (dialerProxy != null || pinsServerHost(s, options))) {
            putJsonObject("streamSettings") { putSockopt(s, options, dialerProxy) }
        }
    }

    private fun hysteriaStreamSettings(
        s: ServerConfig,
        options: BuildOptions = BuildOptions(),
        dialerProxy: String? = null,
    ) = buildJsonObject {
        put("network", "hysteria")
        put("security", "tls")
        putJsonObject("tlsSettings") {
            val sni = s.tls.sni?.takeIf { it.isNotBlank() }
            if (sni != null) put("serverName", sni)

            putJsonArray("alpn") { add("h3") }

            val pinned = s.pinnedCertSha256?.takeIf { it.isNotBlank() }
            if (pinned != null) put("pinnedPeerCertSha256", pinned)
            else if (s.tls.allowInsecure && sni != null) put("verifyPeerCertByName", sni)
        }
        putJsonObject("hysteriaSettings") {
            put("version", 2)
            val auth = s.secretKey.ifBlank { s.userId }
            if (auth.isNotBlank()) put("auth", auth)
        }
        val obfs = s.obfsPassword?.takeIf { it.isNotBlank() }
        val hop = s.portHopping?.takeIf { it.isNotBlank() }
        val up = s.bandwidthUp?.takeIf { it.isNotBlank() }
        val down = s.bandwidthDown?.takeIf { it.isNotBlank() }
        if (obfs != null || hop != null || up != null || down != null) {
            putJsonObject("finalmask") {
                if (obfs != null) putJsonArray("udp") {
                    addJsonObject {
                        put("type", "salamander")
                        putJsonObject("settings") { put("password", obfs) }
                    }
                }
                if (hop != null || up != null || down != null) putJsonObject("quicParams") {
                    if (up != null || down != null) {
                        put("congestion", "brutal")

                        up?.let { put("brutalUp", if (it.all(Char::isDigit)) "${it}mbps" else it) }
                        down?.let { put("brutalDown", if (it.all(Char::isDigit)) "${it}mbps" else it) }
                    }
                    if (hop != null) putJsonObject("udpHop") {
                        put("ports", hop)
                        put("interval", "30")
                    }
                }
            }
        }

        putSockopt(s, options, dialerProxy)
    }

    private fun streamSettings(
        s: ServerConfig,
        options: BuildOptions = BuildOptions(),
        dialerProxy: String? = entryDialer(options),
    ): JsonObject = applyFinalMask(streamSettingsBase(s, options, dialerProxy), s.transport.finalMask)

    private fun applyFinalMask(stream: JsonObject, raw: String?): JsonObject {
        val text = raw?.takeIf { it.isNotBlank() } ?: return stream
        val parsed = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            ?: return stream
        val existing = stream["finalmask"] as? JsonObject
        val merged = if (existing == null) parsed else buildJsonObject {
            existing.forEach { (k, v) -> put(k, v) }
            parsed.forEach { (k, v) -> put(k, v) }
        }
        return buildJsonObject {
            stream.forEach { (k, v) -> if (k != "finalmask") put(k, v) }
            put("finalmask", merged)
        }
    }

    private fun streamSettingsBase(
        s: ServerConfig,
        options: BuildOptions = BuildOptions(),
        dialerProxy: String? = entryDialer(options),
    ) = buildJsonObject {
        val t = s.transport
        val net = t.network.lowercase()
        val hostHeader = t.host?.takeIf { it.isNotBlank() }
        val path = t.path?.takeIf { it.isNotBlank() } ?: "/"

        put("network", net)

        when (net) {
            "tcp", "raw" -> putJsonObject(if (net == "raw") "rawSettings" else "tcpSettings") {
                putJsonObject("header") {
                    if (t.headerType == "http") {
                        put("type", "http")
                        putJsonObject("request") {
                            putJsonArray("path") { path.split(",").forEach { add(it) } }
                            putJsonObject("headers") {
                                putJsonArray("Host") {
                                    (hostHeader ?: "").split(",").forEach { add(it) }
                                }
                            }
                        }
                    } else {
                        put("type", "none")
                    }
                }
            }

            "kcp", "mkcp" -> putJsonObject("kcpSettings") {
                put("mtu", t.kcpMtu ?: 1350)
                put("tti", t.kcpTti ?: 50)
                put("uplinkCapacity", 12)
                put("downlinkCapacity", 100)
            }
            "ws", "websocket" -> putJsonObject("wsSettings") {
                put("path", path)
                if (hostHeader != null) {
                    put("host", hostHeader)
                    putJsonObject("headers") { put("Host", hostHeader) }
                }
            }
            "httpupgrade" -> putJsonObject("httpupgradeSettings") {
                put("path", path)
                if (hostHeader != null) put("host", hostHeader)
            }
            "xhttp", "splithttp" -> putJsonObject("xhttpSettings") {
                put("path", path)
                if (hostHeader != null) put("host", hostHeader)
                put("mode", t.mode?.takeIf { it.isNotBlank() } ?: "auto")

                t.xhttpExtra?.takeIf { it.isNotBlank() }?.let { raw ->
                    (runCatching { Json.parseToJsonElement(raw) }.getOrNull() as? JsonObject)
                        ?.let { put("extra", it) }
                }
            }
            "grpc" -> putJsonObject("grpcSettings") {
                put("serviceName", t.serviceName.orEmpty())
                put("multiMode", t.mode == "multi")
                (t.authority ?: hostHeader)?.let { put("authority", it) }
            }
        }

        if (net == "kcp" || net == "mkcp") {
            val header = t.headerType?.takeIf { it.isNotBlank() && it != "none" }
            val seed = t.seed?.takeIf { it.isNotBlank() }
            putJsonObject("finalmask") {
                putJsonArray("udp") {
                    addJsonObject {
                        put("type", "mkcp-legacy")
                        if (seed != null) putJsonObject("settings") { put("value", seed) }
                    }
                    if (header != null) addJsonObject {
                        put("type", "mkcp-legacy")
                        putJsonObject("settings") {
                            put("header", if (header == "wechat-video") "wechat" else header)
                            if (header == "dns" && hostHeader != null) put("value", hostHeader)
                        }
                    }
                }
            }
        }

        val sec = s.tls.security.lowercase()
        if (sec == "tls" || sec == "reality") {
            put("security", sec)
            val serverName = s.tls.sni?.takeIf { it.isNotBlank() }
                ?: hostHeader?.split(",")?.firstOrNull()?.takeIf { it.isNotBlank() }

            val fingerprint = s.tls.fingerprint?.lowercase()?.takeIf { fp ->
                if (sec == "reality") {
                    fp != "unsafe" && fp != "hellogolang" && (fp in PRESET_FINGERPRINTS || fp.startsWith("hello"))
                } else {
                    fp == "unsafe" || fp in PRESET_FINGERPRINTS || fp.startsWith("hello")
                }
            }
            val pinned = s.pinnedCertSha256?.takeIf { it.isNotBlank() }

            val settings = buildJsonObject {
                if (serverName != null) put("serverName", serverName)
                if (fingerprint != null) put("fingerprint", fingerprint)
                if (sec == "tls") {
                    if (pinned != null) put("pinnedPeerCertSha256", pinned)

                    val vcn = s.tls.verifyPeerCertByName?.takeIf { it.isNotBlank() }
                    if (vcn != null) put("verifyPeerCertByName", vcn)
                    else if (pinned == null && s.tls.allowInsecure && serverName != null) {
                        put("verifyPeerCertByName", serverName)
                    }

                    s.tls.cipherSuites?.takeIf { it.isNotBlank() }?.let { put("cipherSuites", it) }
                    s.tls.alpn?.takeIf { it.isNotBlank() }?.let { alpn ->
                        putJsonArray("alpn") {
                            alpn.split(",").map(String::trim).filter(String::isNotEmpty)
                                .forEach { add(it) }
                        }
                    }

                    s.tls.echConfigList?.takeIf { it.isNotBlank() }?.let { put("echConfigList", it) }
                } else {
                    s.tls.publicKey?.takeIf { it.isNotBlank() }?.let { put("publicKey", it) }
                    s.tls.shortId?.takeIf { it.isNotBlank() }?.let { put("shortId", it) }
                    s.tls.spiderX?.takeIf { it.isNotBlank() }?.let { put("spiderX", it) }

                    s.tls.mldsa65Verify?.takeIf { it.isNotBlank() }?.let { put("mldsa65Verify", it) }
                }
            }
            put(if (sec == "reality") "realitySettings" else "tlsSettings", settings)
        }

        putSockopt(s, options, dialerProxy)
    }

    fun withCarrierProxy(configJson: String, carrierPort: Int): String {
        val root = leanJson.parseToJsonElement(Jsonc.strip(configJson)) as JsonObject

        val outbounds = root["outbounds"] as? JsonArray
            ?: throw IllegalArgumentException("config has no outbounds; cannot attach a carrier")
        if (outbounds.isEmpty()) {
            throw IllegalArgumentException("config has an empty outbounds list; cannot attach a carrier")
        }
        val byTag = outbounds.mapNotNull { ob ->
            val o = ob as? JsonObject ?: return@mapNotNull null
            val t = o["tag"]?.jsonPrimitiveOrNull()?.content ?: return@mapNotNull null
            t to o
        }.toMap()
        fun dialerTagOf(o: JsonObject): String? =
            (o["streamSettings"] as? JsonObject)?.let { it["sockopt"] as? JsonObject }
                ?.get("dialerProxy")?.jsonPrimitiveOrNull()?.content
        var entry = outbounds.first() as? JsonObject ?: return configJson

        var steps = 0
        while (steps++ < outbounds.size) {
            val next = dialerTagOf(entry)?.takeIf { it != "fragment" }?.let { byTag[it] } ?: break
            entry = next
        }

        val patched = buildJsonObject {
            root.forEach { (k, v) ->
                if (k != "outbounds") { put(k, v); return@forEach }
                putJsonArray("outbounds") {
                    outbounds.forEach { element ->
                        val ob = element as? JsonObject
                        val tag = ob?.get("tag")?.jsonPrimitiveOrNull()?.content

                        if (tag == "fragment") return@forEach

                        if (ob == null || ob !== entry) { add(element); return@forEach }
                        add(withCarrierDialer(ob))
                    }
                    add(chainOutOutbound(carrierPort))
                }
            }
        }
        return prettyJson.encodeToString(JsonObject.serializer(), patched)
    }

    private fun withCarrierDialer(outbound: JsonObject): JsonObject {
        val stream = outbound["streamSettings"] as? JsonObject
        val sockopt = stream?.get("sockopt") as? JsonObject
        val newSockopt = buildJsonObject {
            var replaced = false
            sockopt?.forEach { (k, v) ->
                if (k == "dialerProxy") { put(k, JsonPrimitive(CHAIN_OUT_TAG)); replaced = true } else put(k, v)
            }
            if (!replaced) put("dialerProxy", CHAIN_OUT_TAG)
        }
        val newStream = buildJsonObject {
            var replaced = false
            stream?.forEach { (k, v) ->
                if (k == "sockopt") { put(k, newSockopt); replaced = true } else put(k, v)
            }
            if (!replaced) put("sockopt", newSockopt)
        }
        return buildJsonObject {
            var replaced = false
            outbound.forEach { (k, v) ->
                if (k == "streamSettings") { put(k, newStream); replaced = true } else put(k, v)
            }
            if (!replaced) put("streamSettings", newStream)
        }
    }

    private fun kotlinx.serialization.json.JsonElement.jsonPrimitiveOrNull(): JsonPrimitive? =
        this as? JsonPrimitive

    private fun entryDialer(options: BuildOptions): String? = when {
        options.dialerSocksPort != null -> CHAIN_OUT_TAG
        options.fragmentEnabled -> "fragment"
        else -> null
    }

    private fun chainOutOutbound(port: Int) = buildJsonObject {
        put("tag", CHAIN_OUT_TAG)
        put("protocol", "socks")
        putJsonObject("settings") {
            putJsonArray("servers") {
                addJsonObject {
                    put("address", "127.0.0.1")
                    put("port", port)
                }
            }
        }
    }

    private fun fragmentOutbound(options: BuildOptions) = buildJsonObject {
        put("tag", "fragment")
        put("protocol", "freedom")
        putJsonObject("settings") {
            putJsonObject("fragment") {
                put("packets", options.fragmentPackets)
                put("length", options.fragmentLength)
                put("interval", options.fragmentInterval)

                if (options.fragmentMaxSplit > 0) put("maxSplit", options.fragmentMaxSplit)
            }
            put("domainStrategy", "AsIs")
        }
    }
}
