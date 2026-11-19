package dev.cluvex.zedsecure.scan

import dev.cluvex.zedsecure.shared.resources.Res
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

data class PackEntry(val ip: String, val name: String)

@Serializable
data class FoundEntry(
    val ip: String,
    val score: Int,
    val rtt: Int,
    val tunnel: Boolean? = null,
    val e2e: Boolean? = null,
    val network: String = "",
    val at: Long,
)

object DnsPack {
    val iran: List<PackEntry> = listOf(
        PackEntry("178.22.122.100", "Shecan"),
        PackEntry("185.51.200.2", "Shecan"),
        PackEntry("178.22.122.101", "Shecan Pro"),
        PackEntry("185.51.200.1", "Shecan Pro"),
        PackEntry("78.157.42.100", "Electro"),
        PackEntry("78.157.42.101", "Electro"),
        PackEntry("185.55.226.26", "Begzar"),
        PackEntry("185.55.225.25", "Begzar"),
        PackEntry("94.103.125.157", "Shelter"),
        PackEntry("94.103.125.158", "Shelter"),
        PackEntry("181.41.194.177", "Beshkan"),
        PackEntry("181.41.194.186", "Beshkan"),
        PackEntry("5.202.100.100", "Pishgaman"),
        PackEntry("5.202.100.101", "Pishgaman"),
        PackEntry("194.104.158.48", "Server.ir"),
        PackEntry("194.104.158.78", "Server.ir"),
        PackEntry("217.218.155.155", "ITC"),
        PackEntry("217.218.127.127", "TCI"),
        PackEntry("2.189.44.44", "TIC"),
        PackEntry("194.225.152.10", "IPM"),
        PackEntry("85.15.1.14", "Shatel"),
        PackEntry("85.15.1.15", "Shatel"),
        PackEntry("185.98.113.113", "Asiatech"),
        PackEntry("185.98.114.114", "Asiatech"),
        PackEntry("10.202.10.10", "Radar"),
        PackEntry("10.202.10.11", "Radar"),
        PackEntry("10.202.10.202", "403"),
        PackEntry("10.202.10.102", "403"),
        PackEntry("172.29.0.100", "Hostiran"),
        PackEntry("172.29.2.100", "Hostiran"),
    )

    val famous: List<PackEntry> = listOf(
        "8.8.8.8" to "Google", "8.8.4.4" to "Google", "1.1.1.1" to "Cloudflare", "1.0.0.1" to "Cloudflare",
        "1.1.1.2" to "Cloudflare", "1.0.0.2" to "Cloudflare", "9.9.9.9" to "Quad9", "149.112.112.112" to "Quad9",
        "9.9.9.10" to "Quad9", "9.9.9.11" to "Quad9", "4.2.2.1" to "Level3", "4.2.2.2" to "Level3", "4.2.2.3" to "Level3",
        "4.2.2.4" to "Level3", "209.244.0.3" to "Level3", "209.244.0.4" to "Level3", "64.6.64.6" to "Verisign",
        "64.6.65.6" to "Verisign", "208.67.222.222" to "OpenDNS", "208.67.220.220" to "OpenDNS", "94.140.14.14" to "AdGuard",
        "94.140.15.15" to "AdGuard", "94.140.14.140" to "AdGuard", "76.76.2.0" to "Control D", "76.76.10.0" to "Control D",
        "185.228.168.9" to "CleanBrowsing", "185.228.169.9" to "CleanBrowsing", "77.88.8.8" to "Yandex", "77.88.8.1" to "Yandex",
        "156.154.70.1" to "UltraDNS", "156.154.71.1" to "UltraDNS", "8.26.56.26" to "Comodo", "8.20.247.20" to "Comodo",
        "185.222.222.222" to "DNS.SB", "45.11.45.11" to "DNS.SB", "45.90.28.0" to "NextDNS", "45.90.30.0" to "NextDNS",
        "194.242.2.2" to "Mullvad", "76.76.19.19" to "Alternate", "84.200.69.80" to "DNS.Watch", "84.200.70.40" to "DNS.Watch",
        "74.82.42.42" to "Hurricane Electric", "101.101.101.101" to "Quad101", "149.112.121.10" to "CIRA",
        "193.110.81.0" to "dns0.eu", "185.253.5.0" to "dns0.eu", "223.5.5.5" to "AliDNS", "223.6.6.6" to "AliDNS",
        "119.29.29.29" to "DNSPod", "114.114.114.114" to "114DNS", "180.76.76.76" to "Baidu", "95.85.95.85" to "Gcore",
        "2.56.220.2" to "Gcore", "146.255.56.98" to "Applied Privacy", "116.202.176.26" to "LibreDNS",
        "185.95.218.42" to "Digitale Gesellschaft", "109.69.8.51" to "puntCAT", "83.220.169.155" to "Comss",
    ).map { (ip, name) -> PackEntry(ip, name) }

    val countries: List<String> = listOf("IR", "CN", "RU", "TR", "AE", "IQ", "AF", "DE", "NL", "FR", "GB", "US")

    @Volatile
    private var bundledCache: List<String>? = null

    suspend fun bundled(): List<String> {
        bundledCache?.let { return it }
        val list = runCatching { Res.readBytes("files/dns/resolvers.txt").decodeToString() }.getOrDefault("")
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()
        bundledCache = list
        return list
    }

    suspend fun defaultList(found: List<FoundEntry>): List<String> {
        val all = LinkedHashSet<String>()
        iran.forEach { all += it.ip }
        found.forEach { all += it.ip }
        famous.forEach { all += it.ip }
        all += bundled()
        return all.toList()
    }

    fun iranRanges(found: List<FoundEntry>): List<String> =
        (iran.map { it.ip } + found.map { it.ip })
            .filter { ':' !in it }
            .mapNotNull { ip -> ip.split('.').takeIf { it.size == 4 }?.let { "${it[0]}.${it[1]}.${it[2]}.0/24" } }
            .distinct()

    fun nameOf(ip: String): String? = iran.firstOrNull { it.ip == ip }?.name ?: famous.firstOrNull { it.ip == ip }?.name

    fun merge(existing: List<FoundEntry>, results: List<ResolverResult>, network: String, now: Long, cap: Int = 5000): List<FoundEntry> {
        val byIp = LinkedHashMap<String, FoundEntry>()
        results.filter { it.alive && it.score >= 50 }.forEach { r ->
            byIp[r.ip] = FoundEntry(r.ip, r.score, r.rtt, r.tunnel, r.e2e?.ok, network, now)
        }
        existing.forEach { e -> if (e.ip !in byIp) byIp[e.ip] = e }
        return byIp.values.sortedByDescending { it.at }.take(cap)
    }

    fun encodeFound(list: List<FoundEntry>): String = DnsScanHub.json.encodeToString(ListSerializer(FoundEntry.serializer()), list)

    fun decodeFound(text: String?): List<FoundEntry> =
        text?.let { runCatching { DnsScanHub.json.decodeFromString(ListSerializer(FoundEntry.serializer()), it) }.getOrNull() }.orEmpty()
}
