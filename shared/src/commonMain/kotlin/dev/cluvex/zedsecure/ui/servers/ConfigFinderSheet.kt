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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import dev.cluvex.zedsecure.data.net.PingService
import dev.cluvex.zedsecure.data.net.GeoLookup
import dev.cluvex.zedsecure.domain.config.ConfigParser
import dev.cluvex.zedsecure.domain.config.ProfileSource
import dev.cluvex.zedsecure.domain.config.SingBoxJson
import dev.cluvex.zedsecure.domain.config.SingBoxLinks
import dev.cluvex.zedsecure.domain.config.VpnProfile
import dev.cluvex.zedsecure.platform.httpGetResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

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
    val parserSupported: Boolean = false,
    val tcpPingMs: Long? = null,
    val realDelayMs: Long? = null,
    val latencyTested: Boolean = false,
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

private val configLinkRegex = Regex("""(?i)(?:vless|vmess|trojan|ss|socks5?|http|hysteria2?|hy2|tuic|wireguard|awg|ssh|vpn)://[^\s"<>]+""")

@Composable
fun ConfigFinderSheet(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
    onConnect: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var category by remember { mutableStateOf("همه منابع") }
    var country by remember { mutableStateOf("همه کشورها") }
    var protocolFilter by remember { mutableStateOf("همه پروتکل‌ها") }
    var onlyParserSupported by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var testingLatency by remember { mutableStateOf(false) }
    var latencyProgress by remember { mutableStateOf(0) }
    var results by remember { mutableStateOf<List<FinderResult>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("منابع فقط هنگام جست‌وجوی شما خوانده می‌شوند؛ سرعت واقعی هنوز اندازه‌گیری نشده است.") }
    var selectedResult by remember { mutableStateOf<FinderResult?>(null) }
    var detectedCountry by remember { mutableStateOf<String?>(null) }
    var geoLoading by remember { mutableStateOf(false) }
    var selectedHost by remember { mutableStateOf<String?>(null) }
    var selectedPort by remember { mutableStateOf<Int?>(null) }

    fun openDetails(item: FinderResult) {
        selectedResult = item
        detectedCountry = null
        selectedHost = null
        selectedPort = null
        geoLoading = true
        scope.launch {
            val profile = runCatching { profileForLatencyProbe(item) }.getOrNull()
            selectedHost = profile?.address
            selectedPort = profile?.port
            detectedCountry = profile?.address?.let { host ->
                runCatching { GeoLookup.countryOf(host) }.getOrNull()
            }?.let(::countryNameFromCode)
            geoLoading = false
        }
    }

    fun testLatency() {
        if (loading || testingLatency || results.isEmpty()) return
        val candidates = results.filter { it.parserSupported && it.payload != null }.take(MAX_LATENCY_CANDIDATES)
        if (candidates.isEmpty()) {
            status = "گزینهٔ قابل‌آزمایشی پیدا نشد؛ ابتدا جست‌وجو کن یا فیلتر قالب را بررسی کن."
            return
        }
        testingLatency = true
        latencyProgress = 0
        status = "در حال اندازه‌گیری پینگ TCP و تأخیر واقعی از مسیر موتور VPN برای ${candidates.size} گزینه؛ حداکثر $MAX_LATENCY_CANDIDATES گزینه بررسی می‌شوند."
        scope.launch {
            val measured = coroutineScope {
                val permits = Semaphore(3)
                candidates.map { candidate ->
                    async {
                        permits.withPermit {
                            val result = runCatching {
                                val profile = profileForLatencyProbe(candidate)
                                    ?: return@runCatching candidate.copy(
                                        tcpPingMs = -1L,
                                        realDelayMs = -1L,
                                        latencyTested = true,
                                        note = "قالب شناسایی شد اما ساخت پروفایل آزمایشی ناموفق بود.",
                                    )
                                val tcp = runCatching {
                                    PingService.tcpPing(profile.address, profile.port, timeoutMs = 1_800)
                                }.getOrDefault(-1L)
                                val real = runCatching {
                                    kotlinx.coroutines.withTimeoutOrNull(12_000L) {
                                        PingService.realDelay(profile)
                                    } ?: -1L
                                }.getOrDefault(-1L)
                                candidate.copy(
                                    tcpPingMs = tcp,
                                    realDelayMs = real,
                                    latencyTested = true,
                                    note = if (real > 0L) {
                                        "تأخیر واقعی اندازه‌گیری شد؛ نتیجه مربوط به همین شبکه و زمان آزمایش است."
                                    } else {
                                        "تأخیر واقعی اندازه‌گیری نشد؛ ممکن است سرور خاموش، ناسازگار یا در دسترس نباشد."
                                    },
                                )
                            }.getOrElse {
                                candidate.copy(
                                    tcpPingMs = -1L,
                                    realDelayMs = -1L,
                                    latencyTested = true,
                                    note = "اندازه‌گیری ناموفق بود؛ اتصال سالم فرض نمی‌شود.",
                                )
                            }
                            latencyProgress += 1
                            result
                        }
                    }
                }.awaitAll()
            }
            val measuredIds = candidates.map { it.id }.toSet()
            results = (results.filterNot { it.id in measuredIds } + measured)
                .sortedWith(
                    compareBy<FinderResult> { if (it.realDelayMs != null && it.realDelayMs > 0L) 0 else 1 }
                        .thenBy { it.realDelayMs?.takeIf { ms -> ms > 0L } ?: Long.MAX_VALUE }
                        .thenBy { if (it.tcpPingMs != null && it.tcpPingMs > 0L) it.tcpPingMs else Long.MAX_VALUE }
                        .thenByDescending { it.parserSupported }
                )
            val successful = measured.count { (it.realDelayMs ?: -1L) > 0L }
            status = "آزمایش تمام شد: ${successful} از ${measured.size} گزینه پاسخ واقعی دادند. ترتیب بر اساس تأخیر واقعی، سپس پینگ TCP است؛ این اعداد تضمین سرعت دانلود نیستند."
            testingLatency = false
        }
    }

    fun search() {
        if (loading) return
        loading = true
        error = ""
        results = emptyList()
        scope.launch {
            val outcome = runCatching {
                val sources = when (category) {
                            "کشوری" -> countrySources.filter { country == "همه کشورها" || it.country == country }
                            "سرعتی" -> speedSources
                            "تلگرام" -> telegramSources
                            "همه منابع" -> countrySources + speedSources + telegramSources
                            else -> countrySources + speedSources + telegramSources
                        }
                fetchSources(sources)
            }
            outcome.onSuccess { found ->
                val q = query.trim().lowercase()
                results = found
                    .filter { protocolFilter == "همه پروتکل‌ها" || it.protocol.contains(protocolFilter, ignoreCase = true) }
                    .filter { !onlyParserSupported || it.parserSupported }
                    .filter { q.isEmpty() || listOf(it.name, it.protocol, it.country, it.source, it.note).any { s -> s.lowercase().contains(q) } }
                    // Keep at most two parser-compatible candidates per individual source list.
                    // This is a syntax/compatibility preference, not a claim that latency or safety was measured.
                    .groupBy { "${it.source}| ${it.country}" }
                    .values
                    .flatMap { sourceCandidates ->
                        sourceCandidates.sortedByDescending { candidate -> candidate.parserSupported }.take(2)
                    }
                    .groupBy { (it.payload ?: it.rawUrl ?: it.id).trim().lowercase() }
                    .values
                    .map { duplicates ->
                        duplicates.first().copy(source = duplicates.map { it.source }.distinct().joinToString(" · "))
                    }
                    .sortedWith(compareByDescending<FinderResult> { it.parserSupported }.thenBy { it.country }.thenBy { it.protocol })
                    .take(300)
                status = if (results.isEmpty()) "نتیجه‌ای پیدا نشد؛ کشور یا دستهٔ دیگری را امتحان کن." else "تعداد گزینه‌ها: ${results.size} — حداکثر ۲ گزینه از هر فهرست؛ سالم‌بودن و سرعت واقعی هنوز با اتصال تست نشده است."
            }.onFailure {
                error = it.message?.take(240) ?: "خواندن منابع ناموفق بود."
            }
            loading = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.94f).padding(horizontal = 18.dp).padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().weight(1.15f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
            Text("کانفینگ‌یاب", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("کانفیگ‌ها را پیدا کن، روی هر مورد بزن تا جزئیات و کشور واقعیِ تخمینی بر اساس IP را ببینی؛ بعد می‌توانی همان مورد را به فهرست اضافه و برای اتصال انتخاب کنی.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("همه منابع", "کشوری", "سرعتی", "تلگرام").forEach { item ->
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
                    listOf("Hysteria2", "Shadowsocks").forEach { item ->
                        FilterChip(selected = protocolFilter == item, onClick = { protocolFilter = item }, label = { Text(item) })
                    }
                }
            }
            FilterChip(
                selected = onlyParserSupported,
                onClick = { onlyParserSupported = !onlyParserSupported },
                label = { Text("فقط قالب‌های قابل‌شناسایی") },
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("فیلتر نتایج (پروتکل، منبع یا کشور)") },
            )
            Button(onClick = { search() }, enabled = !loading && !testingLatency, modifier = Modifier.fillMaxWidth()) {
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(when (category) {
                    "همه منابع" -> "جست‌وجوی هم‌زمان همه منابع"
                    else -> "جست‌وجوی منابع"
                })
            }
            Button(
                onClick = { testLatency() },
                enabled = !loading && !testingLatency && results.any { it.parserSupported && it.payload != null },
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (testingLatency) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("اندازه‌گیری پینگ و تأخیر واقعی")
            }
            if (testingLatency) {
                Text("پیشرفت آزمایش: $latencyProgress از ${results.count { it.parserSupported && it.payload != null }.coerceAtMost(MAX_LATENCY_CANDIDATES)}", style = MaterialTheme.typography.bodySmall)
            }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (loading) Text("در حال بررسی منابع مجاز…", style = MaterialTheme.typography.bodyMedium)
            if (results.isNotEmpty()) {
                val parserReady = results.count { it.parserSupported }
                val protocolsFound = results.map { it.protocol }.distinct().size
                Text(
                    "خلاصهٔ نتایج: $parserReady از ${results.size} گزینه قالب قابل‌شناسایی دارند · $protocolsFound نوع پروتکل",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                val sourceCount = results.flatMap { it.source.split(" · ") }.distinct().size
                val countryCount = results.map { it.country }.filter { it != "نامشخص" }.distinct().size
                Text(
                    "پوشش جست‌وجو: $sourceCount منبع · $countryCount کشور · اولویت نمایش با قالب‌های قابل‌شناسایی است، نه ادعای سرعت",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            }
            Text(
                if (results.isEmpty()) "نتایج اینجا نمایش داده می‌شوند" else "نتایج (\${results.size}) · روی یک کارت بزن تا جزئیات را ببینی",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                items(results, key = { it.id }) { item ->
                    Surface(onClick = { openDetails(item) }, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(item.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${item.protocol} · ${item.country}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(item.source, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (item.latencyTested) {
                                val realText = item.realDelayMs?.takeIf { it > 0L }?.let { "$it ms" } ?: "پاسخ نداد"
                                val tcpText = item.tcpPingMs?.takeIf { it > 0L }?.let { "$it ms" } ?: "ناموفق"
                                Text(
                                    "تأخیر واقعی از مسیر VPN: $realText · پینگ TCP سرور: $tcpText",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if ((item.realDelayMs ?: -1L) > 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                )
                            }
                            Text(
                                if (item.parserSupported) "قالب: قابل‌شناسایی توسط پارسر" else "قالب: نیازمند بررسی هنگام افزودن",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (item.parserSupported) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (item.note.isNotBlank()) Text(item.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("برای مشاهدهٔ IP، کشور و اقدام اتصال روی کارت بزن", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        selectedResult?.let { item ->
            val profile = runCatching { profileForLatencyProbe(item) }.getOrNull()
            AlertDialog(
                onDismissRequest = { selectedResult = null },
                title = { Text(item.name, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(item.protocol, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        FinderInfoLine("میزبان", selectedHost ?: profile?.address ?: "قالب هنوز قابل‌تجزیه نیست")
                        FinderInfoLine("پورت", (selectedPort ?: profile?.port)?.toString() ?: "نامشخص")
                        FinderInfoLine("کشور در منبع", item.country)
                        FinderInfoLine("کشور بر اساس IP", when {
                            geoLoading -> "در حال بررسی IP…"
                            detectedCountry != null -> detectedCountry!!
                            else -> "قابل‌تشخیص نبود؛ کشور منبع ممکن است با موقعیت واقعی فرق داشته باشد"
                        })
                        FinderInfoLine("منبع", item.source)
                        FinderInfoLine("سازگاری", if (item.parserSupported) "پارسر این قالب را می‌شناسد" else "قالب هنوز تأیید نشده")
                        if (item.latencyTested) {
                            FinderInfoLine("تأخیر واقعی", item.realDelayMs?.takeIf { it > 0L }?.let { "$it ms" } ?: "پاسخی ثبت نشد")
                            FinderInfoLine("پینگ TCP", item.tcpPingMs?.takeIf { it > 0L }?.let { "$it ms" } ?: "ناموفق")
                        }
                        Text("کشور IP یک تخمین ژئولوکیشن است، نه تضمین محل فیزیکی سرور. افزودن کانفیگ ناشناس ریسک حریم خصوصی دارد.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                confirmButton = {
                    Button(enabled = item.payload != null, onClick = { item.payload?.let { link -> selectedResult = null; onConnect(link) } }) {
                        Text("افزودن و اتصال")
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(enabled = item.payload != null, onClick = { item.payload?.let { link -> selectedResult = null; onImport(link) } }) { Text("فقط افزودن") }
                        TextButton(onClick = { selectedResult = null }) { Text("بستن") }
                    }
                },
            )
        }
        }
    }
}

private const val MAX_LATENCY_CANDIDATES = 12

private fun profileForLatencyProbe(candidate: FinderResult): VpnProfile? {
    val link = candidate.payload?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (SingBoxLinks.handles(link)) {
        val parsed = SingBoxLinks.parse(link)
        val server = SingBoxJson.servers(parsed.fragment).firstOrNull() ?: return null
        return VpnProfile.fromSingBox(
            server = server,
            id = candidate.id,
            addedAt = 0L,
            name = candidate.name,
            link = link,
        )
    }
    val config = ConfigParser.parse(link)
    return VpnProfile(
        id = candidate.id,
        name = candidate.name,
        protocol = config.protocol.id.uppercase(),
        address = config.address,
        port = config.port,
        transportLabel = config.transportLabel,
        source = ProfileSource.Link(link),
        addedAt = 0L,
    )
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
                    val links = configLinkRegex.findAll(body).map { it.value.trimEnd(',', ';', '.', ')', ']', '}') }.toList()
                    links.mapIndexed { index, link ->
                        val parserSupported = runCatching { ConfigParser.isSupportedLink(link) }.getOrDefault(false)
                        FinderResult(
                            id = "${source.url.hashCode()}:${index}:${link.hashCode()}",
                            name = "کانفیگ ${index + 1}",
                            protocol = protocolLabel(link),
                            country = source.country,
                            source = source.name,
                            payload = link,
                            parserSupported = parserSupported,
                            note = if (
                                parserSupported ||
                                link.startsWith("vpn://", ignoreCase = true) ||
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

private fun protocolLabel(link: String): String {
    val scheme = link.substringBefore("://", "").lowercase()
    return when (scheme) {
        "vless" -> if (link.contains("security=reality", ignoreCase = true)) "VLESS / Reality" else "VLESS"
        "vmess" -> "VMess"
        "trojan" -> "Trojan"
        "ss" -> "Shadowsocks"
        "hysteria", "hysteria2", "hy2" -> "Hysteria2"
        "tuic" -> "TUIC"
        "wireguard", "awg" -> "WireGuard"
        "socks", "socks5" -> "SOCKS5"
        "http" -> "HTTP Proxy"
        "ssh" -> "SSH"
        "vpn" -> "Amnezia"
        else -> scheme.uppercase().ifBlank { "نامشخص" }
    }
}

@Composable
private fun FinderInfoLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private fun countryNameFromCode(code: String): String = when (code.uppercase()) {
    "US" -> "🇺🇸 آمریکا (US)"
    "DE" -> "🇩🇪 آلمان (DE)"
    "TR" -> "🇹🇷 ترکیه (TR)"
    "KR" -> "🇰🇷 کره جنوبی (KR)"
    "RU" -> "🇷🇺 روسیه (RU)"
    "IR" -> "🇮🇷 ایران (IR)"
    "GB" -> "🇬🇧 بریتانیا (GB)"
    "FR" -> "🇫🇷 فرانسه (FR)"
    "NL" -> "🇳🇱 هلند (NL)"
    "JP" -> "🇯🇵 ژاپن (JP)"
    "SG" -> "🇸🇬 سنگاپور (SG)"
    "CA" -> "🇨🇦 کانادا (CA)"
    "FI" -> "🇫🇮 فنلاند (FI)"
    "PL" -> "🇵🇱 لهستان (PL)"
    "SE" -> "🇸🇪 سوئد (SE)"
    "CH" -> "🇨🇭 سوئیس (CH)"
    "UA" -> "🇺🇦 اوکراین (UA)"
    "AE" -> "🇦🇪 امارات (AE)"
    "HK" -> "🇭🇰 هنگ‌کنگ (HK)"
    "IN" -> "🇮🇳 هند (IN)"
    "BR" -> "🇧🇷 برزیل (BR)"
    else -> "$code (کشور)"
}
