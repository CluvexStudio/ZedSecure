package dev.cluvex.zedsecure.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.cluvex.zedsecure.core.VpnManager
import dev.cluvex.zedsecure.domain.model.ConnectionState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect

private data class GhostAssistSnapshot(
    val state: ConnectionState = ConnectionState.Idle,
    val kind: String? = null,
    val downloadBps: Long = 0L,
    val uploadBps: Long = 0L,
    val totalDownload: Long = 0L,
    val totalUpload: Long = 0L,
)

@Composable
internal fun GhostAssistPanel(
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var snapshot by remember { mutableStateOf(GhostAssistSnapshot()) }

    LaunchedEffect(enabled) {
        if (!enabled) {
            snapshot = GhostAssistSnapshot()
            return@LaunchedEffect
        }

        combine(VpnManager.status, VpnManager.activeKind) { status, kind ->
            GhostAssistSnapshot(
                state = status.state,
                kind = kind,
                downloadBps = status.downloadBps,
                uploadBps = status.uploadBps,
                totalDownload = status.totalDownload,
                totalUpload = status.totalUpload,
            )
        }.collect { snapshot = it }
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("GHOST Assist · لایه پشتیبان", style = MaterialTheme.typography.titleMedium)
            Text(
                if (!enabled) "غیرفعال"
                else when (snapshot.state) {
                    ConnectionState.Connected -> "ناظر تونل فعال است؛ اتصال برقرار است"
                    ConnectionState.Connecting -> "در انتظار برقراری تونل اصلی"
                    ConnectionState.Reconnecting -> "تونل اصلی در حال اتصال مجدد است"
                    ConnectionState.Disconnecting -> "تونل اصلی در حال قطع اتصال است"
                    ConnectionState.Error -> "تونل اصلی خطا گزارش کرده است"
                    ConnectionState.Idle -> "در انتظار فعال‌شدن تونل اصلی"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("هسته/مسیر فعال: ${snapshot.kind.toDisplayName()}", style = MaterialTheme.typography.bodySmall)
            if (snapshot.state == ConnectionState.Connected) {
                Text(
                    "دریافت: ${snapshot.downloadBps.toRateLabel()}  ·  ارسال: ${snapshot.uploadBps.toRateLabel()}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "دریافت کل: ${snapshot.totalDownload.toByteLabel()}  ·  ارسال کل: ${snapshot.totalUpload.toByteLabel()}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                "حالت ایمن: فقط‌خواندنی؛ این لایه کانفیگ، مسیر ترافیک یا هسته را تغییر نمی‌دهد و فرمان قطع‌و‌وصل صادر نمی‌کند.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun String?.toDisplayName(): String = when (this) {
    VpnManager.KIND_XRAY -> "Xray"
    VpnManager.KIND_SINGBOX -> "Sing-box"
    VpnManager.KIND_PSIPHON -> "Psiphon"
    VpnManager.KIND_DNS_TUNNEL -> "DNS Tunnel"
    VpnManager.KIND_MASTERDNS -> "Master DNS"
    VpnManager.KIND_TOR -> "Tor"
    VpnManager.KIND_SSH -> "SSH"
    VpnManager.KIND_SNISPOOF -> "SNI Spoof"
    VpnManager.KIND_OPENCONNECT -> "OpenConnect"
    VpnManager.KIND_IKEV2 -> "IKEv2"
    VpnManager.KIND_CROSS_CHAIN -> "زنجیره ترکیبی"
    null -> "شناسایی نشده"
    else -> this
}

private fun Long.toRateLabel(): String {
    val value = coerceAtLeast(0L).toDouble()
    return when {
        value >= 1_000_000 -> "${"%.2f".format(value / 1_000_000)} MB/s"
        value >= 1_000 -> "${"%.1f".format(value / 1_000)} KB/s"
        else -> "${value.toLong()} B/s"
    }
}

private fun Long.toByteLabel(): String {
    val value = coerceAtLeast(0L).toDouble()
    return when {
        value >= 1_073_741_824 -> "${"%.2f".format(value / 1_073_741_824)} GB"
        value >= 1_048_576 -> "${"%.1f".format(value / 1_048_576)} MB"
        value >= 1_024 -> "${"%.1f".format(value / 1_024)} KB"
        else -> "${value.toLong()} B"
    }
}
