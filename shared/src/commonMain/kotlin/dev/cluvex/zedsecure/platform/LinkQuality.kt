package dev.cluvex.zedsecure.platform

/**
 * Local, privacy-preserving snapshot of the active link (no SIM, phone number or carrier ids are
 * read, nothing is uploaded). Android's bandwidth and signal values are estimates, not a speed test.
 */
data class LinkQuality(
    val downKbps: Int = 0,
    val upKbps: Int = 0,
    val signalDbm: Int? = null,
    val metered: Boolean = true,
    val validated: Boolean = true,
    /** "2G", "3G", "4G", "5G" or null when unknown. */
    val generation: String? = null,
)

/** Best-effort link quality; null when the platform cannot tell. Never throws. */
internal expect fun detectLinkQuality(): LinkQuality?
