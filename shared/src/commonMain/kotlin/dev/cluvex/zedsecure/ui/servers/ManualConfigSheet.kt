@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.cluvex.zedsecure.ui.servers

import org.jetbrains.compose.resources.StringResource

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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.*
import dev.cluvex.zedsecure.domain.config.AwgConfig
import dev.cluvex.zedsecure.domain.config.Protocol
import dev.cluvex.zedsecure.domain.config.SecurityConfig
import dev.cluvex.zedsecure.domain.config.ServerConfig
import dev.cluvex.zedsecure.domain.config.ShareLink
import dev.cluvex.zedsecure.domain.config.SingBoxConvert
import dev.cluvex.zedsecure.domain.config.TransportConfig
import dev.cluvex.zedsecure.ui.components.PickerField
import dev.cluvex.zedsecure.ui.components.SectionTitle
import dev.cluvex.zedsecure.ui.platform.appVerticalScroll

private val NETWORKS = listOf("tcp", "ws", "grpc", "httpupgrade", "xhttp", "kcp", "xdrive")

private val MASQUE_ALPNS = listOf("h3" to "HTTP/3", "h2" to "HTTP/2")
private val SECURITIES = listOf("" to "None", "tls" to "TLS", "reality" to "REALITY")

private val PROXY_SECURITIES = listOf("" to "None", "tls" to "TLS")

private val CORES = listOf(false to "Xray", true to "sing-box")

private val FLOWS = listOf(
    "" to "None",
    "xtls-rprx-vision" to "xtls-rprx-vision",
    "xtls-rprx-vision-udp443" to "xtls-rprx-vision-udp443",
)

private val VMESS_SECURITIES =
    listOf("auto", "aes-128-gcm", "chacha20-poly1305", "none", "zero")

private val SS_METHODS = listOf(
    "aes-256-gcm", "aes-128-gcm", "chacha20-poly1305", "chacha20-ietf-poly1305",
    "xchacha20-poly1305", "xchacha20-ietf-poly1305",
    "2022-blake3-aes-128-gcm", "2022-blake3-aes-256-gcm", "2022-blake3-chacha20-poly1305",
)

private val UTLS_FINGERPRINTS = listOf(
    "" to "None", "chrome" to "chrome", "firefox" to "firefox", "safari" to "safari",
    "ios" to "ios", "android" to "android", "edge" to "edge", "360" to "360", "qq" to "qq",
    "random" to "random", "randomized" to "randomized",

    "unsafe" to "unsafe",
)

private val TCP_HEADER_TYPES = listOf("none", "http")

private val KCP_HEADER_TYPES =
    listOf("none", "srtp", "utp", "wechat-video", "dtls", "wireguard", "dns")

private val GRPC_MODES = listOf("gun", "multi")

private val XHTTP_MODES = listOf("auto", "packet-up", "stream-up", "stream-one")

private val ALPN_OPTIONS = listOf(
    "" to "None", "h3" to "h3", "h2" to "h2", "http/1.1" to "http/1.1",
    "h3,h2,http/1.1" to "h3,h2,http/1.1", "h3,h2" to "h3,h2", "h2,http/1.1" to "h2,http/1.1",
)

@Composable
fun ManualConfigSheet(
    initial: ServerConfig? = null,
    initialSingBoxLink: String? = null,
    onEditJson: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: (link: String) -> Unit,
) {
    var protocol by remember { mutableStateOf(initial?.protocol ?: Protocol.VLESS) }
    val singBoxDraft = remember { SingBoxDraft(initialSingBoxLink) }
    var singBoxOnly by remember { mutableStateOf(initialSingBoxLink != null) }
    var remark by remember { mutableStateOf(initial?.remark ?: "") }
    var address by remember { mutableStateOf(initial?.address ?: "") }
    var port by remember { mutableStateOf(initial?.port?.toString() ?: "443") }
    var userId by remember { mutableStateOf(initial?.userId ?: "") }
    var ssMethod by remember { mutableStateOf(initial?.shadowsocksMethod ?: "aes-256-gcm") }
    var vmessSecurity by remember { mutableStateOf(initial?.vmessSecurity ?: "auto") }
    var alterId by remember { mutableStateOf((initial?.alterId ?: 0).toString()) }

    var encryption by remember { mutableStateOf(initial?.encryption ?: "none") }

    var network by remember { mutableStateOf(initial?.transport?.network ?: "tcp") }
    var host by remember { mutableStateOf(initial?.transport?.host ?: "") }
    var path by remember { mutableStateOf(initial?.transport?.path ?: "") }
    var serviceName by remember { mutableStateOf(initial?.transport?.serviceName ?: "") }

    var username by remember { mutableStateOf(initial?.username ?: "") }
    var secretKey by remember { mutableStateOf(initial?.secretKey ?: "") }
    var peerPublicKey by remember { mutableStateOf(initial?.peerPublicKey ?: "") }

    var headerType by remember { mutableStateOf(initial?.transport?.headerType ?: "none") }
    var streamMode by remember { mutableStateOf(initial?.transport?.mode ?: "") }
    var seed by remember { mutableStateOf(initial?.transport?.seed ?: "") }
    var authority by remember { mutableStateOf(initial?.transport?.authority ?: "") }

    var xhttpExtra by remember { mutableStateOf(initial?.transport?.xhttpExtra ?: "") }
    var kcpMtu by remember { mutableStateOf(initial?.transport?.kcpMtu?.toString() ?: "") }
    var kcpTti by remember { mutableStateOf(initial?.transport?.kcpTti?.toString() ?: "") }

    var obfsPassword by remember { mutableStateOf(initial?.obfsPassword ?: "") }
    var portHopping by remember { mutableStateOf(initial?.portHopping ?: "") }
    var pinnedCert by remember { mutableStateOf(initial?.pinnedCertSha256 ?: "") }
    var reserved by remember { mutableStateOf(initial?.reserved ?: "") }
    var wgMtu by remember { mutableStateOf(initial?.wireguardMtu?.toString() ?: "") }

    var wgPsk by remember { mutableStateOf(initial?.preSharedKey ?: "") }
    var wgKeepalive by remember { mutableStateOf(initial?.wireguardKeepalive?.toString() ?: "") }
    var wgAllowedIps by remember { mutableStateOf(initial?.allowedIps?.joinToString(", ") ?: "") }

    var wgDns by remember { mutableStateOf(initial?.dnsServers?.joinToString(", ") ?: "") }

    var awg by remember { mutableStateOf(initial?.awg ?: AwgConfig()) }
    var awgAdvanced by remember { mutableStateOf(initial?.awg?.let { c -> AwgConfig.KEYS.any { it !in AwgConfig.CLASSIC_KEYS && c[it].isNotBlank() } } ?: false) }
    var localAddress by remember {
        mutableStateOf(initial?.localAddresses?.joinToString(",") ?: "172.16.0.2/32")
    }

    var security by remember { mutableStateOf(initial?.tls?.security ?: "") }
    var sni by remember { mutableStateOf(initial?.tls?.sni ?: "") }
    var alpn by remember { mutableStateOf(initial?.tls?.alpn ?: "") }
    var fingerprint by remember { mutableStateOf(initial?.tls?.fingerprint ?: "chrome") }
    var publicKey by remember { mutableStateOf(initial?.tls?.publicKey ?: "") }
    var shortId by remember { mutableStateOf(initial?.tls?.shortId ?: "") }
    var spiderX by remember { mutableStateOf(initial?.tls?.spiderX ?: "") }
    var echConfigList by remember { mutableStateOf(initial?.tls?.echConfigList ?: "") }
    var cipherSuites by remember { mutableStateOf(initial?.tls?.cipherSuites ?: "") }

    var finalMask by remember { mutableStateOf(initial?.transport?.finalMask ?: "") }
    var headers by remember { mutableStateOf(initial?.transport?.headers ?: "") }
    var xdriveSettings by remember { mutableStateOf(initial?.transport?.xdriveSettings ?: "") }
    var hopInterval by remember { mutableStateOf(initial?.hopInterval ?: "") }
    var warp by remember { mutableStateOf(initial?.isWarp ?: false) }
    var allowInsecure by remember { mutableStateOf(initial?.tls?.allowInsecure ?: false) }
    var verifyCertName by remember { mutableStateOf(initial?.tls?.verifyPeerCertByName ?: "") }
    var mldsa65Verify by remember { mutableStateOf(initial?.tls?.mldsa65Verify ?: "") }
    var flow by remember { mutableStateOf(initial?.flow ?: "") }
    var bandwidthUp by remember { mutableStateOf(initial?.bandwidthUp ?: "") }
    var bandwidthDown by remember { mutableStateOf(initial?.bandwidthDown ?: "") }
    var runOnSingBox by remember { mutableStateOf(initial?.runOnSingBox ?: false) }

    val proxyProtocol = protocol == Protocol.SOCKS || protocol == Protocol.HTTP
    val tlsCapable = protocol.supportsTransport || proxyProtocol
    val singBoxChosen = runOnSingBox && protocol.sharedWithSingBox

    fun buildConfig(): ServerConfig {
        val masque = protocol == Protocol.MASQUE
        return ServerConfig(
            protocol = protocol,
            remark = remark.ifBlank { address },
            address = address.trim(),
            port = port.toIntOrNull() ?: 443,
            userId = userId.trim(),
            alterId = alterId.toIntOrNull(),
            encryption = encryption.ifBlank { "none" },
            vmessSecurity = vmessSecurity.ifBlank { "auto" },
            flow = flow.ifBlank { null },
            shadowsocksMethod = ssMethod.ifBlank { null },
            username = if (masque && warp) "" else username.trim(),
            secretKey = if (masque && !warp) "" else secretKey.trim(),
            peerPublicKey = if (masque && !warp) "" else peerPublicKey.trim(),
            localAddresses = localAddress.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() },
            preSharedKey = wgPsk.trim().ifBlank { null },
            allowedIps = wgAllowedIps.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() },
            dnsServers = wgDns.split(",").map { it.trim() }.filter { it.isNotEmpty() },
            reserved = reserved.ifBlank { null },
            wireguardMtu = wgMtu.toIntOrNull(),
            wireguardKeepalive = wgKeepalive.toIntOrNull()?.takeIf { it > 0 },
            awg = if (protocol == Protocol.AMNEZIAWG) awg else AwgConfig(),
            obfsPassword = obfsPassword.ifBlank { null },
            portHopping = portHopping.ifBlank { null },
            hopInterval = hopInterval.takeIf { portHopping.isNotBlank() }?.ifBlank { null },
            pinnedCertSha256 = pinnedCert.ifBlank { null },
            bandwidthUp = bandwidthUp.ifBlank { null },
            bandwidthDown = bandwidthDown.ifBlank { null },
            runOnSingBox = singBoxChosen,
            transport = TransportConfig(
                network = if (masque) "masque" else network,

                headerType = headerType.takeIf { it.isNotBlank() && it != "none" },
                host = host.ifBlank { null },
                path = path.ifBlank { null },
                seed = seed.ifBlank { null },
                mode = streamMode.ifBlank { null },
                serviceName = serviceName.ifBlank { null },
                authority = authority.ifBlank { null },
                xhttpExtra = xhttpExtra.ifBlank { null },
                kcpMtu = kcpMtu.toIntOrNull(),
                finalMask = finalMask.ifBlank { null },
                kcpTti = kcpTti.toIntOrNull(),
                headers = headers.takeIf { masque || protocol == Protocol.HTTP }?.ifBlank { null },
                xdriveSettings = xdriveSettings.takeIf { !masque && network == "xdrive" }?.ifBlank { null },
            ),
            tls = SecurityConfig(
                security = when {
                    masque || protocol == Protocol.HYSTERIA -> "tls"
                    proxyProtocol && security == "reality" -> ""
                    else -> security
                },
                sni = sni.ifBlank { null },
                alpn = alpn.ifBlank { null },
                fingerprint = fingerprint.ifBlank { null },
                publicKey = publicKey.ifBlank { null },
                shortId = shortId.ifBlank { null },
                spiderX = spiderX.ifBlank { null },
                echConfigList = echConfigList.ifBlank { null },
                cipherSuites = cipherSuites.ifBlank { null },
                verifyPeerCertByName = verifyCertName.ifBlank { null },
                mldsa65Verify = mldsa65Verify.ifBlank { null },
                allowInsecure = allowInsecure,
            ),
        )
    }

    androidx.compose.runtime.LaunchedEffect(protocol) {
        if (initial == null) {
            port = when (protocol) {
                Protocol.SOCKS -> "1080"
                Protocol.HTTP -> "80"

                Protocol.WIREGUARD, Protocol.AMNEZIAWG -> "51820"
                else -> "443"
            }
            if (protocol == Protocol.TROJAN && security.isBlank()) security = "tls"
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,

        sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded)),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .appVerticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(Res.string.servers_add_manual),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            PickerField(
                label = stringResource(Res.string.manual_protocol),
                options = Protocol.entries.map { "x:" + it.name to it.label } +
                    SingBoxKind.entries.map { "s:" + it.name to it.label + " · sing-box" },
                selected = if (singBoxOnly) "s:" + singBoxDraft.kind.name else "x:" + protocol.name,
                onSelect = { key ->
                    if (key.startsWith("s:")) {
                        singBoxOnly = true
                        singBoxDraft.select(SingBoxKind.valueOf(key.removePrefix("s:")))
                    } else {
                        singBoxOnly = false
                        protocol = Protocol.valueOf(key.removePrefix("x:"))
                    }
                },
            )

            if (singBoxOnly) {
                SingBoxFields(singBoxDraft)
                if (onEditJson != null) {
                    androidx.compose.material3.TextButton(onClick = onEditJson) {
                        Text(stringResource(Res.string.sb_edit_json))
                    }
                }
                Button(
                    onClick = { onSave(singBoxDraft.toLink()) },
                    enabled = singBoxDraft.valid(),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    Text(stringResource(Res.string.action_save), fontWeight = FontWeight.SemiBold)
                }
                return@Column
            }

            if (protocol.sharedWithSingBox) {
                PickerField(
                    label = stringResource(Res.string.manual_core),
                    options = CORES,
                    selected = runOnSingBox,
                    onSelect = { runOnSingBox = it },
                )
            }

            Field(remark, { remark = it }, Res.string.manual_remark)
            Field(address, { address = it }, Res.string.manual_address)
            Field(port, { port = it.filter(Char::isDigit).take(5) }, Res.string.manual_port, number = true)

            when (protocol) {
                Protocol.VLESS -> {
                    Field(userId, { userId = it }, Res.string.manual_uuid)
                    Field(encryption, { encryption = it }, Res.string.manual_vless_encryption)

                    PickerField(
                        label = stringResource(Res.string.manual_flow),
                        options = FLOWS,
                        selected = flow,
                        onSelect = { flow = it },
                    )
                }
                Protocol.VMESS -> {
                    Field(userId, { userId = it }, Res.string.manual_uuid)
                    Field(alterId, { alterId = it.filter(Char::isDigit).take(4) }, Res.string.manual_alter_id, number = true)
                    PickerField(
                        label = stringResource(Res.string.manual_encryption),
                        options = VMESS_SECURITIES.map { it to it },
                        selected = vmessSecurity,
                        onSelect = { vmessSecurity = it },
                    )
                }
                Protocol.TROJAN -> Field(userId, { userId = it }, Res.string.manual_password)
                Protocol.SHADOWSOCKS -> {
                    Field(userId, { userId = it }, Res.string.manual_password)
                    PickerField(
                        label = stringResource(Res.string.manual_ss_method),
                        options = SS_METHODS.map { it to it },
                        selected = ssMethod,
                        onSelect = { ssMethod = it },
                    )
                }
                Protocol.SOCKS, Protocol.HTTP -> {
                    Field(username, { username = it }, Res.string.manual_username)
                    Field(userId, { userId = it }, Res.string.manual_password)
                    if (protocol == Protocol.HTTP) Field(headers, { headers = it }, Res.string.manual_masque_headers)
                }
                Protocol.HYSTERIA -> {
                    Field(secretKey, { secretKey = it }, Res.string.manual_auth)
                    Field(obfsPassword, { obfsPassword = it }, Res.string.manual_obfs_password)
                    Field(portHopping, { portHopping = it }, Res.string.manual_port_hopping)
                    if (portHopping.isNotBlank()) {
                        Field(hopInterval, { hopInterval = it.filter { c -> c.isDigit() || c == '-' }.take(9) }, Res.string.manual_hop_interval)
                    }
                    Field(bandwidthUp, { bandwidthUp = it.filter(Char::isDigit).take(6) }, Res.string.manual_bandwidth_up, number = true)
                    Field(bandwidthDown, { bandwidthDown = it.filter(Char::isDigit).take(6) }, Res.string.manual_bandwidth_down, number = true)
                    Field(sni, { sni = it }, Res.string.manual_sni)
                    PickerField(
                        label = stringResource(Res.string.manual_alpn),
                        options = ALPN_OPTIONS,
                        selected = alpn,
                        onSelect = { alpn = it },
                    )
                    Field(echConfigList, { echConfigList = it }, Res.string.manual_ech)
                    Field(pinnedCert, { pinnedCert = it }, Res.string.manual_pinned_cert)
                    Field(verifyCertName, { verifyCertName = it }, Res.string.manual_verify_cert_name)
                    InsecureSwitch(allowInsecure, { allowInsecure = it }, sni.isBlank() && pinnedCert.isBlank() && !singBoxChosen)
                }
                Protocol.MASQUE -> {
                    LabeledSwitch(
                        label = stringResource(Res.string.manual_masque_warp),
                        checked = warp,
                        onChange = { warp = it },
                    )
                    if (warp) {
                        Text(
                            stringResource(Res.string.manual_masque_warp_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Field(secretKey, { secretKey = it }, Res.string.manual_masque_warp_private_key)
                        Field(peerPublicKey, { peerPublicKey = it }, Res.string.manual_masque_warp_public_key)
                        Field(localAddress, { localAddress = it }, Res.string.manual_local_address)
                    } else {
                        Field(username, { username = it }, Res.string.manual_username)
                        Field(userId, { userId = it }, Res.string.manual_password)
                    }
                    PickerField(
                        label = stringResource(Res.string.manual_masque_http),
                        options = MASQUE_ALPNS,
                        selected = if (alpn.trim().lowercase() == "h2") "h2" else "h3",
                        onSelect = { alpn = it },
                    )
                    Field(sni, { sni = it }, Res.string.manual_sni)
                    if (alpn.trim().lowercase() == "h2") {
                        PickerField(
                            label = stringResource(Res.string.manual_fingerprint),
                            options = UTLS_FINGERPRINTS,
                            selected = fingerprint,
                            onSelect = { fingerprint = it },
                        )
                    }
                    Field(path, { path = it }, Res.string.manual_masque_path)
                    Field(host, { host = it }, Res.string.manual_masque_authority)
                    Field(headers, { headers = it }, Res.string.manual_masque_headers)
                    Field(wgDns, { wgDns = it }, Res.string.manual_masque_dns)
                    Field(xhttpExtra, { xhttpExtra = it }, Res.string.manual_masque_extra)
                    Field(echConfigList, { echConfigList = it }, Res.string.manual_ech)
                    Field(pinnedCert, { pinnedCert = it }, Res.string.manual_pinned_cert)
                    Field(verifyCertName, { verifyCertName = it }, Res.string.manual_verify_cert_name)
                    InsecureSwitch(allowInsecure, { allowInsecure = it }, sni.isBlank() && pinnedCert.isBlank())
                }
                Protocol.WIREGUARD, Protocol.AMNEZIAWG -> {
                    Field(secretKey, { secretKey = it }, Res.string.manual_private_key)
                    Field(peerPublicKey, { peerPublicKey = it }, Res.string.manual_peer_public_key)
                    Field(wgPsk, { wgPsk = it }, Res.string.manual_preshared_key)
                    Field(localAddress, { localAddress = it }, Res.string.manual_local_address)
                    Field(wgAllowedIps, { wgAllowedIps = it }, Res.string.manual_allowed_ips)
                    Field(wgDns, { wgDns = it }, Res.string.manual_wg_dns)
                    Field(reserved, { reserved = it }, Res.string.manual_reserved)
                    Field(wgMtu, { wgMtu = it.filter(Char::isDigit).take(4) }, Res.string.manual_mtu, number = true)
                    Field(
                        wgKeepalive,
                        { wgKeepalive = it.filter(Char::isDigit).take(4) },
                        Res.string.manual_keepalive,
                        number = true,
                    )
                    if (protocol == Protocol.AMNEZIAWG) {
                        SectionTitle(stringResource(Res.string.manual_awg_section))
                        Text(
                            stringResource(Res.string.manual_awg_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        AwgConfig.CLASSIC_KEYS.forEach { key ->
                            AwgField(key, awg[key]) { awg = awg.with(key, it) }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                stringResource(Res.string.manual_awg_advanced),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(checked = awgAdvanced, onCheckedChange = { awgAdvanced = it })
                        }
                        if (awgAdvanced) {
                            AwgConfig.KEYS.filterNot { it in AwgConfig.CLASSIC_KEYS }.forEach { key ->
                                AwgField(key, awg[key]) { awg = awg.with(key, it) }
                            }
                        }
                    }
                }
            }

            if (protocol.supportsTransport) {
                SectionTitle(stringResource(Res.string.manual_transport))
                PickerField(
                    label = stringResource(Res.string.manual_transport),
                    options = NETWORKS.map { it to it.uppercase() },
                    selected = network,
                    onSelect = { network = it },
                )
                if (network != "tcp" && network != "kcp" && network != "xdrive") {
                    Field(host, { host = it }, Res.string.manual_host)
                }

                when (network) {
                    "tcp" -> {
                        PickerField(
                            label = stringResource(Res.string.manual_header_type),
                            options = TCP_HEADER_TYPES.map { it to it },
                            selected = headerType,
                            onSelect = { headerType = it },
                        )

                        if (headerType == "http") {
                            Field(host, { host = it }, Res.string.manual_host)
                            Field(path, { path = it }, Res.string.manual_path)
                        }
                    }

                    "kcp", "mkcp" -> {
                        PickerField(
                            label = stringResource(Res.string.manual_header_type),
                            options = KCP_HEADER_TYPES.map { it to it },
                            selected = headerType,
                            onSelect = { headerType = it },
                        )

                        if (headerType == "dns") Field(host, { host = it }, Res.string.manual_host)
                        Field(seed, { seed = it }, Res.string.manual_seed)
                        Field(kcpMtu, { kcpMtu = it }, Res.string.manual_kcp_mtu)
                        Field(kcpTti, { kcpTti = it }, Res.string.manual_kcp_tti)
                    }
                    "ws", "httpupgrade" -> Field(path, { path = it }, Res.string.manual_path)
                    "xhttp" -> {
                        Field(path, { path = it }, Res.string.manual_path)
                        PickerField(
                            label = stringResource(Res.string.manual_mode),
                            options = XHTTP_MODES.map { it to it },
                            selected = streamMode.ifBlank { "auto" },
                            onSelect = { streamMode = it },
                        )
                        Field(xhttpExtra, { xhttpExtra = it }, Res.string.manual_xhttp_extra)
                    }
                    "xdrive" -> {
                        Text(
                            stringResource(Res.string.manual_xdrive_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = xdriveSettings,
                            onValueChange = { xdriveSettings = it },
                            label = { Text(stringResource(Res.string.manual_xdrive_settings)) },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    "grpc" -> {
                        Field(serviceName, { serviceName = it }, Res.string.manual_service_name)
                        Field(authority, { authority = it }, Res.string.manual_authority)
                        PickerField(
                            label = stringResource(Res.string.manual_mode),
                            options = GRPC_MODES.map { it to it },
                            selected = streamMode.ifBlank { "gun" },
                            onSelect = { streamMode = it },
                        )
                    }
                }

            }
            if (tlsCapable) {
                SectionTitle(stringResource(Res.string.manual_security))
                PickerField(
                    label = stringResource(Res.string.manual_security),
                    options = if (proxyProtocol) PROXY_SECURITIES else SECURITIES,
                    selected = if (proxyProtocol && security == "reality") "" else security,
                    onSelect = { security = it },
                )
            }
            if (tlsCapable && security.isNotEmpty() && !(proxyProtocol && security == "reality")) {
                Field(sni, { sni = it }, Res.string.manual_sni)

                PickerField(
                    label = stringResource(Res.string.manual_fingerprint),
                    options = UTLS_FINGERPRINTS,
                    selected = fingerprint,
                    onSelect = { fingerprint = it },
                )
                if (security == "tls") {
                    PickerField(
                        label = stringResource(Res.string.manual_alpn),
                        options = ALPN_OPTIONS,
                        selected = alpn,
                        onSelect = { alpn = it },
                    )
                    Field(echConfigList, { echConfigList = it }, Res.string.manual_ech)

                    Field(cipherSuites, { cipherSuites = it }, Res.string.manual_cipher_suites)

                    Field(pinnedCert, { pinnedCert = it }, Res.string.manual_pinned_cert)
                    Field(verifyCertName, { verifyCertName = it }, Res.string.manual_verify_cert_name)
                    InsecureSwitch(allowInsecure, { allowInsecure = it }, sni.isBlank() && pinnedCert.isBlank() && !singBoxChosen)
                } else {
                    Field(publicKey, { publicKey = it }, Res.string.manual_public_key)
                    Field(shortId, { shortId = it }, Res.string.manual_short_id)
                    Field(spiderX, { spiderX = it }, Res.string.manual_spider_x)

                    Field(mldsa65Verify, { mldsa65Verify = it }, Res.string.manual_mldsa65_verify)
                }
            }

            OutlinedTextField(
                value = finalMask,
                onValueChange = { finalMask = it },
                label = { Text(stringResource(Res.string.manual_final_mask)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            val problems = if (singBoxChosen) SingBoxConvert.problems(buildConfig()) else emptyList()
            if (problems.isNotEmpty()) {
                Text(
                    stringResource(Res.string.singbox_blocked_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                )
                problems.forEach { problem ->
                    Text(
                        "• " + problemText(problem),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Button(
                onClick = {
                    val config = buildConfig()
                    onSave(ShareLink.build(config))
                },
                enabled = problems.isEmpty() && address.isNotBlank() && when (protocol) {
                    Protocol.SOCKS, Protocol.HTTP -> true
                    Protocol.WIREGUARD, Protocol.AMNEZIAWG ->
                        secretKey.isNotBlank() && peerPublicKey.isNotBlank()
                    Protocol.HYSTERIA -> secretKey.isNotBlank()
                    Protocol.MASQUE -> !warp || secretKey.isNotBlank() && localAddress.isNotBlank()
                    else -> userId.isNotBlank()
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
private fun InsecureSwitch(checked: Boolean, onChange: (Boolean) -> Unit, warnNoName: Boolean) {
    LabeledSwitch(
        label = stringResource(Res.string.manual_allow_insecure),
        checked = checked,
        onChange = onChange,
    )
    if (checked && warnNoName) {
        Text(
            stringResource(Res.string.manual_allow_insecure_needs_sni),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun problemText(problem: SingBoxConvert.Problem): String = when (problem.blocker) {
    SingBoxConvert.Blocker.Protocol -> stringResource(Res.string.singbox_blocker_protocol, problem.detail)
    SingBoxConvert.Blocker.Transport -> stringResource(Res.string.singbox_blocker_transport, problem.detail)
    SingBoxConvert.Blocker.TcpHeader -> stringResource(Res.string.singbox_blocker_tcp_header)
    SingBoxConvert.Blocker.VlessEncryption -> stringResource(Res.string.singbox_blocker_vless_encryption)
    SingBoxConvert.Blocker.Flow -> stringResource(Res.string.singbox_blocker_flow, problem.detail)
    SingBoxConvert.Blocker.FinalMask -> stringResource(Res.string.singbox_blocker_finalmask)
    SingBoxConvert.Blocker.PinnedCert -> stringResource(Res.string.singbox_blocker_pinned_cert)
    SingBoxConvert.Blocker.VerifyName -> stringResource(Res.string.singbox_blocker_verify_name)
    SingBoxConvert.Blocker.Reality -> stringResource(Res.string.singbox_blocker_reality)
    SingBoxConvert.Blocker.Fingerprint -> stringResource(Res.string.singbox_blocker_fingerprint, problem.detail)
    SingBoxConvert.Blocker.Cipher -> stringResource(Res.string.singbox_blocker_cipher, problem.detail)
    SingBoxConvert.Blocker.SocksTls -> stringResource(Res.string.singbox_blocker_socks_tls)
    SingBoxConvert.Blocker.GrpcMode -> stringResource(Res.string.singbox_blocker_grpc_mode)
    SingBoxConvert.Blocker.GrpcAuthority -> stringResource(Res.string.singbox_blocker_grpc_authority)
    SingBoxConvert.Blocker.ShadowsocksTransport -> stringResource(Res.string.singbox_blocker_ss_transport)
    SingBoxConvert.Blocker.Bandwidth -> stringResource(Res.string.singbox_blocker_bandwidth, problem.detail)
    SingBoxConvert.Blocker.Address -> stringResource(Res.string.singbox_blocker_address, problem.detail)
    SingBoxConvert.Blocker.Reserved -> stringResource(Res.string.singbox_blocker_reserved, problem.detail)
}

@Composable
private fun LabeledSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun AwgField(key: String, value: String, onValueChange: (String) -> Unit) {
    val name = AwgConfig.CONF_NAME[key] ?: key
    val desc = AwgConfig.DESCRIPTION[key]
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(if (desc == null) name else "$name — $desc") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: StringResource,
    number: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(labelRes)) },
        singleLine = true,
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number)
        else KeyboardOptions.Default,
        modifier = Modifier.fillMaxWidth(),
    )
}
