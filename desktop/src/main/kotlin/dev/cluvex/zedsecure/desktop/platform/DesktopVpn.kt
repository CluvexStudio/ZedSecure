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
    private const val SSH_PORT = LocalPorts.SSH
    private const val DNS_PORT = LocalPorts.DNS_TUNNEL

    private val work = File(System.getProperty("java.io.tmpdir"), "zedsecure").apply { mkdirs() }
    private var xray: XrayCore? = null
    private var tun: DesktopTun? = null
    private var stats: DesktopStats? = null
    private var singBoxStats: DesktopSingBoxStats? = null
    private var shim: SocksTunBridge? = null
    private var ssh: SshController? = null
    private var dnsTunnel: DesktopDnsTunnel? = null
    @Volatile private var tor: DesktopTor? = null
    @Volatile private var psiphon: DesktopPsiphon? = null
    @Volatile private var ikev2: DesktopIkev2? = null
    private var meter: DesktopMeter? = null
    private var usedSystemProxy = false

    @Volatile var runMode: RunMode = RunMode.SystemProxy

    @Volatile var settingsProvider: () -> AppSettings = { AppSettings() }

    @Volatile

    var chainConfigProvider: (dev.cluvex.zedsecure.domain.config.VpnProfile, dev.cluvex.zedsecure.domain.config.XrayJsonBuilder.BuildOptions) -> String? =
        { _, _ -> null }

    var ruleOutboundsProvider: (List<dev.cluvex.zedsecure.domain.model.RulesetItem>) -> Map<String, dev.cluvex.zedsecure.domain.config.ServerConfig> =
        { emptyMap() }

    private fun geoAssetsAvailable(): Boolean =
        File(work, "geoip.dat").let { it.isFile && it.length() > 0 } &&
            File(work, "geosite.dat").let { it.isFile && it.length() > 0 }

    fun toggle(config: ConfigRepository) {
        val state = VpnManager.status.value.state
        if (state.isActive || state.isTransitioning) { stop(); return }
        val profile = config.activeProfile() ?: run { VpnManager.onError("Select a config first"); return }

        runMode = settingsProvider().runMode

        when {
            profile.ikev2Settings() != null -> startIkev2(profile.name, profile.ikev2Settings()!!)
            profile.psiphonSettings() != null -> startPsiphon(profile.name, profile.psiphonSettings()!!)
            profile.isTor -> startTor(profile.name)
            profile.dnsTunnelSettings() != null -> startDns(profile.name, profile.dnsTunnelSettings()!!)
            profile.sshSettings() != null -> startSsh(profile.name, profile.sshSettings()!!)
            profile.isSingBoxConfig -> startSingBoxConfig(profile.name, profile)
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

    private fun startTor(name: String) {
        VpnManager.onStarting(name)
        thread(name = "desktop-tor") {
            val engine = DesktopTor(settingsProvider(), work)
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
            if (!connectThrough(engine.socksPort, "Tor")) {
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
            val engine = DesktopPsiphon(profile, work, socksPort, httpPort)
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

    private fun connectThrough(upstreamPort: Int, engine: String): Boolean {
        val socksPort = localPort(LocalProxy.SOCKS_PORT, setOf(upstreamPort))
        val metricsPort = localPort(METRICS_PORT, setOf(socksPort, upstreamPort))
        val json = DesktopXray.augmentWithMetrics(DesktopXray.frontConfig(socksPort, upstreamPort), metricsPort)
        val core = XrayCore(work)
        if (!core.start(json)) {
            VpnManager.onError("Core failed to start")
            return false
        }
        xray = core
        if (!route(socksPort, emptyList(), udpOverTcp = true, tunCarries = engine)) {
            core.stop()
            xray = null
            VpnManager.onError("Could not route traffic (system proxy failed)")
            return false
        }
        VpnManager.activeSocksPort = socksPort
        DesktopStats(metricsPort).also { stats = it; it.start() }
        return true
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
            ruleOutbounds = ruleOutboundsProvider(
                dev.cluvex.zedsecure.domain.config.RoutingMigration.effectiveRulesets(settings),
            ),
        )

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
            } else if (profile.isProxyChain) {
                chainConfigProvider(profile, options)
                    ?: error("Proxy chain members are unavailable")
            } else {
                profile.toXrayConfigJson(options)
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
            val json = if (runMode == RunMode.Vpn && TunMode.supported() && TunMode.usesZeptun()) {
                val iface = PhysicalInterface.detect()
                if (iface != null) {
                    LogBus.append("I/Desktop the core's own connections leave through $iface, outside the tunnel")
                    DesktopXray.bindOutbounds(ported, iface, pinServerAddresses(DesktopXray.extractServerHosts(ported)))
                } else {
                    LogBus.append("W/Desktop could not find the network adapter; only the servers are kept outside the tunnel")
                    ported
                }
            } else {
                ported
            }
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

    private fun route(socksPort: Int, bypassIps: List<String>, udpOverTcp: Boolean, tunCarries: String? = null): Boolean {
        if (runMode == RunMode.ProxyOnly) {
            LogBus.append("I/Desktop SOCKS and HTTP proxy on 127.0.0.1:$socksPort; the system proxy is left alone")
            return true
        }
        if (runMode == RunMode.Vpn && tunCarries != null) {
            LogBus.append("W/Desktop $tunCarries reaches too many servers to route around a tunnel; using the system proxy")
        }
        val useTun = runMode == RunMode.Vpn && tunCarries == null
        if (useTun && !TunMode.supported()) {
            LogBus.append("W/Desktop VPN mode is not available on ${Os.current} yet; using the system proxy")
        }
        if (useTun && TunMode.supported()) {
            LogBus.append("I/Desktop TUN bypass: ${bypassIps.joinToString().ifEmpty { "(none)" }}")
            val engine = TunMode.Factory.create(
                work, socksPort, bypassIps, udpOverTcp, settingsProvider().vpnDns, AdminPassword::ask,
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

    private fun pinServerAddresses(hosts: List<String>): Map<String, List<String>> =
        hosts.filter { host -> host.any { it.isLetter() } && ':' !in host }.associateWith { host ->
            val all = runCatching { InetAddress.getAllByName(host).toList() }.getOrDefault(emptyList())
            all.filterIsInstance<java.net.Inet4Address>().ifEmpty { all }.mapNotNull { it.hostAddress }.distinct()
        }.filterValues { it.isNotEmpty() }

    private fun resolveHosts(hosts: List<String>): List<String> = hosts.flatMap { host ->
        runCatching { InetAddress.getAllByName(host).map { it.hostAddress } }.getOrDefault(emptyList())
    }.distinct()

    fun stop() {
        if (!VpnManager.onStopping()) return
        thread(name = "desktop-vpn-stop") {
            teardown()
            VpnManager.onDisconnected()
        }
    }

    fun shutdown() = teardown()

    @Synchronized
    private fun teardown() {
        meter?.stop(); meter = null
        stats?.stop(); stats = null
        singBoxStats?.stop(); singBoxStats = null
        tun?.stop(); tun = null
        tor?.let { tor = null; it.stop() }
        ikev2?.let { ikev2 = null; it.disconnect() }
        psiphon?.let { psiphon = null; it.stop() }
        shim?.stop(); shim = null
        ssh?.stop(); ssh = null
        dnsTunnel?.stop(); dnsTunnel = null
        if (usedSystemProxy) { runCatching { SystemProxy.clear() }; usedSystemProxy = false }
        xray?.stop(); xray = null
        dev.cluvex.zedsecure.core.AutoSelect.end()
    }
}
