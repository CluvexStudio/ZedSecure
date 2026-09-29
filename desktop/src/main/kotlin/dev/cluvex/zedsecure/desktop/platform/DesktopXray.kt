package dev.cluvex.zedsecure.desktop.platform

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

object DesktopXray {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun augmentWithMetrics(config: String, metricsPort: Int): String {
        val root = runCatching { json.parseToJsonElement(config).jsonObject }.getOrNull() ?: return config
        val out = buildJsonObject {
            root.forEach { (k, v) -> if (k != "metrics" && k != "stats" && k != "policy") put(k, v) }
            put("stats", root["stats"] as? JsonObject ?: buildJsonObject {})
            put("metrics", buildJsonObject { put("listen", "127.0.0.1:$metricsPort") })

            val existingPolicy = root["policy"] as? JsonObject
            put("policy", buildJsonObject {
                existingPolicy?.forEach { (k, v) -> if (k != "system") put(k, v) }
                val sys = existingPolicy?.get("system") as? JsonObject
                put("system", buildJsonObject {
                    sys?.forEach { (k, v) -> put(k, v) }
                    put("statsOutboundUplink", true)
                    put("statsOutboundDownlink", true)
                })
            })
        }
        return out.toString()
    }

    fun extractServerHosts(config: String): List<String> {
        val root = runCatching { json.parseToJsonElement(config).jsonObject }.getOrNull() ?: return emptyList()
        val outbounds = root["outbounds"] as? JsonArray ?: return emptyList()
        val hosts = LinkedHashSet<String>()
        outbounds.mapNotNull { it as? JsonObject }.forEach { ob ->
            val settings = ob["settings"] as? JsonObject ?: return@forEach

            if ((ob["protocol"] as? JsonPrimitive)?.content == "singbox") {
                (settings["config"] as? JsonObject)?.let { fragment ->
                    dev.cluvex.zedsecure.domain.config.SingBoxJson.servers(fragment.toString())
                        .mapNotNull { it.address }
                        .forEach { hosts += it }
                }
                return@forEach
            }

            (settings["vnext"] as? JsonArray).orEmptyAddresses(hosts, "address")
            (settings["servers"] as? JsonArray).orEmptyAddresses(hosts, "address")
            (settings["peers"] as? JsonArray).orEmptyAddresses(hosts, "endpoint")
        }
        return hosts.filter { it.isNotBlank() && it != "127.0.0.1" && it != "localhost" && it != "•••" }
    }

    private fun JsonArray?.orEmptyAddresses(into: MutableSet<String>, key: String) {
        this?.mapNotNull { it as? JsonObject }?.forEach { obj ->
            val v = runCatching { (obj[key] as? JsonPrimitive)?.content }.getOrNull()

            val host = v?.substringBeforeLast(':')?.trim('[', ']')
            if (!host.isNullOrBlank()) into += host
        }
    }
}
