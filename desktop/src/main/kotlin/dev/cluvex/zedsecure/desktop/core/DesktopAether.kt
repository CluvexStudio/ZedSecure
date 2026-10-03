package dev.cluvex.zedsecure.desktop.core

import dev.cluvex.zedsecure.core.LogBus
import dev.cluvex.zedsecure.domain.config.AetherCoreBuilder
import dev.cluvex.zedsecure.domain.config.AetherProfile
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

class DesktopAether(
    private val profile: AetherProfile,
    private val workDir: File,
    val socksPort: Int,
) {
    @Volatile
    private var process: Process? = null

    fun start(): Result<Unit> {
        val bin = BundledBinary.extract(workDir, "aether", "aether.exe")
            ?: return Result.failure(IllegalStateException("Aether binary not bundled for ${Os.current}"))

        val args = listOf(bin.absolutePath) + AetherCoreBuilder.buildArgs(
            profile = profile,
            socksPort = socksPort,
            workDir = workDir.absolutePath,
        )
        val pb = ProcessBuilder(args).directory(workDir).redirectErrorStream(true)

        val started = try {
            pb.start()
        } catch (e: Exception) {
            return Result.failure(IllegalStateException("Aether failed to launch: ${e.message}"))
        }
        process = started

        Thread {
            runCatching {
                started.inputStream.bufferedReader().forEachLine { line ->
                    LogBus.append("I/aether: $line")
                }
            }
        }.apply { isDaemon = true; name = "aether-output" }.start()

        val deadline = System.currentTimeMillis() + 30_000L
        while (System.currentTimeMillis() < deadline) {
            if (!started.isAlive) return Result.failure(IllegalStateException("Aether exited unexpectedly"))
            if (isPortOpen("127.0.0.1", socksPort)) return Result.success(Unit)
            Thread.sleep(100)
        }
        stop()
        return Result.failure(IllegalStateException("Aether did not open SOCKS port $socksPort in 30s"))
    }

    fun stop() {
        val p = process ?: return
        process = null
        XrayCore.stopProcess(p)
    }

    private fun isPortOpen(host: String, port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(host, port), 200); true }
    } catch (_: Exception) {
        false
    }
}
