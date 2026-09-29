package dev.cluvex.zedsecure.domain.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SingBoxConfigsTest {
    private fun parse(json: String) = Json.parseToJsonElement(json).jsonObject
    private fun inbounds(json: String) = parse(json)["inbounds"]!!.jsonArray.map { it.jsonObject }
    private fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.content

    @Test
    fun `a proxy-only config gets a TUN, the rules a TUN needs, and a local port`() {
        val config = """{"inbounds":[{"type":"mixed","tag":"in","listen":"0.0.0.0","listen_port":2080}],
            "outbounds":[{"type":"trojan","tag":"t","server":"t.example","server_port":443,"password":"p"}],
            "route":{"rules":[{"domain_suffix":["ir"],"outbound":"direct"}],"final":"t"}}"""
        val prepared = SingBoxConfigs.prepareForDevice(config, socksPort = 10808)
        assertTrue(prepared.addedTun)
        val ins = inbounds(prepared.json)
        assertEquals(listOf("tun", "mixed", "mixed"), ins.map { it.str("type") })
        assertEquals(SingBoxConfigs.TUN_TAG, ins.first().str("tag"))
        assertEquals("true", ins.first().str("auto_route"))

        assertEquals(10808, prepared.socksPort)
        val route = parse(prepared.json)["route"]!!.jsonObject
        val actions = route["rules"]!!.jsonArray.map { it.jsonObject.str("action") }
        assertEquals(listOf("sniff", "hijack-dns", null), actions)
        assertEquals("the user's own route stays", "t", route.str("final"))
    }

    @Test
    fun `a config with its own TUN and loopback port is left exactly as written`() {
        val config = """{"inbounds":[
              {"type":"tun","tag":"tun-in","address":["172.18.0.1/30"],"auto_route":true},
              {"type":"mixed","tag":"local","listen":"127.0.0.1","listen_port":2080}],
            "outbounds":[{"type":"direct","tag":"direct"}],
            "route":{"rules":[{"action":"sniff"}]}}"""
        val prepared = SingBoxConfigs.prepareForDevice(config, socksPort = 10808)
        assertFalse(prepared.addedTun)
        assertEquals(2080, prepared.socksPort)
        assertEquals(parse(config), parse(prepared.json))
    }

    @Test
    fun `a config that already sniffs and hijacks DNS keeps its own rules in charge`() {
        val config = """{"outbounds":[{"type":"direct","tag":"direct"}],
            "route":{"rules":[{"action":"sniff"},{"protocol":"dns","action":"hijack-dns"}]}}"""
        val rules = parse(SingBoxConfigs.prepareForDevice(config).json)["route"]!!.jsonObject["rules"]!!.jsonArray
        assertEquals(2, rules.size)
    }

    @Test
    fun `without IPv6 the added TUN has no IPv6 address`() {
        val prepared = SingBoxConfigs.prepareForDevice("""{"outbounds":[{"type":"direct"}]}""", ipv6 = false)
        val addresses = inbounds(prepared.json).first()["address"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("172.19.0.1/30"), addresses)
    }

    @Test
    fun `on the desktop the TUN goes, a local port and a Clash API come, and routing gains sniffing`() {
        val config = """{"inbounds":[{"type":"tun","tag":"tun-in","address":["172.19.0.1/30"],"auto_route":true}],
            "outbounds":[{"type":"trojan","tag":"t","server":"t.example","server_port":443,"password":"p"}],
            "route":{"rules":[{"inbound":"tun-in","action":"sniff"},{"domain_suffix":["ir"],"outbound":"direct"}]}}"""
        val prepared = SingBoxConfigs.prepareForDesktop(config, socksPort = 10808, clashApiPort = 10817)
        val ins = inbounds(prepared.json)
        assertEquals(listOf("mixed"), ins.map { it.str("type") })
        assertEquals(10808, prepared.socksPort)
        assertEquals("127.0.0.1:10817", prepared.clashApi)
        val clash = parse(prepared.json)["experimental"]!!.jsonObject["clash_api"]!!.jsonObject
        assertEquals("127.0.0.1:10817", clash.str("external_controller"))
        val rules = parse(prepared.json)["route"]!!.jsonObject["rules"]!!.jsonArray.map { it.jsonObject }

        assertEquals("sniff", rules.first().str("action"))
        assertEquals(null, rules.first()["inbound"])
        assertTrue(rules.any { it.str("action") == "hijack-dns" })
    }

    @Test
    fun `a desktop config keeps its own Clash API and secret`() {
        val config = """{"outbounds":[{"type":"direct"}],
            "experimental":{"clash_api":{"external_controller":"0.0.0.0:9090","secret":"s3"}}}"""
        val prepared = SingBoxConfigs.prepareForDesktop(config, socksPort = 10808, clashApiPort = 10817)
        assertEquals("127.0.0.1:9090", prepared.clashApi)
        assertEquals("s3", prepared.clashSecret)
    }

    @Test
    fun `preparing twice changes nothing more`() {
        val once = SingBoxConfigs.prepareForDevice("""{"outbounds":[{"type":"direct"}]}""")
        val twice = SingBoxConfigs.prepareForDevice(once.json)
        assertEquals(parse(once.json), parse(twice.json))
        assertEquals(once.socksPort, twice.socksPort)
    }

    @Test
    fun `how the tunnel moves packets follows this device's settings, not the config's`() {
        val device = SingBoxConfigs.DeviceOptions(
            stack = "gvisor",
            strictRoute = true,
            endpointIndependentNat = true,
            mtu = 1400,
            storeCache = true,
            ntpServer = "time.example",
        )

        val added = parse(SingBoxConfigs.prepareForDevice("""{"outbounds":[{"type":"direct"}]}""", device = device).json)
        val tun = added["inbounds"]!!.jsonArray.first().jsonObject
        assertEquals("gvisor", tun.str("stack"))
        assertEquals("true", tun.str("strict_route"))
        assertEquals("true", tun.str("endpoint_independent_nat"))
        assertEquals("1400", tun.str("mtu"))
        assertEquals("true", added["experimental"]!!.jsonObject["cache_file"]!!.jsonObject.str("enabled"))
        assertEquals("time.example", added["ntp"]!!.jsonObject.str("server"))

        val own = """{"inbounds":[{"type":"tun","tag":"tun-in","address":["172.18.0.1/30"],"auto_route":true,
            "stack":"system","mtu":9000}],"outbounds":[{"type":"direct"}],"route":{"rules":[{"action":"sniff"}]}}"""
        val kept = parse(SingBoxConfigs.prepareForDevice(own, device = device).json)
            .let { it["inbounds"]!!.jsonArray.first().jsonObject }
        assertEquals("gvisor", kept.str("stack"))
        assertEquals("true", kept.str("strict_route"))
        assertEquals("the config's own size stays", "9000", kept.str("mtu"))
        assertEquals(listOf("172.18.0.1/30"), kept["address"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun `a setting left alone never rewrites the config's own tunnel`() {
        val config = """{"inbounds":[{"type":"tun","tag":"t","address":["172.18.0.1/30"],"auto_route":true},
            {"type":"mixed","tag":"local","listen":"127.0.0.1","listen_port":2080}],
            "outbounds":[{"type":"direct"}],"route":{"rules":[{"action":"sniff"},{"protocol":"dns","action":"hijack-dns"}]}}"""
        val prepared = parse(SingBoxConfigs.prepareForDevice(config, device = SingBoxConfigs.DeviceOptions()).json)
        assertNull("no cache file unless asked", prepared["experimental"])
        assertNull("no time server unless asked", prepared["ntp"])
    }

    @Test
    fun `a config that keeps its own cache or time server is not overruled`() {
        val config = """{"outbounds":[{"type":"direct"}],
            "experimental":{"cache_file":{"enabled":false,"store_fakeip":true}},
            "ntp":{"enabled":true,"server":"ntp.example"}}"""
        val device = SingBoxConfigs.DeviceOptions(storeCache = true, ntpServer = "time.example")
        val prepared = parse(SingBoxConfigs.prepareForDevice(config, device = device).json)
        assertEquals("false", prepared["experimental"]!!.jsonObject["cache_file"]!!.jsonObject.str("enabled"))
        assertEquals("ntp.example", prepared["ntp"]!!.jsonObject.str("server"))
    }
}
