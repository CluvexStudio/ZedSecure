package dev.cluvex.zedsecure.desktop.platform

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object DesktopXray {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun withSocksPort(config: String, from: Int, to: Int): String {
        if (from == to) return config
        val root = runCatching { json.parseToJsonElement(config).jsonObject }.getOrNull() ?: return config
        val inbounds = root["inbounds"] as? JsonArray ?: return config
        val moved = JsonArray(
            inbounds.map { element ->
                val inbound = element as? JsonObject ?: return@map element
                if ((inbound["port"] as? JsonPrimitive)?.intOrNull == from) {
                    JsonObject(inbound + ("port" to JsonPrimitive(to)))
                } else {
                    inbound
                }
            },
        )
        return JsonObject(root + ("inbounds" to moved)).toString()
    }

    fun frontConfig(listenPort: Int, upstreamPort: Int): String = buildJsonObject {
        putJsonObject("log") { put("loglevel", "warning") }
        putJsonArray("inbounds") {
            addJsonObject {
                put("tag", "front-in")
                put("listen", "127.0.0.1")
                put("port", listenPort)
                put("protocol", "socks")
                putJsonObject("settings") {
                    put("auth", "noauth")
                    put("udp", true)
                }
            }
        }
        putJsonArray("outbounds") {
            addJsonObject {
                put("tag", "proxy")
                put("protocol", "socks")
                putJsonObject("settings") {
                    putJsonArray("servers") {
                        addJsonObject {
                            put("address", "127.0.0.1")
                            put("port", upstreamPort)
                        }
                    }
                }
            }
        }
    }.toString()

    fun bindOutbounds(config: String, iface: String, pinned: Map<String, List<String>> = emptyMap()): String {
        val root = runCatching { json.parseToJsonElement(config).jsonObject }.getOrNull() ?: return config
        val outbounds = root["outbounds"] as? JsonArray ?: return config
        val bound = JsonArray(
            outbounds.map { element ->
                val outbound = element as? JsonObject ?: return@map element
                val protocol = (outbound["protocol"] as? JsonPrimitive)?.content?.lowercase()
                val stream = outbound["streamSettings"] as? JsonObject ?: JsonObject(emptyMap())
                val sockopt = stream["sockopt"] as? JsonObject ?: JsonObject(emptyMap())
                val skip = protocol in NOT_DIALING || sockopt.containsKey("dialerProxy") ||
                    sockopt.containsKey("interface") || outbound.containsKey("proxySettings") || dialsLoopback(outbound)
                if (skip) {
                    outbound
                } else {
                    val resolvesPinned = !sockopt.containsKey("domainStrategy") && serverHostsOf(outbound).any { it in pinned }
                    val options = sockopt + ("interface" to JsonPrimitive(iface)) +
                        (if (resolvesPinned) mapOf("domainStrategy" to JsonPrimitive("UseIP")) else emptyMap())
                    JsonObject(outbound + ("streamSettings" to JsonObject(stream + ("sockopt" to JsonObject(options)))))
                }
            },
        )
        val usable = pinned.filterValues { it.isNotEmpty() }
        if (usable.isEmpty()) return JsonObject(root + ("outbounds" to bound)).toString()
        val dns = root["dns"] as? JsonObject ?: JsonObject(emptyMap())
        val hosts = dns["hosts"] as? JsonObject ?: JsonObject(emptyMap())
        val added = usable.filterKeys { it !in hosts }.mapValues { (_, ips) -> JsonArray(ips.map(::JsonPrimitive)) }
        val withHosts = JsonObject(dns + ("hosts" to JsonObject(hosts + added)))
        return JsonObject(root + ("outbounds" to bound) + ("dns" to withHosts)).toString()
    }

    private val NOT_DIALING = setOf("blackhole", "block", "dns", "loopback", "autoselect")

    private val LOOPBACK = setOf("127.0.0.1", "localhost", "::1")

    private fun dialsLoopback(outbound: JsonObject): Boolean {
        val settings = outbound["settings"] as? JsonObject ?: return false
        val hosts = buildList {
            listOf("servers", "vnext", "peers").forEach { key ->
                (settings[key] as? JsonArray)?.forEach { entry ->
                    val e = entry as? JsonObject ?: return@forEach
                    listOf("address", "server", "endpoint").forEach { field ->
                        (e[field] as? JsonPrimitive)?.content?.let { add(it) }
                    }
                }
            }
            listOf("address", "server").forEach { field -> (settings[field] as? JsonPrimitive)?.content?.let { add(it) } }
        }
        return hosts.any { hostOf(it) in LOOPBACK }
    }

    private fun hostOf(value: String): String = when {
        value.startsWith("[") -> value.substringAfter("[").substringBefore("]")
        value.count { it == ':' } == 1 -> value.substringBefore(':')
        else -> value
    }

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
        outbounds.mapNotNull { it as? JsonObject }.forEach { hosts += serverHostsOf(it) }
        return hosts.filter { it.isNotBlank() && it != "127.0.0.1" && it != "localhost" && it != "•••" }
    }

    private fun serverHostsOf(ob: JsonObject): List<String> {
        val settings = ob["settings"] as? JsonObject ?: return emptyList()
        val protocol = (ob["protocol"] as? JsonPrimitive)?.content?.lowercase()
        if (protocol in NOT_DIALING || protocol == "freedom") return emptyList()
        if (protocol == "singbox") {
            val fragment = settings["config"] as? JsonObject ?: return emptyList()
            return dev.cluvex.zedsecure.domain.config.SingBoxJson.servers(fragment.toString()).mapNotNull { it.address }
        }
        return buildList {
            (settings["vnext"] as? JsonArray).collectHosts(this, "address", hasPort = false)
            (settings["servers"] as? JsonArray).collectHosts(this, "address", hasPort = false)
            (settings["peers"] as? JsonArray).collectHosts(this, "endpoint", hasPort = true)
            (settings["address"] as? JsonPrimitive)?.content?.trim('[', ']')?.takeIf { it.isNotBlank() }?.let(::add)
        }
    }

    private fun JsonArray?.collectHosts(into: MutableList<String>, key: String, hasPort: Boolean) {
        this?.mapNotNull { it as? JsonObject }?.forEach { obj ->
            val v = runCatching { (obj[key] as? JsonPrimitive)?.content }.getOrNull()?.trim() ?: return@forEach
            val host = if (hasPort) hostOf(v) else v.trim('[', ']')
            if (host.isNotBlank()) into += host
        }
    }
}
