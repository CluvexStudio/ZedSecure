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
 * When enabled, continuously generates decoy traffic that mimics popular Iranian apps
 * (Rubika, Digikala, Neshan, Snapp, Aparat). This raises the cost of DPI blocking because
 * the traffic looks like normal domestic user activity.
 *
 * Features implemented:
 * 1. App Behavior Profiles
 * 2. Smart Decoy Engine
 * 3. Application-Layer Mimicry (realistic UA + paths + headers)
 * 5. Multi-App Concurrent (randomly picks several profiles per cycle)
 */
object GhostEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    @Volatile
    private var currentSettings: GhostSettings = GhostSettings()

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
            Log.i("GhostEngine", "GHOST decoy engine started")
            while (isActive) {
                val s = currentSettings
                if (!s.enabled || !s.decoyEngineEnabled) break

                try {
                    runDecoyCycle(s)
                } catch (e: Exception) {
                    Log.w("GhostEngine", "decoy cycle error: ${e.message}")
                }

                val base = s.decoyIntervalSeconds.coerceIn(5, 120)
                val jitter = Random.nextInt(0, base / 2 + 1)
                delay((base + jitter) * 1000L)
            }
            Log.i("GhostEngine", "GHOST decoy engine stopped")
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun runDecoyCycle(settings: GhostSettings) {
        val profiles = resolveActiveProfiles(settings)
        if (profiles.isEmpty()) return

        val count = if (settings.multiAppConcurrent) {
            Random.nextInt(1, minOf(3, profiles.size) + 1)
        } else {
            1
        }

        val selected = profiles.shuffled().take(count)

        for (profile in selected) {
            val requests = profile.requests.shuffled().take(Random.nextInt(1, 4))
            for (req in requests) {
                try {
                    val ua = if (settings.appLayerMimicryEnabled) req.userAgent else DEFAULT_UA
                    httpGetResponse(
                        url = req.url,
                        userAgent = ua,
                        connectTimeoutMs = 8_000,
                        readTimeoutMs = 12_000,
                    )
                    // small human-like pause between requests of the same app
                    delay(Random.nextLong(300, 1800))
                } catch (_: Exception) {
                    // ignore individual failures – decoy should be best-effort
                }
            }
            delay(Random.nextLong(400, 2200))
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
    // App Behavior Profiles (realistic Iranian apps)
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
    private val DIGIKALA_UA = "Digikala/5.18.0 (Linux; Android 13; Pixel 6) AppleWebKit/537.36"
    private val NESHAN_UA = "Neshan/6.4.1 (Android 12; Redmi Note 10) okhttp/4.9.3"
    private val SNAPP_UA = "Snapp/5.12.0 (Android 13; SM-G991B)"
    private val APARAT_UA = "Aparat/6.1.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120.0.0.0"
    private val DEFAULT_UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private val RUBIKA = AppProfile(
        key = "rubika",
        displayName = "روبیکا",
        requests = listOf(
            DecoyRequest("https://rubika.ir/", RUBIKA_UA),
            DecoyRequest("https://web.rubika.ir/", RUBIKA_UA),
            DecoyRequest("https://rubika.ir/static/js/main.js", RUBIKA_UA),
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
        ),
    )

    private val NESHAN = AppProfile(
        key = "neshan",
        displayName = "نشان",
        requests = listOf(
            DecoyRequest("https://neshan.org/", NESHAN_UA),
            DecoyRequest("https://api.neshan.org/v1/search?term=tehran", NESHAN_UA),
            DecoyRequest("https://neshan.org/maps/", NESHAN_UA),
        ),
    )

    private val SNAPP = AppProfile(
        key = "snapp",
        displayName = "اسنپ",
        requests = listOf(
            DecoyRequest("https://app.snapp.taxi/", SNAPP_UA),
            DecoyRequest("https://api.snapp.ir/", SNAPP_UA),
        ),
    )

    private val APARAT = AppProfile(
        key = "aparat",
        displayName = "آپارات",
        requests = listOf(
            DecoyRequest("https://www.aparat.com/", APARAT_UA),
            DecoyRequest("https://www.aparat.com/v/abc123", APARAT_UA),
            DecoyRequest("https://www.aparat.com/api/fa/v1/video/video/list/tagid/1", APARAT_UA),
        ),
    )

    private val GENERIC_BROWSE = AppProfile(
        key = "generic",
        displayName = "مرورگر",
        requests = listOf(
            DecoyRequest("https://www.varzesh3.com/", DEFAULT_UA),
            DecoyRequest("https://www.hamshahrionline.ir/", DEFAULT_UA),
            DecoyRequest("https://www.zoomit.ir/", DEFAULT_UA),
        ),
    )

    private val ALL_PROFILES = listOf(RUBIKA, DIGIKALA, NESHAN, SNAPP, APARAT, GENERIC_BROWSE)
}
