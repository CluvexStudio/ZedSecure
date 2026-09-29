package dev.cluvex.zedsecure.domain.config

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenConnectPortTest {
    private fun roundTrip(server: String) {
        val (base, port) = OpenConnectProfile.splitPort(server)
        assertEquals("round-trip of $server", server, OpenConnectProfile.withPort(base, port))
    }

    @Test
    fun `splits a plain host and port`() {
        assertEquals("vpn.example.com" to 8443, OpenConnectProfile.splitPort("vpn.example.com:8443"))
    }

    @Test
    fun `host with no port yields null`() {
        assertEquals("vpn.example.com" to null, OpenConnectProfile.splitPort("vpn.example.com"))
    }

    @Test
    fun `keeps the scheme and the group path`() {
        assertEquals(
            "https://vpn.example.com/group" to 8443,
            OpenConnectProfile.splitPort("https://vpn.example.com:8443/group"),
        )
    }

    @Test
    fun `an IPv6 literal is not mistaken for a port`() {
        assertEquals("[2001:db8::1]" to null, OpenConnectProfile.splitPort("[2001:db8::1]"))
        assertEquals("[2001:db8::1]" to 8443, OpenConnectProfile.splitPort("[2001:db8::1]:8443"))
    }

    @Test
    fun `a non-numeric or out-of-range port is left alone`() {
        assertEquals("vpn.example.com:https" to null, OpenConnectProfile.splitPort("vpn.example.com:https"))
        assertEquals("vpn.example.com:99999" to null, OpenConnectProfile.splitPort("vpn.example.com:99999"))
    }

    @Test
    fun `withPort replaces rather than appends`() {
        assertEquals(
            "https://vpn.example.com:443/group",
            OpenConnectProfile.withPort("https://vpn.example.com:8443/group", 443),
        )
    }

    @Test
    fun `a blank port strips the existing one`() {
        assertEquals(
            "https://vpn.example.com/group",
            OpenConnectProfile.withPort("https://vpn.example.com:8443/group", null),
        )
    }

    @Test
    fun `round-trips every shape the editor can produce`() {
        listOf(
            "vpn.example.com",
            "vpn.example.com:8443",
            "https://vpn.example.com",
            "https://vpn.example.com:8443",
            "https://vpn.example.com/group",
            "https://vpn.example.com:8443/group",
            "[2001:db8::1]",
            "[2001:db8::1]:8443",
            "https://[2001:db8::1]:8443/group",
        ).forEach(::roundTrip)
    }

    @Test
    fun `serverPort still sees a port written through withPort`() {
        val server = OpenConnectProfile.withPort("vpn.example.com", 8443)
        assertEquals(8443, OpenConnectProfile(server = server).serverPort())
        assertEquals(443, OpenConnectProfile(server = "vpn.example.com").serverPort())
    }
}
