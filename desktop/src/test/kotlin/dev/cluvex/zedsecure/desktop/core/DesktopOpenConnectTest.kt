package dev.cluvex.zedsecure.desktop.core

import dev.cluvex.zedsecure.domain.config.OpenConnectProfile
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopOpenConnectTest {
    private val work = Files.createTempDirectory("oc-test").toFile()

    private val profile = OpenConnectProfile(
        server = "vpn.example.com",
        username = "alice",
        password = "s3cret pass",
        authgroup = "Staff",
        serverCertSha256 = "pin-sha256:AbCd123=",
        tokenMode = OpenConnectProfile.TOKEN_TOTP,
        tokenSecret = "JBSWY3DPEHPK3PXP",
        disableDtls = true,
    )

    private fun engine(os: Os, p: OpenConnectProfile = profile) = DesktopOpenConnect(p, work, os = os)

    @Test
    fun `the password goes to stdin and never into the arguments`() {
        val args = engine(Os.LINUX).arguments(work, File("/usr/share/vpnc-scripts/vpnc-script"))
        assertTrue("--passwd-on-stdin" in args && "--non-inter" in args, args.toString())
        assertFalse(args.any { "s3cret" in it }, args.toString())
        assertFalse(args.any { "JBSWY3DPEHPK3PXP" in it }, "the token secret is passed as a file")
        val secret = args.first { it.startsWith("--token-secret=@") }.removePrefix("--token-secret=@")
        assertEquals("JBSWY3DPEHPK3PXP", File(secret).readText())
    }

    @Test
    fun `profile options become OpenConnect flags with the server last`() {
        val args = engine(Os.LINUX).arguments(work, File("/etc/vpnc/vpnc-script"))
        assertEquals("--protocol=anyconnect", args.first())
        assertTrue("--user=alice" in args && "--authgroup=Staff" in args, args.toString())
        assertTrue("--servercert=pin-sha256:AbCd123=" in args && "--no-dtls" in args, args.toString())
        assertTrue("--os=linux-64" in args, "a phone OS is not reported from a desktop")
        assertTrue("--script=/etc/vpnc/vpnc-script" in args, args.toString())
        assertEquals("vpn.example.com", args.last())
    }

    @Test
    fun `Windows names the adapter and reports the Windows client`() {
        val args = engine(Os.WINDOWS).arguments(work, null)
        assertTrue("--interface=${DesktopOpenConnect.ADAPTER}" in args && "--os=win" in args, args.toString())
        assertFalse(args.any { it.startsWith("--script=") }, args.toString())
    }

    @Test
    fun `single sign-on is refused with a clear reason`() {
        val oidc = profile.copy(tokenMode = OpenConnectProfile.TOKEN_OIDC)
        val e = assertFailsWith<IllegalStateException> { engine(Os.LINUX, oidc).arguments(work, null) }
        assertTrue("browser" in e.message.orEmpty())
    }

    @Test
    fun `the helper scripts are valid shell`() {
        val oc = File("/usr/bin/openconnect")
        val args = listOf("--user=it's me", "vpn.example.com")
        val linux = File(work, "linux.sh").apply { writeText(engine(Os.LINUX).linuxScript(oc, args, work)) }
        val mac = File(work, "mac.sh").apply { writeText(engine(Os.MACOS).macScript(oc, args)) }
        listOf(linux, mac).forEach { script ->
            val check = ProcessBuilder("sh", "-n", script.absolutePath).redirectErrorStream(true).start()
            val out = check.inputStream.bufferedReader().readText()
            assertEquals(0, check.waitFor(), "${script.name}: $out")
        }
        assertTrue("'--user=it'\\''s me'" in linux.readText(), linux.readText())
        assertTrue(LinuxRootHelper.READY_MARKER in linux.readText())
        assertTrue(Regex(DesktopOpenConnect.CONNECTED).containsMatchIn("Connected as 192.168.10.5, using SSL, with DTLS in progress"))
        assertTrue(Regex(DesktopOpenConnect.CONNECTED).containsMatchIn("ESP session established with server"))
        assertFalse(Regex(DesktopOpenConnect.CONNECTED).containsMatchIn("Failed to obtain WebVPN cookie"))
    }

    @Test
    fun `Windows arguments are quoted the way the C runtime splits them`() {
        assertEquals("plain", DesktopOpenConnect.winArg("plain"))
        assertEquals("\"with space\"", DesktopOpenConnect.winArg("with space"))
        assertEquals("\"say \\\"hi\\\"\"", DesktopOpenConnect.winArg("say \"hi\""))
        assertEquals("\"C:\\Program Files\\x\\\\\"", DesktopOpenConnect.winArg("C:\\Program Files\\x\\"))
        assertEquals("\"\"", DesktopOpenConnect.winArg(""))
    }
}
