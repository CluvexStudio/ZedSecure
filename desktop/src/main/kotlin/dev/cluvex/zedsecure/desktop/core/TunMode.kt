package dev.cluvex.zedsecure.desktop.core

import java.io.File

class TunMode(
    private val hevBinary: File,
    private val socksHost: String,
    private val socksPort: Int,
    private val workDir: File,

    private val bypassIps: List<String> = emptyList(),

    private val udpOverTcp: Boolean = false,

    private val askPassword: ((retry: Boolean) -> CharArray?)? = null,
) : DesktopTun {
    private var helper: LinuxRootHelper? = null
    private var privateDir: File? = null

    val tunName = "tun0"

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
        val dir = LinuxRootHelper.privateDir(workDir, "zedsecure-tun-", "tun") ?: return false
        privateDir = dir
        val hev = File(dir, hevBinary.name)
        runCatching { hevBinary.copyTo(hev, overwrite = true); hev.setExecutable(true, true) }
            .onFailure { println("[tun] could not stage hev: ${it.message}"); return false }
        val script = writeLinuxScript(dir, hev, writeConfig(dir))
        val root = LinuxRootHelper(dir, askPassword, "tun")
        if (!root.start(script)) return false
        helper = root
        return true
    }

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

    companion object {
        fun supported(os: Os = Os.current): Boolean = os == Os.LINUX || os == Os.WINDOWS || os == Os.MACOS

        fun usesZeptun(os: Os = Os.current): Boolean = os == Os.WINDOWS || os == Os.MACOS

        private const val READY_MARKER = LinuxRootHelper.READY_MARKER
        private const val SAFE_PATH = LinuxRootHelper.SAFE_PATH
    }

    override fun stop() {
        helper?.stop()
        helper = null
        privateDir?.let { dir -> runCatching { dir.deleteRecursively() } }
        privateDir = null
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
