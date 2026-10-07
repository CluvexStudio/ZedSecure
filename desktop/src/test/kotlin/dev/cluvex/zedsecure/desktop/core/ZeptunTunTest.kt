package dev.cluvex.zedsecure.desktop.core

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
        assertFalse("strict" in toml, "a zeptun without allow_app would block the core's own DNS to the domestic resolver")
        assertTrue("guid = \"${ZeptunTun.ADAPTER_GUID}\"" in toml, "one adapter identity, so Windows does not see a new network on every connect")
        assertTrue("[handler.direct]\nbind_interface = \"loopback\"" in toml, "zeptun must not pin its sockets to the network card, or it cannot reach the core on 127.0.0.1")
    }

    @Test
    fun `on Windows the strict firewall stops DNS leaks and still lets the core resolve directly`() {
        val xray = File("C:\\Users\\O\"Brien\\AppData\\Local\\Temp\\zedsecure\\xray.exe")
        val toml = ZeptunTun(File(work, "zeptun"), 10808, work, allowApps = listOf(xray, xray), os = Os.WINDOWS)
            .toml(strictFirewall = true)
        assertTrue("strict = true" in toml, toml)
        val expected = "allow_app = [\"" + xray.absolutePath.replace("\\", "\\\\").replace("\"", "\\\"") + "\"]"
        assertTrue(expected in toml, toml)
        assertTrue(toml.indexOf("strict = true") > toml.indexOf("[route]") && toml.indexOf("strict = true") < toml.indexOf("[dns]"), toml)
        assertEquals(listOf(1, 1, 2), ZeptunBinary.parseVersion("zeptun 1.1.2 zig 0.16.0 x86_64-windows"))
        assertNull(ZeptunBinary.parseVersion("zeptun: unknown command"))
        assertTrue(ZeptunBinary.compareVersions(listOf(1, 1, 1), listOf(1, 1, 2)) < 0)
        assertTrue(ZeptunBinary.compareVersions(listOf(1, 2, 0), listOf(1, 1, 2)) > 0)
    }

    @Test
    fun `macOS lets the kernel pick the utun name and UDP over TCP is passed on`() {
        val toml = tun(Os.MACOS, udpOverTcp = true).toml()
        assertFalse("name =" in toml, toml)
        assertFalse("guid" in toml, toml)
        assertTrue("bind_interface = \"lo0\"" in toml, toml)
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
        val lines = text.lines()
        val recover = lines.indexOf("restore_dns \"\$PERSIST\"")
        val launch = lines.indexOfFirst { it.startsWith("\"\$ZEPTUN\" run") }
        val point = lines.indexOf("point_dns_at_tunnel")
        val ready = lines.indexOfFirst { it.startsWith("echo \"\$ZP\" >") }
        assertTrue(recover in 0 until launch, "DNS left over from a crash is put back before anything else")
        assertTrue(point in (launch + 1) until ready, "DNS moves to the tunnel only once it is up")
        assertTrue("networksetup -setdnsservers \"\$svc\" ${ZeptunTun.MAC_TUN_DNS}" in text, text)
        assertTrue(Regex("cleanup\\(\\) \\{[^}]*restore_dns \"\\\$BACKUP\"").containsMatchIn(text), "disconnecting gives the old DNS back")
    }

    @Test
    fun `the packaged app runs itself as the administrator helper`() {
        val exe = File.createTempFile("ZedSecure", ".exe")
        val before = System.getProperty("jpackage.app-path")
        try {
            System.setProperty("jpackage.app-path", exe.absolutePath)
            val token = File(exe.parentFile, "helper token.txt")
            val (file, parameters) = assertNotNull(WindowsElevation.launchCommand(50123, token))
            assertEquals(exe.absolutePath, file)
            assertEquals("${WindowsHelper.FLAG} 50123 \"${token.absolutePath}\"", parameters, "a path with a space stays one argument")
            assertFalse("powershell" in parameters.lowercase())
        } finally {
            if (before == null) System.clearProperty("jpackage.app-path") else System.setProperty("jpackage.app-path", before)
            exe.delete()
        }
    }

    @Test
    fun `the administrator helper proves who it is, answers, and leaves when the app hangs up`() {
        val javaBin = File(System.getProperty("java.home"), if (Os.current == Os.WINDOWS) "bin/java.exe" else "bin/java")
        val classpath = listOf(WindowsHelper::class.java, Unit::class.java, com.sun.jna.Native::class.java)
            .map { File(it.protectionDomain.codeSource.location.toURI()).absolutePath }
            .distinct()
            .joinToString(File.pathSeparator)
        val token = File.createTempFile("helper-", ".token").apply { writeText("abc123") }
        java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress()).use { server ->
            server.soTimeout = 20_000
            val helper = ProcessBuilder(
                javaBin.absolutePath, "-cp", classpath, "dev.cluvex.zedsecure.desktop.core.WindowsHelperKt",
                server.localPort.toString(), token.absolutePath,
            ).redirectErrorStream(true).start()
            try {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    val writer = java.io.PrintWriter(socket.getOutputStream(), true)
                    assertEquals("${WindowsHelper.HELLO} abc123", reader.readLine())
                    writer.println("PING")
                    assertEquals("OK", reader.readLine())
                    writer.println("REBOOT now")
                    assertEquals("ERR unknown command", reader.readLine())
                    writer.println("TUN_UP ${token.parentFile.absolutePath}")
                    assertTrue(reader.readLine().startsWith("ERR zeptun is missing"))
                }
                assertTrue(helper.waitFor(15, java.util.concurrent.TimeUnit.SECONDS), "the helper must exit once the app closes the connection")
                assertEquals(0, helper.exitValue(), helper.inputStream.bufferedReader().readText())
            } finally {
                helper.destroyForcibly()
                token.delete()
            }
        }
    }

    @Test
    fun `the macOS system proxy covers every enabled network service`() {
        val listing = """
            An asterisk (*) denotes that a network service is disabled.
            USB 10/100/1000 LAN
            Wi-Fi
            *Thunderbolt Bridge
            iPhone USB
        """.trimIndent()
        assertEquals(listOf("USB 10/100/1000 LAN", "Wi-Fi", "iPhone USB"), SystemProxy.macServices(listing))
    }

    @Test
    fun `the default route interface is read from route output`() {
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
        assertEquals("wlp3s0", PhysicalInterface.linuxInterface("1.1.1.1 via 192.168.1.1 dev wlp3s0 src 192.168.1.5 uid 1000 \\    cache "))
        assertEquals("ppp0", PhysicalInterface.linuxInterface("1.1.1.1 dev ppp0 src 10.64.64.64 uid 1000"))
        assertNull(PhysicalInterface.linuxInterface("1.1.1.1 dev ${LinuxBroker.TUN_NAME} src 198.18.0.1 uid 1000"))
        assertTrue(PhysicalInterface.canBind(Os.LINUX, "6.8.0-45-generic"))
        assertTrue(PhysicalInterface.canBind(Os.LINUX, "5.7.0"))
        assertFalse(PhysicalInterface.canBind(Os.LINUX, "5.4.0-200-generic"), "older kernels refuse SO_BINDTODEVICE without root")
        assertFalse(PhysicalInterface.canBind(Os.LINUX, "4.19.0-27-amd64"))
        assertTrue(PhysicalInterface.canBind(Os.WINDOWS, "10.0"))
    }
}
