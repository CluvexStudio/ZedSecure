package dev.cluvex.zedsecure.domain.config

import dev.cluvex.zedsecure.domain.model.AppSettings
import dev.cluvex.zedsecure.domain.model.RoutingMode
import dev.cluvex.zedsecure.domain.model.VpnBypassLan

fun AppSettings.toBuildOptions(
    geoAssetsAvailable: Boolean = false,

    geoCodes: GeoCodes? = null,

    ruleOutbounds: Map<String, ServerConfig> = emptyMap(),

    ruleGroups: Map<String, RuleGroup> = emptyMap(),

    carrier: Boolean = false,

    resolvedServerHosts: Map<String, List<String>> = emptyMap(),

    networkType: String? = null,
): XrayJsonBuilder.BuildOptions = XrayJsonBuilder.BuildOptions(
    singBox = singBoxTuning(),
    logLevel = logLevel.value,
    domainStrategy = domainStrategy.value,
    sniffing = sniffingEnabled,
    carrier = carrier,
    routeOnly = routeOnly,
    bypassLan = routingMode == RoutingMode.BypassLan || routingMode == RoutingMode.BypassLanAndIran,
    bypassIran = routingMode == RoutingMode.BypassIran || routingMode == RoutingMode.BypassLanAndIran,
    blockAds = blockAds,
    geoAssetsAvailable = geoAssetsAvailable,
    geoCodes = geoCodes,
    customProxyRules = customProxyRules.toRuleList(),
    customDirectRules = customDirectRules.toRuleList(),
    customBlockRules = customBlockRules.toRuleList() +
        if (ghost.adultContentProtectionEnabled) dev.cluvex.zedsecure.core.ghost.AdultBlocklist.rules +
            dev.cluvex.zedsecure.core.ghost.AdultBlocklist.userRules(ghost.adultExtraDomains) else emptyList(),

    rulesets = RoutingMigration.effectiveRulesets(this),
    networkType = networkType,
    ruleOutbounds = ruleOutbounds,
    ruleGroups = ruleGroups,
    ruleGroupTuning = autoSelectTuning(),
    remoteDns = if (ghost.adultContentProtectionEnabled) {
        "https://doh.cleanbrowsing.org/doh/family-filter/"
    } else dnsResolverProfiles.firstOrNull { it.id == activeDnsResolverId }?.remoteEndpoint ?: remoteDns,
    directDns = if (ghost.adultContentProtectionEnabled) "185.228.168.168" else
        dnsResolverProfiles.firstOrNull { it.id == activeDnsResolverId }?.directAddress ?: directDns,
    muxEnabled = muxEnabled,
    muxConcurrency = muxConcurrency,
    fragmentEnabled = fragmentEnabled,
    fragmentPackets = fragmentPackets.value,
    fragmentLength = fragmentLength,
    fragmentInterval = fragmentInterval,
    fragmentMaxSplit = fragmentMaxSplit,
    lan = lanShare(),
    appendHttpProxy = appendHttpProxy,
    dnsHosts = dnsHosts,
    preferIpv6 = preferIpv6,
    muxXudpConcurrency = muxXudpConcurrency,
    muxXudpQuic = muxXudpQuic.value,
    localDns = localDnsEnabled,
    fakeDns = localDnsEnabled && fakeDnsEnabled,
    outboundDomainResolve = outboundDomainResolve.value,
    resolvedServerHosts = resolvedServerHosts,
    useSystemCa = useSystemCa,
)

val AppSettings.effectiveLanSocksPort: Int get() = LocalPorts.lanSocksPort(socksPort)

val AppSettings.effectiveLocalProxyPort: Int
    get() = LocalPorts.localProxyPort(localProxyPort).takeIf { it != effectiveLanSocksPort && it + 1 != effectiveLanSocksPort }
        ?: LocalPorts.XRAY_SOCKS

fun AppSettings.engineRemoteDns(): String =
    dnsResolverProfiles.firstOrNull { it.id == activeDnsResolverId }?.remoteEndpoint
        ?: remoteDnsPrimary.trim().takeIf { remoteDnsMode == "custom" && it.isNotEmpty() }
        ?: XrayJsonBuilder.ENGINE_REMOTE_DNS

private fun AppSettings.lanShare(): XrayJsonBuilder.LanShare? =
    if (!proxySharing) null
    else XrayJsonBuilder.LanShare(
        port = effectiveLanSocksPort,
        username = socksUsername.trim(),
        password = socksPassword,
        udp = socksEnableUdp,
        auth = lanShareAuth,
    )

private fun String.toRuleList(): List<String> =
    split('\n', ',')
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }

fun AppSettings.autoSelectTuning(): AutoSelectTuning = AutoSelectTuning(
    probeUrl = delayTestUrl,
    activeIntervalSec = autoSelectCheckSeconds,
    sweepIntervalMin = autoSelectSweepMinutes,
    switchMarginPercent = autoSelectSwitchMargin,
    retry = autoSelectRetry,
    excludedCountries = autoSelectExcludedCountries.toSet(),
)

fun AppSettings.singBoxTuning(): SingBoxTuning = SingBoxTuning(
    muxEnabled = singBoxMuxEnabled,
    muxProtocol = singBoxMuxProtocol.value,
    muxMaxConnections = singBoxMuxMaxConnections,
    muxPadding = singBoxMuxPadding,
    brutalEnabled = singBoxBrutalEnabled,
    brutalUpMbps = singBoxBrutalUpMbps,
    brutalDownMbps = singBoxBrutalDownMbps,
    tlsFragment = singBoxTlsFragment,
    tlsRecordFragment = singBoxTlsRecordFragment,
    utlsFingerprint = singBoxUtlsFingerprint,
    udpOverTcp = singBoxUdpOverTcp,
)

fun AppSettings.singBoxDeviceOptions(): SingBoxConfigs.DeviceOptions = SingBoxConfigs.DeviceOptions(
    stack = singBoxTunStack.value,
    strictRoute = singBoxStrictRoute,
    endpointIndependentNat = singBoxEndpointIndependentNat,
    mtu = vpnMtu,
    storeCache = singBoxStoreCache,
    ntpServer = singBoxNtpServer.trim().takeIf { singBoxNtpEnabled } ?: "",
)
