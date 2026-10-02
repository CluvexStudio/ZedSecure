@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.cluvex.zedsecure.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cluvex.zedsecure.core.VpnManager
import dev.cluvex.zedsecure.data.update.Distribution
import dev.cluvex.zedsecure.data.update.NudgePolicy
import dev.cluvex.zedsecure.data.update.UpdateChecker
import dev.cluvex.zedsecure.data.update.UpdateInfo
import dev.cluvex.zedsecure.domain.model.AppSettings
import dev.cluvex.zedsecure.domain.model.ConnectionState
import dev.cluvex.zedsecure.platform.AppInfo
import dev.cluvex.zedsecure.platform.currentTimeMillis
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.*
import dev.cluvex.zedsecure.ui.platform.LocalPlatform
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun NudgeHost(
    settings: AppSettings,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
) {
    val platform = LocalPlatform.current
    val status by VpnManager.status.collectAsStateWithLifecycle()
    val busy = status.state == ConnectionState.Connecting ||
        status.state == ConnectionState.Reconnecting ||
        status.state == ConnectionState.Disconnecting

    var pendingUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    var showRate by remember { mutableStateOf(false) }

    LaunchedEffect(settings.lastUpdateCheckMs) {
        val now = currentTimeMillis()
        if (settings.lastUpdateCheckMs == 0L) {
            onUpdateSettings { it.copy(lastUpdateCheckMs = now) }
            return@LaunchedEffect
        }
        if (now - settings.lastUpdateCheckMs < NudgePolicy.UPDATE_CHECK_INTERVAL_MS) return@LaunchedEffect

        onUpdateSettings { it.copy(lastUpdateCheckMs = now) }
        val socks = if (VpnManager.status.value.state == ConnectionState.Connected) {
            VpnManager.activeSocksPort
        } else {
            null
        }
        val info = UpdateChecker.fetchLatest(
            platform.distribution,
            platform.deviceAbis,
            socks,
            settings.language.tag ?: "en",
        ) ?: return@LaunchedEffect
        if (UpdateChecker.compareVersions(info.versionName, AppInfo.versionName) <= 0) return@LaunchedEffect
        val snoozed = info.versionName == settings.dismissedUpdateVersion &&
            now - settings.dismissedUpdateAtMs < NudgePolicy.UPDATE_DISMISS_SNOOZE_MS
        if (!snoozed) pendingUpdate = info
    }

    LaunchedEffect(settings.successfulConnections, settings.ratePromptLastShownMs) {
        if (platform.distribution != Distribution.PlayStore) return@LaunchedEffect
        if (settings.rateNeverAsk) return@LaunchedEffect
        if (settings.successfulConnections < NudgePolicy.RATE_MIN_CONNECTIONS) return@LaunchedEffect
        val now = currentTimeMillis()
        if (now - settings.ratePromptLastShownMs < NudgePolicy.RATE_INTERVAL_MS) return@LaunchedEffect
        showRate = true
        onUpdateSettings { it.copy(ratePromptLastShownMs = now) }
    }

    val update = pendingUpdate
    if (update != null && !busy) {
        UpdateDialog(
            info = update,
            onDismiss = {
                pendingUpdate = null
                onUpdateSettings {
                    it.copy(
                        dismissedUpdateVersion = update.versionName,
                        dismissedUpdateAtMs = currentTimeMillis(),
                    )
                }
            },
        )
    }

    if (showRate && !busy && pendingUpdate == null) {
        RateDialog(
            onDismiss = { showRate = false },
            onNever = {
                showRate = false
                onUpdateSettings { it.copy(rateNeverAsk = true) }
            },
        )
    }
}

@Composable
fun UpdateDialog(info: UpdateInfo, onDismiss: () -> Unit) {
    val platform = LocalPlatform.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(Res.drawable.ic_download),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        },
        title = { Text(stringResource(Res.string.update_available_title)) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VersionPill(AppInfo.versionName, MaterialTheme.colorScheme.surfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text("→", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    VersionPill(info.versionName, MaterialTheme.colorScheme.primaryContainer)
                }
                if (info.releaseNotes.isNotBlank()) {
                    Spacer(Modifier.padding(top = 12.dp))
                    Text(
                        stringResource(Res.string.update_whats_new),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.padding(top = 4.dp))
                    Box(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                        Text(info.releaseNotes, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val url = info.downloadUrl
                if (url != null) platform.openUri(url) else platform.openStorePage()
                onDismiss()
            }) {
                Text(stringResource(Res.string.update_action_update))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.update_action_later)) }
        },
    )
}

@Composable
private fun VersionPill(version: String, container: androidx.compose.ui.graphics.Color) {
    Surface(shape = RoundedCornerShape(50), color = container) {
        Text(
            version,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun RateDialog(onDismiss: () -> Unit, onNever: () -> Unit) {
    val platform = LocalPlatform.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(5) {
                    Icon(
                        painterResource(Res.drawable.ic_star),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
        title = { Text(stringResource(Res.string.rate_prompt_title)) },
        text = { Text(stringResource(Res.string.rate_prompt_body)) },
        confirmButton = {
            Button(onClick = { platform.openStorePage(); onDismiss() }) {
                Text(stringResource(Res.string.rate_action_rate))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onNever) { Text(stringResource(Res.string.rate_action_never)) }
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.rate_action_later)) }
            }
        },
    )
}

@Composable
fun ManualUpdateCheckHost(
    settings: AppSettings,
    trigger: Int,
    onFinished: () -> Unit,
) {
    val platform = LocalPlatform.current
    var found by remember { mutableStateOf<UpdateInfo?>(null) }
    val upToDate = stringResource(Res.string.update_up_to_date)
    val failed = stringResource(Res.string.update_check_failed)

    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        val socks = if (VpnManager.status.value.state == ConnectionState.Connected) {
            VpnManager.activeSocksPort
        } else {
            null
        }
        val info = UpdateChecker.fetchLatest(
            platform.distribution,
            platform.deviceAbis,
            socks,
            settings.language.tag ?: "en",
        )
        when {
            info == null -> platform.toast(failed)
            UpdateChecker.compareVersions(info.versionName, AppInfo.versionName) > 0 -> found = info
            else -> platform.toast(upToDate)
        }
        onFinished()
    }

    found?.let { UpdateDialog(info = it, onDismiss = { found = null }) }
}
