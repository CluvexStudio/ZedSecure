package dev.cluvex.zedsecure.domain.config

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
object SniSpoofLink {
    const val SCHEME = "snispoof://"

    fun isSniSpoofLink(text: String): Boolean = text.trim().startsWith(SCHEME, ignoreCase = true)

    fun build(profile: SniSpoofProfile, remark: String): String = buildString {
        append(SCHEME)
        append(Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(profile.link.encodeToByteArray()))
        val params = buildList {
            profile.fakeSni.takeIf { it.isNotBlank() }?.let { add("sni=" + encode(it)) }
            profile.cleanIp.takeIf { it.isNotBlank() }?.let { add("ip=" + encode(it)) }
        }
        if (params.isNotEmpty()) append("?").append(params.joinToString("&"))
        remark.takeIf { it.isNotBlank() }?.let { append("#").append(encode(it)) }
    }

    fun parse(uri: String): Pair<String, SniSpoofProfile>? {
        val trimmed = uri.trim()
        if (!isSniSpoofLink(trimmed)) return null
        val body = trimmed.substring(SCHEME.length)
        val remark = body.substringAfter('#', "").let { if (it.isEmpty()) "" else decode(it) }
        val withoutFragment = body.substringBefore('#')
        val payload = withoutFragment.substringBefore('?')
        val query = withoutFragment.substringAfter('?', "")
            .split('&')
            .filter { it.isNotBlank() }
            .associate { p -> decode(p.substringBefore('=')) to decode(p.substringAfter('=', "")) }

        val underlying = runCatching {
            Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
                .decode(payload.trimEnd('='))
                .decodeToString()
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null

        val fakeSni = query["sni"].orEmpty()

        if (fakeSni.isBlank()) return null
        return remark to SniSpoofProfile(
            link = underlying,
            fakeSni = fakeSni,
            cleanIp = query["ip"].orEmpty(),
        )
    }

    private fun encode(value: String): String = buildString {
        value.encodeToByteArray().forEach { b ->
            val c = b.toInt() and 0xFF
            val ch = c.toChar()
            if (ch.isLetterOrDigit() && c < 0x80 || ch in "-_.~") append(ch)
            else append('%').append(HEX[c shr 4]).append(HEX[c and 0x0F])
        }
    }

    private fun decode(value: String): String {
        val out = ArrayList<Byte>(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            when {
                c == '%' && i + 2 < value.length -> {
                    val hi = HEX.indexOf(value[i + 1].uppercaseChar())
                    val lo = HEX.indexOf(value[i + 2].uppercaseChar())
                    if (hi >= 0 && lo >= 0) {
                        out.add(((hi shl 4) or lo).toByte()); i += 3
                    } else {
                        out.add(c.code.toByte()); i++
                    }
                }
                else -> {
                    c.toString().encodeToByteArray().forEach { out.add(it) }
                    i++
                }
            }
        }
        return out.toByteArray().decodeToString()
    }

    private const val HEX = "0123456789ABCDEF"
}
