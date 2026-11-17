package dev.cluvex.zedsecure.domain.config

import dev.cluvex.zedsecure.platform.NetworkKind

/** What the user wants the Smart config to favor. */
enum class AetherSmartGoal { Auto, Speed, Stealth }

/** What the app learned about the user's connection before building an Aether profile. */
data class AetherSmartInput(
    val network: NetworkKind = NetworkKind.Unknown,
    val hasIpv6: Boolean = false,
    val pingMs: Int? = null,
    /** Two-letter country code of the user's current network, when known. */
    val countryCode: String? = null,
    /** Result of the live reachability probe: null = not measured. */
    val edgeReachable: Boolean? = null,
    val edgeMs: Int? = null,
    val apiReachable: Boolean? = null,
    /** Spread between two edge samples; a high value marks an unstable link. */
    val edgeJitterMs: Int? = null,
    val goal: AetherSmartGoal = AetherSmartGoal.Auto,
)

/**
 * Smart config: fills every Aether field from the user's own network instead of asking for them.
 *
 * - Mobile data usually throttles UDP and inspects traffic harder, so it gets the strongest
 *   anti-DPI profile, a thorough scan and the HTTP/2 (TCP) transport as a fallback path.
 * - Wi-Fi and Ethernet keep the faster UDP transport with a balanced profile.
 * - A slow or lossy link (high ping) uses the quicker scan so the connection comes up sooner.
 * - IPv6 is enabled only when the network really has it.
 * - Networks known for heavy filtering stay on the strict anti-DPI profile whatever the transport.
 */
object AetherSmartConfig {
    private val heavilyFiltered = setOf("IR", "CN", "RU", "TM", "BY", "MM")

    fun build(input: AetherSmartInput): AetherProfile {
        val cellular = input.network == NetworkKind.Cellular
        val filtered = input.countryCode?.uppercase() in heavilyFiltered
        val slow = (input.pingMs ?: 0) >= SLOW_PING_MS
        val edgeBlocked = input.edgeReachable == false
        val apiBlocked = input.apiReachable == false
        val fastEdge = input.edgeReachable == true && (input.edgeMs ?: Int.MAX_VALUE) < FAST_EDGE_MS

        val unstable = (input.edgeJitterMs ?: 0) >= UNSTABLE_JITTER_MS
        val strict = filtered || edgeBlocked || input.goal == AetherSmartGoal.Stealth
        val noize = when {
            strict -> "gfw"
            cellular -> "firewall"
            else -> "balanced"
        }
        val scan = when {
            input.goal == AetherSmartGoal.Stealth -> "verified"
            input.goal == AetherSmartGoal.Speed && !edgeBlocked -> "turbo"
            edgeBlocked || unstable -> "thorough"
            fastEdge && !filtered -> "turbo"
            slow -> "turbo"
            cellular || filtered -> "thorough"
            else -> "balanced"
        }
        val h2 = (cellular || strict) && !(input.goal == AetherSmartGoal.Speed && !edgeBlocked && !filtered)
        return AetherProfile(
            profileName = nameFor(input.network),
            protocol = "masque",
            scanMode = scan,
            ipVersion = if (input.hasIpv6) "both" else "v4",
            noize = noize,
            http2 = h2,
            fragment = h2 && strict,
            apiAddress = if (apiBlocked) "162.159.137.105" else "",
        )
    }

    fun nameFor(network: NetworkKind): String = when (network) {
        NetworkKind.Cellular -> "Aether · Smart · Mobile"
        NetworkKind.Wifi -> "Aether · Smart · Wi-Fi"
        NetworkKind.Ethernet -> "Aether · Smart · LAN"
        NetworkKind.Unknown -> "Aether · Smart"
    }

    private const val SLOW_PING_MS = 350
    private const val FAST_EDGE_MS = 220
    private const val UNSTABLE_JITTER_MS = 150
}
