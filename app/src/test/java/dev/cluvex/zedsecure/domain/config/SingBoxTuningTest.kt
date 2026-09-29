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

class SingBoxTuningTest {
    private val everything = SingBoxTuning(
        muxEnabled = true,
        muxProtocol = "smux",
        muxMaxConnections = 8,
        muxPadding = true,
        brutalEnabled = true,
        brutalUpMbps = 20,
        brutalDownMbps = 80,
        tlsFragment = true,
        tlsRecordFragment = true,
        utlsFingerprint = "chrome",
        udpOverTcp = true,
    )

    private fun fragment(vararg outbounds: String) = """{"outbounds":[${outbounds.joinToString(",")}]}"""

    private fun tuned(fragment: String, tuning: SingBoxTuning = everything): JsonObject =
        Json.parseToJsonElement(SingBoxTuning.apply(fragment, tuning)).jsonObject

    private fun first(json: JsonObject) = json["outbounds"]!!.jsonArray.first().jsonObject

    private fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.content

    @Test
    fun `a multiplexing protocol gets the multiplex block the user asked for`() {
        val out = first(
            tuned(fragment("""{"type":"trojan","tag":"T","server":"a.example","server_port":443,"password":"p"}""")),
        )
        val mux = out["multiplex"]!!.jsonObject
        assertEquals("true", mux.str("enabled"))
        assertEquals("smux", mux.str("protocol"))
        assertEquals("8", mux.str("max_connections"))
        assertEquals("true", mux.str("padding"))
        val brutal = mux["brutal"]!!.jsonObject
        assertEquals("20", brutal.str("up_mbps"))
        assertEquals("80", brutal.str("down_mbps"))
    }

    @Test
    fun `protocols that cannot multiplex, or carry UDP over TCP, are left alone`() {
        val anytls = first(tuned(fragment("""{"type":"anytls","tag":"A","server":"a.example","server_port":443,"password":"p"}""")))
        assertNull("AnyTLS has no multiplex option", anytls["multiplex"])
        assertNull(anytls["udp_over_tcp"])

        val ss = first(
            tuned(fragment("""{"type":"shadowsocks","tag":"S","server":"a.example","server_port":443,"method":"aes-128-gcm","password":"p"}""")),
        )
        assertEquals("shadowsocks multiplexes", "true", ss["multiplex"]!!.jsonObject.str("enabled"))
        assertEquals("and carries UDP over TCP", "true", ss.str("udp_over_tcp"))
    }

    @Test
    fun `TLS options only reach an outbound that speaks TLS`() {
        val withTls = first(
            tuned(
                fragment(
                    """{"type":"vless","tag":"V","server":"a.example","server_port":443,"uuid":"u",
                       "tls":{"enabled":true,"server_name":"a.example"}}""",
                ),
            ),
        )
        val tls = withTls["tls"]!!.jsonObject
        assertEquals("true", tls.str("fragment"))
        assertEquals("true", tls.str("record_fragment"))
        assertEquals("chrome", tls["utls"]!!.jsonObject.str("fingerprint"))
        assertEquals("what the server set stays", "a.example", tls.str("server_name"))

        val plain = first(tuned(fragment("""{"type":"snell","tag":"S","server":"a.example","server_port":1234,"psk":"k"}""")))
        assertNull(plain["tls"])
        val off = first(
            tuned(fragment("""{"type":"trojan","tag":"T","server":"a.example","server_port":443,"password":"p","tls":{"enabled":false}}""")),
        )
        assertNull("TLS switched off is not TLS", off["tls"]!!.jsonObject["fragment"])
    }

    @Test
    fun `endpoints keep every field exactly as they came`() {
        val endpoints = """{"endpoints":[
            {"type":"openvpn-client","tag":"O","server":"a.example","server_port":1194,"tls":{"certificate":["x"]}},
            {"type":"wireguard","tag":"W","peers":[{"address":"a.example","port":51820}]}]}"""
        assertEquals(endpoints, SingBoxTuning.apply(endpoints, everything))
    }

    @Test
    fun `a server that already says something keeps its own answer`() {
        val out = first(
            tuned(
                fragment(
                    """{"type":"trojan","tag":"T","server":"a.example","server_port":443,"password":"p",
                       "multiplex":{"enabled":false},"tls":{"enabled":true,"fragment":false,
                       "utls":{"enabled":true,"fingerprint":"firefox"}}}""",
                ),
            ),
        )
        assertEquals("false", out["multiplex"]!!.jsonObject.str("enabled"))
        val tls = out["tls"]!!.jsonObject
        assertEquals("false", tls.str("fragment"))
        assertEquals("firefox", tls["utls"]!!.jsonObject.str("fingerprint"))
        assertEquals("but what it did not set is still added", "true", tls.str("record_fragment"))
    }

    @Test
    fun `settings switched off change nothing at all`() {
        val fragment = fragment("""{"type":"trojan","tag":"T","server":"a.example","server_port":443,"password":"p","tls":{"enabled":true}}""")
        assertTrue(SingBoxTuning().isEmpty)
        assertEquals(fragment, SingBoxTuning.apply(fragment, SingBoxTuning()))

        val brutalOnly = SingBoxTuning(brutalEnabled = true, brutalUpMbps = 10, brutalDownMbps = 10)
        assertEquals(fragment, SingBoxTuning.apply(fragment, brutalOnly))
    }

    @Test
    fun `the whole path from settings to a built config carries them`() {
        val settings = dev.cluvex.zedsecure.domain.model.AppSettings(
            singBoxMuxEnabled = true,
            singBoxUtlsFingerprint = "safari",
        )
        val server = SingBoxJson.servers(
            """{"type":"trojan","tag":"T","server":"a.example","server_port":443,"password":"p","tls":{"enabled":true}}""",
        ).single()
        val config = XrayJsonBuilder.buildSingBox(server.fragment, server.tag, settings.toBuildOptions())
        assertTrue(config.contains("\"multiplex\""))
        assertTrue(config.contains("\"safari\""))
    }
}
