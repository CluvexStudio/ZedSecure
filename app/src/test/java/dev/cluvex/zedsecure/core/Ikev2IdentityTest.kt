package dev.cluvex.zedsecure.core

import dev.cluvex.zedsecure.core.Ikev2Controller.IdentityKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Ikev2IdentityTest {
    private fun kind(value: String) = Ikev2Controller.identityKind(value)

    @Test
    fun `a host name stays a name and is never looked up`() {
        val id = kind("vpn.example.com")
        assertTrue(id is IdentityKind.Fqdn)
        assertEquals("vpn.example.com", id.value)
    }

    @Test
    fun `addresses stay addresses`() {
        assertTrue(kind("203.0.113.7") is IdentityKind.Address)
        assertTrue(kind("2001:db8::7") is IdentityKind.Address)
    }

    @Test
    fun `the prefixes Android uses pick the identity type`() {
        assertTrue(kind("@vpn.example.com") is IdentityKind.Fqdn)
        assertEquals("vpn.example.com", kind("@vpn.example.com").value)
        assertTrue(kind("@@user@example.com") is IdentityKind.Email)
        assertEquals("user@example.com", kind("@@user@example.com").value)
    }

    @Test
    fun `a bare email address is an email identity like on Android`() {
        val id = kind("user@example.com")
        assertTrue(id is IdentityKind.Email)
        assertEquals("user@example.com", id.value)
    }

    @Test
    fun `a plain user name is sent as a name`() {
        assertTrue(kind("user1") is IdentityKind.Fqdn)
    }
}
