package dev.cluvex.zedsecure.domain.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Serializable
data class SubscriptionPreset(
    val address: String = "",
    val fingerprint: String = "",
    val alpn: String = "",
    val finalMask: String = "",
    val cipherSuites: String = "",
    val echConfigList: String = "",
    val core: String = "",
) {
    val isEmpty: Boolean
        get() = listOf(address, fingerprint, alpn, finalMask, cipherSuites, echConfigList, core).all { it.isBlank() }

    fun apply(link: String): String {
        if (isEmpty) return link
        val rewritten = runCatching { SubscriptionPresets.rewrite(link.trim(), this) }.getOrDefault(link)
        return runCatching { SubscriptionPresets.withCore(rewritten, core) }.getOrDefault(rewritten)
    }

    companion object {
        const val ALPN_AUTO = "auto"
        const val CORE_SINGBOX = "singbox"
        const val CORE_XRAY = "xray"
    }
}

internal object SubscriptionPresets {
    private val URL_SCHEMES = setOf("vless", "trojan", "ss", "hysteria2", "hy2", "tuic")
    private val MASK_ONLY_SCHEMES = setOf("wireguard", "wg", "socks", "socks5", "http", "https")
    private val CORE_SCHEMES = setOf("vless", "vmess", "trojan", "ss", "hysteria2", "hy2", "wireguard", "wg", "socks", "socks5", "http", "https")
    private val TLS_SCHEMES = setOf("hysteria2", "hy2", "tuic", "trojan")
    private val HOST_HEADER_NETWORKS = setOf("ws", "httpupgrade", "xhttp", "splithttp", "h2", "http")
    private val CDN_NETWORKS = HOST_HEADER_NETWORKS + "grpc"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun rewrite(link: String, preset: SubscriptionPreset): String {
        val scheme = link.substringBefore("://", "").lowercase()
        return when (scheme) {
            "vmess" -> rewriteVmess(link, preset)
            in URL_SCHEMES -> rewriteUrl(scheme, link, preset)
            in MASK_ONLY_SCHEMES -> preset.finalMask.trim().takeIf { it.isNotEmpty() }?.let { setQuery(link, "fm", it) } ?: link
            else -> link
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun withCore(link: String, core: String): String {
        val wanted = core.trim().lowercase()
        if (wanted != SubscriptionPreset.CORE_SINGBOX && wanted != SubscriptionPreset.CORE_XRAY) return link
        val scheme = link.substringBefore("://", "").lowercase()
        if (scheme !in CORE_SCHEMES) return link
        val singBox = wanted == SubscriptionPreset.CORE_SINGBOX
        val payload = link.substringAfter("://").substringBefore('#')
        if (scheme == "vmess" && '@' !in payload) {
            val obj = json.parseToJsonElement(ConfigParser.decodeBase64Utf8(payload)) as? JsonObject ?: return link
            val rebuilt = buildJsonObject {
                obj.forEach { (k, v) -> if (k != "core") put(k, v) }
                if (singBox) put("core", JsonPrimitive("singbox"))
            }
            val fragment = if ('#' in link) "#" + link.substringAfter('#') else ""
            return "vmess://" + Base64.encode(rebuilt.toString().encodeToByteArray()) + fragment
        }
        return setQuery(link, "core", if (singBox) "singbox" else null)
    }

    private fun setQuery(link: String, key: String, value: String?): String {
        val fragment = if ('#' in link) "#" + link.substringAfter('#') else ""
        val body = link.substringBefore('#')
        val base = body.substringBefore('?')
        val query = body.substringAfter('?', "").split('&').filter { it.isNotEmpty() && it.substringBefore('=') != key }
        val parts = if (value == null) query else query + "$key=${encode(value)}"
        return base + (if (parts.isEmpty()) "" else "?" + parts.joinToString("&")) + fragment
    }

    internal fun autoAlpn(network: String?): String? = when (network?.lowercase()) {
        "ws", "httpupgrade" -> "http/1.1"
        "xhttp", "splithttp" -> "h2,http/1.1"
        else -> null
    }

    private fun rewriteUrl(scheme: String, link: String, preset: SubscriptionPreset): String {
        val fragment = link.substringAfter('#', "").let { if ('#' in link) "#$it" else "" }
        val body = link.substringBefore('#')
        val afterScheme = body.substringAfter("://")
        val authorityEnd = afterScheme.indexOfFirst { it == '/' || it == '?' }.let { if (it < 0) afterScheme.length else it }
        val authority = afterScheme.substring(0, authorityEnd)
        val tail = afterScheme.substring(authorityEnd)
        val path = tail.substringBefore('?')
        val rawQuery = tail.substringAfter('?', "")

        val at = authority.lastIndexOf('@')
        if (at < 0 && scheme == "ss") return link
        val userInfo = if (at >= 0) authority.substring(0, at + 1) else ""
        val hostPort = if (at >= 0) authority.substring(at + 1) else authority
        val (host, port) = splitHostPort(hostPort)

        val query = rawQuery.split('&').filter { it.isNotEmpty() }.map { part ->
            part.substringBefore('=') to part.substringAfter('=', "")
        }.toMutableList()
        fun value(key: String): String? = query.firstOrNull { it.first == key }?.second?.let { decode(it) }
        fun set(key: String, raw: String) {
            val encoded = encode(raw)
            val index = query.indexOfFirst { it.first == key }
            if (index >= 0) query[index] = key to encoded else query += key to encoded
        }
        fun remove(key: String) {
            query.removeAll { it.first == key }
        }

        var newHost = host
        val address = preset.address.trim()
        val cdnCapable = value("security")?.lowercase() != "reality" && value("type")?.lowercase() in CDN_NETWORKS
        if (address.isNotEmpty() && address != host && cdnCapable) {
            newHost = address
            val domain = host.any { it.isLetter() } && ':' !in host
            val usesTls = when (value("security")?.lowercase()) {
                "tls", "reality", "xtls" -> true
                "none" -> false
                else -> scheme in TLS_SCHEMES
            }
            if (domain && usesTls && value("sni").isNullOrBlank()) set("sni", host)
            if (domain && value("type")?.lowercase() in HOST_HEADER_NETWORKS && value("host").isNullOrBlank()) set("host", host)
        }
        val tlsParams = scheme != "ss"
        if (tlsParams) {
            preset.fingerprint.trim().takeIf { it.isNotEmpty() }?.let { set("fp", it) }
            val alpn = preset.alpn.trim()
            when {
                alpn.equals(SubscriptionPreset.ALPN_AUTO, ignoreCase = true) -> autoAlpn(value("type"))?.let { set("alpn", it) }
                alpn.isNotEmpty() -> set("alpn", alpn)
            }
            val ech = preset.echConfigList.trim()
            if (ech.isNotEmpty()) {
                set("ech", ech)
                if (preset.finalMask.isBlank()) remove("fm")
                if (preset.cipherSuites.isBlank()) remove("cs")
            }
            preset.cipherSuites.trim().takeIf { it.isNotEmpty() }?.let { set("cs", it) }
        }
        preset.finalMask.trim().takeIf { it.isNotEmpty() }?.let { set("fm", it) }

        val hostOut = if (':' in newHost && !newHost.startsWith("[")) "[$newHost]" else newHost
        val portOut = port?.let { ":$it" }.orEmpty()
        val queryOut = query.joinToString("&") { (k, v) -> if (v.isEmpty()) k else "$k=$v" }
        return buildString {
            append(link.substringBefore("://")).append("://").append(userInfo).append(hostOut).append(portOut).append(path)
            if (queryOut.isNotEmpty()) append('?').append(queryOut)
            append(fragment)
        }
    }

    private fun splitHostPort(hostPort: String): Pair<String, String?> = when {
        hostPort.startsWith("[") -> {
            val end = hostPort.indexOf(']')
            hostPort.substring(1, end) to hostPort.substring(end + 1).removePrefix(":").takeIf { it.isNotEmpty() }
        }
        hostPort.count { it == ':' } == 1 -> hostPort.substringBefore(':') to hostPort.substringAfter(':')
        else -> hostPort to null
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun rewriteVmess(link: String, preset: SubscriptionPreset): String {
        val payload = link.substringAfter("://").substringBefore('#')
        val decoded = ConfigParser.decodeBase64Utf8(payload)
        val obj = json.parseToJsonElement(decoded) as? JsonObject ?: return link
        fun text(key: String): String? = (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: (obj[key] as? JsonPrimitive)?.toString()?.trim('"')
        val updates = LinkedHashMap<String, String>()
        val address = preset.address.trim()
        val oldHost = text("add").orEmpty()
        if (address.isNotEmpty() && address != oldHost && text("net")?.lowercase() in CDN_NETWORKS) {
            updates["add"] = address
            val domain = oldHost.any { it.isLetter() } && ':' !in oldHost
            if (domain && text("tls")?.lowercase() == "tls" && text("sni").isNullOrBlank()) updates["sni"] = oldHost
            if (domain && text("net")?.lowercase() in HOST_HEADER_NETWORKS && text("host").isNullOrBlank()) updates["host"] = oldHost
        }
        preset.fingerprint.trim().takeIf { it.isNotEmpty() }?.let { updates["fp"] = it }
        val alpn = preset.alpn.trim()
        when {
            alpn.equals(SubscriptionPreset.ALPN_AUTO, ignoreCase = true) -> autoAlpn(text("net"))?.let { updates["alpn"] = it }
            alpn.isNotEmpty() -> updates["alpn"] = alpn
        }
        val ech = preset.echConfigList.trim()
        if (ech.isNotEmpty()) {
            updates["ech"] = ech
            if (preset.finalMask.isBlank() && obj.containsKey("fm")) updates["fm"] = ""
            if (preset.cipherSuites.isBlank() && obj.containsKey("cs")) updates["cs"] = ""
        }
        preset.cipherSuites.trim().takeIf { it.isNotEmpty() }?.let { updates["cs"] = it }
        preset.finalMask.trim().takeIf { it.isNotEmpty() }?.let { updates["fm"] = it }
        if (updates.isEmpty()) return link
        val rebuilt = buildJsonObject {
            obj.forEach { (k, v) -> if (k !in updates) put(k, v) }
            updates.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
        }
        val fragment = if ('#' in link) "#" + link.substringAfter('#') else ""
        return "vmess://" + Base64.encode(rebuilt.toString().encodeToByteArray()) + fragment
    }

    private fun encode(value: String): String = ShareLink.enc(value)

    private fun decode(value: String): String = runCatching { ConfigParser.percentDecode(value) }.getOrDefault(value)
}
