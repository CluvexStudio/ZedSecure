package dev.cluvex.zedsecure.desktop.core

import dev.cluvex.zedsecure.core.tor.TorConfigBuilder
import dev.cluvex.zedsecure.domain.model.AppSettings
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class DesktopTor(
    private val settings: AppSettings,
    private val workDir: File,
    private val upstreamSocksPort: Int? = null,
) {
    @Volatile private var process: Process? = null

    val socksPort: Int get() = TorConfigBuilder.SOCKS_PORT

    fun start(onProgress: (Int) -> Unit = {}, waitSec: Long = BOOTSTRAP_WAIT_SEC): Result<Unit> {
        val home = home(workDir)
        val root = BundledTree.extract(home, "tor")
            ?: return Result.failure(IllegalStateException("Tor is not bundled for ${Os.current}"))
        val torrc = File(home, "torrc").apply { writeText(torrc(settings, root, File(home, "data"), upstreamSocksPort)) }
        if (settings.torFakeSniEnabled) println("[tor] fake SNI needs the patched Android tor; this tor connects without it")

        val exe = File(root, if (Os.current == Os.WINDOWS) "tor.exe" else "tor")
        val builder = ProcessBuilder(exe.absolutePath, "-f", torrc.absolutePath)
            .redirectErrorStream(true)
            .directory(home)
        when (Os.current) {
            Os.LINUX -> builder.environment()["LD_LIBRARY_PATH"] = root.absolutePath
            Os.MACOS -> builder.environment()["DYLD_LIBRARY_PATH"] = root.absolutePath
            else -> Unit
        }
        val started = try {
            builder.start()
        } catch (e: Exception) {
            return Result.failure(IllegalStateException("Tor failed to start: ${e.message}"))
        }
        process = started

        val bootstrapped = AtomicBoolean(false)
        val settled = CountDownLatch(1)
        val lastProblem = StringBuilder()
        Thread {
            runCatching {
                started.inputStream.bufferedReader().forEachLine { line ->
                    println("[tor] $line")
                    if (" [err] " in line || " [warn] " in line) {
                        lastProblem.setLength(0)
                        lastProblem.append(line.substringAfter("] ").trim())
                    }
                    val pct = BOOTSTRAP_PCT.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: return@forEachLine
                    runCatching { onProgress(pct) }
                    if (pct >= 100 && bootstrapped.compareAndSet(false, true)) settled.countDown()
                }
            }
            settled.countDown()
        }.apply { isDaemon = true; name = "tor-output" }.start()

        settled.await(waitSec, TimeUnit.SECONDS)
        if (bootstrapped.get()) return Result.success(Unit)
        val reason = if (started.isAlive) {
            "Tor did not finish connecting in $waitSec seconds"
        } else {
            "Tor exited before connecting" + lastProblem.toString().takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
        }
        stop()
        return Result.failure(IllegalStateException(reason))
    }

    fun stop() {
        val running = process ?: return
        process = null
        XrayCore.stopProcess(running)
    }

    companion object {
        private const val BOOTSTRAP_WAIT_SEC = 180L
        private const val CONJURE_REGISTER_URL = "https://registration.refraction.network/api"
        private val BOOTSTRAP_PCT = Regex("""Bootstrapped (\d{1,3})%""")

        fun home(workDir: File): File {
            val preferred = File(workDir, "tor")
            if (Os.current != Os.WINDOWS || ' ' !in preferred.absolutePath) return preferred
            val programData = System.getenv("ProgramData")?.takeIf { ' ' !in it } ?: "C:\\ProgramData"
            return File(programData, "ZedSecure\\tor")
        }

        fun transports(root: File, windows: Boolean = Os.current == Os.WINDOWS): TorConfigBuilder.Transports {
            val suffix = if (windows) ".exe" else ""
            val pt = File(root, "pt")
            val lyrebird = File(pt, "lyrebird$suffix").takeIf { it.isFile }?.absolutePath
            val conjure = File(pt, "conjure-client$suffix").takeIf { it.isFile }
                ?.let { "${it.absolutePath} -registerURL $CONJURE_REGISTER_URL" }
            return TorConfigBuilder.Transports(obfs = lyrebird, snowflake = lyrebird, conjure = conjure, dnstt = null)
        }

        fun torrc(settings: AppSettings, root: File, dataDir: File, upstreamSocksPort: Int? = null): String {
            dataDir.mkdirs()
            return TorConfigBuilder.build(
                settings = settings,
                nativeLibDir = root.absolutePath,
                torDir = root,
                dataDir = dataDir,
                upstreamSocksPort = upstreamSocksPort,
                transports = transports(root),
            )
        }
    }
}
