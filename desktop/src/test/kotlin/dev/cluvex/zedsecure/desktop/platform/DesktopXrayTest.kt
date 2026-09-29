package dev.cluvex.zedsecure.desktop.platform

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopXrayTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `server hosts are found in every outbound shape`() {
        val config = """
            {"outbounds":[
              {"protocol":"vless","settings":{"vnext":[{"address":"a.example.com","port":443}]}},
              {"protocol":"shadowsocks","settings":{"servers":[{"address":"b.example.com","port":8388}]}},
              {"protocol":"wireguard","settings":{"peers":[{"endpoint":"c.example.com:51820"}]}}
            ]}
        """.trimIndent()
        assertEquals(listOf("a.example.com", "b.example.com", "c.example.com"), DesktopXray.extractServerHosts(config))
    }

    @Test
    fun `an IPv6 endpoint keeps its address and loses its brackets and port`() {
        val config = """{"outbounds":[{"settings":{"peers":[{"endpoint":"[2001:db8::1]:51820"}]}}]}"""
        assertEquals(listOf("2001:db8::1"), DesktopXray.extractServerHosts(config))
    }

    @Test
    fun `loopback and placeholders are not routed around`() {
        val config = """
            {"outbounds":[{"settings":{"vnext":[
              {"address":"127.0.0.1"},{"address":"localhost"},{"address":"•••"},{"address":"real.example.com"}
            ]}}]}
        """.trimIndent()
        assertEquals(listOf("real.example.com"), DesktopXray.extractServerHosts(config))
    }

    @Test
    fun `a config that is not JSON yields no hosts instead of throwing`() {
        assertEquals(emptyList<String>(), DesktopXray.extractServerHosts("not json at all"))
        assertEquals(emptyList<String>(), DesktopXray.extractServerHosts(""))
    }

    @Test
    fun `metrics are added and the counters turned on`() {
        val out = json.parseToJsonElement(DesktopXray.augmentWithMetrics("""{"outbounds":[]}""", 9100)).jsonObject
        assertEquals("127.0.0.1:9100", out.getValue("metrics").jsonObject.getValue("listen").jsonPrimitive.content)
        val sys = out.getValue("policy").jsonObject.getValue("system").jsonObject
        assertTrue(sys.getValue("statsOutboundUplink").jsonPrimitive.boolean)
        assertTrue(sys.getValue("statsOutboundDownlink").jsonPrimitive.boolean)
        assertTrue("stats" in out)
    }

    @Test
    fun `what the user already wrote survives the rewrite`() {
        val config = """
            {"log":{"loglevel":"warning"},
             "outbounds":[{"protocol":"freedom"}],
             "policy":{"levels":{"8":{"connIdle":300}},"system":{"statsInboundUplink":true}}}
        """.trimIndent()
        val out = json.parseToJsonElement(DesktopXray.augmentWithMetrics(config, 9100)).jsonObject

        assertEquals("warning", out.getValue("log").jsonObject.getValue("loglevel").jsonPrimitive.content)
        assertTrue("outbounds" in out)
        val policy = out.getValue("policy").jsonObject
        assertEquals("300", policy.getValue("levels").jsonObject.getValue("8").jsonObject.getValue("connIdle").jsonPrimitive.content)
        val sys = policy.getValue("system").jsonObject
        assertTrue(sys.getValue("statsInboundUplink").jsonPrimitive.boolean, "an existing system policy key was dropped")
        assertTrue(sys.getValue("statsOutboundUplink").jsonPrimitive.boolean)
    }

    @Test
    fun `a broken config is handed back untouched rather than replaced`() {
        assertEquals("{ not json", DesktopXray.augmentWithMetrics("{ not json", 9100))
    }

    @Test
    fun `running it twice leaves one metrics block, not two`() {
        val once = DesktopXray.augmentWithMetrics("""{"outbounds":[]}""", 9100)
        val twice = DesktopXray.augmentWithMetrics(once, 9200)
        val out: JsonObject = json.parseToJsonElement(twice).jsonObject
        assertEquals("127.0.0.1:9200", out.getValue("metrics").jsonObject.getValue("listen").jsonPrimitive.content)
        assertEquals(1, twice.split("\"metrics\"").size - 1)
    }
}
