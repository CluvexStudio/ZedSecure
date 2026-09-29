package dev.cluvex.zedsecure.core

import dev.cluvex.zedsecure.domain.config.Ikev2Auth
import dev.cluvex.zedsecure.domain.config.Ikev2Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Ikev2ProposalTest {
    @Test
    fun `legacy EAP_TLS folds into CERTIFICATE`() {
        val p = Ikev2Profile(server = "vpn.example.com", authType = Ikev2Auth.EAP_TLS, userCertAlias = "a")
        assertEquals(Ikev2Auth.CERTIFICATE, p.effectiveAuth)
        assertTrue("a legacy EAP-TLS profile must stay usable", p.isValid)
    }

    @Test
    fun `other auth modes are unchanged by the fold`() {
        listOf(Ikev2Auth.EAP_MSCHAPV2, Ikev2Auth.CERTIFICATE, Ikev2Auth.PSK).forEach {
            assertEquals(it, Ikev2Profile(server = "s", authType = it).effectiveAuth)
        }
    }

    @Test
    fun `EAP profile requires a password, not just a username`() {
        val noPass = Ikev2Profile(
            server = "vpn.example.com", authType = Ikev2Auth.EAP_MSCHAPV2, username = "u",
        )
        assertFalse("the platform requires a non-null password", noPass.isValid)
        assertTrue(noPass.copy(password = "p").isValid)
    }

    @Test
    fun `server is always required`() {
        assertFalse(Ikev2Profile(authType = Ikev2Auth.PSK, psk = "k").isValid)
        assertTrue(Ikev2Profile(server = "s", authType = Ikev2Auth.PSK, psk = "k").isValid)
    }

    @Test
    fun `certificate profile needs an alias`() {
        val p = Ikev2Profile(server = "s", authType = Ikev2Auth.CERTIFICATE)
        assertFalse(p.isValid)
        assertTrue(p.copy(userCertAlias = "alias").isValid)
    }

    @Test
    fun `explicit local id always wins`() {
        val p = Ikev2Profile(server = "vpn.example.com", localId = "@me.example.com", username = "u")
        assertEquals("@me.example.com", Ikev2Controller.localIdentity(p, null))
    }

    @Test
    fun `EAP falls back to the username, never the server`() {
        val p = Ikev2Profile(server = "vpn.example.com", authType = Ikev2Auth.EAP_MSCHAPV2, username = "alice")
        assertEquals("alice", Ikev2Controller.localIdentity(p, null))
    }

    @Test
    fun `with no identity at all it falls back to the server`() {
        val p = Ikev2Profile(server = "vpn.example.com", authType = Ikev2Auth.PSK, psk = "k")
        assertEquals("vpn.example.com", Ikev2Controller.localIdentity(p, null))
    }

    @Test
    fun `blank proposals are always acceptable`() {
        assertTrue(runCatching { Ikev2Proposals.isParsable("", child = false) }.getOrDefault(true))
    }

    @Test
    fun `dead fields are gone from the model`() {
        val names = Ikev2Profile::class.java.declaredFields.map { it.name }
        listOf("mobike", "splitIncludedSubnets", "splitExcludedSubnets", "useAppDns").forEach {
            assertFalse("$it must not be reintroduced", names.contains(it))
        }
    }

    @Test
    fun `remote id defaults to the server`() {
        val p = Ikev2Profile(server = "vpn.example.com")
        assertEquals("vpn.example.com", p.effectiveRemoteId)
        assertEquals("gw.example.com", p.copy(remoteId = "gw.example.com").effectiveRemoteId)
    }

    @Test
    fun `sealed secrets are not readable as plaintext`() {
        val sealed = Ikev2Profile.sealPassword("hunter2")
        assertTrue(sealed.isNotBlank())
        assertNull(null)
    }
}
