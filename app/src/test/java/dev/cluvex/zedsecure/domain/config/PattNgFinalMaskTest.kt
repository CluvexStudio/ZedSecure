package dev.cluvex.zedsecure.domain.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PattNgFinalMaskTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val cipherSuites =
        "TLS_AES_256_GCM_SHA384:TLS_CHACHA20_POLY1305_SHA256:TLS_AES_128_GCM_SHA256"

    private val finalMask =
        """{"tcp": [{"type": "fragment", "settings": {"packets": "tlshello", "lengths": ["5", "94", "1"], "delays": ["0"], "maxSplit": "0"}},""" +
            """{"type": "fragment", "settings": {"packets": "1-1", "lengths": ["109", "1"], "delays": ["1"], "maxSplit": "355"}}]}"""

    private fun urlEncode(v: String) = java.net.URLEncoder.encode(v, "UTF-8").replace("+", "%20")

    private fun link(extra: String = "") =
        "vless://11111111-2222-3333-4444-555555555555@example.com:443" +
            "?encryption=none&security=tls&type=ws&host=cdn.example.com&path=%2Fassignment" +
            "&sni=cdn.example.com&fp=unsafe" +
            "&cs=" + cipherSuites +
            "&fm=" + urlEncode(finalMask) +
            extra + "#PattNG"

    private fun streamOf(configJson: String): JsonObject =
        json.parseToJsonElement(configJson).jsonObject["outbounds"]!!.jsonArray
            .map { it.jsonObject }
            .first { it["protocol"]?.jsonPrimitive?.content == "vless" }["streamSettings"]!!.jsonObject

    @Test
    fun `parses all three PattNG keys`() {
        val c = ConfigParser.parse(link())
        assertEquals("unsafe", c.tls.fingerprint)
        assertEquals(cipherSuites, c.tls.cipherSuites)
        assertEquals(finalMask, c.transport.finalMask)
    }

    @Test
    fun `finalmask reaches the generated streamSettings`() {
        val stream = streamOf(XrayJsonBuilder.build(ConfigParser.parse(link())))
        val mask = stream["finalmask"]?.jsonObject
        assertNotNull("streamSettings.finalmask must be emitted", mask)
        val tcp = mask!!["tcp"]!!.jsonArray
        assertEquals("both fragment stages must survive", 2, tcp.size)
        assertEquals("fragment", tcp[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(
            "tlshello",
            tcp[0].jsonObject["settings"]!!.jsonObject["packets"]!!.jsonPrimitive.content,
        )

        assertEquals(
            "355",
            tcp[1].jsonObject["settings"]!!.jsonObject["maxSplit"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `unsafe fingerprint and cipher suites survive into tlsSettings`() {
        val stream = streamOf(XrayJsonBuilder.build(ConfigParser.parse(link())))
        val tls = stream["tlsSettings"]!!.jsonObject
        assertEquals("unsafe", tls["fingerprint"]!!.jsonPrimitive.content)
        assertTrue(
            "cipherSuites must be passed through for fp=unsafe to mean anything",
            tls["cipherSuites"]!!.jsonPrimitive.content.contains("TLS_AES_256_GCM_SHA384"),
        )
    }

    @Test
    fun `fm round-trips through the share link`() {
        val c = ConfigParser.parse(link())
        val reparsed = ConfigParser.parse(ShareLink.build(c))
        assertEquals(finalMask, reparsed.transport.finalMask)
        assertEquals(cipherSuites, reparsed.tls.cipherSuites)
        assertEquals("unsafe", reparsed.tls.fingerprint)
    }

    @Test
    fun `invalid fm json is dropped rather than breaking the config`() {
        val bad = "vless://11111111-2222-3333-4444-555555555555@example.com:443" +
            "?encryption=none&security=tls&type=ws&fm=not-json#Bad"
        val stream = streamOf(XrayJsonBuilder.build(ConfigParser.parse(bad)))
        assertNull("a broken fm must not emit finalmask", stream["finalmask"])
    }

    @Test
    fun `fm merges with a transport-generated finalmask instead of replacing it`() {
        val kcp = "vless://11111111-2222-3333-4444-555555555555@example.com:443" +
            "?encryption=none&security=tls&type=kcp&headerType=dtls&seed=abc" +
            "&fm=" + urlEncode(finalMask) + "#Kcp"
        val stream = streamOf(XrayJsonBuilder.build(ConfigParser.parse(kcp)))
        val mask = stream["finalmask"]!!.jsonObject
        assertNotNull("the user's tcp masks must be present", mask["tcp"])
        assertNotNull("mKCP's own udp masks must survive", mask["udp"])
    }
}
