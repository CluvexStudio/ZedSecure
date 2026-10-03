package dev.cluvex.zedsecure.domain.config

object AetherCoreBuilder {
    fun buildArgs(
        profile: AetherProfile,
        socksPort: Int,
        httpProxyPort: Int? = null,
        workDir: String? = null,
        fwMark: Int? = null,
        psiphonBin: String? = null,
    ): List<String> = buildList {
        if (profile.customCommand.isNotBlank()) {
            val words = profile.customCommand.trim().split(Regex("\\s+"))
            val clean = if (words.firstOrNull()?.startsWith("-") == false) words.drop(1) else words
            addAll(clean)
            return@buildList
        }

        val warpPort = if (profile.psiphonMode == AetherProfile.CARRIER_CHAIN) socksPort + 2 else socksPort
        val psiphonPort = if (profile.psiphonMode == AetherProfile.CARRIER_CHAIN) socksPort else socksPort + 2

        add("--bind"); add("127.0.0.1:$warpPort")
        httpProxyPort?.let { add("--http-proxy"); add("127.0.0.1:$it") }
        fwMark?.let { add("--mark"); add(it.toString()) }

        add("--protocol"); add(profile.protocol)
        add("--scan"); add(profile.scanMode)
        if (profile.obfuscation != AetherProfile.NOISE_AUTO) {
            add("--noize"); add(profile.obfuscation)
        }
        add("--ip"); add(profile.ipVersion)

        if (profile.dns.isNotBlank()) { add("--dns"); add(profile.dns) }
        if (profile.exitLoc.isNotBlank()) { add("--exit-loc"); add(profile.exitLoc) }

        if (profile.isOverMasque) {
            if (profile.transport == AetherProfile.TRANSPORT_H2) {
                add("--h2")
                if (profile.fragment) {
                    add("--fragment")
                    add("--fragment-size"); add(profile.fragmentSize)
                    add("--fragment-delay"); add(profile.fragmentDelay)
                }
            } else {
                add("--h3")
            }
            if (profile.ech) {
                add("--ech"); add("auto")
                if (profile.echDns.isNotBlank()) { add("--ech-dns"); add(profile.echDns) }
                if (profile.echDomain.isNotBlank()) { add("--ech-domain"); add(profile.echDomain) }
            }
        }

        if (profile.isTwoHops) {
            val flagPrefix = if (profile.protocol == AetherProfile.PROTOCOL_MIM) "--mim" else "--wiw"
            if (profile.wiwOuter.isNotBlank()) { add("$flagPrefix-outer"); add(profile.wiwOuter) }
            if (profile.wiwInner.isNotBlank()) { add("$flagPrefix-inner"); add(profile.wiwInner) }
            if (profile.wiwOuter.isBlank() && profile.wiwInner.isBlank()) add("$flagPrefix-scan")
        } else if (profile.server.isNotBlank()) {
            add("--peer"); add(profile.server)
        }

        when (profile.torMode) {
            AetherProfile.CARRIER_CHAIN -> add("--tor")
            AetherProfile.CARRIER_REVERSE -> add("--tor-reverse")
            AetherProfile.CARRIER_ONLY -> add("--tor-only")
        }
        if (profile.torMode != AetherProfile.CARRIER_OFF) {
            when (profile.torBridges) {
                "first" -> add("--tor-bridges")
                "never" -> add("--no-tor-bridges")
            }
            if (profile.torRelays != "auto") { add("--tor-relays"); add(profile.torRelays) }
            if (profile.torBridgeLines.isNotBlank()) {
                profile.torBridgeLines.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
                    .forEach { add("--tor-bridge"); add(it) }
            }
        }

        when (profile.psiphonMode) {
            AetherProfile.CARRIER_CHAIN -> {
                add("--psiphon")
                add("--psiphon-bind"); add("127.0.0.1:$psiphonPort")
            }
            AetherProfile.CARRIER_REVERSE -> add("--psiphon-reverse")
            AetherProfile.CARRIER_ONLY -> add("--psiphon-only")
        }
        if (profile.psiphonMode != AetherProfile.CARRIER_OFF) {
            psiphonBin?.let { add("--psiphon-bin"); add(it) }
            if (profile.psiphonTactics != "auto") { add("--psiphon-mode"); add(profile.psiphonTactics) }
            if (profile.psiphonRegion.isNotBlank()) { add("--psiphon-region"); add(profile.psiphonRegion) }
            if (profile.psiphonCdnIps.isNotBlank()) { add("--psiphon-cdn-ips"); add(profile.psiphonCdnIps) }
            if (profile.psiphonCdnSni.isNotBlank()) { add("--psiphon-cdn-sni"); add(profile.psiphonCdnSni) }
        }

        if (profile.teamName.isNotBlank()) {
            add("--team"); add(profile.teamName)
            if (profile.accessClientId.isNotBlank()) { add("--access-id"); add(profile.accessClientId) }
            if (profile.accessClientSecret.isNotBlank()) { add("--access-secret"); add(profile.accessClientSecret) }
            if (profile.accessToken.isNotBlank()) { add("--access-token"); add(profile.accessToken) }
            if (profile.gateway) add("--gateway")
        }

        add("--quick-reconnect")
    }
}
