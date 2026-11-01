package dev.cluvex.zedsecure.data.net

data class SpeedResponse(
    val status: Int,
    val ttfbMs: Double,
    val payloadMs: Double,
    val headerBytes: Long,
    val bodyBytes: Long,
    val serverTiming: String?,
    val headers: Map<String, String>,
    val body: String?,
) {
    val transferBytes: Long get() = headerBytes + bodyBytes

    fun header(name: String): String? = headers[name.lowercase()]
}

class SpeedHttpException(val status: Int) : Exception("HTTP $status")

interface SpeedLink {
    suspend fun get(path: String, keepBody: Boolean = false, onBytes: (Long) -> Boolean = { true }): SpeedResponse

    suspend fun post(path: String, bytes: Long, onBytes: (Long) -> Boolean = { true }): SpeedResponse

    fun close()
}

internal expect fun openSpeedLink(host: String, socksPort: Int?): SpeedLink
