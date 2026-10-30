package dev.cluvex.zedsecure.desktop.core

import java.io.File

class HevMacTun(
    private val hev: File,
    private val socksPort: Int,
    private val workDir: File,
    private val bypassIps: List<String> = emptyList(),
    private val udpOverTcp: Boolean = false,
    private val dnsServers: String = ZeptunTun.DEFAULT_DNS,
    private val logLevel: String = "warn",
) : DesktopTun {
    internal fun config(): String = buildString {
        appendLine("tunnel:")
        appendLine("  name: zedsecure")
        appendLine("  mtu: $MTU")
        appendLine("  ipv4: ${ZeptunTun.TUN_V4}")
        appendLine("  ipv6: '${ZeptunTun.TUN_V6}'")
        appendLine("socks5:")
        appendLine("  address: 127.0.0.1")
        appendLine("  port: $socksPort")
        appendLine("  udp: '${if (udpOverTcp) "tcp" else "udp"}'")
        appendLine("misc:")
        appendLine("  log-level: $logLevel")
    }

    internal fun dns(): String = ZeptunTun.dnsAddress(dnsServers)?.takeIf { IPV4.matches(it) } ?: ZeptunTun.DEFAULT_DNS

    internal fun bypass(): List<String> = bypassIps.map { it.trim() }.filter { IPV4.matches(it) }.distinct()

    override fun start(): Boolean {
        val up = MacTunHelper.upHev(workDir, hev, config(), dns(), bypass())
        if (!up) MacTunHelper.lastLog().forEach { println("[hev] $it") }
        return up
    }

    override fun stop() = MacTunHelper.down()

    private companion object {
        const val MTU = 1500
        val IPV4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
    }
}
