package dev.cluvex.zedsecure.ui.servers

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.cluvex.zedsecure.domain.config.ConfigParser
import dev.cluvex.zedsecure.domain.config.ShareLink
import dev.cluvex.zedsecure.domain.config.SingBoxLinks
import dev.cluvex.zedsecure.domain.config.SingBoxTuning
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.*
import dev.cluvex.zedsecure.ui.components.PickerField
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

enum class SingBoxKind(val label: String) {
    TUIC("TUIC"),
    ANYTLS("AnyTLS"),
    HYSTERIA("Hysteria v1"),
    NAIVE("NaïveProxy"),
    SOCKS4("SOCKS4 / 4a"),
}

@Stable
class SingBoxDraft(link: String?) {
    var kind by mutableStateOf(SingBoxKind.TUIC)
    var name by mutableStateOf("")
    var server by mutableStateOf("")
    var port by mutableStateOf("443")
    var user by mutableStateOf("")
    var password by mutableStateOf("")
    var quic by mutableStateOf(false)
    var socks4a by mutableStateOf(false)
    val params = mutableStateMapOf<String, String>()

    init {
        if (link != null) load(link)
    }

    private fun load(link: String) {
        val scheme = link.substringBefore("://").lowercase()
        kind = when (scheme) {
            "anytls" -> SingBoxKind.ANYTLS
            "hysteria" -> SingBoxKind.HYSTERIA
            "naive+https", "naive+quic" -> SingBoxKind.NAIVE
            "socks4", "socks4a" -> SingBoxKind.SOCKS4
            else -> SingBoxKind.TUIC
        }
        quic = scheme == "naive+quic"
        socks4a = scheme == "socks4a"
        val parts = ConfigParser.parseLink(link.substringAfter("://"))
        name = parts.fragment
        server = parts.host
        port = parts.port?.toString() ?: if (kind == SingBoxKind.SOCKS4) "1080" else "443"
        val info = parts.userInfo
        when (kind) {
            SingBoxKind.ANYTLS -> password = info
            SingBoxKind.SOCKS4 -> user = info.substringBefore(':')
            else -> {
                user = info.substringBefore(':')
                password = info.substringAfter(':', "")
            }
        }
        params.clear()
        parts.query.forEach { (k, v) -> params[k] = v }
        if (kind == SingBoxKind.HYSTERIA) params.remove("auth")?.let { user = it }
    }

    fun select(newKind: SingBoxKind) {
        if (newKind == kind) return
        kind = newKind
        params.clear()
        if (newKind == SingBoxKind.SOCKS4 && port == "443") port = "1080"
        if (newKind != SingBoxKind.SOCKS4 && port == "1080") port = "443"
    }

    operator fun get(key: String): String = params[key].orEmpty()

    operator fun set(key: String, value: String) {
        if (value.isEmpty()) params.remove(key) else params[key] = value
    }

    val ready: Boolean
        get() = server.isNotBlank() && port.toIntOrNull() in 1..65535 && when (kind) {
            SingBoxKind.TUIC -> user.isNotBlank()
            SingBoxKind.ANYTLS -> password.isNotBlank()
            else -> true
        }

    fun toLink(): String {
        val scheme = when (kind) {
            SingBoxKind.TUIC -> "tuic"
            SingBoxKind.ANYTLS -> "anytls"
            SingBoxKind.HYSTERIA -> "hysteria"
            SingBoxKind.NAIVE -> if (quic) "naive+quic" else "naive+https"
            SingBoxKind.SOCKS4 -> if (socks4a) "socks4a" else "socks4"
        }
        val enc = ShareLink::enc
        val credentials = when (kind) {
            SingBoxKind.TUIC -> "${enc(user.trim())}:${enc(password)}@"
            SingBoxKind.ANYTLS -> "${enc(password)}@"
            SingBoxKind.HYSTERIA -> ""
            SingBoxKind.NAIVE -> when {
                user.isNotBlank() && password.isNotEmpty() -> "${enc(user.trim())}:${enc(password)}@"
                user.isNotBlank() -> "${enc(user.trim())}@"
                password.isNotEmpty() -> ":${enc(password)}@"
                else -> ""
            }
            SingBoxKind.SOCKS4 -> if (user.isBlank()) "" else "${enc(user.trim())}@"
        }
        val query = buildList {
            if (kind == SingBoxKind.HYSTERIA && user.isNotEmpty()) add("auth" to user)
            if (kind != SingBoxKind.SOCKS4) params.forEach { (k, v) -> if (v.isNotBlank()) add(k to v.trim()) }
        }.joinToString("&") { (k, v) -> "$k=${enc(v)}" }
        val host = server.trim().let { if (':' in it && !it.startsWith("[")) "[$it]" else it }
        val label = name.trim().ifEmpty { "$host:${port.trim()}" }
        return "$scheme://$credentials$host:${port.trim()}${if (query.isEmpty()) "" else "?$query"}#${enc(label)}"
    }

    fun valid(): Boolean = ready && runCatching { SingBoxLinks.parse(toLink()) }.isSuccess

    companion object {
        fun canEdit(link: String): Boolean = SingBoxLinks.handles(link)
    }
}

private val TUIC_CONGESTION = listOf("" to "—", "cubic" to "cubic", "new_reno" to "new_reno", "bbr" to "bbr")
private val TUIC_RELAY = listOf("" to "—", "native" to "native", "quic" to "quic")
private val NAIVE_CONGESTION = listOf("" to "—", "bbr" to "bbr", "bbr2" to "bbr2", "cubic" to "cubic", "reno" to "reno")

@Composable
fun SingBoxFields(draft: SingBoxDraft) {
    SbField(draft.name, { draft.name = it }, Res.string.manual_remark)
    SbField(draft.server, { draft.server = it }, Res.string.manual_address)
    SbField(draft.port, { draft.port = it.filter(Char::isDigit).take(5) }, Res.string.manual_port, number = true)
    when (draft.kind) {
        SingBoxKind.TUIC -> {
            SbField(draft.user, { draft.user = it }, Res.string.manual_uuid)
            SbField(draft.password, { draft.password = it }, Res.string.manual_password)
            PickerField(stringResource(Res.string.sb_congestion), TUIC_CONGESTION, draft["congestion_control"], { draft["congestion_control"] = it })
            val overStream = draft["udp_over_stream"].let { it == "1" || it.equals("true", ignoreCase = true) }
            LabeledToggle(stringResource(Res.string.sb_udp_over_stream), overStream) {
                draft["udp_over_stream"] = if (it) "1" else ""
                if (it) draft["udp_relay_mode"] = ""
            }
            if (!overStream) {
                PickerField(stringResource(Res.string.sb_udp_relay), TUIC_RELAY, draft["udp_relay_mode"], { draft["udp_relay_mode"] = it })
            }
            SbSwitch(Res.string.sb_zero_rtt, draft, "zero_rtt_handshake")
            SbField(draft["heartbeat"], { draft["heartbeat"] = it }, Res.string.sb_heartbeat)
            TlsFields(draft, alpnDefault = "h3", fingerprint = false)
        }
        SingBoxKind.ANYTLS -> {
            SbField(draft.password, { draft.password = it }, Res.string.manual_password)
            SbField(draft["idle_session_check_interval"], { draft["idle_session_check_interval"] = it }, Res.string.sb_idle_check)
            SbField(draft["idle_session_timeout"], { draft["idle_session_timeout"] = it }, Res.string.sb_idle_timeout)
            SbField(draft["min_idle_session"], { draft["min_idle_session"] = it.filter(Char::isDigit).take(4) }, Res.string.sb_min_idle, number = true)
            TlsFields(draft, alpnDefault = null, fingerprint = true)
        }
        SingBoxKind.HYSTERIA -> {
            SbField(draft.user, { draft.user = it }, Res.string.manual_auth)
            SbField(draft["upmbps"], { draft["upmbps"] = it.filter(Char::isDigit).take(6) }, Res.string.manual_bandwidth_up, number = true)
            SbField(draft["downmbps"], { draft["downmbps"] = it.filter(Char::isDigit).take(6) }, Res.string.manual_bandwidth_down, number = true)
            SbField(draft["obfsParam"], { draft["obfsParam"] = it }, Res.string.manual_obfs_password)
            SbField(draft["mport"], { draft["mport"] = it }, Res.string.manual_port_hopping)
            if (draft["mport"].isNotBlank()) SbField(draft["hop_interval"], { draft["hop_interval"] = it }, Res.string.manual_hop_interval)
            SbField(draft["recv_window_conn"], { draft["recv_window_conn"] = it.filter(Char::isDigit).take(12) }, Res.string.sb_recv_window_conn, number = true)
            SbField(draft["recv_window"], { draft["recv_window"] = it.filter(Char::isDigit).take(12) }, Res.string.sb_recv_window, number = true)
            SbSwitch(Res.string.sb_disable_mtu, draft, "disable_mtu_discovery")
            TlsFields(draft, alpnDefault = "hysteria", fingerprint = false, sniKey = "peer")
        }
        SingBoxKind.NAIVE -> {
            SbField(draft.user, { draft.user = it }, Res.string.manual_username)
            SbField(draft.password, { draft.password = it }, Res.string.manual_password)
            LabeledToggle(stringResource(Res.string.sb_quic), draft.quic) { draft.quic = it }
            if (draft.quic) {
                PickerField(stringResource(Res.string.sb_quic_congestion), NAIVE_CONGESTION, draft["quic_congestion_control"], { draft["quic_congestion_control"] = it })
            }
            SbField(draft["insecure-concurrency"], { draft["insecure-concurrency"] = it.filter(Char::isDigit).take(3) }, Res.string.sb_insecure_concurrency, number = true)
            SbField(
                draft["extra-headers"].replace("\r\n", "; "),
                { draft["extra-headers"] = it.split(';').map(String::trim).filter(String::isNotEmpty).joinToString("\r\n") },
                Res.string.manual_masque_headers,
            )
            SbField(draft["sni"], { draft["sni"] = it }, Res.string.manual_sni)
            SbField(draft["ech"], { draft["ech"] = it }, Res.string.manual_ech)
        }
        SingBoxKind.SOCKS4 -> {
            SbField(draft.user, { draft.user = it }, Res.string.manual_username)
            LabeledToggle(stringResource(Res.string.sb_socks4a), draft.socks4a) { draft.socks4a = it }
        }
    }
}

@Composable
private fun TlsFields(draft: SingBoxDraft, alpnDefault: String?, fingerprint: Boolean, sniKey: String = "sni") {
    SbField(draft[sniKey], { draft[sniKey] = it }, Res.string.manual_sni)
    OutlinedTextField(
        value = draft["alpn"],
        onValueChange = { draft["alpn"] = it },
        label = { Text(stringResource(Res.string.manual_alpn)) },
        placeholder = alpnDefault?.let { { Text(it) } },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    if (fingerprint) {
        PickerField(
            stringResource(Res.string.manual_fingerprint),
            listOf("" to "—") + SingBoxTuning.FINGERPRINTS.map { it to it },
            draft["fp"],
            { draft["fp"] = it },
        )
    }
    if (sniKey == "sni") SbField(draft["ech"], { draft["ech"] = it }, Res.string.manual_ech)
    SbSwitch(Res.string.sb_disable_sni, draft, "disable_sni")
    SbSwitch(Res.string.manual_allow_insecure, draft, "insecure")
}

@Composable
private fun SbSwitch(label: StringResource, draft: SingBoxDraft, key: String) {
    val on = draft[key].let { it == "1" || it.equals("true", ignoreCase = true) }
    LabeledToggle(stringResource(label), on) { draft[key] = if (it) "1" else "" }
}

@Composable
private fun LabeledToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SbField(value: String, onValueChange: (String) -> Unit, label: StringResource, number: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        modifier = Modifier.fillMaxWidth(),
    )
}
