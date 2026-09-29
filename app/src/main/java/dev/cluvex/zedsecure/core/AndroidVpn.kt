package dev.cluvex.zedsecure.core

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

object AndroidVpn {
    fun start(
        context: Context,
        configJson: String,
        remark: String,
        socksPort: Int,
        kind: String = VpnManager.KIND_XRAY,

        proxyOnly: Boolean = false,
    ) {
        VpnManager.onStarting(remark)

        val viaFile = configJson.length > INLINE_CONFIG_LIMIT
        val handoff = if (viaFile) {
            runCatching {
                java.io.File(context.filesDir, "pending-config.json").apply { writeText(configJson) }
            }.getOrNull()
        } else {
            null
        }
        val intent = Intent(context, ZedVpnService::class.java).apply {
            putExtra(VpnManager.EXTRA_COMMAND, VpnManager.CMD_START)
            if (handoff != null) putExtra(VpnManager.EXTRA_CONFIG_PATH, handoff.absolutePath)
            else putExtra(VpnManager.EXTRA_CONFIG, configJson)
            putExtra(VpnManager.EXTRA_REMARK, remark)
            putExtra(VpnManager.EXTRA_SOCKS_PORT, socksPort)
            putExtra(VpnManager.EXTRA_KIND, kind)
            putExtra(VpnManager.EXTRA_PROXY_ONLY, proxyOnly)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    private const val INLINE_CONFIG_LIMIT = 48 * 1024

    fun stop(context: Context) {
        if (!VpnManager.onStopping()) return
        val intent = Intent(context, ZedVpnService::class.java).apply {
            putExtra(VpnManager.EXTRA_COMMAND, VpnManager.CMD_STOP)
        }
        context.startService(intent)
    }
}
