package dev.cluvex.zedsecure.data.net

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

object SpeedTestMath {
    fun percentile(values: List<Double>, perc: Double = 0.5): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val idx = (sorted.size - 1) * perc
        val rem = idx - floor(idx)
        if (rem == 0.0) return sorted[idx.toInt()]
        val lo = sorted[floor(idx).toInt()]
        val hi = sorted[ceil(idx).toInt()]
        return lo + (hi - lo) * rem
    }

    fun median(values: List<Double>): Double = percentile(values, 0.5)

    fun jitter(values: List<Double>): Double? {
        if (values.size < 2) return null
        var sum = 0.0
        for (i in 1 until values.size) sum += abs(values[i] - values[i - 1])
        return sum / (values.size - 1)
    }

    fun serverTimeMs(header: String?): Double? {
        if (header.isNullOrBlank()) return null
        Regex("(?:^|,\\s*)cfReq(?:uest)?Dur(?:ation)?;\\s*dur=([0-9.]+)", RegexOption.IGNORE_CASE).find(header)
            ?.groupValues?.get(1)?.toDoubleOrNull()?.takeIf { it > SERVER_TIME_MIN }?.let { return it }
        val sum = Regex("(?:^|,\\s*)cfSpeed[a-zA-Z]*;\\s*dur=([0-9.]+)", RegexOption.IGNORE_CASE).findAll(header)
            .sumOf { it.groupValues[1].toDoubleOrNull() ?: 0.0 }
        return sum.takeIf { it > SERVER_TIME_MIN }
    }

    fun scale(value: Double, domain: DoubleArray, range: IntArray): Int {
        var i = 0
        while (i < domain.size && value >= domain[i]) i++
        return range[i]
    }

    private const val SERVER_TIME_MIN = 0.01

    fun bandwidthBps(
        samples: List<BandwidthSample>,
        perc: Double,
        minDurationMs: Double,
    ): Double? {
        val usable = samples
            .filter { it.durationMs >= minDurationMs && it.bps > 0.0 }
            .map { it.bps }
        if (usable.isEmpty()) return null
        return percentile(usable, perc)
    }
}

data class BandwidthSample(
    val bytes: Long,
    val durationMs: Double,
    val bps: Double,
    val ping: Double,
)
