@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.cluvex.zedsecure.ui.servers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cluvex.zedsecure.data.config.ConfigExplorerEngine
import dev.cluvex.zedsecure.data.config.ConfigExplorerEntry
import dev.cluvex.zedsecure.data.config.ConfigRepository
import dev.cluvex.zedsecure.platform.currentTimeMillis
import dev.cluvex.zedsecure.ui.platform.LocalPlatform
import dev.cluvex.zedsecure.ui.platform.rememberAppSheetState

@Composable
fun ConfigExplorerSheet(
    repository: ConfigRepository,
    onDismiss: () -> Unit,
    onServerActivated: () -> Unit,
) {
    val state by repository.configExplorer.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf("live") }
    var selectedEntry by rememberSaveable { mutableStateOf<ConfigExplorerEntry?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var protoFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var sort by rememberSaveable { mutableStateOf("latency") }
    val platform = LocalPlatform.current

    LaunchedEffect(repository) { ConfigExplorerEngine.start(repository) }
    // While the best list is still empty, show the live findings instead of a blank "preparing" page.
    LaunchedEffect(state.healthyPool.isEmpty(), state.liveEntries.isNotEmpty()) {
        if (tab == "best" && state.healthyPool.isEmpty() && state.liveEntries.isNotEmpty()) tab = "live"
    }

    val visibleHealthyCount = (state.healthyPool + state.liveEntries).distinctBy { it.id }.size

    val entries = when (tab) {
        "live" -> state.liveEntries
        "reserve" -> state.healthyPool.drop(ConfigExplorerEngine.BEST_LIMIT)
        "pool" -> state.healthyPool
        else -> state.healthyPool.take(ConfigExplorerEngine.BEST_LIMIT)
    }

    val protocols = entries.map { it.protocol }.distinct().sorted()
    val shown = entries
        .filter { protoFilter == null || it.protocol == protoFilter }
        .filter {
            val q = query.trim().lowercase()
            q.isEmpty() || it.name.lowercase().contains(q) || it.address.lowercase().contains(q) ||
                (it.countryCode?.lowercase() ?: "").contains(q) || it.protocol.lowercase().contains(q) ||
                it.sourceName.lowercase().contains(q)
        }
        .let { list ->
            when (sort) {
                "jitter" -> list.sortedBy { it.jitterMs }
                "quality" -> list.sortedByDescending { explorerQuality(it) }
                else -> list
            }
        }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberAppSheetState()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("کاوشگر ZX", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "کشف خودکار، آزمایش واقعی و رتبه‌بندی سرورها",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onDismiss) { Text("بستن") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExplorerMetric("کاندید", state.candidateCount.toString(), Modifier.weight(1f))
                ExplorerMetric("قابل‌تحلیل", state.parsedCount.toString(), Modifier.weight(1f))
                ExplorerMetric("سالم", visibleHealthyCount.toString(), Modifier.weight(1f))
                ExplorerMetric("برتر", state.healthyPool.take(ConfigExplorerEngine.BEST_LIMIT).size.toString(), Modifier.weight(1f))
            }

            val best = state.healthyPool.firstOrNull { it.profileId != null }
            if (best != null) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "بهترین سرور فعلی: " + (best.countryCode?.let(::countryFlag) ?: "🌐") + " " +
                                best.protocol + " · " + best.medianMs + " ms · امتیاز " + explorerQuality(best),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        val pool = state.healthyPool
                        val protoStats = pool.groupingBy { it.protocol }.eachCount().entries
                            .sortedByDescending { it.value }.take(4)
                            .joinToString(" · ") { it.key + " " + it.value }
                        val countryStats = pool.mapNotNull { it.countryCode }.groupingBy { it }.eachCount().entries
                            .sortedByDescending { it.value }.take(5)
                            .joinToString("  ") { countryFlag(it.key) + it.value }
                        if (protoStats.isNotBlank()) {
                            Text(protoStats, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        if (countryStats.isNotBlank()) {
                            Text(countryStats, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Button(
                            onClick = { best.profileId?.let { repository.setActive(it); onServerActivated(); onDismiss() } },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("اتصال سریع به بهترین سرور") }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("چرخه خودکار", fontWeight = FontWeight.SemiBold)
                            Text(
                                "اجرای خودکار هر ۱۵ دقیقه (پیش‌فرض خاموش). با دکمهٔ «شروع کاوش» هر وقت خواستید دستی اجرا کنید",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.enabled,
                            onCheckedChange = { ConfigExplorerEngine.setEnabled(repository, it) },
                        )
                    }
                    if (state.isRunning) {
                        Text(explorerPhaseLabel(state.phase), style = MaterialTheme.typography.labelMedium)
                        val fraction = if (state.progressTotal > 0) {
                            state.progressDone.toFloat() / state.progressTotal.toFloat()
                        } else 0f
                        LinearProgressIndicator(
                            progress = fraction.coerceIn(0f, 1f),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            state.progressDone.toString() + " / " + state.progressTotal,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            state.lastError ?: state.lastRunAt?.let { last ->
                                "آخرین چرخه " + relativeAgo(last) + " · چرخهٔ بعدی " + relativeIn(last + ConfigExplorerEngine.INTERVAL_MS)
                            } ?: "هنوز چرخه‌ای اجرا نشده است",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.lastError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.isRunning) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = { ConfigExplorerEngine.stop(repository) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("توقف کاوش (یافته‌ها حفظ می‌شود)") }
                    } else {
                        Button(
                            onClick = { ConfigExplorerEngine.runNow(repository) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("شروع کاوش") }
                    }
                    Text(
                        "چرخه " + state.cycleCount + " · تعداد تست‌ها " + state.testedCount,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ExplorerTab("یافته‌های زنده " + state.liveEntries.size, tab == "live") { tab = "live" }
                ExplorerTab("۱۰۰ برتر", tab == "best") { tab = "best" }
                ExplorerTab("ذخیره جایگزین", tab == "reserve") { tab = "reserve" }
                ExplorerTab("همه سالم‌ها", tab == "pool") { tab = "pool" }
                ExplorerTab("منابع", tab == "sources") { tab = "sources" }
            }

            HorizontalDivider()

            if (tab != "sources") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("جست‌وجو (نام، آدرس، کشور، پروتکل، منبع)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(selected = protoFilter == null, onClick = { protoFilter = null }, label = { Text("همه") })
                    protocols.forEach { proto ->
                        FilterChip(
                            selected = protoFilter == proto,
                            onClick = { protoFilter = if (protoFilter == proto) null else proto },
                            label = { Text(proto) },
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(selected = sort == "latency", onClick = { sort = "latency" }, label = { Text("تأخیر") })
                    FilterChip(selected = sort == "jitter", onClick = { sort = "jitter" }, label = { Text("کمترین نوسان") })
                    FilterChip(selected = sort == "quality", onClick = { sort = "quality" }, label = { Text("امتیاز کیفیت") })
                    Text(
                        shown.size.toString() + " نتیجه",
                        Modifier.padding(start = 6.dp, top = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (tab == "sources") {
                ConfigExplorerEngine.sources.forEach { source ->
                    val status = state.sourceStatuses.firstOrNull { it.sourceId == source.id }
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(source.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Text(
                                when {
                                    source.feedUrl == null -> "مرجع مکمل"
                                    status == null -> "در انتظار"
                                    status.ok -> "دریافت موفق"
                                    else -> "خطا"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    source.feedUrl == null -> MaterialTheme.colorScheme.onSurfaceVariant
                                    status?.ok == true -> MaterialTheme.colorScheme.primary
                                    status == null -> MaterialTheme.colorScheme.onSurfaceVariant
                                    else -> MaterialTheme.colorScheme.error
                                },
                            )
                        }
                        Text(
                            source.repositoryUrl,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            status?.let { it.fetchedCount.toString() + " لینک · " + it.message } ?: source.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider()
                }
            } else if (shown.isEmpty()) {
                Text(
                    if (state.isRunning) "هنوز نتیجه سالمی تأیید نشده؛ با پایان اولین آزمایش‌ها، نتایج همین‌جا ظاهر می‌شوند." else "هنوز کانفیگ سالم تأییدشده‌ای در این فهرست وجود ندارد.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 18.dp),
                )
            } else {
                shown.forEachIndexed { index, entry ->
                    ExplorerEntryRow(
                        index = index + 1,
                        entry = entry,
                        isReserve = tab == "reserve",
                        onClick = { selectedEntry = entry },
                        onPromote = { ConfigExplorerEngine.promoteReserve(repository, entry.id) },
                    )
                    HorizontalDivider()
                }
            }

            Text(
                if (tab == "live") "نتایج به‌محض تأیید مجدد وارد این فهرست می‌شوند؛ اطلاعات IP و کشور در پس‌زمینه تکمیل می‌شود." else "سلامت یعنی حداقل دو موفقیت از سه آزمایش HTTPS از مسیر هسته ZX؛ تست TCP به‌تنهایی کافی نیست.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    selectedEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { selectedEntry = null },
            title = { Text(entry.name.ifBlank { entry.protocol + " · " + entry.address }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text((entry.countryCode?.let(::countryFlag) ?: "🌐") + "  " + (entry.countryCode ?: "کشور نامشخص"))
                    Text("IP: " + (entry.ipAddress ?: "در حال شناسایی یا نامشخص"))
                    Text("سرور: " + entry.address + ":" + entry.port)
                    Text("پروتکل: " + entry.protocol)
                    Text("منبع: " + entry.sourceName)
                    Text("نمونه‌های تأخیر: " + entry.samplesMs.joinToString("، ") + " ms")
                    Text("میانه: " + entry.medianMs + " ms · میانگین: " + entry.meanMs + " ms")
                    Text("نوسان: " + entry.jitterMs + " ms")
                    Text("امتیاز کیفیت: " + explorerQuality(entry) + " / 100")
                    Text("آخرین بررسی: " + relativeAgo(entry.lastCheckedAt))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    entry.profileId?.let { repository.setActive(it); onServerActivated(); onDismiss() }
                    selectedEntry = null
                }, enabled = entry.profileId != null) { Text("اتصال به این سرور") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { platform.copyToClipboard(entry.link); platform.toast("لینک کپی شد") }) { Text("کپی لینک") }
                    TextButton(onClick = { selectedEntry = null }) { Text("بستن") }
                }
            },
        )
    }
}

private fun countryFlag(code: String): String {
    if (code.length != 2) return "🌐"
    val base = 0x1F1E6
    return code.uppercase().map { char ->
        val point = base + char.code - 'A'.code
        val high = ((point - 0x10000) shr 10) + 0xD800
        val low = ((point - 0x10000) and 0x3FF) + 0xDC00
        "" + high.toChar() + low.toChar()
    }.joinToString("")
}

@Composable
private fun ExplorerMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 11.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ExplorerTab(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun ExplorerEntryRow(
    index: Int,
    entry: ConfigExplorerEntry,
    isReserve: Boolean,
    onClick: () -> Unit,
    onPromote: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(index.toString().padStart(2, '0'), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
                Text((entry.countryCode?.let(::countryFlag) ?: "🌐") + "  " + entry.name.ifBlank { entry.protocol + " · " + entry.address }, fontWeight = FontWeight.SemiBold)
                Text(
                    entry.protocol + " · " + entry.address + ":" + entry.port + " · " + entry.sourceName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(entry.medianMs.toString() + " ms", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("★ " + explorerQuality(entry), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                Text("نوسان " + entry.jitterMs + " ms", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (isReserve) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onPromote) { Text("جایگزین در ۱۰۰ برتر") }
            }
        }
    }
}

private fun explorerPhaseLabel(phase: String): String = when (phase) {
    "collecting" -> "در حال دریافت فیدهای کانفیگ"
    "parsing" -> "در حال بررسی ساختار و سازگاری کانفیگ‌ها"
    "testing_first" -> "تست واقعی تأخیر · مرحله اول"
    "testing_second" -> "تست واقعی تأخیر · مرحله دوم"
    "rechecking" -> "تأیید مجدد پایداری کانفیگ‌های برتر"
    "publishing" -> "در حال همگام‌سازی ۱۰۰ سرور برتر"
    "error" -> "چرخه با خطا متوقف شد"
    else -> "در حال اجرا"
}

/** 0..100 score from median latency and jitter: lower is better, stable beats fast-but-jumpy. */
internal fun explorerQuality(entry: ConfigExplorerEntry): Int =
    (100 - entry.medianMs / 12 - entry.jitterMs / 8).coerceIn(1, 100)

private fun relativeAgo(timestamp: Long): String {
    val minutes = ((currentTimeMillis() - timestamp) / 60_000L).coerceAtLeast(0L)
    return when {
        minutes < 1 -> "هم‌اکنون"
        minutes < 60 -> minutes.toString() + " دقیقه پیش"
        minutes < 1_440 -> (minutes / 60).toString() + " ساعت پیش"
        else -> (minutes / 1_440).toString() + " روز پیش"
    }
}

private fun relativeIn(timestamp: Long): String {
    val minutes = ((timestamp - currentTimeMillis()) / 60_000L)
    return if (minutes <= 0L) "به‌زودی" else "تا " + minutes.toString() + " دقیقه دیگر"
}
