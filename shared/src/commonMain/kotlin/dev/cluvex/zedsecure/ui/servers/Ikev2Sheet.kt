@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.cluvex.zedsecure.ui.servers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.*
import dev.cluvex.zedsecure.domain.config.Ikev2Auth
import dev.cluvex.zedsecure.domain.config.Ikev2Profile
import dev.cluvex.zedsecure.ui.components.PickerField
import dev.cluvex.zedsecure.ui.platform.LocalPlatform

@Composable
fun Ikev2Sheet(
    initial: Ikev2Profile? = null,
    initialName: String = "",
    onDismiss: () -> Unit,
    onSave: (name: String, Ikev2Profile) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val platform = LocalPlatform.current

    var name by remember { mutableStateOf(initialName) }
    var server by remember { mutableStateOf(initial?.server ?: "") }
    var remoteId by remember { mutableStateOf(initial?.remoteId ?: "") }
    var localId by remember { mutableStateOf(initial?.localId ?: "") }

    var authType by remember { mutableStateOf(initial?.effectiveAuth ?: Ikev2Auth.EAP_MSCHAPV2) }
    var username by remember { mutableStateOf(initial?.username ?: "") }

    var password by remember { mutableStateOf("") }
    var psk by remember { mutableStateOf("") }
    var caCertPem by remember { mutableStateOf(initial?.caCertPem ?: "") }
    var userCertAlias by remember { mutableStateOf(initial?.userCertAlias ?: "") }

    var caCertLabel by remember { mutableStateOf("") }
    var certError by remember { mutableStateOf<String?>(null) }
    var caError by remember { mutableStateOf<String?>(null) }
    var ikeProposal by remember { mutableStateOf(initial?.ikeProposal ?: "") }
    var espProposal by remember { mutableStateOf(initial?.espProposal ?: "") }
    var mtu by remember { mutableStateOf((initial?.mtu ?: 1400).toString()) }
    var advancedOpen by remember { mutableStateOf(false) }

    val needsUser = authType == Ikev2Auth.EAP_MSCHAPV2
    val needsCert = authType == Ikev2Auth.CERTIFICATE

    val advancedIkeSupported = (Ikev2CertBridge.supportsAdvancedIke?.invoke() ?: true) &&
        authType != Ikev2Auth.EAP_MSCHAPV2
    val validateProposal = Ikev2CertBridge.validateProposal
    val ikeProposalBad = ikeProposal.isNotBlank() && validateProposal?.invoke(ikeProposal, false) == false
    val espProposalBad = espProposal.isNotBlank() && validateProposal?.invoke(espProposal, true) == false
    val valid = server.isNotBlank() && !ikeProposalBad && !espProposalBad && when (authType) {
        Ikev2Auth.EAP_MSCHAPV2 ->
            username.isNotBlank() && (password.isNotBlank() || initial?.password?.isNotBlank() == true)
        Ikev2Auth.CERTIFICATE -> userCertAlias.isNotBlank()
        Ikev2Auth.EAP_TLS -> false
        Ikev2Auth.PSK -> psk.isNotBlank() || (initial?.psk?.isNotBlank() == true)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,

        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(Res.string.ikev2_add_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Field(server, Res.string.ikev2_server, placeholder = "vpn.example.com") { server = it }

            PickerField(
                label = stringResource(Res.string.ikev2_vpn_type),
                options = listOf(
                    Ikev2Auth.EAP_MSCHAPV2 to stringResource(Res.string.ikev2_type_eap),
                    Ikev2Auth.CERTIFICATE to stringResource(Res.string.ikev2_type_cert),
                    Ikev2Auth.PSK to stringResource(Res.string.ikev2_type_psk),
                ),
                selected = authType,
                onSelect = { authType = it },
            )

            if (needsUser) {
                Field(username, Res.string.ikev2_username) { username = it }
            }
            if (authType == Ikev2Auth.EAP_MSCHAPV2) {
                Field(
                    password, Res.string.ikev2_password, isPassword = true,

                    supporting = if (initial?.password?.isNotBlank() == true) {
                        stringResource(Res.string.ikev2_secret_stored)
                    } else null,
                ) { password = it }
            }
            if (authType == Ikev2Auth.PSK) {
                Field(
                    psk, Res.string.ikev2_psk, isPassword = true,
                    supporting = if (initial?.psk?.isNotBlank() == true) {
                        stringResource(Res.string.ikev2_secret_stored)
                    } else null,
                ) { psk = it }
            }

            if (needsCert) {
                val notCert = stringResource(Res.string.cert_error_not_credential)
                val installFailed = stringResource(Res.string.cert_error_install_failed)
                CertSourceRow(
                    label = stringResource(Res.string.ikev2_user_cert),
                    value = userCertAlias,
                    emptyLabel = stringResource(Res.string.ikev2_client_cert_pick),
                    hint = stringResource(Res.string.cert_keychain_hint),
                    error = certError,
                    onChoose = Ikev2CertBridge.pickClientCertAlias?.let { pick ->
                        { scope.launch { certError = null; pick()?.let { userCertAlias = it } } }
                    },
                    onImport = CertImport.installToKeyChain?.let { installer ->
                        {
                            scope.launch {
                                certError = null
                                val file = platform.pickFileBytes()
                                if (file != null) {
                                    val info = CertImport.inspect?.invoke(file.bytes, file.name)
                                    if (info != null && info.kind != CertKind.Pkcs12) {
                                        certError = notCert
                                    } else {
                                        val suggested = file.name.substringBeforeLast('.')
                                        when (installer(file.bytes, suggested)) {
                                            CertInstallResult.Installed ->
                                                Ikev2CertBridge.pickClientCertAlias?.invoke()
                                                    ?.let { userCertAlias = it }
                                            CertInstallResult.Cancelled -> Unit
                                            CertInstallResult.Unsupported -> certError = installFailed
                                        }
                                    }
                                }
                            }
                        }
                    },
                    onClear = { userCertAlias = "" },
                )
            }

            val caNotCert = stringResource(Res.string.cert_error_not_certificate)
            CertSourceRow(
                label = stringResource(Res.string.ikev2_ca),
                value = when {
                    caCertPem.isBlank() -> null
                    caCertLabel.isNotBlank() -> caCertLabel
                    else -> stringResource(Res.string.ikev2_ca_imported)
                },
                emptyLabel = stringResource(Res.string.ikev2_ca_auto),
                hint = if (CertImport.canInstallCaCert?.invoke() == false) {
                    stringResource(Res.string.cert_ca_file_only)
                } else null,
                error = caError,
                onImport = {
                    scope.launch {
                        caError = null
                        val file = platform.pickFileBytes() ?: return@launch
                        val info = CertImport.inspect?.invoke(file.bytes, file.name)
                        val pem = info?.pem
                            ?: Ikev2CertBridge.readCaCertPem?.invoke(file.bytes)
                        if (pem == null) {
                            caError = caNotCert
                        } else {
                            caCertPem = pem
                            caCertLabel = info?.description.orEmpty()
                        }
                    }
                },
                onClear = { caCertPem = ""; caCertLabel = ""; caError = null },
            )

            Field(name, Res.string.ikev2_profile_name) { name = it }

            TextButton(onClick = { advancedOpen = !advancedOpen }) {
                Text(
                    stringResource(
                        if (advancedOpen) Res.string.ikev2_advanced_title else Res.string.ikev2_show_advanced,
                    ),
                )
            }
            AnimatedVisibility(advancedOpen) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Field(
                        remoteId,
                        Res.string.ikev2_server_identity,
                        placeholder = "vpn.example.com",
                        supporting = stringResource(Res.string.ikev2_server_identity_hint),
                    ) { remoteId = it }
                    Field(
                        localId,
                        Res.string.ikev2_client_identity,
                        supporting = stringResource(Res.string.ikev2_client_identity_hint),
                    ) { localId = it }
                    if (!advancedIkeSupported) Hint(stringResource(Res.string.ikev2_advanced_scope))

                    Field(mtu, Res.string.ikev2_mtu_label, numeric = true) { mtu = it }

                    SectionLabel(stringResource(Res.string.ikev2_algorithms))
                    Hint(stringResource(Res.string.ikev2_algorithms_intro))
                    Field(ikeProposal, Res.string.ikev2_ike_algorithms, placeholder = "aes256-sha256-modp2048") { ikeProposal = it }
                    if (ikeProposalBad) Hint(stringResource(Res.string.ikev2_proposal_invalid), error = true)
                    Field(espProposal, Res.string.ikev2_esp_algorithms, placeholder = "aes256-sha256") { espProposal = it }
                    if (espProposalBad) Hint(stringResource(Res.string.ikev2_proposal_invalid), error = true)

                    Hint(stringResource(Res.string.ikev2_platform_note))
                }
            }

            Button(
                onClick = {
                    val sealedPassword = if (password.isNotEmpty()) Ikev2Profile.sealPassword(password) else initial?.password ?: ""
                    val sealedPsk = if (psk.isNotEmpty()) Ikev2Profile.sealPsk(psk) else initial?.psk ?: ""
                    onSave(
                        name,
                        Ikev2Profile(
                            server = server.trim(),
                            remoteId = remoteId.trim(),
                            localId = localId.trim(),
                            authType = authType,
                            username = username.trim(),
                            password = sealedPassword,
                            psk = sealedPsk,
                            caCertPem = caCertPem,
                            userCertAlias = userCertAlias,
                            ikeProposal = ikeProposal.trim(),
                            espProposal = espProposal.trim(),

                            mtu = mtu.toIntOrNull()?.coerceIn(1280, 1500) ?: 1400,
                        ),
                    )
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.action_save), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun Hint(text: String, error: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun SwitchRow(labelRes: StringResource, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(labelRes), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Field(
    value: String,
    labelRes: StringResource,
    placeholder: String? = null,
    numeric: Boolean = false,
    isPassword: Boolean = false,
    supporting: String? = null,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(if (numeric) it.filter { c -> c.isDigit() } else it) },
        label = { Text(stringResource(labelRes)) },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = supporting?.let { { Text(it) } },
        singleLine = true,
        visualTransformation = if (isPassword) {
            androidx.compose.ui.text.input.PasswordVisualTransformation()
        } else {
            androidx.compose.ui.text.input.VisualTransformation.None
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
