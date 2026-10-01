@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package dev.cluvex.zedsecure.domain.config

import dev.cluvex.zedsecure.crypto.ZsxCrypto
import kotlin.io.encoding.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface ProfileSource {
    @Serializable
    @SerialName("link")
    data class Link(val link: String) : ProfileSource

    @Serializable
    @SerialName("json")
    data class RawJson(val json: String) : ProfileSource

    @Serializable
    @SerialName("sealed")
    data class Sealed(val zsxBase64: String) : ProfileSource

    @Serializable
    @SerialName("psiphon")
    data class Psiphon(val settings: PsiphonProfile) : ProfileSource

    @Serializable
    @SerialName("dns_tunnel")
    data class DnsTunnel(val settings: DnsTunnelProfile) : ProfileSource

    @Serializable
    @SerialName("tor")
    data object Tor : ProfileSource

    @Serializable
    @SerialName("ssh")
    data class Ssh(val settings: SshProfile) : ProfileSource

    @Serializable
    @SerialName("sni_spoof")
    data class SniSpoof(val settings: SniSpoofProfile) : ProfileSource

    @Serializable
    @SerialName("master_dns")
    data class MasterDns(val settings: MasterDnsProfile) : ProfileSource

    @Serializable
    @SerialName("openconnect")
    data class OpenConnect(val settings: OpenConnectProfile) : ProfileSource

    @Serializable
    @SerialName("ikev2")
    data class Ikev2(val settings: Ikev2Profile) : ProfileSource

    @Serializable
    @SerialName("proxy_chain")
    data class ProxyChain(val memberIds: List<String>) : ProfileSource

    @Serializable
    @SerialName("cross_chain")
    data class CrossChain(val innerId: String, val outerId: String) : ProfileSource

    @Serializable
    @SerialName("auto_select")
    data class AutoSelect(val subscriptionId: String? = null) : ProfileSource

    @Serializable
    @SerialName("sing_box")
    data class SingBox(val json: String, val carrier: String? = null, val link: String? = null) : ProfileSource

    @Serializable
    @SerialName("sing_box_config")
    data class SingBoxConfig(val json: String) : ProfileSource
}

@Serializable
data class VpnProfile(
    val id: String,
    val name: String,
    val protocol: String,
    val address: String,
    val port: Int,
    val transportLabel: String,
    val source: ProfileSource,
    val addedAt: Long,
    val isLocked: Boolean = false,

    val subscriptionId: String = "",
    val lastPingMs: Int? = null,
    val countryCode: String? = null,

    val bytesDown: Long = 0,
    val bytesUp: Long = 0,

    val note: String? = null,
) {
    val isCustom: Boolean get() = source is ProfileSource.RawJson

    val isServerless: Boolean by lazy { (source as? ProfileSource.RawJson)?.json?.let(CustomConfig::isServerless) == true }

    val isPsiphon: Boolean get() = source is ProfileSource.Psiphon

    val isDnsTunnel: Boolean get() = source is ProfileSource.DnsTunnel

    val isMasterDns: Boolean get() = source is ProfileSource.MasterDns

    val isTor: Boolean get() = source is ProfileSource.Tor

    val isSsh: Boolean get() = source is ProfileSource.Ssh

    val isSniSpoof: Boolean get() = source is ProfileSource.SniSpoof

    val isOpenConnect: Boolean get() = source is ProfileSource.OpenConnect

    val isIkev2: Boolean get() = source is ProfileSource.Ikev2

    val isProxyChain: Boolean get() = source is ProfileSource.ProxyChain

    val isAutoSelect: Boolean get() = source is ProfileSource.AutoSelect

    val isSingBox: Boolean get() = source is ProfileSource.SingBox

    val isSingBoxConfig: Boolean get() = source is ProfileSource.SingBoxConfig

    fun autoSelectSettings(): ProfileSource.AutoSelect? = source as? ProfileSource.AutoSelect

    private val chainProtocol: Protocol?
        get() = (source as? ProfileSource.Link)
            ?.let { runCatching { ConfigParser.parse(it.link) }.getOrNull()?.protocol }

    val needsUdpHop: Boolean
        get() = chainProtocol?.let { it.isWireguardFamily || it == Protocol.HYSTERIA } == true

    val carriesUdpHop: Boolean get() = chainProtocol?.carriesUdp != false

    fun sshSettings(): SshProfile? = (source as? ProfileSource.Ssh)?.settings

    fun sniSpoofSettings(): SniSpoofProfile? = (source as? ProfileSource.SniSpoof)?.settings

    val isManagedTunnel: Boolean
        get() = isPsiphon || isDnsTunnel || isMasterDns || isTor || isSsh || isSniSpoof ||
            isOpenConnect || isIkev2 || isCrossChain || isSingBoxConfig

    val isDnsBasedTunnel: Boolean get() = isDnsTunnel || isMasterDns

    fun psiphonSettings(): PsiphonProfile? = (source as? ProfileSource.Psiphon)?.settings

    fun dnsTunnelSettings(): DnsTunnelProfile? = (source as? ProfileSource.DnsTunnel)?.settings

    fun masterDnsSettings(): MasterDnsProfile? = (source as? ProfileSource.MasterDns)?.settings

    fun openConnectSettings(): OpenConnectProfile? = (source as? ProfileSource.OpenConnect)?.settings

    fun ikev2Settings(): Ikev2Profile? = (source as? ProfileSource.Ikev2)?.settings

    fun proxyChainSettings(): List<String>? = (source as? ProfileSource.ProxyChain)?.memberIds

    val isCrossChain: Boolean get() = source is ProfileSource.CrossChain

    val canCarryChain: Boolean
        get() = when (source) {
            is ProfileSource.Link, is ProfileSource.RawJson, is ProfileSource.Sealed,
            is ProfileSource.ProxyChain, is ProfileSource.SingBox,
            is ProfileSource.Psiphon, is ProfileSource.Tor -> true
            else -> false
        }

    val canDialThroughProxy: Boolean
        get() = when (source) {
            is ProfileSource.Tor, is ProfileSource.Psiphon -> true
            is ProfileSource.Link, is ProfileSource.RawJson, is ProfileSource.Sealed,
            is ProfileSource.ProxyChain, is ProfileSource.SingBox -> true
            else -> false
        }

    val carrierRelaysUdp: Boolean
        get() = when (source) {
            is ProfileSource.Link, is ProfileSource.RawJson, is ProfileSource.Sealed,
            is ProfileSource.ProxyChain, is ProfileSource.SingBox, is ProfileSource.Psiphon -> true
            else -> false
        }

    fun crossChainSettings(): Pair<String, String>? =
        (source as? ProfileSource.CrossChain)?.let { it.innerId to it.outerId }

    val isEditable: Boolean get() = !isLocked

    fun rawPayload(): String? = when (val src = source) {
        is ProfileSource.Link -> src.link
        is ProfileSource.RawJson -> src.json

        is ProfileSource.Sealed -> null

        is ProfileSource.Psiphon -> ZedLink.build(name, src)
        is ProfileSource.DnsTunnel -> ZedLink.build(name, src)
        is ProfileSource.Tor -> ZedLink.build(name, src)
        is ProfileSource.Ssh -> ZedLink.build(name, src)

        is ProfileSource.SniSpoof -> SniSpoofLink.build(src.settings, name)
        is ProfileSource.MasterDns -> ZedLink.build(name, src)
        is ProfileSource.OpenConnect -> ZedLink.build(name, src)
        is ProfileSource.Ikev2 -> ZedLink.build(name, src)

        is ProfileSource.ProxyChain -> null
        is ProfileSource.CrossChain -> null

        is ProfileSource.AutoSelect -> null

        is ProfileSource.SingBox -> src.link ?: src.json
        is ProfileSource.SingBoxConfig -> src.json
    }

    fun toXrayConfigJson(
        options: XrayJsonBuilder.BuildOptions = XrayJsonBuilder.BuildOptions(),
        forSpeedtest: Boolean = false,
    ): String = when (val src = source) {
        is ProfileSource.Link -> if (SingBoxLinks.handles(src.link)) {
            val parsed = SingBoxLinks.parse(src.link)
            XrayJsonBuilder.buildSingBox(parsed.fragment, parsed.name, options = options, forSpeedtest = forSpeedtest)
        } else {
            XrayJsonBuilder.build(
                ConfigParser.parse(src.link),
                options = options,
                forSpeedtest = forSpeedtest,
            )
        }
        is ProfileSource.RawJson -> XrayJsonBuilder.normalizeRawJson(
            src.json,
            forSpeedtest = forSpeedtest,
            geoAssetsAvailable = options.geoAssetsAvailable,
        )
        is ProfileSource.Sealed ->
            configJsonFromPayload(
                ZsxCrypto.open(Base64.decode(src.zsxBase64), password = null),
                options,
                forSpeedtest,
            )

        is ProfileSource.Psiphon -> throw IllegalStateException("Psiphon profiles do not build Xray config")
        is ProfileSource.DnsTunnel -> throw IllegalStateException("DNS-tunnel profiles do not build Xray config")
        is ProfileSource.MasterDns -> throw IllegalStateException("MasterDNS profiles do not build Xray config")
        is ProfileSource.OpenConnect -> throw IllegalStateException("OpenConnect profiles do not build Xray config")
        is ProfileSource.Ikev2 -> throw IllegalStateException("IKEv2 profiles do not build Xray config")
        is ProfileSource.Tor -> throw IllegalStateException("Tor profiles do not build Xray config")
        is ProfileSource.Ssh -> throw IllegalStateException("SSH profiles do not build Xray config")

        is ProfileSource.ProxyChain ->
            throw IllegalStateException("Proxy-chain profiles are built via ConfigRepository.buildChainConfig")

        is ProfileSource.CrossChain ->
            throw IllegalStateException("Cross-chain profiles are built via ConfigRepository.buildCrossChainConfig")
        is ProfileSource.AutoSelect ->
            throw IllegalStateException("Auto-select profiles are built via ConfigRepository.buildAutoSelectConfig")
        is ProfileSource.SingBox -> XrayJsonBuilder.buildSingBox(
            fragment = src.json,
            carrier = src.carrier,
            options = options,
            forSpeedtest = forSpeedtest,
        )

        is ProfileSource.SingBoxConfig -> if (forSpeedtest) {
            val server = SingBoxJson.probeServer(src.json)
                ?: throw IllegalStateException("The sing-box config has no server to measure")
            XrayJsonBuilder.buildSingBox(server.fragment, server.tag, options, forSpeedtest = true)
        } else {
            throw IllegalStateException("sing-box configs run on the sing-box engine")
        }

        is ProfileSource.SniSpoof -> if (CustomConfig.looksLikeCustomJson(src.settings.link)) {
            XrayJsonBuilder.normalizeRawJson(
                src.settings.link,
                forSpeedtest = forSpeedtest,
                geoAssetsAvailable = options.geoAssetsAvailable,
            )
        } else {
            XrayJsonBuilder.build(
                ConfigParser.parse(src.settings.link),
                options = options,
                forSpeedtest = forSpeedtest,
            )
        }
    }

    companion object {
        fun fromPsiphon(
            settings: PsiphonProfile,
            id: String,
            addedAt: Long,
            name: String,
        ): VpnProfile = VpnProfile(
            id = id,
            name = name.ifBlank { "Psiphon" },
            protocol = "PSIPHON",
            address = "psiphon",
            port = 0,
            transportLabel = "Psiphon",
            source = ProfileSource.Psiphon(settings),
            addedAt = addedAt,
            countryCode = settings.country.takeIf { it.isNotBlank() }?.uppercase(),
        )

        fun fromDnsTunnel(
            settings: DnsTunnelProfile,
            id: String,
            addedAt: Long,
            name: String,
        ): VpnProfile = VpnProfile(
            id = id,
            name = name.ifBlank { if (settings.isVaydns) "VayDNS" else "DNSTT" },
            protocol = settings.engine.uppercase(),
            address = settings.domain,
            port = 0,
            transportLabel = if (settings.isVaydns) "VayDNS" else "DNSTT",
            source = ProfileSource.DnsTunnel(settings),
            addedAt = addedAt,
        )

        fun fromMasterDns(
            settings: MasterDnsProfile,
            id: String,
            addedAt: Long,
            name: String,
        ): VpnProfile = VpnProfile(
            id = id,
            name = name.ifBlank { "MasterDNS" },
            protocol = "MASTERDNS",
            address = settings.domains.split(',', '\n').firstOrNull()?.trim().orEmpty(),
            port = 0,
            transportLabel = "MasterDNS",
            source = ProfileSource.MasterDns(settings),
            addedAt = addedAt,
        )

        fun fromOpenConnect(
            settings: OpenConnectProfile,
            id: String,
            addedAt: Long,
            name: String,
        ): VpnProfile = VpnProfile(
            id = id,
            name = name.ifBlank { "OpenConnect ${settings.server}" },
            protocol = "OPENCONNECT",
            address = settings.server,

            port = settings.serverPort(),
            transportLabel = settings.protocol.uppercase(),
            source = ProfileSource.OpenConnect(settings),
            addedAt = addedAt,
        )

        fun fromIkev2(
            settings: Ikev2Profile,
            id: String,
            addedAt: Long,
            name: String,
        ): VpnProfile = VpnProfile(
            id = id,
            name = name.ifBlank { "IKEv2 ${settings.server}" },
            protocol = "IKEV2",
            address = settings.server,
            port = 0,
            transportLabel = "IKEv2",
            source = ProfileSource.Ikev2(settings),
            addedAt = addedAt,
        )

        fun fromAutoSelect(subscriptionId: String?, groupName: String, members: Int): VpnProfile = VpnProfile(
            id = AutoSelectIds.of(subscriptionId),
            name = groupName,
            protocol = "AUTO",
            address = "auto",
            port = 0,
            transportLabel = "Auto-select · $members",
            source = ProfileSource.AutoSelect(subscriptionId),
            addedAt = 0,
            subscriptionId = subscriptionId.orEmpty(),
        )

        fun fromSingBox(
            server: SingBoxJson.Server,
            id: String,
            addedAt: Long,
            subscriptionId: String = "",
            name: String? = null,
            link: String? = null,
        ): VpnProfile = VpnProfile(
            id = id,
            name = name?.takeIf { it.isNotBlank() } ?: server.tag,

            protocol = server.type.removeSuffix("-client").uppercase(),
            address = server.address ?: "-",
            port = server.port ?: 0,
            transportLabel = "${server.label} · sing-box",
            source = ProfileSource.SingBox(json = server.fragment, carrier = server.tag, link = link),
            addedAt = addedAt,
            subscriptionId = subscriptionId,
        )

        fun fromSingBoxConfig(
            json: String,
            id: String,
            addedAt: Long,
            name: String? = null,
            subscriptionId: String = "",
        ): VpnProfile {
            val probe = SingBoxJson.probeServer(json)
            return VpnProfile(
                id = id,
                name = name?.takeIf { it.isNotBlank() } ?: probe?.tag ?: "sing-box config",
                protocol = "SING-BOX",
                address = probe?.address ?: "-",
                port = probe?.port ?: 0,
                transportLabel = "sing-box config",
                source = ProfileSource.SingBoxConfig(json),
                addedAt = addedAt,
                subscriptionId = subscriptionId,
            )
        }

        fun fromTor(id: String, addedAt: Long, name: String): VpnProfile = VpnProfile(
            id = id,
            name = name.ifBlank { "Tor" },
            protocol = "TOR",
            address = "tor",
            port = 0,
            transportLabel = "Tor",
            source = ProfileSource.Tor,
            addedAt = addedAt,
        )

        fun fromProxyChain(
            memberIds: List<String>,
            id: String,
            addedAt: Long,
            name: String,
        ): VpnProfile = VpnProfile(
            id = id,
            name = name.ifBlank { "Proxy chain" },
            protocol = "CHAIN",
            address = "chain",
            port = 0,
            transportLabel = "Proxy chain · ${memberIds.size}",
            source = ProfileSource.ProxyChain(memberIds),
            addedAt = addedAt,
        )

        fun fromCrossChain(
            innerId: String,
            outerId: String,
            id: String,
            addedAt: Long,
            name: String,
            innerName: String = "",
            outerName: String = "",
        ): VpnProfile = VpnProfile(
            id = id,
            name = name.ifBlank {
                if (innerName.isNotBlank() && outerName.isNotBlank()) "$innerName via $outerName"
                else "Cross chain"
            },
            protocol = "CROSSCHAIN",
            address = "cross",
            port = 0,

            transportLabel = if (innerName.isNotBlank() && outerName.isNotBlank()) {
                "$innerName \u2192 $outerName"
            } else "Cross chain",
            source = ProfileSource.CrossChain(innerId = innerId, outerId = outerId),
            addedAt = addedAt,
        )

        fun fromSniSpoof(settings: SniSpoofProfile, id: String, addedAt: Long, name: String): VpnProfile {
            val custom = CustomConfig.looksLikeCustomJson(settings.link)
            val info = if (custom) CustomConfig.inspect(settings.link) else null
            val parsed =
                if (custom) null else runCatching { ConfigParser.parse(settings.link) }.getOrNull()
            return VpnProfile(
                id = id,
                name = name.ifBlank {
                    (info?.remarks ?: parsed?.remark)?.ifBlank { null } ?: "SNI spoof"
                },
                protocol = "SNI-SPOOF",

                address = info?.address ?: parsed?.address ?: "sni-spoof",
                port = info?.port ?: parsed?.port ?: 0,
                transportLabel = "SNI spoof",
                source = ProfileSource.SniSpoof(settings),
                addedAt = addedAt,
            )
        }

        fun fromSsh(settings: SshProfile, id: String, addedAt: Long, name: String): VpnProfile = VpnProfile(
            id = id,
            name = name.ifBlank { "SSH ${settings.host}" },
            protocol = "SSH",
            address = settings.host,
            port = settings.port,
            transportLabel = "SSH",
            source = ProfileSource.Ssh(settings),
            addedAt = addedAt,
        )

        internal fun configJsonFromPayload(
            payload: String,
            options: XrayJsonBuilder.BuildOptions = XrayJsonBuilder.BuildOptions(),
            forSpeedtest: Boolean = false,
        ): String {
            val trimmed = payload.trim()
            return if (CustomConfig.looksLikeCustomJson(trimmed)) {
                XrayJsonBuilder.normalizeRawJson(
                    trimmed,
                    forSpeedtest = forSpeedtest,
                    geoAssetsAvailable = options.geoAssetsAvailable,
                )
            } else {
                XrayJsonBuilder.build(
                    ConfigParser.parse(trimmed),
                    options = options,
                    forSpeedtest = forSpeedtest,
                )
            }
        }

        fun fromLink(
            link: String,
            id: String,
            addedAt: Long,
            locked: Boolean = false,
            subscriptionId: String = "",
        ): VpnProfile {
            val parsed = ConfigParser.parse(link)
            return VpnProfile(
                id = id,
                name = parsed.remark.ifBlank { "${parsed.address}:${parsed.port}" },
                protocol = parsed.protocol.name,
                address = parsed.address,
                port = parsed.port,
                transportLabel = parsed.transportLabel,
                source = ProfileSource.Link(link),
                addedAt = addedAt,
                isLocked = locked,
                subscriptionId = subscriptionId,

                countryCode = null,
            )
        }

        fun fromRawJson(
            rawJson: String,
            id: String,
            addedAt: Long,
            fallbackName: String? = null,
            subscriptionId: String = "",
        ): VpnProfile {
            val info = CustomConfig.inspect(rawJson)
            val name = info.remarks
                ?: fallbackName?.takeIf { it.isNotBlank() }
                ?: info.address?.let { a -> info.port?.let { p -> "$a:$p" } ?: a }
                ?: "Custom config"

            return VpnProfile(
                id = id,
                name = name,
                protocol = "CUSTOM",
                address = info.address ?: "-",
                port = info.port ?: 0,
                transportLabel = "Custom config",
                source = ProfileSource.RawJson(rawJson),
                addedAt = addedAt,
                subscriptionId = subscriptionId,
                countryCode = null,
            )
        }
    }
}
