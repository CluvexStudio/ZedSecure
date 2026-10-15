package dev.cluvex.zedsecure.desktop.core

import dev.cluvex.zedsecure.domain.config.AetherLogEvent
import dev.cluvex.zedsecure.domain.config.AetherProfile
import dev.cluvex.zedsecure.domain.config.parseAetherLogLine

import java.io.File
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

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
