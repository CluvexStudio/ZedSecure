package dev.cluvex.zedsecure.desktop.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.io.File
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

class XrayCore(private val workDir: File) {
    private var process: Process? = null

    val isRunning: Boolean get() = process?.isAlive == true

    fun start(configJson: String, configName: String = "xray-config.json"): Boolean {
        val cfg = File(workDir, configName).apply { writeText(configJson) }
        return launch(localPorts(configJson)) { bin -> arrayOf(bin.absolutePath, "run", "-c", cfg.absolutePath) }
    }

    fun startSingBox(configJson: String): Boolean {
        val cfg = File(workDir, "sing-box-config.json").apply { writeText(configJson) }
        val dir = File(workDir, "sing-box").apply { mkdirs() }
        return launch(localPorts(configJson)) { bin -> arrayOf(bin.absolutePath, "singbox", "-c", cfg.absolutePath, "-D", dir.absolutePath) }
    }

    private fun launch(readyPorts: List<Int>, command: (File) -> Array<String>): Boolean {
        val bin = XrayBinary.extract(workDir) ?: run {
            System.err.println("no bundled xray for ${Os.current}")
            return false
        }
        return try {
            process = ProcessBuilder(*command(bin))
                .directory(workDir)
                .apply { environment()["XRAY_LOCATION_ASSET"] = DesktopGeoAssets.dir.absolutePath }
                .start()
            Thread { runCatching { process?.inputStream?.bufferedReader()?.forEachLine { println("[xray] $it") } } }
                .apply { isDaemon = true }.start()
            Thread { runCatching { process?.errorStream?.bufferedReader()?.forEachLine { println("[xray] $it") } } }
                .apply { isDaemon = true }.start()
            if (readyPorts.isEmpty()) {
                Thread.sleep(300)
                process?.isAlive == true
            } else {
                waitReady(readyPorts)
            }
        } catch (e: Exception) {
            System.err.println("xray start failed: ${e.message}")
            false
        }
    }

    private fun waitReady(ports: List<Int>): Boolean {
        val deadline = System.currentTimeMillis() + READY_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val running = process ?: return false
            if (!running.isAlive) return false
            if (ports.any { accepts(it) }) return true
            Thread.sleep(25)
        }
        println("[xray] the local port did not open within ${READY_TIMEOUT_MS / 1000}s; going on")
        return process?.isAlive == true
    }

    private fun accepts(port: Int): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 200) }
        true
    }.getOrDefault(false)

    fun stop() {
        val running = process ?: return
        process = null
        stopProcess(running)
    }

    companion object {
        private const val READY_TIMEOUT_MS = 20_000L
        private val LOCAL_LISTEN = setOf("127.0.0.1", "localhost", "::1", "0.0.0.0", "::", "")

        internal fun localPorts(configJson: String): List<Int> = runCatching {
            val root = Json.parseToJsonElement(configJson) as? JsonObject ?: return emptyList()
            val inbounds = root["inbounds"] as? JsonArray ?: return emptyList()
            inbounds.mapNotNull { element ->
                val inbound = element as? JsonObject ?: return@mapNotNull null
                val listen = (inbound["listen"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                val port = (inbound["port"] as? JsonPrimitive)?.intOrNull
                    ?: (inbound["listen_port"] as? JsonPrimitive)?.intOrNull
                port?.takeIf { it in 1..65535 && listen in LOCAL_LISTEN }
            }.distinct()
        }.getOrDefault(emptyList())

        fun stopProcess(process: Process, graceMs: Long = 3_000) {
            runCatching {
                process.destroy()
                if (!process.waitFor(graceMs, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly()
                    process.waitFor(2, TimeUnit.SECONDS)
                }
            }
        }
    }
}

object XrayBinary {
    fun extract(workDir: File): File? {
        val (sub, name) = when (Os.current) {
            Os.LINUX -> "linux" to "xray"
            Os.MACOS -> "macos" to "xray"
            Os.WINDOWS -> "windows" to "xray.exe"
            else -> return null
        }
        val stream = XrayBinary::class.java.getResourceAsStream("/bin/$sub/$name") ?: return null
        val out = File(workDir, name)
        if (!replace(stream, out) && !out.isFile) return null
        out.setExecutable(true)

        val cronet = when (Os.current) {
            Os.LINUX -> "libcronet.so"
            Os.WINDOWS -> "libcronet.dll"
            else -> null
        }
        if (cronet != null) {
            XrayBinary::class.java.getResourceAsStream("/bin/$sub/$cronet")?.use { input ->
                val lib = File(workDir, cronet)
                val bytes = input.readBytes()
                if (!lib.isFile || lib.length() != bytes.size.toLong()) replace(bytes.inputStream(), lib)
            }
        }
        return out
    }

    fun replace(source: InputStream, target: File): Boolean {
        val staged = File(target.parentFile, "${target.name}.new")
        return runCatching {
            source.use { input -> staged.outputStream().use { input.copyTo(it) } }
            Files.move(staged.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            true
        }.getOrElse {
            staged.delete()
            false
        }
    }
}
