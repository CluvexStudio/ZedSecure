@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package dev.cluvex.zedsecure.domain.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.io.encoding.Base64

/**
 * Reader for NPV Tunnel's documented/open exports and legacy NPVT1 containers.
 * It preserves full Xray JSON when the NPV profile says that its JSON is authoritative.
 *
 * NPVS encrypted envelopes are intentionally not treated as plaintext: they need
 * their declared unlock method and key/passphrase, and must not be silently downgraded.
 */
object NpvTunnelParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private const val MAX_TEXT_BYTES = 1_048_576

    data class Profile(
        val name: String,
        val link: String? = null,
        val rawJson: String? = null,
    )

    data class NpvsInfo(
        val version: Int,
        val unlockMethod: String,
        val byteCount: Int,
    )

    fun isNpvText(text: String): Boolean {
        val raw = text.trimStart().removePrefix("\uFEFF")
        return raw.startsWith("NPVT1", ignoreCase = true) ||
            raw.startsWith("NPVO1", ignoreCase = true) ||
            raw.startsWith("npvt://", ignoreCase = true) ||
            looksLikeProfileJson(raw)
    }

    /**
     * Returns null for ordinary configs. Throws a useful error for recognized but
     * malformed NPV data, so callers don't fall through and misreport it as a link.
     */
    fun parse(text: String): List<Profile>? {
        val raw = text.trim().removePrefix("\uFEFF")
        if (raw.isEmpty()) return null
        require(raw.encodeToByteArray().size <= MAX_TEXT_BYTES) { "NPV file exceeds the 1 MB import limit" }

        if (raw.startsWith("NPVS")) {
            throw IllegalArgumentException("NPVS is a binary encrypted container; import its bytes through the file picker")
        }
        if (raw.startsWith("NPVO1")) {
            val payload = raw.removePrefix("NPVO1").trimStart(' ', '\n', '\r', '\t', ':', '=', '-')
            val element = runCatching { json.parseToJsonElement(payload) }
                .getOrElse { throw IllegalArgumentException("Malformed NPVO1 export") }
            val root = element as? JsonObject ?: throw IllegalArgumentException("NPVO1 root must be a JSON object")
            val configs = root["configs"] as? JsonArray ?: throw IllegalArgumentException("NPVO1 export has no configs array")
            val profiles = configs.flatMap { parseElement(it) }
            require(profiles.isNotEmpty()) { "NPVO1 contains no supported NPV profiles" }
            return profiles
        }
        if (raw.startsWith("NPVT1") || raw.startsWith("npvt://", ignoreCase = true)) {
            return parseNpvt(raw)
        }

        val element = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: return null
        val profiles = parseElement(element)
        return profiles.takeIf { it.isNotEmpty() }
    }

    fun inspectNpvs(bytes: ByteArray): NpvsInfo? {
        if (bytes.size < 4 || bytes[0] != 'N'.code.toByte() ||
            bytes[1] != 'P'.code.toByte() || bytes[2] != 'V'.code.toByte() ||
            bytes[3] != 'S'.code.toByte()
        ) return null
        require(bytes.size >= 9) { "Truncated NPVS header" }
        val version = bytes[4].toInt() and 0xff
        if (version != 5) throw IllegalArgumentException("NPVS version $version is not supported by this adapter yet")
        val headerLength = readUInt32(bytes, 5)
        require(headerLength >= 135 && 9L + headerLength + 16L + 64L <= bytes.size.toLong()) {
            "Malformed NPVS v5 header length"
        }
        val header = bytes.copyOfRange(9, 9 + headerLength)
        require((header[0].toInt() and 0xff) == 1) { "Unsupported NPVS v5 header revision" }
        val method = header[50].toInt() and 0xff
        val recipients = ((header[51].toInt() and 0xff) shl 8) or (header[52].toInt() and 0xff)
        require(method in 0..2) { "Unknown NPVS unlock method" }
        val keyEnd = 53L + recipients.toLong() * 125L
        val methodLength = when (method) { 1 -> 80L; 2 -> 78L; else -> 0L }
        val metaOffset = keyEnd + methodLength
        require(metaOffset + 4L <= header.size.toLong()) { "Truncated NPVS key/metadata section" }
        val iterations = if (method == 1) readUInt32(header, keyEnd.toInt()) else 0
        require(method != 1 || iterations in 1..10_000_000) { "Invalid NPVS passphrase KDF parameters" }
        val metaLength = readUInt32(header, metaOffset.toInt())
        require(metaLength >= 16 && metaOffset + 4L + metaLength <= header.size.toLong()) {
            "Malformed NPVS encrypted metadata"
        }
        val bodyOffset = 9 + headerLength
        val bodyLength = readUInt32(bytes, bodyOffset + 12)
        require(bodyLength >= 32 && bodyOffset.toLong() + 16L + bodyLength + 64L == bytes.size.toLong()) {
            "Malformed NPVS ciphertext length"
        }
        val unlock = when (method) {
            0 -> "recipient private key"
            1 -> "creator passphrase (PBKDF2-HMAC-SHA256, $iterations iterations)"
            else -> "app key"
        }
        return NpvsInfo(version, unlock, bytes.size)
    }

    private fun parseNpvt(raw: String): List<Profile> {
        val body = raw.replaceFirst(Regex("^npvt://", RegexOption.IGNORE_CASE), "")
            .replaceFirst(Regex("^NPVT1\\s*[:=,\\-]?\\s*", RegexOption.IGNORE_CASE), "")
            .trim()
        require(body.isNotBlank()) { "NPVT1 container is empty" }
        val tokens = body.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        require(tokens.size in 1..16) { "NPVT1 has an invalid chunk count" }
        val decoded = tokens.mapIndexed { index, token ->
            val bytes = decodeToken(token)
            val plain = runCatching { NpvtWhiteboxCipher.decrypt(bytes).decodeToString(throwOnInvalidSequence = false) }
                .getOrElse { throw IllegalArgumentException("NPVT1 chunk ${index + 1} could not be decoded", it) }
                .trimEnd('\u0000', '\uFEFF', '\n', '\r', ' ', '\t')
                .trim()
            plain
        }
        // Legacy NPVT1 has version, profile-array, and lock-config chunks. Do not
        // assume the profiles are always in slot 2: inspect each decrypted chunk.
        val profiles = decoded.flatMap { chunk ->
            val element = runCatching { json.parseToJsonElement(chunk) }.getOrNull() ?: return@flatMap emptyList()
            parseElement(element)
        }.distinctBy { it.rawJson ?: it.link ?: it.name }
        require(profiles.isNotEmpty()) { "NPVT1 was decoded, but no supported profile data was found" }
        return profiles
    }

    private fun decodeToken(token: String): ByteArray {
        var clean = token.trim().trim('"', '\'', '`')
            .filterNot { it.isWhitespace() || it == '\uFEFF' }
        require(clean.isNotEmpty()) { "Empty NPVT1 chunk" }
        if (clean.length >= 32 && clean.length % 2 == 0 && clean.all { it in "0123456789abcdefABCDEF" }) {
            return ByteArray(clean.length / 2) { i -> clean.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        }
        clean = clean.replace('-', '+').replace('_', '/')
        clean += "=".repeat((4 - clean.length % 4) % 4)
        return runCatching { Base64.Default.decode(clean) }
            .getOrElse { throw IllegalArgumentException("NPVT1 chunk is not valid Base64", it) }
    }

    private fun parseElement(element: JsonElement): List<Profile> = when (element) {
        is JsonArray -> element.flatMap(::parseElement)
        is JsonObject -> {
            val configs = element["configs"] as? JsonArray
            if (configs != null) {
                configs.flatMap(::parseElement)
            } else if (element["v2rayProfile"] is JsonObject) {
                listOfNotNull(parseNpvWrapper(element))
            } else if (looksLikeXrayJson(element)) {
                listOf(Profile(name = element.string("remarks").ifBlank { "NPV Xray profile" }, rawJson = json.encodeToString(JsonObject.serializer(), element)))
            } else {
                emptyList()
            }
        }
        else -> emptyList()
    }

    private fun parseNpvWrapper(wrapper: JsonObject): Profile? {
        val p = wrapper["v2rayProfile"] as? JsonObject ?: return null
        val name = wrapper.string("name").ifBlank {
            p.string("remarks").ifBlank { "NPV Tunnel profile" }
        }.take(120)
        val embeddedJson = p.string("v2rayJson").trim()
        if (embeddedJson.isNotEmpty() && runCatching { json.parseToJsonElement(embeddedJson) }.getOrNull() is JsonObject) {
            val obj = json.parseToJsonElement(embeddedJson) as JsonObject
            if (looksLikeXrayJson(obj)) {
                return Profile(name = name, rawJson = json.encodeToString(JsonObject.serializer(), obj))
            }
        }

        val protocol = when (p.string("protocol").lowercase()) {
            "vless" -> "vless"
            "vmess" -> "vmess"
            "trojan" -> "trojan"
            "ss", "shadowsocks" -> "ss"
            "socks" -> "socks"
            else -> when (p.int("configType")) {
                1 -> "vmess"
                3 -> "ss"
                5 -> "vless"
                6 -> "trojan"
                7 -> "socks"
                else -> ""
            }
        }
        if (protocol.isBlank()) return null
        val server = p.string("server").ifBlank { wrapper.string("address").substringBeforeLast(':', "") }
        val port = p.string("serverPort").toIntOrNull()
            ?: wrapper.string("address").substringAfterLast(':', "").toIntOrNull()
            ?: 0
        if (server.isBlank() || port !in 1..65535) return null

        val credential = p.string("password").ifBlank { p.string("uuid") }
        if (credential.isBlank() && protocol != "socks") return null
        val network = p.string("network").ifBlank { "tcp" }
        val security = p.string("security").ifBlank { p.string("tls") }
        val host = p.string("host").ifBlank { p.string("wsHost") }
        val path = p.string("path")
        val sni = p.string("sni").ifBlank { p.string("serverName") }
        val fp = p.string("fingerPrint").ifBlank { p.string("fingerprint") }.ifBlank { p.string("fp") }
        val alpn = p.string("alpn")
        val header = p.string("headerType")
        val transport = TransportConfig(
            network = network,
            headerType = header.ifBlank { null },
            host = host.ifBlank { null },
            path = path.ifBlank { null },
            mode = p.string("mode").ifBlank { null },
            serviceName = p.string("serviceName").ifBlank { null },
            authority = p.string("authority").ifBlank { null },
            xhttpExtra = p.string("xhttpExtra").ifBlank { null },
            finalMask = p.string("finalMask").ifBlank { p.string("fm") }.ifBlank { null },
        )
        val tls = SecurityConfig(
            security = security,
            sni = sni.ifBlank { null },
            fingerprint = fp.ifBlank { null },
            alpn = alpn.ifBlank { null },
            publicKey = p.string("publicKey").ifBlank { p.string("pbk") }.ifBlank { null },
            shortId = p.string("shortId").ifBlank { p.string("sid") }.ifBlank { null },
            spiderX = p.string("spiderX").ifBlank { p.string("spx") }.ifBlank { null },
            allowInsecure = p.boolean("allowInsecure") || p.boolean("insecure"),
            pinnedCertSha256 = null,
        )
        val cfg = when (protocol) {
            "vless" -> ServerConfig(Protocol.VLESS, name, server, port, userId = credential,
                encryption = p.string("encryption").ifBlank { "none" }, flow = p.string("flow").ifBlank { null },
                transport = transport, tls = tls)
            "vmess" -> ServerConfig(Protocol.VMESS, name, server, port, userId = credential,
                alterId = p.string("alterId").toIntOrNull() ?: p.string("aid").toIntOrNull() ?: 0,
                vmessSecurity = p.string("method").ifBlank { p.string("scy") }.ifBlank { "auto" },
                transport = transport, tls = tls)
            "trojan" -> ServerConfig(Protocol.TROJAN, name, server, port, userId = credential,
                transport = transport, tls = tls.copy(security = security.ifBlank { "tls" }))
            "ss" -> ServerConfig(Protocol.SHADOWSOCKS, name, server, port, userId = credential,
                shadowsocksMethod = p.string("method").ifBlank { p.string("securityCipher") }.ifBlank { "aes-256-gcm" },
                transport = transport, tls = tls)
            else -> ServerConfig(Protocol.SOCKS, name, server, port, userId = p.string("password"),
                username = p.string("username"), transport = transport, tls = tls)
        }
        return Profile(name = name, link = ShareLink.build(cfg))
    }

    private fun looksLikeProfileJson(raw: String): Boolean {
        if (!raw.startsWith("{") && !raw.startsWith("[")) return false
        val element = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: return false
        return parseElement(element).isNotEmpty()
    }

    private fun looksLikeXrayJson(obj: JsonObject): Boolean =
        obj["outbounds"] is JsonArray && (obj["outbounds"] as JsonArray).any { item ->
            item is JsonObject && (item["protocol"] is JsonPrimitive || item["type"] is JsonPrimitive)
        }

    private fun JsonObject.string(key: String): String {
        val value = this[key] ?: return ""
        return (value as? JsonPrimitive)?.content?.takeIf { it != "null" } ?: ""
    }

    private fun JsonObject.int(key: String): Int? = string(key).toIntOrNull()

    private fun JsonObject.boolean(key: String): Boolean =
        string(key).equals("true", ignoreCase = true) || string(key) == "1"

    private fun readUInt32(bytes: ByteArray, offset: Int): Int {
        require(offset >= 0 && offset + 4 <= bytes.size) { "Truncated NPVS integer field" }
        val value = ((bytes[offset].toLong() and 0xff) shl 24) or
            ((bytes[offset + 1].toLong() and 0xff) shl 16) or
            ((bytes[offset + 2].toLong() and 0xff) shl 8) or
            (bytes[offset + 3].toLong() and 0xff)
        require(value <= Int.MAX_VALUE.toLong()) { "NPVS length field is too large" }
        return value.toInt()
    }
}
