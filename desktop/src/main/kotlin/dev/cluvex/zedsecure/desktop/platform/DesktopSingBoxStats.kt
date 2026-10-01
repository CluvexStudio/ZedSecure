package dev.cluvex.zedsecure.desktop.platform

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.HttpURLConnection
import java.net.URL

class DesktopSingBoxStats(private val controller: String, private val secret: String?) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val meter = DesktopMeter(totals = { read()?.let { (up, down) -> down to up } })

    fun start() = meter.start()

    fun stop() = meter.stop()

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
