package dev.cluvex.zedsecure.domain.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
object SsdLink {
    private const val PREFIX = "ssd://"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun isSsdLink(text: String): Boolean = text.trim().startsWith(PREFIX, ignoreCase = true)

    fun groupName(text: String): String = document(text)?.string("airport").orEmpty()

    fun parse(text: String): List<String> {
        val obj = document(text) ?: return emptyList()
        val group = obj.string("airport").orEmpty()
        val method = obj.string("encryption").orEmpty()
        val password = obj.string("password").orEmpty()
        val port = obj.string("port").orEmpty()
        val servers = (obj["servers"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: return emptyList()

        return servers.mapNotNull { node ->
            val host = node.string("server")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val nodeMethod = node.string("encryption")?.takeIf { it.isNotBlank() } ?: method
            val nodePassword = node.string("password")?.takeIf { it.isNotBlank() } ?: password
            val nodePort = node.string("port")?.takeIf { it.isNotBlank() && it != "0" } ?: port
            if (nodeMethod.isBlank() || nodePort.isBlank()) return@mapNotNull null

            val remark = node.string("remarks")?.takeIf { it.isNotBlank() } ?: "$host:$nodePort"
            val name = if (group.isBlank()) remark else "$group - $remark"
            val userInfo = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
                .encode("$nodeMethod:$nodePassword".encodeToByteArray())
            val plugin = node.string("plugin")?.takeIf { it.isNotBlank() }?.let { p ->
                val options = node.string("plugin_options")?.takeIf { it.isNotBlank() }
                "?plugin=" + encodeQuery(if (options == null) p else "$p;$options")
            }.orEmpty()
            "ss://$userInfo@${endpoint(host, nodePort)}$plugin#${encodeRemark(name)}"
        }
    }

    private fun document(text: String): JsonObject? {
        val body = text.trim().removePrefix(PREFIX).removePrefix(PREFIX.uppercase()).trim()
        if (body.isEmpty()) return null
        val decoded = runCatching {
            Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
                .decode(body.replace('+', '-').replace('/', '_').trimEnd('='))
                .decodeToString()
        }.getOrNull() ?: return null
        return runCatching { json.parseToJsonElement(decoded) as? JsonObject }.getOrNull()
    }

    private fun endpoint(host: String, port: String): String =
        if (host.contains(':') && !host.startsWith("[")) "[$host]:$port" else "$host:$port"

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private const val UNRESERVED = "-_.~"
    private const val HEX = "0123456789ABCDEF"

    private fun encodeRemark(value: String): String = percentEncode(value)

    private fun encodeQuery(value: String): String = percentEncode(value)

    private fun percentEncode(value: String): String = buildString {
        value.encodeToByteArray().forEach { byte ->
            val b = byte.toInt() and 0xFF
            val c = b.toChar()
            if (b < 0x80 && (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c in UNRESERVED)) {
                append(c)
            } else {
                append('%')
                append(HEX[(b shr 4) and 0xF])
                append(HEX[b and 0xF])
            }
        }
    }
}
