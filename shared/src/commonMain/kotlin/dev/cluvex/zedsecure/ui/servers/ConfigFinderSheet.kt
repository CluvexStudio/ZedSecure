@file:OptIn(
    kotlinx.coroutines.ExperimentalCoroutinesApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package dev.cluvex.zedsecure.ui.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.cluvex.zedsecure.domain.config.ConfigParser
import dev.cluvex.zedsecure.domain.config.NpvTunnelParser
import dev.cluvex.zedsecure.platform.httpGetResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

private data class FinderSource(
    val name: String,
    val url: String,
    val country: String = "نامشخص",
    val category: String = "کشوری",
)

private data class FinderResult(
    val id: String,
    val name: String,
    val protocol: String,
    val country: String,
    val source: String,
    val payload: String? = null,
    val rawUrl: String? = null,
    val note: String = "",
)

private val finderCountries = listOf("همه کشورها", "آمریکا", "آلمان", "ترکیه", "کره جنوبی", "روسیه")

private val countrySources = listOf(
    FinderSource("V2RayScrapeByCountry", "https://raw.githubusercontent.com/miladtahanian/V2RayScrapeByCountry/refs/heads/main/Countries/United_States.txt", "آمریکا"),
    FinderSource("V2RayScrapeByCountry", "https://raw.githubusercontent.com/miladtahanian/V2RayScrapeByCountry/refs/heads/main/Countries/Germany.txt", "آلمان"),
    FinderSource("V2RayScrapeByCountry", "https://raw.githubusercontent.com/miladtahanian/V2RayScrapeByCountry/refs/heads/main/Countries/Türkiye.txt", "ترکیه"),
    FinderSource("V2RayScrapeByCountry", "https://raw.githubusercontent.com/miladtahanian/V2RayScrapeByCountry/refs/heads/main/Countries/South_Korea.txt", "کره جنوبی"),
    FinderSource("V2RayScrapeByCountry", "https://raw.githubusercontent.com/miladtahanian/V2RayScrapeByCountry/refs/heads/main/Countries/Russia.txt", "روسیه"),
    FinderSource("SoliSpirit/v2ray-configs", "https://raw.githubusercontent.com/SoliSpirit/v2ray-configs/main/Countries/United_States.txt", "آمریکا"),
    FinderSource("SoliSpirit/v2ray-configs", "https://raw.githubusercontent.com/SoliSpirit/v2ray-configs/main/Countries/Germany.txt", "آلمان"),
    FinderSource("SoliSpirit/v2ray-configs", "https://raw.githubusercontent.com/SoliSpirit/v2ray-configs/main/Countries/Türkiye.txt", "ترکیه"),
    FinderSource("SoliSpirit/v2ray-configs", "https://raw.githubusercontent.com/SoliSpirit/v2ray-configs/main/Countries/South_Korea.txt", "کره جنوبی"),
    FinderSource("SoliSpirit/v2ray-configs", "https://raw.githubusercontent.com/SoliSpirit/v2ray-configs/main/Countries/Russia.txt", "روسیه"),
    FinderSource("Delta-Kronecker/V2ray-Config", "https://raw.githubusercontent.com/Delta-Kronecker/V2ray-Config/main/config/countries/us.txt", "آمریکا"),
    FinderSource("Delta-Kronecker/V2ray-Config", "https://raw.githubusercontent.com/Delta-Kronecker/V2ray-Config/main/config/countries/de.txt", "آلمان"),
    FinderSource("Delta-Kronecker/V2ray-Config", "https://raw.githubusercontent.com/Delta-Kronecker/V2ray-Config/main/config/countries/tr.txt", "ترکیه"),
    FinderSource("Delta-Kronecker/V2ray-Config", "https://raw.githubusercontent.com/Delta-Kronecker/V2ray-Config/main/config/countries/kr.txt", "کره جنوبی"),
    FinderSource("Delta-Kronecker/V2ray-Config", "https://raw.githubusercontent.com/Delta-Kronecker/V2ray-Config/main/config/countries/ru.txt", "روسیه"),
    FinderSource("Danialsamadi/v2go", "https://raw.githubusercontent.com/Danialsamadi/v2go/main/Splitted-By-Country/US.txt", "آمریکا"),
    FinderSource("Danialsamadi/v2go", "https://raw.githubusercontent.com/Danialsamadi/v2go/main/Splitted-By-Country/DE.txt", "آلمان"),
    FinderSource("Danialsamadi/v2go", "https://raw.githubusercontent.com/Danialsamadi/v2go/main/Splitted-By-Country/TR.txt", "ترکیه"),
    FinderSource("Danialsamadi/v2go", "https://raw.githubusercontent.com/Danialsamadi/v2go/main/Splitted-By-Country/KR.txt", "کره جنوبی"),
    FinderSource("Danialsamadi/v2go", "https://raw.githubusercontent.com/Danialsamadi/v2go/main/Splitted-By-Country/RU.txt", "روسیه"),
)

private val speedSources = listOf(
    FinderSource("Reality · morpheusadam", "https://raw.githubusercontent.com/morpheusadam/v2ray-config/main/subs/bundles/reality.txt", category = "سرعتی"),
    FinderSource("Best · morpheusadam", "https://raw.githubusercontent.com/morpheusadam/v2ray-config/main/subs/bundles/best.txt", category = "سرعتی"),
    FinderSource("Iran · morpheusadam", "https://raw.githubusercontent.com/morpheusadam/v2ray-config/main/subs/bundles/iran.txt", "ایران", "سرعتی"),
    FinderSource("Hysteria2 · MatinGhanbari", "https://raw.githubusercontent.com/MatinGhanbari/v2ray-configs/main/subscriptions/filtered/subs/hysteria2.txt", category = "سرعتی"),
    FinderSource("Fast · 0xRadikal", "https://raw.githubusercontent.com/0xRadikal/Free-v2ray-Configs/main/fast/configs.txt", category = "سرعتی"),
    FinderSource("Verified · 0xRadikal", "https://raw.githubusercontent.com/0xRadikal/Free-v2ray-Configs/main/verified/configs.txt", category = "سرعتی"),
    FinderSource("Top5 · PulseConfigs", "https://raw.githubusercontent.com/drmikecrypto/PulseConfigs/main/top5.txt", category = "سرعتی"),
    FinderSource("Top5 Speed · PulseConfigs", "https://raw.githubusercontent.com/drmikecrypto/PulseConfigs/main/top5_speed.txt", category = "سرعتی"),
    FinderSource("Top100 · FastNodes", "https://raw.githubusercontent.com/rtwo2/FastNodes/main/sub/Best-Results/top100.txt", category = "سرعتی"),
    FinderSource("Top500 · FastNodes", "https://raw.githubusercontent.com/rtwo2/FastNodes/main/sub/Best-Results/top500.txt", category = "سرعتی"),
)

private val telegramSources = listOf(
    FinderSource("Telegram · hddify", "https://t.me/s/hddify", category = "تلگرام"),
    FinderSource("Telegram · mitivpn", "https://t.me/s/mitivpn", category = "تلگرام"),
)

private val finderRepositories = listOf(
    "miladtahanian/V2RayScrapeByCountry",
    "SoliSpirit/v2ray-configs",
    "Delta-Kronecker/V2ray-Config",
    "Danialsamadi/v2go",
    "morpheusadam/v2ray-config",
    "MatinGhanbari/v2ray-configs",
    "0xRadikal/Free-v2ray-Configs",
    "drmikecrypto/PulseConfigs",
    "rtwo2/FastNodes",
)

private val finderJson = Json { ignoreUnknownKeys = true; isLenient = true }
private val configLinkRegex = Regex("""(?i)(?:vless|vmess|trojan|ss|socks|http|hysteria2?|tuic|wireguard|awg|ssh|vpn|npvt)://[^\s"'<>]+""")

@Composable
fun ConfigFinderSheet(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var category by remember { mutableStateOf("کشوری") }
    var country by remember { mutableStateOf("همه کشورها") }
    var protocolFilter by remember { mutableStateOf("همه پروتکل‌ها") }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<FinderResult>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("منابع فقط هنگام جست‌وجوی شما خوانده می‌شوند؛ سرعت واقعی هنوز اندازه‌گیری نشده است.") }

    fun search() {
        if (loading) return
        loading = true
        error = ""
        results = emptyList()
        scope.launch {
            val outcome = runCatching {
                when (category) {
                    "NPV / مخازن" -> scanNpvRepositories()
                    else -> {
                        val sources = when (category) {
                            "کشوری" -> countrySources.filter { country == "همه کشورها" || it.country == country }
                            "سرعتی" -> speedSources
                            "تلگرام" -> telegramSources
                            else -> countrySources + speedSources + telegramSources
                        }
                        fetchSources(sources)
                    }
                }
            }
            outcome.onSuccess { found ->
                val q = query.trim().lowercase()
                results = found
                    .filter { protocolFilter == "همه پروتکل‌ها" || it.protocol.contains(protocolFilter, ignoreCase = true) }
                    .filter { q.isEmpty() || listOf(it.name, it.protocol, it.country, it.source, it.note).any { s -> s.lowercase().contains(q) } }
                    .groupBy { (it.payload ?: it.rawUrl ?: it.id).trim().lowercase() }
                    .values
                    .map { duplicates ->
                        duplicates.first().copy(source = duplicates.map { it.source }.distinct().joinToString(" · "))
                    }
                    .take(300)
                status = if (results.isEmpty()) "نتیجه‌ای پیدا نشد؛ کشور یا دستهٔ دیگری را امتحان کن." else "تعداد نتیجه: ${results.size} — «قالب معتبر» به معنی امنیت یا اتصال موفق نیست."
            }.onFailure {
                error = it.message?.take(240) ?: "خواندن منابع ناموفق بود."
            }
            loading = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("کانفینگ‌یاب", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("جست‌وجوی منابع عمومی؛ قبل از افزودن، هر کانفیگ را بررسی کن.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("کشوری", "سرعتی", "تلگرام", "NPV / مخازن").forEach { item ->
                    FilterChip(selected = category == item, onClick = { category = item }, label = { Text(item) })
                }
            }
            if (category == "کشوری") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    finderCountries.forEach { item ->
                        FilterChip(selected = country == item, onClick = { country = item }, label = { Text(item) })
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("همه پروتکل‌ها", "VLESS", "VMess", "Trojan").forEach { item ->
                        FilterChip(selected = protocolFilter == item, onClick = { protocolFilter = item }, label = { Text(item) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Hysteria2", "Shadowsocks", "NPV").forEach { item ->
                        FilterChip(selected = protocolFilter == item, onClick = { protocolFilter = item }, label = { Text(item) })
                    }
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("فیلتر نتایج (پروتکل، منبع یا کشور)") },
            )
            Button(onClick = { search() }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(if (category == "NPV / مخازن") "پیدا کردن فایل‌های NPV در GitHub" else "جست‌وجوی منابع")
            }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (loading) Text("در حال بررسی منابع مجاز…", style = MaterialTheme.typography.bodyMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().height(360.dp)) {
                items(results, key = { it.id }) { item ->
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(item.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${item.protocol} · ${item.country}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(item.source, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (item.note.isNotBlank()) Text(item.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { item.rawUrl?.let { url -> scope.launch { runCatching { withContext(Dispatchers.IO) { httpGetResponse(url, "ZX-VPN-ConfigFinder/1.0", connectTimeoutMs = 8000, readTimeoutMs = 12000) } }.onSuccess { res -> if (res.code in 200..299) onImport(res.body.take(1_048_576)) else error = "HTTP ${res.code} هنگام دریافت فایل" }.onFailure { error = it.message?.take(200) ?: "دریافت فایل ناموفق بود" } } } }, enabled = item.rawUrl != null) { Text("دریافت و افزودن") }
                                TextButton(onClick = { item.payload?.let(onImport) }, enabled = item.payload != null) { Text("افزودن") }
                            }
                        }
                    }
                }
            }
            Text("منابع ناشناس ممکن است ناامن یا غیرفعال باشند. تست سرعت/اتصال فقط پس از افزودن و با موتور بومی انجام می‌شود.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private suspend fun fetchSources(sources: List<FinderSource>): List<FinderResult> = coroutineScope {
    val semaphore = Semaphore(4)
    sources.map { source ->
        async(Dispatchers.IO) {
            semaphore.withPermit {
                runCatching {
                    val response = httpGetResponse(source.url, "ZX-VPN-ConfigFinder/1.0", connectTimeoutMs = 8000, readTimeoutMs = 12000)
                    if (response.code !in 200..299) return@withPermit emptyList()
                    val body = response.body.take(512 * 1024)
                        .replace("&amp;", "&")
                        .replace("&#x2F;", "/")
                        .replace("&quot;", "\"")
                    val fullNpv = body.trim()
                    if (source.category == "NPV" || NpvTunnelParser.isNpvText(fullNpv)) {
                        if (runCatching { NpvTunnelParser.isNpvText(fullNpv) }.getOrDefault(false)) {
                            return@withPermit listOf(FinderResult(
                                id = "${source.name}:${source.url}",
                                name = "NPV / کانفیگ بسته‌ای",
                                protocol = "NPV",
                                country = source.country,
                                source = source.name,
                                payload = fullNpv,
                                note = "فقط قالب متنی شناخته‌شده؛ قالب رمزگذاری‌شده ممکن است به کلید نیاز داشته باشد.",
                            ))
                        }
                    }
                    val links = configLinkRegex.findAll(body).map { it.value.trimEnd(',', ';', '.', ')', ']', '}') }.toList()
                    links.mapIndexed { index, link ->
                        FinderResult(
                            id = "${source.name}:${index}:${link.hashCode()}",
                            name = "کانفیگ ${index + 1}",
                            protocol = protocolLabel(link),
                            country = source.country,
                            source = source.name,
                            payload = link,
                            note = if (
                                runCatching { ConfigParser.isSupportedLink(link) }.getOrDefault(false) ||
                                link.startsWith("vpn://", ignoreCase = true) ||
                                link.startsWith("npvt://", ignoreCase = true) ||
                                link.startsWith("ssh://", ignoreCase = true)
                            ) "شناسهٔ پروتکل شناخته شد؛ اتصال و سرعت هنوز تست نشده است."
                            else "سازگاری نهایی هنگام افزودن توسط پارسر بومی بررسی می‌شود.",
                        )
                    }
                }.getOrDefault(emptyList())
            }
        }
    }.awaitAll().flatten()
}

private suspend fun scanNpvRepositories(): List<FinderResult> = coroutineScope {
    val discovered = runCatching {
        val response = httpGetResponse(
            "https://api.github.com/search/repositories?q=NPV%20Tunnel&sort=updated&per_page=10",
            "ZX-VPN-ConfigFinder/1.0",
            connectTimeoutMs = 8000,
            readTimeoutMs = 12000,
        )
        if (response.code !in 200..299) emptyList()
        else finderJson.parseToJsonElement(response.body).jsonObject["items"]?.jsonArray.orEmpty()
            .mapNotNull { it.jsonObject["full_name"]?.jsonPrimitive?.contentOrNull }
    }.getOrDefault(emptyList())
    val repositories = (finderRepositories + discovered).distinct().take(20)
    val semaphore = Semaphore(3)
    repositories.map { repo ->
        async(Dispatchers.IO) {
            semaphore.withPermit {
                runCatching {
                    val meta = httpGetResponse("https://api.github.com/repos/$repo", "ZX-VPN-ConfigFinder/1.0", connectTimeoutMs = 8000, readTimeoutMs = 12000)
                    if (meta.code !in 200..299) return@withPermit emptyList()
                    val branch = finderJson.parseToJsonElement(meta.body).jsonObject["default_branch"]?.jsonPrimitive?.contentOrNull ?: "main"
                    val treeResponse = httpGetResponse("https://api.github.com/repos/$repo/git/trees/$branch?recursive=1", "ZX-VPN-ConfigFinder/1.0", connectTimeoutMs = 8000, readTimeoutMs = 15000)
                    if (treeResponse.code !in 200..299) return@withPermit emptyList()
                    val tree = finderJson.parseToJsonElement(treeResponse.body).jsonObject["tree"]?.jsonArray.orEmpty()
                    tree.mapNotNull { node ->
                        val path = node.jsonObject["path"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                        val lower = path.lowercase()
                        val isNpv = lower.endsWith(".npv") || lower.endsWith(".npvt") ||
                            lower.endsWith(".npvtsub") || lower.contains("npvt") ||
                            (lower.contains("npv") && (lower.endsWith(".json") || lower.endsWith(".txt")))
                        if (!isNpv || lower.endsWith(".npvs")) return@mapNotNull null
                        val rawUrl = "https://raw.githubusercontent.com/$repo/$branch/$path"
                        FinderResult(
                            id = "$repo:$path",
                            name = path,
                            protocol = "NPV احتمالی",
                            country = "نامشخص",
                            source = repo,
                            rawUrl = rawUrl,
                            note = "مسیر از درخت مخزن پیدا شد؛ سازگاری پس از دریافت و تحلیل مشخص می‌شود.",
                        )
                    }.take(25)
                }.getOrDefault(emptyList())
            }
        }
    }.awaitAll().flatten()
}

private fun protocolLabel(link: String): String {
    val scheme = link.substringBefore("://", "").lowercase()
    return when (scheme) {
        "vless" -> if (link.contains("security=reality", ignoreCase = true)) "VLESS / Reality" else "VLESS"
        "vmess" -> "VMess"
        "trojan" -> "Trojan"
        "ss" -> "Shadowsocks"
        "hysteria", "hysteria2" -> "Hysteria2"
        "tuic" -> "TUIC"
        "wireguard", "awg" -> "WireGuard"
        "ssh" -> "SSH"
        "vpn" -> "Amnezia"
        "npvt" -> "NPV Tunnel"
        else -> scheme.uppercase().ifBlank { "نامشخص" }
    }
}
