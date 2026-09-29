package dev.cluvex.zedsecure.domain.config

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

object SshLink {
    private const val PREFIX = "ssh://"

    fun isSshLink(text: String): Boolean = text.trim().startsWith(PREFIX, ignoreCase = true)

    @OptIn(ExperimentalEncodingApi::class)
    fun parse(text: String): Pair<String, SshProfile>? {
        val trimmed = text.trim()
        if (!isSshLink(trimmed)) return null
        val body = trimmed.substring(PREFIX.length)
        if (body.isBlank()) return null

        val remark = body.substringAfter('#', "").let { ConfigParser.percentDecode(it) }
        val withoutRemark = body.substringBefore('#')

        val userInfo = withoutRemark.substringBeforeLast('@', "")
        val endpoint = withoutRemark.substringAfterLast('@')
        if (userInfo.isBlank() || endpoint.isBlank()) return null

        val host: String
        val port: Int
        if (endpoint.startsWith("[")) {
            host = endpoint.substringAfter('[').substringBefore(']')
            port = endpoint.substringAfterLast(':', "").toIntOrNull() ?: 22
        } else {
            host = endpoint.substringBeforeLast(':', endpoint)
            port = endpoint.substringAfterLast(':', "").toIntOrNull() ?: 22
        }
        if (host.isBlank()) return null

        val fields = splitFields(userInfo)
            ?: splitFields(runCatching { decodeBase64(userInfo) }.getOrNull().orEmpty())
            ?: listOf(ConfigParser.percentDecode(userInfo))

        val username = fields.getOrNull(0)?.let { ConfigParser.percentDecode(it) }.orEmpty()
        val password = fields.getOrNull(1)?.let { ConfigParser.percentDecode(it) }.orEmpty()
        val keyBlob = fields.getOrNull(2).orEmpty()
        if (username.isBlank()) return null

        val privateKey = keyBlob.takeIf { it.isNotBlank() }
            ?.let { runCatching { decodeBase64(it) }.getOrNull() }
            ?.takeIf { it.contains("PRIVATE KEY") }
            .orEmpty()

        return (remark.ifBlank { "$username@$host" }) to SshProfile(
            host = host,
            port = port,
            username = username,
            authType = if (privateKey.isNotBlank()) SshProfile.AUTH_KEY else SshProfile.AUTH_PASSWORD,
            password = password,
            privateKey = privateKey,
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun build(name: String, profile: SshProfile): String {
        val key = profile.privateKey.takeIf { it.isNotBlank() }
            ?.let { Base64.encode(it.encodeToByteArray()) }
            .orEmpty()
        val userInfo = listOf(profile.username, profile.password, key).joinToString(",")
        val host = if (profile.host.contains(':') && !profile.host.startsWith("[")) {
            "[${profile.host}]"
        } else {
            profile.host
        }
        val fragment = if (name.isBlank()) "" else "#" + encodeFragment(name)
        return "$PREFIX$userInfo@$host:${profile.port}$fragment"
    }

    private fun splitFields(value: String): List<String>? {
        if (value.isBlank()) return null
        val parts = value.split(',')
        if (parts.size < 3) return null

        return listOf(
            parts.first().trim(),
            parts.subList(1, parts.size - 1).joinToString(",").trim(),
            parts.last().trim(),
        )
    }

    private const val HEX = "0123456789ABCDEF"

    private fun encodeFragment(value: String): String = buildString {
        value.encodeToByteArray().forEach { byte ->
            val b = byte.toInt() and 0xFF
            val c = b.toChar()
            if (b < 0x80 && (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c in "-_.~")) {
                append(c)
            } else {
                append('%').append(HEX[(b shr 4) and 0xF]).append(HEX[b and 0xF])
            }
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeBase64(value: String): String {
        val cleaned = value.trim().replace('-', '+').replace('_', '/').filterNot { it == '=' }
        val padded = cleaned + "=".repeat((4 - cleaned.length % 4) % 4)
        return Base64.decode(padded).decodeToString()
    }
}
