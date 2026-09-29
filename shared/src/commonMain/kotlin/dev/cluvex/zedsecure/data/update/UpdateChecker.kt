package dev.cluvex.zedsecure.data.update

import dev.cluvex.zedsecure.platform.httpGetViaSocks

object PlayStore {
    const val PACKAGE = "com.zedsecure.vpn"

    const val MARKET_URL = "market://details?id=$PACKAGE"
    const val WEB_URL = "https://play.google.com/store/apps/details?id=$PACKAGE"

    fun listingUrl(lang: String): String = "$WEB_URL&hl=$lang"
}

data class UpdateInfo(val versionName: String, val releaseNotes: String)

object UpdateChecker {
    private const val BROWSER_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Safari/537.36"

    private val VERSION_ANCHOR = Regex("""\[\[\["([0-9][0-9A-Za-z._\- ]{0,31})"]],\[\[\[""")

    private const val NOTES_ANCHOR = "[null,[null,\""

    private const val NOTES_WINDOW = 8_000

    suspend fun fetchLatest(socksPort: Int?, lang: String): UpdateInfo? = runCatching {
        val html = httpGetViaSocks(
            url = PlayStore.listingUrl(lang),
            socksPort = socksPort,
            userAgent = BROWSER_UA,
            connectTimeoutMs = 12_000,
            readTimeoutMs = 15_000,
        )
        parse(html)
    }.getOrNull()

    internal fun parse(html: String): UpdateInfo? {
        val version = VERSION_ANCHOR.find(html) ?: return null
        val name = version.groupValues[1].trim().ifBlank { return null }
        val from = version.range.last + 1
        val window = html.substring(from, minOf(from + NOTES_WINDOW, html.length))
        val notesAt = window.indexOf(NOTES_ANCHOR)

        val notes = if (notesAt < 0) "" else {
            htmlToPlainText(readJsString(window, notesAt + NOTES_ANCHOR.length))
        }
        return UpdateInfo(versionName = name, releaseNotes = notes)
    }

    fun compareVersions(a: String, b: String): Int {
        val x = numericSegments(a)
        val y = numericSegments(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }

    private fun numericSegments(v: String): List<Int> =
        Regex("\\d+").findAll(v).map { it.value.toIntOrNull() ?: 0 }.toList()

    private fun readJsString(s: String, start: Int): String {
        val out = StringBuilder()
        var i = start
        while (i < s.length) {
            val c = s[i]
            if (c == '"') break
            if (c != '\\' || i + 1 >= s.length) {
                out.append(c)
                i++
                continue
            }
            when (val esc = s[i + 1]) {
                'u' -> {
                    val code = if (i + 6 <= s.length) s.substring(i + 2, i + 6).toIntOrNull(16) else null
                    if (code != null) {
                        out.append(code.toChar())
                        i += 6
                    } else {
                        out.append(esc)
                        i += 2
                    }
                }
                'n' -> { out.append('\n'); i += 2 }
                't' -> { out.append('\t'); i += 2 }
                'r' -> i += 2
                else -> { out.append(esc); i += 2 }
            }
        }
        return out.toString()
    }

    private fun htmlToPlainText(raw: String): String = raw
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)</(p|div|li)>"), "\n")
        .replace(Regex("(?i)<li[^>]*>"), "• ")
        .replace(Regex("<[^>]*>"), "")
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")
        .replace(Regex("[ \t]+\n"), "\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}

object NudgePolicy {
    const val UPDATE_CHECK_INTERVAL_MS = 12L * 60 * 60 * 1000

    const val UPDATE_DISMISS_SNOOZE_MS = 24L * 60 * 60 * 1000

    const val RATE_INTERVAL_MS = 24L * 60 * 60 * 1000

    const val RATE_MIN_CONNECTIONS = 5
}
