package dev.cluvex.zedsecure.domain.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerlessChainTest {
    private val serverless = """
        {
          "outbounds": [
            { "tag": "block", "protocol": "block" },
            { "tag": "tcp-direct", "protocol": "direct" },
            { "tag": "tcp-fragment-tls", "protocol": "direct", "streamSettings": { "finalmask": { "tcp": [
              { "type": "fragment", "settings": { "packets": "tlshello", "lengths": ["0", "104", "1"] } } ] } } },
            { "tag": "udp-noises", "protocol": "direct", "streamSettings": { "finalmask": { "udp": [
              { "type": "noise", "settings": { "reset": "28" } } ] } } }
          ],
          "routing": { "rules": [
            { "outboundTag": "block", "domain": ["geosite:category-ads-all"] },
            { "outboundTag": "tcp-fragment-tls", "network": "tcp", "protocol": ["tls"], "ip": ["0.0.0.0/0"] },
            { "outboundTag": "tcp-direct", "network": "tcp", "ip": ["0.0.0.0/0"] } ] }
        }
    """.trimIndent()

    private val vless = ServerConfig(
        protocol = Protocol.VLESS,
        remark = "S",
        address = "server.example.com",
        port = 443,
        userId = "11111111-2222-3333-4444-555555555555",
    )

    private val wireguard = ServerConfig(
        protocol = Protocol.WIREGUARD,
        remark = "W",
        address = "wg.example.com",
        port = 51820,
        secretKey = "c2VjcmV0c2VjcmV0c2VjcmV0c2VjcmV0c2VjcmV0c2U=",
        peerPublicKey = "cHVibGljcHVibGljcHVibGljcHVibGljcHVibGljcHU=",
    )

    private fun outbounds(json: String) =
        Json.parseToJsonElement(json).jsonObject["outbounds"]!!.jsonArray.map { it.jsonObject }

    private fun JsonObject.tag() = this["tag"]?.jsonPrimitive?.content

    private fun JsonObject.dialer() =
        this["streamSettings"]?.jsonObject?.get("sockopt")?.jsonObject?.get("dialerProxy")?.jsonPrimitive?.content

    @Test
    fun `a server behind a serverless config dials out through its TLS fragment outbound`() {
        val json = XrayJsonBuilder.buildChainHops(listOf(XrayJsonBuilder.ChainHop.Serverless(serverless), XrayJsonBuilder.ChainHop.Xray(vless)))
        val all = outbounds(json)
        val proxy = all.first()
        val dialer = all.firstOrNull { it.tag() == XrayJsonBuilder.SERVERLESS_DIALER_TAG }

        assertEquals("proxy", proxy.tag())
        assertEquals(XrayJsonBuilder.SERVERLESS_DIALER_TAG, proxy.dialer())
        assertNotNull(dialer)
        assertTrue(dialer!!["streamSettings"]!!.jsonObject["finalmask"]!!.jsonObject.containsKey("tcp"))
        assertFalse("the serverless block outbound is not carried over", all.any { it.tag() == "block" && it.containsKey("streamSettings") })
    }

    @Test
    fun `a UDP server behind a serverless config takes its noise outbound`() {
        val json = XrayJsonBuilder.buildChainHops(listOf(XrayJsonBuilder.ChainHop.Serverless(serverless), XrayJsonBuilder.ChainHop.Xray(wireguard)))
        val dialer = outbounds(json).first { it.tag() == XrayJsonBuilder.SERVERLESS_DIALER_TAG }

        assertTrue(dialer["streamSettings"]!!.jsonObject["finalmask"]!!.jsonObject.containsKey("udp"))
    }

    @Test
    fun `a serverless config anywhere but first is refused`() {
        val e = assertThrows(ServerlessHopException::class.java) {
            XrayJsonBuilder.buildChainHops(listOf(XrayJsonBuilder.ChainHop.Xray(vless), XrayJsonBuilder.ChainHop.Serverless(serverless)))
        }
        assertEquals(ServerlessHopException.Reason.NotFirst, e.reason)
    }

    @Test
    fun `a serverless config needs a server after it`() {
        val e = assertThrows(ServerlessHopException::class.java) {
            XrayJsonBuilder.buildChainHops(listOf(XrayJsonBuilder.ChainHop.Serverless(serverless)))
        }
        assertEquals(ServerlessHopException.Reason.NoServer, e.reason)
    }

    @Test
    fun `only configs without a proxy outbound count as serverless`() {
        assertTrue(CustomConfig.isServerless(serverless))
        assertFalse(CustomConfig.isServerless("""{"outbounds":[{"protocol":"vless","settings":{}},{"protocol":"freedom"}]}"""))
        assertFalse(CustomConfig.isServerless("""{"outbounds":[{"protocol":"block"}]}"""))
        assertFalse(CustomConfig.isServerless("not json"))
    }
}
