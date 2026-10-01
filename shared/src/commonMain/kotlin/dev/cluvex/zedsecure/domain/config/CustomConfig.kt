package dev.cluvex.zedsecure.domain.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

data class CustomConfigInfo(
    val remarks: String?,
    val protocol: String?,
    val address: String?,
    val port: Int?,
) {
    val label: String
        get() = listOfNotNull(protocol?.uppercase(), "Custom").joinToString(" · ")
}

object CustomConfig {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val PROXY_PROTOCOLS = setOf(
        "vless", "vmess", "trojan", "shadowsocks", "socks", "http",
        "wireguard", "hysteria2", "hysteria",
    )

    fun looksLikeCustomJson(text: String): Boolean {
        val t = Jsonc.strip(text).trim()
        if (!t.startsWith("{") && !t.startsWith("[")) return false

        return t.contains("outbounds") && !SingBoxJson.isSingBox(t)
    }

    fun inspect(rawJson: String): CustomConfigInfo {
        val root = runCatching { json.parseToJsonElement(Jsonc.strip(rawJson)) }.getOrNull()
        val obj = root as? JsonObject ?: return CustomConfigInfo(null, null, null, null)

        val remarks = obj["remarks"]?.let {
            runCatching { it.jsonPrimitive.content }.getOrNull()
        }?.takeIf { it.isNotBlank() }

        val outbound = (obj["outbounds"] as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            ?.firstOrNull { ob ->
                val p = runCatching { ob["protocol"]?.jsonPrimitive?.content }.getOrNull()
                p != null && p.lowercase() in PROXY_PROTOCOLS
            }

        val protocol = runCatching { outbound?.get("protocol")?.jsonPrimitive?.content }.getOrNull()
        val settings = outbound?.get("settings") as? JsonObject

        val node = (settings?.get("vnext") as? JsonArray)?.firstOrNull() as? JsonObject
            ?: (settings?.get("servers") as? JsonArray)?.firstOrNull() as? JsonObject
            ?: settings

        val address = runCatching { node?.get("address")?.jsonPrimitive?.content }.getOrNull()
        val port = runCatching { node?.get("port")?.jsonPrimitive?.content?.toIntOrNull() }.getOrNull()

        return CustomConfigInfo(remarks, protocol, address, port)
    }

    private val UDP_PROTOCOLS = setOf("wireguard", "hysteria2", "hysteria")

    private val SERVERLESS_PROTOCOLS = setOf("freedom", "direct", "blackhole", "block", "dns", "loopback")

    fun isServerless(rawJson: String): Boolean {
        val obj = runCatching { json.parseToJsonElement(Jsonc.strip(rawJson)) }.getOrNull() as? JsonObject ?: return false
        val protocols = (obj["outbounds"] as? JsonArray)?.mapNotNull { ob ->
            runCatching { (ob as? JsonObject)?.get("protocol")?.jsonPrimitive?.content?.lowercase() }.getOrNull()
        }.orEmpty()
        return protocols.isNotEmpty() && protocols.all { it in SERVERLESS_PROTOCOLS } &&
            protocols.any { it == "freedom" || it == "direct" }
    }

    fun tcpProbeable(rawJson: String): Boolean {
        val obj = runCatching { json.parseToJsonElement(Jsonc.strip(rawJson)) }.getOrNull() as? JsonObject
            ?: return false
        val outbounds = (obj["outbounds"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: return false

        if (outbounds.any { ob ->
                ((ob["streamSettings"] as? JsonObject)?.get("sockopt") as? JsonObject)
                    ?.get("dialerProxy") != null
            }
        ) return false
        val proxy = outbounds.firstOrNull { ob ->
            val p = runCatching { ob["protocol"]?.jsonPrimitive?.content }.getOrNull()
            p != null && p.lowercase() in PROXY_PROTOCOLS
        } ?: return false
        val protocol = runCatching { proxy["protocol"]?.jsonPrimitive?.content }.getOrNull()?.lowercase()
        return protocol != null && protocol !in UDP_PROTOCOLS
    }
}
