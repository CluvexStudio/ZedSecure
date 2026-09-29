package dev.cluvex.zedsecure.core

import dev.cluvex.zedsecure.core.AppLog as Log
import dev.cluvex.zedsecure.domain.config.MasterDnsProfile
import dev.cluvex.zedsecure.platform.InAppLog
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

class MasterDnsController(
    private val settings: MasterDnsProfile,
    private val filesDir: File,
    private val listenPort: Int,

    private val maxListenPort: Int = listenPort + 9,
    private val listenHost: String = "127.0.0.1",

    private val pool: List<String> = emptyList(),

    private val poolFullVerification: Boolean = false,
) {
    @Volatile private var started = false
    @Volatile private var boundPort: Int = 0
    @Volatile private var ready = false
    private var logTail: Thread? = null
    @Volatile private var tailing = false
    @Volatile private var lastProbeAt = 0L
    @Volatile private var lastProbeOk = true

    val isRunning: Boolean
        get() {
            if (!started) return false
            val goAlive = runCatching { masterdns.Masterdns.isRunning() }.getOrDefault(false)
            if (!goAlive) return false
            if (!ready) return true

            val now = System.currentTimeMillis()
            if (now - lastProbeAt < PROBE_CACHE_MS) return lastProbeOk
            lastProbeAt = now
            lastProbeOk = portAccepts(listenHost, boundPort, timeoutMs = 800)
            return lastProbeOk
        }

    val isReady: Boolean get() = ready

    sealed interface Failure {
        data class NoUsableResolver(val tried: Int) : Failure

        data class SessionRefused(val usable: Int, val reason: String) : Failure

        data class Stalled(val checked: Int, val total: Int, val usable: Int) : Failure

        data class Other(val reason: String) : Failure
    }

    @Volatile var failure: Failure? = null
        private set

    @Volatile var stopped = false
        private set

    @Volatile private var scanChecked = 0
    @Volatile private var scanTotal = 0
    @Volatile private var scanUsable = 0
    @Volatile private var noUsableResolver = false
    @Volatile private var sessionFailures = 0
    @Volatile private var sessionError: String? = null
    @Volatile private var lastErrorLine: String? = null
    @Volatile private var lastActivityAt = 0L

    fun start(): Int {
        failure = null
        if (!settings.isValid) {
            fail("domains, key and resolvers are required")
            return -1
        }
        val effective = prescreen(resolveResolverHosts(applyPool(settings)))
        return launch(effective)
    }

    @Synchronized
    private fun launch(effective: MasterDnsProfile): Int {
        if (stopped) return -1
        val invalid = MasterDnsProfile.invalidResolvers(effective.resolvers)
        if (MasterDnsProfile.splitResolvers(effective.resolvers).isEmpty() ||
            invalid.size == MasterDnsProfile.splitResolvers(effective.resolvers).size
        ) {
            fail("no usable resolvers (must be IP addresses): ${invalid.joinToString()}")
            return -1
        }
        if (invalid.isNotEmpty()) Log.w(TAG, "ignoring non-IP resolvers: ${invalid.joinToString()}")

        val port = firstFreePort(listenPort)
        if (port < 0) {
            fail("no free local port near $listenPort")
            return -1
        }
        return try {
            val dir = File(filesDir, "masterdns").apply { mkdirs() }
            val configFile = File(dir, "client_config.toml")
            val resolversFile = File(dir, "client_resolvers.txt")
            val logFile = File(dir, "client.log")
            configFile.writeText(effective.copy(listenPort = port).toToml(listenHost))
            resolversFile.writeText(effective.resolversText())
            runCatching { logFile.delete() }

            runCatching { masterdns.Masterdns.stop() }
            resetProgress()
            startLogTail(logFile)

            masterdns.Masterdns.start(configFile.absolutePath, resolversFile.absolutePath, logFile.absolutePath)
            started = true
            boundPort = port
            Log.i(TAG, "MasterDNS bootstrapped; SOCKS $port will open after MTU tests + session setup")
            port
        } catch (e: Throwable) {
            failure = Failure.Other(e.message ?: e.toString())
            Log.e(TAG, "MasterDNS start failed", e)
            runCatching { masterdns.Masterdns.stop() }
            stopLogTail()
            started = false
            -1
        }
    }

    fun awaitReady(idleTimeoutMs: Long, maxTimeoutMs: Long): Boolean {
        val begin = System.currentTimeMillis()
        lastActivityAt = begin
        while (true) {
            if (!started) return false
            if (portAccepts(listenHost, boundPort, timeoutMs = 500)) {
                ready = true
                lastProbeAt = System.currentTimeMillis()
                lastProbeOk = true
                Log.i(TAG, "MasterDNS SOCKS ready on $boundPort")
                return true
            }
            if (!runCatching { masterdns.Masterdns.isRunning() }.getOrDefault(false)) {
                failure = failure ?: Failure.Other(lastErrorLine ?: "client stopped during bootstrap")
                return false
            }

            if (noUsableResolver) {
                failure = Failure.NoUsableResolver(maxOf(scanTotal, scanChecked))
                return false
            }
            if (sessionFailures >= SESSION_FAILURES_TO_GIVE_UP) {
                failure = Failure.SessionRefused(scanUsable, sessionError.orEmpty())
                return false
            }
            val now = System.currentTimeMillis()
            if (now - lastActivityAt > idleTimeoutMs || now - begin > maxTimeoutMs) {
                failure = if (sessionFailures > 0) {
                    Failure.SessionRefused(scanUsable, sessionError.orEmpty())
                } else {
                    Failure.Stalled(scanChecked, scanTotal, scanUsable)
                }
                return false
            }
            try { Thread.sleep(500) } catch (_: InterruptedException) { return false }
        }
    }

    @Synchronized
    fun stop() {
        stopped = true
        stopLogTail()

        runCatching { masterdns.Masterdns.stop() }
        started = false
        ready = false
        lastProbeAt = 0L
        lastProbeOk = true

        if (boundPort > 0) {
            runCatching { Socket().use { it.connect(InetSocketAddress(listenHost, boundPort), 500) } }
            boundPort = 0
        }
    }

    private fun applyPool(p: MasterDnsProfile): MasterDnsProfile {
        if (pool.isEmpty()) return p
        val fastest = DnsProbe.fastest(
            pool,
            count = 3,

            tunnelDomain = p.domains.split(',', '\n').map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty(),
            fullVerification = poolFullVerification,
        )
        if (fastest.isEmpty()) return p
        Log.i(TAG, "DNS pool picked: ${fastest.joinToString()}")
        return p.copy(resolvers = fastest.joinToString(","))
    }

    private fun prescreen(p: MasterDnsProfile): MasterDnsProfile {
        if (pool.isNotEmpty()) return p
        val all = MasterDnsProfile.splitResolvers(p.resolvers)

        if (all.size <= PRESCREEN_KEEP || all.any { '/' in it }) return p
        val domain = p.domains.split(',', '\n').map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
        mirrorNote("checking ${all.size} resolvers before the MTU scan…")
        val fastest = DnsProbe.fastest(all, count = PRESCREEN_KEEP, tunnelDomain = domain)
        if (fastest.isEmpty()) {
            mirrorNote("no resolver answered the quick check; the client will test all ${all.size}")
            return p
        }
        mirrorNote("kept the ${fastest.size} fastest of ${all.size} resolvers for the MTU scan")
        return p.copy(resolvers = fastest.joinToString(","))
    }

    private fun mirrorNote(message: String) {
        InAppLog.write('I', LOG_TAG, message, null, android.os.Process.myPid())
    }

    private fun resolveResolverHosts(p: MasterDnsProfile): MasterDnsProfile {
        if (p.resolvers.isBlank()) return p
        val out = MasterDnsProfile.splitResolvers(p.resolvers).map { entry ->
            if (MasterDnsProfile.isNumericResolver(entry)) return@map entry
            val ipv6 = entry.startsWith("[")
            val sep = if (ipv6) entry.indexOf("]:") else entry.lastIndexOf(':')
            val host = if (sep >= 0) (if (ipv6) entry.substring(1, sep) else entry.substring(0, sep))
            else entry.trim('[', ']')
            val portSuffix = if (sep >= 0) entry.substring(if (ipv6) sep + 1 else sep) else ""
            runCatching { InetAddress.getByName(host).hostAddress }.getOrNull()
                ?.let { "$it$portSuffix" } ?: entry
        }
        return p.copy(resolvers = out.joinToString(","))
    }

    private fun startLogTail(logFile: File) {
        stopLogTail()
        tailing = true
        val pid = android.os.Process.myPid()
        logTail = Thread {
            var pos = 0L
            while (tailing) {
                try {
                    if (logFile.exists() && logFile.length() < pos) pos = 0L
                    if (logFile.exists() && logFile.length() > pos) {
                        logFile.inputStream().use { ins ->
                            ins.skip(pos)
                            ins.bufferedReader().forEachLine { line ->
                                if (line.isNotBlank()) mirror(line, pid)
                            }
                        }
                        pos = logFile.length()
                    }
                    Thread.sleep(700)
                } catch (_: InterruptedException) {
                    return@Thread
                } catch (_: Exception) {
                    try { Thread.sleep(700) } catch (_: InterruptedException) { return@Thread }
                }
            }
        }.apply { isDaemon = true; start() }
    }

    private fun mirror(line: String, pid: Int) {
        val m = FILE_LINE.find(line)
        val level = when (m?.groupValues?.get(1)) {
            "DEBUG" -> 'D'
            "WARN" -> 'W'
            "ERROR" -> 'E'
            else -> 'I'
        }
        val message = m?.groupValues?.get(2) ?: line
        InAppLog.write(level, LOG_TAG, message, null, pid)
        track(level, message)
    }

    private fun track(level: Char, message: String) {
        lastActivityAt = System.currentTimeMillis()
        SCAN_PROGRESS.find(message)?.let { p ->
            scanChecked = p.groupValues[2].toInt()
            scanTotal = p.groupValues[3].toInt()
            scanUsable = p.groupValues[4].toInt()
        }
        when {
            message.contains("No valid connections found after MTU testing") -> noUsableResolver = true
            message.contains("Session Initialized Successfully") -> {
                sessionFailures = 0
                sessionError = null
            }
            else -> SESSION_FAILED.find(message)?.let {
                sessionFailures++
                sessionError = it.groupValues[1].trim().take(160)
            }
        }
        if (level == 'E') lastErrorLine = message.take(200)
    }

    private fun resetProgress() {
        scanChecked = 0
        scanTotal = 0
        scanUsable = 0
        noUsableResolver = false
        sessionFailures = 0
        sessionError = null
        lastErrorLine = null
        lastActivityAt = System.currentTimeMillis()
    }

    private fun fail(reason: String) {
        failure = Failure.Other(reason)
        Log.e(TAG, reason)
    }

    private fun stopLogTail() {
        tailing = false
        logTail?.interrupt()
        logTail = null
    }

    private fun firstFreePort(from: Int): Int {
        for (p in from..maxListenPort) {
            try {
                ServerSocket().use { it.bind(InetSocketAddress(listenHost, p)); return p }
            } catch (_: Exception) {  }
        }
        return -1
    }

    private fun portAccepts(host: String, port: Int, timeoutMs: Int): Boolean {
        if (port <= 0) return false
        return try {
            Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
            true
        } catch (_: Exception) {
            false
        }
    }

    private companion object {
        const val TAG = "MasterDnsController"

        const val LOG_TAG = "MasterDns"

        val FILE_LINE = Regex("""^\d{4}/\d{2}/\d{2} \d{2}:\d{2}:\d{2} \[(DEBUG|INFO|WARN|ERROR)] ?(.*)$""")

        val SCAN_PROGRESS = Regex("""(Accepted|Rejected) \((\d+)/(\d+)\).*?valid=(\d+)""")

        val SESSION_FAILED = Regex("""Session initialization failed:\s*(.*)$""")

        const val SESSION_FAILURES_TO_GIVE_UP = 3

        const val PRESCREEN_KEEP = 32

        const val PROBE_CACHE_MS = 5_000L
    }
}
