package dev.cluvex.zedsecure.desktop.core

import java.io.File
import java.nio.CharBuffer
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class LinuxRootHelper(
    private val workingDir: File,
    private val askPassword: ((retry: Boolean) -> CharArray?)?,
    private val label: String,
    private val readyWaitSec: Long = SUDO_WAIT_SEC,
) {
    @Volatile private var process: Process? = null

    private enum class Outcome { Ready, Dismissed, Failed }

    fun start(script: File): Boolean {
        val detach = if (onPath("setsid")) listOf("setsid", "-w") else emptyList()
        val agent = PolkitAgent.available()
        if (onPath("pkexec") && agent) {
            when (elevate(detach + listOf("pkexec", "/bin/sh", script.absolutePath), null, PKEXEC_WAIT_SEC)) {
                Outcome.Ready -> return true
                Outcome.Dismissed -> return false
                Outcome.Failed -> println("[$label] pkexec could not authorise; trying sudo")
            }
        } else if (!agent) {
            println("[$label] this desktop has no polkit agent; asking for the password in ZedSecure instead")
        }
        if (!onPath("sudo")) {
            println("[$label] neither a polkit agent nor sudo is available to get administrator rights")
            return false
        }
        if (elevate(detach + listOf("sudo", "-n", "/bin/sh", script.absolutePath), null, readyWaitSec) == Outcome.Ready) {
            return true
        }
        val ask = askPassword ?: return false
        var retry = false
        repeat(PASSWORD_TRIES) {
            val password = ask(retry) ?: return false
            try {
                if (!sudoAccepts(detach, password)) {
                    retry = true
                    return@repeat
                }
                val run = detach + listOf("sudo", "-S", "-k", "-p", "", "/bin/sh", script.absolutePath)
                return elevate(run, password, readyWaitSec) == Outcome.Ready
            } finally {
                password.fill('\u0000')
            }
        }
        return false
    }

    val isAlive: Boolean get() = process?.isAlive == true

    fun stop(waitSec: Long = 5) {
        val p = process ?: return
        process = null
        runCatching { p.outputStream.close() }
        if (!p.waitFor(waitSec, TimeUnit.SECONDS)) println("[$label] the root helper is still shutting down")
    }

    private fun elevate(cmd: List<String>, password: CharArray?, waitSec: Long): Outcome {
        val p = try {
            ProcessBuilder(cmd).redirectErrorStream(true).directory(workingDir).start()
        } catch (e: Exception) {
            println("[$label] ${cmd.first()} failed to start: ${e.message}")
            return Outcome.Failed
        }
        if (password != null) {
            runCatching {
                p.outputStream.write(encode(password))
                p.outputStream.write('\n'.code)
                p.outputStream.flush()
            }
        }
        val ready = AtomicBoolean(false)
        val settled = CountDownLatch(1)
        Thread {
            runCatching {
                p.inputStream.bufferedReader().forEachLine { line ->
                    if (line.trim() == READY_MARKER) {
                        ready.set(true)
                        settled.countDown()
                    } else if (line.isNotBlank()) {
                        println("[$label] $line")
                    }
                }
            }
            settled.countDown()
        }.apply { isDaemon = true; name = "$label-helper-output" }.start()

        settled.await(waitSec, TimeUnit.SECONDS)
        if (ready.get()) {
            process = p
            return Outcome.Ready
        }
        if (p.isAlive && !p.waitFor(2, TimeUnit.SECONDS)) {
            p.destroy()
            return Outcome.Failed
        }
        return if (!p.isAlive && p.exitValue() == PKEXEC_DISMISSED) Outcome.Dismissed else Outcome.Failed
    }

    private fun sudoAccepts(detach: List<String>, password: CharArray): Boolean = runCatching {
        val p = ProcessBuilder(detach + listOf("sudo", "-S", "-k", "-v", "-p", "")).redirectErrorStream(true).start()
        p.outputStream.use {
            it.write(encode(password))
            it.write('\n'.code)
        }
        p.inputStream.bufferedReader().use { it.readText() }
        if (!p.waitFor(SUDO_WAIT_SEC, TimeUnit.SECONDS)) {
            p.destroyForcibly()
            false
        } else {
            p.exitValue() == 0
        }
    }.getOrDefault(false)

    private fun encode(password: CharArray): ByteArray {
        val buffer = Charsets.UTF_8.encode(CharBuffer.wrap(password))
        return ByteArray(buffer.remaining()).also { buffer.get(it) }
    }

    companion object {
        const val READY_MARKER = "ZEDSECURE_TUN_READY"
        const val SAFE_PATH =
            "/run/wrappers/bin:/run/current-system/sw/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        private const val PKEXEC_DISMISSED = 126
        private const val PKEXEC_WAIT_SEC = 180L
        private const val SUDO_WAIT_SEC = 30L
        private const val PASSWORD_TRIES = 3

        fun onPath(command: String): Boolean = which(command) != null

        fun which(command: String): File? =
            (System.getenv("PATH").orEmpty().split(File.pathSeparator) + SAFE_PATH.split(':'))
                .filter { it.isNotBlank() }
                .map { File(it, command) }
                .firstOrNull { it.canExecute() }

        fun privateDir(workDir: File, prefix: String, label: String): File? = runCatching {
            val base = System.getenv("XDG_RUNTIME_DIR")?.let(::File)?.takeIf { it.isDirectory && it.canWrite() }
                ?: workDir.also { it.mkdirs() }
            val perms = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))
            Files.createTempDirectory(base.toPath(), prefix, perms).toFile()
        }.onFailure { println("[$label] could not create a private directory: ${it.message}") }.getOrNull()
    }
}
