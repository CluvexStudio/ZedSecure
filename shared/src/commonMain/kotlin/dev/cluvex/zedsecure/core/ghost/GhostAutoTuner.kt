package dev.cluvex.zedsecure.core.ghost

import dev.cluvex.zedsecure.domain.model.GhostSettings
import dev.cluvex.zedsecure.platform.NetworkKind
import dev.cluvex.zedsecure.platform.detectNetworkKind

/**
 * Fully automatic anti-DPI planner for GHOST.
 * Chooses profiles, volume, timing and burst from network type + pressure.
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

    /** Mobile data: conserve quota, still look like domestic apps. */
    private fun cellularPlan(base: GhostSettings, underPressure: Boolean): Plan {
        val s = base.copy(
            appProfilesEnabled = true,
            activeProfiles = if (underPressure)
                "rubika,neshan,snapp,digikala,aparat"
            else
                "rubika,neshan,snapp,digikala",
            decoyEngineEnabled = true,
            decoyVolumePercent = if (underPressure) 48 else 32,
            decoyIntervalSeconds = if (underPressure) 9 else 18,
            appLayerMimicryEnabled = true,
            multiAppConcurrent = true,
            adaptiveFallbackEnabled = true,
            pressureDetectionEnabled = true,
            burstModeEnabled = underPressure,
            burstIntensity = if (underPressure) 3 else 1,
            maxParallelRequests = if (underPressure) 2 else 1,
        )
        return Plan(
            network = NetworkKind.Cellular,
            label = "داده همراه",
            settings = s,
            reason = if (underPressure)
                "داده همراه + فشار DPI → حجم متوسط، فاصله کوتاه، Burst ملایم"
            else
                "داده همراه → مصرف کم، پروفایل‌های ضروری، بدون Burst سنگین",
        )
    }

    /** Wi‑Fi: maximize analysis cost for DPI. */
    private fun wifiPlan(base: GhostSettings, underPressure: Boolean): Plan {
        val s = base.copy(
            appProfilesEnabled = true,
            activeProfiles = "rubika,digikala,neshan,snapp,aparat,balad,torob,generic",
            decoyEngineEnabled = true,
            decoyVolumePercent = if (underPressure) 65 else 52,
            decoyIntervalSeconds = if (underPressure) 7 else 11,
            appLayerMimicryEnabled = true,
            multiAppConcurrent = true,
            adaptiveFallbackEnabled = true,
            pressureDetectionEnabled = true,
            burstModeEnabled = true,
            burstIntensity = if (underPressure) 5 else 3,
            maxParallelRequests = if (underPressure) 4 else 2,
        )
        return Plan(
            network = NetworkKind.Wifi,
            label = "وای‌فای",
            settings = s,
            reason = if (underPressure)
                "وای‌فای + فشار → حداکثر نویز، Burst قوی، همه پروفایل‌ها"
            else
                "وای‌فای → ضد DPI قوی با Multi-App و حجم بالا",
        )
    }

    private fun balancedPlan(base: GhostSettings, underPressure: Boolean): Plan {
        val s = base.copy(
            appProfilesEnabled = true,
            activeProfiles = "rubika,neshan,digikala,snapp,aparat",
            decoyEngineEnabled = true,
            decoyVolumePercent = if (underPressure) 55 else 42,
            decoyIntervalSeconds = if (underPressure) 8 else 13,
            appLayerMimicryEnabled = true,
            multiAppConcurrent = true,
            adaptiveFallbackEnabled = true,
            pressureDetectionEnabled = true,
            burstModeEnabled = underPressure || true, // light baseline noise when unknown
            burstIntensity = if (underPressure) 3 else 2,
            maxParallelRequests = 2,
        )
        return Plan(
            network = NetworkKind.Unknown,
            label = "نامشخص",
            settings = s,
            reason = if (underPressure)
                "شبکه نامشخص + فشار → پروفایل متعادل با Burst"
            else
                "شبکه نامشخص → تنظیم متعادل ضد DPI",
        )
    }
}
