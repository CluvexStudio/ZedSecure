@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.cluvex.zedsecure.ui.servers

import org.jetbrains.compose.resources.StringResource

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.*
import dev.cluvex.zedsecure.domain.config.SshProfile
import dev.cluvex.zedsecure.ui.components.PickerField

@Composable
fun SshSheet(
    onDismiss: () -> Unit,
    onSave: (name: String, SshProfile) -> Unit,
    initial: SshProfile? = null,
    initialName: String = "",
) {
    var name by remember { mutableStateOf(initialName) }
    var host by remember { mutableStateOf(initial?.host ?: "") }
    var port by remember { mutableStateOf((initial?.port ?: 22).toString()) }
    var username by remember { mutableStateOf(initial?.username ?: "") }
    var authType by remember { mutableStateOf(initial?.authType ?: SshProfile.AUTH_PASSWORD) }
    var password by remember { mutableStateOf(initial?.password ?: "") }
    var privateKey by remember { mutableStateOf(initial?.privateKey ?: "") }
    var passphrase by remember { mutableStateOf(initial?.keyPassphrase ?: "") }

    val isKey = authType == SshProfile.AUTH_KEY

    ModalBottomSheet(
        onDismissRequest = onDismiss,

        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.ssh_add_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            F(name, Res.string.manual_remark) { name = it }
            F(host, Res.string.ssh_host, "example.com") { host = it }
            F(port, Res.string.ssh_port, numeric = true) { port = it }
            F(username, Res.string.ssh_username) { username = it }
            PickerField(
                label = stringResource(Res.string.ssh_auth),
                options = listOf(
                    SshProfile.AUTH_PASSWORD to stringResource(Res.string.ssh_auth_password),
                    SshProfile.AUTH_KEY to stringResource(Res.string.ssh_auth_key),
                ),
                selected = authType,
                onSelect = { authType = it },
            )
            if (isKey) {
                F(privateKey, Res.string.ssh_private_key, minLines = 4) { privateKey = it }
                F(passphrase, Res.string.ssh_passphrase) { passphrase = it }
            } else {
                F(password, Res.string.manual_password) { password = it }
            }

            Button(
                onClick = {
                    onSave(
                        name.trim(),
                        SshProfile(
                            host = host.trim(),
                            port = port.toIntOrNull() ?: 22,
                            username = username.trim(),
                            authType = authType,
                            password = password,
                            privateKey = privateKey.trim(),
                            keyPassphrase = passphrase,
                        ),
                    )
                },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) { Text(stringResource(Res.string.action_save), fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun F(value: String, labelRes: StringResource, placeholder: String? = null, minLines: Int = 1, numeric: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(if (numeric) it.filter { c -> c.isDigit() } else it) },
        label = { Text(stringResource(labelRes)) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = minLines == 1,
        minLines = minLines,
        modifier = Modifier.fillMaxWidth(),
    )
}
