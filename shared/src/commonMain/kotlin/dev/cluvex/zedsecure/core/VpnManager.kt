package dev.cluvex.zedsecure.core

import dev.cluvex.zedsecure.domain.model.ConnectionState
import kotlin.concurrent.Volatile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object VpnManager {
    data class Status(
        val state: ConnectionState = ConnectionState.Idle,
        val durationSeconds: Int = 0,
        val downloadBps: Long = 0,
        val uploadBps: Long = 0,
        val totalDownload: Long = 0,
        val totalUpload: Long = 0,
        val serverName: String? = null,
        val error: String? = null,

        val sessionId: Int = 0,
    )

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    @Volatile
    var activeSocksPort: Int? = null

    @Volatile
    var deviceVpnProbe: () -> Boolean = { false }

    fun deviceVpnActive(): Boolean = runCatching { deviceVpnProbe() }.getOrDefault(false)

    private val _activeKind = MutableStateFlow<String?>(null)
    val activeKind: StateFlow<String?> = _activeKind.asStateFlow()

    fun setActiveKind(kind: String?) {
        _activeKind.value = kind
    }

    const val EXTRA_COMMAND = "zed.command"
    const val EXTRA_CONFIG = "zed.config"

    const val EXTRA_CONFIG_PATH = "zed.config.path"
    const val EXTRA_REMARK = "zed.remark"
    const val EXTRA_SOCKS_PORT = "zed.socksPort"
    const val EXTRA_KIND = "zed.kind"

    const val EXTRA_PROXY_ONLY = "zed.proxyOnly"
    const val CMD_START = "start"
    const val CMD_STOP = "stop"

    const val CMD_START_ACTIVE = "start_active"

    const val KIND_XRAY = "xray"
    const val KIND_PSIPHON = "psiphon"

    const val KIND_DNS_TUNNEL = "dns_tunnel"

    const val KIND_MASTERDNS = "master_dns"

    const val KIND_TOR = "tor"

    const val KIND_SSH = "ssh"

    const val KIND_SNISPOOF = "sni_spoof"

    const val KIND_OPENCONNECT = "openconnect"

    const val KIND_IKEV2 = "ikev2"

    const val KIND_CROSS_CHAIN = "cross_chain"

    const val KIND_SINGBOX = "sing_box"

    fun onStarting(remark: String) {
        activeSocksPort = null
        _status.value = Status(
            state = ConnectionState.Connecting,
            serverName = remark,
            sessionId = _status.value.sessionId + 1,
        )
    }

    fun onStopping(): Boolean {
        if (_status.value.state == ConnectionState.Idle) return false
        _status.value = _status.value.copy(state = ConnectionState.Disconnecting)
        return true
    }

    fun onConnected(remark: String?) {
        _status.value = _status.value.copy(
            state = ConnectionState.Connected,
            serverName = remark ?: _status.value.serverName,
            error = null,
        )
    }

    fun onMetrics(durationSeconds: Int, downBps: Long, upBps: Long, totalDown: Long, totalUp: Long) {
        val s = _status.value
        if (s.state != ConnectionState.Connected) return
        _status.value = s.copy(
            durationSeconds = durationSeconds,
            downloadBps = downBps,
            uploadBps = upBps,
            totalDownload = totalDown,
            totalUpload = totalUp,
        )
    }

    fun onError(reason: String) {
        _status.value = Status(
            state = ConnectionState.Error,
            error = reason,
            sessionId = _status.value.sessionId,
        )
    }

    fun onDisconnected() {
        activeSocksPort = null
        _activeKind.value = null
        val previous = _status.value
        _status.value = Status(
            state = ConnectionState.Idle,
            serverName = previous.serverName,
            sessionId = previous.sessionId,
        )
    }
}
