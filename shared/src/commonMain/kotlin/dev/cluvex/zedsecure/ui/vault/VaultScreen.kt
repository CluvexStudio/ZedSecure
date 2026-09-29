@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package dev.cluvex.zedsecure.ui.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.*
import dev.cluvex.zedsecure.core.VaultImportBus
import dev.cluvex.zedsecure.ui.platform.LocalPlatform
import dev.cluvex.zedsecure.crypto.ZsxSealRequest
import dev.cluvex.zedsecure.data.config.ConfigRepository
import dev.cluvex.zedsecure.domain.config.VpnProfile
import dev.cluvex.zedsecure.ui.components.MorphingBlob
import dev.cluvex.zedsecure.ui.components.PageHeader
import dev.cluvex.zedsecure.ui.theme.ZedGradients

private const val NOTE_MAX_LEN = 500

@Composable
fun VaultScreen(
    repository: ConfigRepository,
    contentPadding: PaddingValues,
    onImportRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val platform = LocalPlatform.current
    val needConfigMsg = stringResource(Res.string.vault_need_config_first)
    val profiles by repository.profiles.collectAsStateWithLifecycle()
    val activeId by repository.activeId.collectAsStateWithLifecycle()
    val locked = profiles.filter { it.isLocked }

    val sources = profiles.filterNot { it.isLocked || it.isManagedTunnel || it.isProxyChain }
    var showCreate by remember { mutableStateOf(false) }
    var sealing by remember { mutableStateOf(false) }
    val sealScope = rememberCoroutineScope()

    Column(modifier.fillMaxSize().padding(contentPadding)) {
        Spacer(Modifier.height(8.dp))
        PageHeader(
            title = stringResource(Res.string.vault_title),
            subtitle = stringResource(Res.string.vault_intro_body),
        )

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(Res.drawable.ic_info),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        stringResource(Res.string.vault_experimental_badge),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Text(
                        stringResource(Res.string.vault_experimental_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                    )
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (locked.isEmpty()) {
                VaultEmptyState(Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(locked, key = { it.id }) { profile ->
                        val selectedMsg = stringResource(Res.string.vault_selected, profile.name)
                        LockedCard(
                            profile = profile,
                            active = profile.id == activeId,
                            onUse = {
                                repository.setActive(profile.id)
                                platform.toast(selectedMsg)
                            },
                            onDelete = { repository.remove(profile.id) },
                        )
                    }
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = {
                    if (sources.isEmpty()) {
                        platform.toast(needConfigMsg)
                    } else {
                        showCreate = true
                    }
                },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Icon(painterResource(Res.drawable.ic_lock), null, Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(Res.string.vault_create), fontWeight = FontWeight.SemiBold)
            }
            FilledTonalButton(
                onClick = onImportRequested,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Icon(painterResource(Res.drawable.ic_ios_share), null, Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(Res.string.vault_import), fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (showCreate) {
        CreateLockedSheet(
            sources = sources,
            sealing = sealing,
            onDismiss = { showCreate = false },
            onCreate = { requests ->

                sealing = true
                sealScope.launch {
                    val files = runCatching {
                        withContext(Dispatchers.Default) {
                            requests.map { (request, fileName) -> fileName to repository.createLockedZsx(request) }
                        }
                    }.getOrNull()
                    sealing = false
                    if (files != null) {
                        platform.shareFiles(files)
                        showCreate = false
                    }
                }
            },
        )
    }
}

@Composable
private fun VaultEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        MorphingBlob(progress = 0.5f, brush = ZedGradients.connected, modifier = Modifier.size(132.dp)) {
            Icon(
                painterResource(Res.drawable.ic_encrypted),
                null,
                tint = Color.White,
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(Modifier.height(22.dp))
        Text(
            stringResource(Res.string.vault_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(Res.string.vault_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LockedCard(
    profile: VpnProfile,
    active: Boolean,
    onUse: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        onClick = onUse,
        shape = RoundedCornerShape(22.dp),

        color = if (active) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(Res.drawable.ic_encrypted),
                null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(if (active) Res.string.config_active else Res.string.locked_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                profile.note?.takeIf { it.isNotBlank() }?.let { note ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (active) {
                Icon(
                    painterResource(Res.drawable.ic_check_circle),
                    contentDescription = stringResource(Res.string.config_active),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(4.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(
                    painterResource(Res.drawable.ic_delete),
                    contentDescription = stringResource(Res.string.action_delete),
                )
            }
        }
    }
}

@Composable
private fun CreateLockedSheet(
    sources: List<VpnProfile>,
    sealing: Boolean,
    onDismiss: () -> Unit,
    onCreate: (List<Pair<ZsxSealRequest, String>>) -> Unit,
) {
    val selected = remember { sources.take(1).map { it.id }.toMutableStateList() }
    var displayName by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var expiryDays by remember { mutableStateOf("0") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(Res.string.vault_create_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(Res.string.vault_pick_servers),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            sources.forEach { profile ->
                val checked = profile.id in selected
                Surface(
                    onClick = {
                        if (checked) selected.remove(profile.id) else selected.add(profile.id)
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = if (checked) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                profile.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                profile.transportLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text(stringResource(Res.string.vault_form_name)) },
                supportingText = {
                    if (selected.size > 1) Text(stringResource(Res.string.vault_name_numbered))
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = note,
                onValueChange = { if (it.length <= NOTE_MAX_LEN) note = it },
                label = { Text(stringResource(Res.string.vault_form_note)) },
                supportingText = { Text("${note.length} / $NOTE_MAX_LEN") },
                minLines = 2,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(Res.string.vault_form_password_optional)) },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            painterResource(
                                if (passwordVisible) Res.drawable.ic_visibility_off
                                else Res.drawable.ic_key,
                            ),
                            contentDescription = stringResource(Res.string.vault_toggle_password),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = expiryDays,
                onValueChange = { expiryDays = it.filter(Char::isDigit).take(4) },
                label = { Text(stringResource(Res.string.vault_form_expiry_days)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    val chosen = sources.filter { it.id in selected }
                    val days = expiryDays.toIntOrNull() ?: 0
                    val expiresAt = if (days > 0) {
                        System.currentTimeMillis() + days * 86_400_000L
                    } else {
                        null
                    }
                    val requests = chosen.mapIndexedNotNull { index, profile ->
                        val payload = profile.rawPayload() ?: return@mapIndexedNotNull null
                        val base = displayName.trim().ifBlank { profile.name }
                        val finalName = if (chosen.size > 1) "$base ${index + 1}" else base
                        ZsxSealRequest(
                            configPayload = payload,
                            nameEn = finalName,
                            nameFa = finalName,
                            note = note,
                            expiresAt = expiresAt,
                            password = password.ifBlank { null },
                        ) to finalName
                    }
                    onCreate(requests)
                },

                enabled = !sealing && selected.isNotEmpty(),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                if (sealing) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                } else {
                    Text(
                        stringResource(Res.string.vault_create_and_share),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
