package dev.cluvex.zedsecure.desktop.core

import java.io.File
import java.nio.file.Files

internal object MacTunHelper {
    private const val LABEL = "tun"
    private const val PROMPT_WAIT_SEC = 180L
    private const val READY_WAIT_SEC = 45L
    private const val STOP_WAIT_MS = 20_000L
    private const val POLL_MS = 200L
    private const val LOG_TAIL = 40

    @Volatile private var process: Process? = null
    @Volatile private var control: File? = null

    val dnsLeftBehind: Boolean get() = File(ZeptunTun.MAC_DNS_RESTORE).isFile

    @Synchronized
    fun up(workDir: File, zeptun: File, toml: String): Boolean {
        val ctrl = ensure(workDir, zeptun, HevBinary.extract(workDir)) ?: return false
        listOf("ready", "failed", "lost", "stop").forEach { File(ctrl, it).delete() }
        File(ctrl, "zeptun.toml").writeText(toml)
        File(ctrl, "engine").writeText("zeptun")
        File(ctrl, "start").writeText("start")
        return awaitReady(ctrl)
    }

    @Synchronized
    fun upHev(workDir: File, hev: File, yaml: String, dns: String, bypass: List<String>): Boolean {
        val ctrl = ensure(workDir, ZeptunBinary.extract(workDir), hev) ?: return false
        listOf("ready", "failed", "lost", "stop").forEach { File(ctrl, it).delete() }
        File(ctrl, "hev.yml").writeText(yaml)
        File(ctrl, "dns").writeText(dns)
        File(ctrl, "bypass").writeText(bypass.joinToString("") { "$it\n" })
        File(ctrl, "engine").writeText("hev")
        File(ctrl, "start").writeText("start")
        return awaitReady(ctrl)
    }

    @Synchronized
    fun down() {
        val ctrl = control ?: return
        if (process?.isAlive != true) return
        File(ctrl, "stop").writeText("stop")
        val deadline = System.currentTimeMillis() + STOP_WAIT_MS
        while ((File(ctrl, "stop").isFile || File(ctrl, "ready").isFile) && System.currentTimeMillis() < deadline) {
            if (process?.isAlive != true) break
            Thread.sleep(POLL_MS)
        }
    }

    @Synchronized
    fun repair(workDir: File, zeptun: File?): Boolean {
        if (!dnsLeftBehind) return true
        println("[$LABEL] the DNS of this Mac still points at the tunnel from an earlier run; putting it back")
        val hev = HevBinary.extract(workDir)
        if (zeptun == null && hev == null) return false
        return ensure(workDir, zeptun, hev) != null && !dnsLeftBehind
    }

    @Synchronized
    fun close() {
        val ctrl = control ?: return
        runCatching { File(ctrl, "exit").writeText("exit") }
        val running = process
        if (running != null) {
            val deadline = System.currentTimeMillis() + STOP_WAIT_MS
            while (running.isAlive && System.currentTimeMillis() < deadline) Thread.sleep(POLL_MS)
            if (running.isAlive) {
                // It is still tearing down (e.g. many network services to restore DNS on).
                // Deleting its control directory now would cut off the only channel we have
                // to it (it is root; we cannot force-kill it), orphaning it forever as a
                // zombie that keeps its tun interface and routes up until the next reboot.
                println("[$LABEL] the privileged helper is still shutting down; leaving it to finish on its own")
                return
            }
        }
        process = null
        control = null
        runCatching { ctrl.deleteRecursively() }
    }

    fun lastLog(): List<String> {
        val ctrl = control ?: return emptyList()
        return runCatching { File(ctrl, "zeptun.log").readLines().takeLast(LOG_TAIL) }.getOrDefault(emptyList())
            .filter { it.isNotBlank() }
    }

    private fun ensure(workDir: File, zeptun: File?, hev: File?): File? {
        control?.takeIf { process?.isAlive == true }?.let { return it }
        control?.let { runCatching { it.deleteRecursively() } }
        control = null
        process = null
        val ctrl = runCatching { Files.createTempDirectory(workDir.also { it.mkdirs() }.toPath(), "mactun-").toFile() }
            .onFailure { println("[$LABEL] could not create a control directory: ${it.message}") }
            .getOrNull() ?: return null
        val script = File(ctrl, "tun-helper.sh")
        val staged = runCatching {
            script.writeText(script())
            script.setExecutable(true, true)
            zeptun?.copyTo(File(ctrl, "zeptun"), overwrite = true)
            hev?.copyTo(File(ctrl, "hev"), overwrite = true)
        }
        if (staged.isFailure) {
            println("[$LABEL] could not stage the helper: ${staged.exceptionOrNull()?.message}")
            ctrl.deleteRecursively()
            return null
        }
        val args = listOf(
            "/bin/sh", script.absolutePath,
            if (zeptun != null) File(ctrl, "zeptun").absolutePath else "-",
            ctrl.absolutePath,
            ProcessHandle.current().pid().toString(),
            if (hev != null) File(ctrl, "hev").absolutePath else "-",
        )
        val command = if (passwordlessSudo()) {
            println("[$LABEL] using sudo, which needs no password here")
            listOf("sudo", "-n") + args
        } else {
            val shell = args.joinToString(" ") { shQuote(it) }
            val apple = "do shell script \"" + shell.replace("\\", "\\\\").replace("\"", "\\\"") +
                "\" with administrator privileges"
            listOf("osascript", "-e", apple)
        }
        val started = runCatching { ProcessBuilder(command).redirectErrorStream(true).start() }.getOrElse {
            println("[$LABEL] the helper did not start: ${it.message}")
            ctrl.deleteRecursively()
            return null
        }
        Thread {
            runCatching { started.inputStream.bufferedReader().forEachLine { if (it.isNotBlank()) println("[$LABEL] $it") } }
        }.apply { isDaemon = true; name = "tun-helper-output" }.start()
        val deadline = System.currentTimeMillis() + PROMPT_WAIT_SEC * 1000
        while (System.currentTimeMillis() < deadline) {
            if (File(ctrl, "helper").isFile) {
                process = started
                control = ctrl
                return ctrl
            }
            if (File(ctrl, "failed").isFile || !started.isAlive) break
            Thread.sleep(POLL_MS)
        }
        val why = runCatching { File(ctrl, "failed").readText().trim() }.getOrNull()
        println("[$LABEL] ${why ?: "the administrator prompt was dismissed or the helper stopped"}")
        started.destroy()
        ctrl.deleteRecursively()
        return null
    }

    private fun awaitReady(ctrl: File): Boolean {
        val deadline = System.currentTimeMillis() + READY_WAIT_SEC * 1000
        while (System.currentTimeMillis() < deadline) {
            if (File(ctrl, "ready").isFile) return true
            if (File(ctrl, "failed").isFile) {
                println("[$LABEL] ${runCatching { File(ctrl, "failed").readText().trim() }.getOrDefault("zeptun failed")}")
                return false
            }
            if (process?.isAlive != true) {
                println("[$LABEL] the helper stopped")
                return false
            }
            Thread.sleep(POLL_MS)
        }
        println("[$LABEL] zeptun did not come up in $READY_WAIT_SEC seconds")
        File(ctrl, "stop").writeText("stop")
        return false
    }

    private fun passwordlessSudo(): Boolean =
        runCatching { exec("sudo", "-n", "true", timeoutSec = 5).first == 0 }.getOrDefault(false)

    private fun shQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    internal fun script(): String = """
        |#!/bin/sh
        |PATH=/usr/bin:/bin:/usr/sbin:/sbin
        |export PATH
        |CTRL="${'$'}2"
        |APP_PID="${'$'}3"
        |TAB="${'$'}(printf '\t')"
        |PERSIST="${ZeptunTun.MAC_DNS_RESTORE}"
        |RUN=${'$'}(mktemp -d /tmp/zedsecure-tun.XXXXXX) || { echo "the helper has no private directory" >"${'$'}CTRL/failed"; exit 1; }
        |BACKUP="${'$'}RUN/dns-backup"
        |if [ "${'$'}1" != - ] && { ! cp "${'$'}1" "${'$'}RUN/zeptun" || ! chmod 700 "${'$'}RUN/zeptun"; }; then
        |  echo "zeptun could not be staged" >"${'$'}CTRL/failed"
        |  rm -rf "${'$'}RUN"
        |  exit 1
        |fi
        |if [ -n "${'$'}4" ] && [ "${'$'}4" != - ] && { ! cp "${'$'}4" "${'$'}RUN/hev" || ! chmod 700 "${'$'}RUN/hev"; }; then
        |  echo "hev could not be staged" >"${'$'}CTRL/failed"
        |  rm -rf "${'$'}RUN"
        |  exit 1
        |fi
        |ZP=
        |TUNIF=
        |TUN_DNS=${ZeptunTun.MAC_TUN_DNS}
        |restore_dns() {
        |  [ -f "${'$'}1" ] || return 0
        |  while IFS="${'$'}TAB" read -r svc servers; do
        |    [ -n "${'$'}svc" ] || continue
        |    if [ "${'$'}servers" = "Empty" ]; then
        |      networksetup -setdnsservers "${'$'}svc" Empty >/dev/null 2>&1
        |    else
        |      networksetup -setdnsservers "${'$'}svc" ${'$'}servers >/dev/null 2>&1
        |    fi
        |  done < "${'$'}1"
        |  rm -f "${'$'}1"
        |  dscacheutil -flushcache >/dev/null 2>&1
        |  killall -HUP mDNSResponder >/dev/null 2>&1
        |}
        |point_dns_at_tunnel() {
        |  : > "${'$'}BACKUP"
        |  networksetup -listallnetworkservices 2>/dev/null | tail -n +2 | grep -v '^\*' | while IFS= read -r svc; do
        |    [ -n "${'$'}svc" ] || continue
        |    cur=${'$'}(networksetup -getdnsservers "${'$'}svc" 2>/dev/null | grep -E '^[0-9A-Fa-f:.]+${'$'}' | tr '\n' ' ' | sed 's/ *${'$'}//')
        |    [ -n "${'$'}cur" ] || cur=Empty
        |    printf '%s\t%s\n' "${'$'}svc" "${'$'}cur" >> "${'$'}BACKUP"
        |  done
        |  mkdir -p "${'$'}(dirname "${'$'}PERSIST")" && cp "${'$'}BACKUP" "${'$'}PERSIST"
        |  while IFS="${'$'}TAB" read -r svc servers; do
        |    networksetup -setdnsservers "${'$'}svc" "${'$'}TUN_DNS" >/dev/null 2>&1
        |  done < "${'$'}BACKUP"
        |  dscacheutil -flushcache >/dev/null 2>&1
        |  killall -HUP mDNSResponder >/dev/null 2>&1
        |}
        |reclaim_routes() {
        |  # auto_route punches 0.0.0.0/1 + 128.0.0.0/1 (and the v6 equivalents) so the tunnel
        |  # outranks the real default route. macOS does not retract these on its own when the
        |  # tun goes away (or is killed before it retracts them itself), so every one of them
        |  # left behind blackholes ALL traffic until a reboot clears the routing table. Always
        |  # sweep them here instead of trusting the engine's own shutdown timing.
        |  route -n delete -net 0.0.0.0/1 >/dev/null 2>&1
        |  route -n delete -net 128.0.0.0/1 >/dev/null 2>&1
        |  route -n delete -inet6 ::/1 >/dev/null 2>&1
        |  route -n delete -inet6 8000::/1 >/dev/null 2>&1
        |  stale=${'$'}(ifconfig 2>/dev/null | awk '/^utun[0-9]+:/{dev=substr(${'$'}1,1,length(${'$'}1)-1)} ${'$'}1=="inet" && ${'$'}2=="'"${ZeptunTun.TUN_V4}"'"{print dev}')
        |  for u in ${'$'}stale; do ifconfig "${'$'}u" destroy >/dev/null 2>&1; done
        |}
        |tun_down() {
        |  restore_dns "${'$'}BACKUP"
        |  rm -f "${'$'}PERSIST"
        |  if [ -f "${'$'}RUN/routes" ]; then
        |    while IFS= read -r spec; do
        |      [ -n "${'$'}spec" ] && route -n delete ${'$'}spec >/dev/null 2>&1
        |    done < "${'$'}RUN/routes"
        |    rm -f "${'$'}RUN/routes"
        |  fi
        |  if [ -n "${'$'}ZP" ]; then
        |    kill "${'$'}ZP" 2>/dev/null
        |    i=0
        |    while kill -0 "${'$'}ZP" 2>/dev/null && [ ${'$'}i -lt 30 ]; do sleep 0.1; i=${'$'}((i + 1)); done
        |    kill -9 "${'$'}ZP" 2>/dev/null
        |    wait "${'$'}ZP" 2>/dev/null
        |  fi
        |  ZP=
        |  TUNIF=
        |  rm -f "${'$'}CTRL/ready"
        |  reclaim_routes
        |}
        |tun_up() {
        |  tun_down
        |  rm -f "${'$'}CTRL/failed" "${'$'}CTRL/lost"
        |  if [ "${'$'}(cat "${'$'}CTRL/engine" 2>/dev/null)" = hev ]; then
        |    hev_up
        |  else
        |    zeptun_up
        |  fi
        |}
        |hev_up() {
        |  TUN_DNS=${'$'}(cat "${'$'}CTRL/dns" 2>/dev/null)
        |  case ${'$'}TUN_DNS in ''|*[!0-9.]*) TUN_DNS=${ZeptunTun.DEFAULT_DNS} ;; esac
        |  if [ ! -x "${'$'}RUN/hev" ]; then
        |    echo "hev is not part of this helper" >"${'$'}CTRL/failed"
        |    return
        |  fi
        |  if ! cp "${'$'}CTRL/hev.yml" "${'$'}RUN/hev.yml" 2>/dev/null; then
        |    echo "the tunnel settings are missing" >"${'$'}CTRL/failed"
        |    return
        |  fi
        |  "${'$'}RUN/hev" "${'$'}RUN/hev.yml" >"${'$'}CTRL/zeptun.log" 2>&1 &
        |  ZP=${'$'}!
        |  i=0
        |  while [ ${'$'}i -lt 150 ]; do
        |    kill -0 "${'$'}ZP" 2>/dev/null || break
        |    TUNIF=${'$'}(ifconfig | awk '/^[a-z]/ { n = ${'$'}1; sub(":", "", n) } index(${'$'}0, "inet ${ZeptunTun.TUN_V4} ") { print n; exit }')
        |    [ -n "${'$'}TUNIF" ] && break
        |    sleep 0.2
        |    i=${'$'}((i + 1))
        |  done
        |  if [ -z "${'$'}TUNIF" ]; then
        |    echo "hev did not bring the tunnel up. ${'$'}(tail -n 5 "${'$'}CTRL/zeptun.log" 2>/dev/null | tr '\n' ' ')" >"${'$'}CTRL/failed"
        |    tun_down
        |    return
        |  fi
        |  : > "${'$'}RUN/routes"
        |  GW=${'$'}(route -n get default 2>/dev/null | awk '/gateway:/ { print ${'$'}2; exit }')
        |  if [ -n "${'$'}GW" ] && [ -f "${'$'}CTRL/bypass" ]; then
        |    while IFS= read -r ip; do
        |      case ${'$'}ip in ''|*[!0-9.]*) continue ;; esac
        |      route -n add -host "${'$'}ip" "${'$'}GW" >/dev/null 2>&1 && echo "-host ${'$'}ip" >> "${'$'}RUN/routes"
        |    done < "${'$'}CTRL/bypass"
        |  fi
        |  for net in 0.0.0.0/1 128.0.0.0/1; do
        |    if ! route -n add -net "${'$'}net" -interface "${'$'}TUNIF" >/dev/null 2>&1; then
        |      echo "macOS refused the tunnel route ${'$'}net" >"${'$'}CTRL/failed"
        |      tun_down
        |      return
        |    fi
        |    echo "-net ${'$'}net" >> "${'$'}RUN/routes"
        |  done
        |  for net in ::/1 8000::/1; do
        |    route -n add -inet6 -net "${'$'}net" -interface "${'$'}TUNIF" >/dev/null 2>&1 && echo "-inet6 -net ${'$'}net" >> "${'$'}RUN/routes"
        |  done
        |  point_dns_at_tunnel
        |  echo "${'$'}ZP" >"${'$'}CTRL/ready"
        |}
        |zeptun_up() {
        |  TUN_DNS=${ZeptunTun.MAC_TUN_DNS}
        |  if [ ! -x "${'$'}RUN/zeptun" ]; then
        |    echo "zeptun is not part of this helper" >"${'$'}CTRL/failed"
        |    return
        |  fi
        |  if ! cp "${'$'}CTRL/zeptun.toml" "${'$'}RUN/zeptun.toml" 2>/dev/null; then
        |    echo "the tunnel settings are missing" >"${'$'}CTRL/failed"
        |    return
        |  fi
        |  "${'$'}RUN/zeptun" run -c "${'$'}RUN/zeptun.toml" >"${'$'}CTRL/zeptun.log" 2>&1 &
        |  ZP=${'$'}!
        |  UP=
        |  i=0
        |  while [ ${'$'}i -lt 150 ]; do
        |    kill -0 "${'$'}ZP" 2>/dev/null || break
        |    if ifconfig | grep -q 'inet ${ZeptunTun.TUN_V4} '; then UP=1; break; fi
        |    sleep 0.2
        |    i=${'$'}((i + 1))
        |  done
        |  if [ -z "${'$'}UP" ]; then
        |    echo "zeptun did not bring the tunnel up. ${'$'}(tail -n 5 "${'$'}CTRL/zeptun.log" 2>/dev/null | tr '\n' ' ')" >"${'$'}CTRL/failed"
        |    tun_down
        |    return
        |  fi
        |  point_dns_at_tunnel
        |  echo "${'$'}ZP" >"${'$'}CTRL/ready"
        |}
        |cleanup() {
        |  trap '' INT TERM HUP
        |  tun_down
        |  rm -rf "${'$'}RUN"
        |  rm -f "${'$'}CTRL/helper"
        |}
        |trap cleanup EXIT
        |trap 'exit 0' INT TERM HUP
        |restore_dns "${'$'}PERSIST"
        |reclaim_routes
        |echo "${'$'}${'$'}" >"${'$'}CTRL/helper"
        |while kill -0 "${'$'}APP_PID" 2>/dev/null && [ ! -e "${'$'}CTRL/exit" ]; do
        |  if [ -e "${'$'}CTRL/start" ]; then
        |    rm -f "${'$'}CTRL/start"
        |    tun_up
        |  fi
        |  if [ -e "${'$'}CTRL/stop" ]; then
        |    tun_down
        |    rm -f "${'$'}CTRL/stop"
        |  fi
        |  if [ -n "${'$'}ZP" ] && ! kill -0 "${'$'}ZP" 2>/dev/null; then
        |    tun_down
        |    echo "zeptun stopped" >"${'$'}CTRL/lost"
        |  fi
        |  sleep 0.25
        |done
        |exit 0
        |""".trimMargin()
}
