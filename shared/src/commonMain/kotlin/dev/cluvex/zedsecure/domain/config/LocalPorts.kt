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

    fun isInternal(port: Int): Boolean =
        port == XRAY_SOCKS || port == XRAY_HTTP || port == SNI_SPOOF || port == DESKTOP_METRICS ||
            port == SHIM || port == PSIPHON_SOCKS || port == PSIPHON_HTTP || port == TOR_SOCKS ||
            port in SSH..SSH_MAX || port in DNS_TUNNEL..DNS_TUNNEL_MAX ||
            port in MASTER_DNS..MASTER_DNS_MAX || port in SSH_OVER_DNS..SSH_OVER_DNS_MAX

    fun lanSocksPort(requested: Int): Int =
        if (requested in 1024..65534 && !isInternal(requested) && !isInternal(requested + 1)) requested
        else LAN_SOCKS
}
