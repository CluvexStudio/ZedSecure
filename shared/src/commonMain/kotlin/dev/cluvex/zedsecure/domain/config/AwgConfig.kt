package dev.cluvex.zedsecure.domain.config

data class AwgConfig(val values: Map<String, String> = emptyMap()) {
    operator fun get(key: String): String = values[key].orEmpty()

    val isEmpty: Boolean get() = values.values.none { it.isNotBlank() }

    val entries: List<Pair<String, String>>
        get() = KEYS.mapNotNull { k -> values[k]?.takeIf { it.isNotBlank() }?.let { k to it } }

    fun with(key: String, value: String): AwgConfig =
        AwgConfig(values.toMutableMap().apply { if (value.isBlank()) remove(key) else put(key, value) })

    companion object {
        val KEYS = listOf(
            "jc", "jmin", "jmax",
            "s1", "s2", "s3", "s4",
            "h1", "h2", "h3", "h4",
            "i1", "i2", "i3", "i4", "i5",
            "header_protection_key", "content_padding_addition",
            "rekey_after_time", "rekey_timeout", "reject_after_time",
            "keepalive_timeout", "max_handshake_attempts",
            "random_trailers", "disable_cookies",
        )

        val CLASSIC_KEYS = listOf("jc", "jmin", "jmax", "s1", "s2", "h1", "h2", "h3", "h4")

        val CONF_NAME = mapOf(
            "jc" to "Jc", "jmin" to "Jmin", "jmax" to "Jmax",
            "s1" to "S1", "s2" to "S2", "s3" to "S3", "s4" to "S4",
            "h1" to "H1", "h2" to "H2", "h3" to "H3", "h4" to "H4",
            "i1" to "I1", "i2" to "I2", "i3" to "I3", "i4" to "I4", "i5" to "I5",
            "header_protection_key" to "HeaderProtectionKey",
            "content_padding_addition" to "ContentPaddingAddition",
            "rekey_after_time" to "RekeyAfterTime",
            "rekey_timeout" to "RekeyTimeout",
            "reject_after_time" to "RejectAfterTime",
            "keepalive_timeout" to "KeepaliveTimeout",
            "max_handshake_attempts" to "MaxHandshakeAttempts",
            "random_trailers" to "RandomTrailers",
            "disable_cookies" to "DisableCookies",
        )

        val DESCRIPTION = mapOf(
            "jc" to "junkPacketCount", "jmin" to "junkPacketMinSize", "jmax" to "junkPacketMaxSize",
            "s1" to "initPacketJunkSize", "s2" to "responsePacketJunkSize",
            "s3" to "cookieReplyPacketJunkSize", "s4" to "transportPacketJunkSize",
            "h1" to "initPacketMagicHeader", "h2" to "responsePacketMagicHeader",
            "h3" to "underloadPacketMagicHeader", "h4" to "transportPacketMagicHeader",
            "i1" to "specialJunk1", "i2" to "specialJunk2", "i3" to "specialJunk3",
            "i4" to "specialJunk4", "i5" to "specialJunk5",
        )

        private val BY_NORMALIZED: Map<String, String> =
            KEYS.associateBy { it.replace("_", "") }

        fun canonicalKey(raw: String): String? =
            BY_NORMALIZED[raw.trim().lowercase().replace("_", "").replace("-", "")]

        fun amneziaDefaults(junkPacketCount: Int = (4..6).random()): AwgConfig = AwgConfig(
            linkedMapOf(
                "jc" to junkPacketCount.toString(),
                "jmin" to "10",
                "jmax" to "50",
                "s1" to "0",
                "s2" to "0",
                "h1" to "1",
                "h2" to "2",
                "h3" to "3",
                "h4" to "4",
            ),
        )

        fun from(raw: Map<String, String>): AwgConfig {
            val out = LinkedHashMap<String, String>()
            for (k in KEYS) {
                raw.entries.firstOrNull { canonicalKey(it.key) == k }
                    ?.value?.trim()?.takeIf { it.isNotBlank() }
                    ?.let { out[k] = it }
            }
            return AwgConfig(out)
        }
    }
}
