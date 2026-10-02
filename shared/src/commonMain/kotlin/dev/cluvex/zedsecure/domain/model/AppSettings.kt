package dev.cluvex.zedsecure.domain.model

import kotlinx.serialization.Serializable

enum class ThemeMode { System, Light, Dark }

enum class ScreenTransition {
    Fade,

    Slide,

    Push,

    Depth,

    Elastic,

    Curtain,

    Flip,
}

enum class AppLanguage(val tag: String?) {
    System(null),
    English("en"),
    Persian("fa"),
    Chinese("zh"),
    Russian("ru"),
}

enum class RoutingMode { Global, BypassLan, BypassIran, BypassLanAndIran }

enum class DomainStrategy(val value: String) {
    AsIs("AsIs"),
    IpIfNonMatch("IPIfNonMatch"),
    IpOnDemand("IPOnDemand"),
}

enum class LogLevel(val value: String) {
    Debug("debug"),
    Info("info"),
    Warning("warning"),
    Error("error"),
    None("none"),
}

enum class RenderingMode { Auto, Gpu, Software }

enum class RunMode(val value: String) {
    Vpn("VPN"),
    ProxyOnly("Proxy only"),
    SystemProxy("System proxy"),
}

enum class HevLogLevel(val value: String) {
    Error("error"),
    Warn("warn"),
    Info("info"),
    Debug("debug"),
}

enum class VpnBypassLan(val value: String) {
    FollowConfig("0"),
    Bypass("1"),
    NotBypass("2"),
}

enum class CardCornerStyle(val serverCardDp: Int, val tileScale: Float) {
    Squared(6, 0.30f),
    Rounded(22, 1f),
    Pill(34, 1.45f),
}

enum class NavBarStyle {
    FloatingPill,

    FullBar,

    ExpressivePill,

    CompactDock,

    Underline,

    Minimal,
}

enum class NotifChip {
    Speed,

    ConfigName,

    Duration,

    Total,

    None,
}

enum class ConnectButtonStyle {
    Pill,
    Hero,
    Ring,
    Bar,
    Icon,
    Switch,
}

enum class ListDensity(val cardVerticalDp: Int, val gapDp: Int) {
    Comfortable(12, 10),
    Compact(6, 6),
}

enum class UiFontScale(val scale: Float) {
    Smallest(0.80f),
    Smaller(0.90f),
    Normal(1.00f),
    Larger(1.12f),
    Largest(1.25f),
}

enum class TrafficTileSize(val paddingDp: Int, val gapDp: Int) {
    Small(8, 5),
    Normal(14, 12),
    Large(18, 16),
}

enum class TrafficCardStyle {
    Cards,

    Duo,

    Rings,

    Minimal,

    Graph,
}

enum class OutboundDomainResolve(val value: String) {
    DoNotResolve("0"),
    ResolveAndAddToHosts("1"),
    ResolveAndReplace("2"),
}

enum class XudpQuic(val value: String) {
    Reject("reject"),
    Allow("allow"),
    Skip("skip"),
}

enum class SingBoxMuxProtocol(val value: String) {
    H2Mux("h2mux"),
    SMux("smux"),
    YaMux("yamux"),
}

enum class SingBoxStack(val value: String) {
    Auto(""),

    System("system"),

    GVisor("gvisor"),

    Mixed("mixed"),
}

enum class FragmentPackets(val value: String) {
    TlsHello("tlshello"),
    OneOne("1-1"),
    OneTwo("1-2"),
    OneThree("1-3"),
    OneFive("1-5"),
}

enum class VpnInterfaceAddress(
    val label: String,
    val ipv4Client: String,
    val ipv4Router: String,
    val ipv6Client: String,
    val ipv6Router: String,
) {
    Option1("10.10.14.x", "10.10.14.1", "10.10.14.2", "fc00::10:10:14:1", "fc00::10:10:14:2"),
    Option2("10.1.0.x", "10.1.0.1", "10.1.0.2", "fc00::10:1:0:1", "fc00::10:1:0:2"),
    Option3("10.0.0.x", "10.0.0.1", "10.0.0.2", "fc00::10:0:0:1", "fc00::10:0:0:2"),
    Option4("172.31.0.x", "172.31.0.1", "172.31.0.2", "fc00::172:31:0:1", "fc00::172:31:0:2"),
    Option5("172.20.0.x", "172.20.0.1", "172.20.0.2", "fc00::172:20:0:1", "fc00::172:20:0:2"),
    Option6("172.16.0.x", "172.16.0.1", "172.16.0.2", "fc00::172:16:0:1", "fc00::172:16:0:2"),
    Option7("192.168.100.x", "192.168.100.1", "192.168.100.2", "fc00::192:168:100:1", "fc00::192:168:100:2"),
}

enum class GeoFilesSource(val repo: String) {
    Loyalsoldier("Loyalsoldier/v2ray-rules-dat"),
    Runetfreedom("runetfreedom/russia-v2ray-rules-dat"),
    Chocolate4U("Chocolate4U/Iran-v2ray-rules"),
}

@Serializable
data class AppSettings(

    val ai: dev.cluvex.zedsecure.domain.ai.AiSettings = dev.cluvex.zedsecure.domain.ai.AiSettings(),

    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = false,

    val accentColor: Int = 0,

    val amoledBlack: Boolean = false,
    val language: AppLanguage = AppLanguage.System,

    val customDownloadColor: Long? = null,
    val customUploadColor: Long? = null,

    val customDownloadTileColor: Long? = null,
    val customUploadTileColor: Long? = null,
    val customConnectColor: Long? = null,

    val customConnectedColor: Long? = null,

    val customTapHintColor: Long? = null,

    val customServerCardColor: Long? = null,
    val customServerActiveColor: Long? = null,

    val customConfigCardColor: Long? = null,
    val customConfigCardTextColor: Long? = null,

    val connectButtonStyle: ConnectButtonStyle = ConnectButtonStyle.Hero,

    val navBarStyle: NavBarStyle = NavBarStyle.ExpressivePill,

    val themeProfileId: String? = null,

    val cardCornerStyle: CardCornerStyle = CardCornerStyle.Rounded,
    val listDensity: ListDensity = ListDensity.Comfortable,

    val showServerPing: Boolean = true,
    val showServerUsage: Boolean = true,

    val monospaceAddress: Boolean = false,
    val speedInNotification: Boolean = true,

    val liveNotification: Boolean = true,

    val notifChip: NotifChip = NotifChip.Speed,

    val onboardingVersion: Int = 0,

    val screenTransition: ScreenTransition = ScreenTransition.Push,
    val confirmRemove: Boolean = true,

    val homeAfterSelect: Boolean = false,

    val showTrafficTiles: Boolean = true,
    val trafficTilesAboveHero: Boolean = false,
    val trafficTileSize: TrafficTileSize = TrafficTileSize.Normal,

    val uiFontScale: UiFontScale = UiFontScale.Normal,
    val trafficCardStyle: TrafficCardStyle = TrafficCardStyle.Cards,
    val doubleColumnDisplay: Boolean = false,
    val groupAllDisplay: Boolean = true,
    val reduceMotion: Boolean = false,
    val renderingMode: RenderingMode = RenderingMode.Auto,

    val speedFabAtEnd: Boolean = true,
    val speedFabY: Float = 0.45f,

    val verifiedOriginCode: String = "",
    val verifiedOriginLabel: String = "",

    val runMode: RunMode = RunMode.Vpn,

    val enableIpv6: Boolean = false,
    val preferIpv6: Boolean = false,
    val localDnsEnabled: Boolean = false,
    val fakeDnsEnabled: Boolean = false,
    val vpnDns: String = "1.1.1.1",
    val appendHttpProxy: Boolean = false,
    val vpnBypassLan: VpnBypassLan = VpnBypassLan.FollowConfig,
    val vpnInterfaceAddress: VpnInterfaceAddress = VpnInterfaceAddress.Option2,
    val vpnMtu: Int = 1500,

    val useZepTun: Boolean = false,
    val hevTunLogLevel: HevLogLevel = HevLogLevel.Warn,

    val hevTunRwTimeout: String = "300,60",

    val sniffingEnabled: Boolean = true,
    val routeOnly: Boolean = false,
    val enableLocalProxy: Boolean = true,
    val proxySharing: Boolean = false,
    val dynamicSocksPort: Boolean = false,

    val socksPort: Int = dev.cluvex.zedsecure.domain.config.LocalPorts.LAN_SOCKS,

    val socksUsername: String = "",
    val socksPassword: String = "",

    val socksEnableUdp: Boolean = false,
    val remoteDns: String = "https://cloudflare-dns.com/dns-query",
    val directDns: String = "223.5.5.5",

    val dnsHosts: String = "",
    val logLevel: LogLevel = LogLevel.Warning,
    val outboundDomainResolve: OutboundDomainResolve = OutboundDomainResolve.ResolveAndAddToHosts,

    val domainStrategy: DomainStrategy = DomainStrategy.IpIfNonMatch,
    val routingMode: RoutingMode = RoutingMode.BypassLan,
    val blockAds: Boolean = false,

    val customProxyRules: String = "",
    val customDirectRules: String = "",
    val customBlockRules: String = "",

    val rulesets: List<RulesetItem> = emptyList(),

    val routingMigrated: Boolean = false,

    val routingSeeded: Boolean = false,

    val perAppProxyEnabled: Boolean = false,
    val perAppBypassMode: Boolean = true,
    val perAppPackages: Set<String> = emptySet(),

    val muxEnabled: Boolean = false,
    val muxConcurrency: Int = 8,
    val muxXudpConcurrency: Int = 16,
    val muxXudpQuic: XudpQuic = XudpQuic.Reject,

    val singBoxMuxEnabled: Boolean = false,
    val singBoxMuxProtocol: SingBoxMuxProtocol = SingBoxMuxProtocol.H2Mux,

    val singBoxMuxMaxConnections: Int = 4,

    val singBoxMuxPadding: Boolean = false,

    val singBoxBrutalEnabled: Boolean = false,
    val singBoxBrutalUpMbps: Int = 50,
    val singBoxBrutalDownMbps: Int = 100,

    val singBoxTlsFragment: Boolean = false,

    val singBoxTlsRecordFragment: Boolean = false,

    val singBoxUtlsFingerprint: String = "",

    val singBoxUdpOverTcp: Boolean = false,

    val singBoxTunStack: SingBoxStack = SingBoxStack.Auto,

    val singBoxStrictRoute: Boolean = false,

    val singBoxEndpointIndependentNat: Boolean = false,

    val singBoxStoreCache: Boolean = true,

    val singBoxNtpEnabled: Boolean = false,
    val singBoxNtpServer: String = "time.apple.com",

    val fragmentEnabled: Boolean = false,
    val fragmentPackets: FragmentPackets = FragmentPackets.TlsHello,
    val fragmentLength: String = "50-100",
    val fragmentInterval: String = "10-20",
    val fragmentMaxSplit: Int = 10,

    val autoSelectCheckSeconds: Int = 30,

    val autoSelectSweepMinutes: Int = 10,

    val autoSelectSwitchMargin: Int = 30,

    val autoSelectRetry: Boolean = true,

    val autoConnectOnBoot: Boolean = false,
    val delayTestUrl: String = "https://www.gstatic.com/generate_204",
    val realPingConcurrency: Int = 16,
    val ipApiUrl: String = "https://api.ip.sb/geoip",
    val useRealDelayForTests: Boolean = true,

    val lastUpdateCheckMs: Long = 0L,

    val dismissedUpdateVersion: String = "",
    val dismissedUpdateAtMs: Long = 0L,

    val successfulConnections: Int = 0,
    val ratePromptLastShownMs: Long = 0L,
    val rateNeverAsk: Boolean = false,

    val geoFilesSource: GeoFilesSource = GeoFilesSource.Loyalsoldier,

    val autoUpdateSubscriptions: Boolean = false,
    val subscriptionUpdateIntervalHours: Int = 12,
    val autoTestAfterUpdate: Boolean = false,
    val autoRemoveInvalidAfterTest: Boolean = false,
    val autoSortAfterTest: Boolean = false,

    val dnsGlobalResolverEnabled: Boolean = false,
    val dnsGlobalResolvers: String = "",

    val dnsPoolEnabled: Boolean = false,
    val dnsPoolText: String = "",

    val dnsPoolFullVerification: Boolean = false,

    val remoteDnsMode: String = "default",
    val remoteDnsPrimary: String = "",
    val remoteDnsFallback: String = "",

    val dnsWorkerMode: String = "per_query",

    val preventDnsFallback: Boolean = true,

    val sshCipher: String = "auto",
    val sshCompression: Boolean = false,
    val sshMaxChannels: Int = 16,

    val killSwitch: Boolean = false,

    val torBridgesMode: String = "none",

    val torBridgeTransport: String = "obfs4",

    val torOwnBridges: String = "",

    val torEntryNodes: String = "",
    val torExitNodes: String = "",
    val torExcludeNodes: String = "",
    val torExcludeExitNodes: String = "",
    val torStrictNodes: Boolean = false,

    val torVirtualAddrNetwork: String = "10.192.0.0/10",
    val torHardwareAccel: Boolean = true,
    val torAvoidDiskWrites: Boolean = true,
    val torConnectionPadding: Boolean = true,
    val torReducedConnectionPadding: Boolean = true,
    val torFascistFirewall: Boolean = false,
    val torNewCircuitPeriod: Int = 30,
    val torMaxCircuitDirtiness: Int = 600,
    val torDormantClientTimeout: Int = 15,
    val torEnforceDistinctSubnets: Boolean = true,
    val torTrackHostExits: Boolean = false,
    val torClientUseIPv4: Boolean = true,
    val torClientUseIPv6: Boolean = true,

    val torIsolateUid: Boolean = true,
    val torIsolateDestAddr: Boolean = false,
    val torIsolateDestPort: Boolean = false,

    val torSnowflakeRendezvous: String = "amp",

    val torSnowflakeStun: String = "",

    val torEnabledBridges: String = "",

    val torFakeSniEnabled: Boolean = false,
    val torFakeSniHosts: String = "",
) {
    val useHevTun: Boolean get() = true

    val localProxyForced: Boolean get() = runMode == RunMode.Vpn && useHevTun

    val effectiveLocalProxy: Boolean get() = enableLocalProxy || localProxyForced

    val isVpnMode: Boolean get() = runMode == RunMode.Vpn

    companion object {
        val DURATION_PATTERN = Regex("""[1-9]\d*(ms|s|m|h)""")
    }
}
