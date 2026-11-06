package dev.cluvex.zedsecure.domain.config

import kotlinx.serialization.Serializable

/** Settings for the pinned Aether 2.4 CLI. Identities stay on the device, outside share links. */
@Serializable
data class AetherProfile(
    val protocol: String = "masque",
    val http2: Boolean = false,
    val scan: String = "balanced",
    val ipVersion: String = "v4",
    val noize: String = "firewall",
    val peer: String = "",
    val fragment: Boolean = false,
    val ech: Boolean = false,
    val tlsVerify: Boolean = true,
) {
    fun validate() {
        require(protocol in PROTOCOLS) { "Unsupported Aether protocol" }
        require(scan in SCAN_MODES) { "Unsupported Aether scan mode" }
        require(ipVersion in IP_VERSIONS) { "Unsupported Aether IP version" }
        require(noize in NOIZE_PROFILES) { "Unsupported Aether noise profile" }
        require(!http2 || usesMasque) { "HTTP/2 needs a MASQUE transport" }
        require(peer.isBlank() || validPeer(peer.trim())) { "Aether peer must be an IP address and port" }
        require(protocol != "gool-classic" || peer.isBlank()) { "Classic gool discovers both peers automatically" }
    }

    val usesMasque: Boolean get() = protocol in listOf("masque", "gool", "mim")

    /** Pass each argument separately to ProcessBuilder, never to a shell. */
    fun arguments(identityPath: String, socksPort: Int): List<String> {
        validate()
        require(socksPort in 1..65535)
        require(identityPath.isNotBlank())
        return buildList {
            add("--$protocol")
            if (usesMasque) add(if (http2) "--h2" else "--h3")
            addAll(listOf("--scan", scan, "--ip", ipVersion, "--noize", noize))
            addAll(listOf("--bind", "127.0.0.1:$socksPort", "--config", identityPath))
            add("--quick-reconnect")
            if (peer.isNotBlank()) addAll(listOf("--peer", peer.trim()))
            if (fragment) add("--fragment")
            if (ech && usesMasque) addAll(listOf("--ech", "auto"))
            if (tlsVerify && usesMasque) add("--tls-verify")
        }
    }

    companion object {
        val PROTOCOLS = listOf("masque", "wg", "gool", "gool-classic", "mim")
        val SCAN_MODES = listOf("turbo", "balanced", "thorough", "verified", "ironclad")
        val IP_VERSIONS = listOf("v4", "v6", "both")
        val NOIZE_PROFILES = listOf("off", "light", "firewall", "balanced", "gfw", "aggressive")

        fun validPeer(value: String): Boolean {
            val host: String
            val port: String
            if (value.startsWith("[")) {
                val end = value.indexOf("]:")
                if (end < 0) return false
                host = value.substring(1, end)
                port = value.substring(end + 2)
                if (!validIpv6(host)) return false
            } else {
                host = value.substringBeforeLast(':', "")
                port = value.substringAfterLast(':', "")
                if (!validIpv4(host)) return false
            }
            return port.isNotEmpty() && port.all { it in '0'..'9' } && port.toIntOrNull()?.let { it in 1..65535 } == true
        }

        private fun validIpv4(host: String): Boolean = host.split('.').let { parts ->
            parts.size == 4 && parts.all {
                it.isNotEmpty() && it.length <= 3 && it.all { c -> c in '0'..'9' } && it.toIntOrNull()?.let { n -> n in 0..255 } == true
            }
        }

        private fun validIpv6(host: String): Boolean {
            if (host.isEmpty() || host.any { it !in "0123456789abcdefABCDEF:" }) return false
            if (":::" in host || host.windowed(2).count { it == "::" } > 1) return false
            val parts = host.split(':').filter { it.isNotEmpty() }
            if (parts.any { it.length > 4 }) return false
            return if ("::" in host) parts.size < 8
            else parts.size == 8 && !host.startsWith(':') && !host.endsWith(':')
        }
    }
}
