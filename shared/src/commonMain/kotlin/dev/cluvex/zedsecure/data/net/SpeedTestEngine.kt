package dev.cluvex.zedsecure.data.net

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlin.concurrent.Volatile
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import kotlin.time.TimeSource

enum class SpeedTestPhase { Idle, Ping, Download, Upload, Done, Stopped, Error }

data class SpeedSample(val mbps: Double)

enum class SpeedGrade { Bad, Poor, Average, Good, Great }

data class SpeedScores(val streaming: SpeedGrade?, val gaming: SpeedGrade?, val rtc: SpeedGrade?)

data class SpeedMeta(
    val clientIp: String?,
    val network: String?,
    val asn: Long?,
    val colo: String?,
    val coloCity: String?,
    val country: String?,
    val city: String?,
)

data class SpeedStep(val upload: Boolean, val bytes: Long, val done: Int, val count: Int)

data class SpeedTestState(
    val phase: SpeedTestPhase = SpeedTestPhase.Idle,
    val pingMs: Double? = null,
    val jitterMs: Double? = null,
    val loadedPingMs: Double? = null,
    val downLoadedPingMs: Double? = null,
    val upLoadedPingMs: Double? = null,
    val downLoadedJitterMs: Double? = null,
    val upLoadedJitterMs: Double? = null,
    val downloadMbps: Double? = null,
    val uploadMbps: Double? = null,
    val liveMbps: Double = 0.0,
    val progress: Float = 0f,
    val downSamples: List<SpeedSample> = emptyList(),
    val upSamples: List<SpeedSample> = emptyList(),
    val bytesUsed: Long = 0L,
    val step: SpeedStep? = null,
    val meta: SpeedMeta? = null,
    val scores: SpeedScores? = null,
    val elapsedMs: Long = 0L,
    val error: String? = null,
) {
    val bufferbloatMs: Double?
        get() {
            val idle = pingMs ?: return null
            val loaded = loadedPingMs ?: return null
            return max(0.0, loaded - idle)
        }

    val running: Boolean
        get() = phase == SpeedTestPhase.Ping ||
            phase == SpeedTestPhase.Download ||
            phase == SpeedTestPhase.Upload

    val hasResults: Boolean
        get() = pingMs != null || downloadMbps != null || uploadMbps != null
}

sealed interface Measurement {
    data class Latency(val count: Int) : Measurement
    data class Bandwidth(val upload: Boolean, val bytes: Long, val count: Int, val bypassFinishRule: Boolean = false) : Measurement
}

class SpeedTestEngine(
    private val socksPort: Int?,
    private val dataLimitBytes: Long? = null,
    private val host: String = HOST,
) {
    private val _state = MutableStateFlow(SpeedTestState())
    val state: StateFlow<SpeedTestState> = _state.asStateFlow()

    @Volatile
    private var stopped = false

    fun cancel() {
        stopped = true
    }

    private class Sample(val bytes: Long, val durationMs: Double, val bps: Double)

    private class SetResult(val upload: Boolean, val bytes: Long) {
        val samples = ArrayList<Sample>()
        val side = ArrayList<Double>()
    }

    private val idlePings = ArrayList<Double>()
    private val sets = ArrayList<SetResult>()
    private var spent = 0L
    private var downFinished = false
    private var upFinished = false
    private var measId = ""

    suspend fun run() {
        stopped = false
        idlePings.clear()
        sets.clear()
        spent = 0L
        downFinished = false
        upFinished = false
        measId = (1..16).map { Random.nextInt(10) }.joinToString("")
        val started = TimeSource.Monotonic.markNow()
        _state.value = SpeedTestState(phase = SpeedTestPhase.Ping)
        val link = openSpeedLink(host, socksPort)
        val probe = openSpeedLink(host, socksPort)
        val sideScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            fetchMeta(link)
            val plan = SCHEDULE
            val totalRequests = plan.sumOf { if (it is Measurement.Latency) it.count else (it as Measurement.Bandwidth).count }
            var doneRequests = 0
            for (measurement in plan) {
                if (stopped) break
                when (measurement) {
                    is Measurement.Latency -> {
                        repeat(measurement.count) {
                            if (stopped) return@repeat
                            val ping = latency(link, "idle", count = true)
                            if (ping != null) idlePings += ping
                            doneRequests++
                            _state.update {
                                it.copy(
                                    pingMs = idlePings.takeIf { p -> p.isNotEmpty() }?.let { p -> SpeedTestMath.percentile(p, LATENCY_PERCENTILE) },
                                    jitterMs = SpeedTestMath.jitter(idlePings),
                                    liveMbps = if (it.phase == SpeedTestPhase.Ping) ping ?: it.liveMbps else it.liveMbps,
                                    progress = doneRequests / totalRequests.toFloat(),
                                    elapsedMs = started.elapsedNow().inWholeMilliseconds,
                                )
                            }
                        }
                        if (idlePings.isEmpty() && !stopped) throw IllegalStateException("No latency response")
                    }
                    is Measurement.Bandwidth -> {
                        val finished = if (measurement.upload) upFinished else downFinished
                        if (finished) {
                            doneRequests += measurement.count
                            continue
                        }
                        if (!measurement.upload && measurement.bytes > MAX_DOWNLOAD_BYTES) {
                            downFinished = true
                            doneRequests += measurement.count
                            continue
                        }
                        runSet(link, probe, sideScope, measurement) { doneRequests++; doneRequests / totalRequests.toFloat() }
                    }
                }
            }
            if (stopped) {
                _state.update { it.copy(phase = SpeedTestPhase.Stopped, liveMbps = 0.0, step = null, elapsedMs = started.elapsedNow().inWholeMilliseconds) }
            } else {
                _state.update {
                    it.copy(
                        phase = SpeedTestPhase.Done,
                        liveMbps = 0.0,
                        progress = 1f,
                        step = null,
                        scores = scoresOf(it),
                        elapsedMs = started.elapsedNow().inWholeMilliseconds,
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _state.update {
                it.copy(
                    phase = if (stopped) SpeedTestPhase.Stopped else SpeedTestPhase.Error,
                    liveMbps = 0.0,
                    step = null,
                    error = if (stopped) null else e.message ?: "Speed test failed",
                    scores = if (it.hasResults) scoresOf(it) else null,
                    elapsedMs = started.elapsedNow().inWholeMilliseconds,
                )
            }
        } finally {
            sideScope.cancel()
            link.close()
            probe.close()
        }
    }

    private suspend fun fetchMeta(link: SpeedLink) {
        val response = runCatching { link.get("/meta", keepBody = true) }.getOrNull() ?: return
        spent += response.transferBytes
        val root = runCatching { Json.parseToJsonElement(response.body.orEmpty()).jsonObject }.getOrNull() ?: return
        fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        val colo = root["colo"] as? JsonObject
        _state.update {
            it.copy(
                bytesUsed = spent,
                meta = SpeedMeta(
                    clientIp = root.text("clientIp"),
                    network = root.text("asOrganization"),
                    asn = root.text("asn")?.toLongOrNull(),
                    colo = colo?.text("iata") ?: root.text("colo"),
                    coloCity = colo?.text("city"),
                    country = root.text("country"),
                    city = root.text("city"),
                ),
            )
        }
    }

    private suspend fun latency(link: SpeedLink, during: String, count: Boolean): Double? {
        val response = runCatching { link.get("/__down?bytes=0&measId=$measId&during=$during") }.getOrNull() ?: return null
        if (count) spent += response.transferBytes
        if (response.status !in 200..299) return null
        return pingOf(response)
    }

    private fun pingOf(response: SpeedResponse): Double {
        val server = SpeedTestMath.serverTimeMs(response.serverTiming) ?: 0.0
        return max(0.0, response.ttfbMs - server)
    }

    private suspend fun runSet(
        link: SpeedLink,
        probe: SpeedLink,
        sideScope: CoroutineScope,
        m: Measurement.Bandwidth,
        tick: () -> Float,
    ) {
        val set = SetResult(m.upload, m.bytes).also { sets += it }
        _state.update {
            it.copy(
                phase = if (m.upload) SpeedTestPhase.Upload else SpeedTestPhase.Download,
                liveMbps = 0.0,
                step = SpeedStep(m.upload, m.bytes, 0, m.count),
            )
        }
        val side: Job = sideScope.launch {
            delay(SIDE_START_MS)
            val during = if (m.upload) "upload" else "download"
            while (isActive && !stopped) {
                val ping = latency(probe, during, count = false)
                if (ping != null) synchronized(set.side) { set.side += ping }
                delay(LOADED_THROTTLE_MS)
            }
        }
        var minDuration = Double.POSITIVE_INFINITY
        try {
            for (i in 0 until m.count) {
                if (stopped) break
                val limit = dataLimitBytes
                if (limit != null && spent + m.bytes > limit) {
                    if (m.upload) upFinished = true else downFinished = true
                    break
                }
                val sample = request(link, m)
                if (if (m.upload) upFinished else downFinished) break
                if (sample == null) continue
                set.samples += sample
                minDuration = min(minDuration, sample.durationMs)
                publish(m, set, i + 1, tick())
            }
        } finally {
            side.cancelAndJoin()
        }
        if (!m.bypassFinishRule && set.samples.isNotEmpty() && minDuration > FINISH_REQUEST_MS) {
            if (m.upload) upFinished = true else downFinished = true
        }
        publishLoaded()
    }

    private suspend fun request(link: SpeedLink, m: Measurement.Bandwidth): Sample? {
        var moved = 0L
        var tick = TimeSource.Monotonic.markNow()
        val onBytes: (Long) -> Boolean = { delta ->
            moved += delta
            if (tick.elapsedNow().inWholeMilliseconds >= LIVE_TICK_MS) {
                tick = TimeSource.Monotonic.markNow()
                _state.update { it.copy(bytesUsed = spent + moved) }
            }
            !stopped
        }
        val response = try {
            if (m.upload) link.post("/__up?measId=$measId", m.bytes, onBytes)
            else link.get("/__down?bytes=${m.bytes}&measId=$measId", onBytes = onBytes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            spent += moved
            if (stopped) return null
            throw e
        }
        spent += if (m.upload) m.bytes + response.transferBytes else response.transferBytes
        if (response.status == 403 || response.status == 413) {
            if (m.upload) upFinished = true else downFinished = true
            return null
        }
        if (response.status !in 200..299) throw SpeedHttpException(response.status)
        val ping = pingOf(response)
        val durationMs = if (m.upload) response.ttfbMs else ping + response.payloadMs
        if (durationMs <= 0.0) return null
        val bits = if (m.upload) {
            8.0 * m.bytes * (1 + HEADER_FRACTION)
        } else {
            8.0 * (response.transferBytes.takeIf { it > 0 }?.toDouble() ?: (m.bytes * (1 + HEADER_FRACTION)))
        }
        val bps = bits / (durationMs / 1000.0)
        if (!bps.isFinite() || bps <= 0.0) return null
        return Sample(m.bytes, durationMs, bps)
    }

    private fun publish(m: Measurement.Bandwidth, set: SetResult, done: Int, progress: Float) {
        val direction = sets.filter { it.upload == m.upload }.flatMap { it.samples }
        val bandwidth = SpeedTestMath.percentile(
            direction.filter { it.durationMs >= MIN_REQUEST_MS }.map { it.bps },
            BANDWIDTH_PERCENTILE,
        ).takeIf { it > 0.0 }?.let { it / 1_000_000.0 }
        val graph = direction.map { SpeedSample(it.bps / 1_000_000.0) }
        val live = set.samples.lastOrNull()?.bps?.div(1_000_000.0) ?: 0.0
        _state.update {
            it.copy(
                liveMbps = live,
                progress = progress,
                bytesUsed = spent,
                step = SpeedStep(m.upload, m.bytes, done, m.count),
                downSamples = if (!m.upload) graph else it.downSamples,
                upSamples = if (m.upload) graph else it.upSamples,
                downloadMbps = if (!m.upload) bandwidth ?: it.downloadMbps else it.downloadMbps,
                uploadMbps = if (m.upload) bandwidth ?: it.uploadMbps else it.uploadMbps,
            )
        }
    }

    private fun loadedPoints(upload: Boolean): List<Double> =
        sets.filter { it.upload == upload && it.samples.isNotEmpty() && it.samples.minOf { s -> s.durationMs } >= LOADED_MIN_REQUEST_MS }
            .flatMap { synchronized(it.side) { it.side.toList() } }
            .takeLast(LOADED_MAX_POINTS)

    private fun publishLoaded() {
        val down = loadedPoints(false)
        val up = loadedPoints(true)
        _state.update {
            val downPing = down.takeIf { p -> p.isNotEmpty() }?.let { p -> SpeedTestMath.percentile(p, LATENCY_PERCENTILE) }
            val upPing = up.takeIf { p -> p.isNotEmpty() }?.let { p -> SpeedTestMath.percentile(p, LATENCY_PERCENTILE) }
            it.copy(
                downLoadedPingMs = downPing,
                upLoadedPingMs = upPing,
                downLoadedJitterMs = SpeedTestMath.jitter(down),
                upLoadedJitterMs = SpeedTestMath.jitter(up),
                loadedPingMs = listOfNotNull(downPing, upPing).maxOrNull(),
            )
        }
    }

    private fun scoresOf(s: SpeedTestState): SpeedScores {
        val points = HashMap<String, Int>()
        points["packetLoss"] = 0
        s.pingMs?.let { points["latency"] = SpeedTestMath.scale(it, LATENCY_POINTS_AT, LATENCY_POINTS) }
        s.jitterMs?.let { points["jitter"] = SpeedTestMath.scale(it, JITTER_POINTS_AT, JITTER_POINTS) }
        s.downloadMbps?.let { points["download"] = SpeedTestMath.scale(it * 1_000_000.0, BANDWIDTH_POINTS_AT, BANDWIDTH_POINTS) }
        s.uploadMbps?.let { points["upload"] = SpeedTestMath.scale(it * 1_000_000.0, BANDWIDTH_POINTS_AT, BANDWIDTH_POINTS) }
        val idle = s.pingMs
        val loaded = listOfNotNull(s.downLoadedPingMs, s.upLoadedPingMs).maxOrNull()
        if (idle != null && loaded != null) {
            points["loadedLatencyIncrease"] = SpeedTestMath.scale(loaded - idle, LATENCY_POINTS_AT, LATENCY_POINTS)
        }
        fun grade(inputs: List<String>, thresholds: DoubleArray): SpeedGrade? {
            if (!inputs.all { it in points }) return null
            val sum = max(0, inputs.sumOf { points.getValue(it) })
            return SpeedGrade.entries[SpeedTestMath.scale(sum.toDouble(), thresholds, intArrayOf(0, 1, 2, 3, 4))]
        }
        return SpeedScores(
            streaming = grade(listOf("latency", "packetLoss", "download", "loadedLatencyIncrease"), doubleArrayOf(15.0, 20.0, 40.0, 60.0)),
            gaming = grade(listOf("latency", "packetLoss", "loadedLatencyIncrease"), doubleArrayOf(5.0, 15.0, 25.0, 30.0)),
            rtc = grade(listOf("latency", "jitter", "packetLoss", "loadedLatencyIncrease"), doubleArrayOf(5.0, 15.0, 25.0, 40.0)),
        )
    }

    companion object {
        const val HOST = "speed.cloudflare.com"

        const val FINISH_REQUEST_MS = 1_000.0
        const val MIN_REQUEST_MS = 10.0
        const val BANDWIDTH_PERCENTILE = 0.9
        const val LATENCY_PERCENTILE = 0.5
        const val LOADED_THROTTLE_MS = 400L
        const val LOADED_MAX_POINTS = 20
        const val LOADED_MIN_REQUEST_MS = 250.0
        const val HEADER_FRACTION = 0.005
        const val MAX_DOWNLOAD_BYTES = 100_000_000L
        const val SAVER_LIMIT_BYTES = 150L * 1000 * 1000

        private const val SIDE_START_MS = 20L
        private const val LIVE_TICK_MS = 250L

        private val LATENCY_POINTS_AT = doubleArrayOf(10.0, 20.0, 50.0, 100.0, 500.0)
        private val LATENCY_POINTS = intArrayOf(20, 10, 5, 0, -10, -20)
        private val JITTER_POINTS_AT = doubleArrayOf(10.0, 20.0, 100.0, 500.0)
        private val JITTER_POINTS = intArrayOf(10, 5, 0, -10, -20)
        private val BANDWIDTH_POINTS_AT = doubleArrayOf(1e6, 10e6, 50e6, 100e6)
        private val BANDWIDTH_POINTS = intArrayOf(0, 5, 10, 20, 30)

        val SCHEDULE: List<Measurement> = listOf(
            Measurement.Latency(2),
            Measurement.Bandwidth(upload = false, bytes = 100_000, count = 1, bypassFinishRule = true),
            Measurement.Latency(20),
            Measurement.Bandwidth(upload = false, bytes = 100_000, count = 9),
            Measurement.Latency(2),
            Measurement.Bandwidth(upload = false, bytes = 1_000_000, count = 8),
            Measurement.Latency(2),
            Measurement.Bandwidth(upload = true, bytes = 100_000, count = 8),
            Measurement.Latency(2),
            Measurement.Bandwidth(upload = true, bytes = 1_000_000, count = 6),
            Measurement.Latency(2),
            Measurement.Bandwidth(upload = false, bytes = 10_000_000, count = 6),
            Measurement.Latency(2),
            Measurement.Bandwidth(upload = true, bytes = 10_000_000, count = 4),
            Measurement.Latency(2),
            Measurement.Bandwidth(upload = false, bytes = 25_000_000, count = 4),
            Measurement.Latency(2),
            Measurement.Bandwidth(upload = true, bytes = 25_000_000, count = 4),
            Measurement.Latency(2),
            Measurement.Bandwidth(upload = false, bytes = 100_000_000, count = 3),
            Measurement.Latency(2),
            Measurement.Bandwidth(upload = true, bytes = 50_000_000, count = 3),
            Measurement.Latency(2),
            Measurement.Bandwidth(upload = false, bytes = 250_000_000, count = 2),
        )
    }
}

val speedTestBudgetMb: Int
    get() = (SpeedTestEngine.SAVER_LIMIT_BYTES / 1_000_000).toInt()
