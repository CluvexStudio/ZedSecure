package dev.cluvex.zedsecure.desktop.core

import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

class XrayCore(private val workDir: File) {
    private var process: Process? = null

    val isRunning: Boolean get() = process?.isAlive == true

    fun start(configJson: String): Boolean {
        val cfg = File(workDir, "xray-config.json").apply { writeText(configJson) }
        return launch { bin -> arrayOf(bin.absolutePath, "run", "-c", cfg.absolutePath) }
    }

    fun startSingBox(configJson: String): Boolean {
        val cfg = File(workDir, "sing-box-config.json").apply { writeText(configJson) }
        val dir = File(workDir, "sing-box").apply { mkdirs() }
        return launch { bin -> arrayOf(bin.absolutePath, "singbox", "-c", cfg.absolutePath, "-D", dir.absolutePath) }
    }

    private fun launch(command: (File) -> Array<String>): Boolean {
        val bin = XrayBinary.extract(workDir) ?: run {
            System.err.println("no bundled xray for ${Os.current}")
            return false
        }
        return try {
            val env = if (Os.current == Os.WINDOWS) emptyArray() else arrayOf("XRAY_LOCATION_ASSET=${workDir.absolutePath}")
            process = Runtime.getRuntime().exec(
                command(bin),
                if (env.isEmpty()) null else env,
                workDir,
            )
            Thread { runCatching { process?.inputStream?.bufferedReader()?.forEachLine { println("[xray] $it") } } }
                .apply { isDaemon = true }.start()
            Thread { runCatching { process?.errorStream?.bufferedReader()?.forEachLine { println("[xray] $it") } } }
                .apply { isDaemon = true }.start()
            Thread.sleep(300)
            process?.isAlive == true
        } catch (e: Exception) {
            System.err.println("xray start failed: ${e.message}")
            false
        }
    }

    fun stop() {
        val running = process ?: return
        process = null
        stopProcess(running)
    }

    companion object {
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
