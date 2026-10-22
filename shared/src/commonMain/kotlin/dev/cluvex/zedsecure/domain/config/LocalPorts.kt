package dev.cluvex.zedsecure.domain.config

object LocalPorts {
    const val XRAY_SOCKS = 10808

    const val XRAY_HTTP = 10809

    const val SNI_SPOOF = 10811

    const val DESKTOP_METRICS = 10817

    const val SHIM = 10820

    const val PSIPHON_SOCKS = 10830
    const val PSIPHON_HTTP = 10831

    const val SSH = 10840
    const val SSH_MAX = 10849

    const val DNS_TUNNEL = 10850
    const val DNS_TUNNEL_MAX = 10859

    const val MASTER_DNS = 10860
    const val MASTER_DNS_MAX = 10869

    const val SSH_OVER_DNS = 10870
    const val SSH_OVER_DNS_MAX = 10879

    const val TOR_SOCKS = 9250

    const val LAN_SOCKS = 10880

    const val CHAIN_CARRIER = 10890

    fun isInternal(port: Int): Boolean =
        port == XRAY_SOCKS || port == XRAY_HTTP || port == SNI_SPOOF || port == DESKTOP_METRICS ||
            port == SHIM || port == PSIPHON_SOCKS || port == PSIPHON_HTTP || port == TOR_SOCKS || port == CHAIN_CARRIER ||
            port in SSH..SSH_MAX || port in DNS_TUNNEL..DNS_TUNNEL_MAX ||
            port in MASTER_DNS..MASTER_DNS_MAX || port in SSH_OVER_DNS..SSH_OVER_DNS_MAX

    fun lanSocksPort(requested: Int): Int =
        if (requested in 1024..65534 && !isInternal(requested) && !isInternal(requested + 1)) requested
        else LAN_SOCKS

    /** Ports the other engines claim, so the local proxy port picker must not land on one of them.
     *  [XRAY_SOCKS] and [XRAY_HTTP] are deliberately left out: they are the pair the picker is
     *  placing — XRAY_HTTP is never bound on its own, it is always XRAY_SOCKS + 1 — so leaving
     *  them in would push every pick off 10808 and change the ordinary single-instance case. */
    val reservedPorts: Set<Int> = buildSet {
        addAll(
            listOf(
                SNI_SPOOF, DESKTOP_METRICS, SHIM,
                PSIPHON_SOCKS, PSIPHON_HTTP, TOR_SOCKS, LAN_SOCKS, CHAIN_CARRIER,
            ),
        )
        addAll(SSH..SSH_MAX)
        addAll(DNS_TUNNEL..DNS_TUNNEL_MAX)
        addAll(MASTER_DNS..MASTER_DNS_MAX)
        addAll(SSH_OVER_DNS..SSH_OVER_DNS_MAX)
    }
}
