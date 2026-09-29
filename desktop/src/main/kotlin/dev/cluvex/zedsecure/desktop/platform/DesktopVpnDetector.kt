package dev.cluvex.zedsecure.desktop.platform

import java.net.Inet4Address
import java.net.NetworkInterface

object DesktopVpnDetector {
    private val TUNNEL_NAME = Regex(
        "^(tun|tap|wg|utun|ppp|ipsec|nordlynx|proton|mullvad|outline)|wintun|wireguard|openvpn|tap-windows|warp|anyconnect|pangp|vpn",
        RegexOption.IGNORE_CASE,
    )
    private val MESH_NAME = Regex("tailscale|zerotier|^zt", RegexOption.IGNORE_CASE)

    fun anyVpn(): Boolean = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().any(::isVpnInterface)
    }.getOrDefault(false)

    private fun isVpnInterface(ni: NetworkInterface): Boolean = runCatching {
        if (!ni.isUp || ni.isLoopback) return false
        val names = listOfNotNull(ni.name, ni.displayName)
        if (names.any { MESH_NAME.containsMatchIn(it) }) return false
        val looksLikeTunnel = ni.isPointToPoint || names.any { TUNNEL_NAME.containsMatchIn(it) }
        looksLikeTunnel && ni.inetAddresses.toList().any { address ->
            address is Inet4Address && !address.isLoopbackAddress && !address.isLinkLocalAddress &&

                !(address.address[0].toInt() == 100 && (address.address[1].toInt() and 0xC0) == 64)
        }
    }.getOrDefault(false)
}
