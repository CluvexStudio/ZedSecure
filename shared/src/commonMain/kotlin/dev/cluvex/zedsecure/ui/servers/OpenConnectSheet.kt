@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.cluvex.zedsecure.ui.servers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import org.jetbrains.compose.resources.painterResource
import dev.cluvex.zedsecure.shared.resources.ic_keyboard_arrow_down
import dev.cluvex.zedsecure.shared.resources.ic_chevron_right
import dev.cluvex.zedsecure.shared.resources.openconnect_port
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.*
import dev.cluvex.zedsecure.domain.config.OpenConnectProfile
import dev.cluvex.zedsecure.ui.components.PickerField
import kotlinx.coroutines.launch
import dev.cluvex.zedsecure.ui.platform.LocalPlatform

@Composable
fun OpenConnectSheet(
    initial: OpenConnectProfile? = null,
    initialName: String = "",
    onDismiss: () -> Unit,
    onSave: (name: String, OpenConnectProfile) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }

    var server by remember {
        mutableStateOf(OpenConnectProfile.splitPort(initial?.server ?: "").first)
    }
    var port by remember {
        mutableStateOf(OpenConnectProfile.splitPort(initial?.server ?: "").second?.toString() ?: "")
    }
    var protocol by remember { mutableStateOf(initial?.protocol ?: OpenConnectProfile.PROTO_ANYCONNECT) }
    var username by remember { mutableStateOf(initial?.username ?: "") }
    var password by remember { mutableStateOf(initial?.password ?: "") }
    var authgroup by remember { mutableStateOf(initial?.authgroup ?: "") }
    var userAgent by remember { mutableStateOf(initial?.userAgent ?: "") }
    var clientCert by remember { mutableStateOf(initial?.clientCertPem ?: "") }
    var clientKey by remember { mutableStateOf(initial?.clientKeyPem ?: "") }
    var keyPassword by remember { mutableStateOf(initial?.clientKeyPassword ?: "") }
    var caCert by remember { mutableStateOf(initial?.caCertPem ?: "") }
    var certPin by remember { mutableStateOf(initial?.serverCertSha256 ?: "") }
    var tokenMode by remember { mutableStateOf(initial?.tokenMode ?: OpenConnectProfile.TOKEN_NONE) }
    var tokenSecret by remember { mutableStateOf(initial?.tokenSecret ?: "") }
    var disableDtls by remember { mutableStateOf(initial?.disableDtls ?: false) }
    var reportedOs by remember { mutableStateOf(initial?.reportedOs ?: "android") }
    var certP12 by remember { mutableStateOf(initial?.clientCertP12Base64 ?: "") }
    var certP12Name by remember { mutableStateOf("") }
    var p12Error by remember { mutableStateOf<String?>(null) }
    var sni by remember { mutableStateOf(initial?.sni ?: "") }
    var mtu by remember { mutableStateOf(initial?.mtu?.takeIf { it > 0 }?.toString() ?: "") }
    var reconnect by remember {
        mutableStateOf(
            (initial?.reconnectTimeoutSec ?: OpenConnectProfile.DEFAULT_RECONNECT_TIMEOUT).toString(),
        )
    }
    var proxy by remember { mutableStateOf(initial?.proxy ?: "") }
    var disableIpv6 by remember { mutableStateOf(initial?.disableIpv6 ?: false) }
    var advancedOpen by remember { mutableStateOf(false) }

    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()

    val valid = server.isNotBlank()

    val scrollState = rememberScrollState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(if (initial == null) Res.string.openconnect_add_title else Res.string.openconnect_edit_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Field(name, Res.string.manual_remark) { name = it }
            Field(server, Res.string.openconnect_server, placeholder = "vpn.example.com") { server = it }

            Field(port, Res.string.openconnect_port, placeholder = "443", numeric = true) {
                port = it.filter(Char::isDigit).take(5)
            }
            PickerField(
                label = stringResource(Res.string.openconnect_protocol),
                options = OpenConnectProfile.PROTOCOLS,
                selected = protocol,
                onSelect = { protocol = it },
            )
            Field(username, Res.string.openconnect_username) { username = it }
            Field(password, Res.string.openconnect_password, secret = true) { password = it }
            Field(authgroup, Res.string.openconnect_authgroup) { authgroup = it }
            Field(certPin, Res.string.openconnect_cert_pin, placeholder = "sha256:…") { certPin = it }

            Text(
                stringResource(Res.string.openconnect_scope_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            TextButton(onClick = { advancedOpen = !advancedOpen }) {
                Text(stringResource(Res.string.openconnect_advanced))
                Spacer(Modifier.width(6.dp))
                Icon(
                    painterResource(
                        if (advancedOpen) Res.drawable.ic_keyboard_arrow_down
                        else Res.drawable.ic_chevron_right,
                    ),
                    contentDescription = null,
                )
            }
            LaunchedEffect(advancedOpen) {
                if (advancedOpen) {
                    kotlinx.coroutines.delay(120)
                    scrollState.animateScrollTo(scrollState.maxValue)
                }
            }
            AnimatedVisibility(advancedOpen) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Field(sni, Res.string.openconnect_sni, placeholder = "vpn.example.com") { sni = it }
                    Field(proxy, Res.string.openconnect_proxy, placeholder = "socks5://127.0.0.1:1080") { proxy = it }
                    Field(mtu, Res.string.openconnect_mtu, placeholder = "1400", numeric = true) { mtu = it }
                    Field(reconnect, Res.string.openconnect_reconnect, placeholder = "300", numeric = true) { reconnect = it }
                    Field(userAgent, Res.string.openconnect_user_agent,
                        placeholder = OpenConnectProfile.defaultUserAgentFor(protocol)) { userAgent = it }

                    PickerField(
                        label = stringResource(Res.string.openconnect_reported_os),
                        options = OpenConnectProfile.REPORTED_OS_VALUES,
                        selected = reportedOs,
                        onSelect = { reportedOs = it },
                    )

                    val p12NotBundle = stringResource(Res.string.cert_error_not_bundle)
                    CertSourceRow(
                        label = stringResource(Res.string.openconnect_client_cert),
                        value = certP12.takeIf { it.isNotBlank() }
                            ?.let { certP12Name.ifBlank { null } }
                            ?: certP12.takeIf { it.isNotBlank() }
                                ?.let { stringResource(Res.string.openconnect_p12_loaded) },
                        emptyLabel = stringResource(Res.string.openconnect_p12_none),
                        hint = stringResource(Res.string.cert_file_hint),
                        error = p12Error,
                        onImport = {
                            scope.launch {
                                p12Error = null
                                val f = platform.pickFileBytes() ?: return@launch
                                val info = CertImport.inspect?.invoke(f.bytes, f.name)
                                if (info != null && info.kind != CertKind.Pkcs12) {
                                    p12Error = p12NotBundle
                                } else {
                                    certP12 = encodeBase64(f.bytes)
                                    certP12Name = f.name
                                }
                            }
                        },
                        onClear = { certP12 = ""; certP12Name = ""; p12Error = null },
                    )
                    if (certP12.isNotBlank()) {
                        Text(
                            stringResource(Res.string.openconnect_p12_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    CertFileRow(Res.string.openconnect_ca_cert, caCert, platform, scope) { caCert = it }
                    Field(caCert, Res.string.openconnect_ca_cert, minLines = 3) { caCert = it }
                    CertFileRow(Res.string.openconnect_client_cert, clientCert, platform, scope) { clientCert = it }
                    Field(clientCert, Res.string.openconnect_client_cert, minLines = 3) { clientCert = it }
                    CertFileRow(Res.string.openconnect_client_key, clientKey, platform, scope) { clientKey = it }
                    Field(clientKey, Res.string.openconnect_client_key, minLines = 3) { clientKey = it }
                    Field(keyPassword, Res.string.openconnect_key_password, secret = true) { keyPassword = it }
                    PickerField(
                        label = stringResource(Res.string.openconnect_token_mode),
                        options = OpenConnectProfile.TOKEN_MODES,
                        selected = tokenMode,
                        onSelect = { tokenMode = it },
                    )
                    if (tokenMode != OpenConnectProfile.TOKEN_NONE) {
                        Field(tokenSecret, Res.string.openconnect_token_secret, minLines = 2, secret = true) { tokenSecret = it }
                    }
                    SwitchRow(Res.string.openconnect_disable_dtls, disableDtls) { disableDtls = it }
                    SwitchRow(Res.string.openconnect_disable_ipv6, disableIpv6) { disableIpv6 = it }
                }
            }

            Button(
                onClick = {
                    onSave(
                        name.trim(),
                        OpenConnectProfile(
                            server = OpenConnectProfile.withPort(server.trim(), port.toIntOrNull()),
                            protocol = protocol,
                            username = username.trim(),
                            password = password,
                            authgroup = authgroup.trim(),
                            caCertPem = caCert.trim(),
                            serverCertSha256 = certPin.trim(),
                            clientCertPem = clientCert.trim(),
                            clientKeyPem = clientKey.trim(),
                            clientKeyPassword = keyPassword,

                            userAgent = userAgent.trim(),
                            reportedOs = reportedOs.trim().ifBlank { "android" },
                            tokenMode = tokenMode,
                            tokenSecret = tokenSecret.trim(),
                            disableDtls = disableDtls,
                            clientCertP12Base64 = certP12,
                            sni = sni.trim(),
                            mtu = mtu.trim().toIntOrNull()?.coerceAtLeast(0) ?: 0,
                            reconnectTimeoutSec = reconnect.trim().toIntOrNull()?.takeIf { it > 0 }
                                ?: OpenConnectProfile.DEFAULT_RECONNECT_TIMEOUT,
                            proxy = proxy.trim(),
                            disableIpv6 = disableIpv6,
                            formEntries = initial?.formEntries.orEmpty(),
                        ),
                    )
                },
                enabled = valid,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Text(stringResource(Res.string.action_save), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun Field(
    value: String,
    labelRes: StringResource,
    placeholder: String? = null,
    minLines: Int = 1,

    secret: Boolean = false,
    numeric: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
        ),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        label = { Text(stringResource(labelRes)) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = minLines == 1,
        minLines = minLines,
        modifier = Modifier.fillMaxWidth(),
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
private fun CertFileRow(
    labelRes: StringResource,
    current: String,
    platform: dev.cluvex.zedsecure.ui.platform.Platform,
    scope: kotlinx.coroutines.CoroutineScope,
    onLoaded: (String) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(labelRes),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = {
            scope.launch {
                platform.pickFileBytes()?.let { onLoaded(it.bytes.decodeToString()) }
            }
        }) { Text(stringResource(Res.string.openconnect_load_file)) }
    }
}

@OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
private fun encodeBase64(bytes: ByteArray): String = kotlin.io.encoding.Base64.Default.encode(bytes)
