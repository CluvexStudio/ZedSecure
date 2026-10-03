package dev.cluvex.zedsecure.core

import android.content.Context
import android.net.VpnService
import dev.cluvex.zedsecure.domain.config.AetherCoreBuilder
import dev.cluvex.zedsecure.domain.config.AetherProfile
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean

class AetherController(
    private val context: Context,
    private val service: VpnService,
    private val profile: AetherProfile,
    val socksPort: Int,
    private val onStopped: (String?) -> Unit,
) {
    private val active = AtomicBoolean(false)
    private var process: Process? = null

    fun start(): Boolean {
        if (active.get()) stop()
        return try {
            val nativeLibDir = context.applicationInfo.nativeLibraryDir
            val binPath = "$nativeLibDir/libaether.so"
            val workDir = File(context.filesDir, "aether").apply { mkdirs() }
            val args = listOf(binPath) + AetherCoreBuilder.buildArgs(
                profile = profile,
                socksPort = socksPort,
                workDir = workDir.absolutePath,
            )

            val pb = ProcessBuilder(args)
            pb.environment()["AETHER_PSIPHON_BIN"] = "$nativeLibDir/libpsiphon-tunnel-core.so"
            pb.environment()["AETHER_TOR_PT"] = "obfs4=$nativeLibDir/liblyrebird.so;snowflake=$nativeLibDir/liblyrebird.so"
            pb.environment()["SSL_CERT_DIR"] = "/apex/com.android.conscrypt/cacerts:/system/etc/security/cacerts"
            pb.environment()["HOME"] = context.filesDir.absolutePath
            pb.environment()["TMPDIR"] = workDir.absolutePath
            pb.environment()["AETHER_CONFIG"] = File(workDir, "aether.toml").absolutePath
            pb.environment()["AETHER_MASQUE_CONFIG"] = File(workDir, "aether-masque.toml").absolutePath
            pb.environment()["AETHER_WG_CONFIG"] = File(workDir, "aether-wg.toml").absolutePath
            pb.directory(workDir)
            pb.redirectErrorStream(true)

            val p = pb.start()
            process = p
            active.set(true)

            val recentLines = ConcurrentLinkedDeque<String>()
            Thread {
                p.inputStream.bufferedReader().forEachLine { line ->
                    AppLog.i("Aether", line)
                    LogBus.append("I/aether: $line")
                    recentLines.add(line)
                    while (recentLines.size > 3) recentLines.pollFirst()
                }
                if (active.compareAndSet(true, false)) {
                    val code = runCatching { p.waitFor() }.getOrDefault(-1)
                    val lastError = recentLines.joinToString(" \n ").ifBlank { null }
                    onStopped(if (code == 0) null else lastError ?: "Aether exited ($code)")
                }
            }.apply { isDaemon = true; name = "aether-reader" }.start()

            val deadline = System.currentTimeMillis() + 90_000L
            while (System.currentTimeMillis() < deadline) {
                if (!p.isAlive) {
                    stop()
                    return false
                }
                if (isSocks5Ready("127.0.0.1", socksPort)) return true
                Thread.sleep(100)
            }
            stop()
            false
        } catch (e: Exception) {
            active.set(false)
            AppLog.e("Aether", "failed to start", e)
            false
        }
    }

    fun stop() {
        active.set(false)
        runCatching { process?.destroy() }
        process = null
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
