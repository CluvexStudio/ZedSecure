// Generated from the Android AdultBlocklist.kt - keep both in sync.
import Foundation

enum AdultBlocklist {
    static let keywords: [String] = [
        "xvideos",
        "xnxx",
        "xhamster",
        "redtube",
        "youporn",
        "hentai",
        "brazzers",
        "onlyfans",
        "spankbang",
        "chaturbate",
        "stripchat",
        "bongacams",
        "camsoda",
        "pornhub",
        "eporner",
        "porntrex",
    ]

    static let domains: [String] = [
        "pornhub.com",
        "pornhub.org",
        "phncdn.com",
        "xvideos.com",
        "xvideos-cdn.com",
        "xnxx.com",
        "xnxx-cdn.com",
        "xhamster.com",
        "xhcdn.com",
        "redtube.com",
        "youporn.com",
        "tube8.com",
        "spankbang.com",
        "eporner.com",
        "porntrex.com",
        "beeg.com",
        "motherless.com",
        "thumbzilla.com",
        "tnaflix.com",
        "txxx.com",
        "hclips.com",
        "fapello.com",
        "rule34.xxx",
        "e-hentai.org",
        "nhentai.net",
        "hanime.tv",
        "chaturbate.com",
        "stripchat.com",
        "bongacams.com",
        "livejasmin.com",
        "cam4.com",
        "camsoda.com",
        "myfreecams.com",
        "erome.com",
        "ixxx.com",
        "sex.com",
        "fuq.com",
        "porn.com",
        "naughtyamerica.com",
        "realitykings.com",
        "bangbros.com",
        "redgifs.com",
        "4tube.com",
        "empflix.com",
        "drtuber.com",
        "slutload.com",
        "keezmovies.com",
        "vporn.com",
        "youjizz.com",
        "javhd.com",
        "javmost.com",
        "jav.guru",
        "javbus.com",
        "gelbooru.com",
        "danbooru.donmai.us",
        "rule34.paheal.net",
        "pornhubpremium.com",
        "pornhubpremium.net",
        "xhamsterlive.com",
        "xhamster.desi",
        "xnxx.tv",
        "xnxx.health",
        "xvideos2.com",
        "xvideos3.com",
        "adulttime.com",
        "teamskeet.com",
        "mofos.com",
        "digitalplayground.com",
        "wicked.com",
        "vixen.com",
        "deeper.com",
        "blacked.com",
        "tushy.com",
        "bellesa.co",
        "manyvids.com",
    ]

    static let moreDomains: [String] = [
        "xhamster2.com",
        "xhamster3.com",
        "pornone.com",
        "pornhd.com",
        "pornhat.com",
        "porndig.com",
        "porn300.com",
        "sxyprn.com",
        "daftsex.com",
        "hqporner.com",
        "nuvid.com",
        "sunporno.com",
        "tube8.com",
        "spankwire.com",
        "extremetube.com",
        "gotporn.com",
        "analdin.com",
        "pornpics.com",
        "imagefap.com",
        "luscious.net",
        "hentai2read.com",
        "hitomi.la",
        "e621.net",
        "fansly.com",
        "clips4sale.com",
        "iwara.tv",
        "missav.com",
        "supjav.com",
        "jable.tv",
        "flirt4free.com",
        "imlive.com",
        "streamate.com",
        "xlovecam.com",
        "jerkmate.com",
        "adultfriendfinder.com",
        "nubiles.net",
        "fakehub.com",
        "evilangel.com",
        "hotmovies.com",
        "adultempire.com",
    ]

    static let dohBypass: [String] = [
        "dns.google",
        "dns.google.com",
        "cloudflare-dns.com",
        "mozilla.cloudflare-dns.com",
        "chrome.cloudflare-dns.com",
        "one.one.one.one",
        "dns.quad9.net",
        "doh.opendns.com",
        "dns.nextdns.io",
        "doh.dns.sb",
        "dns.adguard.com",
        "dns.adguard-dns.com",
    ]

    /// Routing rules in the same `keyword:` / `domain:` form the Android core uses.
    static var rules: [String] {
        var all: [String] = []
        all += keywords.map { "keyword:" + $0 }
        all += (domains + moreDomains + dohBypass).map { "domain:" + $0 }
        var seen = Set<String>()
        return all.filter { seen.insert($0).inserted }
    }

    /// The user's own list (one host per line, or separated by commas/semicolons/spaces), max 500.
    static func userRules(_ raw: String) -> [String] {
        let separators = CharacterSet(charactersIn: "\n,; ")
        var seen = Set<String>()
        var result: [String] = []
        for part in raw.components(separatedBy: separators) {
            var host = part.trimmingCharacters(in: .whitespaces).lowercased()
            if host.hasPrefix("https://") { host.removeFirst(8) }
            if host.hasPrefix("http://") { host.removeFirst(7) }
            if let slash = host.firstIndex(of: "/") { host = String(host[..<slash]) }
            let valid = !host.isEmpty && host.count <= 253 && host.contains(".") &&
                host.allSatisfy { $0.isLetter || $0.isNumber || $0 == "." || $0 == "-" }
            if valid && seen.insert(host).inserted { result.append("domain:" + host) }
            if result.count >= 500 { break }
        }
        return result
    }
}
