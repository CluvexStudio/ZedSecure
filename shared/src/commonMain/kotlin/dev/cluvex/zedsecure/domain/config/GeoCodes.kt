package dev.cluvex.zedsecure.domain.config

import dev.cluvex.zedsecure.domain.model.RulesetItem

data class GeoCodes(val site: Set<String>, val ip: Set<String>) {
    fun hasSite(code: String): Boolean = code.uppercase() in site

    fun hasIp(code: String): Boolean = code.uppercase() in ip

    fun allowsDomain(entry: String): Boolean {
        val e = entry.trim()
        if (!e.startsWith("geosite:")) return true
        return hasSite(e.removePrefix("geosite:").substringBefore('@').trim())
    }

    fun allowsIp(entry: String): Boolean {
        val bare = entry.trim().trimStart('!')
        if (!bare.startsWith("geoip:")) return true
        return hasIp(bare.removePrefix("geoip:").trimStart('!').trim())
    }

    fun missingIn(rulesets: List<RulesetItem>): List<String> = rulesets
        .filter { it.enabled }
        .flatMap { r -> r.domain.filterNot { allowsDomain(it) } + r.ip.filterNot { allowsIp(it) } }
        .map { it.trim() }
        .distinct()
}
