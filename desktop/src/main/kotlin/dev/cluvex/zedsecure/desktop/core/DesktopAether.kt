package dev.cluvex.zedsecure.desktop.core

import java.io.File
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Aether censorship-tunnel engine (CluvexStudio/Aether v2.3.0): automatic
 * route discovery over MASQUE/WireGuard/gool/mim, Tor/Psiphon overlays,
 * local SOCKS5 + HTTP frontends. Self-contained section: bundled binary,
 * own profile model, own markers — stock engines untouched.
 *
 * Flag model mirrors the reference GUI (profiles.rs as_args): same modes,
 * same fixed exits (Tor 1820 / Psiphon 1821 unless overridden), same
 * per-mode HTTP flags, same gating. Empty extras forward nothing.
 */
data class AetherProfile(
    val protocol: String = "auto",
    val scanMode: String = "balanced",
    val ipVersion: String = "v4",
    val quickReconnect: Boolean = true,
    val noize: String = "firewall",
    val bind: String = "127.0.0.1:1819",
    val httpPort: String = "",
    val extraTransport: String = "none",
    val torBind: String = "",
    val torBridges: Boolean = false,
    val torRelays: String = "",
    val torBridge: String = "",
    val torBridgeFile: String = "",
    val psiphonBind: String = "",
    val psiphonRegion: String = "",
    val psiphonMode: String = "auto",
    val psiphonConfig: String = "",
    val psiphonCdnIps: String = "",
    val psiphonCdnSni: String = "",
    val psiphonCdnSets: String = "",
    val psiphonServerEntries: String = "",
    val upstream: String = "",
    val dns: String = "",
    val exitLoc: String = "",
    val masqueHttp2: Boolean = false,
) {
    companion object {
        const val TOR_EXIT = "127.0.0.1:1820"
        const val PSI_EXIT = "127.0.0.1:1821"
    }

    private fun isTor() = extraTransport == "tor" || extraTransport == "tor_reverse" || extraTransport == "tor_only"
    private fun isPsi() = extraTransport == "psiphon" || extraTransport == "psiphon_reverse" || extraTransport == "psiphon_only"
    private fun isTorChain() = extraTransport == "tor" || extraTransport == "tor_reverse"
    private fun isPsiChain() = extraTransport == "psiphon" || extraTransport == "psiphon_reverse"

    /** Reverse modes carry TCP only: WireGuard/gool refused by the core. */
    fun effectiveProtocol(): String {
        if ((extraTransport == "tor_reverse" || extraTransport == "psiphon_reverse") &&
            (protocol == "wireguard" || protocol == "gool" || protocol == "gool-classic")
        ) return "masque"
        return protocol
    }

    fun extraWaitSecs(): Long = when {
        isTor() -> 480L
        isPsi() -> 200L
        else -> 0L
    }

    private fun parsePort(addr: String): Int? {
        val p = addr.substringAfterLast(':', "").toIntOrNull() ?: return null
        return if (p in 1..65535) p else null
    }

    private fun loopback(addr: String): String {
        val host = addr.substringBeforeLast(':').trim('[', ']').trim()
        val port = parsePort(addr) ?: return addr
        val h = if (host.isEmpty() || host == "0.0.0.0" || host == "::") "127.0.0.1" else host
        return "$h:$port"
    }

    /** Effective Tor exit (explicit or 1820), loopback-mapped, chain only. */
    fun torExit(): String? {
        if (!isTorChain()) return null
        val raw = torBind.trim().ifEmpty { TOR_EXIT }
        return if (parsePort(raw) != null) loopback(raw) else loopback(TOR_EXIT)
    }

    fun psiExit(): String? {
        if (!isPsiChain()) return null
        val raw = psiphonBind.trim().ifEmpty { PSI_EXIT }
        return if (parsePort(raw) != null) loopback(raw) else loopback(PSI_EXIT)
    }

    fun bindPort(): Int = parsePort(bind) ?: 1819

    /** HTTP frontend port: custom wins unless colliding (SOCKS/exits),
     * else SOCKS+1 (1822 for Tor chains), bumped past taken ports. */
    fun frontendHttpPort(): Int {
        val socks = bindPort()
        val taken = mutableSetOf(socks)
        torExit()?.let { parsePort(it)?.let(taken::add) }
        psiExit()?.let { parsePort(it)?.let(taken::add) }
        httpPort.trim().toIntOrNull()?.let { p ->
            if (p in 1..65535 && p !in taken) return p
        }
        var h = if (isTorChain()) 1822 else if (socks >= 65535) 1820 else socks + 1
        repeat(4) {
            if (h !in taken) return h
            h = if (h >= 65535) 1820 else h + 1
        }
        return h
    }

    /** User-facing SOCKS: the Tor/Psiphon exit in chain modes. */
    fun primaryAddr(): String = torExit() ?: psiExit() ?: loopback("$bind")

    fun httpFrontFlag(): String = when {
        extraTransport == "psiphon" || extraTransport == "psiphon_reverse" || extraTransport == "psiphon_only" -> "--psiphon-http"
        isTor() -> "--tor-http"
        else -> "--http-proxy"
    }

    fun flagHttpAddr(): String {
        val port = frontendHttpPort()
        val host = bind.substringBeforeLast(':').trim('[', ']').ifBlank { "127.0.0.1" }
        return "$host:$port"
    }

    /** CLI flags — the whole profile up front so prompts never appear. */
    fun toArgv(): List<String> {
        val args = ArrayList<String>()
        when (effectiveProtocol()) {
            "masque" -> args.add("--masque")
            "wireguard" -> args.add("--wg")
            "gool" -> args.add("--gool")
            "gool-classic" -> args.add("--gool-classic")
            "mim" -> args.add("--mim")
        }
        args.add("--$scanMode")
        args.add(when (ipVersion) { "v6" -> "-6"; "both" -> "--dual"; else -> "-4" })
        args.add(if (quickReconnect) "--quick-reconnect" else "--no-quick-reconnect")
        args.add("--noize")
        args.add(noize.ifBlank { "firewall" })
        if (bind != "127.0.0.1:1819" && parsePort(bind) != null) {
            args.add("--bind"); args.add(bind)
        }
        args.add(httpFrontFlag()); args.add(flagHttpAddr())
        if (upstream.isNotBlank()) { args.add("--upstream"); args.add(upstream.trim()) }
        when (extraTransport) {
            "tor" -> args.add("--tor")
            "tor_reverse" -> args.add("--tor-reverse")
            "tor_only" -> args.add("--tor-only")
            "psiphon" -> args.add("--psiphon")
            "psiphon_reverse" -> args.add("--psiphon-reverse")
            "psiphon_only" -> args.add("--psiphon-only")
        }
        if (torBridges && isTor()) args.add("--tor-bridges")
        if (isPsi()) {
            val region = psiphonRegion.trim().uppercase()
            if (region.length == 2 && region.all { it.isLetter() }) {
                args.add("--psiphon-region"); args.add(region)
            }
            if (psiphonMode == "cdn") { args.add("--psiphon-mode"); args.add("cdn") }
            if (psiphonMode == "direct") { args.add("--psiphon-mode"); args.add("direct") }
        }
        if (dns.isNotBlank()) { args.add("--dns"); args.add(dns.trim()) }
        if (exitLoc.isNotBlank()) { args.add("--exit-loc"); args.add(exitLoc.trim()) }
        if (isTor()) {
            if (torRelays.isNotBlank()) { args.add("--tor-relays"); args.add(torRelays.trim()) }
            torBridge.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                args.add("--tor-bridge"); args.add(it)
            }
            if (torBridgeFile.isNotBlank()) { args.add("--tor-bridge-file"); args.add(torBridgeFile.trim()) }
            if (torBind.isNotBlank()) { args.add("--tor-bind"); args.add(torBind.trim()) }
        }
        if (isPsi()) {
            mapOf(
                "--psiphon-config" to psiphonConfig, "--psiphon-cdn-ips" to psiphonCdnIps,
                "--psiphon-cdn-sni" to psiphonCdnSni, "--psiphon-cdn-sets" to psiphonCdnSets,
                "--psiphon-server-entries" to psiphonServerEntries, "--psiphon-bind" to psiphonBind,
            ).forEach { (flag, v) -> if (v.isNotBlank()) { args.add(flag); args.add(v.trim()) } }
        }
        return args
    }

    fun env(): Map<String, String> = mapOf(
        "AETHER_MASQUE_HTTP2" to if (masqueHttp2) "1" else "0",
    )
}

/** Structured core-log events (same markers the reference GUI keys on). */
sealed interface AetherLogEvent {
    data object EngineStarted : AetherLogEvent
    data object OverlayReady : AetherLogEvent
    data class ScanBudget(val secs: Int) : AetherLogEvent
    data class TorPercent(val pct: Int) : AetherLogEvent
    data class Traffic(val upBytes: Long, val downBytes: Long) : AetherLogEvent
}

private val BUDGET_RE = Regex("""budget=(\d+)s""")
private val TOR_PCT_RE = Regex("""reaching the network:\s*(\d+)%""")
private val STATS_RE = Regex("""\[=\] up ([\d.]+) (\w+) down ([\d.]+) (\w+)""")

private fun toBytes(v: Double, unit: String): Long = when (unit) {
    "KiB" -> (v * 1024).toLong()
    "MiB" -> (v * 1024 * 1024).toLong()
    "GiB" -> (v * 1024 * 1024 * 1024).toLong()
    "TiB" -> (v * 1024 * 1024 * 1024 * 1024).toLong()
    else -> v.toLong()
}

fun parseAetherLogLine(line: String): AetherLogEvent? {
    if ("Zero Trust access code required" in line) return null // surfaced via raw log watch
    if ("tor is ready;" in line || "psiphon is ready;" in line) return AetherLogEvent.OverlayReady
    if ("Aether v" in line) return AetherLogEvent.EngineStarted
    TOR_PCT_RE.findAll(line).lastOrNull()?.let { return AetherLogEvent.TorPercent(it.groupValues[1].toInt()) }
    BUDGET_RE.find(line)?.let { return AetherLogEvent.ScanBudget(it.groupValues[1].toInt()) }
    STATS_RE.find(line)?.let {
        return AetherLogEvent.Traffic(
            toBytes(it.groupValues[1].toDouble(), it.groupValues[2]),
            toBytes(it.groupValues[3].toDouble(), it.groupValues[4]),
        )
    }
    return null
}

private fun portLive(host: String, port: Int): Boolean = runCatching {
    Socket(host, port).use { true }
}.getOrDefault(false)

/**
 * Aether engine: bundled binary + argv + marker-driven readiness.
 * Mirrors DesktopTor: start() blocks until ready/timeout, stop() kills.
 */
class DesktopAether(
    private val workDir: File,
    private val profile: AetherProfile,
) {
    @Volatile private var process: Process? = null

    val socksPort: Int get() = profile.bindPort()

    fun start(
        onEvent: (AetherLogEvent) -> Unit = {},
        onLogLine: (String) -> Unit = {},
        waitExtraSecs: Long = profile.extraWaitSecs(),
        scanBudgetSecs: Long = 150L,
    ): Result<Unit> {
        val root = BundledTree.extract(File(workDir, "aether"), "aether")
            ?: return Result.failure(IllegalStateException("Aether is not bundled for ${Os.current}"))
        val exe = File(root, if (Os.current == Os.WINDOWS) "aether.exe" else "aether")
        if (!exe.isFile) return Result.failure(IllegalStateException("aether binary missing in bundle"))
        val command = mutableListOf(exe.absolutePath) + profile.toArgv()
        val started = try {
            ProcessBuilder(command).directory(root)
                .apply { environment().putAll(profile.env()) }
                .redirectErrorStream(true).start()
        } catch (e: Exception) {
            return Result.failure(IllegalStateException("Aether failed to start: ${e.message}"))
        }
        process = started

        val overlayReady = AtomicBoolean(profile.extraTransport == "none")
        val engineSeen = AtomicBoolean(false)
        val settled = CountDownLatch(1)
        val needPorts = mutableListOf(profile.bindPort())
        profile.torExit()?.substringAfterLast(':')?.toIntOrNull()?.let(needPorts::add)
        profile.psiExit()?.substringAfterLast(':')?.toIntOrNull()?.let(needPorts::add)
        fun socksLive(): Boolean {
            val host = if (profile.bind.startsWith("0.0.0.0")) "127.0.0.1" else profile.bind.substringBeforeLast(':')
            return needPorts.all { portLive(host, it) }
        }
        Thread {
            runCatching {
                started.inputStream.bufferedReader().forEachLine { line ->
                    onLogLine(line)
                    val ev = parseAetherLogLine(line)
                    when (ev) {
                        is AetherLogEvent.EngineStarted -> engineSeen.set(true)
                        is AetherLogEvent.OverlayReady -> overlayReady.set(true)
                        else -> Unit
                    }
                    if (ev != null) runCatching { onEvent(ev) }
                    if (overlayReady.get() && socksLive()) settled.countDown()
                }
            }
            settled.countDown()
        }.apply { isDaemon = true; name = "aether-output" }.start()

        settled.await(scanBudgetSecs + waitExtraSecs, TimeUnit.SECONDS)
        if (overlayReady.get() && socksLive()) return Result.success(Unit)
        val reason = if (started.isAlive) "Aether did not finish connecting in time"
        else "Aether exited before connecting"
        stop()
        return Result.failure(IllegalStateException(reason))
    }

    fun stop() {
        val running = process ?: return
        process = null
        runCatching { running.destroy() }
        runCatching { running.waitFor() }
    }
}
