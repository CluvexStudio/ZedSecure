package dev.cluvex.zedsecure.desktop.platform

import dev.cluvex.zedsecure.core.LogBus
import dev.cluvex.zedsecure.core.SocksTunBridge
import dev.cluvex.zedsecure.core.SshController
import dev.cluvex.zedsecure.core.VpnManager
import dev.cluvex.zedsecure.data.config.ConfigRepository
import dev.cluvex.zedsecure.desktop.core.DesktopDnsTunnel
import dev.cluvex.zedsecure.desktop.core.DesktopIkev2
import dev.cluvex.zedsecure.desktop.core.DesktopPsiphon
import dev.cluvex.zedsecure.desktop.core.DesktopTor
import dev.cluvex.zedsecure.desktop.core.DesktopTun
import dev.cluvex.zedsecure.desktop.core.EngineCgroup
import dev.cluvex.zedsecure.desktop.core.PhysicalInterface
import dev.cluvex.zedsecure.desktop.core.LocalPortPicker
import dev.cluvex.zedsecure.desktop.core.SystemProxy
import dev.cluvex.zedsecure.desktop.core.Os
import dev.cluvex.zedsecure.desktop.core.TunMode
import dev.cluvex.zedsecure.desktop.core.XrayCore
import dev.cluvex.zedsecure.domain.config.DnsTunnelProfile
import dev.cluvex.zedsecure.domain.config.Ikev2Profile
import dev.cluvex.zedsecure.domain.config.LocalPorts
import dev.cluvex.zedsecure.domain.config.LocalProxy
import dev.cluvex.zedsecure.domain.config.PsiphonProfile
import dev.cluvex.zedsecure.domain.config.SshProfile
import dev.cluvex.zedsecure.domain.config.autoSelectTuning
import dev.cluvex.zedsecure.domain.config.toBuildOptions
import dev.cluvex.zedsecure.domain.model.AppSettings
import dev.cluvex.zedsecure.domain.model.RunMode
import java.io.File
import java.net.InetAddress
import kotlin.concurrent.thread

object DesktopVpn {
    private const val METRICS_PORT = LocalPorts.DESKTOP_METRICS
    private const val SHIM_PORT = LocalPorts.SHIM
    private const val DNS_WARM_UP_MS = 20_000L
    private const val SSH_PORT = LocalPorts.SSH
    private const val DNS_PORT = LocalPorts.DNS_TUNNEL
    private const val CARRIER_PORT = LocalPorts.CHAIN_CARRIER
    private const val CARRIER_CONFIG = "xray-carrier.json"
    private const val CHAIN = "The chain"

    val work = File(System.getProperty("java.io.tmpdir"), "zedsecure").apply { mkdirs() }
    private var xray: XrayCore? = null
    private var tun: DesktopTun? = null
    private var stats: DesktopStats? = null
    private var singBoxStats: DesktopSingBoxStats? = null
    private var shim: SocksTunBridge? = null
    private var ssh: SshController? = null
    private var dnsTunnel: DesktopDnsTunnel? = null
    @Volatile private var tor: DesktopTor? = null
    @Volatile private var psiphon: DesktopPsiphon? = null
    @Volatile private var masterDns: dev.cluvex.zedsecure.desktop.core.DesktopMasterDns? = null
    @Volatile private var ikev2: DesktopIkev2? = null
    @Volatile private var carrier: XrayCore? = null
    @Volatile private var openConnect: dev.cluvex.zedsecure.desktop.core.DesktopOpenConnect? = null
    @Volatile private var outerPsiphon: DesktopPsiphon? = null
    @Volatile private var outerTor: DesktopTor? = null
    @Volatile private var outerSsh: SshController? = null
    @Volatile private var outerDns: DesktopDnsTunnel? = null
    @Volatile private var carrierBypass: List<String>? = null
    @Volatile private var engineCgroup: String? = null
    private var meter: DesktopMeter? = null
    private var usedSystemProxy = false

    @Volatile var runMode: RunMode = RunMode.SystemProxy

    @Volatile var settingsProvider: () -> AppSettings = { AppSettings() }

    @Volatile

    var chainConfigProvider: (dev.cluvex.zedsecure.domain.config.VpnProfile, dev.cluvex.zedsecure.domain.config.XrayJsonBuilder.BuildOptions) -> String? =
        { _, _ -> null }

    var ruleOutboundsProvider: (List<dev.cluvex.zedsecure.domain.model.RulesetItem>) -> Map<String, dev.cluvex.zedsecure.domain.config.ServerConfig> =
        { emptyMap() }

    var ruleGroupsProvider: (List<dev.cluvex.zedsecure.domain.model.RulesetItem>) -> Map<String, dev.cluvex.zedsecure.domain.config.RuleGroup> =
        { emptyMap() }

    private fun geoAssetsAvailable(): Boolean = dev.cluvex.zedsecure.desktop.core.DesktopGeoAssets.allPresent()

    private fun geoCodes(settings: AppSettings): dev.cluvex.zedsecure.domain.config.GeoCodes? =
        if (!geoAssetsAvailable()) null
        else dev.cluvex.zedsecure.data.assets.GeoCodeReader.readFor(
            dev.cluvex.zedsecure.desktop.core.DesktopGeoAssets.dir,
            dev.cluvex.zedsecure.domain.config.RoutingMigration.effectiveRulesets(settings),
        )

    fun toggle(config: ConfigRepository) {
        val state = VpnManager.status.value.state
        if (state.isActive || state.isTransitioning) { stop(); return }
        val profile = config.activeProfile() ?: run { VpnManager.onError("Select a config first"); return }

        runMode = settingsProvider().runMode

        when {
            profile.sniSpoofSettings() != null ->
                VpnManager.onError("SNI spoofing needs a rooted Android device; it is not available on desktop yet")
            profile.ikev2Settings() != null -> startIkev2(profile.name, profile.ikev2Settings()!!)
            profile.openConnectSettings() != null -> startOpenConnect(profile.name, profile.openConnectSettings()!!)
            profile.psiphonSettings() != null -> startPsiphon(profile.name, profile.psiphonSettings()!!)
            profile.isTor -> startTor(profile.name)
            profile.dnsTunnelSettings() != null -> startDns(profile.name, profile.dnsTunnelSettings()!!)
            profile.masterDnsSettings() != null -> startMasterDns(profile.name, profile.masterDnsSettings()!!)
            profile.sshSettings() != null -> startSsh(profile.name, profile.sshSettings()!!)
            profile.isSingBoxConfig -> startSingBoxConfig(profile.name, profile)
            profile.isCrossChain -> startCrossChain(profile.name, profile, config)
            else -> startXray(profile.name, profile, config)
        }
    }

    private fun startDns(name: String, dnsProfile: DnsTunnelProfile) {
        VpnManager.onStarting(name)
        thread(name = "desktop-dns") {
            val dns = DesktopDnsTunnel(dnsProfile, work, listenPort = DNS_PORT)
            dnsTunnel = dns
            val port = dns.start()
            if (port < 0) { VpnManager.onError("DNS tunnel failed to start"); return@thread }

            val upstreamPort = if (dnsProfile.sshEnabled && dnsProfile.sshHost.isNotBlank()) {
                val sshProfile = SshProfile(
                    host = dnsProfile.sshHost, port = dnsProfile.sshPort, username = dnsProfile.sshUsername,
                    authType = dnsProfile.sshAuthType, password = dnsProfile.sshPassword,
                    privateKey = dnsProfile.sshPrivateKey, keyPassphrase = dnsProfile.sshKeyPassphrase,
                )
                val controller = SshController(
                    profile = sshProfile, cipher = "auto", compression = false, listenPort = SSH_PORT,
                    proxySocksHost = "127.0.0.1", proxySocksPort = port,
                    proxySocksUser = dnsProfile.socksUser, proxySocksPass = dnsProfile.socksPass,
                )
                ssh = controller
                val sshPort = controller.start()
                if (sshPort < 0) { dns.stop(); ssh = null; VpnManager.onError("SSH-over-DNS failed to connect"); return@thread }
                sshPort
            } else port

            val shimPort = localPort(SHIM_PORT, setOf(port, upstreamPort))
            val bridge = SocksTunBridge("127.0.0.1", shimPort, "127.0.0.1", upstreamPort, dnsHost = "8.8.8.8")
            shim = bridge
            if (!bridge.start()) { stop(); VpnManager.onError("Shim failed to start"); return@thread }

            val bypass = resolveHosts(dnsResolverHosts(dnsProfile) + listOfNotNull(dnsProfile.sshHost.takeIf { it.isNotBlank() }))
            if (!route(shimPort, bypass, udpOverTcp = true)) {
                stop(); VpnManager.onError("Could not route traffic (TUN + system proxy failed)"); return@thread
            }
            VpnManager.activeSocksPort = shimPort
            startShimStats(bridge)
            VpnManager.onConnected(name)
        }
    }

    private fun startMasterDns(name: String, profile: dev.cluvex.zedsecure.domain.config.MasterDnsProfile) {
        VpnManager.onStarting(name)
        thread(name = "desktop-masterdns") {
            val settings = settingsProvider()
            val effective = if (settings.dnsGlobalResolverEnabled && settings.dnsGlobalResolvers.isNotBlank()) {
                profile.copy(resolvers = settings.dnsGlobalResolvers)
            } else profile
            val engine = dev.cluvex.zedsecure.desktop.core.DesktopMasterDns(effective, work)
            masterDns = engine
            val port = runCatching { engine.start() }.getOrElse {
                LogBus.append("E/MasterDns ${it.message ?: it}")
                -1
            }
            if (masterDns !== engine) return@thread
            if (port < 0) {
                masterDns = null
                VpnManager.onError("MasterDNS could not connect: ${engine.lastError ?: "unknown error"}")
                return@thread
            }
            val shimPort = localPort(SHIM_PORT, setOf(port))
            val bridge = SocksTunBridge("127.0.0.1", shimPort, "127.0.0.1", port, dnsHost = "8.8.8.8")
            shim = bridge
            if (!bridge.start()) { stop(); VpnManager.onError("Shim failed to start"); return@thread }
            if (!route(shimPort, resolveHosts(engine.resolverHosts()), udpOverTcp = true)) {
                stop(); VpnManager.onError("Could not route traffic (TUN + system proxy failed)"); return@thread
            }
            VpnManager.activeSocksPort = shimPort
            startShimStats(bridge)
            VpnManager.onConnected(name)
        }
    }

    private fun startTor(name: String) {
        VpnManager.onStarting(name)
        thread(name = "desktop-tor") {
            val cgroup = engineCgroupForTun()
            val engine = DesktopTor(settingsProvider(), work, engineCgroup = cgroup, dnsPort = cgroup?.let { freeUdpPort() })
            tor = engine
            var reported = -1
            val started = engine.start(onProgress = { pct ->
                if (pct > reported) {
                    reported = pct
                    LogBus.append("I/Tor bootstrapped $pct%")
                }
            })
            if (tor !== engine) return@thread
            started.exceptionOrNull()?.let { e ->
                tor = null
                VpnManager.onError(e.message ?: "Tor failed to start")
                return@thread
            }
            if (!connectThrough(engine.socksPort, "Tor", localDns = engine.dnsPort)) {
                engine.stop()
                tor = null
                return@thread
            }
            VpnManager.onConnected(name)
        }
    }

    private fun startPsiphon(name: String, profile: PsiphonProfile) {
        VpnManager.onStarting(name)
        thread(name = "desktop-psiphon") {
            val socksPort = localPort(LocalPorts.PSIPHON_SOCKS)
            val httpPort = localPort(LocalPorts.PSIPHON_HTTP, setOf(socksPort))
            val engine = DesktopPsiphon(profile, work, socksPort, httpPort, engineCgroup = engineCgroupForTun())
            psiphon = engine
            val started = engine.start(onNotice = { notice ->
                if (notice.type == "ConnectedServerRegion") {
                    LogBus.append("I/Psiphon exit region ${notice.string("region").orEmpty()}")
                }
            })
            if (psiphon !== engine) return@thread
            started.exceptionOrNull()?.let { e ->
                psiphon = null
                VpnManager.onError(e.message ?: "Psiphon failed to start")
                return@thread
            }
            if (!connectThrough(engine.socksPort, "Psiphon")) {
                engine.stop()
                psiphon = null
                return@thread
            }
            VpnManager.onConnected(name)
        }
    }

    private fun startIkev2(name: String, profile: Ikev2Profile) {
        VpnManager.onStarting(name)
        thread(name = "desktop-ikev2") {
            val engine = DesktopIkev2(profile, work)
            ikev2 = engine
            val connected = engine.connect()
            if (ikev2 !== engine) {
                if (connected.isSuccess) engine.disconnect()
                return@thread
            }
            connected.exceptionOrNull()?.let { e ->
                ikev2 = null
                VpnManager.onError(e.message ?: "IKEv2 failed to connect")
                return@thread
            }
            LogBus.append("I/IKEv2 the system VPN '${DesktopIkev2.NAME}' carries all traffic")
            VpnManager.onConnected(name)
            meter = DesktopMeter(totals = { null }).also { it.start() }
            while (ikev2 === engine) {
                Thread.sleep(15_000)
                if (ikev2 === engine && !engine.isUp()) {
                    ikev2 = null
                    VpnManager.onError("IKEv2 disconnected")
                    break
                }
            }
        }
    }

    private fun startOpenConnect(name: String, profile: dev.cluvex.zedsecure.domain.config.OpenConnectProfile) {
        VpnManager.onStarting(name)
        thread(name = "desktop-openconnect") {
            val engine = dev.cluvex.zedsecure.desktop.core.DesktopOpenConnect(profile, work, AdminPassword::ask)
            openConnect = engine
            val connected = engine.connect()
            if (openConnect !== engine) {
                if (connected.isSuccess) engine.disconnect()
                return@thread
            }
            connected.exceptionOrNull()?.let { e ->
                openConnect = null
                VpnManager.onError(e.message ?: "OpenConnect failed to connect")
                return@thread
            }
            LogBus.append("I/OpenConnect the system VPN carries all traffic")
            VpnManager.onConnected(name)
            meter = DesktopMeter(totals = { null }).also { it.start() }
            while (openConnect === engine) {
                Thread.sleep(5_000)
                if (openConnect === engine && !engine.isUp) {
                    openConnect = null
                    engine.disconnect()
                    VpnManager.onError("OpenConnect disconnected")
                    break
                }
            }
        }
    }

    private fun connectThrough(upstreamPort: Int, engine: String, bypass: List<String>? = null, localDns: Int? = null): Boolean {
        val tunReady = runMode == RunMode.Vpn && TunMode.supported()
        val excluded = engineCgroup.takeIf { bypass == null && tunReady }
        if (excluded != null) return bridgeThroughTun(upstreamPort, excluded, localDns)
        if (bypass != null && tunReady) return bridgeThroughTun(upstreamPort, null, localDns, bypass)
        val socksPort = localPort(LocalProxy.SOCKS_PORT, setOf(upstreamPort))
        val metricsPort = localPort(METRICS_PORT, setOf(socksPort, upstreamPort))
        val front = DesktopXray.frontConfig(socksPort, upstreamPort, blockTunNoise = runMode == RunMode.Vpn && bypass != null)
        val json = DesktopXray.augmentWithMetrics(front, metricsPort)
        val core = XrayCore(work)
        if (!core.start(json)) {
            VpnManager.onError("Core failed to start")
            return false
        }
        xray = core
        if (!route(socksPort, bypass.orEmpty(), udpOverTcp = true, tunCarries = engine.takeIf { bypass == null })) {
            core.stop()
            xray = null
            VpnManager.onError("Could not route traffic (system proxy failed)")
            return false
        }
        VpnManager.activeSocksPort = socksPort
        DesktopStats(metricsPort).also { stats = it; it.start() }
        return true
    }

    private fun bridgeThroughTun(upstreamPort: Int, cgroup: String?, localDns: Int?, bypass: List<String> = emptyList()): Boolean {
        val shimPort = localPort(SHIM_PORT, setOf(upstreamPort))
        val bridge = SocksTunBridge(
            "127.0.0.1", shimPort, "127.0.0.1", upstreamPort, dnsHost = "8.8.8.8",
            localResolver = localDns?.let { java.net.InetSocketAddress("127.0.0.1", it) },
        )
        shim = bridge
        if (!bridge.start()) {
            shim = null
            VpnManager.onError("Shim failed to start")
            return false
        }
        if (!bridge.warmUp(DNS_WARM_UP_MS)) LogBus.append("W/Desktop DNS through the engine is slow to answer; the first lookups may fail")
        if (!route(shimPort, bypass, udpOverTcp = true, excludeCgroup = cgroup)) {
            bridge.stop()
            shim = null
            VpnManager.onError("Could not route traffic (TUN + system proxy failed)")
            return false
        }
        VpnManager.activeSocksPort = shimPort
        startShimStats(bridge)
        return true
    }

    private fun startCrossChain(
        name: String,
        profile: dev.cluvex.zedsecure.domain.config.VpnProfile,
        config: ConfigRepository,
    ) {
        val (inner, outer) = runCatching { config.crossChainMembers(profile) }.getOrElse {
            VpnManager.onError(it.message ?: "This chain cannot be built"); return
        }
        if (inner.isTor && !dev.cluvex.zedsecure.core.tor.TorConfigBuilder.supportsUpstreamProxy(settingsProvider())) {
            val refused = dev.cluvex.zedsecure.data.config.CrossChainUnsupportedException(
                dev.cluvex.zedsecure.data.config.CrossChainUnsupportedException.DIALER, inner.name,
            )
            VpnManager.onError(refused.message ?: "${inner.name} cannot run through ${outer.name}")
            return
        }
        VpnManager.onStarting(name)
        thread(name = "desktop-chain") {
            LogBus.append("I/Desktop chain: ${inner.name} runs through ${outer.name}")
            val carrierPort = runCatching { startCarrier(outer) }.getOrElse { e ->
                LogBus.append("E/Desktop chain: ${outer.name} did not start: ${e.message}")
                -1
            }
            if (carrierPort <= 0) {
                teardown()
                VpnManager.onError("${outer.name} did not start, so the chain has nothing to run through")
                return@thread
            }
            LogBus.append("I/Desktop chain: ${outer.name} carries on 127.0.0.1:$carrierPort")
            val error = runCatching { startInner(inner, carrierPort) }.getOrElse { it.message ?: "failed" }
            if (error != null) {
                teardown()
                VpnManager.onError("${inner.name} could not run through ${outer.name}: $error")
                return@thread
            }
            VpnManager.onConnected(name)
        }
    }

    private fun startCarrier(outer: dev.cluvex.zedsecure.domain.config.VpnProfile): Int {
        carrierBypass = null
        outer.psiphonSettings()?.let { psiphonProfile ->
            val socks = localPort(LocalPorts.PSIPHON_SOCKS)
            val http = localPort(LocalPorts.PSIPHON_HTTP, setOf(socks))
            val engine = DesktopPsiphon(psiphonProfile, work, socks, http, engineCgroup = engineCgroupForTun())
            outerPsiphon = engine
            return if (engine.start().isSuccess) socks else -1
        }
        if (outer.isTor) {
            val engine = DesktopTor(settingsProvider(), work, engineCgroup = engineCgroupForTun())
            outerTor = engine
            return if (engine.start(onProgress = { LogBus.append("I/Tor bootstrapped $it%") }).isSuccess) engine.socksPort else -1
        }
        outer.sshSettings()?.let { sshProfile ->
            val controller = SshController(profile = sshProfile, cipher = "auto", compression = false, listenPort = SSH_PORT)
            outerSsh = controller
            carrierBypass = resolveHosts(listOf(sshProfile.host))
            return controller.start()
        }
        outer.dnsTunnelSettings()?.let { dnsProfile ->
            val engine = DesktopDnsTunnel(dnsProfile, work, listenPort = DNS_PORT)
            outerDns = engine
            carrierBypass = resolveHosts(dnsResolverHosts(dnsProfile))
            return engine.start()
        }
        val settings = settingsProvider()
        val options = settings.toBuildOptions(geoAssetsAvailable = geoAssetsAvailable(), geoCodes = geoCodes(settings))
            .copy(carrier = true, appendHttpProxy = false, lan = null)
        val port = localPort(CARRIER_PORT)
        val json = keepCoreOutside(DesktopXray.withSocksPort(xrayJsonOf(outer, options), LocalProxy.SOCKS_PORT, port))
        carrierBypass = resolveHosts(DesktopXray.extractServerHosts(json))
        val core = XrayCore(work)
        carrier = core
        return if (core.start(json, CARRIER_CONFIG)) port else -1
    }

    private fun startInner(inner: dev.cluvex.zedsecure.domain.config.VpnProfile, carrierPort: Int): String? {
        inner.psiphonSettings()?.let { psiphonProfile ->
            val socks = localPort(LocalPorts.PSIPHON_SOCKS, setOf(carrierPort))
            val http = localPort(LocalPorts.PSIPHON_HTTP, setOf(socks, carrierPort))
            val engine = DesktopPsiphon(psiphonProfile, work, socks, http, upstreamSocksPort = carrierPort)
            psiphon = engine
            engine.start().exceptionOrNull()?.let { return it.message ?: "Psiphon failed to start" }
            return if (connectThrough(engine.socksPort, CHAIN, carrierBypass)) null else "the core did not start"
        }
        if (inner.isTor) {
            val dnsPort = if (runMode == RunMode.Vpn && TunMode.supported()) freeUdpPort() else null
            val engine = DesktopTor(settingsProvider(), work, upstreamSocksPort = carrierPort, dnsPort = dnsPort)
            tor = engine
            engine.start(onProgress = { LogBus.append("I/Tor bootstrapped $it%") })
                .exceptionOrNull()?.let { return it.message ?: "Tor failed to start" }
            return if (connectThrough(engine.socksPort, CHAIN, carrierBypass, localDns = engine.dnsPort)) null else "the core did not start"
        }
        inner.sshSettings()?.let { sshProfile ->
            val controller = SshController(
                profile = sshProfile, cipher = "auto", compression = false,
                listenPort = localPort(SSH_PORT + 5, setOf(carrierPort)),
                proxySocksHost = "127.0.0.1", proxySocksPort = carrierPort,
            )
            ssh = controller
            val port = controller.start()
            if (port < 0) return "SSH failed to connect"
            return if (connectThrough(port, CHAIN, carrierBypass)) null else "the core did not start"
        }
        val settings = settingsProvider()
        val options = settings.toBuildOptions(
            geoAssetsAvailable = geoAssetsAvailable(),
            geoCodes = geoCodes(settings),
            ruleOutbounds = ruleOutboundsProvider(
                dev.cluvex.zedsecure.domain.config.RoutingMigration.effectiveRulesets(settings),
            ),
            ruleGroups = ruleGroupsProvider(
                dev.cluvex.zedsecure.domain.config.RoutingMigration.effectiveRulesets(settings),
            ),
        ).let(::vpnModeOptions)
        val chained = dev.cluvex.zedsecure.domain.config.XrayJsonBuilder.withCarrierProxy(xrayJsonOf(inner, options), carrierPort)
        val socksPort = localPort(LocalProxy.SOCKS_PORT, setOf(carrierPort))
        val metricsPort = localPort(METRICS_PORT, setOf(socksPort, carrierPort))
        val bypass = carrierBypass
        val json = DesktopXray.augmentWithMetrics(DesktopXray.withSocksPort(chained, LocalProxy.SOCKS_PORT, socksPort), metricsPort)
            .let { if (bypass != null) keepCoreOutside(it) else it }
        val core = XrayCore(work)
        if (!core.start(json)) return "the core did not start"
        xray = core
        val excluded = engineCgroup.takeIf { bypass == null }
        if (!route(socksPort, bypass.orEmpty(), udpOverTcp = false, tunCarries = CHAIN.takeIf { bypass == null && excluded == null }, excludeCgroup = excluded)) {
            return "could not route traffic"
        }
        VpnManager.activeSocksPort = socksPort
        DesktopStats(metricsPort).also { stats = it; it.start() }
        return null
    }

    private fun xrayJsonOf(
        profile: dev.cluvex.zedsecure.domain.config.VpnProfile,
        options: dev.cluvex.zedsecure.domain.config.XrayJsonBuilder.BuildOptions,
    ): String = if (profile.isProxyChain) {
        chainConfigProvider(profile, options) ?: error("Proxy chain members are unavailable")
    } else {
        profile.toXrayConfigJson(options)
    }

    private fun dnsResolverHosts(p: DnsTunnelProfile): List<String> =
        if (p.dnsTransport == DnsTunnelProfile.TRANSPORT_DOH) {
            runCatching { java.net.URI(p.dohUrl.trim()).host }.getOrNull()?.let { listOf(it) } ?: emptyList()
        } else {
            p.resolvers.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }.map { hp ->
                if (hp.startsWith("[")) hp.substringAfter('[').substringBefore(']')
                else hp.substringBefore(':')
            }
        }

    private fun startXray(
        name: String,
        profile: dev.cluvex.zedsecure.domain.config.VpnProfile,
        config: ConfigRepository,
    ) {
        val settings = settingsProvider()
        val options = settings.toBuildOptions(
            geoAssetsAvailable = geoAssetsAvailable(),
            geoCodes = geoCodes(settings),
            ruleOutbounds = ruleOutboundsProvider(
                dev.cluvex.zedsecure.domain.config.RoutingMigration.effectiveRulesets(settings),
            ),
            ruleGroups = ruleGroupsProvider(
                dev.cluvex.zedsecure.domain.config.RoutingMigration.effectiveRulesets(settings),
            ),
        ).let(::vpnModeOptions)

        dev.cluvex.zedsecure.core.AutoSelect.clearPrepared()
        val baseJson = runCatching {
            if (profile.isAutoSelect) {
                val build = config.buildAutoSelectConfig(
                    profile,
                    options,
                    settings.autoSelectTuning(),
                )
                dev.cluvex.zedsecure.core.AutoSelect.prepare(profile.id, build.memberProfiles)
                build.json
            } else {
                xrayJsonOf(profile, options)
            }
        }.getOrElse {
            VpnManager.onError(it.message ?: "This config type isn't supported on desktop yet"); return
        }
        val socksPort = localPort(LocalProxy.SOCKS_PORT)
        val metricsPort = localPort(METRICS_PORT, setOf(socksPort))
        val ported = DesktopXray.augmentWithMetrics(
            DesktopXray.withSocksPort(baseJson, LocalProxy.SOCKS_PORT, socksPort),
            metricsPort,
        )
        VpnManager.onStarting(name)
        thread(name = "desktop-xray") {
            val json = keepCoreOutside(ported)
            val core = XrayCore(work)
            if (!core.start(json)) { VpnManager.onError("Core failed to start"); return@thread }
            xray = core
            dev.cluvex.zedsecure.core.AutoSelect.activate()
            val bypass = resolveHosts(DesktopXray.extractServerHosts(json))
            if (!route(socksPort, bypass, udpOverTcp = false)) {
                core.stop(); xray = null
                VpnManager.onError("Could not route traffic (TUN + system proxy failed)"); return@thread
            }
            VpnManager.activeSocksPort = socksPort
            DesktopStats(metricsPort).also { stats = it; it.start() }
            VpnManager.onConnected(name)
        }
    }

    private fun startSingBoxConfig(name: String, profile: dev.cluvex.zedsecure.domain.config.VpnProfile) {
        val source = profile.source as? dev.cluvex.zedsecure.domain.config.ProfileSource.SingBoxConfig ?: return
        val socksPort = localPort(LocalProxy.SOCKS_PORT)
        val prepared = runCatching {
            dev.cluvex.zedsecure.domain.config.SingBoxConfigs.prepareForDesktop(
                source.json,
                socksPort = socksPort,
                clashApiPort = localPort(METRICS_PORT, setOf(socksPort)),
            )
        }.getOrElse { VpnManager.onError(it.message ?: "Not a usable sing-box config"); return }
        VpnManager.onStarting(name)
        thread(name = "desktop-singbox") {
            val core = XrayCore(work)
            if (!core.startSingBox(prepared.json)) { VpnManager.onError("sing-box failed to start"); return@thread }
            xray = core

            val hosts = dev.cluvex.zedsecure.domain.config.SingBoxJson.servers(source.json).mapNotNull { it.address }
            if (!route(prepared.socksPort, resolveHosts(hosts), udpOverTcp = false)) {
                core.stop(); xray = null
                VpnManager.onError("Could not route traffic (TUN + system proxy failed)"); return@thread
            }
            VpnManager.activeSocksPort = prepared.socksPort
            DesktopSingBoxStats(prepared.clashApi, prepared.clashSecret).also { singBoxStats = it; it.start() }
            VpnManager.onConnected(name)
        }
    }

    private fun startSsh(name: String, sshProfile: dev.cluvex.zedsecure.domain.config.SshProfile) {
        VpnManager.onStarting(name)
        thread(name = "desktop-ssh") {
            val controller = SshController(
                profile = sshProfile,
                cipher = "auto",
                compression = false,
                listenPort = SSH_PORT,
            )
            ssh = controller
            val sshPort = controller.start()
            if (sshPort < 0) { VpnManager.onError("SSH failed to connect"); return@thread }

            val shimPort = localPort(SHIM_PORT, setOf(sshPort))
            val bridge = SocksTunBridge("127.0.0.1", shimPort, "127.0.0.1", sshPort, dnsHost = "8.8.8.8")
            shim = bridge
            if (!bridge.start()) { controller.stop(); ssh = null; VpnManager.onError("Shim failed"); return@thread }
            val bypass = resolveHosts(listOf(sshProfile.host))
            if (!route(shimPort, bypass, udpOverTcp = true)) {
                bridge.stop(); controller.stop(); ssh = null
                VpnManager.onError("Could not route traffic"); return@thread
            }
            VpnManager.activeSocksPort = shimPort
            startShimStats(bridge)
            VpnManager.onConnected(name)
        }
    }

    private fun localPort(preferred: Int, taken: Set<Int> = emptySet()): Int =
        LocalPortPicker.pick(preferred, taken).also { port ->
            if (port != preferred) {
                LogBus.append("W/Desktop port $preferred is in use by another program; using $port instead")
            }
        }

    private fun freeUdpPort(): Int? = runCatching {
        java.net.DatagramSocket(0, java.net.InetAddress.getLoopbackAddress()).use { it.localPort }
    }.getOrNull()

    private fun engineCgroupForTun(): String? {
        if (runMode != RunMode.Vpn || Os.current != Os.LINUX) return null
        engineCgroup?.let { return it }
        return EngineCgroup.create().also { created ->
            engineCgroup = created
            if (created == null) LogBus.append("W/Desktop could not give the engine its own cgroup here; VPN mode will use the system proxy for it")
        }
    }

    private fun route(
        socksPort: Int,
        bypassIps: List<String>,
        udpOverTcp: Boolean,
        tunCarries: String? = null,
        excludeCgroup: String? = null,
    ): Boolean {
        if (runMode == RunMode.ProxyOnly) {
            LogBus.append("I/Desktop SOCKS and HTTP proxy on 127.0.0.1:$socksPort; the system proxy is left alone")
            return true
        }
        if (runMode == RunMode.Vpn && tunCarries != null) {
            LogBus.append("W/Desktop $tunCarries reaches too many servers to route around a tunnel; using the system proxy")
            if (tunCarries == CHAIN) LogBus.append("I/Desktop VPN mode needs a chain whose first hop is Xray, SSH or a DNS tunnel")
        }
        val useTun = runMode == RunMode.Vpn && tunCarries == null
        if (useTun && !TunMode.supported()) {
            LogBus.append("W/Desktop VPN mode is not available on ${Os.current} yet; using the system proxy")
        }
        if (useTun && TunMode.supported()) {
            LogBus.append("I/Desktop TUN bypass: ${bypassIps.joinToString().ifEmpty { "(none)" }}")
            excludeCgroup?.let { LogBus.append("I/Desktop the engine runs outside the tunnel from its own cgroup") }
            val settings = settingsProvider()
            val engine = TunMode.Factory.create(
                work, socksPort, bypassIps, udpOverTcp, settings.vpnDns, AdminPassword::ask,
                allowApps = listOf("xray.exe", "zeddns.exe").map { File(work, it) }.filter { it.isFile },
                zeptunOnWindows = settings.useZepTun,
                ipv6 = settings.enableIpv6,
                excludeCgroup = excludeCgroup,
            )
            if (engine != null && engine.start()) { tun = engine; return true }
            LogBus.append("W/Desktop TUN unavailable — falling back to system proxy")
        }
        return if (SystemProxy.set("127.0.0.1", socksPort)) { usedSystemProxy = true; true } else false
    }

    private fun startShimStats(bridge: SocksTunBridge) {
        var totalDown = 0L
        var totalUp = 0L
        meter = DesktopMeter(totals = {
            val (down, up) = bridge.readDelta()
            totalDown += down
            totalUp += up
            totalDown to totalUp
        }).also { it.start() }
    }

    private fun vpnModeOptions(options: dev.cluvex.zedsecure.domain.config.XrayJsonBuilder.BuildOptions) = when {
        runMode != RunMode.Vpn -> options
        options.directDns.trim().equals("localhost", ignoreCase = true) -> {
            LogBus.append("I/Desktop VPN mode would send the system resolver back into the tunnel; direct DNS uses ${AppSettings().directDns}")
            options.copy(directDns = AppSettings().directDns, tunTraffic = true)
        }
        else -> options.copy(tunTraffic = true)
    }

    private fun keepCoreOutside(json: String): String {
        if (runMode != RunMode.Vpn || !TunMode.supported()) return json
        val iface = PhysicalInterface.detect()
        if (iface == null || !PhysicalInterface.canBind()) {
            LogBus.append("W/Desktop could not find the network adapter; only the servers are kept outside the tunnel")
            return json
        }
        LogBus.append("I/Desktop the core's own connections leave through $iface, outside the tunnel")
        return DesktopXray.bindOutbounds(json, iface, pinServerAddresses(DesktopXray.extractServerHosts(json)))
    }

    private fun pinServerAddresses(hosts: List<String>): Map<String, List<String>> =
        hosts.filter { host -> host.any { it.isLetter() } && ':' !in host }.associateWith { host ->
            val all = runCatching { InetAddress.getAllByName(host).toList() }.getOrDefault(emptyList())
            all.filterIsInstance<java.net.Inet4Address>().ifEmpty { all }.mapNotNull { it.hostAddress }.distinct()
        }.filterValues { it.isNotEmpty() }

    private fun resolveHosts(hosts: List<String>): List<String> = hosts.flatMap { host ->
        runCatching { InetAddress.getAllByName(host).map { it.hostAddress } }.getOrDefault(emptyList())
    }.distinct()

    fun reconnect(config: ConfigRepository) {
        val state = VpnManager.status.value.state
        if (state != dev.cluvex.zedsecure.domain.model.ConnectionState.Connected) return
        thread(name = "desktop-reconnect") {
            stop()
            val deadline = System.currentTimeMillis() + 15_000
            while (VpnManager.status.value.state != dev.cluvex.zedsecure.domain.model.ConnectionState.Idle && System.currentTimeMillis() < deadline) {
                Thread.sleep(100)
            }
            if (VpnManager.status.value.state == dev.cluvex.zedsecure.domain.model.ConnectionState.Idle) toggle(config)
        }
    }

    fun stop() {
        if (!VpnManager.onStopping()) return
        thread(name = "desktop-vpn-stop") {
            teardown()
            VpnManager.onDisconnected()
        }
    }

    fun shutdown() {
        teardown()
        dev.cluvex.zedsecure.desktop.core.LinuxBroker.close()
        dev.cluvex.zedsecure.desktop.core.WindowsElevation.close()
    }

    private fun tunnelLost() {
        if (tun != null && VpnManager.status.value.state.isActive) {
            LogBus.append("E/Desktop the tunnel engine stopped; disconnecting so traffic does not leave outside the VPN")
            thread(name = "desktop-tun-lost") {
                teardown()
                VpnManager.onError("The tunnel stopped unexpectedly")
            }
        }
    }

    init {
        dev.cluvex.zedsecure.desktop.core.LinuxBroker.onTunLost = { tunnelLost() }
        dev.cluvex.zedsecure.desktop.core.WindowsElevation.onLost = { what -> if (what == "tun") tunnelLost() }
    }

    @Synchronized
    private fun teardown() {
        meter?.stop(); meter = null
        stats?.stop(); stats = null
        singBoxStats?.stop(); singBoxStats = null
        tun?.stop(); tun = null
        tor?.let { tor = null; it.stop() }
        ikev2?.let { ikev2 = null; it.disconnect() }
        openConnect?.let { openConnect = null; it.disconnect() }
        psiphon?.let { psiphon = null; it.stop() }
        masterDns?.let { masterDns = null; it.stop() }
        shim?.stop(); shim = null
        ssh?.stop(); ssh = null
        dnsTunnel?.stop(); dnsTunnel = null
        if (usedSystemProxy) { runCatching { SystemProxy.clear() }; usedSystemProxy = false }
        xray?.stop(); xray = null
        carrier?.let { carrier = null; it.stop() }
        outerPsiphon?.let { outerPsiphon = null; it.stop() }
        outerTor?.let { outerTor = null; it.stop() }
        outerSsh?.let { outerSsh = null; it.stop() }
        outerDns?.let { outerDns = null; it.stop() }
        engineCgroup?.let { engineCgroup = null; EngineCgroup.remove(it) }
        dev.cluvex.zedsecure.core.AutoSelect.end()
    }
}
