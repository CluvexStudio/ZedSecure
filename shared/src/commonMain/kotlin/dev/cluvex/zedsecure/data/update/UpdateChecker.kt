package dev.cluvex.zedsecure.data.update

import dev.cluvex.zedsecure.platform.AppInfo
import dev.cluvex.zedsecure.platform.httpGetViaSocks
import dev.cluvex.zedsecure.platform.httpJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object PlayStore {
    const val PACKAGE = "com.zedsecure.vpn"

    const val MARKET_URL = "market://details?id=$PACKAGE"
    const val WEB_URL = "https://play.google.com/store/apps/details?id=$PACKAGE"

    fun listingUrl(lang: String): String = "$WEB_URL&hl=$lang"

    const val SIGNING_CERT_SHA256 = "4a54234609158a2cbb07b30f024751c236dbcf55242aed7b0fd1cd3a7d0d98ec"
}

enum class Distribution { PlayStore, GitHub }

object GitHubReleases {
    const val REPO = "CluvexStudio/ZedSecure"

    const val PROJECT_URL = "https://github.com/$REPO"

    const val LATEST_URL = "$PROJECT_URL/releases/latest"

    const val LATEST_API = "https://api.github.com/repos/$REPO/releases/latest"
}

data class UpdateInfo(
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String? = null,
)

object UpdateChecker {
    private const val BROWSER_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Safari/537.36"

    private val VERSION_ANCHOR = Regex("""\[\[\["([0-9][0-9A-Za-z._\- ]{0,31})"]],\[\[\[""")

    private const val NOTES_ANCHOR = "[null,[null,\""

    private const val NOTES_WINDOW = 8_000

    suspend fun fetchLatest(
        distribution: Distribution,
        abis: List<String>,
        socksPort: Int?,
        lang: String,
    ): UpdateInfo? = when (distribution) {
        Distribution.PlayStore -> fetchFromPlay(socksPort, lang)
        Distribution.GitHub -> fetchFromGitHub(abis, socksPort)
    }

    private suspend fun fetchFromGitHub(abis: List<String>, socksPort: Int?): UpdateInfo? = runCatching {
        val response = httpJson(
            method = "GET",
            url = GitHubReleases.LATEST_API,
            headers = mapOf(
                "Accept" to "application/vnd.github+json",
                "User-Agent" to AppInfo.userAgent,
                "X-GitHub-Api-Version" to "2022-11-28",
            ),
            body = null,
            connectTimeoutMs = 12_000,
            readTimeoutMs = 15_000,
            socksPort = socksPort,
        )
        if (response.code !in 200..299) return@runCatching null
        parseGitHubRelease(response.body, abis)
    }.getOrNull()

    private val releaseJson = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parseGitHubRelease(body: String, abis: List<String>): UpdateInfo? {
        val root = releaseJson.parseToJsonElement(body) as? JsonObject ?: return null
        fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val tag = root.text("tag_name") ?: return null
        val version = Regex("""\d+(?:\.\d+)+""").find(tag)?.value ?: return null
        val assets = (root["assets"] as? JsonArray).orEmpty().mapNotNull { element ->
            val asset = element as? JsonObject ?: return@mapNotNull null
            val name = asset.text("name") ?: return@mapNotNull null
            val url = asset.text("browser_download_url") ?: return@mapNotNull null
            name to url
        }.toMap()
        val apk = if (abis.isEmpty()) null else {
            (abis.map { "ZedSecure-$version-$it.apk" } + "ZedSecure-$version-universal.apk")
                .firstNotNullOfOrNull { assets[it] }
        }
        return UpdateInfo(
            versionName = version,
            releaseNotes = releaseNotesFrom(root.text("body").orEmpty()),
            downloadUrl = apk ?: root.text("html_url") ?: GitHubReleases.LATEST_URL,
        )
    }

    fun releaseNotesFrom(markdown: String): String {
        val start = markdown.indexOf(WHATS_NEW)
        if (start < 0) return ""
        val section = markdown.substring(start + WHATS_NEW.length).substringBefore("\n<sub>").substringBefore("\n### ")
        return section.lines()
            .map { line ->
                line.trim()
                    .replace(Regex("""\[([^\]]+)]\([^)]*\)"""), "$1")
                    .replace("**", "")
                    .replace("`", "")
                    .replace(Regex("<[^>]*>"), "")
                    .let { if (it.startsWith("- ") || it.startsWith("* ")) "• " + it.substring(2) else it }
            }
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private const val WHATS_NEW = "### What's new"

    private suspend fun fetchFromPlay(socksPort: Int?, lang: String): UpdateInfo? = runCatching {
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
