package dev.cluvex.zedsecure.domain.config

import kotlinx.serialization.Serializable

@Serializable
data class OpenConnectProfile(

    val server: String = "",

    val protocol: String = PROTO_ANYCONNECT,
    val username: String = "",

    val password: String = "",

    val authgroup: String = "",

    val caCertPem: String = "",

    val serverCertSha256: String = "",

    val clientCertPem: String = "",

    val clientKeyPem: String = "",
    val clientKeyPassword: String = "",

    val userAgent: String = "",

    val reportedOs: String = "android",

    val tokenMode: String = TOKEN_NONE,

    val tokenSecret: String = "",

    val disableDtls: Boolean = false,

    val clientCertP12Base64: String = "",

    val sni: String = "",

    val mtu: Int = 0,

    val reconnectTimeoutSec: Int = DEFAULT_RECONNECT_TIMEOUT,

    val proxy: String = "",

    val proxyAuth: String = "",

    val disableIpv6: Boolean = false,

    val formEntries: Map<String, String> = emptyMap(),
) {
    fun effectiveUserAgent(): String =
        userAgent.trim().ifBlank { defaultUserAgentFor(protocol) }

    fun serverUrl(): String {
        val s = server.trim()
        return when {
            s.startsWith("https://", true) -> s
            s.startsWith("http://", true) -> "https://" + s.substring(7)
            else -> "https://$s"
        }
    }

    fun serverPort(): Int {
        val authority = serverUrl().removePrefix("https://").substringBefore('/')

        val hostEnd = if (authority.startsWith("[")) authority.indexOf(']') + 1 else 0
        val colon = authority.indexOf(':', startIndex = hostEnd)
        if (colon < 0) return 443
        return authority.substring(colon + 1).substringBefore('/').toIntOrNull()?.takeIf { it in 1..65535 } ?: 443
    }

    val isValid: Boolean get() = server.isNotBlank()

    companion object {
        fun splitPort(server: String): Pair<String, Int?> {
            val s = server.trim()
            if (s.isEmpty()) return "" to null
            val schemeEnd = s.indexOf("://").let { if (it < 0) 0 else it + 3 }
            val scheme = s.substring(0, schemeEnd)
            val rest = s.substring(schemeEnd)
            val pathStart = rest.indexOf('/').let { if (it < 0) rest.length else it }
            val authority = rest.substring(0, pathStart)
            val path = rest.substring(pathStart)

            val hostEnd = if (authority.startsWith("[")) authority.indexOf(']') + 1 else 0
            val colon = authority.indexOf(':', startIndex = hostEnd)
            if (colon < 0) return s to null
            val port = authority.substring(colon + 1).toIntOrNull()?.takeIf { it in 1..65535 }
                ?: return s to null
            return (scheme + authority.substring(0, colon) + path) to port
        }

        fun withPort(server: String, port: Int?): String {
            val base = splitPort(server).first
            if (port == null || port !in 1..65535) return base
            val schemeEnd = base.indexOf("://").let { if (it < 0) 0 else it + 3 }
            val scheme = base.substring(0, schemeEnd)
            val rest = base.substring(schemeEnd)
            val pathStart = rest.indexOf('/').let { if (it < 0) rest.length else it }
            return scheme + rest.substring(0, pathStart) + ":" + port + rest.substring(pathStart)
        }

        const val PROTO_ANYCONNECT = "anyconnect"
        const val PROTO_GP = "gp"
        const val PROTO_PULSE = "pulse"
        const val PROTO_NC = "nc"
        const val PROTO_F5 = "f5"
        const val PROTO_FORTINET = "fortinet"
        const val PROTO_ARRAY = "array"

        const val TOKEN_NONE = "none"
        const val TOKEN_STOKEN = "stoken"
        const val TOKEN_TOTP = "totp"
        const val TOKEN_HOTP = "hotp"

        const val TOKEN_OIDC = "oidc"

        const val DEFAULT_RECONNECT_TIMEOUT = 300

        val REPORTED_OS_VALUES = listOf(
            "android" to "Android",
            "apple-ios" to "Apple iOS",
            "linux" to "Linux (32-bit)",
            "linux-64" to "Linux (64-bit)",
            "win" to "Windows",
            "mac-intel" to "macOS",
        )

        fun isMobileOs(os: String): Boolean = os == "android" || os == "apple-ios"

        val PROTOCOLS = listOf(
            PROTO_ANYCONNECT to "Cisco AnyConnect",
            PROTO_GP to "GlobalProtect",
            PROTO_PULSE to "Pulse / Juniper (Pulse)",
            PROTO_NC to "Juniper Network Connect",
            PROTO_F5 to "F5 BIG-IP",
            PROTO_FORTINET to "Fortinet",
            PROTO_ARRAY to "Array Networks",
        )

        val TOKEN_MODES = listOf(
            TOKEN_NONE to "None",
            TOKEN_STOKEN to "RSA SecurID (stoken)",
            TOKEN_TOTP to "TOTP",
            TOKEN_HOTP to "HOTP",
            TOKEN_OIDC to "OIDC bearer token",
        )

        fun defaultUserAgentFor(protocol: String): String = when (protocol) {
            PROTO_GP -> "PAN GlobalProtect"
            PROTO_PULSE, PROTO_NC -> "Pulse-Secure/9.1.11.6725"
            PROTO_F5 -> "F5 Networks"
            PROTO_FORTINET -> "FortiGate"
            PROTO_ARRAY -> "Array"
            else -> "Cisco AnyConnect VPN Agent for Windows 4.10.06079"
        }
    }
}
