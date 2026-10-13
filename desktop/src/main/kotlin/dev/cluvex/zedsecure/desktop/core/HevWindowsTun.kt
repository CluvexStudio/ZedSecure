package dev.cluvex.zedsecure.desktop.core

import java.io.File
import java.nio.file.Files

class HevWindowsTun(
    private val hev: File,
    private val socksPort: Int,
    private val workDir: File,
    private val bypassIps: List<String> = emptyList(),
    private val udpOverTcp: Boolean = false,
    private val dnsServers: String = ZeptunTun.DEFAULT_DNS,
    private val ipv6: Boolean = false,
    private val routes: List<String> = ROUTES,
    private val logLevel: String = "warn",
) : DesktopTun {
    @Volatile private var state: File? = null
    @Volatile private var up = false

    internal fun config(): String = buildString {
        appendLine("tunnel:")
        appendLine("  name: $ADAPTER")
        appendLine("  mtu: 1500")
        appendLine("  ipv4: $ADDRESS")
        if (ipv6) appendLine("  ipv6: '$ADDRESS6'")
        appendLine("socks5:")
        appendLine("  address: 127.0.0.1")
        appendLine("  port: $socksPort")
        appendLine("  udp: '${if (udpOverTcp) "tcp" else "udp"}'")
        appendLine("misc:")
        appendLine("  log-level: $logLevel")
    }

    internal fun plan(): String = buildString {
        appendLine("address=$ADDRESS")
        appendLine("routes=${routes.filter { ':' !in it }.joinToString(",")}")
        appendLine("routes6=${if (ipv6) routes.filter { ':' in it }.joinToString(",") else ""}")
        appendLine("dns=${dnsAddresses(dnsServers).joinToString(",")}")
        appendLine("bypass=${bypassIps.map { it.trim() }.filter { IPV4.matches(it) }.distinct().joinToString(",")}")
    }

    override fun start(): Boolean {
        val dir = runCatching { Files.createTempDirectory(workDir.also { it.mkdirs() }.toPath(), "hev-").toFile() }
            .onFailure { println("[tun] could not create a state directory: ${it.message}") }
            .getOrNull() ?: return false
        state = dir
        val staged = runCatching {
            hev.copyTo(File(dir, "hev-socks5-tunnel.exe"), overwrite = true)
            HevBinary.WINDOWS_COMPANIONS.forEach { name ->
                File(hev.parentFile, name).takeIf { it.isFile }?.copyTo(File(dir, name), overwrite = true)
            }
            File(dir, "hev.yml").writeText(config())
            File(dir, "tun.properties").writeText(plan())
        }
        if (staged.isFailure) {
            println("[tun] could not stage hev: ${staged.exceptionOrNull()?.message}")
            discard()
            return false
        }
        if (!WindowsElevation.ensure(workDir)) {
            discard()
            return false
        }
        WindowsElevation.hevUp(dir).onFailure {
            println("[tun] ${it.message}")
            discard()
            return false
        }
        up = true
        return true
    }

    override fun stop() {
        if (up) WindowsElevation.hevDown()
        up = false
        discard()
    }

    private fun discard() {
        val dir = state ?: return
        state = null
        runCatching { File(dir, "hev.log").readLines().takeLast(20) }.getOrNull()
            ?.filter { it.isNotBlank() }
            ?.forEach { println("[hev] $it") }
        runCatching { dir.deleteRecursively() }
    }

    companion object {
        const val ADAPTER = "ZedSecure"
        const val ADDRESS = "172.19.0.1"
        const val ADDRESS6 = "fdfe:dcba:9876::1"
        val ROUTES = listOf("0.0.0.0/1", "128.0.0.0/1", "0.0.0.0/0", "::/1", "8000::/1", "::/0")
        private val IPV4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

        internal fun dnsAddresses(servers: String): List<String> =
            servers.split(',', ' ', ';', '\n').map { it.trim().substringBefore(':') }.filter { IPV4.matches(it) }.distinct().take(3)
                .ifEmpty { listOf(ZeptunTun.DEFAULT_DNS) }
    }
}
