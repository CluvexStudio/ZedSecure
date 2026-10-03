package dev.cluvex.zedsecure.desktop.core

import dev.cluvex.zedsecure.core.LogBus
import dev.cluvex.zedsecure.core.VpnManager
import dev.cluvex.zedsecure.domain.config.AetherCoreBuilder
import dev.cluvex.zedsecure.domain.config.AetherProfile
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean

class DesktopAether(
    private val profile: AetherProfile,
    private val workDir: File,
    val socksPort: Int,
) {
    @Volatile
    private var process: Process? = null
    private val active = AtomicBoolean(false)

    fun start(): Result<Unit> {
        val bin = BundledBinary.extract(workDir, "aether", "aether.exe")
            ?: return Result.failure(IllegalStateException("Aether binary not bundled for ${Os.current}"))

        val psiphonBin = if (profile.psiphonMode != AetherProfile.CARRIER_OFF) {
            BundledBinary.extract(workDir, "psiphon", "psiphon.exe")
        } else null

        val args = listOf(bin.absolutePath) + AetherCoreBuilder.buildArgs(
            profile = profile,
            socksPort = socksPort,
            workDir = workDir.absolutePath,
            psiphonBin = psiphonBin?.absolutePath,
        )
        val pb = ProcessBuilder(args).directory(workDir).redirectErrorStream(true)
        pb.environment()["AETHER_CONFIG"] = File(workDir, "aether.toml").absolutePath
        pb.environment()["AETHER_MASQUE_CONFIG"] = File(workDir, "aether-masque.toml").absolutePath
        pb.environment()["AETHER_WG_CONFIG"] = File(workDir, "aether-wg.toml").absolutePath
        pb.environment()["TMPDIR"] = workDir.absolutePath
        if (psiphonBin != null) {
            pb.environment()["AETHER_PSIPHON_BIN"] = psiphonBin.absolutePath
        }

        val started = try {
            pb.start()
        } catch (e: Exception) {
            return Result.failure(IllegalStateException("Aether failed to launch: ${e.message}"))
        }
        process = started

        val recentLines = ConcurrentLinkedDeque<String>()
        Thread {
            runCatching {
                started.inputStream.bufferedReader().forEachLine { line ->
                    LogBus.append("I/aether: $line")
                    recentLines.add(line)
                    while (recentLines.size > 3) recentLines.pollFirst()
                }
            }
            if (active.compareAndSet(true, false)) {
                process = null
                val lastError = recentLines.joinToString(" \n ").ifBlank { "exited unexpectedly" }
                VpnManager.onError("Aether: $lastError")
            }
        }.apply { isDaemon = true; name = "aether-output" }.start()

        val timeoutMs = if (profile.server.isNotBlank()) {
            25_000L
        } else if (profile.scanMode in setOf(AetherProfile.SCAN_BALANCED, AetherProfile.SCAN_THOROUGH, AetherProfile.SCAN_IRONCLAD)) {
            90_000L
        } else {
            25_000L
        }

        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (!started.isAlive) {
                val lastError = recentLines.joinToString(" \n ").ifBlank { "exited unexpectedly" }
                return Result.failure(IllegalStateException("Aether: $lastError"))
            }
            if (isSocks5Ready("127.0.0.1", socksPort)) {
                active.set(true)
                return Result.success(Unit)
            }
            Thread.sleep(100)
        }
        stop()
        return Result.failure(IllegalStateException("Aether did not open SOCKS port $socksPort in ${(timeoutMs / 1000)}s"))
    }

    fun stop() {
        active.set(false)
        val p = process ?: return
        process = null
        XrayCore.stopProcess(p)
    }

    private fun isSocks5Ready(host: String, port: Int): Boolean = try {
        Socket().use { s ->
            s.connect(InetSocketAddress(host, port), 500)
            s.soTimeout = 1000
            val out = s.getOutputStream()
            val input = s.getInputStream()
            out.write(byteArrayOf(0x05, 0x01, 0x00))
            out.flush()
            val b1 = input.read()
            val b2 = input.read()
            b1 == 0x05 && b2 == 0x00
        }
    } catch (_: Exception) {
        false
    }
}
