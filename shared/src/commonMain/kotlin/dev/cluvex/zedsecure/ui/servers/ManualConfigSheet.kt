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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
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
import dev.cluvex.zedsecure.domain.config.TransportConfig
import dev.cluvex.zedsecure.ui.components.PickerField
import dev.cluvex.zedsecure.ui.components.SectionTitle

private val NETWORKS = listOf("tcp", "ws", "grpc", "httpupgrade", "xhttp", "kcp")
private val SECURITIES = listOf("" to "None", "tls" to "TLS", "reality" to "REALITY")

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
    onDismiss: () -> Unit,
    onSave: (link: String) -> Unit,
) {
    var protocol by remember { mutableStateOf(initial?.protocol ?: Protocol.VLESS) }
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
    var allowInsecure by remember { mutableStateOf(initial?.tls?.allowInsecure ?: false) }
    var verifyCertName by remember { mutableStateOf(initial?.tls?.verifyPeerCertByName ?: "") }
    var mldsa65Verify by remember { mutableStateOf(initial?.tls?.mldsa65Verify ?: "") }
    var flow by remember { mutableStateOf(initial?.flow ?: "") }
    var bandwidthUp by remember { mutableStateOf(initial?.bandwidthUp ?: "") }
    var bandwidthDown by remember { mutableStateOf(initial?.bandwidthDown ?: "") }

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

        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
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
                options = Protocol.entries.map { it to it.label },
                selected = protocol,
                onSelect = { protocol = it },
            )

            Field(remark, { remark = it }, Res.string.manual_remark)
            Field(address, { address = it }, Res.string.manual_address)
            Field(port, { port = it.filter(Char::isDigit).take(5) }, Res.string.manual_port, number = true)

            when (protocol) {
                Protocol.VLESS -> {
                    Field(userId, { userId = it }, Res.string.manual_uuid)

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
                }
                Protocol.HYSTERIA -> {
                    Field(secretKey, { secretKey = it }, Res.string.manual_auth)
                    Field(obfsPassword, { obfsPassword = it }, Res.string.manual_obfs_password)
                    Field(portHopping, { portHopping = it }, Res.string.manual_port_hopping)
                    Field(pinnedCert, { pinnedCert = it }, Res.string.manual_pinned_cert)
                    Field(bandwidthUp, { bandwidthUp = it.filter(Char::isDigit).take(6) }, Res.string.manual_bandwidth_up, number = true)
                    Field(bandwidthDown, { bandwidthDown = it.filter(Char::isDigit).take(6) }, Res.string.manual_bandwidth_down, number = true)
                    Field(sni, { sni = it }, Res.string.manual_sni)
                    PickerField(
                        label = stringResource(Res.string.manual_alpn),
                        options = ALPN_OPTIONS,
                        selected = alpn,
                        onSelect = { alpn = it },
                    )
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
                if (network != "tcp" && network != "kcp") {
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

                SectionTitle(stringResource(Res.string.manual_security))
                PickerField(
                    label = stringResource(Res.string.manual_security),
                    options = SECURITIES,
                    selected = security,
                    onSelect = { security = it },
                )
            }
            if (protocol.supportsTransport && security.isNotEmpty()) {
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

                    Field(finalMask, { finalMask = it }, Res.string.manual_final_mask)

                    Field(pinnedCert, { pinnedCert = it }, Res.string.manual_pinned_cert)
                    Field(verifyCertName, { verifyCertName = it }, Res.string.manual_verify_cert_name)
                    LabeledSwitch(
                        label = stringResource(Res.string.manual_allow_insecure),
                        checked = allowInsecure,
                        onChange = { allowInsecure = it },
                    )

                    if (allowInsecure && sni.isBlank() && pinnedCert.isBlank()) {
                        Text(
                            stringResource(Res.string.manual_allow_insecure_needs_sni),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    Field(publicKey, { publicKey = it }, Res.string.manual_public_key)
                    Field(shortId, { shortId = it }, Res.string.manual_short_id)
                    Field(spiderX, { spiderX = it }, Res.string.manual_spider_x)

                    Field(mldsa65Verify, { mldsa65Verify = it }, Res.string.manual_mldsa65_verify)
                }
            }

            Button(
                onClick = {
                    val config = ServerConfig(
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
                        username = username.trim(),
                        secretKey = secretKey.trim(),
                        peerPublicKey = peerPublicKey.trim(),
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
                        pinnedCertSha256 = pinnedCert.ifBlank { null },
                        bandwidthUp = bandwidthUp.ifBlank { null },
                        bandwidthDown = bandwidthDown.ifBlank { null },
                        transport = TransportConfig(
                            network = network,

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
                        ),
                        tls = SecurityConfig(
                            security = security,
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
                    onSave(ShareLink.build(config))
                },
                enabled = address.isNotBlank() && when (protocol) {
                    Protocol.SOCKS, Protocol.HTTP -> true
                    Protocol.WIREGUARD, Protocol.AMNEZIAWG ->
                        secretKey.isNotBlank() && peerPublicKey.isNotBlank()
                    Protocol.HYSTERIA -> secretKey.isNotBlank()
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
