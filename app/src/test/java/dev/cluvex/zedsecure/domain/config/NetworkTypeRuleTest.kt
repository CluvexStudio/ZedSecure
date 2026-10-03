package dev.cluvex.zedsecure.domain.config

import dev.cluvex.zedsecure.domain.model.RulesetItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkTypeRuleTest {
    private val server = ConfigParser.parse(
        "vless://11111111-1111-1111-1111-111111111111@main.example:443?security=tls&type=tcp#main",
    )

    private val wifiDirect = RulesetItem(
        id = "wifi",
        outboundTag = RulesetItem.OUTBOUND_DIRECT,
        domain = listOf("domain:example.org"),
        networkType = listOf(RulesetItem.NETWORK_WIFI),
    )

    private val mobileBlock = RulesetItem(
        id = "mobile",
        outboundTag = RulesetItem.OUTBOUND_BLOCK,
        networkType = listOf(RulesetItem.NETWORK_CELLULAR),
    )

    private fun rules(networkType: String?): List<JsonObject> {
        val cfg = XrayJsonBuilder.build(
            server,
            options = XrayJsonBuilder.BuildOptions(
                geoAssetsAvailable = true,
                bypassLan = false,
                rulesets = listOf(wifiDirect, mobileBlock),
                networkType = networkType,
            ),
        )
        return ((Json.parseToJsonElement(cfg) as JsonObject)["routing"] as JsonObject)["rules"]
            .let { it as JsonArray }
            .map { it as JsonObject }
            .filter { it["inboundTag"] == null }
    }

    private fun JsonObject.has(domain: String) = (this["domain"] as? JsonArray)?.any { (it as JsonPrimitive).content == domain } == true

    @Test
    fun `a rule for Wi-Fi applies only on Wi-Fi`() {
        assertTrue(rules(RulesetItem.NETWORK_WIFI).any { it.has("domain:example.org") })
        assertFalse(rules(RulesetItem.NETWORK_CELLULAR).any { it.has("domain:example.org") })
        assertFalse(rules(null).any { it.has("domain:example.org") })
    }

    @Test
    fun `a rule with only a network type covers all traffic on that network`() {
        val block = rules(RulesetItem.NETWORK_CELLULAR).filter {
            (it["outboundTag"] as? JsonPrimitive)?.content == RulesetItem.OUTBOUND_BLOCK && it["network"] != null
        }
        assertEquals("tcp,udp", (block.single()["network"] as JsonPrimitive).content)
        assertTrue(rules(RulesetItem.NETWORK_WIFI).none { (it["network"] as? JsonPrimitive)?.content == "tcp,udp" && (it["outboundTag"] as? JsonPrimitive)?.content == RulesetItem.OUTBOUND_BLOCK })
    }

    @Test
    fun `rules without a network type apply everywhere`() {
        val plain = RulesetItem(id = "p", outboundTag = RulesetItem.OUTBOUND_DIRECT, domain = listOf("domain:plain.example"))
        assertTrue(plain.appliesOn(null))
        assertTrue(plain.appliesOn(RulesetItem.NETWORK_OTHER))
        assertFalse(plain.isEmpty)
        assertFalse(mobileBlock.isEmpty)
    }

    @Test
    fun `exported rules keep their network type and drop unknown ones`() {
        val text = RulesetTransfer.encode(listOf(wifiDirect))
        assertEquals(listOf(RulesetItem.NETWORK_WIFI), RulesetTransfer.decode(text)!!.single().networkType)
        val odd = text.replace("\"wifi\"", "\"satellite\"")
        assertTrue(RulesetTransfer.decode(odd)!!.single().networkType.isEmpty())
    }
}
