package dev.cluvex.zedsecure.core

import dev.cluvex.zedsecure.domain.config.AetherProfile
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

/** One-shot process owner shared by Android (API 29+) and desktop. */
class AetherProcess(
    private val binary: File,
    private val dataDir: File,
    private val profile: AetherProfile,
    val socksPort: Int,
    private val launch: (ProcessBuilder) -> Process = { it.start() },
    private val log: (String) -> Unit = {},
) {
    @Volatile private var process: Process? = null
    @Volatile private var stopped = false
    @Volatile private var startedOnce = false
    val isRunning: Boolean get() = !stopped && process?.isAlive == true

    fun start(waitMs: Long = 180_000): Result<Unit> = runCatching {
        synchronized(this) {
            check(!stopped && !startedOnce) { "Aether session has already been used" }
            startedOnce = true
            check(binary.isFile && binary.canExecute()) { "The bundled Aether executable is missing" }
            check(LocalPortPicker.isFree(socksPort)) { "The Aether SOCKS port is already in use" }
            check(dataDir.isDirectory || dataDir.mkdirs()) { "Cannot create the Aether identity directory" }
            // WARP credentials belong to this user, and must survive application restarts.
            dataDir.setReadable(false, false)
            dataDir.setWritable(false, false)
            dataDir.setExecutable(false, false)
            dataDir.setReadable(true, true)
            dataDir.setWritable(true, true)
            dataDir.setExecutable(true, true)
            val builder = ProcessBuilder(listOf(binary.absolutePath) + profile.arguments(File(dataDir, "identity.toml").absolutePath, socksPort))
                .directory(dataDir).redirectErrorStream(true)
            // CLI options use environment variables internally. Do not inherit another Aether
            // session's proxy, Tor, HTTP listener, Zero Trust credentials or transport choices.
            builder.environment().keys.filter { it.startsWith("AETHER_") || it == "RUST_LOG" }.toList()
                .forEach { builder.environment().remove(it) }
            builder.environment()["RUST_LOG"] = "info"
            process = launch(builder)
            process!!.outputStream.close() // Noninteractive; every supported choice is explicit.
        }
        val running = checkNotNull(process)
        Thread({
            runCatching {
                running.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line -> if (line.isNotBlank()) runCatching { log(line.take(1500)) } }
                }
            }
        }, "aether-output").apply { isDaemon = true; start() }
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(waitMs)
        while (isRunning && System.nanoTime() < deadline) {
            // Aether reserves its listener early, but serves SOCKS only after setup.
            // A TCP connect alone would report success while the scan is still running.
            if (socksReady(socksPort) && isRunning) return@runCatching Unit
            Thread.sleep(150)
        }
        error(if (stopped) "Aether connection cancelled" else if (!running.isAlive) "Aether exited before connecting" else "Aether connection timed out")
    }.onFailure { stop() }

    @Synchronized
    fun stop() {
        stopped = true
        val running = process ?: return
        process = null
        running.destroy()
        if (!running.waitFor(2, TimeUnit.SECONDS)) {
            running.destroyForcibly()
            running.waitFor(2, TimeUnit.SECONDS)
        }
    }

    companion object {
        fun socksReady(port: Int): Boolean = runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", port), 250)
                socket.soTimeout = 300
                socket.getOutputStream().apply { write(byteArrayOf(5, 1, 0)); flush() }
                val input = socket.getInputStream()
                input.read() == 5 && input.read() == 0
            }
        }.getOrDefault(false)
    }
}
