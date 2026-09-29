package dev.cluvex.zedsecure.data.net

import dev.cluvex.zedsecure.domain.config.ConfigParser
import dev.cluvex.zedsecure.domain.config.CustomConfig
import dev.cluvex.zedsecure.domain.config.VpnProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PingPrecheckTest {
    private fun customProfile(rawJson: String) =
        VpnProfile.fromRawJson(rawJson, id = "x", addedAt = 0L)

    private fun linkProfile(link: String) =
        VpnProfile.fromLink(link, id = "y", addedAt = 0L)

    private fun customConfig(protocol: String, extra: String = "") = """
        {
          "outbounds": [
            { "protocol": "$protocol",
              "settings": { "servers": [ { "address": "a.example", "port": 8443 } ] }$extra
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `custom tcp outbound is pre-checked`() {
        val p = customProfile(customConfig("trojan"))
        assertTrue("address must be extracted", p.address == "a.example" && p.port == 8443)
        assertTrue(CustomConfig.tcpProbeable(customConfig("trojan")))
        assertTrue(PingService.shouldPrecheck(p))
    }

    @Test
    fun `custom udp outbounds are not pre-checked`() {
        for (proto in listOf("wireguard", "hysteria2", "hysteria")) {
            assertFalse(proto, CustomConfig.tcpProbeable(customConfig(proto)))
            assertFalse(proto, PingService.shouldPrecheck(customProfile(customConfig(proto))))
        }
    }

    @Test
    fun `custom config chaining through dialerProxy is not pre-checked`() {
        val chained = customConfig(
            "trojan",
            extra = ""","streamSettings": { "sockopt": { "dialerProxy": "chain-out" } }""",
        )
        assertFalse(CustomConfig.tcpProbeable(chained))
        assertFalse(PingService.shouldPrecheck(customProfile(chained)))
    }

    @Test
    fun `link profiles are pre-checked except udp ones`() {
        val trojan = linkProfile("trojan://pw@t.example:8443#T")
        assertTrue(PingService.shouldPrecheck(trojan))

        val wg = linkProfile(
            "wireguard://aPrivateKeyValue000000000000000000000000000%3D@w.example:51820" +
                "?publickey=aPublicKeyValue0000000000000000000000000000%3D&address=10.8.0.2%2F32#W",
        )
        assertFalse("WireGuard reaches its peer over UDP", PingService.shouldPrecheck(wg))
    }

    @Test
    fun `a profile with no usable address is not pre-checked`() {
        val p = customProfile("""{ "outbounds": [ { "protocol": "freedom" } ] }""")
        assertFalse(PingService.shouldPrecheck(p))
    }

    @Test
    fun `parser sanity for the link fixtures`() {
        assertTrue(ConfigParser.parse("trojan://pw@t.example:8443#T").port == 8443)
    }
}
