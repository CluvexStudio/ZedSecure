package dev.cluvex.zedsecure.desktop.core

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class ZeptunTun(
    private val zeptun: File,
    private val socksPort: Int,
    private val workDir: File,
    private val bypassIps: List<String> = emptyList(),
    private val udpOverTcp: Boolean = false,
    private val dnsServers: String = DEFAULT_DNS,
    private val includeOnly: List<String> = emptyList(),
    private val os: Os = Os.current,
) : DesktopTun {
    @Volatile private var helper: Process? = null
    @Volatile private var state: File? = null

    internal fun toml(): String = buildString {
        appendLine("preset = \"desktop\"")
        appendLine("log_level = \"warn\"")
        appendLine()
        appendLine("[tun]")
        if (os == Os.WINDOWS) appendLine("name = \"$ADAPTER\"")
        appendLine("address = [\"$TUN_V4/30\", \"$TUN_V6/126\"]")
        appendLine()
        appendLine("[handler]")
        appendLine("kind = \"socks5\"")
        appendLine()
        appendLine("[handler.socks5]")
        appendLine("server = \"127.0.0.1:$socksPort\"")
        appendLine("udp_mode = \"${if (udpOverTcp) "tcp" else "udp"}\"")
        appendLine()
        appendLine("[route]")
        appendLine("auto_route = true")
        if (includeOnly.isNotEmpty()) appendLine("include = ${tomlList(includeOnly)}")
        val excluded = bypassIps.mapNotNull(::hostPrefix).distinct()
        if (excluded.isNotEmpty()) appendLine("exclude = ${tomlList(excluded)}")
        appendLine()
        appendLine("[dns]")
        appendLine("hijack = true")
        appendLine("upstream = \"${dnsUpstream(dnsServers)}\"")
    }

    override fun start(): Boolean = when (os) {
        Os.WINDOWS -> startWindows()
        Os.MACOS -> startMac()
        else -> false
    }

    private fun stage(): Pair<File, File>? {
        val dir = runCatching { Files.createTempDirectory(workDir.also { it.mkdirs() }.toPath(), "tun-").toFile() }
            .onFailure { println("[tun] could not create a state directory: ${it.message}") }
            .getOrNull() ?: return null
        val engine = File(dir, zeptun.name)
        val copied = runCatching {
            zeptun.copyTo(engine, overwrite = true)
            engine.setExecutable(true, true)
            if (os == Os.WINDOWS) {
                ZeptunBinary.WINDOWS_COMPANIONS.forEach { name ->
                    File(zeptun.parentFile, name).takeIf { it.isFile }?.copyTo(File(dir, name), overwrite = true)
                }
            }
        }
        if (copied.isFailure) {
            println("[tun] could not stage zeptun: ${copied.exceptionOrNull()?.message}")
            dir.deleteRecursively()
            return null
        }
        state = dir
        return dir to engine
    }

    private fun startWindows(): Boolean {
        val (dir, engine) = stage() ?: return false
        val config = File(dir, "zeptun.toml").apply { writeText(toml()) }
        val script = File(dir, "tun-helper.ps1").apply { writeText(windowsHelper()) }
        val inner = listOf(
            "-NoProfile", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden",
            "-File", winQuote(script), "-Zeptun", winQuote(engine), "-Config", winQuote(config),
            "-State", winQuote(dir), "-AppPid", ProcessHandle.current().pid().toString(),
        )
        val command = "Start-Process -FilePath 'powershell.exe' -Verb RunAs -WindowStyle Hidden -ArgumentList @(" +
            inner.joinToString(",") { psLiteral(it) } + ")"
        val launcher = runCatching {
            ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", command)
                .redirectErrorStream(true)
                .start()
        }.getOrElse {
            println("[tun] powershell did not start: ${it.message}")
            discard()
            return false
        }
        val output = launcher.inputStream.bufferedReader().readText()
        val launched = launcher.waitFor(UAC_WAIT_SEC, TimeUnit.SECONDS) && launcher.exitValue() == 0
        if (!launched) {
            launcher.destroyForcibly()
            val reason = output.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
            println("[tun] administrator rights were not granted: $reason")
            discard()
            return false
        }
        return awaitReady(dir, null)
    }

    private fun startMac(): Boolean {
        val (dir, engine) = stage() ?: return false
        val config = File(dir, "zeptun.toml").apply { writeText(toml()) }
        val script = File(dir, "tun-helper.sh").apply {
            writeText(macHelper())
            setExecutable(true, true)
        }
        val shell = listOf("/bin/sh", script.absolutePath, engine.absolutePath, config.absolutePath, dir.absolutePath)
            .joinToString(" ") { shQuote(it) } + " " + ProcessHandle.current().pid()
        val apple = "do shell script \"" + shell.replace("\\", "\\\\").replace("\"", "\\\"") +
            "\" with administrator privileges"
        val process = runCatching {
            ProcessBuilder("osascript", "-e", apple).redirectErrorStream(true).start()
        }.getOrElse {
            println("[tun] osascript did not start: ${it.message}")
            discard()
            return false
        }
        helper = process
        Thread {
            runCatching { process.inputStream.bufferedReader().forEachLine { if (it.isNotBlank()) println("[tun] $it") } }
        }.apply { isDaemon = true; name = "tun-helper-output" }.start()
        return awaitReady(dir, process)
    }

    private fun awaitReady(dir: File, process: Process?): Boolean {
        val ready = File(dir, "ready")
        val failed = File(dir, "failed")
        val deadline = System.currentTimeMillis() + READY_WAIT_SEC * 1000
        while (System.currentTimeMillis() < deadline) {
            if (ready.isFile) return true
            if (failed.isFile) {
                println("[tun] ${runCatching { failed.readText().trim() }.getOrDefault("zeptun failed")}")
                stop()
                return false
            }
            if (process != null && !process.isAlive) {
                println("[tun] the administrator prompt was dismissed or the helper stopped")
                discard()
                return false
            }
            Thread.sleep(POLL_MS)
        }
        println("[tun] zeptun did not come up in $READY_WAIT_SEC seconds")
        stop()
        return false
    }

    override fun stop() {
        val dir = state ?: return
        runCatching { File(dir, "stop").writeText("stop") }
        val ready = File(dir, "ready")
        val deadline = System.currentTimeMillis() + STOP_WAIT_MS
        while (ready.isFile && System.currentTimeMillis() < deadline) Thread.sleep(POLL_MS)
        helper?.let { if (!it.waitFor(2, TimeUnit.SECONDS)) it.destroy() }
        helper = null
        discard()
    }

    private fun discard() {
        val dir = state ?: return
        state = null
        runCatching { dir.deleteRecursively() }
    }

    internal fun windowsHelper(): String = """
        |param([string]${'$'}Zeptun, [string]${'$'}Config, [string]${'$'}State, [int]${'$'}AppPid)
        |${'$'}ErrorActionPreference = 'Continue'
        |${'$'}ready = Join-Path ${'$'}State 'ready'
        |${'$'}stop = Join-Path ${'$'}State 'stop'
        |${'$'}failed = Join-Path ${'$'}State 'failed'
        |${'$'}log = Join-Path ${'$'}State 'zeptun.log'
        |${'$'}out = Join-Path ${'$'}State 'zeptun.out'
        |try {
        |    ${'$'}p = Start-Process -FilePath ${'$'}Zeptun -ArgumentList @('run', '-c', ('"' + ${'$'}Config + '"')) -WorkingDirectory ${'$'}State -NoNewWindow -PassThru -RedirectStandardError ${'$'}log -RedirectStandardOutput ${'$'}out
        |} catch {
        |    Set-Content -LiteralPath ${'$'}failed -Value ('zeptun did not start: ' + ${'$'}_.Exception.Message)
        |    exit 1
        |}
        |${'$'}up = ${'$'}false
        |for (${'$'}i = 0; ${'$'}i -lt 150; ${'$'}i++) {
        |    if (${'$'}p.HasExited) { break }
        |    if (Get-NetIPAddress -IPAddress '$TUN_V4' -ErrorAction SilentlyContinue) { ${'$'}up = ${'$'}true; break }
        |    Start-Sleep -Milliseconds 200
        |}
        |if (-not ${'$'}up) {
        |    if (-not ${'$'}p.HasExited) { Stop-Process -Id ${'$'}p.Id -Force -ErrorAction SilentlyContinue }
        |    ${'$'}why = (Get-Content -LiteralPath ${'$'}log -Tail 5 -ErrorAction SilentlyContinue) -join ' '
        |    Set-Content -LiteralPath ${'$'}failed -Value ('zeptun did not bring the tunnel up. ' + ${'$'}why)
        |    exit 1
        |}
        |Set-Content -LiteralPath ${'$'}ready -Value ${'$'}p.Id
        |while (-not ${'$'}p.HasExited -and -not (Test-Path -LiteralPath ${'$'}stop) -and (Get-Process -Id ${'$'}AppPid -ErrorAction SilentlyContinue)) {
        |    Start-Sleep -Milliseconds 300
        |}
        |if (-not ${'$'}p.HasExited) {
        |    Stop-Process -Id ${'$'}p.Id -Force -ErrorAction SilentlyContinue
        |    ${'$'}p.WaitForExit(5000) | Out-Null
        |}
        |Remove-Item -LiteralPath ${'$'}ready -ErrorAction SilentlyContinue
        |""".trimMargin()

    internal fun macHelper(): String = """
        |#!/bin/sh
        |PATH=/usr/bin:/bin:/usr/sbin:/sbin
        |export PATH
        |ZEPTUN="${'$'}1"
        |CONFIG="${'$'}2"
        |STATE="${'$'}3"
        |APP_PID="${'$'}4"
        |rm -f "${'$'}STATE/ready" "${'$'}STATE/stop" "${'$'}STATE/failed"
        |"${'$'}ZEPTUN" run -c "${'$'}CONFIG" >"${'$'}STATE/zeptun.log" 2>&1 &
        |ZP=${'$'}!
        |cleanup() {
        |  kill "${'$'}ZP" 2>/dev/null
        |  wait "${'$'}ZP" 2>/dev/null
        |  rm -f "${'$'}STATE/ready"
        |}
        |trap cleanup EXIT
        |trap 'exit 0' INT TERM HUP
        |UP=
        |i=0
        |while [ ${'$'}i -lt 150 ]; do
        |  kill -0 "${'$'}ZP" 2>/dev/null || break
        |  if ifconfig | grep -q 'inet $TUN_V4 '; then UP=1; break; fi
        |  sleep 0.2
        |  i=${'$'}((i + 1))
        |done
        |if [ -z "${'$'}UP" ]; then
        |  echo "zeptun did not bring the tunnel up. ${'$'}(tail -n 5 "${'$'}STATE/zeptun.log" 2>/dev/null | tr '\n' ' ')" >"${'$'}STATE/failed"
        |  exit 1
        |fi
        |echo "${'$'}ZP" >"${'$'}STATE/ready"
        |while kill -0 "${'$'}ZP" 2>/dev/null && [ ! -e "${'$'}STATE/stop" ] && kill -0 "${'$'}APP_PID" 2>/dev/null; do
        |  sleep 0.3
        |done
        |exit 0
        |""".trimMargin()

    companion object {
        const val ADAPTER = "ZedSecure"
        const val TUN_V4 = "172.19.0.1"
        const val TUN_V6 = "fdfe:dcba:9876::1"
        const val DEFAULT_DNS = "1.1.1.1"
        private const val UAC_WAIT_SEC = 180L
        private const val READY_WAIT_SEC = 45L
        private const val STOP_WAIT_MS = 8_000L
        private const val POLL_MS = 200L

        private val IPV4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
        private val IPV4_PORT = Regex("""^\d{1,3}(\.\d{1,3}){3}:\d{1,5}$""")
        private val IPV6_PORT = Regex("""^\[[0-9A-Fa-f:.]+]:\d{1,5}$""")

        private fun isIpv6(value: String): Boolean =
            value.count { it == ':' } >= 2 && value.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' || it == '.' }

        internal fun hostPrefix(ip: String): String? {
            val value = ip.trim().removePrefix("[").removeSuffix("]")
            return when {
                IPV4.matches(value) -> "$value/32"
                isIpv6(value) -> "$value/128"
                else -> null
            }
        }

        internal fun dnsUpstream(servers: String): String =
            servers.split(',', ' ', ';', '\n').map { it.trim() }.firstNotNullOfOrNull(::upstreamOf) ?: "$DEFAULT_DNS:53"

        private fun upstreamOf(value: String): String? {
            val bare = value.removePrefix("[").removeSuffix("]")
            return when {
                IPV4.matches(value) -> "$value:53"
                IPV4_PORT.matches(value) || IPV6_PORT.matches(value) -> value
                isIpv6(bare) -> "[$bare]:53"
                else -> null
            }
        }

        private fun tomlList(values: List<String>): String = values.joinToString(", ", "[", "]") { "\"$it\"" }

        private fun psLiteral(value: String): String = "'" + value.replace("'", "''") + "'"

        private fun winQuote(file: File): String = "\"" + file.absolutePath + "\""

        private fun shQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
    }
}
