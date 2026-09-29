package dev.cluvex.zedsecure.domain.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SniSpoofCustomTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val customJson = """
        {
          "remarks": "Provider node",
          "outbounds": [
            {
              "tag": "proxy",
              "protocol": "vless",
              "settings": {
                "vnext": [
                  { "address": "edge.example.com", "port": 8443,
                    "users": [ { "id": "11111111-2222-3333-4444-555555555555", "encryption": "none" } ] }
                ]
              },
              "streamSettings": { "network": "ws", "security": "tls" }
            },
            { "tag": "direct", "protocol": "freedom" }
          ]
        }
    """.trimIndent()

    private fun profile(underlying: String) = VpnProfile.fromSniSpoof(
        SniSpoofProfile(link = underlying, fakeSni = "www.speedtest.net"),
        id = "id-1",
        addedAt = 0L,
        name = "",
    )

    @Test
    fun `a custom JSON config is accepted and its endpoint read`() {
        val p = profile(customJson)
        assertEquals("edge.example.com", p.address)
        assertEquals(8443, p.port)

        assertEquals("Provider node", p.name)
    }

    @Test
    fun `the built config keeps the provider outbound the forwarder rewrite needs`() {
        val built = profile(customJson).toXrayConfigJson()
        val proxy = json.parseToJsonElement(built).jsonObject["outbounds"]!!.jsonArray
            .map { it.jsonObject }
            .first { it["protocol"]?.jsonPrimitive?.content == "vless" }
        val node = proxy["settings"]!!.jsonObject["vnext"]!!.jsonArray[0].jsonObject

        assertEquals("edge.example.com", node["address"]!!.jsonPrimitive.content)
        assertEquals(8443, node["port"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `a share link still works exactly as before`() {
        val link = "vless://11111111-2222-3333-4444-555555555555@link.example.com:443" +
            "?encryption=none&security=tls&type=ws#Linked"
        val p = profile(link)
        assertEquals("link.example.com", p.address)
        assertEquals(443, p.port)
        assertEquals("Linked", p.name)
        assertNotNull(p.toXrayConfigJson())
    }

    @Test
    fun `an unparseable underlying config degrades instead of throwing`() {
        val p = profile("this is neither a link nor json")
        assertEquals("sni-spoof", p.address)
        assertEquals(0, p.port)
        assertEquals("SNI spoof", p.name)
    }

    @Test
    fun `custom detection does not misfire on a share link`() {
        assertTrue(CustomConfig.looksLikeCustomJson(customJson))
        assertEquals(false, CustomConfig.looksLikeCustomJson("vless://x@y:443?type=ws#n"))
    }
}
