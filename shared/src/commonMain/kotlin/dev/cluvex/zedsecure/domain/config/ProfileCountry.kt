package dev.cluvex.zedsecure.domain.config

object ProfileCountry {
    private const val FIRST_INDICATOR = 0x1F1E6
    private const val LAST_INDICATOR = 0x1F1FF

    fun of(profile: VpnProfile): String? =
        profile.countryCode?.trim()?.uppercase()?.takeIf { it.length == 2 } ?: fromFlag(profile.name)

    fun fromFlag(text: String): String? {
        var i = 0
        var previous = -1
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val letter = if (cp in FIRST_INDICATOR..LAST_INDICATOR) cp - FIRST_INDICATOR else -1
            if (letter >= 0 && previous >= 0) return "${'A' + previous}${'A' + letter}"
            previous = letter
            i += Character.charCount(cp)
        }
        return null
    }
}
