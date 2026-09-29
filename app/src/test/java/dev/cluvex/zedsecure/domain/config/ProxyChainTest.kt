package dev.cluvex.zedsecure.domain.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ProxyChainTest {
    private fun parse(link: String) = ConfigParser.parse(link)

    private val vless1 = parse("vless://11111111-1111-1111-1111-111111111111@a.example:443?encryption=none&security=tls&type=tcp#A")
    private val vless2 = parse("vless://22222222-2222-2222-2222-222222222222@b.example:443?encryption=none&security=tls&type=tcp#B")
    private val vless3 = parse("vless://33333333-3333-3333-3333-333333333333@c.example:443?encryption=none&security=tls&type=tcp#C")

    private fun outbounds(json: String): List<JsonObject> =
        Json.parseToJsonElement(json).jsonObject["outbounds"]!!.jsonArray.map { it.jsonObject }

    private fun tags(json: String) = outbounds(json).map { it["tag"]!!.jsonPrimitive.content }

    private fun dialerOf(o: JsonObject): String? =
        o["streamSettings"]?.jsonObject?.get("sockopt")?.jsonObject?.get("dialerProxy")?.jsonPrimitive?.content

    @Test
    fun `chain is emitted exit-first so the default handler is the exit`() {
        val json = XrayJsonBuilder.buildChain(listOf(vless1, vless2, vless3))
        val tags = tags(json)

        assertEquals("proxy", tags.first())
        assertTrue("proxy-h1 missing: $tags", "proxy-h1" in tags)
        assertTrue("proxy-h0 missing: $tags", "proxy-h0" in tags)
        assertTrue(tags.indexOf("proxy") < tags.indexOf("proxy-h1"))
    }

    @Test
    fun `each hop dials the one before it and the entry dials raw`() {
        val json = XrayJsonBuilder.buildChain(listOf(vless1, vless2, vless3))
        val byTag = outbounds(json).associateBy { it["tag"]!!.jsonPrimitive.content }
        assertEquals("proxy-h1", dialerOf(byTag.getValue("proxy")))
        assertEquals("proxy-h0", dialerOf(byTag.getValue("proxy-h1")))
        assertNull("entry hop must make the physical dial", dialerOf(byTag.getValue("proxy-h0")))

        assertEquals("c.example", byTag.getValue("proxy")["settings"]!!.jsonObject["vnext"]!!.jsonArray[0].jsonObject["address"]!!.jsonPrimitive.content)
    }

    @Test
    fun `only the entry hop uses the fragment dialer`() {
        val json = XrayJsonBuilder.buildChain(
            listOf(vless1, vless2, vless3),
            options = XrayJsonBuilder.BuildOptions(fragmentEnabled = true),
        )
        val byTag = outbounds(json).associateBy { it["tag"]!!.jsonPrimitive.content }

        assertEquals("fragment", dialerOf(byTag.getValue("proxy-h0")))
        assertEquals("proxy-h0", dialerOf(byTag.getValue("proxy-h1")))
        assertEquals("proxy-h1", dialerOf(byTag.getValue("proxy")))
        assertTrue("fragment" in byTag.keys)
    }

    @Test
    fun `single member chain equals a single server build`() {
        assertEquals(
            XrayJsonBuilder.build(vless1),
            XrayJsonBuilder.buildChain(listOf(vless1)),
        )
    }

    @Test
    fun `wireguard hop carries the dialerProxy instead of dialing out directly`() {
        val wg = parse("wireguard://aPrivateKeyValue000000000000000000000000000%3D@wg.example:51820?publickey=cHVia2V5MDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDA%3D&address=10.8.0.2%2F32#WG")
        val json = XrayJsonBuilder.buildChain(listOf(vless1, wg))
        val exit = outbounds(json).first { it["tag"]!!.jsonPrimitive.content == "proxy" }
        assertEquals("wireguard", exit["protocol"]!!.jsonPrimitive.content)

        assertEquals("proxy-h0", dialerOf(exit))
    }

    @Test
    fun `a UDP hop after an HTTP hop is rejected rather than silently black-holed`() {
        val http = parse("http://user:pass@h.example:8080#H")
        val wg = parse("wireguard://aPrivateKeyValue000000000000000000000000000%3D@wg.example:51820?publickey=cHVia2V5MDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDA%3D&address=10.8.0.2%2F32#WG")
        try {
            XrayJsonBuilder.buildChain(listOf(http, wg))
            fail("expected UdpHopUnsupportedException")
        } catch (e: UdpHopUnsupportedException) {
            assertEquals("WireGuard", e.member)
            assertEquals("HTTP", e.previous)
        }
    }

    @Test
    fun `mux rides only on the exit hop`() {
        val json = XrayJsonBuilder.buildChain(
            listOf(vless1, vless2, vless3),
            options = XrayJsonBuilder.BuildOptions(muxEnabled = true),
        )
        val byTag = outbounds(json).associateBy { it["tag"]!!.jsonPrimitive.content }
        assertNotNull(byTag.getValue("proxy")["mux"])

        assertNull(byTag.getValue("proxy-h0")["mux"])
        assertNull(byTag.getValue("proxy-h1")["mux"])
    }

    @Test
    fun `a speedtest chain strips routing but keeps every hop`() {
        val json = XrayJsonBuilder.buildChain(listOf(vless1, vless2), forSpeedtest = true)
        val root = Json.parseToJsonElement(json).jsonObject
        assertEquals(0, (root["inbounds"] as JsonArray).size)
        assertEquals(0, root["routing"]!!.jsonObject["rules"]!!.jsonArray.size)
        val byTag = outbounds(json).associateBy { it["tag"]!!.jsonPrimitive.content }

        assertEquals("proxy-h0", dialerOf(byTag.getValue("proxy")))
    }
}
