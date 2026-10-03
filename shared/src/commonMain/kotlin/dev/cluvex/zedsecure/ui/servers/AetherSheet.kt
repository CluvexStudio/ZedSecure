@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.cluvex.zedsecure.ui.servers

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cluvex.zedsecure.domain.config.AetherProfile
import dev.cluvex.zedsecure.ui.components.PickerField

@Composable
fun AetherSheet(
    initial: AetherProfile? = null,
    initialName: String = "",
    onDismiss: () -> Unit,
    onSave: (name: String, AetherProfile) -> Unit,
) {
    var name by remember { mutableStateOf(initialName.ifBlank { "Aether MASQUE" }) }
    var protocol by remember { mutableStateOf(initial?.protocol ?: AetherProfile.PROTOCOL_MASQUE) }
    var transport by remember { mutableStateOf(initial?.transport ?: AetherProfile.TRANSPORT_H3) }
    var scanMode by remember { mutableStateOf(initial?.scanMode ?: AetherProfile.SCAN_BALANCED) }
    var obfuscation by remember { mutableStateOf(initial?.obfuscation ?: AetherProfile.NOISE_AUTO) }
    var ipVersion by remember { mutableStateOf(initial?.ipVersion ?: AetherProfile.IP_V4) }

    var server by remember { mutableStateOf(initial?.server ?: "") }
    var wiwOuter by remember { mutableStateOf(initial?.wiwOuter ?: "") }
    var wiwInner by remember { mutableStateOf(initial?.wiwInner ?: "") }

    var ech by remember { mutableStateOf(initial?.ech ?: false) }
    var fragment by remember { mutableStateOf(initial?.fragment ?: false) }
    var fragmentSize by remember { mutableStateOf(initial?.fragmentSize ?: "16-32") }
    var fragmentDelay by remember { mutableStateOf(initial?.fragmentDelay ?: "2-10") }

    var torMode by remember { mutableStateOf(initial?.torMode ?: AetherProfile.CARRIER_OFF) }
    var psiphonMode by remember { mutableStateOf(initial?.psiphonMode ?: AetherProfile.CARRIER_OFF) }

    val isTwoHops = protocol == AetherProfile.PROTOCOL_GOOL || protocol == AetherProfile.PROTOCOL_MIM
    val isMasque = protocol == AetherProfile.PROTOCOL_MASQUE || protocol == AetherProfile.PROTOCOL_MIM

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
                "Aether WARP & MASQUE",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Configuration Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            PickerField(
                label = "Protocol",
                options = listOf(
                    AetherProfile.PROTOCOL_MASQUE to "MASQUE (HTTP/3 & HTTP/2)",
                    AetherProfile.PROTOCOL_WIREGUARD to "WireGuard",
                    AetherProfile.PROTOCOL_GOOL to "WARP-in-WARP (gool)",
                    AetherProfile.PROTOCOL_MIM to "MASQUE-in-MASQUE (mim)",
                ),
                selected = protocol,
                onSelect = { protocol = it },
            )

            if (isMasque) {
                PickerField(
                    label = "MASQUE Transport",
                    options = listOf(
                        AetherProfile.TRANSPORT_H3 to "HTTP/3 (QUIC / UDP)",
                        AetherProfile.TRANSPORT_H2 to "HTTP/2 (TLS / TCP)",
                    ),
                    selected = transport,
                    onSelect = { transport = it },
                )
            }

            PickerField(
                label = "Endpoint Scan Mode",
                options = listOf(
                    AetherProfile.SCAN_TURBO to "Turbo (Fastest)",
                    AetherProfile.SCAN_BALANCED to "Balanced (Recommended)",
                    AetherProfile.SCAN_THOROUGH to "Thorough",
                    AetherProfile.SCAN_VERIFIED to "Verified",
                    AetherProfile.SCAN_IRONCLAD to "Ironclad",
                ),
                selected = scanMode,
                onSelect = { scanMode = it },
            )

            PickerField(
                label = "Noise / Obfuscation",
                options = listOf(
                    AetherProfile.NOISE_AUTO to "Auto",
                    AetherProfile.NOISE_OFF to "Off",
                    AetherProfile.NOISE_LIGHT to "Light",
                    AetherProfile.NOISE_FIREWALL to "Firewall Evasion",
                    AetherProfile.NOISE_BALANCED to "Balanced",
                    AetherProfile.NOISE_GFW to "Deep Inspection Bypassing",
                    AetherProfile.NOISE_AGGRESSIVE to "Aggressive",
                ),
                selected = obfuscation,
                onSelect = { obfuscation = it },
            )

            PickerField(
                label = "IP Version",
                options = listOf(
                    AetherProfile.IP_V4 to "IPv4 Only",
                    AetherProfile.IP_V6 to "IPv6 Only",
                    AetherProfile.IP_DUAL to "Dual-Stack (IPv4 + IPv6)",
                ),
                selected = ipVersion,
                onSelect = { ipVersion = it },
            )

            if (isTwoHops) {
                OutlinedTextField(
                    value = wiwOuter,
                    onValueChange = { wiwOuter = it },
                    label = { Text("Outer Endpoint (IP:Port, optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = wiwInner,
                    onValueChange = { wiwInner = it },
                    label = { Text("Inner Endpoint (IP:Port, optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                OutlinedTextField(
                    value = server,
                    onValueChange = { server = it },
                    label = { Text("Custom Peer Endpoint (IP:Port, optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (isMasque) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Encrypted Client Hello (ECH)", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = ech, onCheckedChange = { ech = it })
                }

                if (transport == AetherProfile.TRANSPORT_H2) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("TLS ClientHello Fragmentation", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = fragment, onCheckedChange = { fragment = it })
                    }
                    if (fragment) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = fragmentSize,
                                onValueChange = { fragmentSize = it },
                                label = { Text("Fragment Size") },
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = fragmentDelay,
                                onValueChange = { fragmentDelay = it },
                                label = { Text("Delay (ms)") },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            PickerField(
                label = "Tor Integration",
                options = listOf(
                    AetherProfile.CARRIER_OFF to "Disabled",
                    AetherProfile.CARRIER_CHAIN to "Tor inside Tunnel (Exit: Tor)",
                    AetherProfile.CARRIER_REVERSE to "Tunnel inside Tor (Exit: WARP)",
                    AetherProfile.CARRIER_ONLY to "Tor Alone",
                ),
                selected = torMode,
                onSelect = { torMode = it },
            )

            PickerField(
                label = "Psiphon Integration",
                options = listOf(
                    AetherProfile.CARRIER_OFF to "Disabled",
                    AetherProfile.CARRIER_CHAIN to "Psiphon inside Tunnel",
                    AetherProfile.CARRIER_REVERSE to "Tunnel inside Psiphon",
                ),
                selected = psiphonMode,
                onSelect = { psiphonMode = it },
            )

            Button(
                onClick = {
                    val profile = AetherProfile(
                        protocol = protocol,
                        transport = transport,
                        scanMode = scanMode,
                        obfuscation = obfuscation,
                        ipVersion = ipVersion,
                        server = server.trim(),
                        wiwOuter = wiwOuter.trim(),
                        wiwInner = wiwInner.trim(),
                        ech = ech,
                        fragment = fragment,
                        fragmentSize = fragmentSize.trim(),
                        fragmentDelay = fragmentDelay.trim(),
                        torMode = torMode,
                        psiphonMode = psiphonMode,
                    )
                    onSave(name.trim().ifBlank { "Aether ${protocol.uppercase()}" }, profile)
                    onDismiss()
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text("Save Configuration")
            }
        }
    }
}
