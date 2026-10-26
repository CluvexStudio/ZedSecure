package dev.cluvex.zedsecure.domain.config

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Imports SlipNet's public share format. This intentionally converts only the
 * DNSTT/VayDNS profiles that this app's current tunnel runtime can execute.
 * Other SlipNet modes are rejected rather than silently changing their protocol.
 */
@OptIn(ExperimentalEncodingApi::class)
object SlipNetConfigParser {
    data class ParsedProfile(val name: String, val settings: DnsTunnelProfile, val version: Int)

    class PasswordRequiredException : IllegalArgumentException(
        "This SlipNet bundle is password-protected. Enter its export password and retry.",
    )

    private const val PLAIN = "slipnet://"
    private const val ENCRYPTED = "slipnet-enc://"
    private const val BUNDLE = "slipnet-bundle-enc://"

    fun parse(text: String, password: String? = null): List<ParsedProfile>? {
        val trimmed = text.trim()
        if (!(trimmed.startsWith(PLAIN, true) ||
                trimmed.startsWith(ENCRYPTED, true) ||
                trimmed.startsWith(BUNDLE, true))
        ) return null

        val output = mutableListOf<ParsedProfile>()
        val lines = trimmed.lines().map(String::trim).filter(String::isNotEmpty)
        for (line in lines) {
            when {
                line.startsWith(PLAIN, true) -> output += parseDecoded(decodeBase64(line.substringAfter("://")))
                line.startsWith(ENCRYPTED, true) -> {
                    val decoded = SlipNetCrypto.decryptLegacy(line.substringAfter("://"))
                    output += parseDecoded(decoded)
                }
                line.startsWith(BUNDLE, true) -> {
                    val supplied = password?.takeIf(String::isNotEmpty) ?: throw PasswordRequiredException()
                    val decoded = SlipNetCrypto.decryptBundle(line.substringAfter("://"), supplied)
                    val nested = parse(decoded, supplied)
                    if (nested != null) output += nested
                    else {
                        decoded.lines().map(String::trim).filter(String::isNotEmpty).forEach { row ->
                            output += parseDecoded(row)
                        }
                    }
                }
                else -> throw IllegalArgumentException("Unsupported SlipNet bundle content")
            }
        }
        require(output.isNotEmpty()) { "SlipNet file contains no runnable profiles" }
        return output
    }

    private fun parseDecoded(decoded: String): ParsedProfile {
        val fields = decoded.trim().trimEnd('|').split('|')
        require(fields.size >= 12) { "SlipNet profile is truncated or unsupported" }
        val version = fields[0].removePrefix("v").toIntOrNull()
            ?: throw IllegalArgumentException("Unknown SlipNet profile version")
        require(version in setOf(20, 21, 22, 23, 24, 25, 26, 27, 28)) {
            "SlipNet profile v$version does not include the required tunnel key fields"
        }
        // SlipNet adds fields by schema version. Allow the final optional field to
        // be empty (the exporter may end the line with a delimiter), but reject
        // truncated payloads before reading defaults from unrelated positions.
        val minimumFields = when (version) {
            20 -> 50
            21 -> 59
            22 -> 60
            23, 24 -> 63
            25 -> 72
            26, 27 -> 78
            28 -> 79
            else -> error("Unsupported SlipNet schema")
        }
        require(fields.size >= minimumFields) { "SlipNet profile v$version is truncated" }

        fun value(index: Int): String = fields.getOrNull(index).orEmpty().trim()
        fun flag(index: Int): Boolean = value(index) == "1" || value(index).equals("true", true)
        fun number(index: Int, fallback: Int): Int = value(index).toIntOrNull() ?: fallback
        fun decimal(index: Int, fallback: Double): Double = value(index).toDoubleOrNull() ?: fallback

        val mode = value(1).lowercase()
        val engine = when (mode) {
            "dnstt", "dnstt_ssh" -> DnsTunnelProfile.ENGINE_DNSTT
            "vaydns", "vaydns_ssh" -> DnsTunnelProfile.ENGINE_VAYDNS
            else -> throw IllegalArgumentException(
                "SlipNet mode '$mode' is not executable by this build yet; supported modes are DNSTT and VayDNS (with optional SSH chaining).",
            )
        }
        val domain = value(3)
        val publicKey = value(11)
        require(domain.isNotBlank()) { "SlipNet profile is missing its DNS tunnel domain" }
        require(publicKey.isNotBlank()) { "SlipNet profile is missing the DNSTT public key" }

        require(!flag(31)) { "This SlipNet profile has a profile lock; importing it without enforcing its lock would bypass its protection. Unlock/export it without a profile lock first." }

        val resolverField = value(4).ifBlank { if (flag(36)) value(37) else "" }
        val resolvers = resolverField.split(',').map { raw ->
            val item = raw.trim()
            if (item.isBlank()) return@map ""
            // SlipNet serializes resolver entries as host:port:authoritative.
            // Preserve bracketed IPv6 literals and remove only the trailing auth flag.
            if (item.startsWith("[") && item.contains("]:")) {
                val end = item.indexOf(']')
                val tail = item.substring(end + 1).removePrefix(":").split(':')
                if (tail.size >= 2) item.substring(0, end + 1) + ":" + tail[0] else item
            } else {
                val parts = item.split(':')
                if (parts.size == 3 && parts[1].toIntOrNull() != null) "${parts[0]}:${parts[1]}" else item
            }
        }.filter(String::isNotBlank).joinToString(",")

        val transport = when (value(22).lowercase()) {
            "tcp" -> DnsTunnelProfile.TRANSPORT_TCP
            "dot", "tls" -> DnsTunnelProfile.TRANSPORT_DOT
            "doh", "https" -> DnsTunnelProfile.TRANSPORT_DOH
            else -> DnsTunnelProfile.TRANSPORT_UDP
        }
        val settings = DnsTunnelProfile(
            engine = engine,
            domain = domain,
            publicKey = publicKey,
            dnsttCompat = flag(41),
            dnsTransport = transport,
            resolvers = resolvers,
            dohUrl = value(21),
            resolverMode = if (version >= 23 && (value(61).equals("roundrobin", true) || value(61) == "1")) {
                DnsTunnelProfile.MODE_ROUND_ROBIN
            } else DnsTunnelProfile.MODE_FANOUT,
            rrSpreadCount = number(if (version >= 23) 62 else -1, 3),
            authoritative = flag(27),
            dnsPayloadSize = number(39, 100),
            recordType = value(42).ifBlank { "txt" }.lowercase(),
            maxQnameLen = number(43, 101),
            rps = decimal(44, 0.0),
            idleTimeout = number(45, 0),
            keepalive = number(46, 0),
            udpTimeout = number(47, 0),
            maxNumLabels = number(48, 0),
            clientIdSize = number(49, 0),
            socksUser = value(12),
            socksPass = value(13),
            sshEnabled = flag(14) || mode.endsWith("_ssh"),
            sshHost = value(19),
            sshPort = number(17, 22),
            sshUsername = value(15),
            sshPassword = value(16),
            sshAuthType = if (value(23).equals("key", true)) DnsTunnelProfile.AUTH_KEY else DnsTunnelProfile.AUTH_PASSWORD,
            sshPrivateKey = decodeOptionalBase64(value(24)),
            sshKeyPassphrase = decodeOptionalBase64(value(25)),
            forwardDnsThroughSsh = flag(18),
        )
        return ParsedProfile(value(2).ifBlank { "SlipNet $mode" }, settings, version)
    }

    private fun decodeOptionalBase64(value: String): String =
        if (value.isBlank()) "" else runCatching { decodeBase64(value) }.getOrDefault(value)

    private fun decodeBase64(value: String): String {
        val cleaned = value.trim().substringBefore('#').trimEnd('=')
        val bytes = runCatching {
            Base64.Default.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(cleaned)
        }.recoverCatching {
            Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(cleaned)
        }.getOrElse { throw IllegalArgumentException("SlipNet payload is not valid Base64") }
        return bytes.decodeToString()
    }
}
