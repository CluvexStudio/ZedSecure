package dev.cluvex.zedsecure.core.ghost

/**
 * Additional routing layer for GHOST adult-content protection.
 *
 * These domain rules complement the configured family-safe DNS resolver. They are not
 * a replacement for DNS filtering: encrypted DNS inside another app, direct-IP access,
 * and third-party VPNs can bypass app-level domain rules.
 */
object AdultBlocklist {
    /**
     * Hostname fragments used by the routing engine. Keep these specific to avoid
     * matching ordinary words in unrelated domains.
     */
    private val keywords = listOf(
        "xvideos", "xnxx", "xhamster", "redtube", "youporn", "hentai",
        "brazzers", "onlyfans", "spankbang", "chaturbate", "stripchat",
        "bongacams", "camsoda", "pornhub", "eporner", "porntrex",
    )

    /**
     * Curated domain rules. Xray's domain: matcher also covers subdomains.
     * The list is intentionally maintained separately from the DNS provider list.
     */
    private val domains = listOf(
        "pornhub.com", "pornhub.org", "phncdn.com",
        "xvideos.com", "xvideos-cdn.com", "xnxx.com", "xnxx-cdn.com",
        "xhamster.com", "xhcdn.com", "redtube.com", "youporn.com", "tube8.com",
        "spankbang.com", "eporner.com", "porntrex.com", "beeg.com",
        "motherless.com", "thumbzilla.com", "tnaflix.com", "txxx.com", "hclips.com",
        "fapello.com", "rule34.xxx", "e-hentai.org", "nhentai.net", "hanime.tv",
        "chaturbate.com", "stripchat.com", "bongacams.com", "livejasmin.com",
        "cam4.com", "camsoda.com", "myfreecams.com", "erome.com", "ixxx.com",
        "sex.com", "fuq.com", "porn.com", "naughtyamerica.com",
        "realitykings.com", "bangbros.com",
        "redgifs.com", "4tube.com", "empflix.com", "drtuber.com",
        "slutload.com", "keezmovies.com", "vporn.com", "youjizz.com",
        "javhd.com", "javmost.com", "jav.guru", "javbus.com",
        "gelbooru.com", "danbooru.donmai.us", "rule34.paheal.net",
        "pornhubpremium.com", "pornhubpremium.net", "xhamsterlive.com", "xhamster.desi",
        "xnxx.tv", "xnxx.health", "xvideos2.com", "xvideos3.com",
        "adulttime.com", "teamskeet.com", "mofos.com", "digitalplayground.com",
        "wicked.com", "vixen.com", "deeper.com", "blacked.com", "tushy.com",
        "bellesa.co", "manyvids.com",
    )

    /** More adult hosts, kept apart from the base list above. */
    private val moreDomains = listOf(
        "xhamster2.com", "xhamster3.com", "pornone.com", "pornhd.com", "pornhat.com", "porndig.com",
        "porn300.com", "sxyprn.com", "daftsex.com", "hqporner.com", "nuvid.com", "sunporno.com",
        "tube8.com", "spankwire.com", "extremetube.com", "gotporn.com", "analdin.com", "pornpics.com",
        "imagefap.com", "luscious.net", "hentai2read.com", "hitomi.la", "e621.net", "fansly.com",
        "clips4sale.com", "iwara.tv", "missav.com", "supjav.com", "jable.tv", "flirt4free.com",
        "imlive.com", "streamate.com", "xlovecam.com", "jerkmate.com", "adultfriendfinder.com",
        "nubiles.net", "fakehub.com", "evilangel.com", "hotmovies.com", "adultempire.com",
    )

    /**
     * Encrypted-DNS endpoints that browsers use to skip the system resolver. Blocking them while the
     * adult filter is on makes those apps fall back to the filtered DNS instead of bypassing it.
     */
    private val dohBypass = listOf(
        "dns.google", "dns.google.com", "cloudflare-dns.com", "mozilla.cloudflare-dns.com",
        "chrome.cloudflare-dns.com", "one.one.one.one", "dns.quad9.net", "doh.opendns.com",
        "dns.nextdns.io", "doh.dns.sb", "dns.adguard.com", "dns.adguard-dns.com",
    )

    /** Stable, de-duplicated Xray routing rules. */
    val rules: List<String> = buildList {
        keywords.forEach { add("keyword:$it") }
        domains.forEach { add("domain:$it") }
        moreDomains.forEach { add("domain:$it") }
        dohBypass.forEach { add("domain:$it") }
    }.distinct()

    /** Turns the user's own list (one host per line, or comma separated) into routing rules. */
    fun userRules(raw: String): List<String> = raw
        .split('\n', ',', ';', ' ')
        .map { it.trim().lowercase().removePrefix("https://").removePrefix("http://").substringBefore('/') }
        .filter { host ->
            host.isNotEmpty() && host.length <= 253 && '.' in host &&
                host.all { it.isLetterOrDigit() || it == '.' || it == '-' }
        }
        .distinct()
        .take(500)
        .map { "domain:$it" }
}
