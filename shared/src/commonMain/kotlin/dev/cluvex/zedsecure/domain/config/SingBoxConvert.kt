package dev.cluvex.zedsecure.domain.config

import dev.cluvex.zedsecure.platform.hasAesGcmHardware
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object SingBoxConvert {
    enum class Blocker {
        Protocol,
        Transport,
        TcpHeader,
        VlessEncryption,
        Flow,
        FinalMask,
        PinnedCert,
        VerifyName,
        Reality,
        Fingerprint,
        Cipher,
        SocksTls,
        GrpcMode,
        GrpcAuthority,
        ShadowsocksTransport,
        Bandwidth,
        Address,
        Reserved,
    }

    data class Problem(val blocker: Blocker, val detail: String = "")

    val FINGERPRINTS = setOf("chrome", "firefox", "edge", "safari", "360", "qq", "ios", "android", "random", "randomized")

    private val SS_METHODS = mapOf(
        "aes-128-gcm" to "aes-128-gcm",
        "aes-256-gcm" to "aes-256-gcm",
        "chacha20-poly1305" to "chacha20-ietf-poly1305",
        "chacha20-ietf-poly1305" to "chacha20-ietf-poly1305",
        "xchacha20-poly1305" to "xchacha20-ietf-poly1305",
        "xchacha20-ietf-poly1305" to "xchacha20-ietf-poly1305",
        "2022-blake3-aes-128-gcm" to "2022-blake3-aes-128-gcm",
        "2022-blake3-aes-256-gcm" to "2022-blake3-aes-256-gcm",
        "2022-blake3-chacha20-poly1305" to "2022-blake3-chacha20-poly1305",
    )

    private val VISION_FLOWS = setOf("xtls-rprx-vision", "xtls-rprx-vision-udp443")

    private val VMESS_CIPHERS = setOf("aes-128-gcm", "chacha20-poly1305")

    private val STREAM_NETWORKS = setOf("tcp", "raw", "ws", "websocket", "httpupgrade", "grpc")

    fun canRun(s: ServerConfig): Boolean = problems(s).isEmpty()

    fun problems(s: ServerConfig): List<Problem> = buildList {
        if (!s.protocol.sharedWithSingBox) {
            add(Problem(Blocker.Protocol, s.protocol.label))
            return@buildList
        }
        val security = s.tls.security.lowercase()
        val network = s.transport.network.lowercase()
        if (!s.transport.finalMask.isNullOrBlank()) add(Problem(Blocker.FinalMask))
        if (!s.pinnedCertSha256.isNullOrBlank() && (security.isNotEmpty() || s.protocol == Protocol.HYSTERIA)) {
            add(Problem(Blocker.PinnedCert))
        }
        val vcn = s.tls.verifyPeerCertByName?.trim()?.takeIf { it.isNotEmpty() }
        if (vcn != null && vcn != effectiveServerName(s)) add(Problem(Blocker.VerifyName))
        if (security == "reality") add(Problem(Blocker.Reality))
        if (security == "tls" || security == "reality") {
            val fp = s.tls.fingerprint?.trim()?.lowercase().orEmpty()
            if (fp.isNotEmpty() && fp !in FINGERPRINTS) add(Problem(Blocker.Fingerprint, s.tls.fingerprint.orEmpty()))
        }

        when (s.protocol) {
            Protocol.VLESS -> {
                val encryption = s.encryption.trim().lowercase()
                if (encryption.isNotEmpty() && encryption != "none") add(Problem(Blocker.VlessEncryption))
                val flow = s.flow?.trim()?.lowercase().orEmpty()
                if (flow.isNotEmpty() && flow !in VISION_FLOWS) add(Problem(Blocker.Flow, s.flow.orEmpty()))
            }
            Protocol.SHADOWSOCKS -> {
                val method = s.shadowsocksMethod?.trim()?.lowercase().orEmpty().ifEmpty { "aes-256-gcm" }
                if (method !in SS_METHODS) add(Problem(Blocker.Cipher, s.shadowsocksMethod.orEmpty()))
                if (shadowsocksPlugin(s) == null) add(Problem(Blocker.ShadowsocksTransport, network.uppercase()))
            }
            Protocol.SOCKS -> {
                if (security.isNotEmpty()) add(Problem(Blocker.SocksTls))
                if (network !in setOf("tcp", "raw")) add(Problem(Blocker.Transport, network.uppercase()))
            }
            Protocol.HTTP -> {
                if (network !in setOf("tcp", "raw")) add(Problem(Blocker.Transport, network.uppercase()))
                if (s.transport.headerType == "http") add(Problem(Blocker.TcpHeader))
            }
            Protocol.HYSTERIA -> {
                listOfNotNull(s.bandwidthUp, s.bandwidthDown).filter { it.isNotBlank() }.forEach { value ->
                    if (mbpsOf(value) == null) add(Problem(Blocker.Bandwidth, value))
                }
                s.portHopping?.takeIf { it.isNotBlank() }?.let { hop ->
                    if (portRangesOf(hop) == null) add(Problem(Blocker.Transport, hop))
                }
            }
            Protocol.WIREGUARD -> {
                (s.localAddresses + s.allowedIps).forEach { address ->
                    if (prefixOf(address) == null) add(Problem(Blocker.Address, address))
                }
                s.reserved?.takeIf { it.isNotBlank() }?.let { if (reservedOf(it) == null) add(Problem(Blocker.Reserved, it)) }
            }
            else -> Unit
        }

        if (s.protocol == Protocol.VLESS || s.protocol == Protocol.VMESS || s.protocol == Protocol.TROJAN) {
            if (network !in STREAM_NETWORKS) add(Problem(Blocker.Transport, network.uppercase()))
            if ((network == "tcp" || network == "raw") && s.transport.headerType == "http") add(Problem(Blocker.TcpHeader))
            if (network == "grpc") {
                if (s.transport.mode?.trim()?.lowercase() == "multi" || s.transport.serviceName.orEmpty().startsWith("/")) {
                    add(Problem(Blocker.GrpcMode))
                }
                val authority = s.transport.authority?.trim()?.takeIf { it.isNotEmpty() }
                if (authority != null && authority != effectiveServerName(s) && authority != s.address) {
                    add(Problem(Blocker.GrpcAuthority))
                }
            }
        }
    }

    fun tagOf(s: ServerConfig): String = s.remark.trim().ifEmpty { "${s.address}:${s.port}" }

    fun isEndpoint(s: ServerConfig): Boolean = s.protocol == Protocol.WIREGUARD

    fun fragment(s: ServerConfig, tag: String = tagOf(s)): String {
        val built = outbound(s, tag)
        return SingBoxJson.encode(buildJsonObject {
            putJsonArray(if (isEndpoint(s)) "endpoints" else "outbounds") { add(built) }
        })
    }

    fun outbound(s: ServerConfig, tag: String = tagOf(s)): JsonObject {
        val problems = problems(s)
        require(problems.isEmpty()) { "sing-box cannot run this server: ${problems.joinToString { it.blocker.name }}" }
        return when (s.protocol) {
            Protocol.VLESS -> buildJsonObject {
                head("vless", tag, s)
                put("uuid", s.userId)
                s.flow?.trim()?.lowercase()?.takeIf { it in VISION_FLOWS }?.let { put("flow", "xtls-rprx-vision") }
                tls(s)
                transport(s)
            }
            Protocol.VMESS -> buildJsonObject {
                head("vmess", tag, s)
                put("uuid", s.userId)
                put("security", vmessCipherOf(s.vmessSecurity))
                put("global_padding", true)
                tls(s)
                transport(s)
            }
            Protocol.TROJAN -> buildJsonObject {
                head("trojan", tag, s)
                put("password", s.userId)
                tls(s)
                transport(s)
            }
            Protocol.SHADOWSOCKS -> buildJsonObject {
                head("shadowsocks", tag, s)
                put("method", SS_METHODS.getValue(s.shadowsocksMethod?.trim()?.lowercase().orEmpty().ifEmpty { "aes-256-gcm" }))
                put("password", s.userId)
                shadowsocksPlugin(s)?.takeIf { it.first.isNotEmpty() }?.let { (plugin, opts) ->
                    put("plugin", plugin)
                    put("plugin_opts", opts)
                }
            }
            Protocol.SOCKS -> buildJsonObject {
                head("socks", tag, s)
                put("version", "5")
                if (s.username.isNotBlank()) put("username", s.username)
                if (s.userId.isNotBlank()) put("password", s.userId)
            }
            Protocol.HTTP -> buildJsonObject {
                head("http", tag, s)
                if (s.username.isNotBlank()) put("username", s.username)
                if (s.userId.isNotBlank()) put("password", s.userId)
                XrayJsonBuilder.masqueHeaders(s.transport.headers)?.let { headers ->
                    putJsonObject("headers") { headers.forEach { (k, v) -> put(k, v) } }
                }
                tls(s)
            }
            Protocol.HYSTERIA -> buildJsonObject {
                head("hysteria2", tag, s)
                s.portHopping?.takeIf { it.isNotBlank() }?.let { hop ->
                    putJsonArray("server_ports") { portRangesOf(hop).orEmpty().forEach { add(it) } }
                    val interval = XrayJsonBuilder.hopIntervalOf(s.hopInterval)
                    if ('-' in interval) {
                        put("hop_interval", interval.substringBefore('-') + "s")
                        put("hop_interval_max", interval.substringAfter('-') + "s")
                    } else {
                        put("hop_interval", interval + "s")
                    }
                }
                s.bandwidthUp?.takeIf { it.isNotBlank() }?.let { mbpsOf(it) }?.let { put("up_mbps", it) }
                s.bandwidthDown?.takeIf { it.isNotBlank() }?.let { mbpsOf(it) }?.let { put("down_mbps", it) }
                s.obfsPassword?.takeIf { it.isNotBlank() }?.let { password ->
                    putJsonObject("obfs") {
                        put("type", "salamander")
                        put("password", password)
                    }
                }
                s.secretKey.ifBlank { s.userId }.takeIf { it.isNotBlank() }?.let { put("password", it) }
                putJsonObject("tls") {
                    put("enabled", true)
                    s.tls.sni?.trim()?.takeIf { it.isNotEmpty() }?.let { put("server_name", it) }
                    if (s.tls.allowInsecure) put("insecure", true)
                    alpnOf(s)?.let { alpn -> putJsonArray("alpn") { alpn.forEach { add(it) } } }
                    ech(s)
                }
            }
            Protocol.WIREGUARD -> buildJsonObject {
                put("type", "wireguard")
                put("tag", tag)
                put("mtu", s.wireguardMtu ?: 1420)
                putJsonArray("address") {
                    s.localAddresses.ifEmpty { listOf("172.16.0.2/32") }.forEach { add(prefixOf(it)!!) }
                }
                put("private_key", s.secretKey.trim())
                putJsonArray("peers") {
                    addJsonObject {
                        put("address", s.address)
                        put("port", s.port)
                        put("public_key", s.peerPublicKey.trim())
                        s.preSharedKey?.trim()?.takeIf { it.isNotEmpty() }?.let { put("pre_shared_key", it) }
                        putJsonArray("allowed_ips") {
                            s.allowedIps.ifEmpty { listOf("0.0.0.0/0", "::/0") }.forEach { add(prefixOf(it)!!) }
                        }
                        s.wireguardKeepalive?.takeIf { it > 0 }?.let { put("persistent_keepalive_interval", it) }
                        s.reserved?.takeIf { it.isNotBlank() }?.let { reservedOf(it) }
                            ?.takeIf { bytes -> bytes.any { it != 0 } }
                            ?.let { bytes -> putJsonArray("reserved") { bytes.forEach { add(it) } } }
                    }
                }
            }
            Protocol.AMNEZIAWG, Protocol.MASQUE -> error("unreachable")
        }
    }

    private fun JsonObjectBuilder.head(type: String, tag: String, s: ServerConfig) {
        put("type", type)
        put("tag", tag)
        put("server", s.address)
        put("server_port", s.port)
    }

    private fun hostHeaderOf(s: ServerConfig): String? =
        s.transport.host?.split(",")?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    private fun effectiveServerName(s: ServerConfig): String =
        s.tls.sni?.trim()?.takeIf { it.isNotEmpty() }
            ?: hostHeaderOf(s).takeIf { s.protocol != Protocol.HYSTERIA }
            ?: s.address

    private fun alpnOf(s: ServerConfig): List<String>? =
        s.tls.alpn?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.takeIf { it.isNotEmpty() }

    private fun JsonObjectBuilder.tls(s: ServerConfig) {
        val security = s.tls.security.lowercase()
        if (security != "tls") return
        putJsonObject("tls") {
            put("enabled", true)
            (s.tls.sni?.trim()?.takeIf { it.isNotEmpty() } ?: hostHeaderOf(s))?.let { put("server_name", it) }
            if (s.tls.allowInsecure) put("insecure", true)
            alpnOf(s)?.let { alpn -> putJsonArray("alpn") { alpn.forEach { add(it) } } }
            s.tls.cipherSuites?.split(':', ',')?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?.takeIf { it.isNotEmpty() }
                ?.let { suites -> putJsonArray("cipher_suites") { suites.forEach { add(it) } } }
            ech(s)
            putJsonObject("utls") {
                put("enabled", true)
                put("fingerprint", s.tls.fingerprint?.trim()?.lowercase()?.takeIf { it in FINGERPRINTS } ?: "chrome")
            }
        }
    }

    private fun vmessCipherOf(requested: String): String =
        requested.trim().lowercase().takeIf { it in VMESS_CIPHERS }
            ?: if (hasAesGcmHardware()) "aes-128-gcm" else "chacha20-poly1305"

    private fun JsonObjectBuilder.ech(s: ServerConfig) {
        val value = s.tls.echConfigList?.trim()?.takeIf { it.isNotEmpty() } ?: return
        putJsonObject("ech") {
            put("enabled", true)
            if ("://" !in value && value.none { it == '.' || it == ':' }) {
                putJsonArray("config") {
                    add("-----BEGIN ECH CONFIGS-----")
                    add(value)
                    add("-----END ECH CONFIGS-----")
                }
            }
        }
    }

    private fun splitEarlyData(path: String): Pair<String, Int?> {
        val query = path.substringAfter('?', "")
        if (query.isEmpty()) return path to null
        val params = query.split('&').filter { it.isNotEmpty() }
        val ed = params.firstOrNull { it.substringBefore('=') == "ed" }?.substringAfter('=', "")?.toIntOrNull()
        val rest = params.filterNot { it.substringBefore('=') == "ed" }
        val base = path.substringBefore('?')
        return (if (rest.isEmpty()) base else base + "?" + rest.joinToString("&")) to ed
    }

    private fun JsonObjectBuilder.transport(s: ServerConfig) {
        val host = hostHeaderOf(s)
        val rawPath = s.transport.path?.takeIf { it.isNotBlank() } ?: "/"
        when (s.transport.network.lowercase()) {
            "ws", "websocket" -> putJsonObject("transport") {
                val (path, earlyData) = splitEarlyData(rawPath)
                put("type", "ws")
                put("path", path)
                if (host != null) putJsonObject("headers") { put("Host", host) }
                if (earlyData != null && earlyData > 0) {
                    put("max_early_data", earlyData)
                    put("early_data_header_name", "Sec-WebSocket-Protocol")
                }
            }
            "httpupgrade" -> putJsonObject("transport") {
                put("type", "httpupgrade")
                if (host != null) put("host", host)
                put("path", splitEarlyData(rawPath).first)
            }
            "grpc" -> putJsonObject("transport") {
                put("type", "grpc")
                put("service_name", s.transport.serviceName.orEmpty())
            }
        }
    }

    private fun shadowsocksPlugin(s: ServerConfig): Pair<String, String>? {
        val security = s.tls.security.lowercase()
        val network = s.transport.network.lowercase()
        if (security == "reality") return null
        val host = hostHeaderOf(s) ?: s.address
        return when (network) {
            "tcp", "raw" -> when {
                security.isNotEmpty() -> null
                s.transport.headerType == "http" -> "obfs-local" to sip003("obfs" to "http", "obfs-host" to host)
                else -> "" to ""
            }
            "ws", "websocket" -> {
                val path = s.transport.path?.takeIf { it.isNotBlank() } ?: "/"
                if (security == "tls") {
                    val sni = s.tls.sni?.trim()?.takeIf { it.isNotEmpty() }
                    val plain = (sni == null || sni == host) && s.tls.fingerprint.isNullOrBlank() &&
                        (s.tls.alpn.isNullOrBlank() || s.tls.alpn?.trim() == "http/1.1") && !s.tls.allowInsecure &&
                        s.tls.echConfigList.isNullOrBlank() && s.tls.cipherSuites.isNullOrBlank()
                    if (!plain) return null
                    "v2ray-plugin" to sip003("mode" to "websocket", "host" to host, "path" to path, "mux" to "0", "tls" to null)
                } else {
                    "v2ray-plugin" to sip003("mode" to "websocket", "host" to host, "path" to path, "mux" to "0")
                }
            }
            else -> null
        }
    }

    private fun sip003(vararg pairs: Pair<String, String?>): String = pairs.joinToString(";") { (k, v) ->
        if (v == null) k else "$k=" + v.replace("\\", "\\\\").replace(";", "\\;").replace("=", "\\=")
    }

    fun mbpsOf(value: String): Int? {
        val text = value.trim().lowercase().replace(" ", "")
        val match = Regex("^(\\d+(?:\\.\\d+)?)([kmgt]?)(?:bps|bit|bits|b)?(?:/s|ps)?$").find(text) ?: return null
        val number = match.groupValues[1].toDoubleOrNull() ?: return null
        val mbps = when (match.groupValues[2]) {
            "k" -> number / 1000
            "g" -> number * 1000
            "t" -> number * 1_000_000
            else -> number
        }
        return kotlin.math.round(mbps).toInt().coerceAtLeast(1)
    }

    fun portRangesOf(spec: String): List<String>? {
        val parts = spec.replace(" ", "").split(',').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        return parts.map { part ->
            val bounds = part.split('-', ':')
            val from = bounds.getOrNull(0)?.toIntOrNull() ?: return null
            val to = (if (bounds.size > 1) bounds[1].toIntOrNull() else from) ?: return null
            if (bounds.size > 2 || from !in 1..65535 || to !in from..65535) return null
            "$from:$to"
        }
    }

    fun prefixOf(address: String): String? {
        val text = address.trim().removePrefix("[").replace("]", "")
        if (text.isEmpty()) return null
        val ip = text.substringBefore('/')
        val bits = text.substringAfter('/', "").takeIf { '/' in text }?.toIntOrNull()
        val v6 = ':' in ip
        val valid = if (v6) ip.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' || it == ':' || it == '.' } && ip.count { it == ':' } >= 2
        else ip.split('.').let { o -> o.size == 4 && o.all { it.toIntOrNull()?.let { n -> n in 0..255 } == true } }
        if (!valid) return null
        val max = if (v6) 128 else 32
        if ('/' in text && (bits == null || bits !in 0..max)) return null
        return "$ip/${bits ?: max}"
    }

    fun reservedOf(value: String): List<Int>? {
        val bytes = value.split(',').map { it.trim().toIntOrNull() ?: return null }
        return bytes.takeIf { it.size == 3 && it.all { b -> b in 0..255 } }
    }
}
