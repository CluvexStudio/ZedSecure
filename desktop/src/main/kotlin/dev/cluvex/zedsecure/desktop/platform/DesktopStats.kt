package dev.cluvex.zedsecure.desktop.platform

import dev.cluvex.zedsecure.core.VpnManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class DesktopStats(private val metricsPort: Int) {
    companion object {
        @Volatile
        var latestAutoSelect: String? = null
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    @Volatile private var running = false
    private var worker: Thread? = null

    fun start() {
        if (running) return
        running = true
        val startedAt = System.currentTimeMillis()
        var lastUp = 0L
        var lastDown = 0L
        var lastAt = startedAt
        worker = thread(name = "desktop-stats", isDaemon = true) {
            while (running) {
                val (up, down) = read() ?: run { Thread.sleep(1000); null } ?: continue
                val now = System.currentTimeMillis()
                val dt = ((now - lastAt).coerceAtLeast(1)).toDouble() / 1000.0
                val upBps = ((up - lastUp).coerceAtLeast(0) / dt).toLong()
                val downBps = ((down - lastDown).coerceAtLeast(0) / dt).toLong()
                lastUp = up; lastDown = down; lastAt = now
                val duration = ((now - startedAt) / 1000).toInt()
                VpnManager.onMetrics(duration, downBps, upBps, down, up)
                Thread.sleep(1000)
            }
        }
    }

    fun stop() {
        running = false
        worker = null
    }

    private fun read(): Pair<Long, Long>? = runCatching {
        val conn = (URL("http://127.0.0.1:$metricsPort/debug/vars").openConnection() as HttpURLConnection).apply {
            connectTimeout = 800; readTimeout = 800
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        val vars = json.parseToJsonElement(body).jsonObject

        latestAutoSelect = vars["autoselect"]?.toString()
        if (dev.cluvex.zedsecure.core.AutoSelect.session.value != null) dev.cluvex.zedsecure.core.AutoSelect.poll()
        val outbound = vars["stats"]?.jsonObject
            ?.get("outbound")?.jsonObject ?: return@runCatching 0L to 0L
        var up = 0L; var down = 0L

        val autoMembers = outbound.keys.any { dev.cluvex.zedsecure.domain.config.AutoSelectTags.isMember(it) }
        outbound.forEach { (tag, v) ->
            if (tag == "direct" || tag == "block") return@forEach
            if (autoMembers && !dev.cluvex.zedsecure.domain.config.AutoSelectTags.isMember(tag)) return@forEach
            val o = v.jsonObject
            up += runCatching { o["uplink"]?.jsonPrimitive?.content?.toLong() ?: 0L }.getOrDefault(0L)
            down += runCatching { o["downlink"]?.jsonPrimitive?.content?.toLong() ?: 0L }.getOrDefault(0L)
        }
        up to down
    }.getOrNull()
}
