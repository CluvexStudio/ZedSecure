package dev.cluvex.zedsecure.domain.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarrierSniffingTest {
    private val vless = ConfigParser.parse(
        "vless://11111111-1111-1111-1111-111111111111@a.example:443" +
            "?encryption=none&security=tls&type=tcp#A",
    )

    private fun sniffing(json: String) =
        Json.parseToJsonElement(json).jsonObject["inbounds"]!!.jsonArray
            .map { it.jsonObject }
            .first { it["protocol"]!!.jsonPrimitive.content == "socks" }
            .getValue("sniffing").jsonObject

    private fun enabled(json: String) = sniffing(json)["enabled"]!!.jsonPrimitive.content.toBoolean()

    @Test
    fun `a normal config still sniffs`() {
        val json = XrayJsonBuilder.build(vless, options = XrayJsonBuilder.BuildOptions(sniffing = true))
        assertTrue("a standalone tunnel must keep sniffing — routing rules need it", enabled(json))
    }

    @Test
    fun `a carrier config never sniffs`() {
        val json = XrayJsonBuilder.build(
            vless,
            options = XrayJsonBuilder.BuildOptions(sniffing = true, carrier = true),
        )
        assertFalse("the carrier must dial exactly what its client asked for", enabled(json))
    }

    @Test
    fun `carrier overrides sniffing even when the user turned it on and routeOnly off`() {
        val json = XrayJsonBuilder.build(
            vless,
            options = XrayJsonBuilder.BuildOptions(sniffing = true, routeOnly = false, carrier = true),
        )
        assertFalse(enabled(json))
    }

    @Test
    fun `carrier flag does not disturb the rest of the inbound`() {
        val plain = XrayJsonBuilder.build(vless, options = XrayJsonBuilder.BuildOptions(sniffing = false))
        val carrier = XrayJsonBuilder.build(
            vless,
            options = XrayJsonBuilder.BuildOptions(sniffing = false, carrier = true),
        )

        assertEquals(plain, carrier)
    }
}
