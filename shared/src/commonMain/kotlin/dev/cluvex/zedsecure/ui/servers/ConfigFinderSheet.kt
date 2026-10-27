@file:OptIn(
    kotlinx.coroutines.ExperimentalCoroutinesApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package dev.cluvex.zedsecure.ui.servers

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
    val latencyError: String? = null,
    val note: String = "",
)

private val finderCountries = listOf("همه کشورها", "آمریکا", "آلمان", "ترکیه", "کره جنوبی", "روسیه", "فرانسه", "هلند", "ژاپن", "سنگاپور")

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
    FinderSource("V2RayScrapeByCountry", "https://raw.githubusercontent.com/miladtahanian/V2RayScrapeByCountry/refs/heads/main/Countries/France.txt", "فرانسه"),
    FinderSource("V2RayScrapeByCountry", "https://raw.githubusercontent.com/miladtahanian/V2RayScrapeByCountry/refs/heads/main/Countries/Netherlands.txt", "هلند"),
    FinderSource("V2RayScrapeByCountry", "https://raw.githubusercontent.com/miladtahanian/V2RayScrapeByCountry/refs/heads/main/Countries/Japan.txt", "ژاپن"),
    FinderSource("V2RayScrapeByCountry", "https://raw.githubusercontent.com/miladtahanian/V2RayScrapeByCountry/refs/heads/main/Countries/Singapore.txt", "سنگاپور"),
    FinderSource("SoliSpirit/v2ray-configs", "https://raw.githubusercontent.com/SoliSpirit/v2ray-configs/main/Countries/France.txt", "فرانسه"),
    FinderSource("SoliSpirit/v2ray-configs", "https://raw.githubusercontent.com/SoliSpirit/v2ray-configs/main/Countries/Netherlands.txt", "هلند"),
    FinderSource("SoliSpirit/v2ray-configs", "https://raw.githubusercontent.com/SoliSpirit/v2ray-configs/main/Countries/Japan.txt", "ژاپن"),
    FinderSource("SoliSpirit/v2ray-configs", "https://raw.githubusercontent.com/SoliSpirit/v2ray-configs/main/Countries/Singapore.txt", "سنگاپور"),
    FinderSource("Delta-Kronecker/V2ray-Config", "https://raw.githubusercontent.com/Delta-Kronecker/V2ray-Config/main/config/countries/fr.txt", "فرانسه"),
    FinderSource("Delta-Kronecker/V2ray-Config", "https://raw.githubusercontent.com/Delta-Kronecker/V2ray-Config/main/config/countries/nl.txt", "هلند"),
    FinderSource("Delta-Kronecker/V2ray-Config", "https://raw.githubusercontent.com/Delta-Kronecker/V2ray-Config/main/config/countries/jp.txt", "ژاپن"),
    FinderSource("Delta-Kronecker/V2ray-Config", "https://raw.githubusercontent.com/Delta-Kronecker/V2ray-Config/main/config/countries/sg.txt", "سنگاپور"),
    FinderSource("Danialsamadi/v2go", "https://raw.githubusercontent.com/Danialsamadi/v2go/main/Splitted-By-Country/FR.txt", "فرانسه"),
    FinderSource("Danialsamadi/v2go", "https://raw.githubusercontent.com/Danialsamadi/v2go/main/Splitted-By-Country/NL.txt", "هلند"),
    FinderSource("Danialsamadi/v2go", "https://raw.githubusercontent.com/Danialsamadi/v2go/main/Splitted-By-Country/JP.txt", "ژاپن"),
    FinderSource("Danialsamadi/v2go", "https://raw.githubusercontent.com/Danialsamadi/v2go/main/Splitted-By-Country/SG.txt", "سنگاپور"),
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
    FinderSource("Top · FastNodes", "https://raw.githubusercontent.com/rtwo2/FastNodes/main/sub/top.txt", category = "سرعتی"),
    FinderSource("Verified · FastNodes", "https://raw.githubusercontent.com/rtwo2/FastNodes/main/sub/verified.txt", category = "سرعتی"),
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
    var onlyTested by remember { mutableStateOf(false) }
    var onlyRealDelaySuccess by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf("پیشنهادی") }
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

    val displayedResults = remember(
        results, sortMode, protocolFilter, onlyParserSupported, onlyTested, onlyRealDelaySuccess, query, country, category,
    ) {
        val q = query.trim().lowercase()
        val applyCountryFilter = category == "کشوری" || category == "همه منابع"
        val filtered = results.filter { item ->
            (!applyCountryFilter || country == "همه کشورها" || item.country == country || item.country == "نامشخص") &&
                (protocolFilter == "همه پروتکل‌ها" || item.protocol.contains(protocolFilter, ignoreCase = true)) &&
                (!onlyParserSupported || item.parserSupported) &&
                (!onlyTested || item.latencyTested) &&
                (!onlyRealDelaySuccess || (item.realDelayMs ?: -1L) > 0L) &&
                (q.isEmpty() || listOf(item.name, item.protocol, item.country, item.source, item.note)
                    .any { value -> value.lowercase().contains(q) })
        }
        when (sortMode) {
            "کیفیت فنی" -> filtered.sortedWith(
                compareByDescending<FinderResult> { finderQualityScore(it) }
                    .thenBy { it.realDelayMs?.takeIf { ms -> ms > 0L } ?: Long.MAX_VALUE }
            )
            "کمترین تأخیر" -> filtered.sortedWith(
                compareBy<FinderResult> { if ((it.realDelayMs ?: -1L) > 0L) 0 else 1 }
                    .thenBy { it.realDelayMs?.takeIf { ms -> ms > 0L } ?: Long.MAX_VALUE }
                    .thenByDescending { it.parserSupported }
            )
            "کشور" -> filtered.sortedWith(compareBy<FinderResult> { it.country }.thenByDescending { it.parserSupported })
            else -> filtered.sortedWith(compareByDescending<FinderResult> { it.parserSupported }
                .thenBy { if (it.latencyTested && (it.realDelayMs ?: -1L) <= 0L) 1 else 0 }
                .thenBy { it.country })
        }
    }

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
        val candidates = displayedResults
            .filter { it.parserSupported && it.payload != null }
            .take(MAX_LATENCY_CANDIDATES)
        if (candidates.isEmpty()) {
            status = "گزینهٔ قابل‌آزمایشی پیدا نشد؛ ابتدا جست‌وجو کن یا فیلتر قالب را بررسی کن."
            return
        }

        testingLatency = true
        latencyProgress = 0
        status = "در حال آزمایش ${candidates.size} گزینه؛ برای هر گزینه ۳ بار اتصال TCP و ۳ بار تأخیر از مسیر موتور اندازه‌گیری می‌شود. زمان و شبکه روی نتیجه اثر دارند."
        scope.launch {
            try {
                val measured = coroutineScope {
                    val permits = Semaphore(3)
                    candidates.map { candidate ->
                        async {
                            permits.withPermit {
                                try {
                                    val profile = profileForLatencyProbe(candidate)
                                    if (profile == null) {
                                        candidate.copy(
                                            tcpPingMs = -1L,
                                            realDelayMs = -1L,
                                            latencyTested = true,
                                            latencyError = "پروفایل آزمایشی ساخته نشد",
                                            note = "پروفایل آزمایشی ساخته نشد؛ این گزینه سالم یا قابل‌اتصال فرض نمی‌شود.",
                                        )
                                    } else {
                                        val tcp = medianOfSuccessfulSamples(LATENCY_SAMPLE_COUNT) {
                                            PingService.tcpPing(profile.address, profile.port, timeoutMs = 1_800)
                                        }
                                        val real = medianOfSuccessfulSamples(LATENCY_SAMPLE_COUNT) {
                                            kotlinx.coroutines.withTimeoutOrNull(10_000L) {
                                                PingService.realDelay(profile)
                                            } ?: -1L
                                        }
                                        candidate.copy(
                                            tcpPingMs = tcp,
                                            realDelayMs = real,
                                            latencyTested = true,
                                            latencyError = null,
                                            note = when {
                                                real > 0L -> "تأخیر واقعی: میانهٔ نمونه‌های موفق از مسیر موتور VPN؛ نتیجه فقط مربوط به همین شبکه و زمان آزمایش است."
                                                tcp > 0L -> "پورت TCP پاسخ داد، اما تأخیر از مسیر موتور VPN اندازه‌گیری نشد؛ اتصال VPN سالم فرض نمی‌شود."
                                                else -> "نمونهٔ موفقی ثبت نشد؛ سرور ممکن است خاموش، ناسازگار یا غیرقابل‌دسترسی باشد."
                                            },
                                        )
                                    }
                                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    candidate.copy(
                                        tcpPingMs = -1L,
                                        realDelayMs = -1L,
                                        latencyTested = true,
                                        latencyError = "خطای اندازه‌گیری",
                                        note = "اندازه‌گیری با خطا روبه‌رو شد؛ نتیجه اتصال ناموفق در نظر گرفته می‌شود.",
                                    )
                                } finally {
                                    latencyProgress = (latencyProgress + 1).coerceAtMost(candidates.size)
                                }
                            }
                        }
                    }.awaitAll()
                }

                val measuredById = measured.associateBy { it.id }
                results = results
                    .map { measuredById[it.id] ?: it }
                    .sortedWith(
                        compareBy<FinderResult> { if ((it.realDelayMs ?: -1L) > 0L) 0 else 1 }
                            .thenBy { it.realDelayMs?.takeIf { ms -> ms > 0L } ?: Long.MAX_VALUE }
                            .thenBy { it.tcpPingMs?.takeIf { ms -> ms > 0L } ?: Long.MAX_VALUE }
                            .thenByDescending { it.parserSupported }
                    )
                val successful = measured.count { (it.realDelayMs ?: -1L) > 0L }
                val tcpReachable = measured.count { (it.tcpPingMs ?: -1L) > 0L }
                status = "آزمایش تمام شد: تأخیر واقعی برای $successful از ${measured.size} گزینه اندازه‌گیری شد؛ اتصال TCP برای $tcpReachable گزینه پاسخ داد. ترتیب اصلی بر اساس میانهٔ تأخیر واقعی است و پینگ TCP فقط معیار ثانویه است؛ این عدد سرعت دانلود را نشان نمی‌دهد."
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                status = "آزمایش لغو شد؛ نتایج اندازه‌گیری‌نشده معتبر فرض نمی‌شوند."
                throw cancelled
            } catch (_: Exception) {
                status = "آزمایش کامل نشد؛ دوباره تلاش کن. نتایج نامعتبر به‌عنوان تأخیر موفق نمایش داده نمی‌شوند."
            } finally {
                testingLatency = false
            }
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
            outcome.onSuccess { fetched ->
                val found = fetched.results
                val q = query.trim().lowercase()
                results = found
                    // Filters stay live in displayedResults so changing search text or chips never needs another network fetch.
                    // Keep at most eight parser-compatible candidates per individual source list.
                    // This is a syntax/compatibility preference, not a claim that latency or safety was measured.
                    .groupBy { "${it.source}| ${it.country}" }
                    .values
                    .flatMap { sourceCandidates ->
                        sourceCandidates.sortedWith(compareByDescending<FinderResult> { candidate -> candidate.parserSupported }
                            .thenBy { candidate -> candidate.name }).take(8)
                    }
                    .groupBy { (it.payload ?: it.rawUrl ?: it.id).trim().lowercase() }
                    .values
                    .map { duplicates ->
                        duplicates.first().copy(source = duplicates.map { it.source }.distinct().joinToString(" · "))
                    }
                    .sortedWith(compareByDescending<FinderResult> { it.parserSupported }.thenBy { it.country }.thenBy { it.protocol })
                    .take(300)
                val failedSourceSummary = fetched.failedSourceNames.take(4).joinToString("، ")
                    .let { names -> if (names.isBlank()) "" else " منابع ناموفق: $names" }
                status = when {
                    results.isEmpty() && fetched.failedSources == fetched.totalSources ->
                        "هیچ‌کدام از منابع پاسخ ندادند؛ اتصال اینترنت را بررسی کن و دوباره جست‌وجو کن. (${fetched.failedSources}/${fetched.totalSources} منبع ناموفق)$failedSourceSummary"
                    results.isEmpty() ->
                        "نتیجه‌ای پیدا نشد؛ کشور یا دستهٔ دیگری را امتحان کن. منابع ناموفق: ${fetched.failedSources} از ${fetched.totalSources}.$failedSourceSummary"
                    fetched.failedSources > 0 ->
                        "تعداد گزینه‌ها: ${results.size} — ${fetched.failedSources} از ${fetched.totalSources} منبع پاسخ نداد؛ نتیجه ناقص است. حداکثر ۸ گزینه از هر فهرست؛ سرعت واقعی تضمین نشده است.$failedSourceSummary"
                    else ->
                        "تعداد گزینه‌ها: ${results.size} — همهٔ ${fetched.totalSources} منبع پاسخ دادند؛ حداکثر ۸ گزینه از هر فهرست. سالم‌بودن و سرعت واقعی هنوز با اتصال تست نشده است."
                }
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
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("همه منابع", "کشوری", "سرعتی", "تلگرام").forEach { item ->
                    FilterChip(selected = category == item, onClick = { category = item }, label = { Text(item) })
                }
            }
            if (category == "کشوری") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    finderCountries.forEach { item ->
                        FilterChip(selected = country == item, onClick = { country = item }, label = { Text(item) })
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("همه پروتکل‌ها", "VLESS", "VMess", "Trojan").forEach { item ->
                        FilterChip(selected = protocolFilter == item, onClick = { protocolFilter = item }, label = { Text(item) })
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Hysteria2", "Shadowsocks").forEach { item ->
                        FilterChip(selected = protocolFilter == item, onClick = { protocolFilter = item }, label = { Text(item) })
                    }
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = onlyParserSupported, onClick = { onlyParserSupported = !onlyParserSupported }, label = { Text("قالب‌های سازگار") })
                FilterChip(selected = onlyTested, onClick = { onlyTested = !onlyTested }, label = { Text("فقط آزمایش‌شده‌ها") })
                FilterChip(selected = onlyRealDelaySuccess, onClick = { onlyRealDelaySuccess = !onlyRealDelaySuccess }, label = { Text("تأخیر واقعی موفق") })
            }
            Text(
                if (category == "کشوری" || category == "همه منابع") {
                    "فیلتر کشور هم زنده روی نتایج اعمال می‌شود؛ برای دیدن همه کشورها، «همه کشورها» را انتخاب کن."
                } else {
                    "فیلترها روی نتایج فعلی و بدون دریافت دوبارهٔ منابع اعمال می‌شوند."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("مرتب‌سازی نتایج", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("پیشنهادی", "کیفیت فنی", "کمترین تأخیر", "کشور").forEach { mode ->
                    FilterChip(selected = sortMode == mode, onClick = { sortMode = mode }, label = { Text(mode) })
                }
            }
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
                enabled = !loading && !testingLatency && displayedResults.any { it.parserSupported && it.payload != null },
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (testingLatency) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("اندازه‌گیری پینگ و تأخیر واقعی")
            }
            if (testingLatency) {
                Text("پیشرفت آزمایش: $latencyProgress از ${displayedResults.count { it.parserSupported && it.payload != null }.coerceAtMost(MAX_LATENCY_CANDIDATES)}", style = MaterialTheme.typography.bodySmall)
            }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (displayedResults.isNotEmpty() && sortMode == "کمترین تأخیر" && displayedResults.none { (it.realDelayMs ?: -1L) > 0L }) {
                Text("هنوز تأخیر واقعی موفقی اندازه‌گیری نشده؛ برای رتبه‌بندی بر اساس شبکهٔ خودت، آزمایش تأخیر را اجرا کن.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
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
                if (displayedResults.isEmpty()) "نتیجه‌ای با این فیلترها پیدا نشد" else "نتایج فیلترشده (${displayedResults.size} از ${results.size}) · روی کارت بزن تا جزئیات را ببینی",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                items(displayedResults, key = { it.id }) { item ->
                    Surface(onClick = { openDetails(item) }, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(item.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${item.protocol} · ${item.country}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(item.source, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("شاخص غربالگری: ${finderQualityScore(item)}/100", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text("${item.source.split(" · ").distinct().size} منبع", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (item.latencyTested) {
                                val realText = item.realDelayMs?.takeIf { it > 0L }?.let { "$it ms" }
                                    ?: if (item.latencyError != null) "خطای تست" else "اندازه‌گیری موفق نشد"
                                val tcpText = item.tcpPingMs?.takeIf { it > 0L }?.let { "$it ms" } ?: "ناموفق"
                                Text(
                                    "تأخیر مسیر موتور VPN: $realText · پینگ TCP: $tcpText",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if ((item.realDelayMs ?: -1L) > 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                )
                            }
                            Text(
                                if (item.parserSupported) "قالب: پارسر آن را می‌شناسد؛ اتصال هنوز تضمین نیست" else "قالب: با پارسرهای فعلی تأیید نشده؛ افزودن/اتصال غیرفعال است",
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
                    Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            FinderInfoLine(
                                "نتیجهٔ تست",
                                when {
                                    item.latencyError != null -> "خطا: ${item.latencyError}"
                                    (item.realDelayMs ?: -1L) > 0L -> "تأخیر از مسیر موتور VPN اندازه‌گیری شد"
                                    (item.tcpPingMs ?: -1L) > 0L -> "فقط پورت TCP پاسخ داد؛ اتصال VPN تأیید نشد"
                                    else -> "نمونهٔ موفقی ثبت نشد"
                                },
                            )
                            FinderInfoLine("تأخیر مسیر موتور VPN", item.realDelayMs?.takeIf { it > 0L }?.let { "$it ms" } ?: "اندازه‌گیری موفق نشد")
                            FinderInfoLine("پینگ TCP", item.tcpPingMs?.takeIf { it > 0L }?.let { "$it ms" } ?: "ناموفق")
                        }
                        Text("شاخص غربالگری فقط برای مرتب‌سازی است؛ تضمین سرعت، اتصال، امنیت یا ناشناس‌ماندن نیست. کشور IP هم تخمین ژئولوکیشن است. کانفیگ ناشناس می‌تواند ریسک حریم خصوصی داشته باشد.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                confirmButton = {
                    Button(enabled = item.parserSupported && item.payload != null, onClick = { item.payload?.takeIf { item.parserSupported }?.let { link -> selectedResult = null; onConnect(link) } }) {
                        Text("افزودن و اتصال")
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(enabled = item.parserSupported && item.payload != null, onClick = { item.payload?.takeIf { item.parserSupported }?.let { link -> selectedResult = null; onImport(link) } }) { Text("فقط افزودن") }
                        TextButton(onClick = { selectedResult = null }) { Text("بستن") }
                    }
                },
            )
        }
    }
}

private fun finderQualityScore(item: FinderResult): Int {
    // Heuristic for screening only; this is not a safety, anonymity, connectivity, or throughput guarantee.
    var score = 0
    if (item.parserSupported) score += 25
    if (item.latencyTested) score += 10
    if ((item.realDelayMs ?: -1L) > 0L) {
        score += when (item.realDelayMs!!) {
            in 1L..100L -> 40
            in 101L..200L -> 32
            in 201L..400L -> 22
            in 401L..800L -> 12
            else -> 5
        }
    }
    if ((item.tcpPingMs ?: -1L) > 0L) score += 10
    val sources = item.source.split(" · ").distinct().size
    score += when {
        sources >= 3 -> 15
        sources == 2 -> 10
        else -> 0
    }
    return score.coerceIn(0, 100)
}

private const val MAX_LATENCY_CANDIDATES = 12
private const val LATENCY_SAMPLE_COUNT = 3

private suspend fun medianOfSuccessfulSamples(
    sampleCount: Int,
    measure: suspend () -> Long,
): Long {
    val successful = ArrayList<Long>(sampleCount)
    repeat(sampleCount.coerceAtLeast(1)) {
        val sample = try {
            measure()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            -1L
        }
        if (sample > 0L) successful += sample
    }
    if (successful.isEmpty()) return -1L
    val sorted = successful.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[middle]
    else sorted[middle - 1] / 2L + sorted[middle] / 2L
}

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

private data class SourceFetchResult(
    val results: List<FinderResult>,
    val failed: Boolean,
)

private data class FetchSourcesResult(
    val results: List<FinderResult>,
    val failedSources: Int,
    val totalSources: Int,
    val failedSourceNames: List<String>,
)

private suspend fun fetchSources(sources: List<FinderSource>): FetchSourcesResult = coroutineScope {
    val semaphore = Semaphore(4)
    val outcomes = sources.map { source ->
        async(Dispatchers.IO) {
            semaphore.withPermit {
                try {
                    val response = httpGetResponse(
                        source.url,
                        "ZX-VPN-ConfigFinder/1.0",
                        connectTimeoutMs = 8000,
                        readTimeoutMs = 12000,
                    )
                    if (response.code !in 200..299) {
                        return@withPermit SourceFetchResult(emptyList(), failed = true)
                    }
                    val body = response.body.take(512 * 1024)
                        .replace("&amp;", "&")
                        .replace("&#x2F;", "/")
                        .replace("&quot;", "\"")
                    val links = configLinkRegex.findAll(body)
                        .map { it.value.trimEnd(',', ';', '.', ')', ']', '}') }
                        .toList()
                    val parsed = links.mapIndexed { index, link ->
                        val parserSupported = runCatching {
                            ConfigParser.isSupportedLink(link) || SingBoxLinks.handles(link)
                        }.getOrDefault(false)
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
                    SourceFetchResult(parsed, failed = false)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    SourceFetchResult(emptyList(), failed = true)
                }
            }
        }
    }.awaitAll()

    FetchSourcesResult(
        results = outcomes.flatMap { it.results },
        failedSources = outcomes.count { it.failed },
        totalSources = sources.size,
        failedSourceNames = outcomes.mapIndexedNotNull { index, outcome ->
            sources.getOrNull(index)?.name?.takeIf { outcome.failed }
        }.distinct(),
    )
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
