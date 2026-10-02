package dev.cluvex.zedsecure.desktop.core

import dev.cluvex.zedsecure.desktop.platform.DesktopXray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URI
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LiveTunTest {
    @Test
    fun `zeptun on Windows carries a plain connection through the core and removes its adapter afterwards`() {
        assumeTrue(System.getenv("ZEDSECURE_LIVE_TUN") == "1" && Os.current == Os.WINDOWS)
        val work = Files.createTempDirectory("live-tun").toFile()
        val iface = assertNotNull(PhysicalInterface.detect(), "no network adapter carries the default route")
        println("physical adapter: $iface")
        val zeptun = assertNotNull(ZeptunBinary.extract(work), "zeptun.exe is not bundled")
        val socksPort = ServerSocket(0).use { it.localPort }
        val access = File(work, "access.log")
        val config = """
            {"log":{"loglevel":"warning","access":${JsonPrimitive(access.absolutePath)}},
             "inbounds":[{"tag":"socks","listen":"127.0.0.1","port":$socksPort,"protocol":"socks","settings":{"udp":true}}],
             "outbounds":[{"tag":"direct","protocol":"freedom"}]}
        """.trimIndent()
        val core = XrayCore(work)
        val tun = ZeptunTun(zeptun, socksPort, work, includeOnly = listOf("1.1.1.1/32"))
        try {
            assertTrue(core.start(DesktopXray.bindOutbounds(config, iface)), "xray did not start")
            assertTrue(tun.start(), "zeptun did not bring the adapter up")
            val trace = fetch("http://1.1.1.1/cdn-cgi/trace")
            assertTrue("h=1.1.1.1" in trace, trace)
            val deadline = System.currentTimeMillis() + 5_000
            while ("1.1.1.1:80" !in access.readTextOrEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(200)
            assertTrue("1.1.1.1:80" in access.readTextOrEmpty(), "the request did not pass through the core: ${access.readTextOrEmpty()}")
        } finally {
            tun.stop()
            core.stop()
        }
        val left = exec(
            "powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
            "Get-NetIPAddress -IPAddress '${ZeptunTun.TUN_V4}' -ErrorAction SilentlyContinue | Select-Object -ExpandProperty InterfaceAlias",
            timeoutSec = 30,
        )
        assertTrue(left.second.isBlank(), "the adapter is still there: ${left.second}")
        work.deleteRecursively()
    }

    private fun File.readTextOrEmpty(): String = runCatching { readText() }.getOrDefault("")

    private fun fetch(url: String): String {
        var last: Exception? = null
        repeat(4) {
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            try {
                return connection.inputStream.bufferedReader().readText()
            } catch (e: Exception) {
                last = e
                Thread.sleep(2_000)
            } finally {
                connection.disconnect()
            }
        }
        throw last!!
    }
}
