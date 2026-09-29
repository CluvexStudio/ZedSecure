@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.cluvex.zedsecure.ui.servers

import org.jetbrains.compose.resources.StringResource

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.*
import dev.cluvex.zedsecure.domain.config.DnsTunnelProfile
import dev.cluvex.zedsecure.domain.config.DohPresets
import dev.cluvex.zedsecure.ui.components.PickerField
import dev.cluvex.zedsecure.ui.platform.draggableHorizontalScroll

@Composable
fun DnsTunnelSheet(
    initial: DnsTunnelProfile? = null,
    initialName: String = "",
    onDismiss: () -> Unit,
    onSave: (name: String, DnsTunnelProfile) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var engine by remember { mutableStateOf(initial?.engine ?: DnsTunnelProfile.ENGINE_DNSTT) }
    var domain by remember { mutableStateOf(initial?.domain ?: "") }
    var publicKey by remember { mutableStateOf(initial?.publicKey ?: "") }
    var transport by remember { mutableStateOf(initial?.dnsTransport ?: DnsTunnelProfile.TRANSPORT_UDP) }
    var resolvers by remember { mutableStateOf(initial?.resolvers ?: "") }
    var dohUrl by remember { mutableStateOf(initial?.dohUrl ?: "") }
    var resolverMode by remember { mutableStateOf(initial?.resolverMode ?: DnsTunnelProfile.MODE_FANOUT) }
    var spread by remember { mutableStateOf(initial?.rrSpreadCount ?: 3) }
    var authoritative by remember { mutableStateOf(initial?.authoritative ?: false) }
    var autoTune by remember { mutableStateOf(initial?.autoTune ?: false) }
    var payloadSize by remember { mutableStateOf(initial?.dnsPayloadSize ?: 0) }
    var recordType by remember { mutableStateOf(initial?.recordType ?: "txt") }
    var qnameLen by remember { mutableStateOf(initial?.maxQnameLen ?: 101) }
    var rps by remember { mutableStateOf(initial?.rps?.takeIf { it > 0 }?.toString() ?: "") }
    var idleTimeout by remember { mutableStateOf(initial?.idleTimeout?.takeIf { it > 0 }?.toString() ?: "") }
    var keepalive by remember { mutableStateOf(initial?.keepalive?.takeIf { it > 0 }?.toString() ?: "") }
    var udpTimeout by remember { mutableStateOf(initial?.udpTimeout?.takeIf { it > 0 }?.toString() ?: "") }
    var maxLabels by remember { mutableStateOf(initial?.maxNumLabels?.takeIf { it > 0 }?.toString() ?: "") }
    var clientIdSize by remember { mutableStateOf(initial?.clientIdSize?.takeIf { it > 0 }?.toString() ?: "") }
    var socksUser by remember { mutableStateOf(initial?.socksUser ?: "") }
    var socksPass by remember { mutableStateOf(initial?.socksPass ?: "") }
    var advancedOpen by remember { mutableStateOf(false) }

    var sshEnabled by remember { mutableStateOf(initial?.sshEnabled ?: false) }
    var sshHost by remember { mutableStateOf(initial?.sshHost ?: "") }
    var sshPort by remember { mutableStateOf((initial?.sshPort ?: 22).toString()) }
    var sshUser by remember { mutableStateOf(initial?.sshUsername ?: "") }
    var sshAuth by remember { mutableStateOf(initial?.sshAuthType ?: DnsTunnelProfile.AUTH_PASSWORD) }
    var sshPass by remember { mutableStateOf(initial?.sshPassword ?: "") }
    var sshKey by remember { mutableStateOf(initial?.sshPrivateKey ?: "") }
    var sshKeyPass by remember { mutableStateOf(initial?.sshKeyPassphrase ?: "") }
    var forwardDns by remember { mutableStateOf(initial?.forwardDnsThroughSsh ?: false) }

    val isVaydns = engine == DnsTunnelProfile.ENGINE_VAYDNS
    val isDoh = transport == DnsTunnelProfile.TRANSPORT_DOH
    val multiResolver = resolvers.contains(",") || resolvers.contains("\n")

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
                stringResource(Res.string.dns_tunnel_add_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            PickerField(
                label = stringResource(Res.string.dns_tunnel_engine),
                options = listOf(
                    DnsTunnelProfile.ENGINE_DNSTT to "DNSTT",
                    DnsTunnelProfile.ENGINE_VAYDNS to "VayDNS",
                ),
                selected = engine,
                onSelect = { engine = it },
            )
            Field(name, Res.string.manual_remark) { name = it }
            Field(domain, Res.string.dns_tunnel_domain, placeholder = "t.example.com") { domain = it }
            Field(publicKey, Res.string.dns_tunnel_pubkey) { publicKey = it }

            Label(Res.string.dns_tunnel_transport)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                DnsTunnelProfile.TRANSPORTS.forEachIndexed { i, (value, display) ->
                    SegmentedButton(
                        selected = transport == value,
                        onClick = { transport = value },
                        shape = SegmentedButtonDefaults.itemShape(i, DnsTunnelProfile.TRANSPORTS.size),
                    ) { Text(display) }
                }
            }

            if (isDoh) {
                PickerField(
                    label = stringResource(Res.string.dns_tunnel_doh_server),
                    options = DohPresets.SERVERS.map { it.url to it.name },
                    selected = DohPresets.SERVERS.firstOrNull { it.url == dohUrl }?.url ?: "",
                    onSelect = { dohUrl = it },
                )
                Field(dohUrl, Res.string.dns_tunnel_doh_url, placeholder = "https://dns.google/dns-query") { dohUrl = it }
            } else {
                Field(
                    resolvers, Res.string.dns_tunnel_resolver,
                    placeholder = "8.8.8.8, 1.1.1.1", minLines = 2,
                ) { resolvers = it }

                if (multiResolver) {
                    Label(Res.string.dns_tunnel_resolver_mode)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DnsTunnelProfile.RESOLVER_MODES.forEach { (value, display) ->
                            FilterChip(
                                selected = resolverMode == value,
                                onClick = { resolverMode = value },
                                label = { Text(display) },
                            )
                        }
                    }
                    if (resolverMode == DnsTunnelProfile.MODE_ROUND_ROBIN) {
                        Stepper(
                            labelRes = Res.string.dns_tunnel_spread,
                            value = spread, min = 1, max = 5,
                            onChange = { spread = it },
                        )
                    }
                }
            }

            if (!isVaydns) {
                SwitchRow(Res.string.dns_tunnel_authoritative, authoritative) { authoritative = it }
                if (authoritative) Warn(Res.string.dns_tunnel_authoritative_warn)
            }

            if (isVaydns) {
                Label(Res.string.dns_tunnel_record_type)
                Row(
                    Modifier.draggableHorizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DnsTunnelProfile.RECORD_TYPES.forEach { rt ->
                        FilterChip(
                            selected = recordType == rt,
                            onClick = { recordType = rt },
                            label = { Text(rt.uppercase()) },
                        )
                    }
                }
                run {
                    Label(Res.string.dns_tunnel_query_len, suffix = "$qnameLen")
                    Slider(
                        value = qnameLen.toFloat(),
                        onValueChange = { qnameLen = it.toInt() },
                        valueRange = 60f..253f,
                    )
                    Field(rps, Res.string.dns_tunnel_rps, placeholder = "0", numeric = true) { rps = it }
                }

                Surface(
                    onClick = { advancedOpen = !advancedOpen },
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(Res.string.dns_tunnel_advanced),
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Medium,
                        )
                        Icon(
                            painter = org.jetbrains.compose.resources.painterResource(
                                if (advancedOpen) Res.drawable.ic_keyboard_arrow_down else Res.drawable.ic_chevron_right,
                            ),
                            contentDescription = null,
                        )
                    }
                }
                AnimatedVisibility(advancedOpen) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Field(idleTimeout, Res.string.dns_tunnel_idle, placeholder = "10", numeric = true) { idleTimeout = it }
                        Field(keepalive, Res.string.dns_tunnel_keepalive, placeholder = "2", numeric = true) { keepalive = it }
                        Field(udpTimeout, Res.string.dns_tunnel_udp_timeout, placeholder = "500", numeric = true) { udpTimeout = it }
                        Field(maxLabels, Res.string.dns_tunnel_max_labels, placeholder = "0", numeric = true) { maxLabels = it }
                        Field(clientIdSize, Res.string.dns_tunnel_client_id, placeholder = "2", numeric = true) { clientIdSize = it }
                    }
                }
            } else {
                Label(Res.string.dns_tunnel_query_size, suffix = if (payloadSize == 0) "full" else "$payloadSize")
                Row(
                    Modifier.draggableHorizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DnsTunnelProfile.PAYLOAD_PRESETS.forEach { p ->
                        FilterChip(
                            selected = payloadSize == p,
                            onClick = { payloadSize = p },
                            label = { Text(if (p == 0) "Full" else "$p") },
                        )
                    }
                }
            }

            Field(socksUser, Res.string.dns_tunnel_socks_user) { socksUser = it }
            Field(socksPass, Res.string.dns_tunnel_socks_pass) { socksPass = it }

            SwitchRow(Res.string.dns_ssh_enable, sshEnabled) { sshEnabled = it }
            AnimatedVisibility(sshEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Field(sshHost, Res.string.ssh_host, placeholder = "example.com") { sshHost = it }
                    Field(sshPort, Res.string.ssh_port, numeric = true) { sshPort = it }
                    Field(sshUser, Res.string.ssh_username) { sshUser = it }
                    PickerField(
                        label = stringResource(Res.string.ssh_auth),
                        options = listOf(
                            DnsTunnelProfile.AUTH_PASSWORD to stringResource(Res.string.ssh_auth_password),
                            DnsTunnelProfile.AUTH_KEY to stringResource(Res.string.ssh_auth_key),
                        ),
                        selected = sshAuth,
                        onSelect = { sshAuth = it },
                    )
                    if (sshAuth == DnsTunnelProfile.AUTH_KEY) {
                        Field(sshKey, Res.string.ssh_private_key, minLines = 4) { sshKey = it }
                        Field(sshKeyPass, Res.string.ssh_passphrase) { sshKeyPass = it }
                    } else {
                        Field(sshPass, Res.string.manual_password) { sshPass = it }
                    }
                    SwitchRow(Res.string.dns_ssh_forward, forwardDns) { forwardDns = it }
                }
            }

            Button(
                onClick = {
                    onSave(
                        name.trim(),
                        DnsTunnelProfile(
                            engine = engine,
                            domain = domain.trim(),
                            publicKey = publicKey.trim(),
                            dnsTransport = transport,
                            resolvers = resolvers.split('\n', ',')
                                .map { it.trim() }.filter { it.isNotEmpty() }.joinToString(","),
                            dohUrl = dohUrl.trim(),
                            resolverMode = resolverMode,
                            rrSpreadCount = spread,
                            authoritative = authoritative,
                            autoTune = autoTune,
                            dnsPayloadSize = payloadSize,
                            recordType = recordType,
                            maxQnameLen = qnameLen,
                            rps = rps.toDoubleOrNull() ?: 0.0,
                            idleTimeout = idleTimeout.toIntOrNull() ?: 0,
                            keepalive = keepalive.toIntOrNull() ?: 0,
                            udpTimeout = udpTimeout.toIntOrNull() ?: 0,
                            maxNumLabels = maxLabels.toIntOrNull() ?: 0,
                            clientIdSize = clientIdSize.toIntOrNull() ?: 0,
                            socksUser = socksUser.trim(),
                            socksPass = socksPass,
                            sshEnabled = sshEnabled,
                            sshHost = sshHost.trim(),
                            sshPort = sshPort.toIntOrNull() ?: 22,
                            sshUsername = sshUser.trim(),
                            sshAuthType = sshAuth,
                            sshPassword = sshPass,
                            sshPrivateKey = sshKey.trim(),
                            sshKeyPassphrase = sshKeyPass,
                            forwardDnsThroughSsh = forwardDns,
                        ),
                    )
                },
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
    numeric: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(if (numeric) it.filter { c -> c.isDigit() || c == '.' } else it) },
        label = { Text(stringResource(labelRes)) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = minLines == 1,
        minLines = minLines,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Label(res: StringResource, suffix: String? = null) {
    Text(
        text = stringResource(res) + (suffix?.let { "  ·  $it" } ?: ""),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun Warn(res: StringResource) {
    Text(
        stringResource(res),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun Stepper(labelRes: StringResource, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(labelRes), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        androidx.compose.material3.IconButton(
            onClick = { if (value > min) onChange(value - 1) },
            enabled = value > min,
        ) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text("$value", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        androidx.compose.material3.IconButton(
            onClick = { if (value < max) onChange(value + 1) },
            enabled = value < max,
        ) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}
