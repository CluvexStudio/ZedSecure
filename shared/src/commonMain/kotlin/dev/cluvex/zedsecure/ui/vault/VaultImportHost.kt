@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.cluvex.zedsecure.ui.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.*
import dev.cluvex.zedsecure.core.VaultImportBus
import dev.cluvex.zedsecure.crypto.ZsxExpiredException
import dev.cluvex.zedsecure.crypto.ZsxLegacyException
import dev.cluvex.zedsecure.ui.platform.LocalPlatform
import dev.cluvex.zedsecure.data.config.ConfigRepository
import dev.cluvex.zedsecure.ui.components.NoteText

@Composable
fun VaultImportHost(repository: ConfigRepository, onImported: () -> Unit) {
    val platform = LocalPlatform.current
    val importedMsg = stringResource(Res.string.zsx_imported)
    val wrongPwdMsg = stringResource(Res.string.zsx_wrong_password)
    val expiredMsg = stringResource(Res.string.zsx_expired)
    val legacyMsg = stringResource(Res.string.zsx_legacy)
    val pending by VaultImportBus.pending.collectAsStateWithLifecycle()

    pending?.let { p ->
        VaultUnlockDialog(
            name = p.metadata.nameEn.ifBlank { p.metadata.nameFa },
            note = p.metadata.note,
            passwordProtected = p.metadata.passwordProtected,
            onDismiss = { VaultImportBus.clear() },
            onUnlock = { password ->
                repository.importLocked(p.bytes, password)
                    .onSuccess {
                        platform.toast(importedMsg)
                        VaultImportBus.clear()
                        onImported()
                    }
                    .onFailure { e ->

                        platform.toast(
                            when (e) {
                                is ZsxLegacyException -> legacyMsg
                                is ZsxExpiredException -> expiredMsg
                                else -> wrongPwdMsg
                            },
                        )
                        if (e is ZsxLegacyException || e is ZsxExpiredException) {
                            VaultImportBus.clear()
                        }
                    }
            },
        )
    }
}

@Composable
internal fun VaultUnlockDialog(
    name: String,
    note: String,
    passwordProtected: Boolean,
    onDismiss: () -> Unit,
    onUnlock: (String?) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(Res.drawable.ic_encrypted), null) },
        title = { Text(name.ifBlank { stringResource(Res.string.vault_unlock_title) }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (note.isNotBlank()) {
                    NoteText(
                        text = note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (passwordProtected) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(Res.string.vault_password)) },
                        singleLine = true,
                        visualTransformation = if (visible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { visible = !visible }) {
                                Icon(
                                    painterResource(
                                        if (visible) Res.drawable.ic_visibility_off
                                        else Res.drawable.ic_visibility,
                                    ),
                                    contentDescription = stringResource(Res.string.vault_toggle_password),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        stringResource(Res.string.vault_no_password_needed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onUnlock(if (passwordProtected) password else null) },
                enabled = !passwordProtected || password.isNotBlank(),
            ) {
                Text(stringResource(if (passwordProtected) Res.string.vault_unlock else Res.string.zsx_import_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}
