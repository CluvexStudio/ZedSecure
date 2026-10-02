package dev.cluvex.zedsecure.desktop.core

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ZeptunTunTest {
    private val work = Files.createTempDirectory("zeptun").toFile()

    private fun tun(os: Os, bypass: List<String> = emptyList(), dns: String = "1.1.1.1", udpOverTcp: Boolean = false) =
        ZeptunTun(File(work, "zeptun"), 10808, work, bypassIps = bypass, udpOverTcp = udpOverTcp, dnsServers = dns, os = os)

    @Test
    fun `zeptun routes everything into the adapter and keeps the servers out of it`() {
        val toml = tun(Os.WINDOWS, bypass = listOf("203.0.113.7", "2001:db8::7", "not-an-ip", "203.0.113.7")).toml()

        assertTrue("name = \"ZedSecure\"" in toml, toml)
        assertTrue("server = \"127.0.0.1:10808\"" in toml, toml)
        assertTrue("auto_route = true" in toml, toml)
        assertTrue("exclude = [\"203.0.113.7/32\", \"2001:db8::7/128\"]" in toml, toml)
        assertTrue("hijack = true" in toml && "upstream = \"1.1.1.1:53\"" in toml, toml)
        assertTrue("address = [\"172.19.0.1/30\", \"fdfe:dcba:9876::1/126\"]" in toml, toml)
        assertFalse("strict" in toml, "strict route would also block the core's own DNS to the domestic resolver")
    }

    @Test
    fun `macOS lets the kernel pick the utun name and UDP over TCP is passed on`() {
        val toml = tun(Os.MACOS, udpOverTcp = true).toml()
        assertFalse("name =" in toml, toml)
        assertTrue("udp_mode = \"tcp\"" in toml, toml)
        assertFalse("exclude" in toml, toml)
    }

    @Test
    fun `the DNS upstream is the first usable address of the VPN DNS setting`() {
        assertEquals("8.8.8.8:53", ZeptunTun.dnsUpstream("8.8.8.8, 1.1.1.1"))
        assertEquals("[2606:4700:4700::1111]:53", ZeptunTun.dnsUpstream("2606:4700:4700::1111"))
        assertEquals("1.1.1.1:53", ZeptunTun.dnsUpstream("dns.example.com"))
        assertEquals("1.1.1.1:53", ZeptunTun.dnsUpstream(""))
        assertEquals("9.9.9.9:5353", ZeptunTun.dnsUpstream("9.9.9.9:5353"))
        assertEquals("[2606:4700::1111]:53", ZeptunTun.dnsUpstream("[2606:4700::1111]:53"))
        assertEquals("1.0.0.1:53", ZeptunTun.dnsUpstream("https://dns.example.com/dns-query, 1.0.0.1"))
        assertNull(ZeptunTun.hostPrefix("server.example.com"))
    }

    @Test
    fun `the macOS helper is valid shell and waits for the tunnel address`() {
        val script = File(work, "helper.sh").apply { writeText(tun(Os.MACOS).macHelper()) }
        val check = ProcessBuilder("sh", "-n", script.absolutePath).redirectErrorStream(true).start()
        val out = check.inputStream.bufferedReader().readText()
        assertEquals(0, check.waitFor(), out)
        val text = script.readText()
        assertTrue("inet 172.19.0.1 " in text && "\$STATE/ready" in text && "kill -0 \"\$APP_PID\"" in text, text)
    }

    @Test
    fun `the Windows helper takes its paths as parameters and reports through files`() {
        val ps = tun(Os.WINDOWS).windowsHelper()
        assertTrue(ps.startsWith("param([string]\$Zeptun, [string]\$Config, [string]\$State, [int]\$AppPid)"), ps)
        assertTrue("Get-NetIPAddress -IPAddress '172.19.0.1'" in ps, ps)
        assertTrue("Test-Path -LiteralPath \$stop" in ps && "Get-Process -Id \$AppPid" in ps, ps)
        assertTrue("Stop-Process -Id \$p.Id -Force" in ps, ps)
    }

    @Test
    fun `the default route interface is read from route and Find-NetRoute output`() {
        val mac = """
               route to: default
            destination: default
                   mask: default
                gateway: 192.168.1.1
              interface: en0
                  flags: <UP,GATEWAY,DONE,STATIC,PRCLONING>
        """.trimIndent()
        assertEquals("en0", PhysicalInterface.macInterface(mac))
        assertNull(PhysicalInterface.macInterface("interface: utun4"))
        assertEquals("Wi-Fi", PhysicalInterface.firstLine("\r\nWi-Fi\r\n"))
    }
}
