package dev.cluvex.zedsecure.domain.config

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SingBoxLinksTest {
    private fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.content
    private fun JsonObject.tls() = this["tls"]!!.jsonObject

    @Test
    fun `tuic`() {
        val parsed = SingBoxLinks.parse(
            "tuic://2dd61d93-75d8-4da4-ac0e-6aece7eac365:secret@tuic.example:8443" +
                "?congestion_control=bbr&udp_relay_mode=quic&alpn=h3&sni=cdn.example&allow_insecure=1#DE%20TUIC",
        )
        val o = parsed.outbound
        assertEquals("DE TUIC", parsed.name)
        assertEquals("tuic", o.str("type"))
        assertEquals("tuic.example", o.str("server"))
        assertEquals("8443", o.str("server_port"))
        assertEquals("2dd61d93-75d8-4da4-ac0e-6aece7eac365", o.str("uuid"))
        assertEquals("secret", o.str("password"))
        assertEquals("bbr", o.str("congestion_control"))
        assertEquals("quic", o.str("udp_relay_mode"))
        assertEquals("cdn.example", o.tls().str("server_name"))
        assertEquals("true", o.tls().str("insecure"))
        assertEquals(listOf("h3"), o.tls()["alpn"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun `anytls`() {
        val o = SingBoxLinks.parse("anytls://pa%3Ass@any.example:443?sni=any.example&fp=chrome#NL").outbound
        assertEquals("anytls", o.str("type"))
        assertEquals("pa:ss", o.str("password"))
        assertEquals("chrome", o.tls()["utls"]!!.jsonObject.str("fingerprint"))
    }

    @Test
    fun `hysteria 1 goes to sing-box, the Hysteria2 form under the old scheme stays with Xray`() {
        val v1 = "hysteria://h1.example:443?protocol=udp&auth=token&peer=sni.example&insecure=1" +
            "&upmbps=20&downmbps=100&alpn=hysteria&obfsParam=mask#H1"
        assertTrue(SingBoxLinks.handles(v1))
        val o = SingBoxLinks.parse(v1).outbound
        assertEquals("hysteria", o.str("type"))
        assertEquals("token", o.str("auth_str"))
        assertEquals("20", o.str("up_mbps"))
        assertEquals("100", o.str("down_mbps"))
        assertEquals("mask", o.str("obfs"))
        assertEquals("sni.example", o.tls().str("server_name"))

        assertFalse(SingBoxLinks.handles("hysteria://password@h2.example:443?sni=h2.example#H2"))
        assertFalse(SingBoxLinks.handles("hysteria2://password@h2.example:443#H2"))
    }

    @Test
    fun `naive over https and quic`() {
        val https = SingBoxLinks.parse("naive+https://user:pass@naive.example:443#N").outbound
        assertEquals("naive", https.str("type"))
        assertEquals("user", https.str("username"))
        assertEquals("pass", https.str("password"))
        assertNull(https["quic"])
        val quic = SingBoxLinks.parse("naive+quic://user:pass@naive.example:443#N").outbound
        assertEquals("true", quic.str("quic"))
    }

    @Test
    fun `socks4 and socks4a, which the Xray client cannot speak`() {
        val four = SingBoxLinks.parse("socks4://alice@10.0.0.1:1080#S4").outbound
        assertEquals("socks", four.str("type"))
        assertEquals("4", four.str("version"))
        assertEquals("alice", four.str("username"))
        assertEquals("4a", SingBoxLinks.parse("socks4a://h.example:1080").outbound.str("version"))
        assertFalse("socks5 stays with the Xray core", SingBoxLinks.handles("socks://u:p@h.example:1080"))
    }

    @Test
    fun `links survive a round trip`() {
        listOf(
            "tuic://2dd61d93-75d8-4da4-ac0e-6aece7eac365:secret@tuic.example:8443?congestion_control=bbr&udp_relay_mode=native&sni=cdn.example&alpn=h3#DE",
            "anytls://password@any.example:443?sni=any.example&insecure=1#NL",
            "hysteria://h1.example:443?auth=token&upmbps=20&downmbps=100&obfsParam=mask&peer=sni.example#H1",
            "naive+quic://user:pass@naive.example:443#N",
            "socks4a://alice@h.example:1080#S",
            "tuic://2dd61d93-75d8-4da4-ac0e-6aece7eac365:secret@[2001:db8::1]:443#V6",
        ).forEach { link ->
            val first = SingBoxLinks.parse(link)
            val rebuilt = SingBoxLinks.build(first.outbound, first.name)!!
            val second = SingBoxLinks.parse(rebuilt)
            assertEquals(link, first.outbound, second.outbound)
            assertEquals(link, first.name, second.name)
        }
    }

    @Test
    fun `protocols without a link form are shared as JSON instead`() {
        val snell = JsonObject(mapOf(
            "type" to kotlinx.serialization.json.JsonPrimitive("snell"),
            "server" to kotlinx.serialization.json.JsonPrimitive("s.example"),
            "server_port" to kotlinx.serialization.json.JsonPrimitive(443),
        ))
        assertNull(SingBoxLinks.build(snell, "S"))
    }

    @Test
    fun `sing-box links count as supported links for subscriptions`() {
        val body = """
            vless://2dd61d93-75d8-4da4-ac0e-6aece7eac365@a.example:443?encryption=none#A
            tuic://2dd61d93-75d8-4da4-ac0e-6aece7eac365:p@b.example:443#B
            anytls://p@c.example:443#C
            unknown://nothing
        """.trimIndent()
        assertEquals(3, SubscriptionParser.extractLinks(body).size)
    }
}
