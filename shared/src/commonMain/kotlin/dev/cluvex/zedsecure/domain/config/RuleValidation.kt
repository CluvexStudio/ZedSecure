package dev.cluvex.zedsecure.domain.config

object RuleValidation {
    private val DOMAIN_GEO_PREFIXES = listOf("geosite:", "ext:", "ext-domain:", "ext-site:")

    private val IP_GEO_PREFIXES = listOf("geoip:", "ext:", "ext-ip:")

    private val DOMAIN_LITERAL_PREFIXES =
        listOf("regexp:", "domain:", "full:", "keyword:", "dotless:")

    fun isGeoDomain(entry: String): Boolean = DOMAIN_GEO_PREFIXES.any { entry.startsWith(it) }

    fun isGeoIp(entry: String): Boolean {
        val bare = entry.trimStart('!')
        return IP_GEO_PREFIXES.any { bare.startsWith(it) }
    }

    enum class Problem {
        GeoIpInDomainField,

        GeoSiteInIpField,

        NegationInDomainField,

        DotlessWithDot,

        NotAnAddress,

        BadRegex,
    }

    fun checkDomainEntry(entry: String): Problem? {
        val e = entry.trim()
        if (e.isEmpty()) return null
        if (e.startsWith("!")) return Problem.NegationInDomainField
        if (e.startsWith("geoip:") || e.startsWith("ext-ip:")) return Problem.GeoIpInDomainField
        if (e.startsWith("dotless:")) {
            val substr = e.removePrefix("dotless:")
            if (substr.contains(".")) return Problem.DotlessWithDot
        }
        if (e.startsWith("regexp:")) {
            val pattern = e.removePrefix("regexp:")
            val ok = runCatching { Regex(pattern) }.isSuccess
            if (!ok) return Problem.BadRegex
        }
        return null
    }

    fun checkIpEntry(entry: String): Problem? {
        val e = entry.trim()
        if (e.isEmpty()) return null
        val bare = e.trimStart('!')
        if (bare.startsWith("geosite:") || bare.startsWith("ext-site:") || bare.startsWith("ext-domain:")) {
            return Problem.GeoSiteInIpField
        }
        if (IP_GEO_PREFIXES.any { bare.startsWith(it) }) return null
        if (bare.startsWith("domain:") || bare.startsWith("full:") || bare.startsWith("regexp:")) {
            return Problem.NotAnAddress
        }
        return if (isAddressOrCidr(bare)) null else Problem.NotAnAddress
    }

    fun isAddressOrCidr(value: String): Boolean {
        val (addr, maskPart) = value.split("/", limit = 2).let {
            it[0] to it.getOrNull(1)
        }
        if (maskPart != null) {
            val mask = maskPart.toIntOrNull() ?: return false
            val max = if (addr.contains(":")) 128 else 32
            if (mask !in 0..max) return false
        }
        if (addr.contains(":")) {
            return addr.isNotEmpty() && addr.all { it.isDigit() || it in "abcdefABCDEF:." }
        }
        val parts = addr.split(".")
        if (parts.size != 4) return false
        return parts.all { p -> p.isNotEmpty() && p.length <= 3 && p.all(Char::isDigit) && p.toInt() in 0..255 }
    }

    fun isValidPort(port: String): Boolean {
        val p = port.trim()
        if (p.isEmpty()) return true
        return p.split(",").all { part ->
            val t = part.trim()
            when {
                t.isEmpty() -> false
                t.startsWith("env:") -> true
                t.contains("-") -> {
                    val (from, to) = t.split("-", limit = 2)
                    val f = from.trim().toIntOrNull()
                    val e = to.trim().toIntOrNull()
                    f != null && e != null && f in 0..65535 && e in 0..65535 && f <= e
                }
                else -> t.toIntOrNull()?.let { it in 0..65535 } == true
            }
        }
    }

    fun usableDomains(domains: List<String>, geoAssetsAvailable: Boolean): List<String> =
        domains.filter { e ->
            checkDomainEntry(e) == null && (geoAssetsAvailable || !isGeoDomain(e))
        }

    fun usableIps(ips: List<String>, geoAssetsAvailable: Boolean): List<String> =
        ips.filter { e ->
            checkIpEntry(e) == null && (geoAssetsAvailable || !isGeoIp(e))
        }
}
