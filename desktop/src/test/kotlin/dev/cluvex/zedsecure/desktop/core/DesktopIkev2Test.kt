package dev.cluvex.zedsecure.desktop.core

import dev.cluvex.zedsecure.domain.config.Ikev2Auth
import dev.cluvex.zedsecure.domain.config.Ikev2Profile
import java.io.File
import java.nio.file.Files
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopIkev2Test {
    private val work = Files.createTempDirectory("ikev2").toFile()

    private val eap = Ikev2Profile(
        server = "vpn.example.com",
        remoteId = "vpn.example.com",
        authType = Ikev2Auth.EAP_MSCHAPV2,
        username = "alice",
        password = "p<a>ss&word",
    )

    @Test
    fun `windows gets an IKEv2 entry with a modern IPsec policy and the proposal it asks for`() {
        val plain = DesktopIkev2(eap, work, Os.WINDOWS).windowsScript()
        assertTrue("-TunnelType Ikev2" in plain && "-AuthenticationMethod Eap" in plain, plain)
        assertTrue("-EncryptionMethod AES256 -IntegrityCheckMethod SHA256 -DHGroup Group14" in plain, plain)
        assertFalse("alice" in plain || "word" in plain, "credentials stay out of the script")

        val gcm = DesktopIkev2(eap.copy(ikeProposal = "aes128gcm16-prfsha256-ecp256", espProposal = "aes128gcm16"), work, Os.WINDOWS)
            .windowsScript()
        assertTrue("-AuthenticationTransformConstants GCMAES128 -CipherTransformConstants GCMAES128" in gcm, gcm)
        assertTrue("-DHGroup ECP256" in gcm, gcm)
    }

    @Test
    fun `linux hands NetworkManager-strongswan the server, the method and the CA`() {
        val ca = File(work, "ca.pem").apply { writeText("x") }
        val cmd = DesktopIkev2(eap, work, Os.LINUX).linuxAddCommand(ca)
        val data = cmd[cmd.indexOf("vpn.data") + 1]

        assertEquals("strongswan", cmd[cmd.indexOf("vpn-type") + 1])
        assertTrue("address=vpn.example.com" in data && "method=eap" in data && "user=alice" in data, data)
        assertTrue("certificate=${ca.absolutePath}" in data && "remote-identity=vpn.example.com" in data, data)
        assertFalse("p<a>ss" in cmd.joinToString(" "), "the password goes through a passwd file, not the command line")

        val psk = DesktopIkev2(eap.copy(authType = Ikev2Auth.PSK, psk = "shared"), work, Os.LINUX).linuxAddCommand(null)
        val pskData = psk[psk.indexOf("vpn.data") + 1]
        assertTrue("method=psk" in pskData && "user=" !in pskData, pskData)
    }

    @Test
    fun `the macOS profile is a well formed plist with the credentials escaped`() {
        val xml = DesktopIkev2(eap, work, Os.MACOS).macProfile()
        val doc = DocumentBuilderFactory.newInstance().apply { isValidating = false }.also {
            it.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        }.newDocumentBuilder().parse(xml.byteInputStream())

        assertEquals("plist", doc.documentElement.tagName)
        assertTrue("<string>IKEv2</string>" in xml && "<string>vpn.example.com</string>" in xml)
        assertTrue("<string>p&lt;a&gt;ss&amp;word</string>" in xml, xml)
        assertTrue("<key>ExtendedAuthEnabled</key><integer>1</integer>" in xml)
    }

    @Test
    fun `certificate sign-in is refused with a reason on the desktop`() {
        val result = DesktopIkev2(eap.copy(authType = Ikev2Auth.CERTIFICATE, userCertAlias = "alias"), work, Os.LINUX).connect()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("key store"))
    }
}
