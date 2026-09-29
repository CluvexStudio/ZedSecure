package dev.cluvex.zedsecure.platform

internal expect fun httpGetText(
    url: String,
    userAgent: String,
    connectTimeoutMs: Int = 15_000,
    readTimeoutMs: Int = 20_000,
): String

internal class HttpTextResponse(
    val code: Int,
    val body: String,
    val headers: Map<String, String>,
)

internal expect fun httpGetResponse(
    url: String,
    userAgent: String,
    socksPort: Int? = null,
    connectTimeoutMs: Int = 15_000,
    readTimeoutMs: Int = 20_000,
): HttpTextResponse

internal expect suspend fun httpJson(
    method: String,
    url: String,
    headers: Map<String, String>,
    body: String?,
    connectTimeoutMs: Int = 20_000,
    readTimeoutMs: Int = 180_000,

    socksPort: Int? = null,
): HttpTextResponse
