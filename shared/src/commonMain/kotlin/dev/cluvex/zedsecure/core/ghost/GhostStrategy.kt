package dev.cluvex.zedsecure.core.ghost

/**
 * High-level anti-DPI strategies for GHOST.
 *
 * Inspired by zapret's multi-mode desync philosophy, adapted to pure
 * application-layer decoys (this project cannot inject raw TCP fakes).
 *
 * Mapping (conceptual):
 * - Phase 0  ~ connection priming noise (probe requests)
 * - Phase 1  ~ "fake" flood before/around real session patterns
 * - Phase 2  ~ main domestic-app mimicry
 * - Phase 3  ~ residual / disordered tail noise
 */
enum class GhostStrategy {
    /** Safer mix – default for unknown networks */
    Balanced,

    /** Higher volume + burst; good on Wi‑Fi */
    Aggressive,

    /** Maximum analysis cost – all profiles, heavy burst, disorder */
    MaxNoise,

    /** Mobile-first: strong enough noise, controlled data use */
    CellularStealth,

    /** Engine picks from network + pressure */
    Auto,
    ;

    companion object {
        fun fromRaw(raw: String?): GhostStrategy {
            return when (raw?.trim()?.lowercase()) {
                "balanced" -> Balanced
                "aggressive" -> Aggressive
                "max", "maxnoise", "max_noise" -> MaxNoise
                "cellular", "cellularstealth", "stealth" -> CellularStealth
                "auto", null, "" -> Auto
                else -> Auto
            }
        }
    }
}

data class StrategyProfile(
    val strategy: GhostStrategy,
    val profiles: String,
    val volumePercent: Int,
    val intervalSeconds: Int,
    val burst: Boolean,
    val burstIntensity: Int,
    val parallel: Int,
    val multiphase: Boolean,
    val disorder: Boolean,
    val fakeFlood: Boolean,
    val multiApp: Boolean,
)

object GhostStrategyResolver {

    fun resolve(
        requested: GhostStrategy,
        networkIsCellular: Boolean,
        networkIsWifi: Boolean,
        underPressure: Boolean,
    ): StrategyProfile {
        val effective = when (requested) {
            GhostStrategy.Auto -> when {
                underPressure && networkIsWifi -> GhostStrategy.MaxNoise
                underPressure && networkIsCellular -> GhostStrategy.Aggressive
                networkIsCellular -> GhostStrategy.CellularStealth
                networkIsWifi -> GhostStrategy.Aggressive
                underPressure -> GhostStrategy.Aggressive
                else -> GhostStrategy.Balanced
            }
            else -> requested
        }
        return profileFor(effective, underPressure)
    }

    private fun profileFor(s: GhostStrategy, underPressure: Boolean): StrategyProfile {
        return when (s) {
            GhostStrategy.Balanced -> StrategyProfile(
                strategy = s,
                profiles = "rubika,neshan,digikala,snapp,aparat",
                volumePercent = if (underPressure) 55 else 42,
                intervalSeconds = if (underPressure) 8 else 12,
                burst = true,
                burstIntensity = if (underPressure) 3 else 2,
                parallel = 2,
                multiphase = true,
                disorder = true,
                fakeFlood = underPressure,
                multiApp = true,
            )
            GhostStrategy.Aggressive -> StrategyProfile(
                strategy = s,
                profiles = "rubika,digikala,neshan,snapp,aparat,balad,generic",
                volumePercent = if (underPressure) 65 else 52,
                intervalSeconds = if (underPressure) 6 else 9,
                burst = true,
                burstIntensity = if (underPressure) 4 else 3,
                parallel = if (underPressure) 3 else 2,
                multiphase = true,
                disorder = true,
                fakeFlood = true,
                multiApp = true,
            )
            GhostStrategy.MaxNoise -> StrategyProfile(
                strategy = s,
                profiles = "rubika,digikala,neshan,snapp,aparat,balad,torob,generic",
                volumePercent = if (underPressure) 75 else 62,
                intervalSeconds = if (underPressure) 5 else 7,
                burst = true,
                burstIntensity = 5,
                parallel = 4,
                multiphase = true,
                disorder = true,
                fakeFlood = true,
                multiApp = true,
            )
            GhostStrategy.CellularStealth -> StrategyProfile(
                strategy = s,
                profiles = if (underPressure)
                    "rubika,neshan,snapp,digikala,aparat,balad,generic"
                else
                    "rubika,neshan,snapp,digikala,aparat",
                volumePercent = if (underPressure) 58 else 44,
                intervalSeconds = if (underPressure) 7 else 11,
                burst = true,
                burstIntensity = if (underPressure) 4 else 2,
                parallel = if (underPressure) 3 else 2,
                multiphase = true,
                disorder = true,
                fakeFlood = underPressure || true,
                multiApp = true,
            )
            GhostStrategy.Auto -> profileFor(GhostStrategy.Balanced, underPressure)
        }
    }
}
