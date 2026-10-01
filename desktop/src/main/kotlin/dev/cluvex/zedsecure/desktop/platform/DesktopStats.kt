package dev.cluvex.zedsecure.desktop.platform

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

class DesktopStats(private val metricsPort: Int) {
    companion object {
        @Volatile
        var latestAutoSelect: String? = null
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val meter = DesktopMeter(totals = { read()?.let { (up, down) -> down to up } })

    fun start() = meter.start()

    fun stop() = meter.stop()

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
