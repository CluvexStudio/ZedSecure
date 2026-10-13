package dev.cluvex.zedsecure.desktop.core

import java.io.File
import java.io.OutputStream
import java.nio.CharBuffer
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class AdminAnswer(val password: CharArray, val remember: Boolean = false)

object LinuxBroker {
    const val SAFE_PATH =
        "/run/wrappers/bin:/run/current-system/sw/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
    const val HOME_DIR = "/var/lib/zedsecure"
    const val TUN_NAME = "zedsecure0"
    const val HELLO = "ZEDSECURE_BROKER"
    private const val LABEL = "root"
    private const val PKEXEC_DISMISSED = 126
    private const val PKEXEC_REFUSED = 127
    private const val PKEXEC_WAIT_SEC = 180L
    private const val SUDO_WAIT_SEC = 30L
    private const val PASSWORD_TRIES = 3

    enum class Method { Remembered, Polkit, Sudo, Password }

    private enum class Outcome { Ready, Dismissed, Refused, Failed }

    private class Session(val process: Process, val input: OutputStream, val replies: LinkedBlockingQueue<String>)

    private val startLock = Any()
    private val requestLock = Any()
    @Volatile private var session: Session? = null
    @Volatile private var staging: File? = null
    @Volatile var method: Method? = null
        private set
    @Volatile var onTunLost: (() -> Unit)? = null
    @Volatile private var tunActive = false

    val isAlive: Boolean get() = session?.process?.isAlive == true

    val isRemembered: Boolean get() = File(HOME_DIR, "broker").isFile

    fun ensure(workDir: File, ask: ((retry: Boolean) -> AdminAnswer?)?): Boolean = synchronized(startLock) {
        if (isAlive && (method != Method.Remembered || isRemembered)) return true
        close()
        val started = start(workDir, ask)
        if (!started) discard()
        started
    }

    private fun start(workDir: File, ask: ((retry: Boolean) -> AdminAnswer?)?): Boolean {
        val hev = HevBinary.extract(workDir) ?: run {
            println("[$LABEL] hev is not bundled with this build")
            return false
        }
        val dir = privateDir(workDir, "zedsecure-root-") ?: return false
        staging = dir
        val stagedHev = File(dir, "hev")
        val script = File(dir, "broker.sh")
        val staged = runCatching {
            hev.copyTo(stagedHev, overwrite = true)
            stagedHev.setExecutable(true, true)
            script.writeText(script(persistent = false))
            script.setExecutable(true, true)
        }
        if (staged.isFailure) {
            println("[$LABEL] could not stage the root helper: ${staged.exceptionOrNull()?.message}")
            return false
        }
        val detach = if (onPath("setsid")) listOf("setsid", "-w") else emptyList()
        val helper = listOf("/bin/sh", script.absolutePath, stagedHev.absolutePath)

        if (rememberedMatches(stagedHev) && onPath("sudo")) {
            if (launch(detach + listOf("sudo", "-n", "$HOME_DIR/broker"), null, SUDO_WAIT_SEC) == Outcome.Ready) {
                method = Method.Remembered
                return true
            }
            println("[$LABEL] the remembered administrator access no longer works here; asking again")
        }

        if (onPath("pkexec") && PolkitAgent.available() && !prefersSudo()) {
            when (launch(detach + listOf("pkexec") + helper, null, PKEXEC_WAIT_SEC)) {
                Outcome.Ready -> {
                    method = Method.Polkit
                    refreshRemembered(stagedHev)
                    return true
                }
                Outcome.Dismissed -> return false
                Outcome.Refused -> {
                    println("[$LABEL] polkit did not authorise this account; ZedSecure will ask for the sudo password itself from now on")
                    setPrefersSudo()
                }
                Outcome.Failed -> return false
            }
        }

        if (!onPath("sudo")) {
            println("[$LABEL] neither a polkit agent nor sudo is available to get administrator rights")
            return false
        }
        if (launch(detach + listOf("sudo", "-n") + helper, null, SUDO_WAIT_SEC) == Outcome.Ready) {
            method = Method.Sudo
            refreshRemembered(stagedHev)
            return true
        }
        val askFor = ask ?: return false
        var retry = false
        repeat(PASSWORD_TRIES) {
            val answer = askFor(retry) ?: return false
            try {
                if (!sudoAccepts(detach, answer.password)) {
                    retry = true
                    return@repeat
                }
                val run = detach + listOf("sudo", "-S", "-k", "-p", "") + helper
                if (launch(run, answer.password, SUDO_WAIT_SEC) != Outcome.Ready) return false
                method = Method.Password
                if (answer.remember) {
                    remember().onFailure { println("[$LABEL] could not remember administrator access: ${it.message}") }
                } else {
                    refreshRemembered(stagedHev)
                }
                return true
            } finally {
                answer.password.fill('\u0000')
            }
        }
        return false
    }

    fun tunUp(socksPort: Int, udpOverTcp: Boolean, dns: String?, bypass: List<String>): Result<Unit> {
        val addresses = bypass.map { it.trim().removePrefix("[").removeSuffix("]") }.filter(::isAddress).distinct()
        val dnsArg = dns?.trim()?.takeIf(::isAddress) ?: "-"
        val line = (listOf("TUN_UP", socksPort.toString(), if (udpOverTcp) "tcp" else "udp", dnsArg) + addresses)
            .joinToString(" ")
        return answer(request(line, TUN_WAIT_SEC), "the tunnel did not come up")
    }

    fun tunDown() {
        if (isAlive) request("TUN_DOWN", STOP_WAIT_SEC)
    }

    fun excludeEngines(cgroup: String): Result<Unit> {
        if (!EngineCgroup.isSafe(cgroup)) return Result.failure(IllegalStateException("the engine cgroup has an unexpected name"))
        return answer(request("EXCLUDE $cgroup", STOP_WAIT_SEC), "the root helper could not keep the engines outside the tunnel")
    }

    fun includeEngines() {
        if (isAlive) request("EXCLUDE_OFF", STOP_WAIT_SEC)
    }

    fun startJob(script: File): Result<Unit> =
        answer(request("JOB ${script.absolutePath}", 10), "the root helper did not start the job")

    fun stopJob() {
        if (isAlive) request("JOB_STOP", JOB_STOP_WAIT_SEC)
    }

    fun remember(): Result<Unit> = answer(request("REMEMBER", 30), "the root helper did not answer")

    fun forget(workDir: File, ask: ((retry: Boolean) -> AdminAnswer?)?): Result<Unit> {
        if (!isAlive && !ensure(workDir, ask)) {
            return Result.failure(IllegalStateException("administrator rights are needed to remove the remembered access"))
        }
        return answer(request("FORGET", 15), "the root helper did not answer")
    }

    fun close(waitSec: Long = 10) {
        val s = session ?: return
        session = null
        runCatching { s.input.close() }
        if (!s.process.waitFor(waitSec, TimeUnit.SECONDS)) println("[$LABEL] the root helper is still shutting down")
        discard()
    }

    private fun answer(reply: String?, fallback: String): Result<Unit> = when {
        reply == null -> Result.failure(IllegalStateException(fallback))
        reply == "OK" -> Result.success(Unit)
        else -> Result.failure(IllegalStateException(reply.removePrefix("ERR").trim().ifEmpty { fallback }))
    }

    private fun request(line: String, waitSec: Long): String? = synchronized(requestLock) {
        val s = session?.takeIf { it.process.isAlive } ?: return null
        s.replies.clear()
        val written = runCatching {
            s.input.write((line + "\n").toByteArray())
            s.input.flush()
        }
        if (written.isFailure) return null
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(waitSec)
        var reply: String? = null
        while (reply == null && s.process.isAlive) {
            val left = deadline - System.nanoTime()
            if (left <= 0) break
            reply = s.replies.poll(minOf(left, POLL_NANOS), TimeUnit.NANOSECONDS)
        }
        reply = reply ?: s.replies.poll()
        if (reply == null) println("[$LABEL] no answer to ${line.substringBefore(' ')}")
        if (line.startsWith("TUN_UP")) tunActive = reply == "OK"
        if (line.startsWith("TUN_DOWN")) tunActive = false
        reply
    }

    private fun launch(cmd: List<String>, password: CharArray?, waitSec: Long): Outcome {
        val p = try {
            ProcessBuilder(cmd).redirectErrorStream(true).directory(staging).start()
        } catch (e: Exception) {
            println("[$LABEL] ${cmd.first()} failed to start: ${e.message}")
            return Outcome.Failed
        }
        if (password != null) {
            runCatching {
                p.outputStream.write(encode(password))
                p.outputStream.write('\n'.code)
                p.outputStream.flush()
            }
        }
        val replies = LinkedBlockingQueue<String>()
        val hello = CountDownLatch(1)
        val tail = ArrayDeque<String>()
        Thread {
            runCatching {
                p.inputStream.bufferedReader().forEachLine { raw ->
                    val line = raw.trim()
                    when {
                        line.startsWith(HELLO) -> hello.countDown()
                        line == "OK" || line.startsWith("ERR") -> if (hello.count == 0L) replies.put(line) else keep(tail, line)
                        line == "DOWN tun" -> if (tunActive) {
                            tunActive = false
                            println("[$LABEL] the tunnel stopped on its own")
                            onTunLost?.invoke()
                        }
                        line.isNotEmpty() -> {
                            keep(tail, line)
                            println("[$LABEL] $line")
                        }
                    }
                }
            }
            hello.countDown()
        }.apply { isDaemon = true; name = "root-helper-output" }.start()

        hello.await(waitSec, TimeUnit.SECONDS)
        if (p.isAlive) {
            if (hello.count == 0L) {
                session = Session(p, p.outputStream, replies)
                return Outcome.Ready
            }
            p.destroy()
            println("[$LABEL] the root helper did not start in $waitSec seconds")
            return Outcome.Failed
        }
        val code = runCatching { p.exitValue() }.getOrDefault(-1)
        synchronized(tail) { tail.toList() }.forEach { println("[$LABEL] $it") }
        return when {
            cmd.contains("pkexec") && code == PKEXEC_DISMISSED -> Outcome.Dismissed
            cmd.contains("pkexec") && code == PKEXEC_REFUSED -> Outcome.Refused
            else -> Outcome.Failed
        }
    }

    private fun keep(tail: ArrayDeque<String>, line: String) = synchronized(tail) {
        tail.addLast(line)
        while (tail.size > 5) tail.removeFirst()
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

    private fun refreshRemembered(hev: File) {
        if (isRemembered && !rememberedMatches(hev)) {
            remember().onSuccess { println("[$LABEL] updated the remembered administrator access for this version") }
        }
    }

    private fun rememberedMatches(hev: File): Boolean = runCatching {
        val broker = File(HOME_DIR, "broker")
        val installedHev = File(HOME_DIR, "hev")
        broker.isFile && installedHev.isFile && broker.readText() == script(persistent = true) &&
            installedHev.length() == hev.length() && installedHev.readBytes().contentEquals(hev.readBytes())
    }.getOrDefault(false)

    private fun preferenceFile(): File =
        File(System.getenv("XDG_CONFIG_HOME") ?: (System.getProperty("user.home") + "/.config"), "zedsecure/elevation")

    private fun prefersSudo(): Boolean = runCatching { preferenceFile().readText().trim() == "sudo" }.getOrDefault(false)

    private fun setPrefersSudo() {
        runCatching { preferenceFile().apply { parentFile.mkdirs() }.writeText("sudo\n") }
    }

    private fun discard() {
        val dir = staging ?: return
        staging = null
        runCatching { dir.deleteRecursively() }
    }

    private fun encode(password: CharArray): ByteArray {
        val buffer = Charsets.UTF_8.encode(CharBuffer.wrap(password))
        return ByteArray(buffer.remaining()).also { buffer.get(it) }
    }

    private const val TUN_WAIT_SEC = 25L
    private const val ENGINE_TABLE = "zedsecure_engines"
    private const val ENGINE_MARK = "0x5ed0"
    private const val ENGINE_ROUTES = 5208
    private const val STOP_WAIT_SEC = 15L
    private const val JOB_STOP_WAIT_SEC = 25L
    private val POLL_NANOS = TimeUnit.MILLISECONDS.toNanos(500)

    internal fun isAddress(value: String): Boolean {
        val v4 = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""").matchEntire(value)
        if (v4 != null) return v4.groupValues.drop(1).all { it.toInt() <= 255 }
        return value.count { it == ':' } >= 2 && value.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' }
    }

    fun onPath(command: String): Boolean = which(command) != null

    fun which(command: String): File? =
        (System.getenv("PATH").orEmpty().split(File.pathSeparator) + SAFE_PATH.split(':'))
            .filter { it.isNotBlank() }
            .map { File(it, command) }
            .firstOrNull { it.canExecute() }

    fun privateDir(workDir: File, prefix: String): File? = runCatching {
        val base = System.getenv("XDG_RUNTIME_DIR")?.let(::File)?.takeIf { it.isDirectory && it.canWrite() }
            ?: workDir.also { it.mkdirs() }
        val perms = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))
        Files.createTempDirectory(base.toPath(), prefix, perms).toFile()
    }.onFailure { println("[$LABEL] could not create a private directory: ${it.message}") }.getOrNull()

    internal fun script(persistent: Boolean): String = """
        |#!/bin/sh
        |PATH=$SAFE_PATH
        |export PATH
        |umask 077
        |MODE=${if (persistent) "persistent" else "session"}
        |HOME_DIR=$HOME_DIR
        |TUN=$TUN_NAME
        |RUN=${'$'}(mktemp -d /run/zedsecure.XXXXXX 2>/dev/null || mktemp -d /tmp/zedsecure.XXXXXX) || { echo "ERR no private directory for the root helper"; exit 1; }
        |if [ "${'$'}MODE" = persistent ]; then
        |  HEV="${'$'}HOME_DIR/hev"
        |else
        |  cp "${'$'}1" "${'$'}RUN/hev" 2>/dev/null && chmod 700 "${'$'}RUN/hev" || { echo "ERR hev could not be staged"; rm -rf "${'$'}RUN"; exit 1; }
        |  HEV="${'$'}RUN/hev"
        |fi
        |HEV_PID=
        |WATCH_PID=
        |JOB_PID=
        |ROUTES=
        |RESOLVED=
        |EXCL=
        |SVM=
        |say() { printf '%s\n' "${'$'}*"; }
        |is_port() { case ${'$'}1 in ''|*[!0-9]*) return 1 ;; esac; [ "${'$'}1" -ge 1 ] && [ "${'$'}1" -le 65535 ]; }
        |is_ip4() { case ${'$'}1 in ''|*[!0-9.]*) return 1 ;; *.*.*.*) return 0 ;; esac; return 1; }
        |is_ip6() { case ${'$'}1 in ''|*[!0-9A-Fa-f:]*) return 1 ;; *:*:*) return 0 ;; esac; return 1; }
        |path_to() {
        |  ip "${'$'}1" route get "${'$'}2" 2>/dev/null | awk '{ for (i = 1; i < NF; i++) { if (${'$'}i == "via") v = ${'$'}(i + 1); if (${'$'}i == "dev") d = ${'$'}(i + 1) } } END { if (d != "") print (v != "" ? "via " v " " : "") "dev " d }'
        |}
        |add_route() {
        |  f=${'$'}1
        |  p=${'$'}2
        |  shift 2
        |  ip "${'$'}f" route add "${'$'}p" "${'$'}@" 2>/dev/null || return 1
        |  ROUTES="${'$'}ROUTES
        |${'$'}f ${'$'}p ${'$'}*"
        |}
        |tun_down() {
        |  if [ -n "${'$'}WATCH_PID" ]; then kill "${'$'}WATCH_PID" 2>/dev/null; wait "${'$'}WATCH_PID" 2>/dev/null; fi
        |  WATCH_PID=
        |  [ -n "${'$'}RESOLVED" ] && resolvectl --no-ask-password revert "${'$'}TUN" >/dev/null 2>&1
        |  RESOLVED=
        |  printf '%s\n' "${'$'}ROUTES" | while read -r f p rest; do
        |    [ -n "${'$'}p" ] && ip "${'$'}f" route del "${'$'}p" ${'$'}rest 2>/dev/null
        |  done
        |  ROUTES=
        |  if [ -n "${'$'}HEV_PID" ]; then
        |    kill "${'$'}HEV_PID" 2>/dev/null
        |    i=0
        |    while kill -0 "${'$'}HEV_PID" 2>/dev/null && [ ${'$'}i -lt 50 ]; do sleep 0.1; i=${'$'}((i + 1)); done
        |    kill -9 "${'$'}HEV_PID" 2>/dev/null
        |    i=0
        |    while ip link show dev "${'$'}TUN" >/dev/null 2>&1 && [ ${'$'}i -lt 50 ]; do sleep 0.1; i=${'$'}((i + 1)); done
        |  fi
        |  HEV_PID=
        |  rm -f "${'$'}RUN/hev.pid"
        |}
        |tun_up() {
        |  tun_down
        |  [ ${'$'}# -ge 3 ] || { say "ERR missing arguments"; return; }
        |  port=${'$'}1
        |  udp=${'$'}2
        |  dns=${'$'}3
        |  shift 3
        |  is_port "${'$'}port" || { say "ERR bad port"; return; }
        |  case ${'$'}udp in udp|tcp) ;; *) say "ERR bad UDP mode"; return ;; esac
        |  [ "${'$'}dns" = - ] || is_ip4 "${'$'}dns" || is_ip6 "${'$'}dns" || { say "ERR bad DNS address"; return; }
        |  for ip in "${'$'}@"; do is_ip4 "${'$'}ip" || is_ip6 "${'$'}ip" || { say "ERR bad server address"; return; }; done
        |  V6=
        |  if [ "${'$'}(cat /proc/sys/net/ipv6/conf/default/disable_ipv6 2>/dev/null)" = 0 ] && [ -n "${'$'}(ip -6 route show default 2>/dev/null)" ]; then V6=1; fi
        |  for ip in "${'$'}@"; do
        |    if is_ip6 "${'$'}ip"; then f=-6; len=128; else f=-4; len=32; fi
        |    way=${'$'}(path_to "${'$'}f" "${'$'}ip")
        |    [ -n "${'$'}way" ] && add_route "${'$'}f" "${'$'}ip/${'$'}len" ${'$'}way
        |  done
        |  {
        |    echo "tunnel:"
        |    echo "  name: ${'$'}TUN"
        |    echo "  mtu: 8500"
        |    echo "  ipv4: 172.19.0.1"
        |    [ -n "${'$'}V6" ] && echo "  ipv6: 'fdfe:dcba:9876::1'"
        |    echo "socks5:"
        |    echo "  address: 127.0.0.1"
        |    echo "  port: ${'$'}port"
        |    echo "  udp: '${'$'}udp'"
        |    echo "misc:"
        |    echo "  log-level: warn"
        |  } > "${'$'}RUN/hev.yml"
        |  ( "${'$'}HEV" "${'$'}RUN/hev.yml" > "${'$'}RUN/hev.log" 2>&1 &
        |    echo ${'$'}! > "${'$'}RUN/hev.pid"
        |    wait ${'$'}!
        |    say "DOWN tun" ) &
        |  WATCH_PID=${'$'}!
        |  i=0
        |  while [ ! -s "${'$'}RUN/hev.pid" ] && [ ${'$'}i -lt 50 ]; do sleep 0.05; i=${'$'}((i + 1)); done
        |  HEV_PID=${'$'}(cat "${'$'}RUN/hev.pid" 2>/dev/null)
        |  i=0
        |  while ! ip -o link show dev "${'$'}TUN" 2>/dev/null | grep -q '[<,]UP[,>]'; do
        |    if [ -z "${'$'}HEV_PID" ] || ! kill -0 "${'$'}HEV_PID" 2>/dev/null || [ ${'$'}i -ge 80 ]; then
        |      why=${'$'}(tail -n 3 "${'$'}RUN/hev.log" 2>/dev/null | tr '\n' ' ')
        |      tun_down
        |      say "ERR hev did not bring ${'$'}TUN up. ${'$'}why"
        |      return
        |    fi
        |    sleep 0.1
        |    i=${'$'}((i + 1))
        |  done
        |  if ! add_route -4 0.0.0.0/1 dev "${'$'}TUN" || ! add_route -4 128.0.0.0/1 dev "${'$'}TUN"; then
        |    tun_down
        |    say "ERR could not route traffic into ${'$'}TUN"
        |    return
        |  fi
        |  if [ -n "${'$'}V6" ]; then
        |    add_route -6 ::/1 dev "${'$'}TUN"
        |    add_route -6 8000::/1 dev "${'$'}TUN"
        |  fi
        |  if [ "${'$'}dns" != - ] && command -v resolvectl >/dev/null 2>&1 && resolvectl --no-ask-password dns "${'$'}TUN" "${'$'}dns" >/dev/null 2>&1; then
        |    resolvectl --no-ask-password domain "${'$'}TUN" '~.' >/dev/null 2>&1
        |    resolvectl --no-ask-password default-route "${'$'}TUN" yes >/dev/null 2>&1
        |    RESOLVED=1
        |  fi
        |  say "OK"
        |}
        |excl_down() {
        |  [ -n "${'$'}{EXCL}" ] || return 0
        |  nft delete table inet $ENGINE_TABLE >/dev/null 2>&1
        |  while ip -4 rule del fwmark $ENGINE_MARK lookup $ENGINE_ROUTES priority $ENGINE_ROUTES >/dev/null 2>&1; do :; done
        |  while ip -6 rule del fwmark $ENGINE_MARK lookup $ENGINE_ROUTES priority $ENGINE_ROUTES >/dev/null 2>&1; do :; done
        |  ip -4 route flush table $ENGINE_ROUTES >/dev/null 2>&1
        |  ip -6 route flush table $ENGINE_ROUTES >/dev/null 2>&1
        |  [ -n "${'$'}{SVM}" ] && printf '%s\n' "${'$'}{SVM}" > /proc/sys/net/ipv4/conf/all/src_valid_mark
        |  SVM=
        |  EXCL=
        |}
        |excl_up() {
        |  excl_down
        |  cg=${'$'}{1:-}
        |  case ${'$'}{cg} in ''|/*|*/|*..*|*[!A-Za-z0-9@._:/+-]*) say "ERR bad cgroup"; return ;; esac
        |  [ -d "/sys/fs/cgroup/${'$'}{cg}" ] || { say "ERR the engine cgroup is missing"; return; }
        |  command -v nft >/dev/null 2>&1 || { say "ERR nft is not installed"; return; }
        |  way=${'$'}(path_to -4 1.1.1.1)
        |  [ -n "${'$'}{way}" ] || { say "ERR no route to the internet"; return; }
        |  level=${'$'}(printf '%s\n' "${'$'}{cg}" | awk -F/ '{ print NF }')
        |  EXCL=1
        |  if ! ip -4 route add default ${'$'}{way} table $ENGINE_ROUTES >/dev/null 2>&1 || ! ip -4 rule add fwmark $ENGINE_MARK lookup $ENGINE_ROUTES priority $ENGINE_ROUTES >/dev/null 2>&1; then
        |    excl_down
        |    say "ERR could not route the engines around the tunnel"
        |    return
        |  fi
        |  way6=${'$'}(path_to -6 2606:4700:4700::1111)
        |  if [ -n "${'$'}{way6}" ] && ip -6 route add default ${'$'}{way6} table $ENGINE_ROUTES >/dev/null 2>&1; then
        |    ip -6 rule add fwmark $ENGINE_MARK lookup $ENGINE_ROUTES priority $ENGINE_ROUTES >/dev/null 2>&1
        |  fi
        |  printf 'table inet %s {\n  chain output {\n    type route hook output priority mangle; policy accept;\n    socket cgroupv2 level %s "%s" meta mark set %s ct mark set meta mark\n  }\n  chain prerouting {\n    type filter hook prerouting priority mangle; policy accept;\n    ct mark %s meta mark set ct mark\n  }\n}\n' $ENGINE_TABLE "${'$'}{level}" "${'$'}{cg}" $ENGINE_MARK $ENGINE_MARK > "${'$'}{RUN}/engines.nft"
        |  if ! nft -f "${'$'}{RUN}/engines.nft" 2>"${'$'}{RUN}/nft.err"; then
        |    why=${'$'}(head -n 2 "${'$'}{RUN}/nft.err" | tr '\n' ' ')
        |    excl_down
        |    say "ERR nftables did not accept the engine rule. ${'$'}{why}"
        |    return
        |  fi
        |  dev=${'$'}{way##* }
        |  if [ "${'$'}(cat /proc/sys/net/ipv4/conf/all/rp_filter 2>/dev/null)" = 1 ] || [ "${'$'}(cat "/proc/sys/net/ipv4/conf/${'$'}{dev}/rp_filter" 2>/dev/null)" = 1 ]; then
        |    if [ "${'$'}(cat /proc/sys/net/ipv4/conf/all/src_valid_mark 2>/dev/null)" = 0 ]; then
        |      SVM=0
        |      echo 1 > /proc/sys/net/ipv4/conf/all/src_valid_mark
        |    fi
        |  fi
        |  say "OK"
        |}
        |job_down() {
        |  [ -n "${'$'}JOB_PID" ] || return 0
        |  kill -TERM "${'$'}JOB_PID" 2>/dev/null
        |  ( sleep 20; kill -9 "${'$'}JOB_PID" 2>/dev/null ) &
        |  DOG=${'$'}!
        |  wait "${'$'}JOB_PID" 2>/dev/null
        |  kill "${'$'}DOG" 2>/dev/null
        |  wait "${'$'}DOG" 2>/dev/null
        |  JOB_PID=
        |}
        |job_up() {
        |  [ "${'$'}MODE" = session ] || { say "ERR jobs need the password of this run"; return; }
        |  job_down
        |  [ -f "${'$'}1" ] || { say "ERR the job script is missing"; return; }
        |  /bin/sh "${'$'}1" < /dev/null > /dev/null 2>&1 &
        |  JOB_PID=${'$'}!
        |  say "OK"
        |}
        |remember() {
        |  [ "${'$'}MODE" = session ] || { say "ERR already remembered"; return; }
        |  user=${'$'}{SUDO_USER:-}
        |  if [ -z "${'$'}user" ] && [ -n "${'$'}{PKEXEC_UID:-}" ]; then user=${'$'}(getent passwd "${'$'}PKEXEC_UID" | cut -d: -f1); fi
        |  case ${'$'}user in ''|root|*[!A-Za-z0-9._-]*) say "ERR could not tell which account to remember"; return ;; esac
        |  command -v visudo >/dev/null 2>&1 && [ -d /etc/sudoers.d ] || { say "ERR this system has no sudo drop-in directory"; return; }
        |  mkdir -p "${'$'}HOME_DIR" && chown root:root "${'$'}HOME_DIR" && chmod 755 "${'$'}HOME_DIR" || { say "ERR could not create ${'$'}HOME_DIR"; return; }
        |  cp "${'$'}HEV" "${'$'}HOME_DIR/hev.new" && chmod 755 "${'$'}HOME_DIR/hev.new" && mv -f "${'$'}HOME_DIR/hev.new" "${'$'}HOME_DIR/hev" || { say "ERR could not install hev"; return; }
        |  sed 's/^MODE=session${'$'}/MODE=persistent/' "${'$'}0" > "${'$'}HOME_DIR/broker.new" && chmod 755 "${'$'}HOME_DIR/broker.new" && mv -f "${'$'}HOME_DIR/broker.new" "${'$'}HOME_DIR/broker" || { say "ERR could not install the helper"; return; }
        |  printf '%s ALL=(root) NOPASSWD: %s ""\n' "${'$'}user" "${'$'}HOME_DIR/broker" > "${'$'}RUN/sudoers"
        |  if ! visudo -cf "${'$'}RUN/sudoers" >/dev/null 2>&1; then rm -rf "${'$'}HOME_DIR"; say "ERR sudo rejected the rule"; return; fi
        |  cp "${'$'}RUN/sudoers" /etc/sudoers.d/zedsecure && chown root:root /etc/sudoers.d/zedsecure && chmod 440 /etc/sudoers.d/zedsecure
        |  if ! sudo -n -l -U "${'$'}user" "${'$'}HOME_DIR/broker" >/dev/null 2>&1; then
        |    rm -f /etc/sudoers.d/zedsecure
        |    rm -rf "${'$'}HOME_DIR"
        |    say "ERR sudo on this system does not read /etc/sudoers.d"
        |    return
        |  fi
        |  say "OK"
        |}
        |forget() {
        |  rm -f /etc/sudoers.d/zedsecure
        |  rm -rf "${'$'}HOME_DIR"
        |  say "OK"
        |}
        |cleanup() {
        |  tun_down
        |  excl_down
        |  job_down
        |  rm -rf "${'$'}RUN"
        |}
        |trap cleanup EXIT
        |trap 'exit 0' INT TERM HUP
        |trap '' PIPE
        |say "$HELLO ${'$'}MODE"
        |while IFS= read -r line; do
        |  set -f
        |  set -- ${'$'}line
        |  set +f
        |  cmd=${'$'}{1:-}
        |  [ ${'$'}# -gt 0 ] && shift
        |  case ${'$'}cmd in
        |    TUN_UP) tun_up "${'$'}@" ;;
        |    TUN_DOWN) tun_down; say "OK" ;;
        |    EXCLUDE) excl_up "${'$'}@" ;;
        |    EXCLUDE_OFF) excl_down; say "OK" ;;
        |    JOB) job_up "${'$'}{line#JOB }" ;;
        |    JOB_STOP) job_down; say "OK" ;;
        |    REMEMBER) remember ;;
        |    FORGET) forget ;;
        |    PING) say "OK" ;;
        |    '') ;;
        |    *) say "ERR unknown command" ;;
        |  esac
        |done
        |exit 0
        |""".trimMargin()
}
