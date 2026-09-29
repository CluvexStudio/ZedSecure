package dev.cluvex.zedsecure.domain.config

sealed interface DeepLinkRequest {
    data class Subscription(val url: String, val name: String?) : DeepLinkRequest

    data class ConfigText(val text: String) : DeepLinkRequest
}

object DeepLinkParser {
    val SCHEMES: Set<String> = setOf(
        "zedsecure", "v2rayng", "hiddify", "sing-box", "clash", "clashmeta", "v2raytun", "streisand",
        "happ", "vless", "vmess", "trojan", "ss", "hysteria", "hysteria2", "hy2", "wireguard",
        "socks", "socks5", "snispoof",
    )

    private val CONFIG_SCHEMES = setOf(
        "vless", "vmess", "trojan", "ss", "hysteria", "hysteria2", "hy2", "wireguard", "socks", "socks5",
        "snispoof",
    )

    private const val MAX_LENGTH = 64 * 1024

    private val NAME_KEYS = listOf("name", "remarks", "remark", "title")

    fun parse(uri: String): DeepLinkRequest? {
        val link = uri.trim()
        if (link.isEmpty() || link.length > MAX_LENGTH) return null
        val sep = link.indexOf("://")
        if (sep <= 0) return null
        val scheme = link.substring(0, sep).lowercase()
        if (scheme !in SCHEMES) return null
        val rest = link.substring(sep + 3)
        if (scheme in CONFIG_SCHEMES) return configText(link)

        val rawFragment = rest.substringAfter('#', "").takeIf { it.isNotEmpty() }
        val fragment = rawFragment?.let(::decode)
        val beforeFragment = rest.substringBefore('#')
        val action = beforeFragment.substringBefore('?').substringBefore('/').lowercase()
        val query = beforeFragment.substringAfter('?', "")

        return when (scheme) {
            "zedsecure" -> when (action) {
                "import", "sub", "subscription", "install-config", "install-sub" ->
                    queryUrl(query)?.let { payload(it, queryName(query) ?: fragment, rawFragment) }
                        ?: payload(rawRemainder(rest, "$action/"), queryName(query))

                else -> if (ZedLink.isZedLink(link)) configText(link) else null
            }
            "v2rayng" -> when (action) {
                "install-config", "install-sub" -> payload(queryUrl(query), queryName(query) ?: fragment, rawFragment)
                else -> null
            }
            "hiddify" -> when (action) {
                "import" -> payload(rawRemainder(rest, "import/"), null)
                "install-config", "install-sub" -> payload(queryUrl(query), queryName(query) ?: fragment, rawFragment)
                else -> null
            }
            "sing-box" -> when (action) {
                "import-remote-profile" -> payload(queryUrl(query), queryName(query) ?: fragment, rawFragment)
                else -> null
            }
            "clash", "clashmeta" -> when (action) {
                "install-config" -> payload(queryUrl(query), queryName(query) ?: fragment, rawFragment)
                else -> null
            }
            "v2raytun", "streisand", "happ" -> when (action) {
                "import", "add" -> payload(rawRemainder(rest, "$action/"), null)
                else -> null
            }
            else -> null
        }
    }

    private fun payload(raw: String?, name: String?, rawFragment: String? = null): DeepLinkRequest? {
        val value = raw?.trim()?.let(::decodeIfEncoded)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)) {
            val url = value.substringBefore('#')
            val label = name ?: value.substringAfter('#', "").takeIf { it.isNotEmpty() }?.let(::decode)
            if (url.any(Char::isWhitespace) || url.length <= "https://".length) return null
            return DeepLinkRequest.Subscription(url, label?.trim()?.takeIf { it.isNotEmpty() })
        }

        val config = if ('#' !in value && rawFragment != null) "$value#$rawFragment" else value
        return configText(config)
    }

    private fun configText(text: String): DeepLinkRequest? {
        val t = text.trim()
        val readable = ConfigParser.isSupportedLink(t) && !t.startsWith("http", ignoreCase = true) ||
            SniSpoofLink.isSniSpoofLink(t) || ZedLink.isZedLink(t)
        return if (readable) DeepLinkRequest.ConfigText(t) else null
    }

    private fun queryUrl(query: String): String? {
        if (query.isEmpty()) return null
        val start = when {
            query.startsWith("url=", ignoreCase = true) -> 4
            else -> query.indexOf("&url=", ignoreCase = true).takeIf { it >= 0 }?.plus(5) ?: return null
        }
        val tail = query.substring(start)
        val end = NAME_KEYS.mapNotNull { key -> tail.indexOf("&$key=", ignoreCase = true).takeIf { it >= 0 } }
            .minOrNull() ?: tail.length
        return tail.substring(0, end).takeIf { it.isNotEmpty() }
    }

    private fun queryName(query: String): String? {
        for (key in NAME_KEYS) {
            val marker = "$key="
            val at = when {
                query.startsWith(marker, ignoreCase = true) -> 0
                else -> query.indexOf("&$marker", ignoreCase = true).takeIf { it >= 0 }?.plus(1) ?: continue
            }
            val value = query.substring(at + marker.length).substringBefore('&')
            decode(value).trim().takeIf { it.isNotEmpty() }?.let { return it }
        }
        return null
    }

    private fun rawRemainder(rest: String, prefix: String): String? {
        if (!rest.startsWith(prefix, ignoreCase = true)) return null
        return rest.substring(prefix.length).takeIf { it.isNotEmpty() }
    }

    private fun decodeIfEncoded(value: String): String {
        val head = value.take(24)
        return if (!head.contains("://") && head.contains("%3A", ignoreCase = true)) decode(value) else value
    }

    internal fun decode(value: String): String {
        if ('%' !in value) return value
        val bytes = ArrayList<Byte>(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '%' && i + 2 < value.length && value[i + 1].isHexDigit() && value[i + 2].isHexDigit()) {
                bytes += value.substring(i + 1, i + 3).toInt(16).toByte()
                i += 3
            } else {
                c.toString().encodeToByteArray().forEach { bytes += it }
                i++
            }
        }
        return bytes.toByteArray().decodeToString()
    }

    private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
}

data class DeepLinkPreview(

    val name: String?,

    val detail: String,
) {
    companion object {
        fun of(request: DeepLinkRequest): DeepLinkPreview = when (request) {
            is DeepLinkRequest.Subscription -> DeepLinkPreview(
                name = request.name,
                detail = request.url.substringAfter("://").substringBefore('/').substringBefore('?')
                    .substringAfterLast('@'),
            )
            is DeepLinkRequest.ConfigText -> configPreview(request.text)
        }

        private fun configPreview(text: String): DeepLinkPreview {
            ZedLink.parse(text)?.let { payload ->
                return DeepLinkPreview(payload.name.takeIf { it.isNotBlank() }, sourceLabel(payload.source))
            }
            if (SniSpoofLink.isSniSpoofLink(text)) {
                return DeepLinkPreview(SniSpoofLink.parse(text)?.first?.takeIf { it.isNotBlank() }, "SNI spoof")
            }
            if (SingBoxLinks.handles(text)) {
                val singBox = runCatching { SingBoxLinks.parse(text) }.getOrNull()
                val server = singBox?.let { SingBoxJson.servers(it.fragment).firstOrNull() }
                return DeepLinkPreview(
                    name = singBox?.name?.takeIf { it.isNotBlank() },
                    detail = server?.let { "${it.label} · ${it.address}:${it.port}" }
                        ?: text.substringBefore("://").uppercase(),
                )
            }
            val parsed = runCatching { ConfigParser.parse(text) }.getOrNull()
            return DeepLinkPreview(
                name = parsed?.remark?.takeIf { it.isNotBlank() },
                detail = parsed?.let { c -> "${c.protocol.label} · ${c.address}:${c.port}" }
                    ?: text.substringBefore("://").uppercase(),
            )
        }

        private fun sourceLabel(source: ProfileSource): String = when (source) {
            is ProfileSource.Psiphon -> "Psiphon"
            is ProfileSource.DnsTunnel -> if (source.settings.isVaydns) "VayDNS" else "DNSTT"
            is ProfileSource.Tor -> "Tor"
            is ProfileSource.Ssh -> "SSH"
            is ProfileSource.MasterDns -> "MasterDNS"
            is ProfileSource.OpenConnect -> "OpenConnect"
            is ProfileSource.Ikev2 -> "IKEv2"
            is ProfileSource.ProxyChain -> "Proxy chain"
            is ProfileSource.CrossChain -> "Cross chain"
            is ProfileSource.SniSpoof -> "SNI spoof"
            is ProfileSource.AutoSelect -> "Auto-select"
            is ProfileSource.SingBox -> "sing-box"
            is ProfileSource.SingBoxConfig -> "sing-box config"
            is ProfileSource.Link, is ProfileSource.RawJson, is ProfileSource.Sealed -> "Config"
        }
    }
}
