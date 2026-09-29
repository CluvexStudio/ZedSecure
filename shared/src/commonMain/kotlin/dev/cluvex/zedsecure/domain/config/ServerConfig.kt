package dev.cluvex.zedsecure.domain.config

enum class Protocol(val id: String, val label: String) {
    VLESS("vless", "VLESS"),
    VMESS("vmess", "VMess"),
    TROJAN("trojan", "Trojan"),
    SHADOWSOCKS("shadowsocks", "Shadowsocks"),
    SOCKS("socks", "SOCKS"),
    HTTP("http", "HTTP"),
    WIREGUARD("wireguard", "WireGuard"),

    AMNEZIAWG("wireguard", "AmneziaWG"),
    HYSTERIA("hysteria", "Hysteria"),
    ;

    val usesUserPass: Boolean get() = this == SOCKS || this == HTTP

    val isWireguardFamily: Boolean get() = this == WIREGUARD || this == AMNEZIAWG

    val carriesUdp: Boolean get() = this != HTTP

    val supportsTransport: Boolean
        get() = this == VLESS || this == VMESS || this == TROJAN || this == SHADOWSOCKS

    val supportsMux: Boolean get() = this == VLESS || this == VMESS

    companion object {
        fun fromScheme(scheme: String): Protocol? = when (scheme.lowercase()) {
            "vless" -> VLESS
            "vmess" -> VMESS
            "trojan" -> TROJAN
            "ss", "shadowsocks" -> SHADOWSOCKS
            "socks", "socks5", "socks4", "socks4a" -> SOCKS
            "http", "https" -> HTTP
            "wireguard", "wg" -> WIREGUARD
            "awg", "amneziawg" -> AMNEZIAWG
            "hysteria", "hysteria2", "hy2" -> HYSTERIA
            else -> null
        }
    }
}

data class TransportConfig(
    val network: String = "tcp",
    val headerType: String? = null,
    val host: String? = null,
    val path: String? = null,
    val seed: String? = null,
    val quicSecurity: String? = null,
    val quicKey: String? = null,
    val mode: String? = null,
    val serviceName: String? = null,

    val authority: String? = null,

    val xhttpExtra: String? = null,
    val kcpMtu: Int? = null,
    val kcpTti: Int? = null,

    val finalMask: String? = null,
)

data class SecurityConfig(
    val security: String = "",
    val sni: String? = null,
    val fingerprint: String? = null,
    val alpn: String? = null,
    val publicKey: String? = null,
    val shortId: String? = null,
    val spiderX: String? = null,

    val echConfigList: String? = null,

    val cipherSuites: String? = null,

    val verifyPeerCertByName: String? = null,

    val mldsa65Verify: String? = null,

    val allowInsecure: Boolean = false,
)

data class ServerConfig(
    val protocol: Protocol,
    val remark: String,
    val address: String,
    val port: Int,
    val userId: String = "",
    val alterId: Int? = null,
    val encryption: String = "none",
    val vmessSecurity: String = "auto",
    val flow: String? = null,
    val shadowsocksMethod: String? = null,
    val transport: TransportConfig = TransportConfig(),
    val tls: SecurityConfig = SecurityConfig(),

    val username: String = "",

    val secretKey: String = "",

    val peerPublicKey: String = "",

    val preSharedKey: String? = null,

    val reserved: String? = null,

    val wireguardMtu: Int? = null,

    val awg: AwgConfig = AwgConfig(),

    val obfsPassword: String? = null,

    val portHopping: String? = null,

    val pinnedCertSha256: String? = null,

    val localAddresses: List<String> = emptyList(),

    val dnsServers: List<String> = emptyList(),

    val wireguardKeepalive: Int? = null,

    val allowedIps: List<String> = emptyList(),

    val bandwidthUp: String? = null,
    val bandwidthDown: String? = null,
) {
    val transportLabel: String
        get() = buildString {
            append(protocol.label)
            if (protocol == Protocol.AMNEZIAWG && !awg.isEmpty) append(" · obfs")
            if (protocol.supportsTransport) {
                append(" · ")
                append(transport.network.uppercase())
                when (tls.security) {
                    "reality" -> append(" / REALITY")
                    "tls" -> append(" / TLS")
                }
            }
        }
}
