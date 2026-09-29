package dev.cluvex.zedsecure.desktop

import dev.cluvex.zedsecure.desktop.core.HevBinary
import dev.cluvex.zedsecure.desktop.core.Os
import dev.cluvex.zedsecure.desktop.core.SystemProxy
import dev.cluvex.zedsecure.desktop.core.TunMode
import dev.cluvex.zedsecure.desktop.core.XrayCore
import java.io.File

fun main(args: Array<String>) {
    val work = File(System.getProperty("java.io.tmpdir"), "zedsecure").apply { mkdirs() }
    when (args.getOrNull(0)) {
        "system" -> {
            val (h, p) = hostPort(args) ?: return usage()
            println(if (SystemProxy.set(h, p)) "System SOCKS proxy → $h:$p (${Os.current})" else "Failed to set system proxy")
        }
        "clear" -> println(if (SystemProxy.clear()) "System proxy cleared" else "Failed to clear")
        "run" -> {
            val cfgPath = args.getOrNull(1) ?: return usage()
            val port = args.getOrNull(2)?.toIntOrNull() ?: return usage()
            val mode = args.getOrNull(3) ?: "system"
            val cfg = File(cfgPath)
            if (!cfg.isFile) return println("config not found: $cfgPath")
            val xray = XrayCore(work)
            if (!xray.start(cfg.readText())) return println("xray core failed to start")
            println("xray core up; SOCKS on 127.0.0.1:$port")
            val tun = if (mode == "tun") {
                val hev = HevBinary.extract(work) ?: return println("no hev binary for ${Os.current}")
                TunMode(hev, "127.0.0.1", port, work).also {
                    if (!it.start()) { xray.stop(); return println("TUN elevation failed") }
                }
            } else {
                if (!SystemProxy.set("127.0.0.1", port)) { xray.stop(); return println("system proxy failed") }
                println("System proxy → 127.0.0.1:$port"); null
            }
            Runtime.getRuntime().addShutdownHook(Thread { tun?.stop(); SystemProxy.clear(); xray.stop() })
            println("Connected ($mode). Ctrl+C to stop.")
            Thread.currentThread().join()
        }
        "tun" -> {
            val (h, p) = hostPort(args) ?: return usage()
            val hev = HevBinary.extract(work) ?: return println("No hev binary bundled for ${Os.current}")
            val tun = TunMode(hev, h, p, work)
            if (tun.start()) {
                println("TUN mode starting (approve the admin prompt). Ctrl+C to stop.")
                Runtime.getRuntime().addShutdownHook(Thread { tun.stop(); SystemProxy.clear() })
                Thread.currentThread().join()
            } else {
                println("TUN mode failed to start (elevation denied?)")
            }
        }
        else -> usage()
    }
}

private fun hostPort(args: Array<String>): Pair<String, Int>? {
    val h = args.getOrNull(1) ?: return null
    val p = args.getOrNull(2)?.toIntOrNull() ?: return null
    return h to p
}

private fun usage() {
    println(
        """
        ZedSecure desktop core (${Os.current})
          run <config.json> <socksPort> [system|tun]  run our xray core + route (self-contained)
          system <host> <port>   set system SOCKS proxy (no admin)
          tun    <host> <port>   TUN mode via hev (asks for admin)
          clear                  restore direct connection
        """.trimIndent(),
    )
}
