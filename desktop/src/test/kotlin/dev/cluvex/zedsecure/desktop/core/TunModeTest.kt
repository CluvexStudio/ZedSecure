package dev.cluvex.zedsecure.desktop.core

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TunModeTest {
    @Test
    fun `every desktop system has a TUN engine that installs its own routes`() {
        assertTrue(TunMode.supported(Os.LINUX))
        assertTrue(TunMode.supported(Os.WINDOWS))
        assertTrue(TunMode.supported(Os.MACOS))
        assertFalse(TunMode.supported(Os.OTHER))
        assertFalse(TunMode.usesZeptun(Os.LINUX), "Linux keeps hev and its root helper")
        assertTrue(TunMode.usesZeptun(Os.WINDOWS), "zeptun installs routes and DNS through IP Helper")
        assertTrue(TunMode.usesZeptun(Os.MACOS), "zeptun splits the default route and binds its sockets")
    }

    @Test
    fun `the root helper script parses in both modes and the remembered copy differs only in its mode`() {
        val dir = Files.createTempDirectory("broker").toFile()
        try {
            val session = File(dir, "session.sh").apply { writeText(LinuxBroker.script(persistent = false)) }
            val persistent = File(dir, "persistent.sh").apply { writeText(LinuxBroker.script(persistent = true)) }
            listOf(session, persistent).forEach { script ->
                val check = ProcessBuilder("sh", "-n", script.absolutePath).redirectErrorStream(true).start()
                val out = check.inputStream.bufferedReader().readText()
                assertEquals(0, check.waitFor(), "${script.name}: $out")
            }
            assertTrue(session.readText().lines()[1].startsWith("PATH=") && "/run/current-system/sw/bin" in session.readText())
            assertEquals(1, session.readText().lines().count { it == "MODE=session" })
            val sed = ProcessBuilder("sed", "s/^MODE=session$/MODE=persistent/", session.absolutePath).start()
            val converted = sed.inputStream.bufferedReader().readText()
            assertEquals(0, sed.waitFor())
            assertEquals(persistent.readText(), converted, "REMEMBER installs a copy the app must recognise as current")
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `addresses handed to the root helper are plain IPs only`() {
        assertTrue(LinuxBroker.isAddress("203.0.113.7"))
        assertTrue(LinuxBroker.isAddress("2001:db8::1"))
        assertFalse(LinuxBroker.isAddress("256.1.1.1"))
        assertFalse(LinuxBroker.isAddress("example.com"))
        assertFalse(LinuxBroker.isAddress("1.2.3.4;reboot"))
        assertFalse(LinuxBroker.isAddress("fe80::1%eth0"))
    }

    @Test
    fun `the root helper raises and drops the tunnel on request and leaves when the app does`() {
        assumeTrue(Os.current == Os.LINUX)
        assumeTrue(javaClass.getResource("/bin/linux/hev-socks5-tunnel") != null)
        assumeTrue(
            exec("unshare", "-rn", "sh", "-c", "ip tuntap add mode tun dev zedcheck0", timeoutSec = 10).first == 0,
            "a user namespace here cannot create a TUN device",
        )

        val dir = Files.createTempDirectory("broker").toFile()
        val hev = BundledBinary.extract(dir, "hev-socks5-tunnel", "hev-socks5-tunnel.exe")!!
        val script = File(dir, "broker.sh").apply { writeText(LinuxBroker.script(persistent = false)) }
        val p = ProcessBuilder("unshare", "-rn", "/bin/sh", script.absolutePath, hev.absolutePath).redirectErrorStream(true).start()
        val lines = java.util.concurrent.LinkedBlockingQueue<String>()
        Thread { p.inputStream.bufferedReader().forEachLine { lines.put(it) } }.apply { isDaemon = true }.start()
        fun send(line: String): String? {
            p.outputStream.write((line + "\n").toByteArray())
            p.outputStream.flush()
            while (true) {
                val reply = lines.poll(20, TimeUnit.SECONDS) ?: return null
                if (reply == "OK" || reply.startsWith("ERR")) return reply
            }
        }
        try {
            assertEquals("${LinuxBroker.HELLO} session", lines.poll(15, TimeUnit.SECONDS))
            assertEquals("OK", send("TUN_UP 1080 udp - 203.0.113.7"))
            assertEquals("OK", send("TUN_UP 1080 tcp -"), "a second TUN_UP replaces the first tunnel")
            assertEquals("OK", send("TUN_DOWN"))
            assertEquals("ERR bad port", send("TUN_UP 99999 udp -"))
            assertTrue(send("TUN_UP 1080 udp - 1.2.3.4;id")?.startsWith("ERR") == true)
            assertTrue(send("JOB /nonexistent/job.sh")?.startsWith("ERR") == true)
            assertEquals("ERR unknown command", send("EXEC id"))
            assertEquals("OK", send("TUN_UP 1080 udp -"))
            assertTrue(p.isAlive, "the helper stays for the whole run")

            p.outputStream.close()
            assertTrue(p.waitFor(15, TimeUnit.SECONDS), "closing the pipe must end the helper")
            assertEquals(0, p.exitValue())
        } finally {
            runCatching { p.outputStream.close() }
            p.waitFor(10, TimeUnit.SECONDS)
            p.descendants().forEach { it.destroyForcibly() }
            p.destroyForcibly()
            dir.deleteRecursively()
        }
    }
}
