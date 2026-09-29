package dev.cluvex.zedsecure.domain.config

import dev.cluvex.zedsecure.domain.model.RoutingPresetType
import dev.cluvex.zedsecure.domain.model.RoutingPresets
import dev.cluvex.zedsecure.domain.model.RulesetItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingRulesetTest {
    private val main = ConfigParser.parse(
        "vless://11111111-1111-1111-1111-111111111111@main.example:443?security=tls&type=tcp#main",
    )
    private val other = ConfigParser.parse(
        "vless://22222222-2222-2222-2222-222222222222@nf.example:443?security=tls&type=tcp#nf",
    )

    private fun tagsOf(cfg: String) =
        Regex("\"tag\"\\s*:\\s*\"([^\"]+)\"")
            .findAll(cfg.substringAfter("\"outbounds\"").substringBefore("\"routing\""))
            .map { it.groupValues[1] }
            .toList()

    private fun rulesOf(cfg: String): List<JsonObject> =
        ((Json.parseToJsonElement(cfg) as JsonObject)["routing"] as JsonObject)["rules"]
            .let { it as JsonArray }
            .map { it as JsonObject }

    private fun ruleTagsOf(cfg: String) = rulesOf(cfg)
        .filter { it["inboundTag"] == null }
        .mapNotNull { (it["outboundTag"] as? JsonPrimitive)?.content }

    private fun dnsRuleTagsOf(cfg: String) = rulesOf(cfg)
        .filter { it["inboundTag"] != null }
        .mapNotNull { (it["outboundTag"] as? JsonPrimitive)?.content }

    private fun buildWith(rules: List<RulesetItem>, outbounds: Map<String, ServerConfig> = emptyMap()) =
        XrayJsonBuilder.build(
            main,
            options = XrayJsonBuilder.BuildOptions(
                geoAssetsAvailable = true,
                bypassLan = false,
                rulesets = rules,
                ruleOutbounds = outbounds,
            ),
        )

    @Test
    fun `rule targeting a saved server gets its own outbound`() {
        val tag = RulesetItem.profileTag("prof-B")
        val cfg = buildWith(
            listOf(RulesetItem(id = "r1", outboundTag = tag, domain = listOf("geosite:netflix"))),
            mapOf(tag to other),
        )
        val dedicated = tagsOf(cfg).single { it.startsWith("out-") }
        assertEquals(listOf(dedicated), ruleTagsOf(cfg))

        assertTrue(
            cfg.substringAfter("\"tag\": \"$dedicated\"").substringBefore("}").plus(
                cfg.substringAfter("\"tag\": \"$dedicated\""),
            ).contains("nf.example"),
        )
    }

    @Test
    fun `the main proxy stays the default outbound`() {
        val tag = RulesetItem.profileTag("prof-B")
        val cfg = buildWith(
            listOf(RulesetItem(id = "r1", outboundTag = tag, domain = listOf("a.example"))),
            mapOf(tag to other),
        )

        assertEquals("proxy", tagsOf(cfg).first())
    }

    @Test
    fun `a rule naming a server that no longer exists falls back to proxy`() {
        val cfg = buildWith(
            listOf(
                RulesetItem(
                    id = "r1",
                    outboundTag = RulesetItem.profileTag("deleted"),
                    domain = listOf("gone.example"),
                ),
            ),
        )
        assertEquals(listOf("proxy"), ruleTagsOf(cfg))
        assertTrue(ruleTagsOf(cfg).all { tag -> tagsOf(cfg).contains(tag) })
    }

    @Test
    fun `every emitted rule tag has a matching outbound`() {
        val tag = RulesetItem.profileTag("prof-B")
        val cfg = buildWith(
            listOf(
                RulesetItem(id = "a", outboundTag = tag, domain = listOf("a.example")),
                RulesetItem(id = "b", outboundTag = RulesetItem.OUTBOUND_DIRECT, domain = listOf("b.example")),
                RulesetItem(id = "c", outboundTag = RulesetItem.OUTBOUND_BLOCK, domain = listOf("c.example")),
                RulesetItem(id = "d", outboundTag = RulesetItem.profileTag("nope"), domain = listOf("d.example")),
            ),
            mapOf(tag to other),
        )
        val outbounds = tagsOf(cfg).toSet()
        ruleTagsOf(cfg).forEach { assertTrue("dangling outboundTag: $it", it in outbounds) }
    }

    @Test
    fun `no preset packs domain and ip into one rule`() {
        RoutingPresetType.entries.forEach { type ->
            RoutingPresets.rules(type).forEach { rule ->
                assertFalse(
                    "${type.name}/${rule.remarks} mixes domain and ip",
                    rule.domain.isNotEmpty() && rule.ip.isNotEmpty(),
                )
            }
        }
    }

    @Test
    fun `presets are non-empty and emit usable rules`() {
        RoutingPresetType.entries.forEach { type ->
            val rules = RoutingPresets.rules(type)
            assertTrue(type.name, rules.isNotEmpty())
            assertTrue("${type.name} has an unmatchable rule", rules.none { it.isEmpty })
            assertEquals("${type.name} has duplicate ids", rules.size, rules.map { it.id }.toSet().size)
        }
    }

    @Test
    fun `applying a preset replaces everything except locked rules`() {
        val locked = RulesetItem(id = "keep", remarks = "mine", locked = true, domain = listOf("keep.example"))
        val loose = RulesetItem(id = "drop", domain = listOf("drop.example"))
        val merged = RoutingPresets.apply(listOf(locked, loose), RoutingPresets.rules(RoutingPresetType.IranWhitelist))
        assertTrue(merged.any { it.id == "keep" })
        assertFalse(merged.any { it.id == "drop" })
        assertEquals(1 + RoutingPresets.rules(RoutingPresetType.IranWhitelist).size, merged.size)
    }

    @Test
    fun `ruleset survives a clipboard round-trip with fresh ids`() {
        val original = RoutingPresets.rules(RoutingPresetType.IranWhitelist)
        val back = RulesetTransfer.decode(RulesetTransfer.encode(original))!!
        assertEquals(original.map { it.copy(id = "") }, back.map { it.copy(id = "") })

        assertTrue(back.none { it.id in original.map { o -> o.id } })
    }

    @Test
    fun `a bad clipboard payload never wipes the ruleset`() {
        assertNull(RulesetTransfer.decode("not json"))
        assertNull(RulesetTransfer.decode(""))
        assertNull(RulesetTransfer.decode(null))
    }

    @Test
    fun `a v2rayNG exported ruleset imports`() {
        val v2rayNg = """
            [{"remarks":"Bypass Iran IP","outboundTag":"direct","ip":["geoip:ir"],
              "process":["com.example"],"locked":true,"enabled":true}]
        """.trimIndent()
        val parsed = RulesetTransfer.decode(v2rayNg)!!
        assertEquals(1, parsed.size)

        assertEquals(listOf("geoip:ir"), parsed[0].ip)
        assertTrue(parsed[0].locked)
    }

    private fun dnsOf(cfg: String) = (Json.parseToJsonElement(cfg) as JsonObject)["dns"] as JsonObject

    private fun dnsServers(cfg: String) = (dnsOf(cfg)["servers"] as JsonArray)

    @Test
    fun `a direct-routed domain is resolved by the direct resolver, not the remote one`() {
        val cfg = buildWith(
            listOf(
                RulesetItem(id = "d", outboundTag = RulesetItem.OUTBOUND_DIRECT, domain = listOf("bank.example")),
            ),
        )

        val server = dnsServers(cfg).filterIsInstance<JsonObject>().single { s ->
            (s["domains"] as? JsonArray)?.any { (it as JsonPrimitive).content == "bank.example" } == true
        }
        assertEquals("1.1.1.1", (server["address"] as JsonPrimitive).content)
        assertTrue((server["tag"] as JsonPrimitive).content.startsWith("domestic-dns"))
        assertTrue((server["skipFallback"] as JsonPrimitive).content.toBoolean())
    }

    @Test
    fun `a proxied domain is resolved by the remote resolver`() {
        val cfg = buildWith(
            listOf(
                RulesetItem(id = "p", outboundTag = RulesetItem.OUTBOUND_PROXY, domain = listOf("news.example")),
            ),
        )
        val server = dnsServers(cfg).filterIsInstance<JsonObject>().single { s ->
            (s["domains"] as? JsonArray)?.any { (it as JsonPrimitive).content == "news.example" } == true
        }
        assertEquals("https://1.1.1.1/dns-query", (server["address"] as JsonPrimitive).content)
        assertNull(server["tag"])
    }

    @Test
    fun `a blocked domain is answered as loopback so the lookup never leaves`() {
        val cfg = buildWith(
            listOf(
                RulesetItem(id = "b", outboundTag = RulesetItem.OUTBOUND_BLOCK, domain = listOf("ads.example")),
            ),
        )
        val hosts = dnsOf(cfg)["hosts"] as JsonObject
        assertEquals("127.0.0.1", (hosts["ads.example"] as JsonPrimitive).content)
    }

    @Test
    fun `DNS routing sends domestic lookups direct and everything else through the tunnel`() {
        val cfg = buildWith(
            listOf(
                RulesetItem(id = "d", outboundTag = RulesetItem.OUTBOUND_DIRECT, domain = listOf("bank.example")),
            ),
        )
        assertEquals(listOf("direct", "proxy"), dnsRuleTagsOf(cfg))

        assertTrue(rulesOf(cfg).take(2).all { it["inboundTag"] != null })

        assertEquals("dns-module", (dnsOf(cfg)["tag"] as JsonPrimitive).content)
    }

    @Test
    fun `a geosite entry typed into the IP field is dropped instead of killing the config`() {
        val cfg = buildWith(
            listOf(
                RulesetItem(id = "bad", outboundTag = RulesetItem.OUTBOUND_DIRECT, ip = listOf("geosite:cn")),
                RulesetItem(id = "ok", outboundTag = RulesetItem.OUTBOUND_DIRECT, domain = listOf("ok.example")),
            ),
        )
        assertFalse(cfg.contains("geosite:cn"))
        assertTrue(cfg.contains("ok.example"))
    }

    @Test
    fun `ext prefixes are gated on the geo files like geosite and geoip`() {
        val rules = listOf(
            RulesetItem(id = "e", outboundTag = RulesetItem.OUTBOUND_DIRECT, domain = listOf("ext:custom.dat:cn")),
        )
        val without = XrayJsonBuilder.build(
            main,
            options = XrayJsonBuilder.BuildOptions(geoAssetsAvailable = false, bypassLan = false, rulesets = rules),
        )

        assertFalse(without.contains("ext:custom.dat:cn"))
        assertTrue(buildWith(rules).contains("ext:custom.dat:cn"))
    }

    @Test
    fun `validation catches the mistakes that would take the whole config down`() {
        assertEquals(RuleValidation.Problem.GeoSiteInIpField, RuleValidation.checkIpEntry("geosite:cn"))
        assertEquals(RuleValidation.Problem.GeoIpInDomainField, RuleValidation.checkDomainEntry("geoip:ir"))
        assertEquals(RuleValidation.Problem.NegationInDomainField, RuleValidation.checkDomainEntry("!geosite:cn"))
        assertEquals(RuleValidation.Problem.DotlessWithDot, RuleValidation.checkDomainEntry("dotless:a.b"))
        assertEquals(RuleValidation.Problem.NotAnAddress, RuleValidation.checkIpEntry("example.com"))
        assertEquals(RuleValidation.Problem.BadRegex, RuleValidation.checkDomainEntry("regexp:[unclosed"))

        assertNull(RuleValidation.checkIpEntry("10.0.0.0/8"))
        assertNull(RuleValidation.checkIpEntry("!geoip:cn"))
        assertNull(RuleValidation.checkIpEntry("2606:4700::/32"))
        assertNull(RuleValidation.checkDomainEntry("domain:example.com"))
        assertTrue(RuleValidation.isValidPort("443,8000-9000"))
        assertFalse(RuleValidation.isValidPort("443,notaport"))
        assertFalse(RuleValidation.isValidPort("9000-8000"))
        assertTrue(RuleValidation.isValidPort(""))
    }

    @Test
    fun `a pasted custom config keeps working when the geo files are missing`() {
        val raw = """
            {"outbounds":[{"protocol":"freedom"}],
             "routing":{"rules":[
               {"type":"field","outboundTag":"direct","domain":["geosite:cn","keep.example"]},
               {"type":"field","outboundTag":"direct","domain":["geosite:ir"]},
               {"type":"field","outboundTag":"block","domain":["ads.example"]}]}}
        """.trimIndent()
        val out = XrayJsonBuilder.normalizeRawJson(raw, geoAssetsAvailable = false)
        assertFalse(out.contains("geosite:"))
        assertTrue(out.contains("keep.example"))
        assertFalse(out.contains("geosite:ir"))
        assertTrue(out.contains("ads.example"))

        assertTrue(XrayJsonBuilder.normalizeRawJson(raw, geoAssetsAvailable = true).contains("geosite:cn"))
        assertTrue(XrayJsonBuilder.referencesGeoData(raw))
    }
}
