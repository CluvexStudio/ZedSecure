package dev.cluvex.zedsecure.desktop.platform

import dev.cluvex.zedsecure.core.VpnManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class DesktopSingBoxStats(private val controller: String, private val secret: String?) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    @Volatile private var running = false

    fun start() {
        if (running) return
        running = true
        val startedAt = System.currentTimeMillis()
        var lastUp = 0L
        var lastDown = 0L
        var lastAt = startedAt
        thread(name = "desktop-singbox-stats", isDaemon = true) {
            while (running) {
                Thread.sleep(1000)
                val (up, down) = read() ?: continue
                val now = System.currentTimeMillis()
                val dt = ((now - lastAt).coerceAtLeast(1)).toDouble() / 1000.0
                val upBps = ((up - lastUp).coerceAtLeast(0) / dt).toLong()
                val downBps = ((down - lastDown).coerceAtLeast(0) / dt).toLong()
                lastUp = up; lastDown = down; lastAt = now
                VpnManager.onMetrics(((now - startedAt) / 1000).toInt(), downBps, upBps, down, up)
            }
        }
    }

    fun stop() {
        running = false
    }

    private fun read(): Pair<Long, Long>? = runCatching {
        val conn = (URL("http://$controller/connections").openConnection() as HttpURLConnection).apply {
            connectTimeout = 800
            readTimeout = 800
            secret?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        val root = json.parseToJsonElement(body).jsonObject
        (root["uploadTotal"]?.jsonPrimitive?.longOrNull ?: 0L) to (root["downloadTotal"]?.jsonPrimitive?.longOrNull ?: 0L)
    }.getOrNull()
}
