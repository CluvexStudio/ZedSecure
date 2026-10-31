package dev.cluvex.zedsecure.platform

import java.net.InetSocketAddress
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

internal actual suspend fun httpPatchJson(
    url: String,
    headers: Map<String, String>,
    body: String,
    connectTimeoutMs: Int,
    readTimeoutMs: Int,
    socksPort: Int?,
): HttpTextResponse = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(connectTimeoutMs.toLong()))
        .apply { if (socksPort != null) proxy(ProxySelector.of(InetSocketAddress("127.0.0.1", socksPort))) }
        .build()
    val request = HttpRequest.newBuilder(URI(url))
        .timeout(Duration.ofMillis(readTimeoutMs.toLong()))
        .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
        .apply { headers.forEach { (k, v) -> if (!k.equals("Connection", ignoreCase = true)) header(k, v) } }
        .build()
    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    HttpTextResponse(response.statusCode(), response.body().orEmpty(), emptyMap())
}
