@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.cluvex.zedsecure.ui.servers

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cluvex.zedsecure.domain.config.AetherProfile
import dev.cluvex.zedsecure.shared.resources.*
import dev.cluvex.zedsecure.ui.components.PickerField
import dev.cluvex.zedsecure.ui.platform.appVerticalScroll
import dev.cluvex.zedsecure.ui.platform.rememberAppSheetState
import org.jetbrains.compose.resources.stringResource

@Composable
fun AetherSheet(
    initial: AetherProfile? = null,
    initialName: String = "",
    onDismiss: () -> Unit,
    onSave: (String, AetherProfile) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var settings by remember { mutableStateOf(initial ?: AetherProfile()) }
    val valid = runCatching { settings.validate() }.isSuccess
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberAppSheetState()) {
        Column(
            Modifier.fillMaxWidth().appVerticalScroll(rememberScrollState()).imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.aether_add_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(stringResource(Res.string.aether_description), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = name, onValueChange = { name = it }, label = { Text(stringResource(Res.string.manual_remark)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            PickerField(
                label = stringResource(Res.string.aether_protocol),
                options = listOf("masque" to "MASQUE", "wg" to "WireGuard", "gool" to "WireGuard over MASQUE", "gool-classic" to "WireGuard in WireGuard", "mim" to "MASQUE in MASQUE"),
                selected = settings.protocol,
                onSelect = { settings = settings.copy(protocol = it, http2 = settings.http2 && it in listOf("masque", "gool", "mim"), peer = if (it == "gool-classic") "" else settings.peer) },
            )
            if (settings.usesMasque) {
                AetherSwitch(stringResource(Res.string.aether_http2), settings.http2) { settings = settings.copy(http2 = it) }
            }
            PickerField(
                label = stringResource(Res.string.aether_scan), options = AetherProfile.SCAN_MODES.map { it to it },
                selected = settings.scan, onSelect = { settings = settings.copy(scan = it) },
            )
            PickerField(
                label = stringResource(Res.string.aether_ip_version), options = listOf("v4" to "IPv4", "v6" to "IPv6", "both" to "IPv4 + IPv6"),
                selected = settings.ipVersion, onSelect = { settings = settings.copy(ipVersion = it) },
            )
            PickerField(
                label = stringResource(Res.string.aether_noize), options = AetherProfile.NOIZE_PROFILES.map { it to it },
                selected = settings.noize, onSelect = { settings = settings.copy(noize = it) },
            )
            if (settings.protocol != "gool-classic") {
                OutlinedTextField(
                    value = settings.peer, onValueChange = { settings = settings.copy(peer = it) },
                    label = { Text(stringResource(Res.string.aether_peer)) },
                    supportingText = { Text(stringResource(Res.string.aether_peer_hint)) },
                    isError = settings.peer.isNotBlank() && !AetherProfile.validPeer(settings.peer.trim()),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
            AetherSwitch(stringResource(Res.string.aether_fragment), settings.fragment) { settings = settings.copy(fragment = it) }
            if (settings.usesMasque) {
                AetherSwitch(stringResource(Res.string.aether_ech), settings.ech) { settings = settings.copy(ech = it) }
                AetherSwitch(stringResource(Res.string.aether_tls_verify), settings.tlsVerify) { settings = settings.copy(tlsVerify = it) }
            }
            Button(
                enabled = valid, onClick = { onSave(name.trim(), settings.copy(peer = settings.peer.trim())) },
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) { Text(stringResource(Res.string.action_save), fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun AetherSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
