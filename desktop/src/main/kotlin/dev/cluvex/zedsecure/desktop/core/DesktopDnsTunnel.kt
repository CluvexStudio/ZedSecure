package dev.cluvex.zedsecure.desktop.core

import dev.cluvex.zedsecure.core.LogBus
import dev.cluvex.zedsecure.domain.config.DnsTunnelProfile
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

class DesktopDnsTunnel(
    private val profile: DnsTunnelProfile,
    private val workDir: File,
    private val listenPort: Int = dev.cluvex.zedsecure.domain.config.LocalPorts.DNS_TUNNEL,
    private val listenHost: String = "127.0.0.1",
) {
    private var process: Process? = null
    @Volatile private var boundPort: Int = 0

    val isRunning: Boolean get() = process?.isAlive == true

    fun start(): Int {
        if (profile.domain.isBlank() || profile.publicKey.isBlank()) {
            LogBus.append("E/DesktopDns domain and public key are required"); return -1
        }
        val name = "zeddns"
        val bin = extract(name) ?: run {
            LogBus.append("E/DesktopDns bundled $name missing for ${Os.current} — build it into resources/bin"); return -1
        }
        val port = firstFreePort(listenPort)
        if (port < 0) { LogBus.append("E/DesktopDns no free port near $listenPort"); return -1 }
        val listenAddr = "$listenHost:$port"
        val args = clientArgs(bin, listenAddr)
        return try {
            val proc = ProcessBuilder(args).directory(workDir).redirectErrorStream(true).start()
            process = proc
            Thread {
                runCatching { proc.inputStream.bufferedReader().forEachLine { LogBus.append("I/${name}: $it") } }
            }.apply { isDaemon = true }.start()
            if (!waitPortReady(port, proc, timeoutMs = 12_000)) {
                LogBus.append("E/DesktopDns $name did not open $listenAddr in time"); stop(); return -1
            }
            boundPort = port
            LogBus.append("I/DesktopDns ${profile.engine} on $listenAddr (resolver=${profile.dnsAddress()}, domain=${profile.domain})")
            port
        } catch (e: Exception) {
            LogBus.append("E/DesktopDns ${profile.engine} failed to start: ${e.message}"); stop(); -1
        }
    }

    internal fun clientArgs(bin: File, listenAddr: String): List<String> = buildList {
        add(bin.absolutePath)
        add("-dns-addr"); add(profile.dnsAddress())
        add("-domain"); add(profile.domain)
        add("-pubkey"); add(profile.publicKey)
        add("-listen"); add(listenAddr)
        add("-resolver-mode"); add(profile.resolverMode)
        add("-rr-spread"); add(profile.rrSpreadCount.toString())
        if (!profile.isVaydns || profile.dnsttCompat) add("-dnstt-compat")
        if (profile.socksUser.isNotEmpty()) {
            add("-socks-user"); add(profile.socksUser); add("-socks-pass"); add(profile.socksPass)
        }
        if (profile.isVaydns) {
            if (profile.recordType != "txt") { add("-record-type"); add(profile.recordType) }
            if (profile.maxQnameLen != 101) { add("-max-qname-len"); add(profile.maxQnameLen.toString()) }
            if (profile.rps > 0) { add("-rps"); add(profile.rps.toString()) }
            if (profile.idleTimeout > 0) { add("-idle-timeout"); add(profile.idleTimeout.toString()) }
            if (profile.keepalive > 0) { add("-keepalive"); add(profile.keepalive.toString()) }
            if (profile.udpTimeout > 0) { add("-udp-timeout"); add(profile.udpTimeout.toString()) }
            if (profile.maxNumLabels > 0) { add("-max-num-labels"); add(profile.maxNumLabels.toString()) }
            if (profile.clientIdSize > 0) { add("-clientid-size"); add(profile.clientIdSize.toString()) }
        } else {
            if (profile.authoritative) add("-authoritative")
            if (profile.dnsPayloadSize > 0) { add("-max-payload"); add(profile.dnsPayloadSize.toString()) }
        }
    }

    fun stop() {
        runCatching { process?.destroy() }

        runCatching { if (process?.waitFor(2, java.util.concurrent.TimeUnit.SECONDS) == false) process?.destroyForcibly() }
        process = null
        boundPort = 0
    }

    private fun extract(base: String): File? {
        val (sub, name) = when (Os.current) {
            Os.LINUX -> "linux" to base
            Os.MACOS -> "macos" to base
            Os.WINDOWS -> "windows" to "$base.exe"
            else -> return null
        }
        val stream = javaClass.getResourceAsStream("/bin/$sub/$name") ?: return null
        val out = File(workDir, name)
        stream.use { input -> out.outputStream().use { input.copyTo(it) } }
        out.setExecutable(true)
        return out
    }

    private fun waitPortReady(port: Int, proc: Process, timeoutMs: Int): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (!proc.isAlive) return false
            try { Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 200); return true } }
            catch (e: Exception) { try { Thread.sleep(50) } catch (ie: InterruptedException) { return false } }
        }
        return false
    }

    private fun firstFreePort(preferred: Int): Int {
        for (p in preferred..(preferred + 10)) if (!inUse(p)) return p
        return -1
    }

    private fun inUse(port: Int): Boolean = try {
        ServerSocket().use { it.reuseAddress = true; it.bind(InetSocketAddress("127.0.0.1", port)); false }
    } catch (e: Exception) { true }
}
