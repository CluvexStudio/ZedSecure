package dev.cluvex.zedsecure.platform

import java.net.HttpURLConnection
import java.net.URL

internal actual suspend fun httpPatchJson(
    url: String,
    headers: Map<String, String>,
    body: String,
    connectTimeoutMs: Int,
    readTimeoutMs: Int,
    socksPort: Int?,
): HttpTextResponse = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val target = URL(url)
    val raw = if (socksPort != null) {
        target.openConnection(
            java.net.Proxy(java.net.Proxy.Type.SOCKS, java.net.InetSocketAddress.createUnresolved("127.0.0.1", socksPort)),
        )
    } else {
        target.openConnection()
    }
    val connection = (raw as HttpURLConnection).apply {
        requestMethod = "PATCH"
        connectTimeout = connectTimeoutMs
        readTimeout = readTimeoutMs
        doOutput = true
        headers.forEach { (k, v) -> setRequestProperty(k, v) }
    }
    try {
        connection.outputStream.use { it.write(body.encodeToByteArray()) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        HttpTextResponse(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty(), emptyMap())
    } catch (e: NullPointerException) {
        throw platformHttpFailure(e)
    } finally {
        connection.disconnectQuietly()
    }
}
