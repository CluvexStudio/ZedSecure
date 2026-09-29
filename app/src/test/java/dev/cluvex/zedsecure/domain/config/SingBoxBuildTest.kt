package dev.cluvex.zedsecure.domain.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SingBoxBuildTest {
    private val tuic = SingBoxJson.servers(
        """{"type":"tuic","tag":"DE","server":"de.example","server_port":443,"uuid":"u","password":"p","tls":{"enabled":true}}""",
    ).single()

    private fun outbounds(json: String): List<JsonObject> =
        Json.parseToJsonElement(json).jsonObject["outbounds"]!!.jsonArray.map { it.jsonObject }

    private fun JsonObject.tag() = this["tag"]?.jsonPrimitive?.content

    private fun JsonObject.dialerProxy() = ((this["streamSettings"] as? JsonObject)?.get("sockopt") as? JsonObject)
        ?.get("dialerProxy")?.jsonPrimitive?.content

    @Test
    fun `the proxy outbound is the singbox outbound carrying the fragment`() {
        val config = XrayJsonBuilder.buildSingBox(tuic.fragment, tuic.tag)
        val proxy = outbounds(config).first()
        assertEquals("proxy", proxy.tag())
        assertEquals("singbox", proxy["protocol"]!!.jsonPrimitive.content)
        val settings = proxy["settings"]!!.jsonObject
        assertEquals("DE", settings["use"]!!.jsonPrimitive.content)
        assertEquals("true", settings["viaXray"]!!.jsonPrimitive.content)
        val carried = settings["config"]!!.jsonObject["outbounds"]!!.jsonArray.single().jsonObject
        assertEquals("tuic", carried["type"]!!.jsonPrimitive.content)
        assertNull("no fragmenting asked for, so no dialer", proxy.dialerProxy())
    }

    @Test
    fun `everything around it matches what a share link gets`() {
        val link = XrayJsonBuilder.build(ConfigParser.parse("trojan://p@t.example:443?security=tls#T"))
        val singBox = XrayJsonBuilder.buildSingBox(tuic.fragment, tuic.tag)
        fun section(json: String, key: String) = Json.parseToJsonElement(json).jsonObject[key]
        listOf("inbounds", "dns", "routing", "policy", "stats").forEach { key ->
            assertEquals(key, section(link, key), section(singBox, key))
        }
    }

    @Test
    fun `fragmenting dials the sing-box server through the fragment outbound`() {
        val config = XrayJsonBuilder.buildSingBox(tuic.fragment, tuic.tag, XrayJsonBuilder.BuildOptions(fragmentEnabled = true))
        val obs = outbounds(config)
        assertEquals("fragment", obs.first().dialerProxy())
        assertTrue(obs.any { it.tag() == "fragment" })
    }

    @Test
    fun `a sing-box server joins an auto-select group`() {
        val build = XrayJsonBuilder.buildAutoSelect(
            members = listOf(
                AutoMember.Server("a", ConfigParser.parse("trojan://p@t.example:443?security=tls#T")),
                AutoMember.SingBox("b", tuic.fragment, tuic.tag),
            ),
            tuning = AutoSelectTuning(),
        )
        assertEquals(mapOf("proxy@0" to "a", "proxy@1" to "b"), build.memberProfiles)
        val member = outbounds(build.json).single { it.tag() == "proxy@1" }
        assertEquals("singbox", member["protocol"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a proxy chain can mix Xray and sing-box hops`() {
        val config = XrayJsonBuilder.buildChainHops(
            listOf(
                XrayJsonBuilder.ChainHop.Xray(ConfigParser.parse("trojan://p@entry.example:443?security=tls#E")),
                XrayJsonBuilder.ChainHop.SingBox(tuic.fragment, tuic.tag, tuic),
            ),
        )
        val obs = outbounds(config)
        val exit = obs.single { it.tag() == "proxy" }
        assertEquals("singbox", exit["protocol"]!!.jsonPrimitive.content)
        assertEquals("the exit dials through the entry hop", "proxy-h0", exit.dialerProxy())
    }

    @Test(expected = UdpHopUnsupportedException::class)
    fun `a QUIC sing-box hop behind an HTTP proxy is refused up front`() {
        XrayJsonBuilder.buildChainHops(
            listOf(
                XrayJsonBuilder.ChainHop.Xray(ConfigParser.parse("http://u:p@proxy.example:8080#H")),
                XrayJsonBuilder.ChainHop.SingBox(tuic.fragment, tuic.tag, tuic),
            ),
        )
    }

    @Test
    fun `a full sing-box config is measured through the server its route picks`() {
        val config = """{"inbounds":[{"type":"tun"}],"outbounds":[
            {"type":"selector","tag":"proxy","outbounds":["A","B"],"default":"B"},
            {"type":"trojan","tag":"A","server":"a.example","server_port":443,"password":"p"},
            {"type":"anytls","tag":"B","server":"b.example","server_port":443,"password":"p"}],
            "route":{"final":"proxy"}}"""
        val profile = VpnProfile.fromSingBoxConfig(config, id = "x", addedAt = 0)
        val probe = profile.toXrayConfigJson(forSpeedtest = true)
        val proxy = outbounds(probe).first()
        assertEquals("B", proxy["settings"]!!.jsonObject["use"]!!.jsonPrimitive.content)
        assertEquals("b.example", profile.address)
    }
}
