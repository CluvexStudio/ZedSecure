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
    fun `the Linux helper script parses and finds its tools on NixOS too`() {
        val dir = Files.createTempDirectory("tunmode").toFile()
        val mode = TunMode(File(dir, "hev"), "127.0.0.1", 1080, dir, bypassIps = listOf("203.0.113.7"))
        val script = mode.writeLinuxScript(dir, File(dir, "hev"), mode.writeConfig(dir))
        val text = script.readText()

        assertTrue(text.lines()[1].startsWith("PATH=") && "/run/current-system/sw/bin" in text.lines()[1])
        assertTrue("ip route add 203.0.113.7/32" in text && "ip route del 203.0.113.7/32" in text)
        assertTrue("echo ZEDSECURE_TUN_READY" in text && "read _" in text)
        val check = ProcessBuilder("sh", "-n", script.absolutePath).redirectErrorStream(true).start()
        val out = check.inputStream.bufferedReader().readText()
        assertEquals(0, check.waitFor(), out)
        dir.deleteRecursively()
    }

    @Test
    fun `the Linux helper brings tun up, says so, and cleans up once the app lets go`() {
        assumeTrue(Os.current == Os.LINUX)
        assumeTrue(javaClass.getResource("/bin/linux/hev-socks5-tunnel") != null)
        assumeTrue(exec("unshare", "-rn", "true", timeoutSec = 10).first == 0, "user namespaces are not allowed here")

        val dir = Files.createTempDirectory("tunmode").toFile()
        val hev = BundledBinary.extract(dir, "hev-socks5-tunnel", "hev-socks5-tunnel.exe")!!
        val mode = TunMode(hev, "127.0.0.1", 1080, dir)
        val script = mode.writeLinuxScript(dir, hev, mode.writeConfig(dir))
        val p = ProcessBuilder("unshare", "-rn", "/bin/sh", script.absolutePath).redirectErrorStream(true).start()
        try {
            val lines = mutableListOf<String>()
            val reader = Thread { p.inputStream.bufferedReader().forEachLine { synchronized(lines) { lines += it } } }
            reader.isDaemon = true
            reader.start()
            val deadline = System.currentTimeMillis() + 15_000
            while (System.currentTimeMillis() < deadline && synchronized(lines) { "ZEDSECURE_TUN_READY" !in lines }) {
                if (!p.isAlive) break
                Thread.sleep(100)
            }
            assertTrue(synchronized(lines) { "ZEDSECURE_TUN_READY" in lines }, synchronized(lines) { lines.joinToString("\n") })
            assertTrue(p.isAlive, "the helper must keep running while the app holds it")

            p.outputStream.close()
            assertTrue(p.waitFor(10, TimeUnit.SECONDS), "closing the pipe must end the helper")
            assertEquals(0, p.exitValue())
        } finally {
            p.destroyForcibly()
            dir.deleteRecursively()
        }
    }
}
