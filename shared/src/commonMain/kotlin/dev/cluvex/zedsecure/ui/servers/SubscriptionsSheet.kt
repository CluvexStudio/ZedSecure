@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.cluvex.zedsecure.ui.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.*
import dev.cluvex.zedsecure.data.config.ConfigRepository
import dev.cluvex.zedsecure.ui.platform.LocalPlatform
import org.jetbrains.compose.resources.getString
import dev.cluvex.zedsecure.ui.components.SectionTitle
import dev.cluvex.zedsecure.ui.format.formatBytes
import androidx.compose.ui.graphics.luminance
import kotlinx.coroutines.launch

@Composable
fun SubscriptionsSheet(
    repository: ConfigRepository,
    onDismiss: () -> Unit,
) {
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()
    val subs by repository.subscriptions.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var userAgent by remember { mutableStateOf("") }
    var groupName by remember { mutableStateOf("") }
    var busyId by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<dev.cluvex.zedsecure.domain.config.Subscription?>(null) }
    var deleteTarget by remember { mutableStateOf<dev.cluvex.zedsecure.domain.config.Subscription?>(null) }
    var qrTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,

        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(Res.string.subs_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            SectionTitle(stringResource(Res.string.subs_add))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(Res.string.subs_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(Res.string.subs_url)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            UserAgentPicker(userAgent) { userAgent = it }
            Button(
                onClick = {
                    val sub = repository.addSubscription(name, url, userAgent)
                    name = ""; url = ""; userAgent = ""
                    busyId = sub.id
                    scope.launch {
                        repository.updateSubscription(sub.id)
                            .onSuccess {
                                platform.toast(getString(Res.string.servers_imported, it))

                                onDismiss()
                            }
                            .onFailure {
                                platform.toast(getString(Res.string.subs_failed))
                            }
                        busyId = null
                    }
                },
                enabled = url.isNotBlank(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text(stringResource(Res.string.subs_add_and_fetch), fontWeight = FontWeight.SemiBold)
            }

            SectionTitle(stringResource(Res.string.groups_add))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text(stringResource(Res.string.groups_name)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        repository.addGroup(groupName)
                        groupName = ""
                    },
                    enabled = groupName.isNotBlank(),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(50.dp),
                ) { Text(stringResource(Res.string.action_add)) }
            }

            if (subs.isNotEmpty()) {
                SectionTitle(stringResource(Res.string.subs_existing))
                subs.forEach { sub ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                      Column {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    sub.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    when {
                                        sub.serverCount > 0 -> stringResource(Res.string.servers_count, sub.serverCount)

                                        sub.url.isBlank() -> stringResource(Res.string.groups_add)
                                        else -> sub.url
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Switch(
                                checked = sub.enabled,
                                onCheckedChange = { repository.setSubscriptionEnabled(sub.id, it) },
                            )
                            if (busyId == sub.id) {
                                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(20.dp))
                                }
                            } else {
                                IconButton(onClick = {
                                    busyId = sub.id
                                    scope.launch {
                                        repository.updateSubscription(sub.id)
                                        busyId = null
                                    }
                                }) {
                                    Icon(
                                        painterResource(Res.drawable.ic_schedule),
                                        contentDescription = stringResource(Res.string.subs_update),
                                    )
                                }
                            }

                            IconButton(onClick = { qrTarget = sub.name to sub.url }) {
                                Icon(
                                    painterResource(Res.drawable.ic_qr_code_2),
                                    contentDescription = stringResource(Res.string.action_share_qr),
                                )
                            }
                            IconButton(onClick = { renameTarget = sub }) {
                                Icon(
                                    painterResource(Res.drawable.ic_edit),
                                    contentDescription = stringResource(Res.string.subs_rename),
                                )
                            }
                            IconButton(onClick = { deleteTarget = sub }) {
                                Icon(
                                    painterResource(Res.drawable.ic_delete),
                                    contentDescription = stringResource(Res.string.action_delete),
                                )
                            }
                        }
                        SubscriptionUsage(sub, onOpen = { platform.openUri(it) })
                      }
                    }
                }
            }
        }
    }

    qrTarget?.let { (qrTitle, qrText) ->
        dev.cluvex.zedsecure.ui.components.QrDialog(
            title = qrTitle,
            text = qrText,
            copyLabel = stringResource(Res.string.action_copy),
            shareLabel = stringResource(Res.string.action_share),
            closeLabel = stringResource(Res.string.action_close),
            unsupportedLabel = stringResource(Res.string.qr_too_large),
            onCopy = { platform.copyToClipboard(qrText) },
            onShare = { platform.shareText(qrText) },
            onDismiss = { qrTarget = null },
        )
    }

    renameTarget?.let { target ->
        var newName by remember(target.id) { mutableStateOf(target.name) }
        var newUrl by remember(target.id) { mutableStateOf(target.url) }
        var newUa by remember(target.id) { mutableStateOf(target.userAgent.orEmpty()) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(Res.string.subs_edit)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(stringResource(Res.string.subs_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = newUrl,
                        onValueChange = { newUrl = it },
                        label = { Text(stringResource(Res.string.subs_url)) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    UserAgentPicker(newUa) { newUa = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    repository.editSubscription(target.id, newName, newUa, newUrl)
                    renameTarget = null
                }) { Text(stringResource(Res.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon = { Icon(painterResource(Res.drawable.ic_delete), null) },
            title = { Text(stringResource(Res.string.subs_delete_confirm_title)) },
            text = { Text(stringResource(Res.string.subs_delete_confirm_body, target.name)) },
            confirmButton = {
                TextButton(onClick = {
                    repository.removeSubscription(target.id, alsoRemoveServers = true)
                    deleteTarget = null
                }) { Text(stringResource(Res.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun UserAgentPicker(value: String, onChange: (String) -> Unit) {
    val default = ConfigRepository.DEFAULT_UA
    val alt = ConfigRepository.ALT_UA
    val isDefault = value.isBlank() || value == default
    val isAlt = value == alt
    var custom by remember(value) { mutableStateOf(!isDefault && !isAlt) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(Res.string.subs_ua_preset),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = isDefault && !custom,
                onClick = { custom = false; onChange("") },
                label = { Text(stringResource(Res.string.subs_ua_v2rayng), maxLines = 1) },
            )
            FilterChip(
                selected = isAlt && !custom,
                onClick = { custom = false; onChange(alt) },
                label = { Text(stringResource(Res.string.subs_ua_zedsecure), maxLines = 1) },
            )
            FilterChip(
                selected = custom,
                onClick = { custom = true },
                label = { Text(stringResource(Res.string.subs_ua_custom), maxLines = 1) },
            )
        }
        if (custom) {
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                label = { Text(stringResource(Res.string.subs_user_agent)) },
                placeholder = { Text(default) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            stringResource(Res.string.subs_ua_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SubscriptionUsage(
    sub: dev.cluvex.zedsecure.domain.config.Subscription,
    onOpen: (String) -> Unit,
) {
    val used = sub.used
    val total = sub.total
    val expireAt = sub.expireAt
    if (used == null && total == null && expireAt == null && sub.supportUrl == null && sub.webPageUrl == null) return

    val now = dev.cluvex.zedsecure.platform.currentTimeMillis()
    val fraction = if (used != null && total != null && total > 0) (used.toDouble() / total).toFloat() else null
    val expired = expireAt != null && expireAt <= now
    val daysLeft = expireAt?.let { ((it - now) / 86_400_000L).toInt() }
    val exhausted = fraction != null && fraction >= 1f
    val warning = !expired && !exhausted && ((fraction != null && fraction >= 0.8f) || (daysLeft != null && daysLeft <= 3))
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val stateColor = when {
        expired || exhausted -> MaterialTheme.colorScheme.error
        warning -> if (dark) androidx.compose.ui.graphics.Color(0xFFFFB74D) else androidx.compose.ui.graphics.Color(0xFF9A5B00)
        else -> MaterialTheme.colorScheme.primary
    }

    Column(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val usageText = when {
                used != null && total != null ->
                    stringResource(Res.string.subs_usage, formatBytes(used), formatBytes(total))
                used != null -> stringResource(Res.string.subs_usage_unlimited, formatBytes(used))
                else -> null
            }
            Text(
                usageText.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val expiryText = when {
                expired -> stringResource(Res.string.subs_expired)
                exhausted -> stringResource(Res.string.subs_quota_exhausted)
                daysLeft == null -> null
                daysLeft <= 0 -> stringResource(Res.string.subs_expires_today)
                daysLeft == 1 -> stringResource(Res.string.subs_one_day_left)
                else -> stringResource(Res.string.subs_days_left, daysLeft)
            }
            if (expiryText != null) {
                Text(
                    expiryText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (expired || exhausted || warning) stateColor else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (fraction != null) {
            androidx.compose.material3.LinearProgressIndicator(
                progress = { fraction.coerceIn(0f, 1f) },
                color = stateColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth().height(4.dp),
            )
        }
        if (sub.supportUrl != null || sub.webPageUrl != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sub.supportUrl?.let { url ->
                    androidx.compose.material3.AssistChip(
                        onClick = { onOpen(url) },
                        label = { Text(stringResource(Res.string.subs_support)) },
                        leadingIcon = { Icon(painterResource(Res.drawable.ic_info), null, Modifier.size(18.dp)) },
                    )
                }
                sub.webPageUrl?.let { url ->
                    androidx.compose.material3.AssistChip(
                        onClick = { onOpen(url) },
                        label = { Text(stringResource(Res.string.subs_website)) },
                        leadingIcon = { Icon(painterResource(Res.drawable.ic_public), null, Modifier.size(18.dp)) },
                    )
                }
            }
        }
    }
}
