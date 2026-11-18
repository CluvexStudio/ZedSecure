package dev.cluvex.zedsecure.core

import dev.cluvex.zedsecure.core.ghost.GhostEngine
import dev.cluvex.zedsecure.domain.model.GhostSettings

/**
 * Coordinates the existing GHOST engine with the VPN session lifecycle.
 *
 * This bridge deliberately does not modify GhostEngine, tunnel configs,
 * packet routing, or any VPN core implementation.
 */
internal object GhostTunnelBridge {
    fun onTunnelConnected(settings: GhostSettings) {
        // updateSettings is idempotent while the existing engine is running
        // and respects the user's existing GHOST switches and profiles.
        GhostEngine.updateSettings(settings)
        // finishConnected() is shared by every core; explicitly publish the
        // tunnel lifecycle so GHOST is core-agnostic instead of remaining idle.
        GhostEngine.onTunnelConnected()
    }

    fun onTunnelStopped() {
        // Keep background GHOST traffic from outliving the VPN session.
        GhostEngine.onTunnelDisconnected()
    }
}
