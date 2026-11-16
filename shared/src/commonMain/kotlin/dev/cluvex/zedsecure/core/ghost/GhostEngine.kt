package dev.cluvex.zedsecure.core.ghost

import dev.cluvex.zedsecure.domain.model.GhostSettings
import dev.cluvex.zedsecure.platform.httpGetResponse
import dev.cluvex.zedsecure.platform.Log
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
 * GHOST – helps the active tunnel with app-layer decoy traffic (anti-DPI noise).
 *
 * Lifecycle (important):
 * - User enables GHOST in settings → only stores preference, does NOT start.
 * - User presses Connect and tunnel becomes Connected → GHOST starts (if enabled).
 * - Tunnel disconnects / errors → GHOST stops immediately.
 *
 * Never touches Xray / sing-box / TUN / routing. Only best-effort HTTP decoys.
 */
object GhostEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    @Volatile private var currentSettings: GhostSettings = GhostSettings()
    @Volatile private var tunnelConnected: Boolean = false

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

    data class DebugStatus(
        val running: Boolean,
        val enabled: Boolean,
        val tunnelConnected: Boolean,
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
        val s = currentSettings
        return DebugStatus(
            running = isRunning,
            enabled = s.enabled && s.decoyEngineEnabled,
            tunnelConnected = tunnelConnected,
            cycles = totalCycles,
            requests = totalRequests,
            successes = successCount,
            failures = failureCount,
            lastProfile = lastProfileUsed,
            lastError = lastError,
            activeProfiles = s.activeProfiles,
            decoyVolumePercent = s.decoyVolumePercent,
            underPressure = underPressure,
            burstMode = s.burstModeEnabled,
        )
    }

    /** Only updates preferences. Does NOT start the engine by itself. */
    fun updateSettings(settings: GhostSettings) {
        currentSettings = settings
        // If user turned GHOST off while tunnel is up → stop.
        // If user turned it on while tunnel is already up → start.
        reconcile()
    }

    /** Called from VpnManager when tunnel reaches Connected. */
    fun onTunnelConnected() {
        tunnelConnected = true
        Log.i("GhostEngine", "tunnel connected – reconciling GHOST")
        reconcile()
    }

    /** Called from VpnManager on disconnect / error / stopping. */
    fun onTunnelDisconnected() {
        tunnelConnected = false
        Log.i("GhostEngine", "tunnel disconnected – stopping GHOST")
        stop()
    }

    private fun reconcile() {
        val s = currentSettings
        val shouldRun = s.enabled && s.decoyEngineEnabled && tunnelConnected
        if (shouldRun) start() else stop()
    }

    private fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            isRunning = true
            Log.i("GhostEngine", "GHOST decoy helper started (tunnel-bound)")
            while (isActive) {
                val s = currentSettings
                if (!s.enabled || !s.decoyEngineEnabled || !tunnelConnected) break
                try {
                    runDecoyCycle(s)
                    totalCycles++
                } catch (e: Exception) {
                    lastError = e.message
                    Log.w("GhostEngine", "cycle error: ${e.message}")
                }
                val base = s.decoyIntervalSeconds.coerceIn(5, 120)
                val jitter = Random.nextInt(0, (base * 0.55).toInt().coerceAtLeast(1))
                val pressureFactor = if (underPressure && s.adaptiveFallbackEnabled) 0.65 else 1.0
                delay(((base + jitter) * pressureFactor * 1000L).toLong())
            }
            isRunning = false
            Log.i("GhostEngine", "GHOST decoy helper stopped")
        }
    }

    private fun stop() {
        job?.cancel()
        job = null
        isRunning = false
    }

    private fun recordResult(ok: Boolean) {
        recentResults.addLast(ok)
        while (recentResults.size > 40) recentResults.removeFirst()
        if (currentSettings.pressureDetectionEnabled && recentResults.size >= 8) {
            val fails = recentResults.count { !it }
            underPressure = fails.toFloat() / recentResults.size >= 0.35f
        } else {
            underPressure = false
        }
    }

    private suspend fun runDecoyCycle(settings: GhostSettings) {
        val profiles = resolveActiveProfiles(settings)
        if (profiles.isEmpty()) return

        var volumeBoost = 1.0f
        if (settings.adaptiveFallbackEnabled && underPressure) volumeBoost *= 1.5f
        if (settings.burstModeEnabled) {
            val intensity = settings.burstIntensity.coerceIn(1, 5)
            volumeBoost *= (1.0f + intensity * 0.25f)
            lastBurstAtMs = System.currentTimeMillis()
        }

        val appCount = if (settings.multiAppConcurrent) {
            Random.nextInt(1, minOf(4, profiles.size) + 1)
        } else 1

        val selected = profiles.shuffled().take(appCount)

        for (profile in selected) {
            lastProfileUsed = profile.displayName
            val n = (Random.nextInt(1, 5) * volumeBoost).toInt().coerceIn(1, 8)
            val requests = profile.requests.shuffled().take(n)
            val parallel = settings.maxParallelRequests.coerceIn(1, 4)

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
                delay(Random.nextLong(200, 900))
            }
            delay(Random.nextLong(300, 2000))
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
        DecoyRequest("https://neshan.org/traffic/", NESHAN_UA),
    ))
    private val SNAPP = AppProfile("snapp", "اسنپ", listOf(
        DecoyRequest("https://app.snapp.taxi/", SNAPP_UA),
        DecoyRequest("https://api.snapp.ir/", SNAPP_UA),
        DecoyRequest("https://snapp.ir/", SNAPP_UA),
    ))
    private val APARAT = AppProfile("aparat", "آپارات", listOf(
        DecoyRequest("https://www.aparat.com/", APARAT_UA),
        DecoyRequest("https://www.aparat.com/v/abc123", APARAT_UA),
        DecoyRequest("https://www.aparat.com/api/fa/v1/video/video/list/tagid/1", APARAT_UA),
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
        DecoyRequest("https://www.tarafdari.com/", DEFAULT_UA),
        DecoyRequest("https://www.eghtesadnews.com/", DEFAULT_UA),
    ))

    private val ALL_PROFILES = listOf(
        RUBIKA, DIGIKALA, NESHAN, SNAPP, APARAT, BALAD, TOROB, GENERIC_BROWSE
    )
}
