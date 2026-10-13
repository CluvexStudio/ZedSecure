package dev.cluvex.zedsecure.desktop.core

import dev.cluvex.zedsecure.core.LogBus
import dev.cluvex.zedsecure.domain.config.LocalPorts
import dev.cluvex.zedsecure.domain.config.MasterDnsProfile
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit

class DesktopMasterDns(
    private val profile: MasterDnsProfile,
    private val workDir: File,
    private val firstPort: Int = LocalPorts.MASTER_DNS,
    private val lastPort: Int = LocalPorts.MASTER_DNS_MAX,
) {
    @Volatile private var process: Process? = null
    @Volatile var lastError: String? = null
        private set
    @Volatile private var fatal = false

    val isRunning: Boolean get() = process?.isAlive == true

    fun resolverHosts(): List<String> = MasterDnsProfile.splitResolvers(profile.resolvers).mapNotNull { entry ->
        val host = if (entry.startsWith("[")) entry.substringAfter('[').substringBefore(']') else entry.substringBefore(':')
        host.takeIf { MasterDnsProfile.isNumericResolver(entry) && it.isNotBlank() }
    }.distinct()

    fun start(readyTimeoutMs: Long = READY_TIMEOUT_MS): Int {
        if (profile.domains.isBlank()) return fail("a tunnel domain is required")
        val all = MasterDnsProfile.splitResolvers(profile.resolvers)
        val invalid = MasterDnsProfile.invalidResolvers(profile.resolvers).toSet()
        if (all.none { it !in invalid }) return fail("no usable resolvers; they must be IP addresses")
        if (invalid.isNotEmpty()) LogBus.append("W/MasterDns ignoring resolvers that are not IP addresses: ${invalid.joinToString()}")
        val bin = BundledBinary.extract(workDir, "masterdns", "masterdns.exe")
            ?: return fail("MasterDNS is not bundled with this build")
        val port = (firstPort..lastPort).firstOrNull { !inUse(it) } ?: return fail("no free local port near $firstPort")
        return try {
            val dir = File(workDir, "masterdns-client").apply { mkdirs() }
            val config = File(dir, "client_config.toml").apply { writeText(profile.copy(listenPort = port).toToml(LISTEN_HOST)) }
            val resolvers = File(dir, "client_resolvers.txt").apply { writeText(profile.resolversText()) }
            val proc = ProcessBuilder(bin.absolutePath, "-config", config.absolutePath, "-resolvers", resolvers.absolutePath)
                .directory(dir)
                .redirectErrorStream(true)
                .start()
            process = proc
            Thread {
                runCatching {
                    proc.inputStream.bufferedReader().forEachLine { raw ->
                        val line = raw.trim()
                        if (line.isEmpty()) return@forEachLine
                        LogBus.append("I/MasterDns $line")
                        if (ERROR_WORDS.any { line.contains(it, ignoreCase = true) }) lastError = line.take(200)
                        if (FATAL_WORDS.any { line.contains(it, ignoreCase = true) }) fatal = true
                    }
                }
            }.apply { isDaemon = true; name = "masterdns-output" }.start()
            if (!awaitReady(proc, port, readyTimeoutMs)) {
                val reason = lastError ?: if (proc.isAlive) "no session within ${readyTimeoutMs / 1000}s" else "the client stopped"
                stop()
                return fail(reason)
            }
            LogBus.append("I/MasterDns SOCKS ready on $LISTEN_HOST:$port")
            port
        } catch (e: Exception) {
            stop()
            fail(e.message ?: e.toString())
        }
    }

    fun stop() {
        val running = process ?: return
        process = null
        runCatching {
            running.destroy()
            if (!running.waitFor(3, TimeUnit.SECONDS)) {
                running.destroyForcibly()
                running.waitFor(2, TimeUnit.SECONDS)
            }
        }
    }

    private fun fail(reason: String): Int {
        lastError = reason
        LogBus.append("E/MasterDns $reason")
        return -1
    }

    private fun awaitReady(proc: Process, port: Int, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (!proc.isAlive || process !== proc || fatal) return false
            val open = runCatching {
                Socket().use { it.connect(InetSocketAddress(LISTEN_HOST, port), 300) }
                true
            }.getOrDefault(false)
            if (open) return true
            try {
                Thread.sleep(250)
            } catch (e: InterruptedException) {
                return false
            }
        }
        return false
    }

    private fun inUse(port: Int): Boolean = try {
        ServerSocket().use { it.reuseAddress = true; it.bind(InetSocketAddress(LISTEN_HOST, port)); false }
    } catch (e: Exception) {
        true
    }

    companion object {
        const val LISTEN_HOST = "127.0.0.1"
        private const val READY_TIMEOUT_MS = 120_000L
        private val ERROR_WORDS = listOf("error", "failed", "fatal", "refused", "no usable")
        private val FATAL_WORDS = listOf("No valid connections", "MTU tests failed")
    }
}
