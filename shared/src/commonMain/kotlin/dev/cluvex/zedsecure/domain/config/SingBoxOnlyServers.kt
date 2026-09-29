package dev.cluvex.zedsecure.domain.config

object SingBoxOnlyServers {
    fun isPlaceholder(profile: VpnProfile): Boolean =
        (profile.source is ProfileSource.Link || profile.source is ProfileSource.RawJson) &&
            isLocalAddress(profile.address)

    fun isLocalAddress(address: String): Boolean {
        val host = address.trim().removePrefix("[").removeSuffix("]").lowercase()
        if (host == "localhost" || host == "::1" || host == "::" || host == "0.0.0.0") return true
        val octets = host.split('.')
        return octets.size == 4 && octets[0] == "127" &&
            octets.all { octet -> octet.toIntOrNull()?.let { it in 0..255 } == true }
    }

    fun missing(imported: List<VpnProfile>, servers: List<SingBoxJson.Server>): List<SingBoxJson.Server> {
        val present = imported.filterNot { isPlaceholder(it) }.mapNotNull { endpointOf(it) }.toSet()
        return servers.filter { server ->
            val address = server.address?.lowercase() ?: return@filter true
            if (isLocalAddress(address)) return@filter false
            val port = server.port ?: return@filter true
            Endpoint(address, port, family(server.type)) !in present && Endpoint(address, port, null) !in present
        }
    }

    fun splice(
        profiles: List<VpnProfile>,
        placeholders: List<VpnProfile>,
        replacements: List<VpnProfile>,
    ): List<VpnProfile> {
        if (placeholders.isEmpty()) return profiles
        val ids = placeholders.map { it.id }
        val byPlaceholder: Map<String, List<VpnProfile>> =
            if (placeholders.size == replacements.size) {
                ids.zip(replacements.map { listOf(it) }).toMap()
            } else {
                ids.associateWith { emptyList<VpnProfile>() } + (ids.first() to replacements)
            }
        return profiles.flatMap { byPlaceholder[it.id] ?: listOf(it) }
    }

    private data class Endpoint(val address: String, val port: Int, val family: String?)

    private fun endpointOf(profile: VpnProfile): Endpoint? {
        if (profile.port !in 1..65535 || profile.address.isBlank() || profile.address == "-") return null
        val protocol = when (val src = profile.source) {
            is ProfileSource.RawJson -> CustomConfig.inspect(src.json).protocol?.lowercase()?.let { name ->
                Protocol.entries.firstOrNull { it.id == name }?.let { xrayFamily(it) } ?: name
            }
            is ProfileSource.SingBox -> SingBoxJson.servers(src.json).firstOrNull()?.type
            is ProfileSource.Link -> if (SingBoxLinks.handles(src.link)) {
                runCatching { SingBoxJson.servers(SingBoxLinks.parse(src.link).fragment).firstOrNull()?.type }.getOrNull()
            } else {
                runCatching { Protocol.valueOf(profile.protocol) }.getOrNull()?.let { xrayFamily(it) }
            }
            else -> null
        }
        return Endpoint(profile.address.lowercase(), profile.port, protocol?.let { family(it) })
    }

    private fun xrayFamily(protocol: Protocol): String = when (protocol) {
        Protocol.HYSTERIA -> "hysteria2"
        Protocol.AMNEZIAWG -> "wireguard"
        else -> protocol.id
    }

    private fun family(protocol: String): String = when (val p = protocol.lowercase()) {
        "ss" -> "shadowsocks"
        "socks5" -> "socks"
        "hy2" -> "hysteria2"
        else -> p
    }
}
