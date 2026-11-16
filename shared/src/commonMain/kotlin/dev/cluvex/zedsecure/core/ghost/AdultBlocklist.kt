package dev.cluvex.zedsecure.core.ghost

/**
 * Second layer of GHOST "Anti-Porn": a built-in routing block list applied on top of the
 * DNS adult filter, so a site stays blocked even when an app bypasses the DNS resolver
 * (DoH inside the app, cached answers, hosts pinned by IP lookups).
 */
object AdultBlocklist {
    /** Substrings strong enough to block without false positives. */
    private val keywords = listOf(
        "porn", "xvideos", "xnxx", "xhamster", "redtube", "youporn", "hentai", "brazzers",
        "onlyfans", "spankbang", "chaturbate", "stripchat", "bongacams", "camsoda",
    )

    private val domains = listOf(
        "pornhub.com", "pornhub.org", "phncdn.com", "xvideos.com", "xvideos-cdn.com", "xnxx.com", "xnxx-cdn.com",
        "xhamster.com", "xhcdn.com", "redtube.com", "youporn.com", "tube8.com", "spankbang.com", "eporner.com",
        "porntrex.com", "beeg.com", "motherless.com", "thumbzilla.com", "tnaflix.com", "txxx.com", "hclips.com",
        "fapello.com", "rule34.xxx", "e-hentai.org", "nhentai.net", "hanime.tv", "chaturbate.com", "stripchat.com",
        "bongacams.com", "livejasmin.com", "cam4.com", "camsoda.com", "myfreecams.com", "erome.com", "ixxx.com",
        "sex.com", "fuq.com", "porn.com", "naughtyamerica.com", "realitykings.com", "bangbros.com",
    )

    /** Rules in Xray routing syntax, ready to append to the custom block list. */
    val rules: List<String> = keywords.map { "keyword:$it" } + domains.map { "domain:$it" }
}
