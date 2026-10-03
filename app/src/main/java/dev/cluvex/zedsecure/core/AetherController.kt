package dev.cluvex.zedsecure.core

import android.content.Context
import android.net.VpnService
import dev.cluvex.zedsecure.domain.config.AetherCoreBuilder
import dev.cluvex.zedsecure.domain.config.AetherProfile
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
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
            val args = listOf(binPath) + AetherCoreBuilder.buildArgs(
                profile = profile,
                socksPort = socksPort,
                workDir = context.filesDir.absolutePath,
            )

            val pb = ProcessBuilder(args)
            pb.environment()["AETHER_PSIPHON_BIN"] = "$nativeLibDir/libpsiphon-tunnel-core.so"
            pb.environment()["AETHER_TOR_PT"] = "obfs4=$nativeLibDir/liblyrebird.so;snowflake=$nativeLibDir/liblyrebird.so"
            pb.environment()["SSL_CERT_DIR"] = "/apex/com.android.conscrypt/cacerts:/system/etc/security/cacerts"
            pb.environment()["HOME"] = context.filesDir.absolutePath
            pb.environment()["TMPDIR"] = context.cacheDir.absolutePath
            pb.directory(context.filesDir)
            pb.redirectErrorStream(true)

            val p = pb.start()
            process = p
            active.set(true)

            Thread {
                p.inputStream.bufferedReader().forEachLine { line ->
                    AppLog.i("Aether", line)
                    LogBus.append("I/aether: $line")
                }
                if (active.compareAndSet(true, false)) {
                    val code = runCatching { p.waitFor() }.getOrDefault(-1)
                    onStopped(if (code == 0) null else "Aether exited ($code)")
                }
            }.apply { isDaemon = true; name = "aether-reader" }.start()

            val deadline = System.currentTimeMillis() + 25_000L
            while (System.currentTimeMillis() < deadline) {
                if (!p.isAlive) {
                    stop()
                    return false
                }
                if (isPortOpen("127.0.0.1", socksPort)) return true
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

    private fun isPortOpen(host: String, port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(host, port), 200); true }
    } catch (_: Exception) {
        false
    }
}
