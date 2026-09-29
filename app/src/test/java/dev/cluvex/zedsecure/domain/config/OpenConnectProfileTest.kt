package dev.cluvex.zedsecure.domain.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenConnectProfileTest {
    @Test
    fun `port comes from the URL, not a hardcoded 443`() {
        assertEquals(443, OpenConnectProfile(server = "vpn.example.com").serverPort())
        assertEquals(8443, OpenConnectProfile(server = "vpn.example.com:8443").serverPort())
        assertEquals(8443, OpenConnectProfile(server = "https://vpn.example.com:8443/group").serverPort())
        assertEquals(443, OpenConnectProfile(server = "https://vpn.example.com/group").serverPort())
    }

    @Test
    fun `an IPv6 literal's inner colons are not mistaken for a port`() {
        assertEquals(443, OpenConnectProfile(server = "[2001:db8::1]").serverPort())
        assertEquals(8443, OpenConnectProfile(server = "[2001:db8::1]:8443").serverPort())
    }

    @Test
    fun `http is upgraded because openconnect_parse_url rejects it outright`() {
        assertEquals("https://vpn.example.com", OpenConnectProfile(server = "http://vpn.example.com").serverUrl())
        assertEquals("https://vpn.example.com", OpenConnectProfile(server = "vpn.example.com").serverUrl())

        assertEquals("https://vpn.example.com/g", OpenConnectProfile(server = "https://vpn.example.com/g").serverUrl())
    }

    @Test
    fun `a gateway alone is valid, because SAML needs no username or certificate`() {
        assertTrue(OpenConnectProfile(server = "vpn.example.com").isValid)
        assertFalse(OpenConnectProfile(server = "  ").isValid)
    }

    @Test
    fun `the Pulse user-agent carries the slash pulse_c tests for`() {
        val ua = OpenConnectProfile.defaultUserAgentFor(OpenConnectProfile.PROTO_PULSE)
        assertTrue("pulse.c matches on the 'Pulse-Secure/' prefix", ua.startsWith("Pulse-Secure/"))
    }

    @Test
    fun `the user-agent follows the protocol instead of being frozen at save time`() {
        val p = OpenConnectProfile(server = "h", protocol = OpenConnectProfile.PROTO_GP)
        assertEquals("PAN GlobalProtect", p.effectiveUserAgent())
        assertEquals(
            OpenConnectProfile.defaultUserAgentFor(OpenConnectProfile.PROTO_FORTINET),
            p.copy(protocol = OpenConnectProfile.PROTO_FORTINET).effectiveUserAgent(),
        )

        assertEquals("Custom/1.0", p.copy(userAgent = "Custom/1.0").effectiveUserAgent())
    }

    @Test
    fun `reported-OS values are exactly what set_reported_os accepts`() {
        assertEquals(
            listOf("android", "apple-ios", "linux", "linux-64", "win", "mac-intel"),
            OpenConnectProfile.REPORTED_OS_VALUES.map { it.first },
        )
        assertTrue(OpenConnectProfile.isMobileOs("android"))
        assertTrue(OpenConnectProfile.isMobileOs("apple-ios"))
        assertFalse(OpenConnectProfile.isMobileOs("linux-64"))
    }

    @Test
    fun `reconnect timeout defaults to upstream's 300, not our old 100`() {
        assertEquals(300, OpenConnectProfile.DEFAULT_RECONNECT_TIMEOUT)
        assertEquals(300, OpenConnectProfile(server = "h").reconnectTimeoutSec)
    }
}
