package dev.cluvex.zedsecure.desktop.core

import dev.cluvex.zedsecure.desktop.platform.DesktopXray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.net.InetSocketAddress
import java.net.StandardProtocolFamily
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LiveTunTest {
    private val os = Os.current

    @Test
    fun `zeptun carries traffic and DNS through the core and leaves nothing behind`() {
        assumeTrue(System.getenv("ZEDSECURE_LIVE_TUN") == "1" && (os == Os.WINDOWS || os == Os.MACOS))
        val work = Files.createTempDirectory("live-tun").toFile()
        val iface = assertNotNull(PhysicalInterface.detect(), "no network adapter carries the default route")
        println("physical adapter: $iface")
        val dnsBefore = systemDns()
        println("system DNS before: $dnsBefore")
        val zeptun = assertNotNull(ZeptunBinary.extract(work), "zeptun is not bundled")
        val socksPort = ServerSocket(0).use { it.localPort }
        val access = File(work, "access.log")
        val config = """
            {"log":{"loglevel":"info","access":${JsonPrimitive(access.absolutePath)}},
             "inbounds":[{"tag":"socks","listen":"127.0.0.1","port":$socksPort,"protocol":"socks","settings":{"udp":true}}],
             "outbounds":[{"tag":"direct","protocol":"freedom"}]}
        """.trimIndent()
        val core = XrayCore(work)
        val tun = ZeptunTun(zeptun, socksPort, work, includeOnly = listOf("1.1.1.1/32"))
        try {
            assertTrue(core.start(DesktopXray.bindOutbounds(config, iface)), "xray did not start")
            assertTrue(tun.start(), "zeptun did not bring the adapter up")

            val trace = fetch("1.1.1.1", "/cdn-cgi/trace")
            assertTrue("h=1.1.1.1" in trace, trace)
            awaitInLog(access, "1.1.1.1:80", "the request did not pass through the core")

            val name = "zedsecure-tun-${System.nanoTime()}.cloudflare.com"
            runCatching { InetAddress.getByName(name) }
            awaitInLog(access, ":53", "the system resolver did not ask through the tunnel")
        } catch (e: Throwable) {
            dumpNetwork(access)
            throw e
        } finally {
            tun.stop()
            core.stop()
        }
        assertTrue(adapterGone(), "the tunnel adapter is still there")
        assertEquals(dnsBefore, systemDns(), "the system DNS was not put back")
        work.deleteRecursively()
    }

    private fun awaitInLog(log: File, needle: String, message: String) {
        val deadline = System.currentTimeMillis() + 8_000
        while (needle !in log.readTextOrEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(200)
        assertTrue(needle in log.readTextOrEmpty(), "$message: ${log.readTextOrEmpty()}")
    }

    private fun dumpNetwork(access: File) {
        println("---- xray access log ----\n${access.readTextOrEmpty()}")
        val commands = when (os) {
            Os.WINDOWS -> listOf(
                listOf("powershell.exe", "-NoProfile", "-Command", "Get-NetIPAddress | Format-Table -AutoSize | Out-String -Width 200"),
                listOf("powershell.exe", "-NoProfile", "-Command", "Get-NetRoute -AddressFamily IPv4 | Format-Table -AutoSize | Out-String -Width 200"),
                listOf("powershell.exe", "-NoProfile", "-Command", "Get-DnsClientServerAddress | Format-Table -AutoSize | Out-String -Width 200"),
            )
            else -> listOf(listOf("ifconfig"), listOf("netstat", "-rn", "-f", "inet"), listOf("scutil", "--dns"))
        }
        commands.forEach { cmd -> println("---- ${cmd.joinToString(" ")} ----\n${exec(*cmd.toTypedArray(), timeoutSec = 30).second}") }
    }

    private fun adapterGone(): Boolean = when (os) {
        Os.WINDOWS -> exec(
            "powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
            "Get-NetIPAddress -IPAddress '${ZeptunTun.TUN_V4}' -ErrorAction SilentlyContinue | Select-Object -ExpandProperty InterfaceAlias",
            timeoutSec = 30,
        ).second.isBlank()
        else -> "inet ${ZeptunTun.TUN_V4} " !in exec("ifconfig", timeoutSec = 10).second
    }

    private fun systemDns(): String = when (os) {
        Os.MACOS -> SystemProxy.macServices().joinToString("; ") { service ->
            "$service=" + exec("/usr/sbin/networksetup", "-getdnsservers", service, timeoutSec = 10).second.trim()
        }
        else -> ""
    }

    private fun File.readTextOrEmpty(): String = runCatching { readText() }.getOrDefault("")

    private fun fetch(host: String, path: String): String {
        var last: Exception? = null
        repeat(4) {
            try {
                SocketChannel.open(StandardProtocolFamily.INET).use { channel ->
                    channel.socket().soTimeout = 15_000
                    channel.connect(InetSocketAddress(InetAddress.getByName(host), 80))
                    val request = "GET $path HTTP/1.1\r\nHost: $host\r\nUser-Agent: zedsecure-test\r\nConnection: close\r\n\r\n"
                    channel.write(ByteBuffer.wrap(request.toByteArray()))
                    return Channels.newInputStream(channel).readBytes().decodeToString()
                }
            } catch (e: Exception) {
                last = e
                Thread.sleep(2_000)
            }
        }
        throw last!!
    }
}
