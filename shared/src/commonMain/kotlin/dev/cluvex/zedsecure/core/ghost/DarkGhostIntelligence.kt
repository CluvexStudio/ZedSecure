package dev.cluvex.zedsecure.core.ghost

import dev.cluvex.zedsecure.platform.Log
import dev.cluvex.zedsecure.platform.currentTimeMillis
import dev.cluvex.zedsecure.platform.httpGetResponse
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.roundToInt

/**
 * DARK GHOST – lightweight network intelligence (app-layer).
 *
 * Inspired by research themes (inspection, classification, adaptive anti-DPI),
 * implemented only with safe HTTPS probes to domestic-looking endpoints.
 *
 * Never labels ordinary failures as "confirmed DPI" without strong evidence.
 */
object DarkGhostIntelligence {

    enum class ConditionClass {
        Healthy,
        Congestion,
        ProbableFiltering,
        DnsOrReachabilityIssue,
        Unknown,
    }

    data class ProbeSample(
        val url: String,
        val ok: Boolean,
        val code: Int,
        val latencyMs: Long,
        val error: String? = null,
    )

    data class NetworkProfile(
        val classified: ConditionClass,
        val successRate: Float,
        val avgLatencyMs: Long,
        val sampleCount: Int,
        val underPressure: Boolean,
        val summaryFa: String,
        val measuredAtMs: Long,
    )

    @Volatile var lastProfile: NetworkProfile? = null
        private set

    private val recent = ArrayDeque<ProbeSample>(48)

    /** Harmless public endpoints commonly reachable in IR domestic path. */
    private val probeUrls = listOf(
        "https://www.digikala.com/",
        "https://rubika.ir/",
        "https://neshan.org/",
        "https://www.varzesh3.com/",
        "https://www.aparat.com/",
        "https://snapp.ir/",
    )

    suspend fun runProbeCycle(maxProbes: Int = 4): NetworkProfile {
        val picks = probeUrls.shuffled().take(maxProbes.coerceIn(2, 6))
        val samples = coroutineScope {
            picks.map { url ->
                async {
                    val t0 = currentTimeMillis()
                    try {
                        val resp = httpGetResponse(
                            url = url,
                            userAgent = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36",
                            connectTimeoutMs = 6_000,
                            readTimeoutMs = 8_000,
                        )
                        val ms = (currentTimeMillis() - t0).coerceAtLeast(0)
                        ProbeSample(url, resp.code in 200..499, resp.code, ms)
                    } catch (e: Exception) {
                        val ms = (currentTimeMillis() - t0).coerceAtLeast(0)
                        ProbeSample(url, false, -1, ms, e.message)
                    }
                }
            }.awaitAll()
        }

        samples.forEach {
            recent.addLast(it)
            while (recent.size > 40) recent.removeFirst()
        }

        val window = recent.toList()
        val okRate = if (window.isEmpty()) 0f else window.count { it.ok }.toFloat() / window.size
        val avgLat = if (window.isEmpty()) 0L else window.map { it.latencyMs }.average().roundToInt().toLong()

        val classified = classify(okRate, avgLat, window)
        val pressure = classified == ConditionClass.ProbableFiltering ||
            classified == ConditionClass.Congestion && okRate < 0.55f

        val summary = when (classified) {
            ConditionClass.Healthy ->
                "شرایط پایدار · موفقیت ${(okRate * 100).roundToInt()}% · تأخیر ${avgLat}ms"
            ConditionClass.Congestion ->
                "احتمال ازدحام/تأخیر بالا · موفقیت ${(okRate * 100).roundToInt()}% · ${avgLat}ms"
            ConditionClass.ProbableFiltering ->
                "الگوی مشکوک به فیلتر/تداخل (احتمالی، نه قطعی DPI) · ${(okRate * 100).roundToInt()}%"
            ConditionClass.DnsOrReachabilityIssue ->
                "مشکل دسترسی/DNS محتمل · موفقیت ${(okRate * 100).roundToInt()}%"
            ConditionClass.Unknown ->
                "داده ناکافی برای طبقه‌بندی"
        }

        val profile = NetworkProfile(
            classified = classified,
            successRate = okRate,
            avgLatencyMs = avgLat,
            sampleCount = window.size,
            underPressure = pressure,
            summaryFa = summary,
            measuredAtMs = currentTimeMillis(),
        )
        lastProfile = profile
        Log.i("DarkGhostIntel", summary)
        return profile
    }

    private fun classify(okRate: Float, avgLat: Long, window: List<ProbeSample>): ConditionClass {
        if (window.size < 3) return ConditionClass.Unknown
        val allFail = window.takeLast(6).all { !it.ok }
        if (allFail && window.size >= 6) return ConditionClass.DnsOrReachabilityIssue
        if (okRate >= 0.75f && avgLat < 1200) return ConditionClass.Healthy
        if (okRate >= 0.55f && avgLat >= 1200) return ConditionClass.Congestion
        if (okRate < 0.45f && avgLat < 800) {
            // Fast failures can indicate filtering; still not "confirmed DPI".
            return ConditionClass.ProbableFiltering
        }
        if (okRate < 0.55f) return ConditionClass.Congestion
        return ConditionClass.Unknown
    }

    fun reset() {
        recent.clear()
        lastProfile = null
    }
}
