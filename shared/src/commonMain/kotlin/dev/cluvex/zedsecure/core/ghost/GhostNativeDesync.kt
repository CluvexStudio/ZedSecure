package dev.cluvex.zedsecure.core.ghost

/**
 * Native packet-desync capability surface (zapret/nfqws-class).
 *
 * True nfqws needs Linux NFQUEUE + CAP_NET_ADMIN/root and C binary.
 * On stock Android VpnService without root, kernel desync is typically unavailable.
 *
 * This API exists so GHOST can:
 * 1) Probe real capability
 * 2) Use app-layer desync-*inspired* patterns when native is absent
 * 3) Later bind a reviewed native module without rewriting UI/engine
 */
data class NativeDesyncCapability(
    val available: Boolean,
    /** none | app_layer | native_nfq | unsupported */
    val mode: String,
    val detailFa: String,
)

enum class DesyncProfile {
    Off,
    /** TCP split-inspired: staggered multi-request bursts */
    Split,
    /** disorder-inspired: reverse/shuffle fire order aggressively */
    Disorder,
    /** fake-ttl-inspired: cheap probe burst then real mimic */
    FakeBurst,
    /** combine split+disorder+fake */
    Max,
    ;

    companion object {
        fun fromRaw(raw: String?): DesyncProfile = when (raw?.trim()?.lowercase()) {
            "split" -> Split
            "disorder" -> Disorder
            "fake", "fakeburst", "fake_burst" -> FakeBurst
            "max" -> Max
            else -> Off
        }
    }
}

expect object GhostNativeDesync {
    fun probe(): NativeDesyncCapability

    /**
     * Best-effort native arming. Returns false if not supported.
     * Must never throw into UI.
     */
    fun tryArm(profile: DesyncProfile): Boolean

    fun disarm()
}
