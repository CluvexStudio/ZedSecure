package dev.cluvex.zedsecure.domain.config

import kotlinx.serialization.Serializable

@Serializable
data class Subscription(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean = true,
    val lastUpdated: Long = 0,
    val serverCount: Int = 0,

    val userAgent: String? = null,

    val upload: Long? = null,

    val download: Long? = null,

    val total: Long? = null,

    val expireAt: Long? = null,

    val title: String? = null,

    val updateIntervalHours: Int? = null,

    val supportUrl: String? = null,

    val webPageUrl: String? = null,
) {
    val used: Long? get() = if (upload == null && download == null) null else (upload ?: 0L) + (download ?: 0L)
}

object SubscriptionParser {
    fun extractLinks(body: String): List<String> {
        val direct = splitLines(body)
        if (direct.isNotEmpty()) return direct

        val decoded = runCatching { ConfigParser.decodeBase64Utf8(body) }.getOrNull() ?: return emptyList()
        return splitLines(decoded)
    }

    private fun splitLines(text: String): List<String> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && ConfigParser.isSupportedLink(it) }
            .toList()
}
