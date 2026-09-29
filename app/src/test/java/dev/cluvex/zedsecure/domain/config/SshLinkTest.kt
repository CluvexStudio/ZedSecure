package dev.cluvex.zedsecure.domain.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class SshLinkTest {
    private fun b64(s: String) = Base64.getEncoder().encodeToString(s.toByteArray())

    @Test
    fun `the V2Box form parses, commas and all`() {
        val (name, p) = SshLink.parse("ssh://alice,hunter2,@203.0.113.9:2222#Berlin")!!

        assertEquals("Berlin", name)
        assertEquals("203.0.113.9", p.host)
        assertEquals(2222, p.port)
        assertEquals("alice", p.username)
        assertEquals("hunter2", p.password)
        assertEquals(SshProfile.AUTH_PASSWORD, p.authType)
    }

    @Test
    fun `a base64 private key in the third field selects key auth`() {
        val key = "-----BEGIN OPENSSH PRIVATE KEY-----\nabc\n-----END OPENSSH PRIVATE KEY-----"
        val (_, p) = SshLink.parse("ssh://bob,,${b64(key)}@ssh.example.com:22#Key")!!

        assertEquals(SshProfile.AUTH_KEY, p.authType)
        assertTrue(p.privateKey.contains("PRIVATE KEY"))
        assertEquals("", p.password)
    }

    @Test
    fun `a password containing a comma or an at sign is not cut in half`() {
        val (_, p) = SshLink.parse("ssh://carol,pa,ss@word,@198.51.100.4:22")!!

        assertEquals("carol", p.username)
        assertEquals("pa,ss@word", p.password)
        assertEquals("198.51.100.4", p.host)
    }

    @Test
    fun `the plain URI form works too, and the port defaults to 22`() {
        val (_, p) = SshLink.parse("ssh://dave@ssh.example.com")!!

        assertEquals("dave", p.username)
        assertEquals(22, p.port)
    }

    @Test
    fun `a non-Latin remark survives the round trip`() {
        val link = SshLink.build("سرور تهران", SshProfile(host = "1.2.3.4", port = 22, username = "u", password = "p"))
        val (name, p) = SshLink.parse(link)!!

        assertEquals("سرور تهران", name)
        assertEquals("u", p.username)
        assertEquals("p", p.password)
    }

    @Test
    fun `something that is not a usable link yields null rather than a broken profile`() {
        assertNull(SshLink.parse("ssh://"))
        assertNull(SshLink.parse("ssh://@host:22"))
        assertNull(SshLink.parse("vless://id@host:443"))
        assertEquals(false, SshLink.isSshLink("sshx://a@b"))
    }
}
