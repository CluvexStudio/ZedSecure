@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.cluvex.zedsecure.ui.servers

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.cluvex.zedsecure.domain.model.AppSettings
import dev.cluvex.zedsecure.domain.model.DnsResolverProfile
import dev.cluvex.zedsecure.ui.platform.rememberAppSheetState
import dev.cluvex.zedsecure.platform.DnsRecordType
import dev.cluvex.zedsecure.platform.DnsResult
import dev.cluvex.zedsecure.platform.DnsTransport
import dev.cluvex.zedsecure.platform.dnsQuery
import kotlinx.coroutines.launch
import kotlin.random.Random

private data class DnsPreset(
    val name: String,
    val protocol: String,
    val endpoint: String,
    val direct: String,
)

private val dnsPresets = listOf(
    DnsPreset("Cloudflare · 1.1.1.1", "doh", "https://cloudflare-dns.com/dns-query", "1.1.1.1"),
    DnsPreset("Cloudflare Security · 1.1.1.2", "doh", "https://security.cloudflare-dns.com/dns-query", "1.1.1.2"),
    DnsPreset("Cloudflare Family · 1.1.1.3", "doh", "https://family.cloudflare-dns.com/dns-query", "1.1.1.3"),
    DnsPreset("Google · 8.8.8.8", "doh", "https://dns.google/dns-query", "8.8.8.8"),
    DnsPreset("Google · 8.8.4.4", "doh", "https://dns.google/dns-query", "8.8.4.4"),
    DnsPreset("Quad9 · 9.9.9.9", "doh", "https://dns.quad9.net/dns-query", "9.9.9.9"),
    DnsPreset("Quad9 · 149.112.112.112", "doh", "https://dns.quad9.net/dns-query", "149.112.112.112"),
    DnsPreset("AdGuard · 94.140.14.14", "doh", "https://dns.adguard.com/dns-query", "94.140.14.14"),
    DnsPreset("AdGuard · 94.140.15.15", "doh", "https://dns.adguard.com/dns-query", "94.140.15.15"),
    DnsPreset("CleanBrowsing Family · 185.228.168.168", "doh", "https://doh.cleanbrowsing.org/doh/family-filter/", "185.228.168.168"),
    DnsPreset("OpenDNS · 208.67.222.222", "doh", "https://doh.opendns.com/dns-query", "208.67.222.222"),
    DnsPreset("OpenDNS · 208.67.220.220", "doh", "https://doh.opendns.com/dns-query", "208.67.220.220"),
    DnsPreset("Cloudflare DoT · 1.1.1.1", "dot", "tls://1.1.1.1:853", "1.1.1.1"),
    DnsPreset("Google UDP · 8.8.8.8", "udp", "8.8.8.8:53", "8.8.8.8"),
    DnsPreset("Cloudflare TCP · 1.1.1.1", "tcp", "tcp://1.1.1.1:53", "1.1.1.1"),
)

@Composable
internal fun DnsResolverSheet(
    settings: AppSettings,
    onDismiss: () -> Unit,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
) {
    val active = settings.dnsResolverProfiles.firstOrNull { it.id == settings.activeDnsResolverId }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var protocol by rememberSaveable { mutableStateOf("doh") }
    var endpoint by rememberSaveable { mutableStateOf("") }
    var direct by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf("") }
    var testStatus by rememberSaveable { mutableStateOf("") }
    var testing by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun startEdit(profile: DnsResolverProfile) {
        editingId = profile.id
        name = profile.name
        protocol = profile.protocol
        endpoint = profile.remoteEndpoint
        direct = profile.directAddress
        error = ""
    }

    fun clearForm() {
        editingId = null
        name = ""
        protocol = "doh"
        endpoint = ""
        direct = ""
        error = ""
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberAppSheetState(),
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("DNS Resolver", style = MaterialTheme.typography.headlineSmall)
            Text(
                "مدیریت DNS مستقل از DNS Tunnel؛ پروفایل انتخاب‌شده در تنظیمات موتور اتصال استفاده می‌شود.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("DNS فعال", style = MaterialTheme.typography.labelLarge)
                    Text(
                        active?.name ?: "استفاده از DNS پیش‌فرض برنامه",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        active?.remoteEndpoint ?: settings.remoteDns,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            Text("پروفایل‌های ذخیره‌شده", style = MaterialTheme.typography.titleMedium)
            if (settings.dnsResolverProfiles.isEmpty()) {
                Text("هنوز پروفایلی ذخیره نشده است.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            settings.dnsResolverProfiles.sortedByDescending { it.favorite }.forEach { profile ->
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = if (profile.id == settings.activeDnsResolverId) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(profile.name, style = MaterialTheme.typography.titleSmall)
                        Text("${profile.protocol.uppercase()} · ${profile.remoteEndpoint}", style = MaterialTheme.typography.bodySmall)
                        Text("IP Resolver / Bootstrap: ${profile.directAddress}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = {
                                onUpdate { it.copy(activeDnsResolverId = profile.id) }
                            }) { Text(if (profile.id == settings.activeDnsResolverId) "فعال" else "فعال‌سازی") }
                            TextButton(onClick = {
                                onUpdate { old -> old.copy(dnsResolverProfiles = old.dnsResolverProfiles.map { if (it.id == profile.id) it.copy(favorite = !it.favorite) else it }) }
                            }) { Text(if (profile.favorite) "حذف از محبوب‌ها" else "★ محبوب") }
                            TextButton(onClick = { startEdit(profile) }) { Text("ویرایش") }
                            TextButton(onClick = {
                                onUpdate {
                                    val remaining = it.dnsResolverProfiles.filterNot { item -> item.id == profile.id }
                                    it.copy(
                                        dnsResolverProfiles = remaining,
                                        activeDnsResolverId = if (it.activeDnsResolverId == profile.id) remaining.firstOrNull()?.id.orEmpty() else it.activeDnsResolverId,
                                    )
                                }
                                if (editingId == profile.id) clearForm()
                            }) { Text("حذف") }
                        }
                    }
                }
            }

            Text(if (editingId == null) "افزودن پروفایل DNS" else "ویرایش پروفایل DNS", style = MaterialTheme.typography.titleMedium)
            Text("پروفایل‌های آماده و IP Resolver", style = MaterialTheme.typography.labelLarge)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                dnsPresets.forEach { preset ->
                    AssistChip(onClick = {
                        name = preset.name
                        protocol = preset.protocol
                        endpoint = preset.endpoint
                        direct = preset.direct
                        error = ""
                    }, label = { Text(preset.name) })
                }
            }
            Text("نوع اتصال", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("udp", "tcp", "dot", "doh").forEach { value ->
                    AssistChip(
                        onClick = {
                            protocol = value
                            endpoint = when (value) {
                                "udp" -> ""
                                "tcp" -> "tcp://"
                                "dot" -> "tls://"
                                else -> "https://"
                            }
                        },
                        label = { Text(value.uppercase()) },
                    )
                }
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; error = "" },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("نام پروفایل") },
            )
            OutlinedTextField(
                value = endpoint,
                onValueChange = { endpoint = it.trim(); error = "" },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("آدرس DNS") },
                supportingText = { Text("DoH: https://…  ·  DoT: tls://host:853  ·  UDP/TCP: scheme://IP:53") },
            )
            OutlinedTextField(
                value = direct,
                onValueChange = { direct = it.trim(); error = "" },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("DNS مستقیم / Bootstrap (IP)") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = !testing, onClick = {
                    val value = endpoint.trim()
                    val transport = when (protocol) {
                        "udp" -> DnsTransport.UDP
                        "tcp" -> DnsTransport.TCP
                        "dot" -> DnsTransport.DOT
                        else -> DnsTransport.DOH
                    }
                    val hostPort = when (protocol) {
                        "doh" -> value
                        "dot" -> value.removePrefix("tls://")
                        "tcp" -> value.removePrefix("tcp://")
                        else -> value
                    }
                    val parsed = splitDnsHostPort(hostPort, when (protocol) { "dot" -> 853; "doh" -> 443; else -> 53 })
                    val server = if (protocol == "doh") value else parsed.first
                    val port = if (protocol == "doh") 443 else parsed.second
                    if (server.isBlank()) {
                        testStatus = "آدرس DNS معتبر نیست."
                    } else {
                        testing = true
                        testStatus = "در حال بررسی پاسخ DNS…"
                        scope.launch {
                            val result = runCatching {
                                dnsQuery(server, port, "example.com", DnsRecordType.A, transport, 3500)
                            }.getOrNull()
                            testStatus = when (result) {
                                is DnsResult.Ok -> "اتصال موفق · ${result.reply.elapsedMs} ms · ${result.reply.answerCount} پاسخ"
                                is DnsResult.Failed -> "آزمایش ناموفق: ${result.reason}"
                                null -> "آزمایش DNS ناموفق بود."
                            }
                            testing = false
                        }
                    }
                }) { Text(if (testing) "در حال تست…" else "تست اتصال DNS") }
                if (testStatus.isNotBlank()) {
                    Text(testStatus, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
                }
            }
            if (error.isNotBlank()) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val normalized = endpoint.trim()
                    val expectedScheme = when (protocol) {
                        "udp" -> ""
                        "tcp" -> "tcp://"
                        "dot" -> "tls://"
                        else -> "https://"
                    }
                    val validEndpoint = when (protocol) {
                        "udp" -> normalized.isNotBlank() && !normalized.contains("://")
                        else -> normalized.startsWith(expectedScheme) && normalized.length > expectedScheme.length
                    }
                    if (name.isBlank()) {
                        error = "برای پروفایل یک نام وارد کن."
                    } else if (!validEndpoint) {
                        error = "آدرس با پروتکل انتخاب‌شده هم‌خوانی ندارد."
                    } else if (direct.isBlank()) {
                        error = "آدرس IP برای DNS مستقیم الزامی است."
                    } else {
                        val id = editingId ?: Random.nextLong().toString()
                        val profile = DnsResolverProfile(id, name.trim(), protocol, normalized, direct.trim())
                        onUpdate { old ->
                            val updated = if (editingId == null) old.dnsResolverProfiles + profile
                            else old.dnsResolverProfiles.map { if (it.id == editingId) profile else it }
                            old.copy(dnsResolverProfiles = updated, activeDnsResolverId = id)
                        }
                        clearForm()
                    }
                }) { Text("ذخیره و فعال‌سازی") }
                TextButton(onClick = { clearForm() }) { Text("پاک‌کردن فرم") }
            }
            Text(
                "تغییر DNS با اتصال مجدد VPN اعمال می‌شود. DNS مستقل سیستم و DNS داخل برنامه‌های دیگر ممکن است از این تنظیم پیروی نکنند.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}


private fun splitDnsHostPort(value: String, defaultPort: Int): Pair<String, Int> {
    val trimmed = value.trim()
    if (trimmed.startsWith("[")) {
        val end = trimmed.indexOf(']')
        if (end > 0) {
            val port = trimmed.substring(end + 1).removePrefix(":").toIntOrNull() ?: defaultPort
            return trimmed.substring(1, end) to port
        }
    }
    val colon = trimmed.lastIndexOf(':')
    if (colon > 0 && trimmed.indexOf(':') == colon) {
        return trimmed.substring(0, colon) to (trimmed.substring(colon + 1).toIntOrNull() ?: defaultPort)
    }
    return trimmed to defaultPort
}
