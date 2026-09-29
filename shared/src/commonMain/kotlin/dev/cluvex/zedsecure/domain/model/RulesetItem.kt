package dev.cluvex.zedsecure.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class RulesetItem(
    val id: String,
    val remarks: String = "",
    val outboundTag: String = OUTBOUND_PROXY,
    val domain: List<String> = emptyList(),
    val ip: List<String> = emptyList(),
    val port: String = "",
    val network: String = "",
    val protocol: List<String> = emptyList(),
    val enabled: Boolean = true,

    val locked: Boolean = false,
) {
    companion object {
        const val OUTBOUND_PROXY = "proxy"
        const val OUTBOUND_DIRECT = "direct"
        const val OUTBOUND_BLOCK = "block"

        val OUTBOUND_TAGS = listOf(OUTBOUND_PROXY, OUTBOUND_DIRECT, OUTBOUND_BLOCK)

        const val OUTBOUND_PROFILE_PREFIX = "profile:"

        fun profileTag(profileId: String): String = "$OUTBOUND_PROFILE_PREFIX$profileId"

        fun profileIdOf(outboundTag: String): String? =
            outboundTag.removePrefix(OUTBOUND_PROFILE_PREFIX)
                .takeIf { it != outboundTag && it.isNotBlank() }

        private var seq = 0

        fun newId(): String {
            seq += 1
            return "rule-${kotlin.random.Random.nextLong().toULong().toString(16)}-$seq"
        }
    }

    val isEmpty: Boolean
        get() = domain.isEmpty() && ip.isEmpty() && port.isBlank() &&
            network.isBlank() && protocol.isEmpty()
}

class RulesetPreset(val label: String, private val make: () -> List<RulesetItem>) {
    fun rules(): List<RulesetItem> = make()
}

object RulesetPresets {
    fun blockAds() = listOf(
        RulesetItem(
            id = RulesetItem.newId(),
            remarks = "Block ads",
            outboundTag = RulesetItem.OUTBOUND_BLOCK,
            domain = listOf("geosite:category-ads-all"),
        ),
    )

    fun bypassLan() = listOf(
        RulesetItem(
            id = RulesetItem.newId(),
            remarks = "Bypass LAN",
            outboundTag = RulesetItem.OUTBOUND_DIRECT,
            ip = listOf("geoip:private"),
        ),
    )

    fun bypassPrivate() = listOf(
        RulesetItem(
            id = RulesetItem.newId(),
            remarks = "Bypass private (no geo assets)",
            outboundTag = RulesetItem.OUTBOUND_DIRECT,
            ip = PRIVATE_RANGES,
        ),
    )

    fun bypassMainlandIran() = listOf(
        RulesetItem(
            id = RulesetItem.newId(),
            remarks = "Bypass Iran (domains)",
            outboundTag = RulesetItem.OUTBOUND_DIRECT,
            domain = listOf("geosite:category-ir"),
        ),
        RulesetItem(
            id = RulesetItem.newId(),
            remarks = "Bypass Iran (IPs)",
            outboundTag = RulesetItem.OUTBOUND_DIRECT,
            ip = listOf("geoip:ir"),
        ),
    )

    fun bypassMainlandChina() = listOf(
        RulesetItem(
            id = RulesetItem.newId(),
            remarks = "Bypass China (domains)",
            outboundTag = RulesetItem.OUTBOUND_DIRECT,
            domain = listOf("geosite:cn"),
        ),
        RulesetItem(
            id = RulesetItem.newId(),
            remarks = "Bypass China (IPs)",
            outboundTag = RulesetItem.OUTBOUND_DIRECT,
            ip = listOf("geoip:cn"),
        ),
    )

    fun blockBittorrent() = listOf(
        RulesetItem(
            id = RulesetItem.newId(),
            remarks = "Block BitTorrent",
            outboundTag = RulesetItem.OUTBOUND_BLOCK,
            protocol = listOf("bittorrent"),
        ),
    )

    val PRIVATE_RANGES = listOf(
        "0.0.0.0/8", "10.0.0.0/8", "100.64.0.0/10", "127.0.0.0/8", "169.254.0.0/16",
        "172.16.0.0/12", "192.0.0.0/24", "192.0.2.0/24", "192.168.0.0/16", "198.18.0.0/15",
        "198.51.100.0/24", "203.0.113.0/24", "224.0.0.0/4", "240.0.0.0/4", "255.255.255.255/32",
        "::1/128", "fc00::/7", "fe80::/10",
    )

    fun all(): List<RulesetPreset> = listOf(
        RulesetPreset("Block ads", ::blockAds),
        RulesetPreset("Bypass LAN", ::bypassLan),
        RulesetPreset("Bypass private (no geo assets)", ::bypassPrivate),
        RulesetPreset("Bypass Iran", ::bypassMainlandIran),
        RulesetPreset("Bypass China", ::bypassMainlandChina),
        RulesetPreset("Block BitTorrent", ::blockBittorrent),
    )
}

enum class RoutingPresetType { IranWhitelist, ChinaWhitelist, ChinaBlacklist, Global, RussiaWhitelist }

object RoutingPresets {
    fun apply(existing: List<RulesetItem>, incoming: List<RulesetItem>): List<RulesetItem> =
        existing.filter { it.locked } + incoming

    fun rules(type: RoutingPresetType): List<RulesetItem> = when (type) {
        RoutingPresetType.IranWhitelist -> iranWhitelist()
        RoutingPresetType.ChinaWhitelist -> chinaWhitelist()
        RoutingPresetType.ChinaBlacklist -> chinaBlacklist()
        RoutingPresetType.Global -> global()
        RoutingPresetType.RussiaWhitelist -> russiaWhitelist()
    }

    private fun rule(
        remarks: String,
        outboundTag: String,
        domain: List<String> = emptyList(),
        ip: List<String> = emptyList(),
        port: String = "",
        network: String = "",
        protocol: List<String> = emptyList(),
    ) = RulesetItem(
        id = RulesetItem.newId(),
        remarks = remarks,
        outboundTag = outboundTag,
        domain = domain,
        ip = ip,
        port = port,
        network = network,
        protocol = protocol,
    )

    private val blockUdp443 = { rule("Block UDP 443 (QUIC)", RulesetItem.OUTBOUND_BLOCK, port = "443", network = "udp") }
    private val directLanIp = { rule("Direct LAN IP", RulesetItem.OUTBOUND_DIRECT, ip = listOf("geoip:private")) }
    private val directLanDomain = { rule("Direct LAN domains", RulesetItem.OUTBOUND_DIRECT, domain = listOf("geosite:private")) }

    private fun iranWhitelist() = listOf(
        blockUdp443(),
        directLanIp(),
        directLanDomain(),
        rule("Bypass Iran domains", RulesetItem.OUTBOUND_DIRECT, domain = listOf("domain:ir", "geosite:category-ir")),
        rule("Bypass Iran IP", RulesetItem.OUTBOUND_DIRECT, ip = listOf("geoip:ir")),
    )

    private fun russiaWhitelist() = listOf(
        rule("Bypass BitTorrent", RulesetItem.OUTBOUND_DIRECT, protocol = listOf("bittorrent")),
        blockUdp443(),
        directLanIp(),
        directLanDomain(),
        rule("Bypass Russia domains", RulesetItem.OUTBOUND_DIRECT, domain = listOf("geosite:category-ru")),
        rule("Bypass Russia IP", RulesetItem.OUTBOUND_DIRECT, ip = listOf("geoip:ru")),
    )

    private fun global() = listOf(
        blockUdp443(),
        directLanIp(),
        directLanDomain(),
        rule("Proxy everything else", RulesetItem.OUTBOUND_PROXY, port = "0-65535"),
    )

    private fun chinaWhitelist() = listOf(
        blockUdp443(),
        rule("Proxy Google", RulesetItem.OUTBOUND_PROXY, domain = listOf("geosite:google")),
        directLanIp(),
        directLanDomain(),
        rule("Bypass Chinese public DNS IP", RulesetItem.OUTBOUND_DIRECT, ip = CN_PUBLIC_DNS_IP),
        rule("Bypass Chinese public DNS domains", RulesetItem.OUTBOUND_DIRECT, domain = CN_PUBLIC_DNS_DOMAIN),
        rule("Bypass China IP", RulesetItem.OUTBOUND_DIRECT, ip = listOf("geoip:cn")),
        rule("Bypass China domains", RulesetItem.OUTBOUND_DIRECT, domain = listOf("geosite:cn")),
    )

    private fun chinaBlacklist() = listOf(
        rule("Bypass BitTorrent", RulesetItem.OUTBOUND_DIRECT, protocol = listOf("bittorrent")),
        blockUdp443(),
        rule("Proxy Google", RulesetItem.OUTBOUND_PROXY, domain = listOf("geosite:google")),
        directLanIp(),
        directLanDomain(),
        rule("Proxy overseas public DNS IP", RulesetItem.OUTBOUND_PROXY, ip = OVERSEAS_DNS_IP),
        rule("Proxy overseas public DNS domains", RulesetItem.OUTBOUND_PROXY, domain = OVERSEAS_DNS_DOMAIN),
        rule(
            "Proxy blocked services IP", RulesetItem.OUTBOUND_PROXY,
            ip = listOf("geoip:facebook", "geoip:fastly", "geoip:google", "geoip:netflix", "geoip:telegram", "geoip:twitter"),
        ),
        rule("Proxy GFW list", RulesetItem.OUTBOUND_PROXY, domain = listOf("geosite:gfw", "geosite:greatfire")),
        rule("Everything else direct", RulesetItem.OUTBOUND_DIRECT, port = "0-65535"),
    )

    private val CN_PUBLIC_DNS_IP = listOf(
        "223.5.5.5", "223.6.6.6", "2400:3200::1", "2400:3200:baba::1",
        "119.29.29.29", "1.12.12.12", "120.53.53.53", "2402:4e00::", "2402:4e00:1::",
        "180.76.76.76", "2400:da00::6666",
        "114.114.114.114", "114.114.115.115", "114.114.114.119", "114.114.115.119",
        "114.114.114.110", "114.114.115.110",
        "180.184.1.1", "180.184.2.2", "101.226.4.6", "218.30.118.6", "123.125.81.6",
        "140.207.198.6", "1.2.4.8", "210.2.4.8", "52.80.66.66",
        "117.50.22.22", "2400:7fc0:849e:200::4", "2404:c2c0:85d8:901::4",
        "117.50.10.10", "52.80.52.52", "2400:7fc0:849e:200::8", "2404:c2c0:85d8:901::8",
        "117.50.60.30", "52.80.60.30",
    )

    private val CN_PUBLIC_DNS_DOMAIN = listOf(
        "domain:alidns.com", "domain:doh.pub", "domain:dot.pub", "domain:360.cn", "domain:onedns.net",
    )

    private val OVERSEAS_DNS_IP = listOf(
        "1.1.1.1", "1.0.0.1", "2606:4700:4700::1111", "2606:4700:4700::1001",
        "1.1.1.2", "1.0.0.2", "2606:4700:4700::1112", "2606:4700:4700::1002",
        "1.1.1.3", "1.0.0.3", "2606:4700:4700::1113", "2606:4700:4700::1003",
        "8.8.8.8", "8.8.4.4", "2001:4860:4860::8888", "2001:4860:4860::8844",
        "94.140.14.14", "94.140.15.15", "2a10:50c0::ad1:ff", "2a10:50c0::ad2:ff",
        "94.140.14.15", "94.140.15.16", "2a10:50c0::bad1:ff", "2a10:50c0::bad2:ff",
        "94.140.14.140", "94.140.14.141", "2a10:50c0::1:ff", "2a10:50c0::2:ff",
        "208.67.222.222", "208.67.220.220", "2620:119:35::35", "2620:119:53::53",
        "208.67.222.123", "208.67.220.123", "2620:119:35::123", "2620:119:53::123",
        "9.9.9.9", "149.112.112.112", "2620:fe::9", "2620:fe::fe",
        "9.9.9.11", "149.112.112.11", "2620:fe::11", "2620:fe::fe:11",
        "9.9.9.10", "149.112.112.10", "2620:fe::10", "2620:fe::fe:10",
        "77.88.8.8", "77.88.8.1", "2a02:6b8::feed:0ff", "2a02:6b8:0:1::feed:0ff",
        "77.88.8.88", "77.88.8.2", "2a02:6b8::feed:bad", "2a02:6b8:0:1::feed:bad",
        "77.88.8.7", "77.88.8.3", "2a02:6b8::feed:a11", "2a02:6b8:0:1::feed:a11",
    )

    private val OVERSEAS_DNS_DOMAIN = listOf(
        "domain:cloudflare-dns.com", "domain:one.one.one.one", "domain:dns.google",
        "domain:adguard-dns.com", "domain:opendns.com", "domain:umbrella.com",
        "domain:quad9.net", "domain:yandex.net",
    )
}
