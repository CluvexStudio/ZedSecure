package dev.cluvex.zedsecure.scan

import dev.cluvex.zedsecure.domain.config.VpnProfile
import dev.cluvex.zedsecure.platform.currentTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

interface ScanBackend {
    fun start(configJson: String): Long

    fun poll(handle: Long, waitMs: Int): String?

    fun stop(handle: Long)

    fun free(handle: Long)

    fun countryGroups(geoipPath: String, codes: String): String? = null
}

@Serializable
data class TunnelProbe(
    val ok: Boolean,
    val setupMs: Int = 0,
    val totalMs: Int = 0,
    val error: String? = null,
)

interface TunnelTester {
    fun supports(profile: VpnProfile): Boolean

    suspend fun test(profile: VpnProfile, resolver: String, full: Boolean, url: String, timeoutMs: Int): TunnelProbe
}

data class E2ePlan(
    val tester: TunnelTester,
    val profile: VpnProfile,
    val full: Boolean,
    val url: String,
    val timeoutMs: Int,
    val concurrency: Int,
    val minScore: Int,
    val auto: Boolean,
)

@Serializable
data class ScanRequest(
    val targets: List<String> = emptyList(),
    val countries: List<String> = emptyList(),
    val geoipPath: String = "",
    val octets: List<Int> = emptyList(),
    val transport: String = "udp",
    val skip: Long = 0,
    val seed: Long = 0,
    val order: String = "smart",
    val limit: Long = 0,
    val port: Int = 53,
    val excludeSpecial: Boolean = true,
    val domain: String = "google.com",
    val rate: Int = 1500,
    val timeoutMs: Int = 2500,
    val retries: Int = 1,
    val adaptive: Boolean = true,
    val detectIntercept: Boolean = true,
    val neighbors: Boolean = false,
    val deep: Boolean = true,
    val tunnelDomain: String = "",
    val deepConcurrency: Int = 32,
    val deepTimeoutMs: Int = 2500,
    val latencySamples: Int = 5,
    val qnameLadder: List<Int> = listOf(64, 101, 150, 200, 253),
    val recordTypes: List<String> = listOf("TXT"),
    val checkEdns: Boolean = true,
    val checkNx: Boolean = true,
    val checkBurst: Boolean = true,
    val burstSize: Int = 16,
    val checkTcp: Boolean = true,
    val checkDot: Boolean = false,
    val blockedDomains: List<String> = emptyList(),
    val tcpConcurrency: Int = 256,
    val sourcePort: Int = 0,
    val capture: Boolean = false,
    val pcapPath: String = "",
    val fwmark: Int = 0,
    val stopAfter: Int = 0,
    val minScore: Int = 50,
    val maxResults: Int = 3000,
)

@Serializable
data class ScanProgress(
    val planned: Long = 0,
    val walked: Long = 0,
    val skipped: Long = 0,
    val sent: Long = 0,
    val done: Long = 0,
    val recv: Long = 0,
    val found: Long = 0,
    val refused: Long = 0,
    val servfail: Long = 0,
    val other: Long = 0,
    val timeouts: Long = 0,
    val closed: Long = 0,
    val unreachable: Long = 0,
    val hijacked: Long = 0,
    val intercepted: Long = 0,
    val late: Long = 0,
    val rate: Int = 0,
    val targetRate: Int = 0,
    val canaryLoss: Float = 0f,
    val elapsedMs: Long = 0,
    val etaMs: Long = 0,
    val sweepDone: Boolean = false,
    val deepQueued: Long = 0,
    val deepDone: Long = 0,
    val good: Long = 0,
    val tcpPlanned: Long = 0,
    val tcpDone: Long = 0,
    val tcpFound: Long = 0,
    val captured: Long = 0,
    val root: Boolean = false,
) {
    val fraction: Float
        get() {
            val total = planned + skipped
            return if (total > 0) ((done + skipped).toFloat() / total).coerceIn(0f, 1f) else 0f
        }
}

@Serializable
data class ResolverResult(
    val ip: String,
    val port: Int = 53,
    val score: Int = 0,
    val alive: Boolean = false,
    val rtt: Int = 0,
    val rttMin: Int = 0,
    val rttP90: Int = 0,
    val jitter: Int = 0,
    val loss: Float = 0f,
    val hijacked: Boolean = false,
    val via: String = "udp",
    val answeredBy: String? = null,
    val injected: Boolean? = null,
    val ttlUdp: Int? = null,
    val ttlTcp: Int? = null,
    val hops: Int? = null,
    val proxySuspect: Boolean? = null,
    val dupAnswers: Int = 0,
    val tunnel: Boolean? = null,
    val tunnelRcode: Int? = null,
    val maxQname: Int = 0,
    val typesOk: List<String> = emptyList(),
    val typesBad: List<String> = emptyList(),
    val edns: Boolean? = null,
    val maxReply: Int = 0,
    val truncates: Boolean = false,
    val nxOk: Boolean? = null,
    val censored: Boolean? = null,
    val burst: Float? = null,
    val tcp: Boolean? = null,
    val tcpRtt: Int? = null,
    val dot: Boolean? = null,
    val dotRtt: Int? = null,
    val e2e: TunnelProbe? = null,
) {
    val endpoint: String
        get() {
            val host = if (ip.contains(':')) "[$ip]" else ip
            return if (port == 53) ip else "$host:$port"
        }
}

@Serializable
data class ScanCheckpoint(
    val request: ScanRequest,
    val walked: Long,
    val planned: Long,
    val results: List<ResolverResult>,
    val savedAt: Long,
)

data class ScanState(
    val running: Boolean = false,
    val request: ScanRequest? = null,
    val progress: ScanProgress? = null,
    val results: List<ResolverResult> = emptyList(),
    val found: Long = 0,
    val interceptedBy: String? = null,
    val warnings: List<String> = emptyList(),
    val reason: String? = null,
    val error: String? = null,
    val startedAt: Long = 0,
    val restored: Boolean = false,
    val e2eQueued: Int = 0,
    val e2eDone: Int = 0,
    val e2ePassed: Int = 0,
)

object DnsScanHub {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
        explicitNulls = false
    }
    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state.asStateFlow()
    private var job: Job? = null

    @Volatile
    private var handle: Long = 0L

    @Volatile
    private var backend: ScanBackend? = null

    var onFinished: ((List<ResolverResult>) -> Unit)? = null
    var onCheckpoint: ((ScanCheckpoint?) -> Unit)? = null
    var onRunning: ((Boolean) -> Unit)? = null

    private val results = ConcurrentHashMap<String, ResolverResult>()
    private val probes = ConcurrentHashMap<String, TunnelProbe>()
    private var e2eQueue: Channel<String>? = null
    private val e2eQueued = AtomicInteger()
    private val e2eDone = AtomicInteger()
    private val e2eSeen = ConcurrentHashMap.newKeySet<String>()

    private val order = compareByDescending<ResolverResult> { it.score }.thenBy { it.rtt }

    private fun snapshot(): List<ResolverResult> =
        results.values.map { r -> probes[r.endpoint]?.let { r.copy(e2e = it) } ?: r }.sortedWith(order)

    val e2eRunning: Boolean get() = e2eQueued.get() > e2eDone.get()

    fun start(engine: ScanBackend, request: ScanRequest, previous: List<ResolverResult> = emptyList(), plan: E2ePlan? = null) {
        if (_state.value.running) return
        val seeded = if (request.seed == 0L) request.copy(seed = Random.nextLong(1, Long.MAX_VALUE)) else request
        val config = json.encodeToString(ScanRequest.serializer(), seeded)
        results.clear()
        probes.clear()
        e2eSeen.clear()
        e2eQueued.set(0)
        e2eDone.set(0)
        previous.forEach { r ->
            results[r.endpoint] = r
            r.e2e?.let { probes[r.endpoint] = it }
        }
        _state.value = ScanState(running = true, request = seeded, startedAt = currentTimeMillis(), results = snapshot())
        runCatching { onRunning?.invoke(true) }
        if (plan != null && plan.auto) openE2e(plan)
        job = scope.launch(Dispatchers.IO) {
            val h = try {
                engine.start(config)
            } catch (e: Throwable) {
                _state.update { it.copy(running = false, error = e.message ?: e.toString()) }
                runCatching { onRunning?.invoke(false) }
                return@launch
            }
            if (h == 0L) {
                _state.update { it.copy(running = false, error = "start failed") }
                runCatching { onRunning?.invoke(false) }
                return@launch
            }
            handle = h
            backend = engine
            var lastCheckpoint = currentTimeMillis()
            try {
                while (true) {
                    val batch = engine.poll(h, 300) ?: break
                    if (batch.length > 2) consume(batch, plan)
                    val now = currentTimeMillis()
                    if (now - lastCheckpoint > 10_000) {
                        lastCheckpoint = now
                        checkpoint(seeded)
                    }
                }
            } catch (e: Throwable) {
                _state.update { it.copy(error = e.message ?: e.toString()) }
            } finally {
                handle = 0L
                backend = null
                runCatching { engine.free(h) }
                _state.update { it.copy(running = false) }
                val reason = _state.value.reason
                if (reason == "complete" || reason == "enough") {
                    runCatching { onCheckpoint?.invoke(null) }
                } else {
                    checkpoint(seeded)
                }
                e2eQueue?.close()
                val finished = snapshot()
                if (finished.isNotEmpty()) runCatching { onFinished?.invoke(finished) }
                if (!e2eRunning) runCatching { onRunning?.invoke(false) }
            }
        }
    }

    private fun checkpoint(request: ScanRequest) {
        val p = _state.value.progress ?: return
        val walked = if (p.walked > 0) p.walked else p.tcpDone + request.skip
        if (walked <= request.skip && p.found == 0L) return
        runCatching {
            onCheckpoint?.invoke(
                ScanCheckpoint(
                    request = request,
                    walked = walked,
                    planned = p.planned + p.skipped,
                    results = snapshot().take(120),
                    savedAt = currentTimeMillis(),
                ),
            )
        }
    }

    fun stop() {
        val h = handle
        val b = backend
        if (h != 0L && b != null) runCatching { b.stop(h) }
        e2eQueue?.close()
    }

    fun clear() {
        if (_state.value.running) return
        results.clear()
        probes.clear()
        _state.value = ScanState()
    }

    fun restore(saved: String?) {
        if (saved.isNullOrBlank() || _state.value.running || _state.value.results.isNotEmpty()) return
        val list = runCatching { json.decodeFromString(ListSerializer(ResolverResult.serializer()), saved) }.getOrNull() ?: return
        if (list.isEmpty()) return
        results.clear()
        probes.clear()
        list.forEach { r ->
            results[r.endpoint] = r
            r.e2e?.let { probes[r.endpoint] = it }
        }
        _state.value = ScanState(results = snapshot(), restored = true)
    }

    fun encode(list: List<ResolverResult>): String =
        json.encodeToString(ListSerializer(ResolverResult.serializer()), list)

    fun encodeCheckpoint(cp: ScanCheckpoint): String = json.encodeToString(ScanCheckpoint.serializer(), cp)

    fun decodeCheckpoint(text: String?): ScanCheckpoint? =
        text?.let { runCatching { json.decodeFromString(ScanCheckpoint.serializer(), it) }.getOrNull() }

    fun runE2e(plan: E2ePlan, endpoints: List<String>) {
        if (endpoints.isEmpty()) return
        val queue = openE2e(plan)
        endpoints.forEach { queueE2e(queue, it) }
        if (!_state.value.running) queue.close()
        runCatching { onRunning?.invoke(true) }
    }

    private fun queueE2e(queue: Channel<String>, endpoint: String) {
        if (!e2eSeen.add(endpoint)) return
        if (queue.trySend(endpoint).isSuccess) {
            e2eQueued.incrementAndGet()
            publishE2e()
        }
    }

    private fun openE2e(plan: E2ePlan): Channel<String> {
        e2eQueue?.let { if (!it.isClosedForSend) return it }
        val queue = Channel<String>(Channel.UNLIMITED)
        e2eQueue = queue
        repeat(plan.concurrency.coerceIn(1, 10)) {
            scope.launch(Dispatchers.IO) {
                for (endpoint in queue) {
                    val probe = runCatching { plan.tester.test(plan.profile, endpoint, plan.full, plan.url, plan.timeoutMs) }
                        .getOrElse { TunnelProbe(ok = false, error = it.message ?: it.toString()) }
                    probes[endpoint] = probe
                    e2eDone.incrementAndGet()
                    publishE2e()
                }
                if (!e2eRunning && !_state.value.running) {
                    val finished = snapshot()
                    if (finished.isNotEmpty()) runCatching { onFinished?.invoke(finished) }
                    runCatching { onRunning?.invoke(false) }
                }
            }
        }
        return queue
    }

    private fun publishE2e() {
        _state.update { s ->
            s.copy(
                results = snapshot(),
                e2eQueued = e2eQueued.get(),
                e2eDone = e2eDone.get(),
                e2ePassed = probes.values.count { it.ok },
            )
        }
    }

    private fun consume(batch: String, plan: E2ePlan?) {
        val events = runCatching { json.parseToJsonElement(batch).jsonArray }.getOrNull() ?: return
        var progress: ScanProgress? = null
        var found = 0L
        var intercepted: String? = null
        val warnings = ArrayList<String>()
        var reason: String? = null
        var changed = false
        for (element in events) {
            val o = element as? JsonObject ?: continue
            when (o["t"]?.jsonPrimitive?.contentOrNull) {
                "progress" -> progress = runCatching { json.decodeFromJsonElement(ScanProgress.serializer(), o) }.getOrNull() ?: progress
                "found" -> found++
                "result" -> runCatching { json.decodeFromJsonElement(ResolverResult.serializer(), o) }.getOrNull()?.let { r ->
                    results[r.endpoint] = r
                    changed = true
                    if (plan != null && plan.auto && r.alive && r.score >= plan.minScore && r.tunnel != false) {
                        e2eQueue?.let { queueE2e(it, r.endpoint) }
                    }
                }
                "intercept" -> intercepted = o["by"]?.jsonPrimitive?.contentOrNull ?: intercepted
                "warning" -> o["msg"]?.jsonPrimitive?.contentOrNull?.let { warnings += it }
                "done" -> {
                    reason = o["reason"]?.jsonPrimitive?.contentOrNull
                    o["error"]?.jsonPrimitive?.contentOrNull?.let { warnings += it }
                    (o["progress"] as? JsonObject)?.let { p ->
                        progress = runCatching { json.decodeFromJsonElement(ScanProgress.serializer(), p) }.getOrNull() ?: progress
                    }
                }
            }
        }
        val sorted = if (changed) snapshot() else null
        _state.update { s ->
            s.copy(
                progress = progress ?: s.progress,
                results = sorted ?: s.results,
                found = s.found + found,
                interceptedBy = intercepted ?: s.interceptedBy,
                warnings = if (warnings.isEmpty()) s.warnings else (s.warnings + warnings).takeLast(10),
                reason = reason ?: s.reason,
                e2eQueued = e2eQueued.get(),
                e2eDone = e2eDone.get(),
            )
        }
    }
}

enum class ScanSpeed(val rate: Int) {
    Gentle(300),
    Normal(1500),
    Fast(5000),
    Max(20000),
}
