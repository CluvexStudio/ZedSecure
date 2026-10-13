package dev.cluvex.zedsecure.desktop.core

import dev.cluvex.zedsecure.domain.config.OpenConnectProfile
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class DesktopOpenConnect(
    private val profile: OpenConnectProfile,
    private val workDir: File,
    private val askPassword: ((retry: Boolean) -> AdminAnswer?)? = null,
    private val os: Os = Os.current,
) {
    @Volatile private var linuxJob = false
    @Volatile private var windowsJob = false
    @Volatile private var helper: Process? = null
    @Volatile private var state: File? = null

    fun connect(): Result<Unit> {
        val binary = locate() ?: return Result.failure(IllegalStateException(missingMessage()))
        val dir = when (os) {
            Os.LINUX -> LinuxBroker.privateDir(workDir, "zedsecure-oc-")
            else -> runCatching { Files.createTempDirectory(workDir.also { it.mkdirs() }.toPath(), "oc-").toFile() }.getOrNull()
        } ?: return Result.failure(IllegalStateException("Could not create a private directory for OpenConnect"))
        state = dir
        val script = if (os == Os.LINUX) binary.script?.let { wrapScript(dir, it) } else binary.script
        val args = runCatching { arguments(dir, script) }.getOrElse {
            discard()
            return Result.failure(it)
        }
        File(dir, SECRET).writeText(profile.password + "\n")
        val up = when (os) {
            Os.LINUX -> startLinux(dir, binary.executable, args, awaitScript = script != null)
            Os.WINDOWS -> startWindows(dir, binary.executable, args)
            Os.MACOS -> startMac(dir, binary.executable, args)
            else -> false
        }
        if (up) return Result.success(Unit)
        val reason = runCatching { File(dir, FAILED).readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }
            ?: logTail(dir)
        disconnect()
        return Result.failure(IllegalStateException(reason ?: "OpenConnect could not connect"))
    }

    val isUp: Boolean
        get() = state?.let { File(it, READY).isFile } == true

    fun disconnect() {
        if (linuxJob) {
            linuxJob = false
            LinuxBroker.stopJob()
        }
        if (windowsJob) {
            windowsJob = false
            WindowsElevation.ocDown()
        }
        val dir = state
        if (dir != null && os == Os.MACOS) {
            runCatching { File(dir, STOP).writeText("stop") }
            val deadline = System.currentTimeMillis() + STOP_WAIT_SEC * 1000
            while (File(dir, READY).isFile && System.currentTimeMillis() < deadline) Thread.sleep(POLL_MS)
            helper?.let { if (!it.waitFor(2, TimeUnit.SECONDS)) it.destroy() }
        }
        helper = null
        discard()
    }

    private fun discard() {
        val dir = state ?: return
        state = null
        runCatching { dir.deleteRecursively() }
    }

    internal fun arguments(dir: File, script: File?): List<String> = buildList {
        add("--protocol=${profile.protocol.ifBlank { OpenConnectProfile.PROTO_ANYCONNECT }}")
        if (profile.username.isNotBlank()) add("--user=${profile.username}")
        add("--passwd-on-stdin")
        add("--non-inter")
        if (profile.authgroup.isNotBlank()) add("--authgroup=${profile.authgroup}")
        if (profile.serverCertSha256.isNotBlank()) add("--servercert=${profile.serverCertSha256.trim()}")
        if (profile.caCertPem.isNotBlank()) add("--cafile=${write(dir, "ca.pem", profile.caCertPem).absolutePath}")
        when {
            profile.clientCertP12Base64.isNotBlank() -> {
                val p12 = File(dir, "client.p12").apply {
                    writeBytes(java.util.Base64.getMimeDecoder().decode(profile.clientCertP12Base64))
                }
                add("--certificate=${p12.absolutePath}")
            }
            profile.clientCertPem.isNotBlank() -> {
                add("--certificate=${write(dir, "client.pem", profile.clientCertPem).absolutePath}")
                if (profile.clientKeyPem.isNotBlank()) add("--sslkey=${write(dir, "client.key", profile.clientKeyPem).absolutePath}")
            }
        }
        if (profile.clientKeyPassword.isNotBlank()) add("--key-password=${profile.clientKeyPassword}")
        when (profile.tokenMode) {
            OpenConnectProfile.TOKEN_TOTP, OpenConnectProfile.TOKEN_HOTP, OpenConnectProfile.TOKEN_STOKEN -> {
                add("--token-mode=${profile.tokenMode}")
                if (profile.tokenSecret.isNotBlank()) add("--token-secret=@${write(dir, "token", profile.tokenSecret).absolutePath}")
            }
            OpenConnectProfile.TOKEN_OIDC -> throw IllegalStateException(
                "Single sign-on (OIDC) logins need a browser and are only available on Android for now",
            )
        }
        if (profile.userAgent.isNotBlank()) add("--useragent=${profile.userAgent}")
        desktopOs()?.let { add("--os=$it") }
        if (profile.disableDtls) add("--no-dtls")
        if (profile.mtu > 0) add("--mtu=${profile.mtu}")
        if (profile.proxy.isNotBlank()) add("--proxy=${profile.proxy}")
        if (os == Os.WINDOWS) add("--interface=$ADAPTER")
        script?.let { add("--script=${it.absolutePath}") }
        add(profile.server.trim())
    }

    private fun desktopOs(): String? = when (profile.reportedOs) {
        "", "android", "apple-ios" -> when (os) {
            Os.WINDOWS -> "win"
            Os.MACOS -> "mac-intel"
            Os.LINUX -> "linux-64"
            else -> null
        }
        else -> profile.reportedOs
    }

    private fun write(dir: File, name: String, text: String): File = File(dir, name).apply { writeText(text) }

    internal class Binary(val executable: File, val script: File?)

    private fun locate(): Binary? = when (os) {
        Os.WINDOWS -> BundledTree.extract(File(workDir, "openconnect"), "openconnect")?.let { root ->
            File(root, "openconnect.exe").takeIf { it.isFile }?.let { Binary(it, File(root, "vpnc-script-win.js").takeIf { s -> s.isFile }) }
        }
        Os.LINUX -> LinuxBroker.which("openconnect")?.let { Binary(it, LINUX_SCRIPTS.map(::File).firstOrNull { s -> s.canExecute() }) }
        Os.MACOS -> MAC_BINARIES.map(::File).firstOrNull { it.canExecute() }
            ?.let { Binary(it, MAC_SCRIPTS.map(::File).firstOrNull { s -> s.isFile }) }
        else -> null
    }

    private fun missingMessage(): String = when (os) {
        Os.LINUX -> "OpenConnect is not installed. Install the openconnect package (for example sudo apt install openconnect) and connect again."
        Os.MACOS -> "OpenConnect is not installed. Install it with Homebrew (brew install openconnect) and connect again."
        else -> "OpenConnect is not bundled with this build"
    }

    internal fun wrapScript(dir: File, real: File): File = File(dir, WRAPPER).apply {
        writeText(
            """
            |#!/bin/sh
            |${shQuote(real.absolutePath)} "${'$'}@"
            |rc=${'$'}?
            |[ "${'$'}{reason:-}" = connect ] && echo "${'$'}rc" > ${shQuote(File(dir, CONFIGURED).absolutePath)}
            |exit ${'$'}rc
            |""".trimMargin(),
        )
        setExecutable(true, true)
    }

    private fun startLinux(dir: File, executable: File, args: List<String>, awaitScript: Boolean): Boolean {
        val script = File(dir, "oc-up.sh").apply {
            writeText(linuxScript(executable, args, dir, awaitScript))
            setExecutable(true, true)
        }
        if (!LinuxBroker.ensure(workDir, askPassword)) return false
        LinuxBroker.startJob(script).onFailure {
            println("[$LABEL] ${it.message}")
            return false
        }
        linuxJob = true
        return awaitReady(dir, null)
    }

    internal fun linuxScript(executable: File, args: List<String>, dir: File, awaitScript: Boolean = false): String = """
        |#!/bin/sh
        |PATH=${LinuxBroker.SAFE_PATH}
        |export PATH
        |umask 022
        |DIR=${shQuote(dir.absolutePath)}
        |rm -f "${'$'}DIR/$READY" "${'$'}DIR/$FAILED" "${'$'}DIR/$CONFIGURED"
        |${shQuote(executable.absolutePath)} ${args.joinToString(" ") { shQuote(it) }} < "${'$'}DIR/$SECRET" > "${'$'}DIR/$LOG" 2>&1 &
        |OC=${'$'}!
        |rm -f "${'$'}DIR/$SECRET"
        |DONE=
        |cleanup() {
        |  [ -n "${'$'}DONE" ] && return
        |  DONE=1
        |  rm -f "${'$'}DIR/$READY"
        |  kill -INT "${'$'}OC" 2>/dev/null
        |  i=0
        |  while kill -0 "${'$'}OC" 2>/dev/null && [ ${'$'}i -lt 50 ]; do sleep 0.2; i=${'$'}((i + 1)); done
        |  kill -TERM "${'$'}OC" 2>/dev/null || true
        |}
        |trap cleanup EXIT
        |trap 'exit 0' INT TERM HUP
        |i=0
        |while [ ${'$'}i -lt ${CONNECT_WAIT_SEC * 5} ]; do
        |  if ! kill -0 "${'$'}OC" 2>/dev/null; then tail -n 5 "${'$'}DIR/$LOG" > "${'$'}DIR/$FAILED" 2>/dev/null; exit 1; fi
        |  if grep -qE '$CONNECTED' "${'$'}DIR/$LOG" 2>/dev/null; then break; fi
        |  sleep 0.2
        |  i=${'$'}((i + 1))
        |done
        |if ! grep -qE '$CONNECTED' "${'$'}DIR/$LOG" 2>/dev/null; then tail -n 5 "${'$'}DIR/$LOG" > "${'$'}DIR/$FAILED" 2>/dev/null; exit 1; fi
        |${if (awaitScript) awaitConfigured() else ""}
        |echo "${'$'}OC" > "${'$'}DIR/$READY"
        |while kill -0 "${'$'}OC" 2>/dev/null; do sleep 1; done
        |exit 0
        |""".trimMargin()

    private fun awaitConfigured(): String = """
        |i=0
        |while [ ! -f "${'$'}DIR/$CONFIGURED" ] && [ ${'$'}i -lt ${SCRIPT_WAIT_SEC * 5} ]; do
        |  if ! kill -0 "${'$'}OC" 2>/dev/null; then tail -n 5 "${'$'}DIR/$LOG" > "${'$'}DIR/$FAILED" 2>/dev/null; exit 1; fi
        |  sleep 0.2
        |  i=${'$'}((i + 1))
        |done
        |if [ ! -f "${'$'}DIR/$CONFIGURED" ]; then echo "the VPN script did not finish setting up the tunnel" > "${'$'}DIR/$FAILED"; exit 1; fi
        |""".trimMargin()

    private fun startMac(dir: File, executable: File, args: List<String>): Boolean {
        val script = File(dir, "oc-helper.sh").apply {
            writeText(macScript(executable, args))
            setExecutable(true, true)
        }
        val command = listOf("/bin/sh", script.absolutePath, dir.absolutePath, ProcessHandle.current().pid().toString())
        val elevated = if (runCatching { exec("sudo", "-n", "true", timeoutSec = 5).first == 0 }.getOrDefault(false)) {
            listOf("sudo", "-n") + command
        } else {
            val shell = command.joinToString(" ") { shQuote(it) }
            listOf("osascript", "-e", "do shell script \"" + shell.replace("\\", "\\\\").replace("\"", "\\\"") + "\" with administrator privileges")
        }
        val process = runCatching { ProcessBuilder(elevated).redirectErrorStream(true).start() }.getOrElse {
            println("[$LABEL] the helper did not start: ${it.message}")
            return false
        }
        helper = process
        Thread {
            runCatching { process.inputStream.bufferedReader().forEachLine { if (it.isNotBlank()) println("[$LABEL] $it") } }
        }.apply { isDaemon = true; name = "oc-helper-output" }.start()
        return awaitReady(dir, process)
    }

    internal fun macScript(executable: File, args: List<String>): String = """
        |#!/bin/sh
        |PATH=/usr/bin:/bin:/usr/sbin:/sbin:/opt/homebrew/bin:/usr/local/bin
        |export PATH
        |DIR="${'$'}1"
        |APP_PID="${'$'}2"
        |rm -f "${'$'}DIR/$READY" "${'$'}DIR/$STOP" "${'$'}DIR/$FAILED"
        |${shQuote(executable.absolutePath)} ${args.joinToString(" ") { shQuote(it) }} < "${'$'}DIR/$SECRET" > "${'$'}DIR/$LOG" 2>&1 &
        |OC=${'$'}!
        |rm -f "${'$'}DIR/$SECRET"
        |cleanup() {
        |  kill -INT "${'$'}OC" 2>/dev/null
        |  i=0
        |  while kill -0 "${'$'}OC" 2>/dev/null && [ ${'$'}i -lt 50 ]; do sleep 0.2; i=${'$'}((i + 1)); done
        |  kill -TERM "${'$'}OC" 2>/dev/null
        |  rm -f "${'$'}DIR/$READY"
        |}
        |trap cleanup EXIT
        |trap 'exit 0' INT TERM HUP
        |i=0
        |while [ ${'$'}i -lt ${CONNECT_WAIT_SEC * 5} ]; do
        |  if ! kill -0 "${'$'}OC" 2>/dev/null; then tail -n 5 "${'$'}DIR/$LOG" > "${'$'}DIR/$FAILED" 2>/dev/null; exit 1; fi
        |  if grep -qE '$CONNECTED' "${'$'}DIR/$LOG" 2>/dev/null; then break; fi
        |  sleep 0.2
        |  i=${'$'}((i + 1))
        |done
        |if ! grep -qE '$CONNECTED' "${'$'}DIR/$LOG" 2>/dev/null; then tail -n 5 "${'$'}DIR/$LOG" > "${'$'}DIR/$FAILED" 2>/dev/null; exit 1; fi
        |echo "${'$'}OC" > "${'$'}DIR/$READY"
        |while kill -0 "${'$'}OC" 2>/dev/null && [ ! -e "${'$'}DIR/$STOP" ] && kill -0 "${'$'}APP_PID" 2>/dev/null; do
        |  sleep 0.3
        |done
        |exit 0
        |""".trimMargin()

    private fun startWindows(dir: File, executable: File, args: List<String>): Boolean {
        File(dir, "oc.args").writeText((listOf(executable.absolutePath) + args).joinToString("\n") + "\n")
        if (!WindowsElevation.ensure(workDir)) return false
        WindowsElevation.ocUp(dir).onFailure {
            println("[$LABEL] ${it.message}")
            File(dir, FAILED).writeText(it.message ?: "OpenConnect could not connect")
            return false
        }
        windowsJob = true
        return true
    }

    private fun awaitReady(dir: File, process: Process?): Boolean {
        val deadline = System.currentTimeMillis() + (CONNECT_WAIT_SEC + 15) * 1000
        while (System.currentTimeMillis() < deadline) {
            if (File(dir, READY).isFile) return true
            if (File(dir, FAILED).isFile) return false
            if (process != null && !process.isAlive && !File(dir, READY).isFile) return false
            Thread.sleep(POLL_MS)
        }
        return false
    }

    private fun logTail(dir: File): String? = runCatching {
        File(dir, LOG).readLines().filter { it.isNotBlank() }.takeLast(3).joinToString(" ")
    }.getOrNull()?.takeIf { it.isNotBlank() }

    companion object {
        const val ADAPTER = "ZedSecure OpenConnect"
        private const val LABEL = "openconnect"
        private const val SECRET = "secret"
        private const val LOG = "openconnect.log"
        private const val READY = "ready"
        private const val STOP = "stop"
        private const val FAILED = "failed"
        private const val CONFIGURED = "configured"
        private const val WRAPPER = "vpnc-wrap.sh"
        private const val SCRIPT_WAIT_SEC = 30L
        private const val CONNECT_WAIT_SEC = 90L
        private const val STOP_WAIT_SEC = 12L
        private const val POLL_MS = 250L
        internal const val CONNECTED = "Connected as |Configured as |ESP session established|Established DTLS connection"

        private val LINUX_SCRIPTS = listOf(
            "/usr/share/vpnc-scripts/vpnc-script", "/etc/vpnc/vpnc-script", "/usr/libexec/vpnc-scripts/vpnc-script",
            "/usr/local/share/vpnc-scripts/vpnc-script", "/etc/vpnc-script",
        )
        private val MAC_BINARIES = listOf("/opt/homebrew/bin/openconnect", "/usr/local/bin/openconnect", "/opt/local/bin/openconnect")
        private val MAC_SCRIPTS = listOf(
            "/opt/homebrew/etc/vpnc/vpnc-script", "/usr/local/etc/vpnc/vpnc-script", "/opt/homebrew/etc/vpnc-script",
            "/usr/local/etc/vpnc-script", "/opt/local/etc/vpnc/vpnc-script",
        )

        internal fun shQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

        internal fun winArg(value: String): String {
            if (value.isNotEmpty() && value.none { it == ' ' || it == '\t' || it == '"' }) return value
            val out = StringBuilder("\"")
            var slashes = 0
            for (c in value) {
                when (c) {
                    '\\' -> slashes++
                    '"' -> {
                        repeat(slashes * 2 + 1) { out.append('\\') }
                        out.append('"')
                        slashes = 0
                    }
                    else -> {
                        repeat(slashes) { out.append('\\') }
                        out.append(c)
                        slashes = 0
                    }
                }
            }
            repeat(slashes * 2) { out.append('\\') }
            return out.append('"').toString()
        }
    }
}
