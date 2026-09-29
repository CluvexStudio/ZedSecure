@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package dev.cluvex.zedsecure.domain.config

import kotlin.io.encoding.Base64

data class SubscriptionMeta(
    val upload: Long? = null,
    val download: Long? = null,

    val total: Long? = null,

    val expireAt: Long? = null,
    val title: String? = null,
    val updateIntervalHours: Int? = null,
    val supportUrl: String? = null,
    val webPageUrl: String? = null,
) {
    val isEmpty: Boolean
        get() = upload == null && download == null && total == null && expireAt == null &&
            title == null && updateIntervalHours == null && supportUrl == null && webPageUrl == null
}

object SubscriptionHeaders {
    fun parse(headers: Map<String, String>): SubscriptionMeta {
        val byName = headers.entries.associate { (k, v) -> k.trim().lowercase() to v }
        val info = byName["subscription-userinfo"]?.let(::parseUserInfo) ?: UserInfo()
        return SubscriptionMeta(
            upload = info.upload,
            download = info.download,
            total = info.total,
            expireAt = info.expireAt,
            title = byName["profile-title"]?.let(::decodeTitle),
            updateIntervalHours = byName["profile-update-interval"]?.let(::parseInterval),
            supportUrl = byName["support-url"]?.let(::httpUrlOrNull),
            webPageUrl = byName["profile-web-page-url"]?.let(::httpUrlOrNull),
        )
    }

    data class UserInfo(
        val upload: Long? = null,
        val download: Long? = null,
        val total: Long? = null,
        val expireAt: Long? = null,
    )

    fun parseUserInfo(value: String): UserInfo {
        val fields = value.split(';', ',')
            .mapNotNull { part ->
                val eq = part.indexOf('=')
                if (eq <= 0) return@mapNotNull null
                val key = part.substring(0, eq).trim().lowercase()
                val number = part.substring(eq + 1).trim().toDoubleOrNull()
                    ?.takeIf { !it.isNaN() && it >= 0 && it < Long.MAX_VALUE.toDouble() }
                    ?.toLong() ?: return@mapNotNull null
                key to number
            }
            .toMap()
        return UserInfo(
            upload = fields["upload"],
            download = fields["download"],
            total = fields["total"]?.takeIf { it > 0 },
            expireAt = fields["expire"]?.takeIf { it > 0 }?.let { seconds ->

                if (seconds >= 100_000_000_000L) seconds else seconds * 1000
            },
        )
    }

    fun decodeTitle(value: String): String? {
        val raw = value.trim()
        if (raw.isEmpty()) return null
        val decoded = if (raw.startsWith("base64:", ignoreCase = true)) {
            val payload = raw.substring("base64:".length).trim()
            runCatching {
                Base64.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(payload).decodeToString()
            }.recoverCatching {
                Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(payload).decodeToString()
            }.getOrNull() ?: return null
        } else if (raw.all { it.code < 256 } && raw.any { it.code >= 128 }) {
            val bytes = ByteArray(raw.length) { raw[it].code.toByte() }
            bytes.decodeToString().takeIf { '�' !in it } ?: raw
        } else {
            raw
        }
        return decoded.trim().replace(Regex("\\s+"), " ").take(MAX_TITLE).takeIf { it.isNotEmpty() }
    }

    private fun parseInterval(value: String): Int? =
        value.trim().toDoubleOrNull()?.toInt()?.takeIf { it > 0 }?.coerceAtMost(168)

    private fun httpUrlOrNull(value: String): String? {
        val v = value.trim()
        return v.takeIf {
            (it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true)) &&
                it.length > "https://".length && it.none(Char::isWhitespace)
        }
    }

    private const val MAX_TITLE = 60
}

object SubscriptionSchedule {
    private const val EARLY_SLACK_MS = 10 * 60 * 1000L

    fun intervalHours(sub: Subscription, globalHours: Int): Int =
        (sub.updateIntervalHours ?: globalHours).coerceIn(1, 168)

    fun isDue(sub: Subscription, globalHours: Int, nowMs: Long): Boolean {
        if (!sub.enabled || sub.url.isBlank()) return false
        if (sub.lastUpdated <= 0L) return true
        return nowMs - sub.lastUpdated >= intervalHours(sub, globalHours) * 3_600_000L - EARLY_SLACK_MS
    }

    fun periodHours(subscriptions: List<Subscription>, globalHours: Int): Int =
        subscriptions.filter { it.enabled }
            .minOfOrNull { intervalHours(it, globalHours) }
            ?: globalHours.coerceIn(1, 168)
}
