package dev.cluvex.zedsecure.domain.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class ConfigParseException(
    message: String,

    val reason: Reason? = null,
) : Exception(message) {
    enum class Reason {
        UnsupportedSsCipher,

        MissingSsCipher,

        UnsupportedSsPlugin,
    }
}

object ConfigParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val UNSUPPORTED_SS_METHODS = setOf("none", "plain")

    fun parse(link: String): ServerConfig {
        val trimmed = link.trim()
        val scheme = trimmed.substringBefore("://", "").lowercase()
        return when (val protocol = Protocol.fromScheme(scheme)) {
            Protocol.VLESS -> parseVless(trimmed)
            Protocol.VMESS -> parseVmess(trimmed)
            Protocol.TROJAN -> parseTrojan(trimmed)
            Protocol.SHADOWSOCKS -> parseShadowsocks(trimmed)
            Protocol.SOCKS, Protocol.HTTP -> parseUserPass(trimmed, protocol)
            Protocol.HYSTERIA -> parseHysteria(trimmed)
            Protocol.WIREGUARD, Protocol.AMNEZIAWG -> parseWireguard(trimmed, protocol)
            null -> throw ConfigParseException("Unsupported or invalid link scheme")
        }
    }

    fun isSupportedLink(link: String): Boolean {
        val scheme = link.trim().substringBefore("://", "").lowercase()
        return Protocol.fromScheme(scheme) != null || SingBoxLinks.handles(link)
    }

    private fun parseVless(link: String): ServerConfig {
        val p = parseLink(link.removePrefix("vless://"))
        if (p.userInfo.isBlank() || p.host.isBlank()) throw ConfigParseException("Invalid vless link")
        val q = p.query
        val flow = q["flow"].orEmpty()
        return ServerConfig(
            protocol = Protocol.VLESS,
            remark = p.fragment,
            address = p.host,
            port = p.port ?: 443,
            userId = p.userInfo,
            encryption = q["encryption"] ?: "none",
            flow = flow.ifEmpty { null },
            transport = transportFrom(q),

            pinnedCertSha256 = q["pcs"]?.takeIf { it.isNotBlank() },
            tls = securityFrom(q, defaultSecurity = q["security"] ?: ""),
        )
    }

    private fun parseVmess(link: String): ServerConfig {
        val payload = link.removePrefix("vmess://")

        if (payload.contains('@') || (payload.contains('?') && payload.contains('&'))) {
            return parseVmessStd(payload)
        }
        val obj = runCatching {
            json.parseToJsonElement(decodeBase64Utf8(payload)) as JsonObject
        }.getOrElse { throw ConfigParseException("Invalid vmess payload") }

        fun str(key: String): String? = obj[key]?.jsonPrimitive?.contentOrNull()
        val net = str("net") ?: "tcp"
        val type = str("type")
        val host = str("host")
        val path = str("path")
        val tls = str("tls").orEmpty()
        val scy = str("scy").orEmpty()
        val isGrpc = net == "grpc"
        val isKcp = net == "kcp" || net == "mkcp"

        return ServerConfig(
            protocol = Protocol.VMESS,
            remark = str("ps").orEmpty(),
            address = str("add").orEmpty(),
            port = str("port")?.toIntOrNull() ?: 443,
            userId = str("id").orEmpty(),
            alterId = str("aid")?.toIntOrNull() ?: 0,
            encryption = "",
            vmessSecurity = scy.ifEmpty { "auto" },

            transport = TransportConfig(
                network = net,
                headerType = if (isGrpc || net == "xhttp") null else type,
                host = if (isGrpc) null else host,
                path = if (isGrpc || isKcp) null else path,
                seed = if (isKcp) path else null,
                serviceName = if (isGrpc) path else null,
                authority = if (isGrpc) host else str("authority"),
                mode = if (isGrpc) type else str("mode"),
                xhttpExtra = str("extra")?.takeIf { it.isNotBlank() },
                finalMask = str("fm")?.takeIf { it.isNotBlank() },
            ),
            tls = SecurityConfig(
                security = tls,
                sni = str("sni"),
                fingerprint = str("fp"),
                alpn = str("alpn"),
                cipherSuites = str("cs")?.takeIf { it.isNotBlank() },
                publicKey = str("pbk")?.takeIf { it.isNotBlank() },
                shortId = str("sid")?.takeIf { it.isNotBlank() },
                spiderX = str("spx")?.takeIf { it.isNotBlank() },
                echConfigList = str("ech")?.takeIf { it.isNotBlank() },
                allowInsecure = str("allowInsecure") == "1" || str("allowInsecure")?.lowercase() == "true",
            ),
        )
    }

    private fun parseVmessStd(payload: String): ServerConfig {
        val p = parseLink(payload)
        if (p.userInfo.isBlank() || p.host.isBlank()) throw ConfigParseException("Invalid vmess link")
        val q = p.query
        return ServerConfig(
            protocol = Protocol.VMESS,
            remark = p.fragment,
            address = p.host,
            port = p.port ?: 443,
            userId = p.userInfo,
            alterId = q["aid"]?.toIntOrNull() ?: 0,
            encryption = "",
            vmessSecurity = (q["encryption"] ?: q["scy"])?.takeIf { it.isNotBlank() } ?: "auto",
            transport = transportFrom(q),

            pinnedCertSha256 = q["pcs"]?.takeIf { it.isNotBlank() },
            tls = securityFrom(q, defaultSecurity = q["security"].orEmpty()),
        )
    }

    private fun parseTrojan(link: String): ServerConfig {
        val p = parseLink(link.removePrefix("trojan://"))
        if (p.userInfo.isBlank() || p.host.isBlank()) throw ConfigParseException("Invalid trojan link")
        val q = p.query

        return ServerConfig(
            protocol = Protocol.TROJAN,
            remark = p.fragment,
            address = p.host,
            port = p.port ?: 443,
            userId = p.userInfo,
            flow = null,
            transport = transportFrom(q),

            pinnedCertSha256 = q["pcs"]?.takeIf { it.isNotBlank() },
            tls = securityFrom(q, defaultSecurity = q["security"] ?: "tls"),
        )
    }

    private fun parseShadowsocks(link: String): ServerConfig {
        var body = link.removePrefix("ss://")
        val fragment = body.substringAfter('#', "").let { decodeRemark(it) }
        body = body.substringBefore('#')

        var method = ""
        var password = ""
        val p: LinkParts
        if (body.contains('@')) {
            p = parseLink(body)
            val decoded = runCatching { decodeBase64Utf8(p.userInfo) }.getOrDefault(p.userInfo)
            if (decoded.contains(':')) {
                method = decoded.substringBefore(':')

                password = decoded.substringAfter(':')
            }
        } else {
            val decoded = decodeBase64Utf8(body.substringBefore('?'))
            p = parseLink(decoded + body.substringAfter('?', "").let { if (it.isEmpty()) "" else "?$it" })

            val creds = decoded.substringBeforeLast('@')
            if (creds.contains(':')) {
                method = creds.substringBefore(':')
                password = creds.substringAfter(':')
            }
        }
        if (p.host.isBlank()) throw ConfigParseException("Invalid shadowsocks link")

        val normalisedMethod = method.trim().lowercase()
        if (normalisedMethod.isBlank()) {
            throw ConfigParseException(
                "Shadowsocks link carries no cipher",
                ConfigParseException.Reason.MissingSsCipher,
            )
        }
        if (normalisedMethod in UNSUPPORTED_SS_METHODS) {
            throw ConfigParseException(
                "Unsupported Shadowsocks cipher: $method",
                ConfigParseException.Reason.UnsupportedSsCipher,
            )
        }

        val q = p.query
        val plugin = q["plugin"]?.takeIf { it.isNotBlank() }
        val mapped = plugin?.let { sip003Transport(it) }
        return ServerConfig(
            protocol = Protocol.SHADOWSOCKS,
            remark = fragment,
            address = p.host,
            port = p.port ?: 443,
            userId = password,
            shadowsocksMethod = method,
            transport = mapped?.transport ?: transportFrom(q),

            pinnedCertSha256 = q["pcs"]?.takeIf { it.isNotBlank() },
            tls = securityFrom(q, defaultSecurity = q["security"] ?: if (mapped?.tls == true) "tls" else ""),
        )
    }

    private data class PluginTransport(val transport: TransportConfig, val tls: Boolean)

    private fun sip003Transport(plugin: String): PluginTransport {
        val segments = plugin.split(';').map { it.trim() }.filter { it.isNotEmpty() }
        val name = segments.firstOrNull()?.lowercase().orEmpty()
        val opts = HashMap<String, String>()
        val flags = HashSet<String>()
        segments.drop(1).forEach { seg ->
            val i = seg.indexOf('=')
            if (i >= 0) opts[seg.substring(0, i).lowercase()] = seg.substring(i + 1) else flags += seg.lowercase()
        }

        fun unsupported(what: String): Nothing = throw ConfigParseException(
            "Unsupported Shadowsocks plugin: $what",
            ConfigParseException.Reason.UnsupportedSsPlugin,
        )

        return when (name) {
            "obfs-local", "simple-obfs", "obfs" -> when (opts["obfs"]?.lowercase()) {
                "http" -> PluginTransport(
                    TransportConfig(
                        network = "tcp",
                        headerType = "http",
                        host = opts["obfs-host"]?.takeIf { it.isNotBlank() },

                        path = (opts["obfs-uri"] ?: opts["path"])?.takeIf { it.isNotBlank() } ?: "/",
                    ),
                    tls = false,
                )

                "tls" -> unsupported("obfs=tls")
                else -> unsupported(plugin)
            }

            "v2ray-plugin" -> {
                val mode = opts["mode"]?.lowercase() ?: "websocket"
                if (mode != "websocket" && mode != "ws") unsupported("v2ray-plugin mode=$mode")
                PluginTransport(
                    TransportConfig(
                        network = "ws",
                        host = opts["host"]?.takeIf { it.isNotBlank() },
                        path = opts["path"]?.takeIf { it.isNotBlank() } ?: "/",
                    ),
                    tls = flags.contains("tls"),
                )
            }

            "shadow-tls", "restls" -> unsupported(name)
            else -> unsupported(plugin)
        }
    }

    private fun parseUserPass(link: String, protocol: Protocol): ServerConfig {
        val body = link.substringAfter("://")
        if (protocol == Protocol.HTTP && hasPath(body)) {
            throw ConfigParseException("A web address, not an HTTP proxy")
        }
        val p = parseLink(body)
        if (p.host.isBlank()) throw ConfigParseException("Invalid ${protocol.id} link")

        var user = ""
        var pass = ""
        if (p.userInfo.isNotBlank()) {
            val raw = p.userInfo
            if (raw.contains(':')) {
                user = raw.substringBefore(':')
                pass = raw.substringAfter(':')
            } else {
                val decoded = runCatching { decodeBase64Utf8(raw) }.getOrNull()
                if (decoded != null && decoded.contains(':')) {
                    user = decoded.substringBefore(':')
                    pass = decoded.substringAfter(':')
                } else {
                    user = raw
                }
            }
        }
        return ServerConfig(
            protocol = protocol,
            remark = p.fragment,
            address = p.host,
            port = p.port ?: if (protocol == Protocol.HTTP) 80 else 1080,
            userId = pass,
            username = user,
            transport = transportFrom(p.query),

            pinnedCertSha256 = p.query["pcs"]?.takeIf { it.isNotBlank() },
            tls = securityFrom(p.query, defaultSecurity = p.query["security"] ?: ""),
        )
    }

    private fun parseHysteria(link: String): ServerConfig {
        val p = parseLink(link.substringAfter("://"))
        if (p.host.isBlank()) throw ConfigParseException("Invalid hysteria link")
        return ServerConfig(
            protocol = Protocol.HYSTERIA,
            remark = p.fragment,
            address = p.host,
            port = p.port ?: 443,
            userId = p.userInfo,
            secretKey = p.userInfo,
            tls = securityFrom(p.query, defaultSecurity = "tls"),

            obfsPassword = p.query["obfs-password"]?.takeIf { it.isNotBlank() },
            portHopping = p.query["mport"]?.takeIf { it.isNotBlank() },
            pinnedCertSha256 = p.query["pinSHA256"]?.takeIf { it.isNotBlank() },
            bandwidthUp = p.query["upmbps"]?.takeIf { it.isNotBlank() },
            bandwidthDown = p.query["downmbps"]?.takeIf { it.isNotBlank() },
        )
    }

    private fun parseWireguard(link: String, protocol: Protocol = Protocol.WIREGUARD): ServerConfig {
        val p = parseLink(link.substringAfter("://"))
        if (p.host.isBlank()) throw ConfigParseException("Invalid wireguard link")
        val addresses = (p.query["address"] ?: p.query["ip"])
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        val awg = AwgConfig.from(p.query)
        return ServerConfig(

            protocol = if (protocol == Protocol.AMNEZIAWG || !awg.isEmpty) Protocol.AMNEZIAWG else Protocol.WIREGUARD,
            remark = p.fragment,
            address = p.host,
            port = p.port ?: 51820,
            secretKey = p.userInfo,
            peerPublicKey = p.query["publickey"] ?: p.query["pbk"] ?: "",
            preSharedKey = (p.query["presharedkey"] ?: p.query["psk"])?.takeIf { it.isNotBlank() },
            localAddresses = addresses,
            reserved = p.query["reserved"]?.takeIf { it.isNotBlank() },
            wireguardMtu = p.query["mtu"]?.toIntOrNull(),
            wireguardKeepalive = (p.query["keepalive"] ?: p.query["persistentkeepalive"])
                ?.toIntOrNull()?.takeIf { it > 0 },
            allowedIps = (p.query["allowedips"] ?: p.query["allowed_ips"])
                ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
            dnsServers = p.query["dns"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
            awg = awg,
        )
    }

    fun parseWireguardConf(text: String, remark: String = ""): ServerConfig {
        var section = ""
        val iface = LinkedHashMap<String, String>()
        val peer = LinkedHashMap<String, String>()
        text.lineSequence().forEach { rawLine ->
            val line = rawLine.substringBefore('#').substringBefore(';').trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("[") && line.endsWith("]") -> section = line.trim('[', ']').lowercase()
                else -> {
                    val k = line.substringBefore('=', "").trim()
                    val v = line.substringAfter('=', "").trim()
                    if (k.isNotEmpty() && v.isNotEmpty()) {
                        if (section == "peer") peer[k.lowercase()] = v else iface[k.lowercase()] = v
                    }
                }
            }
        }
        val endpoint = peer["endpoint"].orEmpty()
        if (endpoint.isBlank()) throw ConfigParseException("Config has no [Peer] Endpoint")

        val host: String
        val portText: String
        if (endpoint.startsWith("[")) {
            host = endpoint.substringAfter('[').substringBefore(']')
            portText = endpoint.substringAfterLast(':', "")
        } else {
            host = endpoint.substringBeforeLast(':', endpoint)
            portText = endpoint.substringAfterLast(':', "")
        }
        if (host.isBlank()) throw ConfigParseException("Config has an invalid Endpoint")

        val awg = AwgConfig.from(iface + peer)
        return ServerConfig(
            protocol = if (awg.isEmpty) Protocol.WIREGUARD else Protocol.AMNEZIAWG,
            remark = remark.ifBlank { wireguardConfName(text) }.ifBlank { host },
            address = host,
            port = portText.toIntOrNull() ?: 51820,
            secretKey = iface["privatekey"].orEmpty(),
            peerPublicKey = peer["publickey"].orEmpty(),
            preSharedKey = peer["presharedkey"]?.takeIf { it.isNotBlank() },
            localAddresses = iface["address"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
            dnsServers = iface["dns"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
            wireguardMtu = iface["mtu"]?.toIntOrNull(),
            wireguardKeepalive = peer["persistentkeepalive"]?.toIntOrNull()?.takeIf { it > 0 },
            allowedIps = peer["allowedips"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
            awg = awg,
        )
    }

    fun looksLikeWireguardConf(text: String): Boolean {
        var iface = false
        var peer = false
        text.lineSequence().forEach { raw ->
            when (raw.trim().trim(BOM).lowercase()) {
                "[interface]" -> iface = true
                "[peer]" -> peer = true
            }
        }
        return iface && peer
    }

    fun wireguardConfName(text: String): String = text.lineSequence()
        .map { it.trim().trim(BOM) }
        .filter { it.startsWith("#") || it.startsWith(";") }
        .firstNotNullOfOrNull { line ->
            val body = line.drop(1).trim()
            val sep = body.indexOfFirst { it == '=' || it == ':' }
            if (sep <= 0) return@firstNotNullOfOrNull null
            body.drop(sep + 1).trim()
                .takeIf { it.isNotEmpty() && body.take(sep).trim().equals("name", ignoreCase = true) }
        }
        .orEmpty()

    private const val BOM = '\uFEFF'

    private fun transportFrom(q: Map<String, String>) = TransportConfig(
        network = q["type"] ?: "tcp",
        headerType = q["headerType"],
        host = q["host"] ?: q["sni"].takeIf { q["type"] == "ws" },
        path = q["path"],
        seed = q["seed"],
        quicSecurity = q["quicSecurity"],
        quicKey = q["key"],
        mode = q["mode"],
        serviceName = q["serviceName"],
        authority = q["authority"],
        xhttpExtra = q["extra"],
        kcpMtu = q["mtu"]?.toIntOrNull(),
        kcpTti = q["tti"]?.toIntOrNull(),

        finalMask = q["fm"]?.takeIf { it.isNotBlank() },
    )

    private fun securityFrom(q: Map<String, String>, defaultSecurity: String) = SecurityConfig(

        security = defaultSecurity.takeIf { it == "tls" || it == "reality" } ?: "",
        sni = q["sni"],
        fingerprint = q["fp"],
        alpn = q["alpn"],
        publicKey = q["pbk"],
        shortId = q["sid"],
        spiderX = q["spx"],

        echConfigList = (q["ech"] ?: q["echConfigList"])?.takeIf { it.isNotBlank() },

        cipherSuites = q["cs"]?.takeIf { it.isNotBlank() },

        verifyPeerCertByName = q["vcn"]?.takeIf { it.isNotBlank() },

        mldsa65Verify = q["pqv"]?.takeIf { it.isNotBlank() },

        allowInsecure = listOf("insecure", "allowInsecure", "allow_insecure")
            .any { q[it] == "1" || q[it]?.lowercase() == "true" },
    )

    data class LinkParts(
        val userInfo: String,
        val host: String,
        val port: Int?,
        val query: Map<String, String>,
        val fragment: String,
    )

    private fun hasPath(body: String): Boolean =
        body.substringBefore('#').substringBefore('?').substringAfterLast('@').substringAfter('/', "").isNotEmpty()

    fun parseLink(input: String): LinkParts {
        var rest = input
        val fragment = if (rest.contains('#')) decodeRemark(rest.substringAfter('#')) else ""
        rest = rest.substringBefore('#')

        val queryStr = if (rest.contains('?')) rest.substringAfter('?') else ""
        rest = rest.substringBefore('?')

        val userInfo: String
        var hostPort: String
        if (rest.contains('@')) {
            userInfo = percentDecode(rest.substringBeforeLast('@'))
            hostPort = rest.substringAfterLast('@')
        } else {
            userInfo = ""
            hostPort = rest
        }

        hostPort = hostPort.substringBefore('/')

        val host: String
        val port: Int?
        if (hostPort.startsWith("[")) {
            host = hostPort.substringAfter('[').substringBefore(']')
            port = hostPort.substringAfter("]:", "").toIntOrNull()
        } else if (hostPort.contains(':')) {
            host = hostPort.substringBeforeLast(':')
            port = hostPort.substringAfterLast(':').toIntOrNull()
        } else {
            host = hostPort
            port = null
        }

        val query = if (queryStr.isEmpty()) emptyMap() else queryStr.split('&').mapNotNull {
            if (it.isEmpty()) null else {
                val k = percentDecode(it.substringBefore('='))
                val v = percentDecode(it.substringAfter('=', ""))
                k to v
            }
        }.toMap()

        return LinkParts(userInfo, host, port, query, fragment)
    }

    private fun decodeRemark(s: String): String = percentDecode(s)

    fun percentDecode(s: String): String {
        if (!s.contains('%')) return s
        val out = ArrayList<Byte>(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%' && i + 2 < s.length) {
                val hex = s.substring(i + 1, i + 3).toIntOrNull(16)
                if (hex != null) {
                    out.add(hex.toByte()); i += 3; continue
                }
            }
            out.addAll(c.toString().encodeToByteArray().toList())
            i++
        }
        return out.toByteArray().toString(Charsets.UTF_8)
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun decodeBase64Utf8(raw: String): String {
        val cleaned = raw.trim().replace('-', '+').replace('_', '/').filterNot { it == '\n' || it == '\r' }
        val padded = when (cleaned.length % 4) {
            2 -> "$cleaned=="
            3 -> "$cleaned="
            else -> cleaned
        }
        return Base64.Default.decode(padded).toString(Charsets.UTF_8)
    }

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? =
        if (this.isString) this.content else this.content.ifBlank { null }
}
