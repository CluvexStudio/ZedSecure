package dev.cluvex.zedsecure.domain.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SingBoxJsonTest {
    private val subscription = """
        {
          "log": {"level": "warn"},
          "dns": {"servers": [{"type": "https", "tag": "remote", "server": "1.1.1.1"}]},
          "inbounds": [{"type": "tun", "tag": "tun-in", "address": ["172.19.0.1/30"], "auto_route": true}],
          "outbounds": [
            {"type": "selector", "tag": "proxy", "outbounds": ["auto", "DE TUIC", "NL AnyTLS"], "default": "NL AnyTLS"},
            {"type": "urltest", "tag": "auto", "outbounds": ["DE TUIC", "NL AnyTLS"]},
            {"type": "tuic", "tag": "DE TUIC", "server": "de.example", "server_port": 443,
             "uuid": "2dd61d93-75d8-4da4-ac0e-6aece7eac365", "password": "p", "tls": {"enabled": true}},
            {"type": "anytls", "tag": "NL AnyTLS", "server": "nl.example", "server_port": 8443,
             "password": "p", "tls": {"enabled": true, "server_name": "nl.example"}},
            {"type": "shadowsocks", "tag": "FR ShadowTLS", "method": "2022-blake3-aes-128-gcm",
             "password": "k", "detour": "fr-stls"},
            {"type": "shadowtls", "tag": "fr-stls", "server": "fr.example", "server_port": 443,
             "version": 3, "password": "s", "tls": {"enabled": true, "server_name": "cdn.example"}},
            {"type": "direct", "tag": "direct"},
            {"type": "block", "tag": "block"}
          ],
          "endpoints": [
            {"type": "wireguard", "tag": "WG", "address": ["10.0.0.2/32"], "private_key": "x",
             "peers": [{"address": "wg.example", "port": 51820, "public_key": "y", "allowed_ips": ["0.0.0.0/0"]}]}
          ],
          "route": {"final": "proxy", "rules": [{"action": "sniff"}]}
        }
    """.trimIndent()

    private val xrayConfig = """
        {"outbounds":[{"tag":"proxy","protocol":"vless","settings":{"vnext":[{"address":"a.example","port":443,
          "users":[{"id":"2dd61d93-75d8-4da4-ac0e-6aece7eac365","encryption":"none"}]}]}},
          {"tag":"direct","protocol":"freedom"}]}
    """.trimIndent()

    @Test
    fun `sing-box and Xray configs are told apart`() {
        assertEquals(SingBoxJson.Shape.FullConfig, SingBoxJson.shapeOf(subscription))
        assertEquals(SingBoxJson.Shape.None, SingBoxJson.shapeOf(xrayConfig))
        assertTrue("the Xray check must not claim sing-box JSON", !CustomConfig.looksLikeCustomJson(subscription))
        assertTrue(CustomConfig.looksLikeCustomJson(xrayConfig))
        assertEquals(SingBoxJson.Shape.None, SingBoxJson.shapeOf("vless://x@y:1"))
        assertEquals(SingBoxJson.Shape.None, SingBoxJson.shapeOf("{not json"))
    }

    @Test
    fun `a single outbound, a list and a bare fragment are recognised`() {
        assertEquals(
            SingBoxJson.Shape.Outbound,
            SingBoxJson.shapeOf("""{"type":"hysteria2","server":"h.example","server_port":443,"password":"p"}"""),
        )
        assertEquals(
            SingBoxJson.Shape.OutboundList,
            SingBoxJson.shapeOf("""[{"type":"trojan","server":"t.example","server_port":443,"password":"p"},{"type":"direct"}]"""),
        )
        assertEquals(
            SingBoxJson.Shape.Fragment,
            SingBoxJson.shapeOf("""{"outbounds":[{"type":"vmess","server":"v.example","server_port":443,"uuid":"u"}]}"""),
        )
    }

    @Test
    fun `servers are the proxies and endpoints, never groups, sinks or another server's hop`() {
        val servers = SingBoxJson.servers(subscription)
        assertEquals(listOf("DE TUIC", "NL AnyTLS", "FR ShadowTLS", "WG"), servers.map { it.tag })
        assertEquals(listOf("tuic", "anytls", "shadowsocks", "wireguard"), servers.map { it.type })
        assertEquals("de.example" to 443, servers[0].address to servers[0].port)
        assertEquals("wg.example" to 51820, servers[3].address to servers[3].port)
        assertTrue(servers[0].udp)
        assertFalse(servers[1].udp)
        assertEquals("TUIC", servers[0].label)
    }

    @Test
    fun `a server brings the outbounds it dials through`() {
        val stls = SingBoxJson.servers(subscription).single { it.tag == "FR ShadowTLS" }
        val outbounds = Json.parseToJsonElement(stls.fragment).jsonObject["outbounds"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("FR ShadowTLS", "fr-stls"), outbounds.map { it["tag"]!!.jsonPrimitive.content })

        assertEquals("fr.example" to 443, stls.address to stls.port)
    }

    @Test
    fun `a missing hop drops that server rather than importing it half-built`() {
        val broken = """{"outbounds":[{"type":"shadowsocks","tag":"a","method":"none","detour":"gone"},
            {"type":"trojan","tag":"b","server":"b.example","server_port":443,"password":"p"}]}"""
        assertEquals(listOf("b"), SingBoxJson.servers(broken).map { it.tag })
    }

    @Test
    fun `endpoints stay endpoints in the fragment`() {
        val wg = SingBoxJson.servers(subscription).single { it.tag == "WG" }
        val fragment = Json.parseToJsonElement(wg.fragment).jsonObject
        assertEquals(1, (fragment["endpoints"] as JsonArray).size)
        assertNull(fragment["outbounds"])
    }

    @Test
    fun `the probe follows the route through the selector to its choice`() {
        assertEquals("NL AnyTLS", SingBoxJson.probeServer(subscription)!!.tag)
        val urltestFirst = subscription.replace("\"final\": \"proxy\"", "\"final\": \"auto\"")
        assertEquals("DE TUIC", SingBoxJson.probeServer(urltestFirst)!!.tag)
    }

    @Test
    fun `untagged servers still get a stable name`() {
        val servers = SingBoxJson.servers("""[{"type":"trojan","server":"t.example","server_port":443,"password":"p"}]""")
        assertEquals("trojan-0", servers.single().tag)
        val outbound = (Json.parseToJsonElement(servers.single().fragment).jsonObject["outbounds"] as JsonArray)[0] as JsonObject
        assertEquals("trojan-0", outbound["tag"]!!.jsonPrimitive.content)
    }
}
