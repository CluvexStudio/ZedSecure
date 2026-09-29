package dev.cluvex.zedsecure.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZepTunConfigTest {
    private fun config(
        socksPort: Int = 10808,
        mtu: Int = 1412,
        udpOverTcp: Boolean = false,
        user: String? = null,
        pass: String? = null,
        pipeline: Boolean = false,
        rwTimeout: String = ZepTunCore.DEFAULT_RW_TIMEOUT,
    ): String {
        val (tcp, udp) = ZepTunCore.parseRwTimeout(rwTimeout)
        return ZepTunCore.buildToml(
            fd = 42,
            socksPort = socksPort,
            mtu = mtu,
            ipv4 = "10.1.0.1",
            ipv6 = null,
            udpOverTcp = udpOverTcp,
            socksUser = user,
            socksPass = pass,
            pipeline = pipeline,
            logLevel = "warn",
            tcpIdleMs = tcp * 1000L,
            udpIdleMs = udp * 1000L,
        )
    }

    @Test
    fun `the tunnel points at our own proxy, with the MTU the interface was built with`() {
        val toml = config(socksPort = 10808, mtu = 1412)

        assertTrue(toml.contains("server = \"127.0.0.1:10808\""))
        assertTrue(toml.contains("mtu = 1412"))
        assertTrue("a phone is not a server", toml.contains("preset = \"mobile\""))
    }

    @Test
    fun `the descriptor is named in the document, not just handed over afterwards`() {
        assertTrue(config().contains("fd = 42"))
    }

    @Test
    fun `the stack is told which addresses the interface carries`() {
        assertTrue(config().contains("address = [\"10.1.0.1/30\"]"))
    }

    @Test
    fun `Android owns the routes, so the engine installs none`() {
        assertTrue(config().contains("auto_route = false"))
    }

    @Test
    fun `a TCP-only proxy carries its datagrams over TCP`() {
        assertTrue(config(udpOverTcp = true).contains("udp_mode = \"tcp\""))
        assertTrue(config(udpOverTcp = false).contains("udp_mode = \"udp\""))

        assertTrue(config(udpOverTcp = true).contains("udp = true"))
    }

    @Test
    fun `credentials appear only when there are any`() {
        val without = config()
        assertFalse(without.contains("username"))
        assertFalse(without.contains("password"))

        val with = config(user = "bob", pass = "s3cret")
        assertTrue(with.contains("username = \"bob\""))
        assertTrue(with.contains("password = \"s3cret\""))
    }

    @Test
    fun `idle timeouts come from the VPN settings, in milliseconds`() {
        val toml = config(rwTimeout = "120,30")

        assertTrue(toml.contains("tcp_idle_timeout_ms = 120000"))
        assertTrue(toml.contains("udp_idle_timeout_ms = 30000"))
    }

    @Test
    fun `a malformed timeout setting falls back instead of producing nonsense`() {
        assertEquals(300L to 60L, ZepTunCore.parseRwTimeout(""))
        assertEquals(300L to 60L, ZepTunCore.parseRwTimeout("abc,def"))
        assertEquals(300L to 60L, ZepTunCore.parseRwTimeout("0,0"))
        assertEquals(90L to 60L, ZepTunCore.parseRwTimeout("90"))
    }

    @Test
    fun `every section the document opens is one the engine defines`() {
        val known = setOf("[tun]", "[stack]", "[handler]", "[handler.socks5]", "[route]")
        val sections = config(user = "u", pass = "p").lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("[") }
            .toList()

        assertTrue("unexpected sections: ${sections - known}", (sections.toSet() - known).isEmpty())
    }
}
