package dev.cluvex.zedsecure.desktop.core

import java.io.File
import java.nio.file.Files

class ZeptunTun(
    private val zeptun: File,
    private val socksPort: Int,
    private val workDir: File,
    private val bypassIps: List<String> = emptyList(),
    private val udpOverTcp: Boolean = false,
    private val dnsServers: String = DEFAULT_DNS,
    private val includeOnly: List<String> = emptyList(),
    private val allowApps: List<File> = emptyList(),
    private val os: Os = Os.current,
) : DesktopTun {
    @Volatile private var state: File? = null
    @Volatile private var windowsUp = false

    internal fun toml(strictFirewall: Boolean = os == Os.WINDOWS && ZeptunBinary.hasStrictFirewall(zeptun)): String = buildString {
        appendLine("preset = \"desktop\"")
        appendLine("log_level = \"warn\"")
        appendLine()
        appendLine("[tun]")
        if (os == Os.WINDOWS) {
            appendLine("name = \"$ADAPTER\"")
            appendLine("guid = \"$ADAPTER_GUID\"")
        }
        appendLine("address = [\"$TUN_V4/30\", \"$TUN_V6/126\"]")
        appendLine()
        appendLine("[handler]")
        appendLine("kind = \"socks5\"")
        appendLine()
        appendLine("[handler.socks5]")
        appendLine("server = \"127.0.0.1:$socksPort\"")
        appendLine("udp_mode = \"${if (udpOverTcp) "tcp" else "udp"}\"")
        appendLine()
        appendLine("[handler.direct]")
        appendLine("bind_interface = \"${if (os == Os.MACOS) "lo0" else "loopback"}\"")
        appendLine()
        appendLine("[route]")
        appendLine("auto_route = true")
        if (strictFirewall) {
            appendLine("strict = true")
            val apps = allowApps.map { it.absolutePath }.distinct()
            if (apps.isNotEmpty()) appendLine("allow_app = ${tomlList(apps)}")
        }
        if (includeOnly.isNotEmpty()) appendLine("include = ${tomlList(includeOnly)}")
        val excluded = bypassIps.mapNotNull(::hostPrefix).distinct()
        if (excluded.isNotEmpty()) appendLine("exclude = ${tomlList(excluded)}")
        appendLine()
        appendLine("[dns]")
        appendLine("hijack = true")
        appendLine("upstream = \"${dnsUpstream(dnsServers)}\"")
    }

    override fun start(): Boolean = when (os) {
        Os.WINDOWS -> startWindows()
        Os.MACOS -> startMac()
        else -> false
    }

    private fun stage(): Pair<File, File>? {
        val dir = runCatching { Files.createTempDirectory(workDir.also { it.mkdirs() }.toPath(), "tun-").toFile() }
            .onFailure { println("[tun] could not create a state directory: ${it.message}") }
            .getOrNull() ?: return null
        val engine = File(dir, zeptun.name)
        val copied = runCatching {
            zeptun.copyTo(engine, overwrite = true)
            engine.setExecutable(true, true)
            if (os == Os.WINDOWS) {
                ZeptunBinary.WINDOWS_COMPANIONS.forEach { name ->
                    File(zeptun.parentFile, name).takeIf { it.isFile }?.copyTo(File(dir, name), overwrite = true)
                }
            }
        }
        if (copied.isFailure) {
            println("[tun] could not stage zeptun: ${copied.exceptionOrNull()?.message}")
            dir.deleteRecursively()
            return null
        }
        state = dir
        return dir to engine
    }

    private fun startWindows(): Boolean {
        val (dir, _) = stage() ?: return false
        File(dir, "zeptun.toml").writeText(toml())
        if (!WindowsElevation.ensure(workDir)) {
            discard()
            return false
        }
        WindowsElevation.tunUp(dir).onFailure {
            println("[tun] ${it.message}")
            discard()
            return false
        }
        windowsUp = true
        return true
    }

    private fun startMac(): Boolean {
        val up = MacTunHelper.up(workDir, zeptun, toml())
        if (!up) MacTunHelper.lastLog().forEach { println("[zeptun] $it") }
        return up
    }

    override fun stop() {
        if (os == Os.MACOS) {
            MacTunHelper.down()
            return
        }
        if (state == null) return
        if (windowsUp) WindowsElevation.tunDown()
        windowsUp = false
        discard()
    }

    private fun discard() {
        val dir = state ?: return
        state = null
        runCatching { File(dir, "zeptun.log").readLines().takeLast(LOG_TAIL) }.getOrNull()
            ?.filter { it.isNotBlank() }
            ?.forEach { println("[zeptun] $it") }
        runCatching { dir.deleteRecursively() }
    }

    companion object {
        const val ADAPTER = "ZedSecure"
        const val TUN_V4 = "172.19.0.1"
        const val TUN_V6 = "fdfe:dcba:9876::1"
        const val DEFAULT_DNS = "1.1.1.1"
        const val ADAPTER_GUID = "7E2B5D41-0C9A-4F3E-8B6D-2A91C4E7F053"
        const val MAC_TUN_DNS = "172.19.0.2"
        const val MAC_DNS_RESTORE = "/Library/Application Support/ZedSecure/dns-restore"
        private const val LOG_TAIL = 40

        private val IPV4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
        private val IPV4_PORT = Regex("""^\d{1,3}(\.\d{1,3}){3}:\d{1,5}$""")
        private val IPV6_PORT = Regex("""^\[[0-9A-Fa-f:.]+]:\d{1,5}$""")

        private fun isIpv6(value: String): Boolean =
            value.count { it == ':' } >= 2 && value.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' || it == '.' }

        internal fun hostPrefix(ip: String): String? {
            val value = ip.trim().removePrefix("[").removeSuffix("]")
            return when {
                IPV4.matches(value) -> "$value/32"
                isIpv6(value) -> "$value/128"
                else -> null
            }
        }

        fun dnsAddress(servers: String): String? =
            dnsUpstream(servers).let { if (it.startsWith("[")) it.substringAfter('[').substringBefore(']') else it.substringBeforeLast(':') }

        internal fun dnsUpstream(servers: String): String =
            servers.split(',', ' ', ';', '\n').map { it.trim() }.firstNotNullOfOrNull(::upstreamOf) ?: "$DEFAULT_DNS:53"

        private fun upstreamOf(value: String): String? {
            val bare = value.removePrefix("[").removeSuffix("]")
            return when {
                IPV4.matches(value) -> "$value:53"
                IPV4_PORT.matches(value) || IPV6_PORT.matches(value) -> value
                isIpv6(bare) -> "[$bare]:53"
                else -> null
            }
        }

        private fun tomlList(values: List<String>): String =
            values.joinToString(", ", "[", "]") { "\"" + it.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" }
    }
}
