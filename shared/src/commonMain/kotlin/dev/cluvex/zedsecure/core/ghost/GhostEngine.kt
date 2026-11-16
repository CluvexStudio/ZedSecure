package dev.cluvex.zedsecure.core.ghost

import dev.cluvex.zedsecure.domain.model.GhostSettings
import dev.cluvex.zedsecure.platform.NetworkKind
import dev.cluvex.zedsecure.platform.httpGetResponse
import dev.cluvex.zedsecure.platform.Log
import dev.cluvex.zedsecure.platform.currentTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Pro GHOST engine – multiphase app-layer anti-DPI (zapret-inspired).
 * Lifecycle: starts on tunnel Connected; stops on disconnect.
 */
object GhostEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    @Volatile private var currentSettings: GhostSettings = GhostSettings()
    @Volatile private var tunnelConnected: Boolean = false
    @Volatile private var lastAutoPlan: GhostAutoTuner.Plan? = null
    @Volatile private var lastStrategy: String = "-"

    private val recentResults = ArrayDeque<Boolean>(64)

    @Volatile var totalCycles: Long = 0; private set
    @Volatile var totalRequests: Long = 0; private set
    @Volatile var successCount: Long = 0; private set
    @Volatile var failureCount: Long = 0; private set
    @Volatile var lastProfileUsed: String = "-"
    @Volatile var isRunning: Boolean = false; private set
    @Volatile var lastError: String? = null
    @Volatile var underPressure: Boolean = false; private set
    @Volatile var lastBurstAtMs: Long = 0; private set
    @Volatile var lastPhase: String = "-"

    data class DebugStatus(
        val running: Boolean,
        val enabled: Boolean,
        val tunnelConnected: Boolean,
        val autoMode: Boolean,
        val strategy: String,
        val networkLabel: String,
        val autoReason: String,
        val phase: String,
        val cycles: Long,
        val requests: Long,
        val successes: Long,
        val failures: Long,
        val lastProfile: String,
        val lastError: String?,
        val activeProfiles: String,
        val decoyVolumePercent: Int,
        val underPressure: Boolean,
        val burstMode: Boolean,
    )

    fun debugStatus(): DebugStatus {
        val effective = effectiveSettings()
        val plan = lastAutoPlan
        return DebugStatus(
            running = isRunning,
            enabled = currentSettings.enabled && effective.decoyEngineEnabled,
            tunnelConnected = tunnelConnected,
            autoMode = currentSettings.autoMode,
            strategy = lastStrategy.ifBlank { effective.strategy },
            networkLabel = plan?.label ?: networkLabel(GhostAutoTuner.currentNetwork()),
            autoReason = plan?.reason ?: "-",
            phase = lastPhase,
            cycles = totalCycles,
            requests = totalRequests,
            successes = successCount,
            failures = failureCount,
            lastProfile = lastProfileUsed,
            lastError = lastError,
            activeProfiles = effective.activeProfiles,
            decoyVolumePercent = effective.decoyVolumePercent,
            underPressure = underPressure,
            burstMode = effective.burstModeEnabled,
        )
    }

    private fun networkLabel(k: NetworkKind): String = when (k) {
        NetworkKind.Wifi -> "وای‌فای"
        NetworkKind.Cellular -> "داده همراه"
        NetworkKind.Ethernet -> "اترنت"
        NetworkKind.Unknown -> "نامشخص"
    }

    private fun effectiveSettings(): GhostSettings {
        val base = currentSettings
        if (!base.autoMode && GhostStrategy.fromRaw(base.strategy) == GhostStrategy.Auto) {
            val plan = GhostAutoTuner.plan(base.copy(autoMode = true), underPressure)
            lastAutoPlan = plan
            lastStrategy = plan.strategyName
            return plan.settings.copy(enabled = base.enabled, autoMode = base.autoMode)
        }
        if (base.autoMode) {
            val plan = GhostAutoTuner.plan(base, underPressure)
            lastAutoPlan = plan
            lastStrategy = plan.strategyName
            return plan.settings.copy(enabled = base.enabled, autoMode = true)
        }
        lastStrategy = GhostStrategy.fromRaw(base.strategy).name
        return base
    }

    fun updateSettings(settings: GhostSettings) {
        currentSettings = settings
        reconcile()
    }

    fun onTunnelConnected() {
        tunnelConnected = true
        Log.i("GhostEngine", "tunnel connected")
        reconcile()
    }

    fun onTunnelDisconnected() {
        tunnelConnected = false
        stop()
    }

    private fun reconcile() {
        val s = effectiveSettings()
        val shouldRun = currentSettings.enabled && s.decoyEngineEnabled && tunnelConnected
        if (shouldRun) start() else stop()
    }

    private fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            isRunning = true
            Log.i("GhostEngine", "GHOST multiphase started")
            while (isActive) {
                if (!currentSettings.enabled || !tunnelConnected) break
                val s = effectiveSettings()
                if (!s.decoyEngineEnabled) break
                try {
                    if (s.multiphaseEnabled) runMultiphaseCycle(s) else runSimpleCycle(s)
                    totalCycles++
                } catch (e: Exception) {
                    lastError = e.message
                    Log.w("GhostEngine", "cycle error: ${e.message}")
                }
                val base = s.decoyIntervalSeconds.coerceIn(4, 120)
                val jitter = Random.nextInt(0, (base * 0.75).toInt().coerceAtLeast(2))
                val pressureFactor = if (underPressure && s.adaptiveFallbackEnabled) 0.5 else 1.0
                delay(((base + jitter) * pressureFactor * 1000L).toLong())
            }
            isRunning = false
            lastPhase = "-"
        }
    }

    private fun stop() {
        job?.cancel()
        job = null
        isRunning = false
        lastPhase = "-"
    }

    private fun recordResult(ok: Boolean) {
        recentResults.addLast(ok)
        while (recentResults.size > 48) recentResults.removeFirst()
        val s = effectiveSettings()
        if (s.pressureDetectionEnabled && recentResults.size >= 6) {
            underPressure = recentResults.count { !it }.toFloat() / recentResults.size >= 0.28f
        } else {
            underPressure = false
        }
    }

    private suspend fun runMultiphaseCycle(settings: GhostSettings) {
        val profiles = resolveActiveProfiles(settings)
        if (profiles.isEmpty()) return
        if (settings.fakeFloodEnabled) {
            lastPhase = "P0-FakeFlood"
            val probes = profiles.flatMap { it.requests }.shuffled().take(
                if (settings.burstModeEnabled) settings.burstIntensity.coerceIn(1, 5) + 1 else 2
            )
            fireRequests(probes, settings, settings.maxParallelRequests)
            delay(Random.nextLong(80, 350))
        }
        lastPhase = "P1-MainMimic"
        runMainMimic(settings, profiles)
        lastPhase = "P2-Residual"
        val tail = profiles.shuffled().first().requests.let { list ->
            val ordered = if (settings.requestDisorder) list.shuffled() else list
            ordered.take(Random.nextInt(1, 3))
        }
        fireRequests(tail, settings, 1)
        lastPhase = "idle"
    }

    private suspend fun runSimpleCycle(settings: GhostSettings) {
        lastPhase = "simple"
        val profiles = resolveActiveProfiles(settings)
        if (profiles.isEmpty()) return
        runMainMimic(settings, profiles)
        lastPhase = "idle"
    }

    private suspend fun runMainMimic(settings: GhostSettings, profiles: List<AppProfile>) {
        var volumeBoost = 1.0f + (settings.decoyVolumePercent.coerceIn(10, 85) / 100f)
        if (settings.adaptiveFallbackEnabled && underPressure) volumeBoost *= 1.45f
        if (settings.burstModeEnabled) {
            volumeBoost *= (1.0f + settings.burstIntensity.coerceIn(1, 5) * 0.3f)
            lastBurstAtMs = currentTimeMillis()
        }
        val appCount = if (settings.multiAppConcurrent) {
            Random.nextInt(1, minOf(4, profiles.size) + 1)
        } else 1
        val selected = profiles.shuffled().take(appCount)
        for (profile in selected) {
            lastProfileUsed = profile.displayName
            var reqs = profile.requests.shuffled().take(
                (Random.nextInt(1, 5) * volumeBoost).toInt().coerceIn(1, 12)
            )
            if (settings.requestDisorder && Random.nextBoolean()) reqs = reqs.reversed()
            fireRequests(reqs, settings, settings.maxParallelRequests)
            delay(Random.nextLong(200, 1800))
        }
    }

    private suspend fun fireRequests(
        requests: List<DecoyRequest>,
        settings: GhostSettings,
        parallelOverride: Int,
    ) {
        val parallel = parallelOverride.coerceIn(1, 4)
        requests.chunked(parallel).forEach { chunk ->
            coroutineScope {
                chunk.map { req ->
                    async {
                        totalRequests++
                        try {
                            val ua = if (settings.appLayerMimicryEnabled) req.userAgent else DEFAULT_UA
                            val resp = httpGetResponse(
                                url = req.url,
                                userAgent = ua,
                                connectTimeoutMs = 7_000,
                                readTimeoutMs = 10_000,
                            )
                            val ok = resp.code in 200..499
                            if (ok) successCount++ else failureCount++
                            recordResult(ok)
                        } catch (e: Exception) {
                            failureCount++
                            lastError = e.message
                            recordResult(false)
                        }
                    }
                }.awaitAll()
            }
            delay(Random.nextLong(120, 900))
        }
    }

    private fun resolveActiveProfiles(settings: GhostSettings): List<AppProfile> {
        if (!settings.appProfilesEnabled) return listOf(GENERIC_BROWSE)
        val keys = settings.activeProfiles.split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
        return ALL_PROFILES.filter { it.key in keys }.ifEmpty { listOf(GENERIC_BROWSE) }
    }

    private data class DecoyRequest(val url: String, val userAgent: String)
    private data class AppProfile(val key: String, val displayName: String, val requests: List<DecoyRequest>)

    private val RUBIKA_UA = "Rubika/3.2.0 (Android 13; SM-A525F) okhttp/4.10.0"
    private val DIGIKALA_UA = "Digikala/5.18.0 (Linux; Android 13; Pixel 6) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"
    private val NESHAN_UA = "Neshan/6.4.1 (Android 12; Redmi Note 10) okhttp/4.9.3"
    private val SNAPP_UA = "Snapp/5.12.0 (Android 13; SM-G991B) okhttp/4.11.0"
    private val APARAT_UA = "Aparat/6.1.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"
    private val BALAD_UA = "Balad/4.8.0 (Android 13; SM-A536B)"
    private val TOROB_UA = "Torob/3.9.0 (Linux; Android 13) AppleWebKit/537.36"
    private val DEFAULT_UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private val RUBIKA = AppProfile("rubika", "روبیکا", listOf(
        DecoyRequest("https://rubika.ir/", RUBIKA_UA),
        DecoyRequest("https://web.rubika.ir/", RUBIKA_UA),
        DecoyRequest("https://rubika.ir/static/js/main.js", RUBIKA_UA),
        DecoyRequest("https://rubika.ir/favicon.ico", RUBIKA_UA),
    ))
    private val DIGIKALA = AppProfile("digikala", "دیجیکالا", listOf(
        DecoyRequest("https://www.digikala.com/", DIGIKALA_UA),
        DecoyRequest("https://www.digikala.com/search/", DIGIKALA_UA),
        DecoyRequest("https://api.digikala.com/v1/categories/", DIGIKALA_UA),
        DecoyRequest("https://www.digikala.com/product/dkp-123456/", DIGIKALA_UA),
        DecoyRequest("https://www.digikala.com/marvel/", DIGIKALA_UA),
    ))
    private val NESHAN = AppProfile("neshan", "نشان", listOf(
        DecoyRequest("https://neshan.org/", NESHAN_UA),
        DecoyRequest("https://api.neshan.org/v1/search?term=tehran", NESHAN_UA),
        DecoyRequest("https://neshan.org/maps/", NESHAN_UA),
    ))
    private val SNAPP = AppProfile("snapp", "اسنپ", listOf(
        DecoyRequest("https://app.snapp.taxi/", SNAPP_UA),
        DecoyRequest("https://api.snapp.ir/", SNAPP_UA),
        DecoyRequest("https://snapp.ir/", SNAPP_UA),
    ))
    private val APARAT = AppProfile("aparat", "آپارات", listOf(
        DecoyRequest("https://www.aparat.com/", APARAT_UA),
        DecoyRequest("https://www.aparat.com/v/abc123", APARAT_UA),
    ))
    private val BALAD = AppProfile("balad", "بلد", listOf(
        DecoyRequest("https://balad.ir/", BALAD_UA),
        DecoyRequest("https://balad.ir/maps", BALAD_UA),
    ))
    private val TOROB = AppProfile("torob", "ترب", listOf(
        DecoyRequest("https://torob.com/", TOROB_UA),
        DecoyRequest("https://api.torob.com/", TOROB_UA),
    ))
    private val GENERIC_BROWSE = AppProfile("generic", "مرورگر", listOf(
        DecoyRequest("https://www.varzesh3.com/", DEFAULT_UA),
        DecoyRequest("https://www.hamshahrionline.ir/", DEFAULT_UA),
        DecoyRequest("https://www.zoomit.ir/", DEFAULT_UA),
        DecoyRequest("https://www.digiato.com/", DEFAULT_UA),
        DecoyRequest("https://www.khabaronline.ir/", DEFAULT_UA),
    ))

    private val ALL_PROFILES = listOf(
        RUBIKA, DIGIKALA, NESHAN, SNAPP, APARAT, BALAD, TOROB, GENERIC_BROWSE
    )
}
