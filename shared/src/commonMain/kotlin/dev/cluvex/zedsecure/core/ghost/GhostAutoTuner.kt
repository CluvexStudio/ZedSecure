package dev.cluvex.zedsecure.core.ghost

import dev.cluvex.zedsecure.domain.model.GhostSettings
import dev.cluvex.zedsecure.platform.NetworkKind
import dev.cluvex.zedsecure.platform.detectNetworkKind

/**
 * Fully automatic GHOST planner.
 * Looks at network type (Wi‑Fi / mobile data) + recent pressure and
 * produces an effective GhostSettings tuned for anti-DPI cost vs data usage.
 */
object GhostAutoTuner {

    data class Plan(
        val network: NetworkKind,
        val label: String,
        val settings: GhostSettings,
        val reason: String,
    )

    fun currentNetwork(): NetworkKind = detectNetworkKind()

    /**
     * @param base user preferences (enabled flags etc.)
     * @param underPressure from GhostEngine recent failure rate
     */
    fun plan(base: GhostSettings, underPressure: Boolean): Plan {
        val net = detectNetworkKind()
        return when (net) {
            NetworkKind.Cellular -> cellularPlan(base, underPressure)
            NetworkKind.Wifi, NetworkKind.Ethernet -> wifiPlan(base, underPressure)
            NetworkKind.Unknown -> balancedPlan(base, underPressure)
        }
    }

    /** Mobile data: save volume, still look domestic, lighter burst. */
    private fun cellularPlan(base: GhostSettings, underPressure: Boolean): Plan {
        val volume = if (underPressure) 45 else 30
        val interval = if (underPressure) 10 else 16
        val burst = underPressure
        val intensity = if (underPressure) 2 else 1
        val profiles = "rubika,neshan,snapp,digikala"
        val s = base.copy(
            appProfilesEnabled = true,
            activeProfiles = profiles,
            decoyEngineEnabled = true,
            decoyVolumePercent = volume,
            decoyIntervalSeconds = interval,
            appLayerMimicryEnabled = true,
            multiAppConcurrent = true,
            adaptiveFallbackEnabled = true,
            pressureDetectionEnabled = true,
            burstModeEnabled = burst,
            burstIntensity = intensity,
            maxParallelRequests = if (underPressure) 2 else 1,
        )
        return Plan(
            network = NetworkKind.Cellular,
            label = "داده همراه",
            settings = s,
            reason = if (underPressure)
                "داده همراه + فشار شبکه → حجم متوسط، فاصله کمتر، Burst ملایم"
            else
                "داده همراه → مصرف کمتر، پروفایل‌های ضروری، بدون Burst سنگین",
        )
    }

    /** Wi‑Fi: can afford heavier decoy noise for stronger anti-DPI. */
    private fun wifiPlan(base: GhostSettings, underPressure: Boolean): Plan {
        val volume = if (underPressure) 60 else 48
        val interval = if (underPressure) 8 else 12
        val s = base.copy(
            appProfilesEnabled = true,
            activeProfiles = "rubika,digikala,neshan,snapp,aparat,balad,torob",
            decoyEngineEnabled = true,
            decoyVolumePercent = volume,
            decoyIntervalSeconds = interval,
            appLayerMimicryEnabled = true,
            multiAppConcurrent = true,
            adaptiveFallbackEnabled = true,
            pressureDetectionEnabled = true,
            burstModeEnabled = true,
            burstIntensity = if (underPressure) 4 else 3,
            maxParallelRequests = if (underPressure) 3 else 2,
        )
        return Plan(
            network = NetworkKind.Wifi,
            label = "وای‌فای",
            settings = s,
            reason = if (underPressure)
                "وای‌فای + فشار → نویز بالا، Burst قوی، همه پروفایل‌ها"
            else
                "وای‌فای → ضد DPI قوی‌تر با حجم بالاتر و Multi-App کامل",
        )
    }

    private fun balancedPlan(base: GhostSettings, underPressure: Boolean): Plan {
        val volume = if (underPressure) 50 else 40
        val interval = if (underPressure) 9 else 13
        val s = base.copy(
            appProfilesEnabled = true,
            activeProfiles = "rubika,neshan,digikala,snapp,aparat",
            decoyEngineEnabled = true,
            decoyVolumePercent = volume,
            decoyIntervalSeconds = interval,
            appLayerMimicryEnabled = true,
            multiAppConcurrent = true,
            adaptiveFallbackEnabled = true,
            pressureDetectionEnabled = true,
            burstModeEnabled = underPressure,
            burstIntensity = if (underPressure) 3 else 2,
            maxParallelRequests = 2,
        )
        return Plan(
            network = NetworkKind.Unknown,
            label = "نامشخص",
            settings = s,
            reason = if (underPressure)
                "نوع شبکه نامشخص + فشار → پروفایل متعادل با Burst"
            else
                "نوع شبکه نامشخص → تنظیم متعادل ضد DPI",
        )
    }
}
