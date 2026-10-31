package dev.cluvex.zedsecure.domain.config

import dev.cluvex.zedsecure.platform.DnsRecordType
import dev.cluvex.zedsecure.platform.DnsResult
import dev.cluvex.zedsecure.platform.DnsTransport
import dev.cluvex.zedsecure.platform.dnsQuery

object DnsGuard {
    private val RESOLVERS = listOf(
        "https://1.1.1.1/dns-query",
        "https://8.8.8.8/dns-query",
        "https://9.9.9.9/dns-query",
        "https://94.140.14.14/dns-query",
    )

    fun isPoisoned(ip: String): Boolean {
        val address = ip.trim().substringBefore('%')
        if (address.isEmpty()) return true
        if (':' in address) {
            val lower = address.lowercase()
            return lower == "::" || lower == "::1" || lower == "0:0:0:0:0:0:0:0" || lower == "0:0:0:0:0:0:0:1"
        }
        val parts = address.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 4) return false
        return (parts[0] == 10 && parts[1] == 10 && parts[2] == 34) || parts[0] == 0 || parts[0] == 127
    }

    fun clean(addresses: List<String>): List<String> = addresses.filterNot(::isPoisoned)

    suspend fun lookup(host: String, timeoutMs: Int = 1_500): String? {
        for (url in RESOLVERS) {
            val reply = (dnsQuery(url, 443, host, DnsRecordType.A, DnsTransport.DOH, timeoutMs) as? DnsResult.Ok)?.reply ?: continue
            val address = reply.address?.trim()?.takeIf { it.isNotEmpty() && !isPoisoned(it) && XrayJsonBuilder.isIpLiteral(it) }
            if (address != null) return address
        }
        return null
    }
}
