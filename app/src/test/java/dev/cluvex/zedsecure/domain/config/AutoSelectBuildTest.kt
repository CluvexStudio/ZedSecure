package dev.cluvex.zedsecure.domain.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSelectBuildTest {
    private fun server(host: String, extra: String = "") = ConfigParser.parse(
        "vless://11111111-1111-1111-1111-111111111111@$host:443?encryption=none&security=tls&sni=$host&type=ws$extra#$host",
    )

    private fun outbounds(json: String): List<JsonObject> =
        Json.parseToJsonElement(json).jsonObject["outbounds"]!!.jsonArray.map { it.jsonObject }

    private fun tag(o: JsonObject) = o["tag"]?.jsonPrimitive?.content

    private fun sockoptDialer(o: JsonObject): String? =
        ((o["streamSettings"] as? JsonObject)?.get("sockopt") as? JsonObject)?.get("dialerProxy")
            ?.jsonPrimitive?.content

    @Test
    fun `the group is the first outbound and names every member in order`() {
        val build = XrayJsonBuilder.buildAutoSelect(
            members = listOf(
                AutoMember.Server("a", server("a.example")),
                AutoMember.Server("b", server("b.example")),
            ),
            tuning = AutoSelectTuning(probeUrl = "https://probe.example/generate_204", switchMarginPercent = 50),
        )
        val obs = outbounds(build.json)
        val group = obs.first()
        assertEquals("proxy", tag(group))
        assertEquals("autoselect", group["protocol"]!!.jsonPrimitive.content)
        val settings = group["settings"]!!.jsonObject
        assertEquals(listOf("proxy@0", "proxy@1"), settings["outbounds"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("https://probe.example/generate_204", settings["probeURL"]!!.jsonPrimitive.content)
        assertEquals("0.5", settings["switchRatio"]!!.jsonPrimitive.content)
        assertEquals(mapOf("proxy@0" to "a", "proxy@1" to "b"), build.memberProfiles)
        assertEquals(listOf("proxy", "proxy@0", "proxy@1"), obs.take(3).map(::tag))
    }

    @Test
    fun `members carry no mux even when mux is on`() {
        val build = XrayJsonBuilder.buildAutoSelect(
            members = listOf(AutoMember.Server("a", server("a.example")), AutoMember.Server("b", server("b.example"))),
            tuning = AutoSelectTuning(),
            options = XrayJsonBuilder.BuildOptions(muxEnabled = true),
        )
        outbounds(build.json).filter { AutoSelectTags.isMember(tag(it)!!) }.forEach {
            assertNull("member ${tag(it)} must not carry mux", it["mux"])
        }
    }

    @Test
    fun `routing still points at proxy, so the group receives everything a single server would`() {
        val single = Json.parseToJsonElement(XrayJsonBuilder.build(server("a.example"))).jsonObject
        val auto = Json.parseToJsonElement(
            XrayJsonBuilder.buildAutoSelect(
                listOf(AutoMember.Server("a", server("a.example")), AutoMember.Server("b", server("b.example"))),
                AutoSelectTuning(),
            ).json,
        ).jsonObject
        assertEquals(single["routing"], auto["routing"])
        assertEquals(single["dns"], auto["dns"])
        assertEquals(single["inbounds"], auto["inbounds"])
    }

    @Test
    fun `fragmentation stays on the physical dial of every link member`() {
        val build = XrayJsonBuilder.buildAutoSelect(
            listOf(AutoMember.Server("a", server("a.example")), AutoMember.Server("b", server("b.example"))),
            AutoSelectTuning(),
            XrayJsonBuilder.BuildOptions(fragmentEnabled = true),
        )
        val obs = outbounds(build.json)
        assertTrue(obs.any { tag(it) == "fragment" })
        obs.filter { AutoSelectTags.isMember(tag(it)!!) }.forEach { assertEquals("fragment", sockoptDialer(it)) }
    }

    @Test
    fun `a custom config brings its proxy and what it dials through, renamed and rewired`() {
        val custom = """
            {"remarks":"C","outbounds":[
              {"tag":"proxy","protocol":"vless","mux":{"enabled":true},
               "settings":{"vnext":[{"address":"c.example","port":443,"users":[{"id":"11111111-1111-1111-1111-111111111111","encryption":"none"}]}]},
               "streamSettings":{"network":"tcp","security":"tls","tlsSettings":{"serverName":"c.example","allowInsecure":true},"sockopt":{"dialerProxy":"frag"}}},
              {"tag":"frag","protocol":"freedom","settings":{"fragment":{"packets":"tlshello","length":"10-20","interval":"10-20"}}},
              {"tag":"direct","protocol":"freedom"},
              {"tag":"block","protocol":"blackhole"}],
             "routing":{"rules":[{"type":"field","outboundTag":"direct","domain":["geosite:ir"]}]}}
        """.trimIndent()
        val build = XrayJsonBuilder.buildAutoSelect(
            listOf(AutoMember.Server("a", server("a.example")), AutoMember.Custom("c", custom)),
            AutoSelectTuning(),
        )
        val obs = outbounds(build.json)
        val member = obs.single { tag(it) == "proxy@1" }
        assertEquals("vless", member["protocol"]!!.jsonPrimitive.content)
        assertEquals("proxy@1/frag", sockoptDialer(member))
        assertNull("the group bypasses mux, so it is dropped", member["mux"])
        val tls = member["streamSettings"]!!.jsonObject["tlsSettings"]!!.jsonObject
        assertFalse("custom members go through the same sanitizer", tls.containsKey("allowInsecure"))
        assertTrue(obs.any { tag(it) == "proxy@1/frag" && it["protocol"]!!.jsonPrimitive.content == "freedom" })

        assertEquals(1, obs.count { tag(it) == "direct" })
        val rules = Json.parseToJsonElement(build.json).jsonObject["routing"]!!.jsonObject["rules"] as JsonArray
        assertFalse(rules.any { (it as JsonObject)["domain"]?.jsonArray?.contains(JsonPrimitive("geosite:ir")) == true })
    }

    @Test
    fun `members that cannot join are skipped, not fatal`() {
        val build = XrayJsonBuilder.buildAutoSelect(
            listOf(
                AutoMember.Server("gone", server("h2.example").copy(transport = server("h2.example").transport.copy(network = "h2"))),
                AutoMember.Custom("empty", """{"outbounds":[{"tag":"direct","protocol":"freedom"}]}"""),
                AutoMember.Server("ok", server("ok.example")),
            ),
            AutoSelectTuning(),
        )
        assertEquals(listOf("gone", "empty"), build.skipped)
        assertEquals(mapOf("proxy@0" to "ok"), build.memberProfiles)
    }

    @Test(expected = AutoSelectNoMembersException::class)
    fun `a group with nothing usable refuses to build`() {
        XrayJsonBuilder.buildAutoSelect(
            listOf(AutoMember.Custom("empty", """{"outbounds":[{"tag":"direct","protocol":"freedom"}]}""")),
            AutoSelectTuning(),
        )
    }

    @Test
    fun `the initial member is passed by its tag`() {
        val build = XrayJsonBuilder.buildAutoSelect(
            listOf(AutoMember.Server("a", server("a.example")), AutoMember.Server("b", server("b.example"))),
            AutoSelectTuning(initialProfileId = "b"),
        )
        val settings = outbounds(build.json).first()["settings"]!!.jsonObject
        assertEquals("proxy@1", settings["initial"]!!.jsonPrimitive.content)
    }

    @Test
    fun `member tags are recognised exactly`() {
        assertTrue(AutoSelectTags.isMember("proxy@0"))
        assertTrue(AutoSelectTags.isMember("proxy@12"))
        assertFalse(AutoSelectTags.isMember("proxy"))
        assertFalse(AutoSelectTags.isMember("proxy@"))
        assertFalse(AutoSelectTags.isMember("proxy@1/frag"))
        assertFalse(AutoSelectTags.isMember("proxy-h0"))
    }
}
