package dev.cluvex.zedsecure.data.config

import dev.cluvex.zedsecure.domain.config.SubscriptionParser
import dev.cluvex.zedsecure.platform.httpGetResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** What a pasted link points at. */
sealed interface HuntTarget {
    /** One concrete file (or any plain https feed): fetched as it is. */
    data class Feed(val url: String) : HuntTarget

    /** A GitHub repository or a folder inside it: its config-looking files are discovered. */
    data class Folder(val owner: String, val repo: String, val ref: String?, val dir: String) : HuntTarget

    data class Unsupported(val reason: String) : HuntTarget
}

/** One feed the hunter verified: [links] is how many proxy links the file really holds. */
data class HuntedFeed(val url: String, val links: Int)

data class HuntResult(val feeds: List<HuntedFeed>, val note: String)

/**
 * ZX Link Hunter — turns *any* GitHub-ish link (repo page, folder, blob page, raw file, gist, or
 * a plain https feed) into verified config feeds the Explorer can scan.
 */
object LinkHunter {
    private const val MAX_FILES = 14
    private const val MAX_BYTES = 6_000_000
    private val json = Json { ignoreUnknownKeys = true }
    private val goodExtensions = setOf("txt", "list", "conf", "sub", "yaml", "yml", "json", "b64", "base64", "")
    private val goodWords = listOf(
        "sub", "config", "vless", "vmess", "trojan", "ss", "hysteria", "tuic", "all", "mix", "merge",
        "base64", "v2ray", "proxy", "server", "reality", "best", "verified", "normal",
    )
    private val badNames = listOf("readme", "license", "changelog", "contributing", "package", "docker", ".git")

    fun looksLikeLink(text: String): Boolean {
        val t = text.trim().lowercase()
        if (t.any { it.isWhitespace() } || t.length < 8) return false
        return t.startsWith("http://") || t.startsWith("https://") ||
            t.startsWith("github.com/") || t.startsWith("www.github.com/") ||
            t.startsWith("raw.githubusercontent.com/") || t.startsWith("gist.github.com/")
    }

    /** Pure parsing — no network. */
    fun classify(input: String): HuntTarget {
        var text = input.trim().substringBefore('#').substringBefore('?')
        if (text.isEmpty() || text.any { it.isWhitespace() }) return HuntTarget.Unsupported("لینک خالی است یا فاصله دارد")
        if (!text.startsWith("http://") && !text.startsWith("https://")) text = "https://$text"
        text = text.replaceFirst("http://", "https://").trimEnd('/')
        val rest = text.removePrefix("https://")
        val host = rest.substringBefore('/').lowercase().removePrefix("www.")
        val parts = rest.substringAfter('/', "").split('/').filter { it.isNotEmpty() }
        return when (host) {
            "raw.githubusercontent.com", "gist.githubusercontent.com" -> HuntTarget.Feed(text)
            "gist.github.com" -> when {
                parts.size >= 2 -> HuntTarget.Feed("https://gist.githubusercontent.com/${parts[0]}/${parts[1]}/raw")
                else -> HuntTarget.Unsupported("آدرس Gist کامل نیست")
            }
            "github.com" -> classifyGithub(parts)
            else -> if (host.contains('.')) HuntTarget.Feed(text) else HuntTarget.Unsupported("آدرس معتبر نیست")
        }
    }

    private fun classifyGithub(parts: List<String>): HuntTarget {
        if (parts.size < 2) return HuntTarget.Unsupported("آدرس مخزن کامل نیست (github.com/کاربر/مخزن)")
        val owner = parts[0]
        val repo = parts[1].removeSuffix(".git")
        if (parts.size == 2) return HuntTarget.Folder(owner, repo, null, "")
        val kind = parts[2]
        val ref = parts.getOrNull(3)
        val path = parts.drop(4).joinToString("/")
        return when {
            (kind == "blob" || kind == "raw") && ref != null && path.isNotEmpty() ->
                HuntTarget.Feed("https://raw.githubusercontent.com/$owner/$repo/$ref/$path")
            kind == "tree" && ref != null -> HuntTarget.Folder(owner, repo, ref, path)
            kind == "tree" || kind == "blob" -> HuntTarget.Folder(owner, repo, null, "")
            else -> HuntTarget.Folder(owner, repo, null, "")
        }
    }

    suspend fun hunt(input: String): HuntResult = withContext(Dispatchers.IO) {
        when (val target = classify(input)) {
            is HuntTarget.Unsupported -> HuntResult(emptyList(), target.reason)
            is HuntTarget.Feed -> verify(listOf(target.url), "")
            is HuntTarget.Folder -> {
                val files = listFolder(target)
                if (files.isEmpty()) {
                    HuntResult(emptyList(), "در این مخزن فایلی شبیه کانفیگ پیدا نشد (یا GitHub و جی‌اس‌دلیور در دسترس نیستند)")
                } else verify(files, "از ${files.size} فایل مخزن بررسی شد؛ ")
            }
        }
    }

    private suspend fun verify(urls: List<String>, prefix: String): HuntResult {
        val checked = coroutineScope {
            urls.map { url -> async(Dispatchers.IO) { HuntedFeed(url, countLinks(url)) } }.awaitAll()
        }
        val good = checked.filter { it.links > 0 }.sortedByDescending { it.links }
        val total = good.sumOf { it.links }
        val note = if (good.isEmpty()) {
            prefix + "هیچ لینک کانفیگ سازگاری داخل این فایل‌ها نبود"
        } else prefix + good.size + " فید معتبر با مجموع " + total + " لینک"
        return HuntResult(good, note)
    }

    private fun mirrors(url: String): List<String> {
        val raw = "https://raw.githubusercontent.com/"
        if (!url.startsWith(raw)) return listOf(url)
        val parts = url.removePrefix(raw).split('/')
        if (parts.size < 4) return listOf(url)
        val path = parts.drop(3).joinToString("/")
        return listOf(url, "https://cdn.jsdelivr.net/gh/${parts[0]}/${parts[1]}@${parts[2]}/$path")
    }

    private fun countLinks(url: String): Int {
        for (candidate in mirrors(url)) {
            val body = runCatching {
                httpGetResponse(candidate, userAgent = "ZX-Link-Hunter/1.0", connectTimeoutMs = 8_000, readTimeoutMs = 15_000)
            }.getOrNull()
            if (body != null && body.code in 200..299 && body.body.length <= MAX_BYTES) {
                return runCatching { SubscriptionParser.extractLinks(body.body).distinct().size }.getOrDefault(0)
            }
        }
        return 0
    }

    private fun score(name: String): Int {
        val lower = name.lowercase()
        return goodWords.count { lower.contains(it) }
    }

    private fun acceptable(path: String, size: Long): Boolean {
        val name = path.substringAfterLast('/')
        val lower = name.lowercase()
        if (badNames.any { lower.startsWith(it) } || name.startsWith(".")) return false
        val ext = if (name.contains('.')) lower.substringAfterLast('.') else ""
        return ext in goodExtensions && (size <= 0 || size <= MAX_BYTES)
    }

    private fun rawUrl(owner: String, repo: String, ref: String, path: String) =
        "https://raw.githubusercontent.com/$owner/$repo/$ref/$path"

    private fun listFolder(target: HuntTarget.Folder): List<String> {
        // 1) GitHub contents API (exact, but rate limited and sometimes blocked).
        val viaApi = runCatching {
            val suffix = if (target.dir.isBlank()) "" else "/" + target.dir
            val ref = target.ref?.let { "?ref=$it" } ?: ""
            val response = httpGetResponse(
                "https://api.github.com/repos/${target.owner}/${target.repo}/contents$suffix$ref",
                userAgent = "ZX-Link-Hunter/1.0", connectTimeoutMs = 8_000, readTimeoutMs = 15_000,
            )
            if (response.code !in 200..299) return@runCatching emptyList()
            val array = json.parseToJsonElement(response.body) as? JsonArray ?: return@runCatching emptyList()
            array.mapNotNull { element ->
                val item = element as? JsonObject ?: return@mapNotNull null
                if (item["type"]?.jsonPrimitive?.contentOrNull != "file") return@mapNotNull null
                val path = item["path"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val size = item["size"]?.jsonPrimitive?.intOrNull?.toLong() ?: 0L
                val url = item["download_url"]?.jsonPrimitive?.contentOrNull
                if (url != null && acceptable(path, size)) path to url else null
            }
        }.getOrDefault(emptyList())
        if (viaApi.isNotEmpty()) return pick(viaApi.map { it.second to it.first })

        // 2) jsDelivr's file index: usually reachable where api.github.com is not.
        val refs = listOfNotNull(target.ref) + if (target.ref == null) listOf("main", "master") else emptyList()
        for (ref in refs) {
            val files = runCatching {
                val response = httpGetResponse(
                    "https://data.jsdelivr.com/v1/package/gh/${target.owner}/${target.repo}@$ref/flat",
                    userAgent = "ZX-Link-Hunter/1.0", connectTimeoutMs = 8_000, readTimeoutMs = 15_000,
                )
                if (response.code !in 200..299) return@runCatching emptyList()
                val array = json.parseToJsonElement(response.body).jsonObject["files"]?.jsonArray ?: return@runCatching emptyList()
                val prefix = if (target.dir.isBlank()) "/" else "/" + target.dir.trim('/') + "/"
                array.mapNotNull { element ->
                    val item = element as? JsonObject ?: return@mapNotNull null
                    val path = item["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val size = item["size"]?.jsonPrimitive?.intOrNull?.toLong() ?: 0L
                    if (path.startsWith(prefix) && acceptable(path, size)) {
                        rawUrl(target.owner, target.repo, ref, path.trimStart('/')) to path
                    } else null
                }
            }.getOrDefault(emptyList())
            if (files.isNotEmpty()) return pick(files)
        }
        return emptyList()
    }

    /** Shallow files first, then the ones whose names look most like configs. */
    private fun pick(files: List<Pair<String, String>>): List<String> =
        files.sortedWith(
            compareByDescending<Pair<String, String>> { score(it.second.substringAfterLast('/')) }
                .thenBy { it.second.count { c -> c == '/' } },
        ).map { it.first }.distinct().take(MAX_FILES)
}
