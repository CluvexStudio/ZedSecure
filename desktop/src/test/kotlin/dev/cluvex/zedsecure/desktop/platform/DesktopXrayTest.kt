package dev.cluvex.zedsecure.desktop.platform

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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

    @Test
    fun `only the SOCKS inbound moves to the new port`() {
        val config = """{"inbounds":[{"tag":"socks-in","listen":"127.0.0.1","port":10808,"protocol":"socks"},""" +
            """{"tag":"dns-in","port":10853,"protocol":"dokodemo-door"}],"outbounds":[{"protocol":"freedom"}]}"""
        val moved = Json.parseToJsonElement(DesktopXray.withSocksPort(config, 10808, 10818)).jsonObject
        val ports = moved["inbounds"]!!.jsonArray.map { it.jsonObject["port"]!!.jsonPrimitive.int }
        assertEquals(listOf(10818, 10853), ports)
        assertEquals(config, DesktopXray.withSocksPort(config, 10808, 10808))
    }

    @Test
    fun `with TUN the core's own connections leave through the adapter and resolve servers to the addresses found before`() {
        val config = """
            {"outbounds":[
              {"tag":"proxy","protocol":"vless","settings":{"vnext":[{"address":"a.example.com","port":443}]},"streamSettings":{"network":"ws"}},
              {"tag":"direct","protocol":"freedom"},
              {"tag":"block","protocol":"blackhole"},
              {"tag":"chained","protocol":"trojan","settings":{"servers":[{"address":"b.example.com","port":443}]},"streamSettings":{"sockopt":{"dialerProxy":"proxy"}}},
              {"tag":"local","protocol":"socks","settings":{"servers":[{"address":"127.0.0.1","port":1080}]}},
              {"tag":"hy","protocol":"hysteria","settings":{"address":"c.example.com","port":443}}
            ],
            "dns":{"hosts":{"a.example.com":"192.0.2.99"}}}
        """.trimIndent()
        val pinned = mapOf("a.example.com" to listOf("203.0.113.1"), "c.example.com" to listOf("203.0.113.3", "203.0.113.4"))
        val root = json.parseToJsonElement(DesktopXray.bindOutbounds(config, "Wi-Fi", pinned)).jsonObject
        val byTag = root["outbounds"]!!.jsonArray.associateBy { it.jsonObject["tag"]!!.jsonPrimitive.content }
        fun sockopt(tag: String) = byTag.getValue(tag).jsonObject["streamSettings"]?.jsonObject?.get("sockopt")?.jsonObject

        assertEquals("Wi-Fi", sockopt("proxy")!!["interface"]!!.jsonPrimitive.content)
        assertEquals("UseIP", sockopt("proxy")!!["domainStrategy"]!!.jsonPrimitive.content)
        assertEquals("ws", byTag.getValue("proxy").jsonObject["streamSettings"]!!.jsonObject["network"]!!.jsonPrimitive.content)
        assertEquals("Wi-Fi", sockopt("direct")!!["interface"]!!.jsonPrimitive.content)
        assertNull(sockopt("direct")!!["domainStrategy"], "direct traffic keeps resolving the way it did")
        assertNull(sockopt("block"))
        assertNull(sockopt("chained")!!["interface"], "a chained hop dials through the outbound it names")
        assertNull(sockopt("local"), "a local engine is reached over loopback")
        assertEquals("UseIP", sockopt("hy")!!["domainStrategy"]!!.jsonPrimitive.content)

        val hosts = root["dns"]!!.jsonObject["hosts"]!!.jsonObject
        assertEquals("192.0.2.99", hosts["a.example.com"]!!.jsonPrimitive.content, "a host the user set stays theirs")
        assertEquals(listOf("203.0.113.3", "203.0.113.4"), hosts["c.example.com"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun `without servers to pin the DNS section is left alone`() {
        val root = json.parseToJsonElement(DesktopXray.bindOutbounds("""{"outbounds":[{"protocol":"freedom"}]}""", "en0")).jsonObject
        assertNull(root["dns"])
    }

    @Test
    fun `a bare IPv6 server stays whole, Hysteria's server is found and a DNS outbound is not a server`() {
        val config = """
            {"outbounds":[
              {"protocol":"vless","settings":{"vnext":[{"address":"2001:db8::7","port":443}]}},
              {"protocol":"hysteria","settings":{"address":"h.example.com","port":443}},
              {"protocol":"dns","settings":{"address":"1.1.1.1","port":53}}
            ]}
        """.trimIndent()
        assertEquals(listOf("2001:db8::7", "h.example.com"), DesktopXray.extractServerHosts(config))
    }
}
