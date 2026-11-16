@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.cluvex.zedsecure.ui.servers

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.cluvex.zedsecure.domain.model.AppSettings
import dev.cluvex.zedsecure.domain.model.DnsResolverProfile
import dev.cluvex.zedsecure.ui.platform.rememberAppSheetState

private data class DnsPreset(
    val name: String,
    val protocol: String,
    val endpoint: String,
    val direct: String,
)

private val dnsPresets = listOf(
    DnsPreset("Cloudflare", "doh", "https://cloudflare-dns.com/dns-query", "1.1.1.1"),
    DnsPreset("Google", "doh", "https://dns.google/dns-query", "8.8.8.8"),
    DnsPreset("Quad9", "doh", "https://dns.quad9.net/dns-query", "9.9.9.9"),
    DnsPreset("AdGuard", "doh", "https://dns.adguard.com/dns-query", "94.140.14.14"),
    DnsPreset("CleanBrowsing Family", "doh", "https://doh.cleanbrowsing.org/doh/family-filter/", "185.228.168.168"),
    DnsPreset("Cloudflare DoT", "dot", "tls://1.1.1.1:853", "1.1.1.1"),
    DnsPreset("Google UDP", "udp", "8.8.8.8:53", "8.8.8.8"),
    DnsPreset("Cloudflare TCP", "tcp", "tcp://1.1.1.1:53", "1.1.1.1"),
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
            settings.dnsResolverProfiles.forEach { profile ->
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = if (profile.id == settings.activeDnsResolverId) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(profile.name, style = MaterialTheme.typography.titleSmall)
                        Text("${profile.protocol.uppercase()} · ${profile.remoteEndpoint}", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = {
                                onUpdate { it.copy(activeDnsResolverId = profile.id) }
                            }) { Text(if (profile.id == settings.activeDnsResolverId) "فعال" else "فعال‌سازی") }
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
            Text("پروفایل آماده", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                dnsPresets.take(4).forEach { preset ->
                    AssistChip(onClick = {
                        name = preset.name
                        protocol = preset.protocol
                        endpoint = preset.endpoint
                        direct = preset.direct
                        error = ""
                    }, label = { Text(preset.name) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                dnsPresets.drop(4).forEach { preset ->
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
                        val id = editingId ?: "${normalized.hashCode()}-${name.trim().hashCode()}"
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
