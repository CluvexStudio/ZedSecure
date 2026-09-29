package dev.cluvex.zedsecure.domain.config

import dev.cluvex.zedsecure.domain.model.AppSettings
import dev.cluvex.zedsecure.domain.model.RoutingMode
import dev.cluvex.zedsecure.domain.model.VpnBypassLan

fun AppSettings.toBuildOptions(
    geoAssetsAvailable: Boolean = false,

    ruleOutbounds: Map<String, ServerConfig> = emptyMap(),

    carrier: Boolean = false,

    resolvedServerHosts: Map<String, List<String>> = emptyMap(),
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
    customProxyRules = customProxyRules.toRuleList(),
    customDirectRules = customDirectRules.toRuleList(),
    customBlockRules = customBlockRules.toRuleList(),

    rulesets = RoutingMigration.effectiveRulesets(this),
    ruleOutbounds = ruleOutbounds,
    remoteDns = remoteDns,
    directDns = directDns,
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
)

val AppSettings.effectiveLanSocksPort: Int get() = LocalPorts.lanSocksPort(socksPort)

private fun AppSettings.lanShare(): XrayJsonBuilder.LanShare? =
    if (!proxySharing) null
    else XrayJsonBuilder.LanShare(
        port = effectiveLanSocksPort,
        username = socksUsername.trim(),
        password = socksPassword,
        udp = socksEnableUdp,
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
