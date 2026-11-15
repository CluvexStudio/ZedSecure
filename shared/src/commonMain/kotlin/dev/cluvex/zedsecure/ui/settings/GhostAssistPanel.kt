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
import dev.cluvex.zedsecure.data.net.PingService
import dev.cluvex.zedsecure.domain.model.ConnectionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

private enum class AssistHealth {
    Unknown,
    Waiting,
    Checking,
    Healthy,
    Degraded,
    Unavailable,
}

private data class GhostAssistSnapshot(
    val state: ConnectionState = ConnectionState.Idle,
    val kind: String? = null,
    val sessionId: Int = 0,
    val serverName: String? = null,
    val durationSeconds: Int = 0,
    val hasTunnelError: Boolean = false,
    val downloadBps: Long = 0L,
    val uploadBps: Long = 0L,
    val totalDownload: Long = 0L,
    val totalUpload: Long = 0L,
    val health: AssistHealth = AssistHealth.Unknown,
    val latencyMs: Long? = null,
)

@Composable
internal fun GhostAssistPanel(
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var snapshot by remember { mutableStateOf(GhostAssistSnapshot()) }

    // Live, read-only telemetry. This collector never sends commands to a VPN core.
    LaunchedEffect(enabled) {
        if (!enabled) {
            snapshot = GhostAssistSnapshot()
            return@LaunchedEffect
        }
        combine(VpnManager.status, VpnManager.activeKind) { status, kind ->
            snapshot = snapshot.copy(
                state = status.state,
                kind = kind,
                sessionId = status.sessionId,
                serverName = status.serverName,
                durationSeconds = status.durationSeconds,
                hasTunnelError = status.error != null,
                downloadBps = status.downloadBps,
                uploadBps = status.uploadBps,
                totalDownload = status.totalDownload,
                totalUpload = status.totalUpload,
            )
        }.collect { }
    }

    // State-aware health checks: pause during transitions and only probe an established tunnel.
    // A failed probe is advisory; it never restarts, reroutes, or stops the user's VPN.
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        combine(
            VpnManager.status.map { it.state to it.sessionId }.distinctUntilChanged(),
            VpnManager.activeKind,
        ) { stateAndSession, kind -> Triple(stateAndSession.first, stateAndSession.second, kind) }
            .collectLatest { (state, sessionId, _) ->
                if (state != ConnectionState.Connected) {
                    snapshot = snapshot.copy(
                        health = if (state.isTransitioning) AssistHealth.Waiting else AssistHealth.Unknown,
                        latencyMs = null,
                    )
                    return@collectLatest
                }

                while (true) {
                    snapshot = snapshot.copy(health = AssistHealth.Checking, latencyMs = null)
                    val result = withTimeoutOrNull(18_000L) {
                        runCatching { PingService.activeConnectionHealth() }.getOrNull()
                    }
                    val current = VpnManager.status.value
                    if (current.sessionId != sessionId || current.state != ConnectionState.Connected) {
                        return@collectLatest
                    }
                    val latency = result?.takeIf { it.ok }?.ms
                    snapshot = snapshot.copy(
                        health = when {
                            latency == null -> AssistHealth.Unavailable
                            latency <= 180L -> AssistHealth.Healthy
                            latency <= 500L -> AssistHealth.Degraded
                            else -> AssistHealth.Degraded
                        },
                        latencyMs = latency,
                    )
                    delay(30_000L)
                }
            }
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text("GHOST Assist · لایه پشتیبان", style = MaterialTheme.typography.titleMedium)
            Text(
                if (!enabled) "غیرفعال"
                else when (snapshot.state) {
                    ConnectionState.Connected -> "تونل اصلی متصل است"
                    ConnectionState.Connecting -> "در انتظار برقراری تونل اصلی"
                    ConnectionState.Reconnecting -> "تونل اصلی در حال اتصال مجدد است"
                    ConnectionState.Disconnecting -> "تونل اصلی در حال قطع اتصال است"
                    ConnectionState.Error -> "تونل اصلی خطا گزارش کرده است"
                    ConnectionState.Idle -> "در انتظار فعال‌شدن تونل اصلی"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "هسته فعال: ${snapshot.kind.toDisplayName()}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "شناسه نشست: ${snapshot.sessionId}",
                style = MaterialTheme.typography.bodySmall,
            )
            snapshot.serverName?.takeIf { it.isNotBlank() }?.let {
                Text("پروفایل: $it", style = MaterialTheme.typography.bodySmall)
            }
            if (snapshot.state == ConnectionState.Connected) {
                Text("مدت اتصال: ${snapshot.durationSeconds.toDurationLabel()}", style = MaterialTheme.typography.bodySmall)
            }
            if (snapshot.hasTunnelError || snapshot.state == ConnectionState.Error) {
                Text(
                    "تونل اصلی خطا گزارش کرده است؛ GHOST فقط وضعیت را نشان می‌دهد و اتصال را تغییر نمی‌دهد.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                "سلامت مسیر: ${snapshot.health.toPersianLabel()}",
                style = MaterialTheme.typography.bodyMedium,
                color = when (snapshot.health) {
                    AssistHealth.Healthy -> MaterialTheme.colorScheme.primary
                    AssistHealth.Degraded, AssistHealth.Unavailable -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            snapshot.latencyMs?.let {
                Text("تأخیر اندازه‌گیری‌شده: ${it} ms", style = MaterialTheme.typography.bodySmall)
            }
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
                "هماهنگی هوشمند: پایش فقط هنگام اتصال برقرار است؛ هنگام تغییر وضعیت، بررسی‌ها متوقف یا از نو آغاز می‌شوند.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                snapshot.health.toGuidance(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "حالت ایمن: فقط‌خواندنی. این بخش کانفیگ، مسیر ترافیک یا هسته را تغییر نمی‌دهد و فرمان قطع، وصل یا تعویض هسته صادر نمی‌کند. خطای GHOST نباید اتصال اصلی را متوقف کند.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun AssistHealth.toGuidance(): String = when (this) {
    AssistHealth.Unknown -> "پایش تا زمان برقراری تونل اصلی غیرفعال می‌ماند."
    AssistHealth.Waiting -> "هماهنگی موقتاً متوقف است تا وضعیت تونل اصلی پایدار شود."
    AssistHealth.Checking -> "در حال سنجش دسترسی از مسیر اتصال فعلی؛ این بررسی ترافیک عادی را تغییر نمی‌دهد."
    AssistHealth.Healthy -> "پیشنهاد GHOST: مسیر پاسخ‌گو است؛ فقط پایش ادامه می‌یابد."
    AssistHealth.Degraded -> "پیشنهاد GHOST: تأخیر بالاست؛ کیفیت شبکه را بررسی کن. هیچ تغییری خودکار اعمال نمی‌شود."
    AssistHealth.Unavailable -> "پاسخ آزمایش دریافت نشد؛ این نتیجه به‌تنهایی به معنی قطع تونل نیست."
}

private fun AssistHealth.toPersianLabel(): String = when (this) {
    AssistHealth.Unknown -> "هنوز بررسی نشده"
    AssistHealth.Waiting -> "در انتظار وضعیت پایدار تونل"
    AssistHealth.Checking -> "در حال بررسی اتصال"
    AssistHealth.Healthy -> "پایدار"
    AssistHealth.Degraded -> "کند یا ناپایدار"
    AssistHealth.Unavailable -> "قابل تأیید نیست"
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

private fun Int.toDurationLabel(): String {
    val safe = coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val seconds = safe % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m ${seconds}s"
}

private fun Long.toRateLabel(): String {
    val value = coerceAtLeast(0L).toDouble()
    return when {
        value >= 1_000_000 -> "${(value / 1_000_000).toLong()} MB/s"
        value >= 1_000 -> "${(value / 1_000).toLong()} KB/s"
        else -> "${value.toLong()} B/s"
    }
}

private fun Long.toByteLabel(): String {
    val value = coerceAtLeast(0L).toDouble()
    return when {
        value >= 1_073_741_824 -> "${(value / 1_073_741_824).toLong()} GB"
        value >= 1_048_576 -> "${(value / 1_048_576).toLong()} MB"
        value >= 1_024 -> "${(value / 1_024).toLong()} KB"
        else -> "${value.toLong()} B"
    }
}
