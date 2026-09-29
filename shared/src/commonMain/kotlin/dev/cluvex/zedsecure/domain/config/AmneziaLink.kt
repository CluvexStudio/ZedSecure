package dev.cluvex.zedsecure.domain.config

import dev.cluvex.zedsecure.platform.inflateZlib
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

object AmneziaLink {
    private const val PREFIX = "vpn://"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    data class Item(

        val name: String,

        val text: String,
    )

    sealed interface Payload {
        data class Configs(val items: List<Item>) : Payload

        data object Backup : Payload

        data object Subscription : Payload

        data class Unusable(val reason: String) : Payload

        data object Unsupported : Payload
    }

    class UnsupportedException(val reason: Reason, message: String) : IllegalArgumentException(message)

    enum class Reason {
        Backup,

        Subscription,

        NoRunnableContainer,
    }

    fun isAmneziaLink(text: String): Boolean = text.trim().startsWith(PREFIX, ignoreCase = true)

    fun isAmneziaJson(text: String): Boolean {
        val t = text.trim()
        if (!t.startsWith("{")) return false
        return t.contains("\"containers\"") || t.contains("\"api_key\"") ||
            t.contains("\"auth_data\"") || t.contains("\"api_config\"") ||
            (t.contains("\"hostName\"") && t.contains("\"userName\"") && t.contains("\"password\""))
    }

    fun isAmneziaBlob(text: String): Boolean {
        val t = text.trim()
        if (isAmneziaLink(t) || isAmneziaJson(t) || !looksLikeBase64(t)) return false
        val decoded = decode(t) ?: return false
        return isAmneziaJson(decoded) || decoded.contains("Servers/serversList")
    }

    fun parse(text: String): Payload {
        val body = bodyOf(text) ?: return Payload.Unsupported

        if (body.contains("Servers/serversList")) return Payload.Backup
        if (!isAmneziaJson(body)) {
            return Payload.Configs(listOf(Item(name = "", text = body)))
        }

        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
            ?: return Payload.Unsupported

        val containers = (root["containers"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
        if (containers.isEmpty()) {
            val account = listOf("api_key", "auth_data", "api_config", "config_version")
                .any { root.containsKey(it) }
            return if (account) Payload.Subscription else Payload.Unsupported
        }

        val items = itemsOf(root, containers)
        if (items.isNotEmpty()) return Payload.Configs(items)

        val names = containers.mapNotNull { it.string("container") }.distinct()
        return Payload.Unusable(names.joinToString(", ").ifBlank { "unknown" })
    }

    fun extractWireguardConf(text: String): String? =
        (parse(text) as? Payload.Configs)?.items
            ?.map { it.text }
            ?.firstOrNull { ConfigParser.looksLikeWireguardConf(it) }

    private fun bodyOf(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        if (isAmneziaLink(trimmed)) return decode(trimmed)
        if (isAmneziaJson(trimmed) || !looksLikeBase64(trimmed)) return trimmed

        val decoded = decode(trimmed) ?: return trimmed
        return if (isAmneziaJson(decoded) || decoded.contains("Servers/serversList")) decoded else trimmed
    }

    private fun looksLikeBase64(text: String): Boolean = text.length >= 8 && text.all {
        it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' ||
            it == '-' || it == '_' || it == '+' || it == '/' || it == '='
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun decode(text: String): String? {
        val body = text.trim().removePrefix(PREFIX).removePrefix(PREFIX.uppercase()).trim()
        if (body.isEmpty()) return null
        val raw = runCatching {
            Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
                .decode(body.replace('+', '-').replace('/', '_').trimEnd('='))
        }.getOrNull() ?: return null

        if (raw.size > 4) {
            inflateZlib(raw.copyOfRange(4, raw.size))?.let { return it.decodeToString() }
        }
        inflateZlib(raw)?.let { return it.decodeToString() }
        return runCatching { raw.decodeToString() }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private val PROTOCOL_KEYS = listOf(
        "awg", "wireguard", "openvpn", "xray", "ssxray", "ikev2", "socks5proxy", "cloak", "shadowsocks",
    )

    private val UNRUNNABLE = setOf("cloak", "shadowsocks")

    private fun itemsOf(root: JsonObject, containers: List<JsonObject>): List<Item> {
        val preferred = root.string("defaultContainer")
        val ordered = containers.sortedBy { if (it.string("container") == preferred) 0 else 1 }
        val label = root.string("description")?.takeIf { it.isNotBlank() }
            ?: root.string("hostName")?.takeIf { it.isNotBlank() }
            ?: "Amnezia"
        val host = root.string("hostName").orEmpty()
        val dns = listOfNotNull(
            root.string("dns1")?.takeIf { it.isNotBlank() },
            root.string("dns2")?.takeIf { it.isNotBlank() },
        )

        val found = ordered.mapNotNull { container ->
            val containerName = container.string("container").orEmpty()
            if (UNRUNNABLE.any { containerName.contains(it, ignoreCase = true) }) return@mapNotNull null

            val protoKey = PROTOCOL_KEYS.firstOrNull { key ->
                containerName.contains(key, ignoreCase = true) && container[key] is JsonObject
            } ?: PROTOCOL_KEYS.firstOrNull { container[it] is JsonObject }
            ?: return@mapNotNull null
            if (protoKey in UNRUNNABLE) return@mapNotNull null
            val proto = container[protoKey] as? JsonObject ?: return@mapNotNull null
            val text = configOf(protoKey, proto, host, dns, label) ?: return@mapNotNull null
            protoKey to text
        }

        val many = found.size > 1
        return found.map { (protoKey, text) ->
            Item(name = if (many) "$label · ${protoLabel(protoKey)}" else label, text = text)
        }
    }

    private fun protoLabel(key: String): String = when (key) {
        "awg" -> "AmneziaWG"
        "wireguard" -> "WireGuard"
        "openvpn" -> "OpenVPN"
        "xray" -> "Xray"
        "ssxray" -> "Shadowsocks"
        "ikev2" -> "IKEv2"
        "socks5proxy" -> "SOCKS5"
        else -> key
    }

    private fun configOf(
        protoKey: String,
        proto: JsonObject,
        host: String,
        dns: List<String>,
        label: String,
    ): String? {
        val last = proto.string("last_config")?.takeIf { it.isNotBlank() }
        val lastJson = last?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() }

        if (lastJson != null) {
            if (lastJson.containsKey("outbounds") && !lastJson.containsKey("config")) return last
            lastJson.string("config")?.takeIf { it.isNotBlank() }?.let { return it }
        } else if (last != null) {
            return last
        }

        val fields = lastJson ?: proto
        return when (protoKey) {
            "awg", "wireguard" -> wireguardConfFrom(fields, proto, host, dns)
            "ikev2" -> ikev2LinkFrom(fields, host, label)
            "socks5proxy" -> socksLinkFrom(fields, host, label)
            else -> null
        }
    }

    private fun wireguardConfFrom(
        fields: JsonObject,
        proto: JsonObject,
        fallbackHost: String,
        dns: List<String>,
    ): String? {
        val privateKey = fields.string("client_priv_key")?.takeIf { it.isNotBlank() } ?: return null
        val serverKey = fields.string("server_pub_key")?.takeIf { it.isNotBlank() } ?: return null
        val host = fields.string("hostName")?.takeIf { it.isNotBlank() }
            ?: fallbackHost.takeIf { it.isNotBlank() }
            ?: return null
        val port = fields.string("port")?.takeIf { it.isNotBlank() }
            ?: proto.string("port")?.takeIf { it.isNotBlank() }
            ?: "51820"
        val address = fields.string("client_ip")?.takeIf { it.isNotBlank() }
            ?.let { if (it.contains('/')) it else "$it/32" }
        val allowed = fields.strings("allowed_ips").takeIf { it.isNotEmpty() }
            ?: listOf("0.0.0.0/0", "::/0")

        return buildString {
            appendLine("[Interface]")
            appendLine("PrivateKey = $privateKey")
            address?.let { appendLine("Address = $it") }
            if (dns.isNotEmpty()) appendLine("DNS = ${dns.joinToString(", ")}")
            fields.string("mtu")?.takeIf { it.isNotBlank() }?.let { appendLine("MTU = $it") }
            AwgConfig.KEYS.forEach { key ->
                val confName = AwgConfig.CONF_NAME[key] ?: return@forEach
                val value = fields.string(confName)?.takeIf { it.isNotBlank() }
                    ?: fields.string(key)?.takeIf { it.isNotBlank() }
                if (value != null) appendLine("$confName = $value")
            }
            appendLine()
            appendLine("[Peer]")
            appendLine("PublicKey = $serverKey")
            fields.string("psk_key")?.takeIf { it.isNotBlank() }?.let { appendLine("PresharedKey = $it") }
            appendLine("AllowedIPs = ${allowed.joinToString(", ")}")
            appendLine("Endpoint = ${endpoint(host, port)}")
            fields.string("persistent_keep_alive")?.takeIf { it.isNotBlank() }
                ?.let { appendLine("PersistentKeepalive = $it") }
        }
    }

    private fun ikev2LinkFrom(fields: JsonObject, fallbackHost: String, label: String): String? {
        val host = fields.string("hostName")?.takeIf { it.isNotBlank() }
            ?: fallbackHost.takeIf { it.isNotBlank() }
            ?: return null
        val user = fields.string("userName")?.takeIf { it.isNotBlank() } ?: return null
        val password = fields.string("password").orEmpty()
        val profile = Ikev2Profile(
            server = host,
            authType = Ikev2Auth.EAP_MSCHAPV2,
            username = user,
            password = if (password.isNotEmpty()) Ikev2Profile.sealPassword(password) else "",
        )
        return ZedLink.build(label, ProfileSource.Ikev2(profile))
    }

    private fun socksLinkFrom(fields: JsonObject, fallbackHost: String, label: String): String? {
        val host = fields.string("hostName")?.takeIf { it.isNotBlank() }
            ?: fallbackHost.takeIf { it.isNotBlank() }
            ?: return null
        val port = fields.string("port")?.takeIf { it.isNotBlank() } ?: return null
        val user = fields.string("userName").orEmpty()
        val password = fields.string("password").orEmpty()
        val credentials = if (user.isNotEmpty()) {
            Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
                .encode("$user:$password".encodeToByteArray()) + "@"
        } else {
            ""
        }
        return "socks://$credentials${endpoint(host, port)}#${label.trim()}"
    }

    private fun endpoint(host: String, port: String): String =
        if (host.contains(':') && !host.startsWith("[")) "[$host]:$port" else "$host:$port"

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.strings(key: String): List<String> = when (val v = this[key]) {
        is JsonArray -> v.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.filter { it.isNotBlank() }
        is JsonPrimitive -> v.contentOrNull?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        else -> emptyList()
    }
}
