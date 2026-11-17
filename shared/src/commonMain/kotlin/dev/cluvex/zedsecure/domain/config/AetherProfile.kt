package dev.cluvex.zedsecure.domain.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Settings for the isolated Aether WARP engine. */
@Serializable
data class AetherProfile(
    val marker: String = MARKER,
    val profileName: String = "Aether",
    val protocol: String = "masque",
    val scanMode: String = "balanced",
    val ipVersion: String = "v4",
    val noize: String = "firewall",
    val http2: Boolean = false,
    /** Sends the TLS ClientHello in pieces (anti-DPI); only meaningful with HTTP/2 transport. */
    val fragment: Boolean = false,
    /** Optional IP for the WARP registration API, for networks that block its default addresses. */
    val apiAddress: String = "",
) {
    fun toArgs(bindPort: Int, workingDirectory: String): List<String> {
        require(protocol in PROTOCOLS) { "Unsupported Aether protocol" }
        require(scanMode in SCAN_MODES) { "Unsupported Aether scan mode" }
        require(ipVersion in IP_VERSIONS) { "Unsupported Aether IP version" }
        require(noize in NOIZE_PROFILES) { "Unsupported Aether obfuscation profile" }
        require(!http2 || protocol != "wg") { "HTTP/2 transport is not available for WireGuard mode" }
        return buildList {
            add("--bind"); add("127.0.0.1:$bindPort")
            add("--protocol"); add(protocol)
            add("--scan"); add(scanMode)
            add("--ip"); add(ipVersion)
            add("--noize"); add(noize)
            if (http2) add("--h2")
            if (fragment && http2) add("--fragment")
            if (apiAddress.isNotBlank() && apiAddress.all { it.isLetterOrDigit() || it in ".:-[]" }) {
                add("--api-address"); add(apiAddress.trim())
            }
            // Keep the last known working gateway across transient reconnects and
            // emit native tunnel counters into the existing Aether log stream.
            add("--quick-reconnect")
            add("--stats"); add("--stats-secs"); add("15")
            add("--log-level"); add("info")
            add("--config"); add("$workingDirectory/aether.toml")
        }
    }

    /**
     * Ordered connection attempts: this profile first, then progressively more conservative
     * variants (other transport, stronger obfuscation, WireGuard, plain). The service tries each
     * until real traffic passes, so one unlucky setting never leaves the user with an error.
     */
    fun attempts(): List<AetherProfile> = buildList {
        add(this@AetherProfile)
        if (protocol == "masque") add(copy(http2 = !http2))
        add(copy(protocol = "masque", http2 = true, fragment = true, noize = "gfw", scanMode = "thorough"))
        add(copy(protocol = "masque", http2 = true, fragment = true, noize = "gfw", scanMode = "thorough", apiAddress = "162.159.137.105"))
        add(copy(protocol = "wg", http2 = false, noize = "balanced", scanMode = "balanced"))
        add(copy(protocol = "masque", http2 = false, noize = "off", ipVersion = "v4", scanMode = "balanced"))
    }.distinct()

    companion object {
        const val MARKER = "ZX_AETHER"
        private val PROTOCOLS = setOf("masque", "wg", "gool", "mim")
        private val SCAN_MODES = setOf("turbo", "balanced", "thorough", "verified", "ironclad")
        private val IP_VERSIONS = setOf("v4", "v6", "both")
        private val NOIZE_PROFILES = setOf("off", "light", "firewall", "balanced", "gfw", "aggressive")

        fun decode(raw: String): AetherProfile? = runCatching {
            val json = Json { ignoreUnknownKeys = true }
            val element = json.parseToJsonElement(raw)
            if (element !is kotlinx.serialization.json.JsonObject ||
                element["marker"]?.toString() != "\"ZX_AETHER\""
            ) {
                null
            } else {
                json.decodeFromString<AetherProfile>(raw)
            }
        }.getOrNull()?.takeIf { it.marker == MARKER }

        fun encode(profile: AetherProfile): String =
            Json { encodeDefaults = true }.encodeToString(profile.copy(marker = MARKER))
    }
}
