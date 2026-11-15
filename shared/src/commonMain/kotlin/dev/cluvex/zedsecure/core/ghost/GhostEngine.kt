package dev.cluvex.zedsecure.core.ghost

import dev.cluvex.zedsecure.domain.model.GhostSettings
import dev.cluvex.zedsecure.platform.httpGetResponse
import dev.cluvex.zedsecure.platform.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * GHOST Engine – Generated Harmless Obfuscated Session Traffic
 *
 * Generates decoy traffic that mimics popular Iranian apps so that
 * DPI systems have a harder time distinguishing VPN traffic from
 * normal domestic activity.
 *
 * Inspiration: MockTraffic (noise generation) + NIKJOO/Traffic-Obfuscator
 * (hybrid decoy patterns).
 */
object GhostEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    @Volatile
    private var currentSettings: GhostSettings = GhostSettings()

    // ── Runtime stats (for debug / UI) ──────────────────────────────
    @Volatile var totalCycles: Long = 0
        private set
    @Volatile var totalRequests: Long = 0
        private set
    @Volatile var successCount: Long = 0
        private set
    @Volatile var failureCount: Long = 0
        private set
    @Volatile var lastProfileUsed: String = "-"
        private set
    @Volatile var isRunning: Boolean = false
        private set
    @Volatile var lastError: String? = null
        private set

    data class DebugStatus(
        val running: Boolean,
        val enabled: Boolean,
        val cycles: Long,
        val requests: Long,
        val successes: Long,
        val failures: Long,
        val lastProfile: String,
        val lastError: String?,
        val activeProfiles: String,
        val decoyVolumePercent: Int,
    )

    fun debugStatus(): DebugStatus {
        val s = currentSettings
        return DebugStatus(
            running = isRunning,
            enabled = s.enabled && s.decoyEngineEnabled,
            cycles = totalCycles,
            requests = totalRequests,
            successes = successCount,
            failures = failureCount,
            lastProfile = lastProfileUsed,
            lastError = lastError,
            activeProfiles = s.activeProfiles,
            decoyVolumePercent = s.decoyVolumePercent,
        )
    }

    fun updateSettings(settings: GhostSettings) {
        currentSettings = settings
        if (settings.enabled && settings.decoyEngineEnabled) {
            start()
        } else {
            stop()
        }
    }

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            isRunning = true
            Log.i("GhostEngine", "GHOST decoy engine started")
            while (isActive) {
                val s = currentSettings
                if (!s.enabled || !s.decoyEngineEnabled) break

                try {
                    runDecoyCycle(s)
                    totalCycles++
                } catch (e: Exception) {
                    lastError = e.message
                    Log.w("GhostEngine", "decoy cycle error: ${e.message}")
                }

                val base = s.decoyIntervalSeconds.coerceIn(5, 120)
                // More human-like jitter (inspired by traffic generators)
                val jitter = Random.nextInt(0, (base * 0.6).toInt().coerceAtLeast(1))
                delay((base + jitter) * 1000L)
            }
            isRunning = false
            Log.i("GhostEngine", "GHOST decoy engine stopped")
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        isRunning = false
    }

    private suspend fun runDecoyCycle(settings: GhostSettings) {
        val profiles = resolveActiveProfiles(settings)
        if (profiles.isEmpty()) return

        // Adaptive: if enabled, occasionally increase activity
        val volumeBoost = if (settings.adaptiveFallbackEnabled && Random.nextFloat() < 0.25f) {
            1.4f
        } else {
            1.0f
        }

        val count = if (settings.multiAppConcurrent) {
            val max = minOf(4, profiles.size)
            Random.nextInt(1, max + 1)
        } else {
            1
        }

        val selected = profiles.shuffled().take(count)

        for (profile in selected) {
            lastProfileUsed = profile.displayName
            val requestCount = (Random.nextInt(1, 5) * volumeBoost).toInt().coerceIn(1, 6)
            val requests = profile.requests.shuffled().take(requestCount)

            for (req in requests) {
                totalRequests++
                try {
                    val ua = if (settings.appLayerMimicryEnabled) req.userAgent else DEFAULT_UA
                    val resp = httpGetResponse(
                        url = req.url,
                        userAgent = ua,
                        connectTimeoutMs = 7_000,
                        readTimeoutMs = 10_000,
                    )
                    if (resp.code in 200..399 || resp.code == 403 || resp.code == 404) {
                        // 403/404 still counts as "reached the real service"
                        successCount++
                    } else {
                        failureCount++
                    }
                    // Human-like pause between requests of the same app
                    delay(Random.nextLong(250, 1600))
                } catch (e: Exception) {
                    failureCount++
                    lastError = e.message
                }
            }
            // Pause between different apps
            delay(Random.nextLong(350, 2400))
        }
    }

    private fun resolveActiveProfiles(settings: GhostSettings): List<AppProfile> {
        if (!settings.appProfilesEnabled) {
            return listOf(GENERIC_BROWSE)
        }
        val keys = settings.activeProfiles
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()

        return ALL_PROFILES.filter { it.key in keys }.ifEmpty { listOf(GENERIC_BROWSE) }
    }

    // ─────────────────────────────────────────────────────────────
    // Rich App Behavior Profiles (expanded)
    // ─────────────────────────────────────────────────────────────

    private data class DecoyRequest(
        val url: String,
        val userAgent: String,
    )

    private data class AppProfile(
        val key: String,
        val displayName: String,
        val requests: List<DecoyRequest>,
    )

    private val RUBIKA_UA = "Rubika/3.2.0 (Android 13; SM-A525F) okhttp/4.10.0"
    private val DIGIKALA_UA = "Digikala/5.18.0 (Linux; Android 13; Pixel 6) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"
    private val NESHAN_UA = "Neshan/6.4.1 (Android 12; Redmi Note 10) okhttp/4.9.3"
    private val SNAPP_UA = "Snapp/5.12.0 (Android 13; SM-G991B) okhttp/4.11.0"
    private val APARAT_UA = "Aparat/6.1.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"
    private val BALAD_UA = "Balad/4.8.0 (Android 13; SM-A536B)"
    private val TOROB_UA = "Torob/3.9.0 (Linux; Android 13) AppleWebKit/537.36"
    private val DEFAULT_UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private val RUBIKA = AppProfile(
        key = "rubika",
        displayName = "روبیکا",
        requests = listOf(
            DecoyRequest("https://rubika.ir/", RUBIKA_UA),
            DecoyRequest("https://web.rubika.ir/", RUBIKA_UA),
            DecoyRequest("https://rubika.ir/static/js/main.js", RUBIKA_UA),
            DecoyRequest("https://rubika.ir/favicon.ico", RUBIKA_UA),
        ),
    )

    private val DIGIKALA = AppProfile(
        key = "digikala",
        displayName = "دیجیکالا",
        requests = listOf(
            DecoyRequest("https://www.digikala.com/", DIGIKALA_UA),
            DecoyRequest("https://www.digikala.com/search/", DIGIKALA_UA),
            DecoyRequest("https://api.digikala.com/v1/categories/", DIGIKALA_UA),
            DecoyRequest("https://www.digikala.com/product/dkp-123456/", DIGIKALA_UA),
            DecoyRequest("https://www.digikala.com/marvel/", DIGIKALA_UA),
        ),
    )

    private val NESHAN = AppProfile(
        key = "neshan",
        displayName = "نشان",
        requests = listOf(
            DecoyRequest("https://neshan.org/", NESHAN_UA),
            DecoyRequest("https://api.neshan.org/v1/search?term=tehran", NESHAN_UA),
            DecoyRequest("https://neshan.org/maps/", NESHAN_UA),
            DecoyRequest("https://neshan.org/traffic/", NESHAN_UA),
        ),
    )

    private val SNAPP = AppProfile(
        key = "snapp",
        displayName = "اسنپ",
        requests = listOf(
            DecoyRequest("https://app.snapp.taxi/", SNAPP_UA),
            DecoyRequest("https://api.snapp.ir/", SNAPP_UA),
            DecoyRequest("https://snapp.ir/", SNAPP_UA),
        ),
    )

    private val APARAT = AppProfile(
        key = "aparat",
        displayName = "آپارات",
        requests = listOf(
            DecoyRequest("https://www.aparat.com/", APARAT_UA),
            DecoyRequest("https://www.aparat.com/v/abc123", APARAT_UA),
            DecoyRequest("https://www.aparat.com/api/fa/v1/video/video/list/tagid/1", APARAT_UA),
            DecoyRequest("https://www.aparat.com/dashboard", APARAT_UA),
        ),
    )

    private val BALAD = AppProfile(
        key = "balad",
        displayName = "بلد",
        requests = listOf(
            DecoyRequest("https://balad.ir/", BALAD_UA),
            DecoyRequest("https://balad.ir/maps", BALAD_UA),
        ),
    )

    private val TOROB = AppProfile(
        key = "torob",
        displayName = "ترب",
        requests = listOf(
            DecoyRequest("https://torob.com/", TOROB_UA),
            DecoyRequest("https://api.torob.com/", TOROB_UA),
        ),
    )

    private val GENERIC_BROWSE = AppProfile(
        key = "generic",
        displayName = "مرورگر",
        requests = listOf(
            DecoyRequest("https://www.varzesh3.com/", DEFAULT_UA),
            DecoyRequest("https://www.hamshahrionline.ir/", DEFAULT_UA),
            DecoyRequest("https://www.zoomit.ir/", DEFAULT_UA),
            DecoyRequest("https://www.digiato.com/", DEFAULT_UA),
            DecoyRequest("https://www.tarafdari.com/", DEFAULT_UA),
        ),
    )

    private val ALL_PROFILES = listOf(
        RUBIKA, DIGIKALA, NESHAN, SNAPP, APARAT, BALAD, TOROB, GENERIC_BROWSE
    )
}
