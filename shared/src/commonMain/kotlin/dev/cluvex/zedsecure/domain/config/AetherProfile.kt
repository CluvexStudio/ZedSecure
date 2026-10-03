package dev.cluvex.zedsecure.domain.config

import kotlinx.serialization.Serializable

@Serializable
data class AetherProfile(
    val protocol: String = PROTOCOL_MASQUE,
    val transport: String = TRANSPORT_H3,
    val scanMode: String = SCAN_BALANCED,
    val obfuscation: String = NOISE_AUTO,
    val ipVersion: String = IP_V4,

    val server: String = "",
    val wiwOuter: String = "",
    val wiwInner: String = "",

    val ech: Boolean = false,
    val echDns: String = "",
    val echDomain: String = "",
    val fragment: Boolean = false,
    val fragmentSize: String = "16-32",
    val fragmentDelay: String = "2-10",

    val dns: String = "1.1.1.1,1.0.0.1",
    val exitLoc: String = "",

    val torMode: String = CARRIER_OFF,
    val torBridges: String = "auto",
    val torRelays: String = "auto",
    val torBridgeLines: String = "",

    val psiphonMode: String = CARRIER_OFF,
    val psiphonTactics: String = "auto",
    val psiphonRegion: String = "",
    val psiphonCdnIps: String = "",
    val psiphonCdnSni: String = "",

    val teamName: String = "",
    val accessClientId: String = "",
    val accessClientSecret: String = "",
    val accessToken: String = "",
    val gateway: Boolean = false,

    val customCommand: String = "",
) {
    val isTwoHops: Boolean get() = protocol == PROTOCOL_GOOL || protocol == PROTOCOL_MIM
    val isOverMasque: Boolean get() = protocol == PROTOCOL_MASQUE || protocol == PROTOCOL_MIM

    companion object {
        const val PROTOCOL_MASQUE = "masque"
        const val PROTOCOL_WIREGUARD = "wg"
        const val PROTOCOL_GOOL = "gool"
        const val PROTOCOL_MIM = "mim"

        const val TRANSPORT_H3 = "h3"
        const val TRANSPORT_H2 = "h2"

        const val SCAN_TURBO = "turbo"
        const val SCAN_BALANCED = "balanced"
        const val SCAN_THOROUGH = "thorough"
        const val SCAN_VERIFIED = "verified"
        const val SCAN_IRONCLAD = "ironclad"

        const val NOISE_AUTO = "auto"
        const val NOISE_OFF = "off"
        const val NOISE_LIGHT = "light"
        const val NOISE_FIREWALL = "firewall"
        const val NOISE_BALANCED = "balanced"
        const val NOISE_GFW = "gfw"
        const val NOISE_AGGRESSIVE = "aggressive"

        const val IP_V4 = "v4"
        const val IP_V6 = "v6"
        const val IP_DUAL = "both"

        const val CARRIER_OFF = "off"
        const val CARRIER_CHAIN = "chain"
        const val CARRIER_REVERSE = "reverse"
        const val CARRIER_ONLY = "only"
    }
}
