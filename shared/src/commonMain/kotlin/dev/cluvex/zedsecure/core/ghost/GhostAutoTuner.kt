package dev.cluvex.zedsecure.core.ghost

import dev.cluvex.zedsecure.domain.model.GhostSettings
import dev.cluvex.zedsecure.platform.NetworkKind
import dev.cluvex.zedsecure.platform.detectNetworkKind

/**
 * Fully automatic anti-DPI planner for GHOST.
 *
 * Cellular is no longer "too quiet": baseline noise is meaningful so mobile
 * DPI still sees domestic app patterns; under pressure it escalates further.
 */
object GhostAutoTuner {

    data class Plan(
        val network: NetworkKind,
        val label: String,
        val settings: GhostSettings,
        val reason: String,
    )

    fun currentNetwork(): NetworkKind = detectNetworkKind()

    fun plan(base: GhostSettings, underPressure: Boolean): Plan {
        return when (val net = detectNetworkKind()) {
            NetworkKind.Cellular -> cellularPlan(base, underPressure)
            NetworkKind.Wifi, NetworkKind.Ethernet -> wifiPlan(base, underPressure)
            NetworkKind.Unknown -> balancedPlan(base, underPressure)
        }
    }

    /**
     * Mobile data: still anti-DPI-first.
     * Baseline noise is stronger than before; under pressure it approaches Wi‑Fi levels
     * (cost of analysis > data savings).
     */
    private fun cellularPlan(base: GhostSettings, underPressure: Boolean): Plan {
        val s = base.copy(
            appProfilesEnabled = true,
            // Always multi-app domestic signature; expand under pressure
            activeProfiles = if (underPressure)
                "rubika,neshan,snapp,digikala,aparat,balad,generic"
            else
                "rubika,neshan,snapp,digikala,aparat",
            decoyEngineEnabled = true,
            // Stronger baseline than earlier versions
            decoyVolumePercent = if (underPressure) 58 else 42,
            decoyIntervalSeconds = if (underPressure) 7 else 12,
            appLayerMimicryEnabled = true,
            multiAppConcurrent = true,
            adaptiveFallbackEnabled = true,
            pressureDetectionEnabled = true,
            // Light burst always on cellular so traffic is not "too clean"
            burstModeEnabled = true,
            burstIntensity = if (underPressure) 4 else 2,
            maxParallelRequests = if (underPressure) 3 else 2,
        )
        return Plan(
            network = NetworkKind.Cellular,
            label = "داده همراه",
            settings = s,
            reason = if (underPressure)
                "داده همراه + فشار DPI → نویز قوی، Burst بالا، پروفایل گسترده"
            else
                "داده همراه → نویز پایه قوی‌تر، Multi-App، Burst ملایم برای ضد DPI",
        )
    }

    /** Wi‑Fi: maximize analysis cost for DPI. */
    private fun wifiPlan(base: GhostSettings, underPressure: Boolean): Plan {
        val s = base.copy(
            appProfilesEnabled = true,
            activeProfiles = "rubika,digikala,neshan,snapp,aparat,balad,torob,generic",
            decoyEngineEnabled = true,
            decoyVolumePercent = if (underPressure) 70 else 55,
            decoyIntervalSeconds = if (underPressure) 6 else 10,
            appLayerMimicryEnabled = true,
            multiAppConcurrent = true,
            adaptiveFallbackEnabled = true,
            pressureDetectionEnabled = true,
            burstModeEnabled = true,
            burstIntensity = if (underPressure) 5 else 3,
            maxParallelRequests = if (underPressure) 4 else 3,
        )
        return Plan(
            network = NetworkKind.Wifi,
            label = "وای‌فای",
            settings = s,
            reason = if (underPressure)
                "وای‌فای + فشار → حداکثر نویز و Burst"
            else
                "وای‌فای → ضد DPI قوی با Multi-App کامل",
        )
    }

    private fun balancedPlan(base: GhostSettings, underPressure: Boolean): Plan {
        val s = base.copy(
            appProfilesEnabled = true,
            activeProfiles = "rubika,neshan,digikala,snapp,aparat,generic",
            decoyEngineEnabled = true,
            decoyVolumePercent = if (underPressure) 58 else 45,
            decoyIntervalSeconds = if (underPressure) 7 else 11,
            appLayerMimicryEnabled = true,
            multiAppConcurrent = true,
            adaptiveFallbackEnabled = true,
            pressureDetectionEnabled = true,
            burstModeEnabled = true,
            burstIntensity = if (underPressure) 4 else 2,
            maxParallelRequests = if (underPressure) 3 else 2,
        )
        return Plan(
            network = NetworkKind.Unknown,
            label = "نامشخص",
            settings = s,
            reason = if (underPressure)
                "شبکه نامشخص + فشار → نویز بالا"
            else
                "شبکه نامشخص → تنظیم متعادل ضد DPI با Burst پایه",
        )
    }
}
