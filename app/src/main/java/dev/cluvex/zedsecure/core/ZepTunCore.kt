package dev.cluvex.zedsecure.core

import android.net.VpnService
import android.os.ParcelFileDescriptor
import dev.cluvex.zedsecure.core.AppLog as Log
import dev.zeptun.Zeptun

object ZepTunCore {
    private const val TAG = "ZepTunCore"

    @Volatile
    private var running = false

    private const val STOP_JOIN_MS = 1_500L

    val version: String
        get() = runCatching { Zeptun.nativeVersion() }.getOrDefault("")

    fun start(
        service: VpnService,
        tun: ParcelFileDescriptor,
        socksPort: Int,
        mtu: Int,
        ipv4: String,
        ipv6: String?,

        udpOverTcp: Boolean = false,
        socksUser: String? = null,
        socksPass: String? = null,

        pipeline: Boolean = false,
        logLevel: String = "warn",

        rwTimeout: String = DEFAULT_RW_TIMEOUT,
    ): Boolean {
        if (running) return false
        return try {
            val (tcpSeconds, udpSeconds) = parseRwTimeout(rwTimeout)
            val config = buildToml(
                fd = tun.fd,
                socksPort = socksPort,
                mtu = mtu,
                ipv4 = ipv4,
                ipv6 = ipv6,
                udpOverTcp = udpOverTcp,
                socksUser = socksUser,
                socksPass = socksPass,
                pipeline = pipeline,
                logLevel = logLevel,
                tcpIdleMs = tcpSeconds * 1000L,
                udpIdleMs = udpSeconds * 1000L,
            )
            val rc = Zeptun.nativeStart(service, tun.fd, config)
            if (rc != 0) {
                Log.e(TAG, "zeptun refused to start: rc=$rc")
                return false
            }
            running = true
            true
        } catch (e: Throwable) {
            Log.e(TAG, "start failed", e)
            false
        }
    }

    private const val STAT_RX_PACKETS = 0
    private const val STAT_TX_PACKETS = 2
    private const val STAT_TCP_OPENED = 15
    private const val STAT_TCP_CONNECT_FAILED = 19

    fun carryingTraffic(): Boolean {
        val rx = counter(STAT_RX_PACKETS)
        val tx = counter(STAT_TX_PACKETS)
        val opened = counter(STAT_TCP_OPENED)
        val failed = counter(STAT_TCP_CONNECT_FAILED)

        if (rx <= 0) return true
        if (tx > 0) return true
        return opened > 0 && failed < opened
    }

    fun stats(): String =
        "rx=${counter(STAT_RX_PACKETS)} tx=${counter(STAT_TX_PACKETS)} " +
            "tcp=${counter(STAT_TCP_OPENED)} failed=${counter(STAT_TCP_CONNECT_FAILED)}"

    private fun counter(index: Int): Long =
        runCatching { Zeptun.nativeCounter(index) }.getOrDefault(-1L)

    fun stop() {
        if (!running) return
        running = false

        val worker = Thread({ runCatching { Zeptun.nativeStop() } }, "zeptun-stop")
        worker.start()
        runCatching { worker.join(STOP_JOIN_MS) }
    }

    internal fun buildToml(

        fd: Int,
        socksPort: Int,
        mtu: Int,

        ipv4: String?,
        ipv6: String?,
        udpOverTcp: Boolean,
        socksUser: String?,
        socksPass: String?,
        pipeline: Boolean,
        logLevel: String,
        tcpIdleMs: Long,
        udpIdleMs: Long,
    ): String = buildString {
        appendLine("preset = \"mobile\"")
        appendLine("log_level = ${quote(logLevel)}")
        appendLine()
        appendLine("[tun]")
        appendLine("fd = $fd")
        appendLine("mtu = $mtu")

        val prefixes = buildList {
            ipv4?.takeIf { it.isNotBlank() }?.let { add("\"$it/30\"") }
            ipv6?.takeIf { it.isNotBlank() }?.let { add("\"$it/126\"") }
        }
        if (prefixes.isNotEmpty()) appendLine("address = [${prefixes.joinToString(", ")}]")
        appendLine()
        appendLine("[stack]")
        appendLine("tcp_idle_timeout_ms = $tcpIdleMs")
        appendLine("udp_idle_timeout_ms = $udpIdleMs")
        appendLine()
        appendLine("[handler]")
        appendLine("kind = \"socks5\"")
        appendLine()
        appendLine("[handler.socks5]")
        appendLine("server = \"127.0.0.1:$socksPort\"")
        socksUser?.takeIf { it.isNotBlank() }?.let { appendLine("username = ${quote(it)}") }
        socksPass?.takeIf { it.isNotBlank() }?.let { appendLine("password = ${quote(it)}") }
        appendLine("udp = true")
        appendLine("udp_mode = ${if (udpOverTcp) "\"tcp\"" else "\"udp\""}")
        appendLine("pipeline = $pipeline")
        appendLine()
        appendLine("[route]")

        appendLine("auto_route = false")
    }

    internal fun parseRwTimeout(value: String): Pair<Long, Long> {
        val parts = value.split(',')
        val tcp = parts.getOrNull(0)?.trim()?.toLongOrNull()?.takeIf { it > 0 } ?: 300L
        val udp = parts.getOrNull(1)?.trim()?.toLongOrNull()?.takeIf { it > 0 } ?: 60L
        return tcp to udp
    }

    private fun quote(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    const val DEFAULT_RW_TIMEOUT = "300,60"
}
