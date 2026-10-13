package dev.cluvex.zedsecure.desktop.core

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.Socket
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

fun main(args: Array<String>) = WindowsHelper.serve(args.toList())

object WindowsHelper {
    const val FLAG = "--zedsecure-helper"
    const val HELLO = "HELLO"

    fun serve(args: List<String>) {
        val port = args.getOrNull(0)?.toIntOrNull() ?: exitProcess(2)
        val token = args.getOrNull(1)?.let { runCatching { File(it).readText().trim() }.getOrNull() } ?: exitProcess(2)
        val socket = runCatching { Socket(InetAddress.getLoopbackAddress(), port) }.getOrElse { exitProcess(3) }
        socket.tcpNoDelay = true
        val out = PrintWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8), true)
        val input = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
        val jobs = Jobs(out)
        jobs.clearLeftovers()
        out.println("$HELLO $token")
        try {
            while (true) {
                val line = runCatching { input.readLine() }.getOrNull() ?: break
                val reply = runCatching { jobs.handle(line.trim()) }.getOrElse { "ERR " + (it.message ?: "failed") }
                synchronized(out) { out.println(reply.replace('\n', ' ').replace('\r', ' ')) }
            }
        } finally {
            jobs.stopAll()
            runCatching { socket.close() }
        }
        exitProcess(0)
    }

    private class Jobs(private val out: PrintWriter) {
        @Volatile private var tun: Process? = null
        @Volatile private var tunDir: File? = null
        @Volatile private var oc: Process? = null
        @Volatile private var ocDir: File? = null
        @Volatile private var hev: Process? = null
        private val bypassRoutes = mutableListOf<List<String>>()

        fun handle(line: String): String {
            val command = line.substringBefore(' ')
            val rest = line.substringAfter(' ', "").trim()
            return when (command) {
                "TUN_UP" -> tunUp(File(rest))
                "TUN_DOWN" -> { stopTun(); "OK" }
                "HEV_UP" -> hevUp(File(rest))
                "HEV_DOWN" -> { stopHev(); "OK" }
                "OC_UP" -> ocUp(File(rest))
                "OC_DOWN" -> { stopOc(); "OK" }
                "CA_TRUST" -> trustCa(File(rest))
                "PING" -> "OK"
                else -> "ERR unknown command"
            }
        }

        fun stopAll() {
            stopOc()
            stopTun()
            stopHev()
        }

        private fun hevUp(dir: File): String {
            stopHev()
            clearNrpt()
            val exe = File(dir, "hev-socks5-tunnel.exe")
            val config = File(dir, "hev.yml")
            val plan = runCatching { java.util.Properties().apply { File(dir, "tun.properties").inputStream().use { load(it) } } }.getOrNull()
            if (!exe.isFile || !config.isFile || plan == null) return "ERR hev is missing from ${dir.path}"
            fun list(key: String) = plan.getProperty(key).orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val address = plan.getProperty("address").orEmpty()
            val dns = list("dns").filter(::isIpv4)
            val routes = list("routes")
            val routes6 = list("routes6")
            val ways = list("bypass").filter(::isIpv4).mapNotNull { ip -> WinApi.bestRoute(ip)?.let { ip to it } }
            val log = File(dir, "hev.log")
            val p = ProcessBuilder(exe.path, config.path)
                .directory(dir)
                .redirectOutput(File(dir, "hev.out"))
                .redirectError(log)
                .start()
            hev = p
            val deadline = System.currentTimeMillis() + TUN_WAIT_MS
            var adapter: NetworkInterface? = null
            while (System.currentTimeMillis() < deadline) {
                if (!p.isAlive) break
                adapter = adapterWith(address)
                if (adapter != null) break
                Thread.sleep(POLL_MS)
            }
            if (adapter == null) {
                val why = (tail(log) + " " + tail(File(dir, "hev.out"))).trim()
                stopHev()
                return "ERR hev did not bring the tunnel up. $why".trim()
            }
            val index = adapter.index.toString()
            runCommand("netsh", "interface", "ipv4", "set", "interface", "interface=$index", "metric=1")
            val missing = routes.filterNot { netsh("interface", "ipv4", "add", "route", "prefix=$it", "interface=$index", "metric=1", "store=active") }
            if (missing.isNotEmpty()) {
                stopHev()
                return "ERR Windows refused the tunnel routes ${missing.joinToString()}"
            }
            if (routes6.isNotEmpty()) {
                runCommand("netsh", "interface", "ipv6", "set", "interface", "interface=$index", "metric=1")
                routes6.forEach { netsh("interface", "ipv6", "add", "route", "prefix=$it", "interface=$index", "metric=1", "store=active") }
            }
            ways.forEach { (ip, way) ->
                val args = listOf("prefix=$ip/32", "interface=${way.interfaceIndex}", "nexthop=${way.nextHop}")
                if (netsh(*(listOf("interface", "ipv4", "add", "route") + args + listOf("metric=1", "store=active")).toTypedArray())) {
                    synchronized(bypassRoutes) { bypassRoutes += args }
                }
            }
            if (dns.isNotEmpty()) {
                netsh("interface", "ipv4", "set", "dnsservers", "name=$index", "source=static", "address=${dns.first()}", "register=none", "validate=no")
                dns.drop(1).forEachIndexed { i, server ->
                    netsh("interface", "ipv4", "add", "dnsservers", "name=$index", "address=$server", "index=${i + 2}", "validate=no")
                }
                WinApi.writeMachineKey(
                    NRPT_KEY,
                    mapOf(
                        "Version" to WinApi.RegValue.Number(1),
                        "Name" to WinApi.RegValue.Texts(listOf(".")),
                        "GenericDNSServers" to WinApi.RegValue.Text(dns.joinToString(";")),
                        "ConfigOptions" to WinApi.RegValue.Number(8),
                    ),
                )
            }
            runCommand("ipconfig", "/flushdns")
            Thread { runCommand("ipconfig", "/registerdns") }.apply { isDaemon = true }.start()
            watch(p, "tun") { hev === p }
            return "OK"
        }

        private fun stopHev() {
            val p = hev
            hev = null
            clearNrpt()
            val routes = synchronized(bypassRoutes) { bypassRoutes.toList().also { bypassRoutes.clear() } }
            routes.forEach { args -> netsh(*(listOf("interface", "ipv4", "delete", "route") + args).toTypedArray()) }
            if (p != null) {
                stop(p)
                runCommand("ipconfig", "/flushdns")
            }
        }

        fun clearLeftovers() = clearNrpt()

        private fun clearNrpt() {
            runCatching { WinApi.deleteMachineKey(NRPT_KEY) }
        }

        private fun adapterWith(address: String): NetworkInterface? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList().firstOrNull { nic ->
                nic.isUp && nic.inetAddresses.toList().any { it.hostAddress == address }
            }
        }.getOrNull()

        private fun isIpv4(value: String) = Regex("""\d{1,3}(\.\d{1,3}){3}""").matches(value)

        private fun netsh(vararg args: String): Boolean = runCommand("netsh", *args) == 0

        private fun tunUp(dir: File): String {
            stopTun()
            val exe = File(dir, "zeptun.exe")
            val config = File(dir, "zeptun.toml")
            if (!exe.isFile || !config.isFile) return "ERR zeptun is missing from ${dir.path}"
            val log = File(dir, "zeptun.log")
            val p = ProcessBuilder(exe.path, "run", "-c", config.path)
                .directory(dir)
                .redirectOutput(File(dir, "zeptun.out"))
                .redirectError(log)
                .start()
            tun = p
            tunDir = dir
            val deadline = System.currentTimeMillis() + TUN_WAIT_MS
            var adapter: NetworkInterface? = null
            while (System.currentTimeMillis() < deadline) {
                if (!p.isAlive) break
                adapter = tunnelAdapter()
                if (adapter != null) break
                Thread.sleep(POLL_MS)
            }
            if (adapter == null) {
                val why = tail(log)
                stopTun()
                return "ERR zeptun did not bring the tunnel up. $why".trim()
            }
            listOf("ipv4", "ipv6").forEach { family ->
                runCommand("netsh", "interface", family, "set", "interface", adapter.index.toString(), "metric=1")
            }
            watch(p, "tun") { tun === p }
            return "OK"
        }

        private fun ocUp(dir: File): String {
            stopOc()
            val lines = runCatching { File(dir, "oc.args").readLines() }.getOrNull()?.filter { it.isNotEmpty() }
            if (lines.isNullOrEmpty() || !File(lines.first()).isFile) return "ERR OpenConnect is missing"
            val secret = File(dir, "secret")
            val log = File(dir, "openconnect.log")
            val output = File(dir, "openconnect.out")
            val builder = ProcessBuilder(lines)
                .directory(File(lines.first()).parentFile)
                .redirectOutput(output)
                .redirectError(log)
            if (secret.isFile) builder.redirectInput(secret)
            val p = builder.start()
            oc = p
            ocDir = dir
            val connected = Regex(DesktopOpenConnect.CONNECTED)
            val deadline = System.currentTimeMillis() + OC_WAIT_MS
            var up = false
            while (System.currentTimeMillis() < deadline && p.isAlive) {
                if (listOf(log, output).any { f -> runCatching { connected.containsMatchIn(f.readText()) }.getOrDefault(false) }) {
                    up = true
                    break
                }
                Thread.sleep(POLL_MS)
            }
            if (!up) {
                val why = (tail(log) + " " + tail(output, 3)).trim()
                stopOc()
                return "ERR OpenConnect did not connect. $why".trim()
            }
            runCatching { secret.delete() }
            File(dir, "ready").writeText(p.pid().toString())
            watch(p, "oc") { oc === p }
            return "OK"
        }

        private fun trustCa(file: File): String {
            val cert = file.inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) } as X509Certificate
            val store = KeyStore.getInstance("Windows-ROOT-LOCALMACHINE").apply { load(null, null) }
            if (store.getCertificateAlias(cert) == null) {
                val thumb = MessageDigest.getInstance("SHA-1").digest(cert.encoded).joinToString("") { "%02x".format(it) }
                store.setCertificateEntry("ZedSecure IKEv2 CA $thumb", cert)
            }
            return "OK"
        }

        private fun stopTun() {
            val p = tun ?: return
            tun = null
            stop(p)
            tunDir = null
        }

        private fun stopOc() {
            val p = oc ?: return
            oc = null
            stop(p)
            ocDir?.let { dir ->
                runCatching { File(dir, "ready").delete() }
                runCatching { File(dir, "secret").delete() }
            }
            ocDir = null
        }

        private fun stop(p: Process) {
            if (p.isAlive) runCatching { WinApi.sendCtrlC(p.pid(), p, STOP_WAIT_MS) }
            if (p.isAlive && !p.waitFor(1, TimeUnit.SECONDS)) {
                p.destroyForcibly()
                p.waitFor(5, TimeUnit.SECONDS)
            }
        }

        private fun watch(p: Process, name: String, current: () -> Boolean) {
            Thread {
                p.waitFor()
                if (current()) {
                    if (name == "oc") ocDir?.let { runCatching { File(it, "ready").delete() } }
                    synchronized(out) { out.println("DOWN $name") }
                }
            }.apply { isDaemon = true; this.name = "helper-watch-$name" }.start()
        }

        private fun tunnelAdapter(): NetworkInterface? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList().firstOrNull { nic ->
                nic.isUp && nic.inetAddresses.toList().any { it.hostAddress == ZeptunTun.TUN_V4 }
            }
        }.getOrNull()

        private fun tail(file: File, lines: Int = 5): String = runCatching {
            file.readLines().filter { it.isNotBlank() }.takeLast(lines).joinToString(" ")
        }.getOrDefault("")

        private fun runCommand(vararg cmd: String): Int = runCatching {
            val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
            p.inputStream.readAllBytes()
            if (!p.waitFor(15, TimeUnit.SECONDS)) {
                p.destroyForcibly()
                -1
            } else {
                p.exitValue()
            }
        }.getOrDefault(-1)
    }

    private const val NRPT_KEY =
        "SYSTEM\\CurrentControlSet\\Services\\Dnscache\\Parameters\\DnsPolicyConfig\\{6F1F6C2A-5B9E-4C1B-9A7E-2D5E3B8C4A10}"
    private const val TUN_WAIT_MS = 30_000L
    private const val OC_WAIT_MS = 90_000L
    private const val STOP_WAIT_MS = 10_000L
    private const val POLL_MS = 200L
}
