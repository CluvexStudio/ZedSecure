package dev.cluvex.zedsecure.domain.config

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object SingBoxLinks {
    private val SCHEMES = setOf("tuic", "anytls", "hysteria", "naive+https", "naive+quic", "socks4", "socks4a")

    private val FORM_TYPES = setOf("tuic", "anytls", "hysteria", "naive", "socks")

    private const val ECH_BEGIN = "-----BEGIN ECH CONFIGS-----"
    private const val ECH_END = "-----END ECH CONFIGS-----"

    private enum class TlsKind { Tcp, Quic, Naive }

    fun handles(link: String): Boolean {
        val trimmed = link.trim()
        val scheme = trimmed.substringBefore("://", "").lowercase()
        if (scheme !in SCHEMES) return false
        if (scheme == "hysteria") return isHysteria1(trimmed)
        return true
    }

    private fun isHysteria1(link: String): Boolean {
        val rest = link.substringAfter("://")
        val authority = rest.substringBefore('?').substringBefore('#').substringBefore('/')
        val query = queryOf(rest)
        if ('@' in authority) return false
        return query.containsKey("auth") || query.containsKey("obfsParam") || query.containsKey("upmbps") ||
            query.containsKey("protocol") || query.containsKey("peer")
    }

    data class Parsed(val name: String, val outbound: JsonObject) {
        val fragment: String get() = SingBoxJson.encode(buildJsonObject { putJsonArray("outbounds") { add(outbound) } })
    }

    fun parse(link: String): Parsed {
        val trimmed = link.trim()
        val scheme = trimmed.substringBefore("://", "").lowercase()
        val parts = LinkParts.of(trimmed.substringAfter("://"))
        if (parts.host.isBlank()) throw ConfigParseException("Invalid $scheme link")
        val name = parts.fragment.ifBlank { "${parts.host}:${parts.port ?: ""}".trimEnd(':') }
        val q = parts.query
        val outbound = when (scheme) {
            "tuic" -> buildJsonObject {
                put("type", "tuic")
                put("tag", name)
                server(parts, 443)
                val (uuid, password) = splitCredentials(parts.userInfo)
                if (uuid.isBlank()) throw ConfigParseException("Invalid tuic link: no uuid")
                put("uuid", uuid)
                (password.ifBlank { q["password"].orEmpty() }).takeIf { it.isNotBlank() }?.let { put("password", it) }
                q["congestion_control"]?.takeIf { it.isNotBlank() }?.let { put("congestion_control", it) }
                (q["udp_relay_mode"] ?: q["udp-relay-mode"])?.takeIf { it.isNotBlank() }?.let { put("udp_relay_mode", it) }
                if (q["udp_over_stream"]?.truthy() == true) put("udp_over_stream", true)
                if ((q["zero_rtt_handshake"] ?: q["reduce_rtt"])?.truthy() == true) put("zero_rtt_handshake", true)
                q["heartbeat"]?.takeIf { it.isNotBlank() }?.let { put("heartbeat", duration(it)) }
                tls(q, TlsKind.Quic, alpnDefault = "h3")
            }
            "anytls" -> buildJsonObject {
                put("type", "anytls")
                put("tag", name)
                server(parts, 443)
                val password = parts.userInfo.ifBlank { q["password"].orEmpty() }
                if (password.isBlank()) throw ConfigParseException("Invalid anytls link: no password")
                put("password", password)
                q["idle_session_check_interval"]?.takeIf { it.isNotBlank() }?.let { put("idle_session_check_interval", duration(it)) }
                q["idle_session_timeout"]?.takeIf { it.isNotBlank() }?.let { put("idle_session_timeout", duration(it)) }
                q["min_idle_session"]?.toIntOrNull()?.takeIf { it > 0 }?.let { put("min_idle_session", it) }
                tls(q, TlsKind.Tcp)
            }
            "hysteria" -> buildJsonObject {
                put("type", "hysteria")
                put("tag", name)
                server(parts, 443)
                q["mport"]?.takeIf { it.isNotBlank() }?.let { ports ->
                    putJsonArray("server_ports") {
                        (SingBoxConvert.portRangesOf(ports) ?: ports.split(',').map { it.trim().replace('-', ':') }).forEach { add(it) }
                    }
                }
                (q["hop_interval"] ?: q["hopInterval"])?.takeIf { it.isNotBlank() }?.let { put("hop_interval", duration(it)) }
                (q["auth"] ?: q["auth_str"])?.takeIf { it.isNotEmpty() }?.let { put("auth_str", it) }
                put("up_mbps", q["upmbps"]?.toIntOrNull() ?: 10)
                put("down_mbps", q["downmbps"]?.toIntOrNull() ?: 50)
                (q["obfsParam"] ?: q["obfs"])?.takeIf { it.isNotBlank() && it != "xplus" }?.let { put("obfs", it) }
                q["recv_window_conn"]?.toLongOrNull()?.takeIf { it > 0 }?.let { put("recv_window_conn", it) }
                q["recv_window"]?.toLongOrNull()?.takeIf { it > 0 }?.let { put("recv_window", it) }
                if (q["disable_mtu_discovery"]?.truthy() == true) put("disable_mtu_discovery", true)
                val tlsQuery = q.toMutableMap()
                q["peer"]?.let { tlsQuery["sni"] = it }
                tls(tlsQuery, TlsKind.Quic, alpnDefault = "hysteria")
            }
            "naive+https", "naive+quic" -> buildJsonObject {
                put("type", "naive")
                put("tag", name)
                server(parts, 443)
                val (user, password) = splitCredentials(parts.userInfo)
                if (user.isNotBlank()) put("username", user)
                if (password.isNotBlank()) put("password", password)
                (q["insecure-concurrency"] ?: q["insecure_concurrency"])?.toIntOrNull()?.takeIf { it > 0 }
                    ?.let { put("insecure_concurrency", it) }
                (q["extra-headers"] ?: q["extra_headers"])?.let { headersOf(it) }?.takeIf { it.isNotEmpty() }?.let { headers ->
                    putJsonObject("extra_headers") { headers.forEach { (k, v) -> put(k, v) } }
                }
                if (scheme == "naive+quic") {
                    put("quic", true)
                    q["quic_congestion_control"]?.takeIf { it.isNotBlank() }?.let { put("quic_congestion_control", it) }
                }
                tls(q, TlsKind.Naive)
            }
            "socks4", "socks4a" -> buildJsonObject {
                put("type", "socks")
                put("tag", name)
                server(parts, 1080)
                put("version", if (scheme == "socks4a") "4a" else "4")
                parts.userInfo.substringBefore(':').takeIf { it.isNotBlank() }?.let { put("username", it) }
            }
            else -> throw ConfigParseException("Unsupported link scheme")
        }
        return Parsed(name, outbound)
    }

    fun formEditable(outbound: JsonObject): Boolean {
        val type = outbound.str("type") ?: return false
        if (type !in FORM_TYPES) return false
        val name = outbound.str("tag") ?: "server"
        val link = build(outbound, name) ?: return false
        val back = runCatching { parse(link).outbound }.getOrNull() ?: return false
        return back == JsonObject(outbound + ("tag" to JsonPrimitive(name)))
    }

    fun build(outbound: JsonObject, name: String): String? {
        val type = outbound.str("type") ?: return null
        val server = outbound.str("server") ?: return null
        val port = outbound.int("server_port") ?: return null
        val host = if (':' in server && !server.startsWith("[")) "[$server]" else server
        val tls = outbound["tls"] as? JsonObject
        val fragment = "#" + encode(name)
        fun queryString(query: List<Pair<String, String>>) =
            if (query.isEmpty()) "" else "?" + query.joinToString("&") { (k, v) -> "$k=${encode(v)}" }
        return when (type) {
            "tuic" -> {
                val uuid = outbound.str("uuid") ?: return null
                val password = outbound.str("password").orEmpty()
                val query = mutableListOf<Pair<String, String>>()
                outbound.str("congestion_control")?.let { query += "congestion_control" to it }
                outbound.str("udp_relay_mode")?.let { query += "udp_relay_mode" to it }
                if (outbound.bool("udp_over_stream") == true) query += "udp_over_stream" to "1"
                if (outbound.bool("zero_rtt_handshake") == true) query += "zero_rtt_handshake" to "1"
                outbound.str("heartbeat")?.let { query += "heartbeat" to it }
                tlsQuery(tls, query, TlsKind.Quic, sniKey = "sni")
                "tuic://${encode(uuid)}:${encode(password)}@$host:$port${queryString(query)}$fragment"
            }
            "anytls" -> {
                val password = outbound.str("password") ?: return null
                val query = mutableListOf<Pair<String, String>>()
                outbound.str("idle_session_check_interval")?.let { query += "idle_session_check_interval" to it }
                outbound.str("idle_session_timeout")?.let { query += "idle_session_timeout" to it }
                outbound.int("min_idle_session")?.let { query += "min_idle_session" to it.toString() }
                tlsQuery(tls, query, TlsKind.Tcp, sniKey = "sni")
                "anytls://${encode(password)}@$host:$port${queryString(query)}$fragment"
            }
            "hysteria" -> {
                val query = mutableListOf<Pair<String, String>>()
                outbound.str("auth_str")?.let { query += "auth" to it }
                outbound.int("up_mbps")?.let { query += "upmbps" to it.toString() }
                outbound.int("down_mbps")?.let { query += "downmbps" to it.toString() }
                outbound.str("obfs")?.let { query += "obfsParam" to it }
                (outbound["server_ports"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { ports -> query += "mport" to ports.joinToString(",") { p -> p.replace(':', '-').let { r -> if (r.substringBefore('-') == r.substringAfter('-')) r.substringBefore('-') else r } } }
                outbound.str("hop_interval")?.let { query += "hop_interval" to it }
                outbound.long("recv_window_conn")?.let { query += "recv_window_conn" to it.toString() }
                outbound.long("recv_window")?.let { query += "recv_window" to it.toString() }
                if (outbound.bool("disable_mtu_discovery") == true) query += "disable_mtu_discovery" to "1"
                tlsQuery(tls, query, TlsKind.Quic, sniKey = "peer")
                "hysteria://$host:$port${queryString(query)}$fragment"
            }
            "naive" -> {
                val quic = outbound.bool("quic") == true
                val scheme = if (quic) "naive+quic" else "naive+https"
                val user = outbound.str("username")
                val password = outbound.str("password")
                val credentials = when {
                    user != null && password != null -> "${encode(user)}:${encode(password)}@"
                    user != null -> "${encode(user)}@"
                    password != null -> ":${encode(password)}@"
                    else -> ""
                }
                val query = mutableListOf<Pair<String, String>>()
                outbound.int("insecure_concurrency")?.let { query += "insecure-concurrency" to it.toString() }
                (outbound["extra_headers"] as? JsonObject)?.takeIf { it.isNotEmpty() }?.let { headers ->
                    query += "extra-headers" to headers.entries.joinToString("\r\n") { (k, v) -> "$k: ${(v as? JsonPrimitive)?.contentOrNull.orEmpty()}" }
                }
                if (quic) outbound.str("quic_congestion_control")?.let { query += "quic_congestion_control" to it }
                tlsQuery(tls, query, TlsKind.Naive, sniKey = "sni")
                "$scheme://$credentials$host:$port${queryString(query)}$fragment"
            }
            "socks" -> {
                val version = outbound.str("version") ?: return null
                val scheme = when (version) {
                    "4" -> "socks4"
                    "4a" -> "socks4a"
                    else -> return null
                }
                val user = outbound.str("username")?.let { "${encode(it)}@" }.orEmpty()
                "$scheme://$user$host:$port$fragment"
            }
            else -> null
        }
    }

    private fun tlsQuery(tls: JsonObject?, query: MutableList<Pair<String, String>>, kind: TlsKind, sniKey: String) {
        if (tls == null) return
        tls.str("server_name")?.let { query += sniKey to it }
        if (kind == TlsKind.Naive) {
            echQuery(tls)?.let { query += "ech" to it }
            return
        }
        if (tls.bool("insecure") == true) query += "insecure" to "1"
        if (tls.bool("disable_sni") == true) query += "disable_sni" to "1"
        (tls["alpn"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?.takeIf { it.isNotEmpty() }?.let { query += "alpn" to it.joinToString(",") }
        if (kind == TlsKind.Tcp) {
            (tls["utls"] as? JsonObject)?.takeIf { it.bool("enabled") == true }?.str("fingerprint")?.let { query += "fp" to it }
        }
        echQuery(tls)?.let { query += "ech" to it }
    }

    private fun echQuery(tls: JsonObject): String? {
        val ech = tls["ech"] as? JsonObject ?: return null
        if (ech.bool("enabled") != true) return null
        val config = (ech["config"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?.filterNot { it.startsWith("-----") }?.joinToString("")
        return config?.takeIf { it.isNotBlank() } ?: "1"
    }

    private fun JsonObjectBuilder.server(parts: LinkParts, defaultPort: Int) {
        put("server", parts.host)
        put("server_port", parts.port ?: defaultPort)
    }

    private fun JsonObjectBuilder.tls(q: Map<String, String>, kind: TlsKind, alpnDefault: String? = null) {
        val sni = (q["sni"] ?: q["peer"] ?: q["servername"])?.takeIf { it.isNotBlank() }
        val insecure = kind != TlsKind.Naive &&
            (q["insecure"] ?: q["allow_insecure"] ?: q["allowInsecure"] ?: q["skip-cert-verify"])?.truthy() == true
        val alpn = if (kind == TlsKind.Naive) emptyList() else
            (q["alpn"]?.takeIf { it.isNotBlank() } ?: alpnDefault)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        val ech = q["ech"]?.trim()?.takeIf { it.isNotEmpty() && it != "0" && !it.equals("false", ignoreCase = true) }
        putJsonObject("tls") {
            put("enabled", true)
            sni?.let { put("server_name", it) }
            if (insecure) put("insecure", true)
            if (kind != TlsKind.Naive && q["disable_sni"]?.truthy() == true) put("disable_sni", true)
            if (alpn.isNotEmpty()) putJsonArray("alpn") { alpn.forEach { add(it) } }
            if (kind == TlsKind.Tcp) {
                q["fp"]?.trim()?.lowercase()?.takeIf { it in SingBoxTuning.FINGERPRINTS }?.let { fp ->
                    putJsonObject("utls") {
                        put("enabled", true)
                        put("fingerprint", fp)
                    }
                }
            }
            if (ech != null) {
                putJsonObject("ech") {
                    put("enabled", true)
                    if (!ech.truthy()) putJsonArray("config") {
                        add(ECH_BEGIN)
                        add(ech)
                        add(ECH_END)
                    }
                }
            }
        }
    }

    private fun duration(value: String): String {
        val text = value.trim()
        return if (text.all { it.isDigit() }) "${text}s" else text
    }

    private fun headersOf(raw: String): Map<String, String> =
        raw.split("\r\n", "\n", ";").mapNotNull { line ->
            val name = line.substringBefore(':', "").trim()
            if (name.isEmpty()) null else name to line.substringAfter(':', "").trim()
        }.toMap()

    private fun splitCredentials(userInfo: String): Pair<String, String> =
        if (':' in userInfo) userInfo.substringBefore(':') to userInfo.substringAfter(':') else userInfo to ""

    private fun String.truthy(): Boolean = this == "1" || equals("true", ignoreCase = true)

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.takeIf { it.isNotBlank() }
    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }
    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }
    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.contentOrNull?.let { it == "true" }

    private fun queryOf(rest: String): Map<String, String> =
        rest.substringAfter('?', "").substringBefore('#').split('&').mapNotNull {
            if (it.isEmpty()) null else percentDecode(it.substringBefore('=')) to percentDecode(it.substringAfter('=', ""))
        }.toMap()

    private fun encode(value: String): String = buildString {
        value.encodeToByteArray().forEach { byte ->
            val c = byte.toInt().toChar()
            if (c.isLetterOrDigit() && c.code < 128 || c in "-._~") append(c)
            else append('%').append(((byte.toInt() and 0xff) or 0x100).toString(16).substring(1).uppercase())
        }
    }

    internal fun percentDecode(value: String): String {
        if ('%' !in value) return value
        val bytes = ArrayList<Byte>(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            val hex = if (c == '%' && i + 2 < value.length) value.substring(i + 1, i + 3).toIntOrNull(16) else null
            if (hex != null) {
                bytes += hex.toByte()
                i += 3
            } else {
                c.toString().encodeToByteArray().forEach { bytes += it }
                i++
            }
        }
        return bytes.toByteArray().decodeToString()
    }

    private data class LinkParts(
        val userInfo: String,
        val host: String,
        val port: Int?,
        val query: Map<String, String>,
        val fragment: String,
    ) {
        companion object {
            fun of(rest: String): LinkParts {
                val fragment = percentDecode(rest.substringAfter('#', ""))
                val beforeFragment = rest.substringBefore('#')
                val query = queryOf(beforeFragment)
                val authority = beforeFragment.substringBefore('?').substringBefore('/')
                val userInfo = if ('@' in authority) percentDecode(authority.substringBeforeLast('@')) else ""
                val hostPort = authority.substringAfterLast('@')
                val (host, port) = if (hostPort.startsWith("[")) {
                    hostPort.substringAfter('[').substringBefore(']') to
                        hostPort.substringAfter("]", "").removePrefix(":").toIntOrNull()
                } else if (hostPort.count { it == ':' } == 1) {
                    hostPort.substringBefore(':') to hostPort.substringAfter(':').toIntOrNull()
                } else {
                    hostPort to null
                }
                return LinkParts(userInfo, host, port, query, fragment)
            }
        }
    }
}
