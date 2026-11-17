package dev.cluvex.zedsecure.domain.config

import dev.cluvex.zedsecure.platform.currentTimeMillis
import dev.cluvex.zedsecure.platform.httpGetResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** Fast parallel reachability probe of the paths Aether depends on (all three run at once, ~3.5 s max). */
object AetherSmartProbe {
    class Result(val edgeMs: Int?, val apiMs: Int?, val edgeJitterMs: Int? = null)

    suspend fun run(): Result = coroutineScope {
        val edge = async {
            val first = time("https://cp.cloudflare.com/generate_204")
            val second = if (first != null) time("https://www.cloudflare.com/cdn-cgi/trace") else null
            first to second
        }
        val api = async { time("https://api.devices.cloudflare.com/") ?: time("https://api.cloudflareclient.com/") }
        val (e1, e2) = edge.await()
        val jitter = if (e1 != null && e2 != null) kotlin.math.abs(e1 - e2) else null
        Result(e1, api.await(), jitter)
    }

    private suspend fun time(url: String): Int? = withContext(Dispatchers.Default) {
        val start = currentTimeMillis()
        val answered = try {
            httpGetResponse(url, "ZX-Smart", null, TIMEOUT_MS, TIMEOUT_MS)
            true
        } catch (e: Throwable) {
            // An HTTP error status still proves the server answered.
            e.message?.startsWith("HTTP ") == true
        }
        if (answered) (currentTimeMillis() - start).toInt() else null
    }

    private const val TIMEOUT_MS = 3_500
}
