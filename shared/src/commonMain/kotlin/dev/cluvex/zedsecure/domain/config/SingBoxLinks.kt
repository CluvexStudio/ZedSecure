package dev.cluvex.zedsecure.domain.config

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object SingBoxLinks {
    private val SCHEMES = setOf("tuic", "anytls", "hysteria", "naive+https", "naive+quic", "socks4", "socks4a")

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
                q["congestion_control"]?.let { put("congestion_control", it) }
                (q["udp_relay_mode"] ?: q["udp-relay-mode"])?.let { put("udp_relay_mode", it) }
                q["zero_rtt_handshake"]?.let { put("zero_rtt_handshake", it.truthy()) }
                q["heartbeat"]?.let { put("heartbeat", it) }
                tls(q, alpnDefault = "h3", always = true)
            }
            "anytls" -> buildJsonObject {
                put("type", "anytls")
                put("tag", name)
                server(parts, 443)
                val password = parts.userInfo.ifBlank { q["password"].orEmpty() }
                if (password.isBlank()) throw ConfigParseException("Invalid anytls link: no password")
                put("password", password)
                tls(q, always = true)
            }
            "hysteria" -> buildJsonObject {
                put("type", "hysteria")
                put("tag", name)
                server(parts, 443)
                q["mport"]?.let { ports -> putJsonArray("server_ports") { ports.split(',').forEach { add(it.trim().replace('-', ':')) } } }
                (q["auth"] ?: q["auth_str"])?.let { put("auth_str", it) }
                put("up_mbps", q["upmbps"]?.toIntOrNull() ?: 10)
                put("down_mbps", q["downmbps"]?.toIntOrNull() ?: 50)
                (q["obfsParam"] ?: q["obfs"])?.takeIf { it.isNotBlank() && it != "xplus" }?.let { put("obfs", it) }
                val tlsQuery = q.toMutableMap()
                q["peer"]?.let { tlsQuery["sni"] = it }
                tls(tlsQuery, alpnDefault = "hysteria", always = true)
            }
            "naive+https", "naive+quic" -> buildJsonObject {
                put("type", "naive")
                put("tag", name)
                server(parts, 443)
                val (user, password) = splitCredentials(parts.userInfo)
                if (user.isNotBlank()) put("username", user)
                if (password.isNotBlank()) put("password", password)
                if (scheme == "naive+quic") put("quic", true)
                tls(q, always = true)
            }
            "socks4", "socks4a" -> buildJsonObject {
                put("type", "socks")
                put("tag", name)
                server(parts, 1080)
                put("version", if (scheme == "socks4a") "4a" else "4")
                if (parts.userInfo.isNotBlank()) put("username", parts.userInfo.substringBefore(':'))
            }
            else -> throw ConfigParseException("Unsupported link scheme")
        }
        return Parsed(name, outbound)
    }

    fun build(outbound: JsonObject, name: String): String? {
        val type = outbound.str("type") ?: return null
        val server = outbound.str("server") ?: return null
        val port = outbound.int("server_port") ?: return null
        val host = if (':' in server && !server.startsWith("[")) "[$server]" else server
        val tls = outbound["tls"] as? JsonObject
        val fragment = "#" + encode(name)
        fun tlsQuery(query: MutableList<Pair<String, String>>) {
            tls?.str("server_name")?.let { query += "sni" to it }
            if (tls?.bool("insecure") == true) query += "insecure" to "1"
            (tls?.get("alpn") as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                ?.takeIf { it.isNotEmpty() }?.let { query += "alpn" to it.joinToString(",") }
        }
        fun queryString(query: List<Pair<String, String>>) =
            if (query.isEmpty()) "" else "?" + query.joinToString("&") { (k, v) -> "$k=${encode(v)}" }
        return when (type) {
            "tuic" -> {
                val uuid = outbound.str("uuid") ?: return null
                val password = outbound.str("password").orEmpty()
                val query = mutableListOf<Pair<String, String>>()
                outbound.str("congestion_control")?.let { query += "congestion_control" to it }
                outbound.str("udp_relay_mode")?.let { query += "udp_relay_mode" to it }
                tlsQuery(query)
                "tuic://${encode(uuid)}:${encode(password)}@$host:$port${queryString(query)}$fragment"
            }
            "anytls" -> {
                val password = outbound.str("password") ?: return null
                val query = mutableListOf<Pair<String, String>>()
                tlsQuery(query)
                "anytls://${encode(password)}@$host:$port${queryString(query)}$fragment"
            }
            "hysteria" -> {
                val query = mutableListOf<Pair<String, String>>()
                outbound.str("auth_str")?.let { query += "auth" to it }
                outbound.int("up_mbps")?.let { query += "upmbps" to it.toString() }
                outbound.int("down_mbps")?.let { query += "downmbps" to it.toString() }
                outbound.str("obfs")?.let { query += "obfsParam" to it }
                tls?.str("server_name")?.let { query += "peer" to it }
                if (tls?.bool("insecure") == true) query += "insecure" to "1"
                (tls?.get("alpn") as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                    ?.takeIf { it.isNotEmpty() }?.let { query += "alpn" to it.joinToString(",") }
                "hysteria://$host:$port${queryString(query)}$fragment"
            }
            "naive" -> {
                val scheme = if (outbound.bool("quic") == true) "naive+quic" else "naive+https"
                val user = outbound.str("username")
                val password = outbound.str("password")
                val credentials = when {
                    user != null && password != null -> "${encode(user)}:${encode(password)}@"
                    user != null -> "${encode(user)}@"
                    else -> ""
                }
                val query = mutableListOf<Pair<String, String>>()
                tlsQuery(query)
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

    private fun JsonObjectBuilder.server(parts: LinkParts, defaultPort: Int) {
        put("server", parts.host)
        put("server_port", parts.port ?: defaultPort)
    }

    private fun JsonObjectBuilder.tls(q: Map<String, String>, alpnDefault: String? = null, always: Boolean = false) {
        val sni = q["sni"] ?: q["peer"] ?: q["servername"]
        val insecure = (q["insecure"] ?: q["allow_insecure"] ?: q["allowInsecure"] ?: q["skip-cert-verify"])?.truthy() == true
        val alpn = (q["alpn"] ?: alpnDefault)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        if (!always && sni == null && !insecure && alpn.isEmpty()) return
        putJsonObject("tls") {
            put("enabled", true)
            sni?.let { put("server_name", it) }
            if (insecure) put("insecure", true)
            if (q["disable_sni"]?.truthy() == true) put("disable_sni", true)
            if (alpn.isNotEmpty()) putJsonArray("alpn") { alpn.forEach { add(it) } }
            q["fp"]?.takeIf { it.isNotBlank() }?.let { fp ->
                putJsonObject("utls") {
                    put("enabled", true)
                    put("fingerprint", fp)
                }
            }
        }
    }

    private fun splitCredentials(userInfo: String): Pair<String, String> =
        if (':' in userInfo) userInfo.substringBefore(':') to userInfo.substringAfter(':') else userInfo to ""

    private fun String.truthy(): Boolean = this == "1" || equals("true", ignoreCase = true)

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }
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
