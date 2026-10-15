package dev.cluvex.zedsecure.ui.aether

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cluvex.zedsecure.domain.config.AetherProfile

private fun fmtBytes(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "${"%.1f".format(b / 1024.0)} KB"
    b < 1024 * 1024 * 1024 -> "${"%.1f".format(b / 1048576.0)} MB"
    else -> "${"%.2f".format(b / 1073741824.0)} GB"
}

@Composable
fun AetherScreen(bridge: AetherBridge, modifier: Modifier = Modifier) {
    val s by bridge.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Status + power.
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when (s.phase) {
                        "live" -> "Tunnel live"
                        "working" -> s.statusLine.ifBlank { "Working…" }
                        "error" -> "Connection failed"
                        else -> "Offline"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                )
                if (s.detailLine.isNotBlank()) Text(s.detailLine, style = MaterialTheme.typography.bodySmall)
                s.torPercent?.let { Text("Tor bootstrap $it%", style = MaterialTheme.typography.bodySmall) }
                if (s.phase == "live") {
                    Text(
                        "↓${fmtBytes(s.downBytes)}  ↑${fmtBytes(s.upBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Button(
                    onClick = { bridge.toggle() },
                    enabled = s.phase != "working",
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (s.phase == "working") CircularProgressIndicator(Modifier.padding(end = 8.dp))
                    Text(if (s.phase == "live" || s.phase == "working") "Disconnect" else "Connect")
                }
            }
        }

        // Endpoints.
        if (s.phase == "live") {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    EndpointRow("SOCKS5", s.socksAddr) { clipboard.setText(AnnotatedString(s.socksAddr)) }
                    EndpointRow("HTTP", s.httpAddr) { clipboard.setText(AnnotatedString(s.httpAddr)) }
                    s.torExit?.let { EndpointRow("Tor exit", it) { clipboard.setText(AnnotatedString(it)) } }
                    s.psiExit?.let { EndpointRow("Psiphon exit", it) { clipboard.setText(AnnotatedString(it)) } }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("System proxy", modifier = Modifier.weight(1f))
                        Switch(checked = s.sysProxy, onCheckedChange = { bridge.setSysProxy(it) })
                    }
                }
            }
        }

        // Transport.
        SectionCard("Transport") {
            ChipRow("Protocol", listOf("auto", "masque", "wireguard", "gool", "gool-classic", "mim"), s.profile.protocol) {
                bridge.updateProfile(s.profile.copy(protocol = it))
            }
            ChipRow("Scan", listOf("turbo", "balanced", "thorough", "verified", "ironclad"), s.profile.scanMode) {
                bridge.updateProfile(s.profile.copy(scanMode = it))
            }
            ChipRow(
                "Overlay",
                listOf("none", "tor", "tor_reverse", "tor_only", "psiphon", "psiphon_reverse", "psiphon_only"),
                s.profile.extraTransport,
            ) { bridge.updateProfile(s.profile.copy(extraTransport = it)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Quick reconnect", modifier = Modifier.weight(1f))
                Switch(checked = s.profile.quickReconnect, onCheckedChange = { bridge.updateProfile(s.profile.copy(quickReconnect = it)) })
            }
        }

        // Tor options.
        SectionCard("Tor options") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Straight to bridges", modifier = Modifier.weight(1f))
                Switch(checked = s.profile.torBridges, onCheckedChange = { bridge.updateProfile(s.profile.copy(torBridges = it)) })
            }
            OptText("Relays mode", s.profile.torRelays, "auto") { bridge.updateProfile(s.profile.copy(torRelays = it)) }
            OptText("Custom bridges (one per line)", s.profile.torBridge, "obfs4 …", multi = true) {
                bridge.updateProfile(s.profile.copy(torBridge = it))
            }
            OptText("Bridge file", s.profile.torBridgeFile, "optional path") { bridge.updateProfile(s.profile.copy(torBridgeFile = it)) }
            OptText("Tor listener", s.profile.torBind, "127.0.0.1:1820") { bridge.updateProfile(s.profile.copy(torBind = it)) }
        }

        // Psiphon options.
        SectionCard("Psiphon options") {
            OptText("Region (DE, empty = auto)", s.profile.psiphonRegion, "") { bridge.updateProfile(s.profile.copy(psiphonRegion = it)) }
            ChipRow("Shape", listOf("auto", "cdn", "direct"), s.profile.psiphonMode) {
                bridge.updateProfile(s.profile.copy(psiphonMode = it))
            }
            OptText("Custom config", s.profile.psiphonConfig, "optional path") { bridge.updateProfile(s.profile.copy(psiphonConfig = it)) }
            OptText("Server entries", s.profile.psiphonServerEntries, "optional file") {
                bridge.updateProfile(s.profile.copy(psiphonServerEntries = it))
            }
            OptText("CDN IPs", s.profile.psiphonCdnIps, "optional") { bridge.updateProfile(s.profile.copy(psiphonCdnIps = it)) }
            OptText("CDN names", s.profile.psiphonCdnSni, "optional") { bridge.updateProfile(s.profile.copy(psiphonCdnSni = it)) }
            OptText("CDN sets", s.profile.psiphonCdnSets, "e.g. cloudflare,fastly") {
                bridge.updateProfile(s.profile.copy(psiphonCdnSets = it))
            }
            OptText("Psiphon listener", s.profile.psiphonBind, "127.0.0.1:1821") {
                bridge.updateProfile(s.profile.copy(psiphonBind = it))
            }
        }

        // Network.
        SectionCard("Network") {
            OptText("SOCKS bind (0.0.0.0 = LAN share)", s.profile.bind, "127.0.0.1:1819") {
                bridge.updateProfile(s.profile.copy(bind = it))
            }
            OptText("HTTP port (empty = SOCKS+1)", s.profile.httpPort, "") { bridge.updateProfile(s.profile.copy(httpPort = it)) }
            OptText("Upstream proxy", s.profile.upstream, "socks5://… (optional)") {
                bridge.updateProfile(s.profile.copy(upstream = it))
            }
            OptText("DNS", s.profile.dns, "optional") { bridge.updateProfile(s.profile.copy(dns = it)) }
            OptText("Exit filter (!IR,AZ,RU)", s.profile.exitLoc, "optional") { bridge.updateProfile(s.profile.copy(exitLoc = it)) }
        }

        // ECH quick preset.
        SectionCard("Handshake disguise (ECH)") {
            ChipRow("ECH", listOf("off", "auto", "custom"), echPresetOf(s.profile)) { preset ->
                val base = s.profile
                bridge.updateProfile(
                    when (preset) {
                        "off" -> base.copy(ech = "", echDns = "", echDomain = "")
                        "auto" -> base.copy(ech = "auto", echDns = "", echDomain = "")
                        else -> if (base.ech.isBlank()) base.copy(ech = "auto") else base
                    },
                )
            }
            if (echPresetOf(s.profile) == "custom") {
                OptText("ECH key", s.profile.ech, "auto or base64") { bridge.updateProfile(s.profile.copy(ech = it)) }
                OptText("Key resolver", s.profile.echDns, "default udp://1.1.1.1") {
                    bridge.updateProfile(s.profile.copy(echDns = it))
                }
                OptText("Key domain", s.profile.echDomain, "default cloudflare-ech.com") {
                    bridge.updateProfile(s.profile.copy(echDomain = it))
                }
            }
        }

        // LAN clients.
        SectionCard("LAN clients (${s.clients})") {
            Text(
                "Devices on your network using this proxy. Counted from live connections; names resolve when routers answer.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = { bridge.refreshClients() }) { Text("Refresh now") }
        }

        // Matrix lab.
        SectionCard("Connection matrix") {
            Text(
                "Tries every protocol × warp/psiphon combo at once and times each tunnel. No Tor rows — Tor never answers fast.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { bridge.runMatrix() }, enabled = !s.matrixRunning) { Text("Test all") }
                if (s.matrixRunning) {
                    OutlinedButton(onClick = { bridge.stopMatrix() }) { Text("Stop") }
                }
            }
            s.matrix.forEach { row ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${row.proto} + ${row.trans}",
                        modifier = Modifier.weight(1f),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        when {
                            row.phase != "done" -> if (row.phase == "running") "…" else ""
                            row.ok && row.ms != null -> "${row.ms}ms"
                            row.ok -> "ok"
                            else -> row.note.take(24)
                        },
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (row.ok) {
                        OutlinedButton(onClick = {
                            val p = s.profile
                            val proto = mapLabProto(row.proto)
                            val trans = mapLabTrans(row.trans)
                            bridge.updateProfile(p.copy(protocol = proto, extraTransport = trans))
                        }) { Text("Use") }
                    }
                }
            }
        }

        // Log tail.
        if (s.logTail.isNotEmpty()) {
            SectionCard("Core log") {
                s.logTail.takeLast(30).forEach {
                    Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

private fun echPresetOf(p: AetherProfile): String =
    if (p.ech.isBlank()) "off"
    else if (p.ech.trim() == "auto" && p.echDns.isBlank() && p.echDomain.isBlank()) "auto"
    else "custom"

private fun mapLabProto(p: String): String = if (p == "gool-classic") "gool_classic" else p
private fun mapLabTrans(t: String): String = if (t == "warp") "none" else t.replace('-', '_')

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun EndpointRow(label: String, value: String, onCopy: () -> Unit) {
    OutlinedButton(onClick = onCopy) { Text("$label  $value", fontFamily = FontFamily.Monospace) }
}

@Composable
private fun OptText(label: String, value: String, placeholder: String, multi: Boolean = false, onChange: (String) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            placeholder = { Text(placeholder) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = !multi,
            minLines = if (multi) 3 else 1,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { o ->
                FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(o) })
            }
        }
    }
}
