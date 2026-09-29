package dev.cluvex.zedsecure.domain.config

object DohPresets {
    data class Server(val name: String, val url: String)

    val SERVERS = listOf(
        Server("Google", "https://dns.google/dns-query"),
        Server("Cloudflare", "https://cloudflare-dns.com/dns-query"),
        Server("Cloudflare Security", "https://security.cloudflare-dns.com/dns-query"),
        Server("Quad9", "https://dns.quad9.net/dns-query"),
        Server("OpenDNS", "https://doh.opendns.com/dns-query"),
        Server("Mullvad", "https://base.dns.mullvad.net/dns-query"),
        Server("AdGuard", "https://dns.adguard.com/dns-query"),
        Server("AdGuard Unfiltered", "https://unfiltered.adguard-dns.com/dns-query"),
        Server("CleanBrowsing", "https://doh.cleanbrowsing.org/doh/security-filter/"),
        Server("DNS.SB", "https://doh.dns.sb/dns-query"),
        Server("Canadian Shield", "https://private.canadianshield.cira.ca/dns-query"),
        Server("Applied Privacy", "https://doh.applied-privacy.net/query"),
        Server("Digitale Gesellschaft", "https://dns.digitale-gesellschaft.ch/dns-query"),
        Server("Rethink DNS", "https://sky.rethinkdns.com/dns-query"),
        Server("JoinDNS4EU", "https://unfiltered.joindns4.eu/dns-query"),
        Server("IIJ Japan", "https://public.dns.iij.jp/dns-query"),
    )
}
