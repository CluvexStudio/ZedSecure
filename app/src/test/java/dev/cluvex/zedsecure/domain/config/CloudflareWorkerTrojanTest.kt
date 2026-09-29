package dev.cluvex.zedsecure.domain.config

import dev.cluvex.zedsecure.domain.model.AppSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudflareWorkerTrojanTest {
    private val link = "trojan://00000000-0000-4000-8000-000000000001@104.16.35.164:443" +
        "?cs=TLS_AES_256_GCM_SHA384%3ATLS_CHACHA20_POLY1305_SHA256%3ATLS_AES_128_GCM_SHA256" +
        "&path=%2F%3Fed%3D2048&security=tls" +
        "&fm=%7B%22tcp%22%3A%20%5B%7B%22type%22%3A%20%22fragment%22%2C%20%22settings%22%3A%20" +
        "%7B%22packets%22%3A%20%22tlshello%22%2C%20%22lengths%22%3A%20%5B%225%22%2C%2294%22%2C%20%221%22%5D%2C%20" +
        "%22delays%22%3A%20%5B%220%22%5D%2C%20%22maxSplit%22%3A%20%220%22%7D%7D%5D%7D" +
        "&insecure=0&host=0000abcd.edge-example.pages.dev&fp=unsafe&type=ws&allowInsecure=0" +
        "&sni=0000abcd.edge-example.pages.dev#Worker"

    private fun outbound(tag: String, json: String) = Json.parseToJsonElement(json).jsonObject["outbounds"]!!
        .jsonArray.map { it.jsonObject }.first { it["tag"]?.jsonPrimitive?.content == tag }

    @Test
    fun `the link parses into what it says`() {
        val c = ConfigParser.parse(link)
        assertEquals(Protocol.TROJAN, c.protocol)
        assertEquals("104.16.35.164", c.address)
        assertEquals(443, c.port)
        assertEquals("00000000-0000-4000-8000-000000000001", c.userId)
        assertEquals("ws", c.transport.network)
        assertEquals("the path keeps its own query - ed=2048 is early data", "/?ed=2048", c.transport.path)
        assertEquals("0000abcd.edge-example.pages.dev", c.transport.host)
        assertEquals("tls", c.tls.security)
        assertEquals("0000abcd.edge-example.pages.dev", c.tls.sni)
        assertEquals("unsafe", c.tls.fingerprint)
        assertTrue("cipher suites survive the link", c.tls.cipherSuites.orEmpty().contains("TLS_AES_256_GCM_SHA384"))
    }

    @Test
    fun `the core config carries the whole shape`() {
        val json = XrayJsonBuilder.build(ConfigParser.parse(link), options = AppSettings().toBuildOptions())
        val proxy = outbound("proxy", json)
        assertEquals("trojan", proxy["protocol"]!!.jsonPrimitive.content)

        val stream = proxy["streamSettings"]!!.jsonObject
        assertEquals("ws", stream["network"]!!.jsonPrimitive.content)
        val ws = stream["wsSettings"]!!.jsonObject
        assertEquals("/?ed=2048", ws["path"]!!.jsonPrimitive.content)
        val hostHeader = ws["host"]?.jsonPrimitive?.content
            ?: ws["headers"]?.jsonObject?.get("Host")?.jsonPrimitive?.content
        assertEquals("0000abcd.edge-example.pages.dev", hostHeader)

        val tls = stream["tlsSettings"]!!.jsonObject
        assertEquals("0000abcd.edge-example.pages.dev", tls["serverName"]!!.jsonPrimitive.content)
        assertEquals("unsafe", tls["fingerprint"]!!.jsonPrimitive.content)
        assertTrue(
            "the link's cipher suites reach the core",
            tls["cipherSuites"]!!.jsonPrimitive.content.contains("TLS_AES_256_GCM_SHA384"),
        )

        assertTrue("the fm programme reaches the core", json.contains("tlshello"))

        System.getenv("ZED_DUMP_DIR")?.let { dir ->
            java.io.File(dir).mkdirs()
            java.io.File(dir, "cf-worker-connect.json").writeText(json)
            java.io.File(dir, "cf-worker-probe.json").writeText(
                XrayJsonBuilder.build(
                    ConfigParser.parse(link),
                    options = AppSettings().toBuildOptions(),
                    forSpeedtest = true,
                ),
            )
        }
    }
}
