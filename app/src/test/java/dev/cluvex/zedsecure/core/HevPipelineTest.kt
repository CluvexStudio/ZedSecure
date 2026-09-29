package dev.cluvex.zedsecure.core

import dev.amirzr.flutter_v2ray_client.v2ray.core.HevTunCore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HevPipelineTest {
    private fun yaml(pipeline: Boolean, udpOverTcp: Boolean = false) = HevTunCore.buildYaml(
        socksPort = 10808,
        mtu = 1500,
        ipv4 = "10.1.0.1",
        ipv6 = "fc00::1",
        preferIpv6 = false,
        udpOverTcp = udpOverTcp,
        socksUser = null,
        socksPass = null,
        pipeline = pipeline,
    )

    @Test
    fun `pipeline is omitted entirely when off`() {
        assertFalse("pipeline must not appear when disabled", yaml(pipeline = false).contains("pipeline"))
    }

    @Test
    fun `pipeline is emitted when on`() {
        assertTrue(yaml(pipeline = true).contains("pipeline: true"))
    }

    @Test
    fun `default is off so a new call site cannot silently break a strict server`() {
        val default = HevTunCore.buildYaml(
            socksPort = 10808,
            mtu = 1500,
            ipv4 = "10.1.0.1",
            ipv6 = null,
            preferIpv6 = false,
            udpOverTcp = false,
            socksUser = null,
            socksPass = null,
            pipeline = false,
        )
        assertFalse(default.contains("pipeline"))
    }

    @Test
    fun `udp mode is independent of pipelining`() {
        assertTrue(yaml(pipeline = false, udpOverTcp = false).contains("udp: 'udp'"))
        assertTrue(yaml(pipeline = true, udpOverTcp = false).contains("udp: 'udp'"))
        assertTrue(yaml(pipeline = false, udpOverTcp = true).contains("udp: 'tcp'"))
    }

    @Test
    fun `credentials and pipelining are not combined`() {
        val withCreds = HevTunCore.buildYaml(
            socksPort = 10808,
            mtu = 1500,
            ipv4 = "10.1.0.1",
            ipv6 = null,
            preferIpv6 = false,
            udpOverTcp = false,
            socksUser = "u",
            socksPass = "p",
            pipeline = false,
        )
        assertTrue(withCreds.contains("username: 'u'"))
        assertFalse(withCreds.contains("pipeline"))
    }

    @Test
    fun `socks address stays loopback`() {
        assertEquals(1, yaml(pipeline = true).lines().count { it.trim() == "address: 127.0.0.1" })
    }
}
