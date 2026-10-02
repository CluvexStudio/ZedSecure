package dev.cluvex.zedsecure.desktop.core

import java.io.File
import java.nio.CharBuffer
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class TunMode(
    private val hevBinary: File,
    private val socksHost: String,
    private val socksPort: Int,
    private val workDir: File,

    private val bypassIps: List<String> = emptyList(),

    private val udpOverTcp: Boolean = false,

    private val askPassword: ((retry: Boolean) -> CharArray?)? = null,
) : DesktopTun {
    private var process: Process? = null
    private var privateDir: File? = null

    val tunName = "tun0"

    private enum class Outcome { Ready, Dismissed, Failed }

    internal fun writeConfig(dir: File): File {
        val cfg = File(dir, "hev-desktop.yaml")
        cfg.writeText(
            """
            tunnel:
              name: $tunName
              mtu: 8500
              ipv4: 198.18.0.1
            socks5:
              address: $socksHost
              port: $socksPort
              udp: '${if (udpOverTcp) "tcp" else "udp"}'
            misc:
              log-level: warn
            """.trimIndent(),
        )
        return cfg
    }

    override fun start(): Boolean = Os.current == Os.LINUX && startLinux()

    private fun startLinux(): Boolean {
        val dir = createPrivateDir() ?: return false
        privateDir = dir
        val hev = File(dir, hevBinary.name)
        runCatching { hevBinary.copyTo(hev, overwrite = true); hev.setExecutable(true, true) }
            .onFailure { println("[tun] could not stage hev: ${it.message}"); return false }
        val script = writeLinuxScript(dir, hev, writeConfig(dir))
        val detach = if (onPath("setsid")) listOf("setsid", "-w") else emptyList()

        if (onPath("pkexec")) {
            when (elevate(detach + listOf("pkexec", "/bin/sh", script.absolutePath), null, PKEXEC_WAIT_SEC)) {
                Outcome.Ready -> return true
                Outcome.Dismissed -> return false
                Outcome.Failed -> println("[tun] pkexec could not authorise; trying sudo")
            }
        }
        if (!onPath("sudo")) return false
        if (elevate(detach + listOf("sudo", "-n", "/bin/sh", script.absolutePath), null, SUDO_WAIT_SEC) == Outcome.Ready) {
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
                return elevate(run, password, SUDO_WAIT_SEC) == Outcome.Ready
            } finally {
                password.fill('\u0000')
            }
        }
        return false
    }

    private fun createPrivateDir(): File? = runCatching {
        val base = System.getenv("XDG_RUNTIME_DIR")?.let(::File)?.takeIf { it.isDirectory && it.canWrite() }
            ?: workDir.also { it.mkdirs() }
        val perms = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))
        Files.createTempDirectory(base.toPath(), "zedsecure-tun-", perms).toFile()
    }.onFailure { println("[tun] could not create a private directory: ${it.message}") }.getOrNull()

    internal fun writeLinuxScript(dir: File, hev: File, cfg: File): File {
        val bypassAdd = bypassIps.joinToString("\n") { ip ->
            "ip route add $ip/32 via \$ORIG_GW dev \$ORIG_DEV 2>/dev/null || true"
        }
        val bypassDel = bypassIps.joinToString("\n") { ip ->
            "  ip route del $ip/32 via \$ORIG_GW dev \$ORIG_DEV 2>/dev/null || true"
        }
        return File(dir, "tun-up.sh").apply {
            writeText(
                """
                |#!/bin/sh
                |PATH=$SAFE_PATH
                |export PATH
                |ORIG_GW=${'$'}(ip route show default | awk '/default/ {print ${'$'}3; exit}')
                |ORIG_DEV=${'$'}(ip route show default | awk '/default/ {print ${'$'}5; exit}')
                |DONE=
                |cleanup() {
                |  [ -n "${'$'}DONE" ] && return
                |  DONE=1
                |  ip route del default dev $tunName 2>/dev/null || true
                |$bypassDel
                |  [ -n "${'$'}HEV" ] && kill ${'$'}HEV 2>/dev/null || true
                |}
                |trap cleanup EXIT
                |trap 'exit 0' INT TERM HUP
                |$bypassAdd
                |"${hev.absolutePath}" "${cfg.absolutePath}" &
                |HEV=${'$'}!
                |for i in 1 2 3 4 5 6 7 8 9 10; do ip link show $tunName >/dev/null 2>&1 && break; sleep 0.3; done
                |if ! ip link show $tunName >/dev/null 2>&1; then echo "hev did not create $tunName"; exit 1; fi
                |ip route add default dev $tunName metric 1 || true
                |( while kill -0 ${'$'}HEV 2>/dev/null; do sleep 1; done; kill -TERM ${'$'}${'$'} 2>/dev/null ) &
                |echo $READY_MARKER
                |read _ || true
                |exit 0
                """.trimMargin() + "\n",
            )
            setExecutable(true, true)
        }
    }

    private fun elevate(cmd: List<String>, password: CharArray?, waitSec: Long): Outcome {
        val p = try {
            ProcessBuilder(cmd).redirectErrorStream(true).directory(privateDir ?: workDir).start()
        } catch (e: Exception) {
            println("[tun] ${cmd.first()} failed to start: ${e.message}")
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
                        println("[tun] $line")
                    }
                }
            }
            settled.countDown()
        }.apply { isDaemon = true; name = "tun-helper-output" }.start()

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

    private fun onPath(command: String): Boolean =
        (System.getenv("PATH").orEmpty().split(File.pathSeparator) + SAFE_PATH.split(':'))
            .any { it.isNotBlank() && File(it, command).canExecute() }

    companion object {
        fun supported(os: Os = Os.current): Boolean = os == Os.LINUX || os == Os.WINDOWS || os == Os.MACOS

        fun usesZeptun(os: Os = Os.current): Boolean = os == Os.WINDOWS || os == Os.MACOS

        private const val READY_MARKER = "ZEDSECURE_TUN_READY"
        private const val PKEXEC_DISMISSED = 126
        private const val PKEXEC_WAIT_SEC = 180L
        private const val SUDO_WAIT_SEC = 30L
        private const val PASSWORD_TRIES = 3
        private const val SAFE_PATH =
            "/run/wrappers/bin:/run/current-system/sw/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
    }

    override fun stop() {
        val p = process
        if (p != null) {
            runCatching { p.outputStream.close() }
            if (!p.waitFor(5, TimeUnit.SECONDS)) println("[tun] the root helper is still shutting down")
        }
        privateDir?.let { dir -> runCatching { dir.deleteRecursively() } }
        privateDir = null
        process = null
    }

    object Factory {
        fun create(
            workDir: File,
            socksPort: Int,
            bypassIps: List<String>,
            udpOverTcp: Boolean,
            dnsServers: String,
            askPassword: ((retry: Boolean) -> CharArray?)?,
        ): DesktopTun? = if (usesZeptun()) {
            ZeptunBinary.extract(workDir)?.let {
                ZeptunTun(it, socksPort, workDir, bypassIps = bypassIps, udpOverTcp = udpOverTcp, dnsServers = dnsServers)
            }
        } else {
            HevBinary.extract(workDir)?.let {
                TunMode(it, "127.0.0.1", socksPort, workDir, bypassIps = bypassIps, udpOverTcp = udpOverTcp, askPassword = askPassword)
            }
        }
    }
}
