package dev.cluvex.zedsecure.core

import dev.amirzr.flutter_v2ray_client.v2ray.core.HevTunCore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HevTunSettingsTest {
    private fun yaml(logLevel: String = HevTunCore.DEFAULT_LOG_LEVEL, tcp: Int = 300, udp: Int = 60) = HevTunCore.buildYaml(
        socksPort = 10808,
        mtu = 1500,
        ipv4 = "10.1.0.1",
        ipv6 = null,
        preferIpv6 = false,
        udpOverTcp = false,
        socksUser = null,
        socksPass = null,
        pipeline = false,
        logLevel = logLevel,
        tcpTimeoutSeconds = tcp,
        udpTimeoutSeconds = udp,
    )

    @Test
    fun `defaults are what hev always ran with`() {
        val y = yaml()
        assertTrue(y.contains("log-level: warn"))
        assertTrue(y.contains("tcp-read-write-timeout: 300000"))
        assertTrue(y.contains("udp-read-write-timeout: 60000"))
    }

    @Test
    fun `settings reach the yaml`() {
        val y = yaml(logLevel = "debug", tcp = 120, udp = 30)
        assertTrue(y.contains("log-level: debug"))
        assertTrue(y.contains("tcp-read-write-timeout: 120000"))
        assertTrue(y.contains("udp-read-write-timeout: 30000"))
    }

    @Test
    fun `a level hev does not know falls back instead of breaking the tunnel`() {
        assertTrue(yaml(logLevel = "verbose").contains("log-level: warn"))
    }

    @Test
    fun `timeouts parse like v2rayNG and reject nonsense per field`() {
        assertEquals(600 to 90, HevTunCore.parseRwTimeout("600,90"))
        assertEquals(45 to 60, HevTunCore.parseRwTimeout(" 45 , "))
        assertEquals(300 to 60, HevTunCore.parseRwTimeout("abc"))
        assertEquals(300 to 60, HevTunCore.parseRwTimeout("0,-5"))
        assertEquals(300 to 15, HevTunCore.parseRwTimeout("x,15"))
    }
}
