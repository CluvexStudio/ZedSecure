package dev.cluvex.zedsecure.core

import android.content.Context
import dev.cluvex.zedsecure.R
import dev.cluvex.zedsecure.ZedSecureApp
import dev.cluvex.zedsecure.data.assets.GeoAssetsRepository
import dev.cluvex.zedsecure.domain.config.ConfigParser
import dev.cluvex.zedsecure.domain.config.Ikev2Profile
import dev.cluvex.zedsecure.domain.config.LocalPorts
import dev.cluvex.zedsecure.domain.config.LocalProxy
import dev.cluvex.zedsecure.domain.config.ProfileSource
import dev.cluvex.zedsecure.domain.config.RoutingMigration
import dev.cluvex.zedsecure.domain.config.ServerConfig
import dev.cluvex.zedsecure.domain.config.UnsupportedTransportException
import dev.cluvex.zedsecure.domain.config.VpnProfile
import dev.cluvex.zedsecure.domain.config.XrayJsonBuilder
import dev.cluvex.zedsecure.domain.config.autoSelectTuning
import dev.cluvex.zedsecure.domain.config.singBoxDeviceOptions
import dev.cluvex.zedsecure.domain.config.toBuildOptions
import dev.cluvex.zedsecure.domain.model.AppSettings
import dev.cluvex.zedsecure.domain.model.OutboundDomainResolve
import dev.cluvex.zedsecure.domain.model.RunMode
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class StartPlanner(context: Context) {
    private val appContext = context.applicationContext
    private val container get() = (appContext as ZedSecureApp).container
    private val configRepository get() = container.configRepository
    private val settingsRepository get() = container.settingsRepository

    sealed interface Plan {
        data class Start(
            val configJson: String,
            val remark: String,
            val kind: String,
            val socksPort: Int,
            val proxyOnly: Boolean,
        ) : Plan

        data class Ikev2(val remark: String, val profile: Ikev2Profile) : Plan

        data class Failure(val message: String) : Plan
    }

    fun planActive(allowProxyOnly: Boolean): Plan = plan(configRepository.activeProfile(), allowProxyOnly)

    fun plan(profile: VpnProfile?, allowProxyOnly: Boolean = true): Plan {
        AutoSelect.clearPrepared()
        if (profile == null) return Plan.Failure(appContext.getString(R.string.select_config_first))
        profile.ikev2Settings()?.let { return Plan.Ikev2(profile.name, it) }
        val kind = engineKindOf(profile)
        val configJson = try {
            if (kind == VpnManager.KIND_CROSS_CHAIN) buildCrossChainWrapper(profile)
            else engineConfigOf(profile, resolveServers = kind == VpnManager.KIND_XRAY)
        } catch (e: Exception) {
            return Plan.Failure(messageFor(e))
        }
        val settings = settingsRepository.settings.value

        val proxyOnly = allowProxyOnly && settings.runMode == RunMode.ProxyOnly && kind == VpnManager.KIND_XRAY
        return Plan.Start(
            configJson = configJson,
            remark = if (profile.isAutoSelect) autoSelectRemark(profile) else profile.name,
            kind = kind,
            socksPort = LocalProxy.SOCKS_PORT,
            proxyOnly = proxyOnly,
        )
    }

    private fun autoSelectRemark(profile: VpnProfile): String {
        val group = when (profile.autoSelectSettings()?.subscriptionId) {
            null -> appContext.getString(R.string.auto_group_all_short)
            "" -> appContext.getString(R.string.auto_group_manual_short)
            else -> profile.name
        }
        return appContext.getString(R.string.auto_notif_remark, group)
    }

    private fun messageFor(e: Exception): String = when (e) {
        is UnsupportedTransportException -> appContext.getString(R.string.transport_unsupported, e.transport)

        is dev.cluvex.zedsecure.crypto.ZsxLegacyException -> appContext.getString(R.string.zsx_legacy)
        is dev.cluvex.zedsecure.crypto.ZsxExpiredException -> appContext.getString(R.string.zsx_expired)

        is dev.cluvex.zedsecure.data.config.ProxyChainMemberMissingException ->
            appContext.getString(R.string.chain_member_missing, e.missing.joinToString(", "))

        is dev.cluvex.zedsecure.data.config.CrossChainUnsupportedException ->
            if (e.reason == dev.cluvex.zedsecure.data.config.CrossChainUnsupportedException.CARRIER) {
                appContext.getString(R.string.crosschain_bad_carrier, e.engine)
            } else {
                appContext.getString(R.string.crosschain_bad_dialer, e.engine)
            }

        is dev.cluvex.zedsecure.data.config.CrossChainUdpUnsupportedException ->
            appContext.getString(R.string.crosschain_udp_unsupported, e.protocol, e.carrier)
        is dev.cluvex.zedsecure.domain.config.UdpHopUnsupportedException ->
            appContext.getString(R.string.chain_udp_hop_unsupported, e.member, e.previous)
        is dev.cluvex.zedsecure.domain.config.ServerlessHopException -> appContext.getString(
            when (e.reason) {
                dev.cluvex.zedsecure.domain.config.ServerlessHopException.Reason.NotFirst -> R.string.chain_serverless_not_first
                dev.cluvex.zedsecure.domain.config.ServerlessHopException.Reason.NoServer -> R.string.chain_serverless_no_server
                dev.cluvex.zedsecure.domain.config.ServerlessHopException.Reason.NoDirectOutbound -> R.string.chain_serverless_no_direct
            },
        )
        is dev.cluvex.zedsecure.domain.config.AutoSelectNoMembersException ->
            appContext.getString(R.string.auto_no_members)
        else -> appContext.getString(R.string.config_invalid)
    }

    fun engineKindOf(profile: VpnProfile): String = when {
        profile.isCrossChain -> VpnManager.KIND_CROSS_CHAIN
        profile.psiphonSettings() != null -> VpnManager.KIND_PSIPHON
        profile.dnsTunnelSettings() != null -> VpnManager.KIND_DNS_TUNNEL
        profile.masterDnsSettings() != null -> VpnManager.KIND_MASTERDNS
        profile.openConnectSettings() != null -> VpnManager.KIND_OPENCONNECT
        profile.ikev2Settings() != null -> VpnManager.KIND_IKEV2
        profile.isTor -> VpnManager.KIND_TOR
        profile.sshSettings() != null -> VpnManager.KIND_SSH
        profile.sniSpoofSettings() != null -> VpnManager.KIND_SNISPOOF
        profile.isSingBoxConfig -> VpnManager.KIND_SINGBOX
        else -> VpnManager.KIND_XRAY
    }

    private fun engineConfigOf(
        profile: VpnProfile,
        carrier: Boolean = false,
        resolveServers: Boolean = false,
    ): String {
        profile.sniSpoofSettings()?.let { sniSpoof ->
            val geoReady = GeoAssetsRepository(appContext).allPresent()
            val xray = profile.toXrayConfigJson(
                settingsRepository.settings.value.buildOptions(geoReady, carrier),
            )
            return org.json.JSONObject().apply {
                put("xray", org.json.JSONObject(xray))
                put("sni", org.json.JSONObject().apply {
                    put("fakeSni", sniSpoof.fakeSni)
                    put("cleanIp", sniSpoof.cleanIp)
                })
            }.toString()
        }
        if (profile.isTor) return "tor"
        (profile.source as? dev.cluvex.zedsecure.domain.config.ProfileSource.SingBoxConfig)?.let { src ->

            val s = settingsRepository.settings.value
            return dev.cluvex.zedsecure.domain.config.SingBoxConfigs.prepareForDevice(
                src.json,
                ipv6 = s.enableIpv6,
                device = s.singBoxDeviceOptions(),
            ).json
        }
        profile.sshSettings()?.let {
            return kotlinx.serialization.json.Json.encodeToString(
                dev.cluvex.zedsecure.domain.config.SshProfile.serializer(), it,
            )
        }
        profile.psiphonSettings()?.let { psiphon ->
            val dataDir = java.io.File(appContext.filesDir, "psiphon").apply { mkdirs() }
            return dev.cluvex.zedsecure.domain.config.PsiphonConfigBuilder.build(
                profile = psiphon,
                dataDirPath = dataDir.absolutePath,
                socksPort = LocalPorts.PSIPHON_SOCKS,
                httpPort = LocalPorts.PSIPHON_HTTP,
            )
        }
        profile.masterDnsSettings()?.let {
            return kotlinx.serialization.json.Json.encodeToString(
                dev.cluvex.zedsecure.domain.config.MasterDnsProfile.serializer(), it,
            )
        }
        profile.openConnectSettings()?.let {
            return kotlinx.serialization.json.Json.encodeToString(
                dev.cluvex.zedsecure.domain.config.OpenConnectProfile.serializer(), it,
            )
        }
        profile.ikev2Settings()?.let {
            return kotlinx.serialization.json.Json.encodeToString(
                dev.cluvex.zedsecure.domain.config.Ikev2Profile.serializer(), it,
            )
        }
        profile.dnsTunnelSettings()?.let { dnsTunnel ->
            val s = settingsRepository.settings.value
            var effective = dnsTunnel

            if (s.dnsGlobalResolverEnabled && s.dnsGlobalResolvers.isNotBlank()) {
                effective = effective.copy(resolvers = s.dnsGlobalResolvers)
            }

            val workers = when (s.dnsWorkerMode) {
                "two" -> 2; "three" -> 3; "five" -> 5; else -> 0
            }
            if (workers > 0) {
                effective = effective.copy(
                    resolverMode = dev.cluvex.zedsecure.domain.config.DnsTunnelProfile.MODE_ROUND_ROBIN,
                    rrSpreadCount = workers,
                )
            }
            return kotlinx.serialization.json.Json.encodeToString(
                dev.cluvex.zedsecure.domain.config.DnsTunnelProfile.serializer(), effective,
            )
        }
        val geoReady = GeoAssetsRepository(appContext).allPresent()
        val settings = settingsRepository.settings.value
        if (profile.isAutoSelect) {
            val build = configRepository.buildAutoSelectConfig(
                profile,
                settings.buildOptions(geoReady, carrier),
                settings.autoSelectTuning(),
            )
            AutoSelect.prepare(profile.id, build.memberProfiles)
            return build.json
        }
        val options = settings.buildOptions(geoReady, carrier, if (resolveServers) profile else null)

        return if (profile.proxyChainSettings() != null) {
            configRepository.buildChainConfig(profile, options)
        } else {
            profile.toXrayConfigJson(options)
        }
    }

    private fun buildCrossChainWrapper(profile: VpnProfile): String {
        val (inner, outer) = configRepository.crossChainMembers(profile)

        if (inner.isTor &&
            !dev.cluvex.zedsecure.core.tor.TorConfigBuilder.supportsUpstreamProxy(
                settingsRepository.settings.value,
            )
        ) {
            throw dev.cluvex.zedsecure.data.config.CrossChainUnsupportedException(
                dev.cluvex.zedsecure.data.config.CrossChainUnsupportedException.DIALER, inner.name,
            )
        }
        return org.json.JSONObject().apply {
            put("innerKind", engineKindOf(inner))
            put("inner", engineConfigOf(inner))
            put("outerKind", engineKindOf(outer))

            put("outer", engineConfigOf(outer, carrier = true))
        }.toString()
    }

    private fun AppSettings.buildOptions(
        geoReady: Boolean,
        carrier: Boolean = false,
        resolveFor: VpnProfile? = null,
    ): XrayJsonBuilder.BuildOptions {
        val ruleOutbounds = configRepository.resolveRuleOutbounds(RoutingMigration.effectiveRulesets(this))
        val hosts = if (resolveFor != null && outboundDomainResolve != OutboundDomainResolve.DoNotResolve) {
            resolveServerHosts(physicalServersOf(resolveFor) + ruleOutbounds.values, preferIpv6)
        } else emptyMap()
        return toBuildOptions(
            geoAssetsAvailable = geoReady,
            carrier = carrier,
            ruleOutbounds = ruleOutbounds,
            resolvedServerHosts = hosts,
        )
    }

    private fun physicalServersOf(profile: VpnProfile): List<ServerConfig> {
        val link = when (val src = profile.source) {
            is ProfileSource.Link -> src.link
            is ProfileSource.ProxyChain -> src.memberIds.firstOrNull()
                ?.let { configRepository.profile(it) }
                ?.takeIf { it.source is ProfileSource.Link }
                ?.rawPayload()
            else -> null
        } ?: return emptyList()
        return listOfNotNull(runCatching { ConfigParser.parse(link) }.getOrNull())
    }

    private fun resolveServerHosts(servers: Collection<ServerConfig>, preferIpv6: Boolean): Map<String, List<String>> {
        val hosts = servers.map { it.address.trim() }
            .filter { it.isNotEmpty() && !XrayJsonBuilder.isIpLiteral(it) }
            .distinct()
        if (hosts.isEmpty()) return emptyMap()
        val pool = Executors.newFixedThreadPool(hosts.size.coerceAtMost(4))
        return try {
            val pending = hosts.associateWith { host ->
                pool.submit(Callable { InetAddress.getAllByName(host).toList() })
            }
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(RESOLVE_BUDGET_MS)
            pending.mapNotNull { (host, future) ->
                val left = (deadline - System.nanoTime()).coerceAtLeast(0)
                val addresses = runCatching { future.get(left, TimeUnit.NANOSECONDS) }.getOrNull()
                    ?: return@mapNotNull null
                orderAddresses(addresses, preferIpv6).takeIf { it.isNotEmpty() }?.let { host to it }
            }.toMap()
        } finally {
            pool.shutdownNow()
        }
    }

    private fun orderAddresses(addresses: List<InetAddress>, preferIpv6: Boolean): List<String> {
        val v4 = addresses.filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress }
        if (!preferIpv6) return v4.distinct()
        val v6 = addresses.filterIsInstance<Inet6Address>().mapNotNull { it.hostAddress?.substringBefore('%') }
        return (v6 + v4).distinct()
    }

    private companion object {
        const val RESOLVE_BUDGET_MS = 2_500L
    }
}
