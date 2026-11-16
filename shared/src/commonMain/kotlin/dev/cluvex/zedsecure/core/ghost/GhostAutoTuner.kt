package dev.cluvex.zedsecure.core.ghost

import dev.cluvex.zedsecure.domain.model.GhostSettings
import dev.cluvex.zedsecure.platform.NetworkKind
import dev.cluvex.zedsecure.platform.detectNetworkKind

/**
 * AUTO planner – network + pressure + GhostStrategy → effective GhostSettings.
 */
object GhostAutoTuner {

    data class Plan(
        val network: NetworkKind,
        val label: String,
        val settings: GhostSettings,
        val reason: String,
        val strategyName: String,
    )

    fun currentNetwork(): NetworkKind = detectNetworkKind()

    fun plan(base: GhostSettings, underPressure: Boolean): Plan {
        val net = detectNetworkKind()
        val requested = GhostStrategy.fromRaw(base.strategy)
        val cellular = net == NetworkKind.Cellular
        val wifi = net == NetworkKind.Wifi || net == NetworkKind.Ethernet

        val resolved = GhostStrategyResolver.resolve(
            requested = if (base.autoMode) GhostStrategy.Auto else requested,
            networkIsCellular = cellular,
            networkIsWifi = wifi,
            underPressure = underPressure,
        )

        val s = base.copy(
            appProfilesEnabled = true,
            activeProfiles = resolved.profiles,
            decoyEngineEnabled = true,
            decoyVolumePercent = resolved.volumePercent,
            decoyIntervalSeconds = resolved.intervalSeconds,
            appLayerMimicryEnabled = true,
            multiAppConcurrent = resolved.multiApp,
            adaptiveFallbackEnabled = true,
            pressureDetectionEnabled = true,
            burstModeEnabled = resolved.burst,
            burstIntensity = resolved.burstIntensity,
            maxParallelRequests = resolved.parallel,
            multiphaseEnabled = resolved.multiphase,
            requestDisorder = resolved.disorder,
            fakeFloodEnabled = resolved.fakeFlood,
            strategy = resolved.strategy.name.lowercase(),
        )

        val label = when (net) {
            NetworkKind.Cellular -> "داده همراه"
            NetworkKind.Wifi -> "وای‌فای"
            NetworkKind.Ethernet -> "اترنت"
            NetworkKind.Unknown -> "نامشخص"
        }

        val reason = buildString {
            append("استراتژی ${resolved.strategy.name}")
            append(" | شبکه $label")
            if (underPressure) append(" | تحت فشار DPI")
            append(" | حجم ${resolved.volumePercent}%")
            if (resolved.fakeFlood) append(" | FakeFlood")
            if (resolved.multiphase) append(" | MultiPhase")
        }

        return Plan(
            network = net,
            label = label,
            settings = s,
            reason = reason,
            strategyName = resolved.strategy.name,
        )
    }
}
