package dev.cluvex.zedsecure.desktop.core

import java.io.File

class TunMode(
    private val hevBinary: File,
    private val socksHost: String,
    private val socksPort: Int,
    private val workDir: File,

    private val bypassIps: List<String> = emptyList(),

    private val udpOverTcp: Boolean = false,
) {
    private var process: Process? = null

    val tunName = when (Os.current) { Os.MACOS -> "utun123"; else -> "tun0" }

    private fun writeConfig(): File {
        val cfg = File(workDir, "hev-desktop.yaml")
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

    fun start(): Boolean {
        val cfg = writeConfig()
        return when (Os.current) {
            Os.LINUX -> startLinux(cfg)
            Os.WINDOWS -> startWindows(cfg)
            Os.MACOS -> startMac(cfg)
            else -> false
        }
    }

    private fun startLinux(cfg: File): Boolean {
        val bypassAdd = bypassIps.joinToString("\n") { ip ->
            "ip route add $ip/32 via \$ORIG_GW dev \$ORIG_DEV 2>/dev/null || true"
        }
        val bypassDel = bypassIps.joinToString("\n") { ip ->
            "ip route del $ip/32 via \$ORIG_GW dev \$ORIG_DEV 2>/dev/null || true"
        }
        val script = File(workDir, "tun-up.sh").apply {
            writeText(
                """
                #!/bin/sh
                ORIG_GW=${'$'}(ip route show default | awk '/default/ {print ${'$'}3; exit}')
                ORIG_DEV=${'$'}(ip route show default | awk '/default/ {print ${'$'}5; exit}')
                cleanup() {
                  ip route del default dev $tunName 2>/dev/null || true
                  $bypassDel
                  kill ${'$'}HEV 2>/dev/null || true
                }
                trap cleanup INT TERM EXIT
                $bypassAdd
                "${hevBinary.absolutePath}" "${cfg.absolutePath}" &
                HEV=${'$'}!
                for i in 1 2 3 4 5 6 7 8 9 10; do ip link show $tunName >/dev/null 2>&1 && break; sleep 0.3; done
                ip route add default dev $tunName metric 1 || true
                wait ${'$'}HEV
                """.trimIndent(),
            )
            setExecutable(true)
        }
        return launch("pkexec", "sh", script.absolutePath)
    }

    private fun startMac(cfg: File): Boolean {
        val script = File(workDir, "tun-up.sh").apply {
            writeText(
                """
                #!/bin/sh
                "${hevBinary.absolutePath}" "${cfg.absolutePath}" &
                HEV=$!
                sleep 2
                route -n add -net 0.0.0.0/1 -interface $tunName || true
                route -n add -net 128.0.0.0/1 -interface $tunName || true
                wait ${'$'}HEV
                """.trimIndent(),
            )
            setExecutable(true)
        }

        val inner = "sh ${script.absolutePath}"
        return launch("osascript", "-e", "do shell script \"$inner\" with administrator privileges")
    }

    private fun startWindows(cfg: File): Boolean {
        val ps = "Start-Process -FilePath '${hevBinary.absolutePath}' -ArgumentList '\"${cfg.absolutePath}\"' -Verb RunAs -WindowStyle Hidden"
        return launch("powershell", "-Command", ps)
    }

    private fun launch(vararg cmd: String): Boolean = try {
        process = ProcessBuilder(*cmd).redirectErrorStream(true).directory(workDir).start()
        Thread { runCatching { process?.inputStream?.bufferedReader()?.forEachLine { println("[tun] $it") } } }
            .apply { isDaemon = true }.start()
        true
    } catch (e: Exception) {
        System.err.println("tun elevation failed: ${e.message}")
        false
    }

    companion object {
        fun supported(os: Os = Os.current): Boolean = os == Os.LINUX
    }

    fun stop() {
        runCatching { process?.destroy() }
        when (Os.current) {
            Os.LINUX -> exec("pkexec", "pkill", "-f", "hev-socks5-tunnel")
            Os.MACOS -> exec("osascript", "-e", "do shell script \"pkill -f hev-socks5-tunnel\" with administrator privileges")
            Os.WINDOWS -> exec("taskkill", "/IM", "hev-socks5-tunnel.exe", "/F")
            else -> {}
        }
        process = null
    }
}
