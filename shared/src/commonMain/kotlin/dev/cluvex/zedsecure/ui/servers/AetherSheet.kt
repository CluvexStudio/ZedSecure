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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import dev.cluvex.zedsecure.core.psiphon.PsiphonDownloadBus
import dev.cluvex.zedsecure.domain.config.AetherProfile
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.*
import dev.cluvex.zedsecure.ui.components.PickerField
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

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
    var echDns by remember { mutableStateOf(initial?.echDns ?: "") }
    var echDomain by remember { mutableStateOf(initial?.echDomain ?: "") }
    var fragment by remember { mutableStateOf(initial?.fragment ?: false) }
    var fragmentSize by remember { mutableStateOf(initial?.fragmentSize ?: "16-32") }
    var fragmentDelay by remember { mutableStateOf(initial?.fragmentDelay ?: "2-10") }

    var dns by remember { mutableStateOf(initial?.dns ?: "1.1.1.1,1.0.0.1") }
    var exitLoc by remember { mutableStateOf(initial?.exitLoc ?: "") }

    var torMode by remember { mutableStateOf(initial?.torMode ?: AetherProfile.CARRIER_OFF) }
    var torBridges by remember { mutableStateOf(initial?.torBridges ?: "auto") }
    var torRelays by remember { mutableStateOf(initial?.torRelays ?: "auto") }
    var torBridgeLines by remember { mutableStateOf(initial?.torBridgeLines ?: "") }

    var psiphonMode by remember { mutableStateOf(initial?.psiphonMode ?: AetherProfile.CARRIER_OFF) }
    var psiphonTactics by remember { mutableStateOf(initial?.psiphonTactics ?: "auto") }
    var psiphonRegion by remember { mutableStateOf(initial?.psiphonRegion ?: "") }
    var psiphonCdnIps by remember { mutableStateOf(initial?.psiphonCdnIps ?: "") }
    var psiphonCdnSni by remember { mutableStateOf(initial?.psiphonCdnSni ?: "") }

    var teamName by remember { mutableStateOf(initial?.teamName ?: "") }
    var accessClientId by remember { mutableStateOf(initial?.accessClientId ?: "") }
    var accessClientSecret by remember { mutableStateOf(initial?.accessClientSecret ?: "") }
    var accessToken by remember { mutableStateOf(initial?.accessToken ?: "") }
    var gateway by remember { mutableStateOf(initial?.gateway ?: false) }

    var customCommand by remember { mutableStateOf(initial?.customCommand ?: "") }

    val hasAdvanced = remember(initial) {
        initial != null && (
            initial.server.isNotBlank() ||
            initial.wiwOuter.isNotBlank() ||
            initial.wiwInner.isNotBlank() ||
            initial.obfuscation != AetherProfile.NOISE_AUTO ||
            initial.ech ||
            initial.echDns.isNotBlank() ||
            initial.echDomain.isNotBlank() ||
            initial.fragment ||
            (initial.dns.isNotBlank() && initial.dns != "1.1.1.1,1.0.0.1") ||
            initial.exitLoc.isNotBlank() ||
            initial.teamName.isNotBlank() ||
            initial.accessClientId.isNotBlank() ||
            initial.accessClientSecret.isNotBlank() ||
            initial.accessToken.isNotBlank() ||
            initial.gateway ||
            initial.customCommand.isNotBlank()
        )
    }
    var advancedOpen by remember { mutableStateOf(hasAdvanced) }

    val isTwoHops = protocol == AetherProfile.PROTOCOL_GOOL || protocol == AetherProfile.PROTOCOL_MIM
    val isMasque = protocol == AetherProfile.PROTOCOL_MASQUE || protocol == AetherProfile.PROTOCOL_MIM
    val isPsiphonActive = psiphonMode != AetherProfile.CARRIER_OFF
    val isTorActive = torMode != AetherProfile.CARRIER_OFF

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
                stringResource(Res.string.aether_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(Res.string.manual_remark)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            PickerField(
                label = stringResource(Res.string.aether_protocol),
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
                    label = stringResource(Res.string.aether_transport),
                    options = listOf(
                        AetherProfile.TRANSPORT_H3 to "HTTP/3 (QUIC / UDP)",
                        AetherProfile.TRANSPORT_H2 to "HTTP/2 (TLS / TCP)",
                    ),
                    selected = transport,
                    onSelect = { transport = it },
                )
            }

            PickerField(
                label = stringResource(Res.string.aether_scan_mode),
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
                label = stringResource(Res.string.aether_ip_version),
                options = listOf(
                    AetherProfile.IP_V4 to "IPv4 Only",
                    AetherProfile.IP_V6 to "IPv6 Only",
                    AetherProfile.IP_DUAL to "Dual-Stack (IPv4 + IPv6)",
                ),
                selected = ipVersion,
                onSelect = { ipVersion = it },
            )

            PickerField(
                label = stringResource(Res.string.aether_psiphon_integration),
                options = listOf(
                    AetherProfile.CARRIER_OFF to "Disabled",
                    AetherProfile.CARRIER_CHAIN to "Psiphon Chain (WARP over Psiphon)",
                    AetherProfile.CARRIER_REVERSE to "Psiphon Reverse (Psiphon over WARP)",
                    AetherProfile.CARRIER_ONLY to "Psiphon Only",
                ),
                selected = psiphonMode,
                onSelect = { selected ->
                    if (selected != AetherProfile.CARRIER_OFF) {
                        PsiphonDownloadBus.ensure { ready ->
                            if (ready) psiphonMode = selected
                        }
                    } else {
                        psiphonMode = selected
                    }
                },
            )

            if (isPsiphonActive) {
                PickerField(
                    label = stringResource(Res.string.aether_psiphon_mode),
                    options = listOf(
                        "auto" to "Auto (All Transports)",
                        "cdn" to "CDN Fronting Only",
                        "direct" to "Direct Handshake Only",
                    ),
                    selected = psiphonTactics,
                    onSelect = { psiphonTactics = it },
                )

                PsiphonCountryField(
                    selected = psiphonRegion,
                    onSelect = { psiphonRegion = it },
                )

                if (psiphonTactics != "direct") {
                    OutlinedTextField(
                        value = psiphonCdnIps,
                        onValueChange = { psiphonCdnIps = it },
                        label = { Text(stringResource(Res.string.psiphon_cdn_ips)) },
                        placeholder = { Text("Comma-separated or one per line") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = psiphonCdnSni,
                        onValueChange = { psiphonCdnSni = it },
                        label = { Text(stringResource(Res.string.psiphon_cdn_sni)) },
                        placeholder = { Text("e.g. cdn.cloudflare.net") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            PickerField(
                label = stringResource(Res.string.aether_tor_integration),
                options = listOf(
                    AetherProfile.CARRIER_OFF to "Disabled",
                    AetherProfile.CARRIER_CHAIN to "Tor inside Tunnel (Exit: Tor)",
                    AetherProfile.CARRIER_REVERSE to "Tunnel inside Tor (Exit: WARP)",
                    AetherProfile.CARRIER_ONLY to "Tor Alone",
                ),
                selected = torMode,
                onSelect = { torMode = it },
            )

            if (isTorActive) {
                PickerField(
                    label = stringResource(Res.string.aether_tor_bridges),
                    options = listOf(
                        "auto" to "Auto (Bridges on Demand)",
                        "first" to "Always Use Bridges",
                        "never" to "Never (Direct Only)",
                        "own" to "Custom Bridge Lines",
                    ),
                    selected = torBridges,
                    onSelect = { torBridges = it },
                )

                if (torBridges == "auto" || torBridges == "first") {
                    PickerField(
                        label = stringResource(Res.string.aether_tor_relays),
                        options = listOf(
                            "auto" to "BridgeDB + Public Relays",
                            "only" to "Public Relays Only",
                            "off" to "BridgeDB Only",
                        ),
                        selected = torRelays,
                        onSelect = { torRelays = it },
                    )
                }

                if (torBridges == "own") {
                    OutlinedTextField(
                        value = torBridgeLines,
                        onValueChange = { torBridgeLines = it },
                        label = { Text(stringResource(Res.string.aether_tor_bridge_lines)) },
                        placeholder = { Text("obfs4 1.2.3.4:443 ...") },
                        minLines = 2,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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
                        stringResource(Res.string.aether_advanced_settings),
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Medium,
                    )
                    Icon(
                        painter = painterResource(
                            if (advancedOpen) Res.drawable.ic_keyboard_arrow_down else Res.drawable.ic_chevron_right,
                        ),
                        contentDescription = null,
                    )
                }
            }

            AnimatedVisibility(advancedOpen) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (isTwoHops) {
                        OutlinedTextField(
                            value = wiwOuter,
                            onValueChange = { wiwOuter = it },
                            label = { Text(stringResource(Res.string.aether_wiw_outer)) },
                            placeholder = { Text("IP:Port (e.g. 162.159.192.1:2408)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = wiwInner,
                            onValueChange = { wiwInner = it },
                            label = { Text(stringResource(Res.string.aether_wiw_inner)) },
                            placeholder = { Text("IP:Port (e.g. 162.159.193.1:2408)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        OutlinedTextField(
                            value = server,
                            onValueChange = { server = it },
                            label = { Text(stringResource(Res.string.aether_custom_peer)) },
                            placeholder = { Text("IP:Port (e.g. 162.159.192.1:2408)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    PickerField(
                        label = stringResource(Res.string.aether_noise),
                        options = listOf(
                            AetherProfile.NOISE_AUTO to "Auto (Protocol Default)",
                            AetherProfile.NOISE_OFF to "Off",
                            AetherProfile.NOISE_LIGHT to "Light",
                            AetherProfile.NOISE_FIREWALL to "Firewall Evasion",
                            AetherProfile.NOISE_BALANCED to "Balanced",
                            AetherProfile.NOISE_GFW to "Deep Inspection Bypassing (GFW)",
                            AetherProfile.NOISE_AGGRESSIVE to "Aggressive",
                        ),
                        selected = obfuscation,
                        onSelect = { obfuscation = it },
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(Res.string.aether_ech), style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = ech, onCheckedChange = { ech = it })
                    }
                    if (ech) {
                        OutlinedTextField(
                            value = echDns,
                            onValueChange = { echDns = it },
                            label = { Text(stringResource(Res.string.aether_ech_dns)) },
                            placeholder = { Text("e.g. https://1.1.1.1/dns-query or udp://1.1.1.1") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = echDomain,
                            onValueChange = { echDomain = it },
                            label = { Text(stringResource(Res.string.aether_ech_domain)) },
                            placeholder = { Text("e.g. cloudflare-ech.com") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(Res.string.aether_fragment), style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = fragment, onCheckedChange = { fragment = it })
                    }
                    if (fragment) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = fragmentSize,
                                onValueChange = { fragmentSize = it },
                                label = { Text(stringResource(Res.string.aether_fragment_size)) },
                                placeholder = { Text("16-32") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = fragmentDelay,
                                onValueChange = { fragmentDelay = it },
                                label = { Text(stringResource(Res.string.aether_fragment_delay)) },
                                placeholder = { Text("2-10") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    OutlinedTextField(
                        value = dns,
                        onValueChange = { dns = it },
                        label = { Text(stringResource(Res.string.aether_dns)) },
                        placeholder = { Text("1.1.1.1,1.0.0.1") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = exitLoc,
                        onValueChange = { exitLoc = it },
                        label = { Text(stringResource(Res.string.aether_exit_loc)) },
                        placeholder = { Text("e.g. DE,SE or !IR,RU") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Text(
                        stringResource(Res.string.aether_zero_trust),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    OutlinedTextField(
                        value = teamName,
                        onValueChange = { teamName = it },
                        label = { Text(stringResource(Res.string.aether_team_name)) },
                        placeholder = { Text("e.g. org.cloudflareaccess.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = accessClientId,
                        onValueChange = { accessClientId = it },
                        label = { Text(stringResource(Res.string.aether_access_id)) },
                        placeholder = { Text("CF-Access-Client-Id (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = accessClientSecret,
                        onValueChange = { accessClientSecret = it },
                        label = { Text(stringResource(Res.string.aether_access_secret)) },
                        placeholder = { Text("CF-Access-Client-Secret (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = accessToken,
                        onValueChange = { accessToken = it },
                        label = { Text(stringResource(Res.string.aether_access_token)) },
                        placeholder = { Text("Service token / JWT (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(Res.string.aether_gateway), style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = gateway, onCheckedChange = { gateway = it })
                    }

                    OutlinedTextField(
                        value = customCommand,
                        onValueChange = { customCommand = it },
                        label = { Text(stringResource(Res.string.aether_custom_command)) },
                        placeholder = { Text("--bind 127.0.0.1:11819 --protocol masque ...") },
                        supportingText = { Text(stringResource(Res.string.aether_custom_command_hint)) },
                        minLines = 2,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Button(
                onClick = {
                    val proceed = {
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
                            echDns = echDns.trim(),
                            echDomain = echDomain.trim(),
                            fragment = fragment,
                            fragmentSize = fragmentSize.trim().ifBlank { "16-32" },
                            fragmentDelay = fragmentDelay.trim().ifBlank { "2-10" },
                            dns = dns.trim().ifBlank { "1.1.1.1,1.0.0.1" },
                            exitLoc = exitLoc.trim().uppercase(),
                            torMode = torMode,
                            torBridges = torBridges,
                            torRelays = torRelays,
                            torBridgeLines = torBridgeLines.trim(),
                            psiphonMode = psiphonMode,
                            psiphonTactics = psiphonTactics,
                            psiphonRegion = psiphonRegion.trim(),
                            psiphonCdnIps = psiphonCdnIps.trim(),
                            psiphonCdnSni = psiphonCdnSni.trim(),
                            teamName = teamName.trim(),
                            accessClientId = accessClientId.trim(),
                            accessClientSecret = accessClientSecret.trim(),
                            accessToken = accessToken.trim(),
                            gateway = gateway,
                            customCommand = customCommand.trim(),
                        )
                        onSave(name.trim().ifBlank { "Aether ${protocol.uppercase()}" }, profile)
                        onDismiss()
                    }
                    if (psiphonMode != AetherProfile.CARRIER_OFF) {
                        PsiphonDownloadBus.ensure { ready ->
                            if (ready) proceed()
                        }
                    } else {
                        proceed()
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(stringResource(Res.string.action_save))
            }
        }
    }
}
