package dev.cluvex.zedsecure.core

import dev.cluvex.zedsecure.core.AppLog as Log
import dev.cluvex.zedsecure.platform.DnsRecordType
import dev.cluvex.zedsecure.platform.DnsResult
import dev.cluvex.zedsecure.platform.DnsTransport
import dev.cluvex.zedsecure.platform.dnsQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.random.Random

object DnsProbe {
    private const val TAG = "DnsProbe"

    private const val CONCURRENCY = 24

    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

    fun fastest(
        candidates: List<String>,
        count: Int = 3,
        timeoutMs: Int = 1500,
        tunnelDomain: String = "",

        fullVerification: Boolean = false,
    ): List<String> {
        val samples = if (fullVerification) 3 else 1
        val hosts = candidates.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (hosts.isEmpty()) return emptyList()
        val domain = tunnelDomain.trim().trim('.').ifBlank { "google.com" }

        val ranked = runBlocking(Dispatchers.IO) {
            hosts.chunked(CONCURRENCY).flatMap { group ->
                group.map { entry ->
                    async {
                        val (host, port) = splitHostPort(entry)
                        val times = ArrayList<Long>(samples)
                        repeat(samples) {
                            val result = dnsQuery(
                                server = host,
                                port = port,

                                name = "${randomLabel(8)}.$domain",
                                type = DnsRecordType.A,
                                transport = DnsTransport.UDP,
                                timeoutMs = timeoutMs,
                            )

                            when (result) {
                                is DnsResult.Ok -> times += result.reply.elapsedMs
                                is DnsResult.Failed -> result.elapsedMs.takeIf { it >= 0 }
                                    ?.let { times += it }
                            }
                        }

                        median(times)?.let { entry to it }
                    }
                }.awaitAll()
            }.filterNotNull().sortedBy { it.second }
        }
        if (ranked.isEmpty()) Log.w(TAG, "no resolver in the pool answered")
        return ranked.take(count).map { it.first }
    }

    private fun median(values: List<Long>): Long? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle]
        else (sorted[middle - 1] + sorted[middle]) / 2
    }

    private fun randomLabel(length: Int): String =
        buildString(length) { repeat(length) { append(ALPHABET[Random.nextInt(ALPHABET.length)]) } }

    private fun splitHostPort(value: String): Pair<String, Int> {
        if (value.startsWith("[")) {
            val close = value.indexOf(']')
            if (close > 0) {
                return value.substring(1, close) to
                    (value.substring(close + 1).removePrefix(":").toIntOrNull() ?: 53)
            }
        }
        val colon = value.lastIndexOf(':')

        if (colon > 0 && value.indexOf(':') == colon) {
            value.substring(colon + 1).toIntOrNull()?.takeIf { it in 1..65535 }
                ?.let { return value.substring(0, colon) to it }
        }
        return value to 53
    }
}
