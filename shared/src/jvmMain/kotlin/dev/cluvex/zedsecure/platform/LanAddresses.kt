package dev.cluvex.zedsecure.platform

import java.net.Inet4Address
import java.net.NetworkInterface

object LanAddresses {
    private val SKIPPED_PREFIXES = listOf("tun", "ppp", "rmnet", "ccmni", "dummy", "ipsec", "clat")

    fun ipv4(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { ni ->
                runCatching { ni.isUp && !ni.isLoopback && !ni.isPointToPoint && !ni.isVirtual }.getOrDefault(false) &&
                    SKIPPED_PREFIXES.none { ni.name.orEmpty().startsWith(it, ignoreCase = true) }
            }
            .flatMap { ni -> ni.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { it.isSiteLocalAddress }
            .mapNotNull { it.hostAddress }
            .distinct()
            .sorted()
    }.getOrDefault(emptyList())
}
