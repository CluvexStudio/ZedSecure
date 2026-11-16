@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.cluvex.zedsecure.ui.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.cluvex.zedsecure.domain.model.DnsResolverProfile
import dev.cluvex.zedsecure.ui.platform.appVerticalScroll
import dev.cluvex.zedsecure.ui.platform.rememberAppSheetState

@Composable
internal fun DnsProfileSheet(
    initial: DnsResolverProfile? = null,
    initialName: String = "",
    onDismiss: () -> Unit,
    onSave: (String, DnsResolverProfile) -> Unit,
) {
    var name by remember(initial?.id) { mutableStateOf(initialName.ifBlank { initial?.name ?: "Cloudflare DNS" }) }
    var address by remember(initial?.id) { mutableStateOf(initial?.directAddress ?: "1.1.1.1") }
    var protocol by remember(initial?.id) { mutableStateOf(initial?.protocol ?: "udp") }
    var error by remember { mutableStateOf("") }
    val validIp = remember(address) {
        val value = address.trim()
        value.isNotBlank() && dev.cluvex.zedsecure.domain.config.XrayJsonBuilder.isIpLiteral(value)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberAppSheetState()) {
        Column(
            Modifier.fillMaxWidth().imePadding().appVerticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("افزودن پروفایل DNS", style = MaterialTheme.typography.titleLarge)
            Text("این پروفایل فقط درخواست‌های DNS را از تونل اصلی عبور می‌دهد؛ سایر ترافیک اینترنت از مسیر عادی دستگاه می‌رود.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Cloudflare" to "1.1.1.1", "Google" to "8.8.8.8", "Quad9" to "9.9.9.9", "AdGuard" to "94.140.14.14").forEach { (label, ip) ->
                    AssistChip(onClick = { name = "$label DNS"; address = ip }, label = { Text(label) })
                }
            }
            OutlinedTextField(value = name, onValueChange = { name = it; error = "" }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("نام پروفایل") })
            OutlinedTextField(value = address, onValueChange = { address = it.trim(); error = "" }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("آدرس IP سرور DNS") }, supportingText = { Text("IPv4 یا IPv6؛ برای نسخه فعلی از IP مستقیم استفاده کن.") })
            Text("انتقال DNS", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { protocol = "udp" }, label = { Text(if (protocol == "udp") "✓ UDP" else "UDP") })
                AssistChip(onClick = { protocol = "tcp" }, label = { Text(if (protocol == "tcp") "✓ TCP" else "TCP") })
            }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = {
                    if (name.isBlank()) error = "نام پروفایل را وارد کن."
                    else if (!validIp) error = "آدرس باید یک IP معتبر باشد."
                    else {
                        val ip = address.trim()
                        val endpoint = if (protocol == "tcp") "tcp://$ip:53" else "$ip:53"
                        onSave(name.trim(), DnsResolverProfile(id = initial?.id ?: "dns-${kotlin.random.Random.nextLong()}", name = name.trim(), protocol = protocol, remoteEndpoint = endpoint, directAddress = ip))
                    }
                },
                enabled = name.isNotBlank() && validIp,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("ذخیره در فهرست کانفیگ‌ها") }
        }
    }
}
