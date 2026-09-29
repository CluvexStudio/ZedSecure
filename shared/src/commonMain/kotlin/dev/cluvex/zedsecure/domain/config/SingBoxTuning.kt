package dev.cluvex.zedsecure.domain.config

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class SingBoxTuning(
    val muxEnabled: Boolean = false,
    val muxProtocol: String = "h2mux",
    val muxMaxConnections: Int = 4,
    val muxPadding: Boolean = false,
    val brutalEnabled: Boolean = false,
    val brutalUpMbps: Int = 0,
    val brutalDownMbps: Int = 0,
    val tlsFragment: Boolean = false,
    val tlsRecordFragment: Boolean = false,
    val utlsFingerprint: String = "",
    val udpOverTcp: Boolean = false,
) {
    val isEmpty: Boolean
        get() = !muxEnabled && !tlsFragment && !tlsRecordFragment && utlsFingerprint.isBlank() && !udpOverTcp

    companion object {
        val MUX_PROTOCOLS = setOf("vmess", "vless", "trojan", "shadowsocks")

        val UDP_OVER_TCP_PROTOCOLS = setOf("shadowsocks", "socks")

        val FINGERPRINTS = listOf(
            "chrome", "firefox", "edge", "safari", "360", "qq", "ios", "android", "random", "randomized",
        )

        fun apply(fragment: String, tuning: SingBoxTuning): String {
            if (tuning.isEmpty) return fragment
            val root = SingBoxJson.parse(fragment) as? JsonObject ?: return fragment
            val outbounds = root["outbounds"] as? JsonArray ?: return fragment
            val tuned = buildJsonArray {
                outbounds.forEach { element ->
                    val outbound = element as? JsonObject
                    add(if (outbound == null) element else tuning.applyTo(outbound))
                }
            }
            return SingBoxJson.encode(JsonObject(root + ("outbounds" to tuned)))
        }
    }

    private fun applyTo(outbound: JsonObject): JsonObject {
        val type = (outbound["type"] as? JsonPrimitive)?.content?.lowercase() ?: return outbound
        var result = outbound
        if (muxEnabled && type in MUX_PROTOCOLS && "multiplex" !in outbound) {
            result = JsonObject(result + ("multiplex" to multiplex()))
        }
        if (udpOverTcp && type in UDP_OVER_TCP_PROTOCOLS && "udp_over_tcp" !in outbound) {
            result = JsonObject(result + ("udp_over_tcp" to JsonPrimitive(true)))
        }
        tls(outbound)?.let { result = JsonObject(result + ("tls" to it)) }
        return result
    }

    private fun multiplex(): JsonObject = buildJsonObject {
        put("enabled", true)
        put("protocol", muxProtocol)
        if (muxMaxConnections > 0) put("max_connections", muxMaxConnections)
        if (muxPadding) put("padding", true)
        if (brutalEnabled && brutalUpMbps > 0 && brutalDownMbps > 0) {
            put(
                "brutal",
                buildJsonObject {
                    put("enabled", true)
                    put("up_mbps", brutalUpMbps)
                    put("down_mbps", brutalDownMbps)
                },
            )
        }
    }

    private fun tls(outbound: JsonObject): JsonElement? {
        val tls = outbound["tls"] as? JsonObject ?: return null
        if ((tls["enabled"] as? JsonPrimitive)?.content != "true") return null
        var result = tls
        if (tlsFragment && "fragment" !in tls) result = JsonObject(result + ("fragment" to JsonPrimitive(true)))
        if (tlsRecordFragment && "record_fragment" !in tls) {
            result = JsonObject(result + ("record_fragment" to JsonPrimitive(true)))
        }
        if (utlsFingerprint.isNotBlank() && "utls" !in tls) {
            result = JsonObject(
                result + (
                    "utls" to buildJsonObject {
                        put("enabled", true)
                        put("fingerprint", utlsFingerprint)
                    }
                    ),
            )
        }
        return result.takeIf { it != tls }
    }
}
