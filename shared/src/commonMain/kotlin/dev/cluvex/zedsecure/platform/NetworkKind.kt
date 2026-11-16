package dev.cluvex.zedsecure.platform

enum class NetworkKind {
    Wifi,
    Cellular,
    Ethernet,
    Unknown,
}

/** Best-effort network transport detection. Never throws. */
internal expect fun detectNetworkKind(): NetworkKind
