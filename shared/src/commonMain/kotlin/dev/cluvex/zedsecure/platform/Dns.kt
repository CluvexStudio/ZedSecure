package dev.cluvex.zedsecure.platform

enum class DnsRecordType(val value: Int) {
    A(1),
    NS(2),
    CNAME(5),
    NULL(10),
    TXT(16),
    AAAA(28),
    SRV(33),
    MX(15),
    DNSKEY(48),
    CAA(257),
    ;

    companion object {
        fun of(name: String): DnsRecordType =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: TXT
    }
}

enum class DnsTransport { UDP, TCP, DOT, DOH }

enum class DnsFailure {
    Timeout,

    Refused,

    Malformed,

    Mismatch,

    Unreachable,
}

data class DnsReply(

    val rcode: Int,

    val truncated: Boolean,
    val answerCount: Int,

    val elapsedMs: Long,

    val sizeBytes: Int,

    val matchingAnswers: Int = 0,

    val txtBytes: Int = 0,

    val nsNames: List<String> = emptyList(),

    val address: String? = null,
)

sealed interface DnsResult {
    data class Ok(val reply: DnsReply) : DnsResult

    data class Failed(val reason: DnsFailure, val elapsedMs: Long = -1) : DnsResult

    val ok: Boolean get() = this is Ok

    val responded: Boolean
        get() = this is Ok || (this is Failed && reason == DnsFailure.Refused)
}

expect suspend fun dnsQuery(
    server: String,
    port: Int,
    name: String,
    type: DnsRecordType,
    transport: DnsTransport,
    timeoutMs: Int,
    ednsPayloadSize: Int = 0,
    dnssecOk: Boolean = false,
): DnsResult

fun paddedQueryName(base: String, totalLength: Int, random: () -> Char): String {
    val suffix = base.trim('.')
    val target = totalLength.coerceIn(suffix.length + 2, MAX_DNS_NAME)
    var needed = target - suffix.length - 1
    if (needed <= 0) return suffix
    val labels = ArrayList<String>(4)
    while (needed > 0) {
        val take = minOf(MAX_DNS_LABEL, needed)
        labels += buildString(take) { repeat(take) { append(random()) } }
        needed -= take + 1
    }
    return labels.joinToString(".") + "." + suffix
}

const val MAX_DNS_LABEL = 63

const val MAX_DNS_NAME = 253
