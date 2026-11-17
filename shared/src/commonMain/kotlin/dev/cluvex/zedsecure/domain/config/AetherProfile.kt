package dev.cluvex.zedsecure.domain.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Settings for the isolated Aether WARP engine. */
@Serializable
data class AetherProfile(
    val marker: String = MARKER,
    val protocol: String = "masque",
    val scanMode: String = "balanced",
    val ipVersion: String = "v4",
    val noize: String = "firewall",
    val http2: Boolean = false,
) {
    fun toArgs(bindPort: Int, workingDirectory: String): List<String> {
        require(protocol in PROTOCOLS) { "Unsupported Aether protocol" }
        require(scanMode in SCAN_MODES) { "Unsupported Aether scan mode" }
        require(ipVersion in IP_VERSIONS) { "Unsupported Aether IP version" }
        require(noize in NOIZE_PROFILES) { "Unsupported Aether obfuscation profile" }
        return buildList {
            add("--bind"); add("127.0.0.1:$bindPort")
            add("--protocol"); add(protocol)
            add("--scan"); add(scanMode)
            add("--ip"); add(ipVersion)
            add("--noize"); add(noize)
            if (http2) add("--h2")
            add("--config"); add("$workingDirectory/aether.toml")
        }
    }

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
            Json.encodeToString(profile.copy(marker = MARKER))
    }
}
