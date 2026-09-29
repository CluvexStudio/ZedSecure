package dev.amirzr.flutter_v2ray_client.v2ray.core

import android.content.Context
import android.os.ParcelFileDescriptor
import dev.cluvex.zedsecure.core.AppLog as Log
import hev.htproxy.TProxyService
import java.io.File

object HevTunCore {
    private const val TAG = "HevTunCore"

    @Volatile
    private var running = false

    fun start(
        context: Context,
        tun: ParcelFileDescriptor,
        socksPort: Int,
        mtu: Int,
        ipv4: String,
        ipv6: String?,
        preferIpv6: Boolean,

        udpOverTcp: Boolean = false,
        socksUser: String? = null,
        socksPass: String? = null,

        pipeline: Boolean = false,

        logLevel: String = DEFAULT_LOG_LEVEL,

        rwTimeout: String = DEFAULT_RW_TIMEOUT,
    ): Boolean {
        if (running) return false
        return try {
            val (tcpTimeout, udpTimeout) = parseRwTimeout(rwTimeout)
            val yaml = buildYaml(
                socksPort, mtu, ipv4, ipv6, preferIpv6, udpOverTcp, socksUser, socksPass, pipeline,
                logLevel = logLevel,
                tcpTimeoutSeconds = tcpTimeout,
                udpTimeoutSeconds = udpTimeout,
            )
            val file = File(context.filesDir, "hev-tunnel.yaml").apply { writeText(yaml) }
            TProxyService.TProxyStartService(file.absolutePath, tun.fd)
            running = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "start failed", e)
            false
        }
    }

    private val STOP_JOIN_MS = 1_500L

    private val STOP_JOIN_WORKER_MS = 10_000L

    fun stop() {
        if (!running) return
        running = false

        val worker = Thread(
            { runCatching { TProxyService.TProxyStopService() }.onFailure { Log.e(TAG, "stop failed", it) } },
            "hev-stop",
        )
        worker.start()
        val onMain = android.os.Looper.myLooper() == android.os.Looper.getMainLooper()
        worker.join(if (onMain) STOP_JOIN_MS else STOP_JOIN_WORKER_MS)
        if (worker.isAlive) Log.w(TAG, "hev still stopping after ${if (onMain) STOP_JOIN_MS else STOP_JOIN_WORKER_MS} ms")
    }

    internal fun buildYaml(
        socksPort: Int,
        mtu: Int,
        ipv4: String,
        ipv6: String?,
        preferIpv6: Boolean,
        udpOverTcp: Boolean,
        socksUser: String?,
        socksPass: String?,
        pipeline: Boolean,
        logLevel: String = DEFAULT_LOG_LEVEL,
        tcpTimeoutSeconds: Int = 300,
        udpTimeoutSeconds: Int = 60,
    ): String = buildString {
        appendLine("tunnel:")
        appendLine("  mtu: $mtu")
        appendLine("  ipv4: $ipv4")
        if (preferIpv6 && ipv6 != null) appendLine("  ipv6: '$ipv6'")
        appendLine("socks5:")
        appendLine("  port: $socksPort")
        appendLine("  address: 127.0.0.1")
        appendLine("  udp: '${if (udpOverTcp) "tcp" else "udp"}'")
        if (!socksUser.isNullOrEmpty()) {
            appendLine("  username: '$socksUser'")
            appendLine("  password: '${socksPass.orEmpty()}'")
        }
        if (pipeline) appendLine("  pipeline: true")
        appendLine("misc:")
        appendLine("  tcp-buffer-size: 131072")
        appendLine("  udp-recv-buffer-size: 262144")
        appendLine("  tcp-read-write-timeout: ${tcpTimeoutSeconds * 1000}")
        appendLine("  udp-read-write-timeout: ${udpTimeoutSeconds * 1000}")
        appendLine("  log-level: ${logLevel.takeIf { it in LOG_LEVELS } ?: DEFAULT_LOG_LEVEL}")
    }

    const val DEFAULT_LOG_LEVEL = "warn"
    const val DEFAULT_RW_TIMEOUT = "300,60"

    private val LOG_LEVELS = setOf("debug", "info", "warn", "error")

    internal fun parseRwTimeout(value: String): Pair<Int, Int> {
        val parts = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val tcp = parts.getOrNull(0)?.toIntOrNull()?.takeIf { it in 1..86_400 } ?: 300
        val udp = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..86_400 } ?: 60
        return tcp to udp
    }
}
