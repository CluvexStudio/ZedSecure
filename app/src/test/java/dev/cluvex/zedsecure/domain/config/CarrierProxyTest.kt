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

class CarrierProxyTest {
    private val vless1 = ConfigParser.parse("vless://11111111-1111-1111-1111-111111111111@a.example:443?encryption=none&security=tls&type=tcp#A")
    private val vless2 = ConfigParser.parse("vless://22222222-2222-2222-2222-222222222222@b.example:443?encryption=none&security=tls&type=tcp#B")
    private val wg = ConfigParser.parse("wireguard://cHJpdmF0ZWtleXByaXZhdGVrZXlwcml2YXRla2V5MDA9@wg.example:51820?publickey=cHVibGlja2V5cHVibGlja2V5cHVibGlja2V5MDAwMD0%3D&address=10.8.0.2/32#WG")

    private val port = 9250

    private fun outbounds(json: String): List<JsonObject> =
        Json.parseToJsonElement(json).jsonObject["outbounds"]!!.jsonArray.map { it.jsonObject }

    private fun tags(json: String) = outbounds(json).map { it["tag"]!!.jsonPrimitive.content }

    private fun byTag(json: String) = outbounds(json).associateBy { it["tag"]!!.jsonPrimitive.content }

    private fun dialerOf(o: JsonObject?): String? =
        o?.get("streamSettings")?.jsonObject?.get("sockopt")?.jsonObject?.get("dialerProxy")?.jsonPrimitive?.content

    @Test
    fun `single server dials out through the carrier`() {
        val json = XrayJsonBuilder.build(
            vless1,
            options = XrayJsonBuilder.BuildOptions(dialerSocksPort = port),
        )
        val out = byTag(json)
        assertEquals(XrayJsonBuilder.CHAIN_OUT_TAG, dialerOf(out["proxy"]))
        val chainOut = out.getValue(XrayJsonBuilder.CHAIN_OUT_TAG)
        assertEquals("socks", chainOut["protocol"]!!.jsonPrimitive.content)
        val server = chainOut["settings"]!!.jsonObject["servers"]!!.jsonArray[0].jsonObject
        assertEquals("127.0.0.1", server["address"]!!.jsonPrimitive.content)
        assertEquals(port, server["port"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `chain-out never carries mux because the carriers do not speak it`() {
        val json = XrayJsonBuilder.build(
            vless1,
            options = XrayJsonBuilder.BuildOptions(dialerSocksPort = port, muxEnabled = true),
        )
        val chainOut = byTag(json).getValue(XrayJsonBuilder.CHAIN_OUT_TAG)
        assertNull("mux would break Tor/Psiphon/SSH/DNSTT", chainOut["mux"])

        assertTrue(byTag(json).getValue("proxy")["mux"] != null)
    }

    @Test
    fun `carrier replaces fragment on the entry hop and the fragment outbound is dropped`() {
        val json = XrayJsonBuilder.build(
            vless1,
            options = XrayJsonBuilder.BuildOptions(dialerSocksPort = port, fragmentEnabled = true),
        )

        assertFalse("fragment must not be emitted: ${tags(json)}", "fragment" in tags(json))
        assertEquals(XrayJsonBuilder.CHAIN_OUT_TAG, dialerOf(byTag(json)["proxy"]))
    }

    @Test
    fun `a multi-hop chain keeps exit-first order and only the entry dials the carrier`() {
        val json = XrayJsonBuilder.buildChain(
            listOf(vless1, vless2),
            options = XrayJsonBuilder.BuildOptions(dialerSocksPort = port),
        )
        val tags = tags(json)

        assertEquals("proxy", tags.first())
        val out = byTag(json)
        assertEquals("proxy-h0", dialerOf(out["proxy"]))
        assertEquals(XrayJsonBuilder.CHAIN_OUT_TAG, dialerOf(out["proxy-h0"]))
    }

    @Test
    fun `a WireGuard entry hop still gets the carrier sockopt`() {
        val json = XrayJsonBuilder.build(
            wg,
            options = XrayJsonBuilder.BuildOptions(dialerSocksPort = port),
        )

        assertEquals(XrayJsonBuilder.CHAIN_OUT_TAG, dialerOf(byTag(json)["proxy"]))
    }

    @Test
    fun `patching a built config equals building it with the carrier port`() {
        val cases = listOf(
            "plain" to XrayJsonBuilder.BuildOptions(),
            "fragment" to XrayJsonBuilder.BuildOptions(fragmentEnabled = true),
            "mux" to XrayJsonBuilder.BuildOptions(muxEnabled = true),
        )
        for ((name, base) in cases) {
            val built = XrayJsonBuilder.build(vless1, options = base.copy(dialerSocksPort = port))
            val patched = XrayJsonBuilder.withCarrierProxy(XrayJsonBuilder.build(vless1, options = base), port)
            assertEquals("single/$name", built, patched)
        }
        for ((name, base) in cases) {
            val built = XrayJsonBuilder.buildChain(listOf(vless1, vless2), options = base.copy(dialerSocksPort = port))
            val patched = XrayJsonBuilder.withCarrierProxy(
                XrayJsonBuilder.buildChain(listOf(vless1, vless2), options = base), port,
            )
            assertEquals("chain/$name", built, patched)
        }
        val builtWg = XrayJsonBuilder.build(wg, options = XrayJsonBuilder.BuildOptions(dialerSocksPort = port))
        val patchedWg = XrayJsonBuilder.withCarrierProxy(XrayJsonBuilder.build(wg), port)
        assertEquals("wireguard", builtWg, patchedWg)
    }

    @Test
    fun `custom config with an unusual tag still gets the carrier`() {
        val custom = """
            {"outbounds":[
              {"tag":"my-node","protocol":"vless","settings":{},
               "streamSettings":{"network":"tcp"}},
              {"tag":"DIRECT","protocol":"freedom"}
            ]}
        """.trimIndent()
        val patched = XrayJsonBuilder.withCarrierProxy(custom, port)
        val out = byTag(patched)
        assertEquals(XrayJsonBuilder.CHAIN_OUT_TAG, dialerOf(out["my-node"]))
        assertTrue(XrayJsonBuilder.CHAIN_OUT_TAG in tags(patched))
    }

    @Test
    fun `hand-written internal chain gets the carrier on its last hop`() {
        val custom = """
            {"outbounds":[
              {"tag":"a","protocol":"vless","settings":{},
               "streamSettings":{"network":"tcp","sockopt":{"dialerProxy":"b"}}},
              {"tag":"b","protocol":"vless","settings":{},
               "streamSettings":{"network":"tcp"}}
            ]}
        """.trimIndent()
        val out = byTag(XrayJsonBuilder.withCarrierProxy(custom, port))
        assertEquals("b", dialerOf(out["a"]))
        assertEquals(XrayJsonBuilder.CHAIN_OUT_TAG, dialerOf(out["b"]))
    }

    @Test
    fun `no carrier port leaves the config exactly as it was`() {
        val plain = XrayJsonBuilder.build(vless1)
        assertNull(dialerOf(byTag(plain)["proxy"]))
        assertFalse(XrayJsonBuilder.CHAIN_OUT_TAG in tags(plain))
    }
}
