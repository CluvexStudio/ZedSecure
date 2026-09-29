package dev.cluvex.zedsecure.domain.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SingBoxOnlyServersTest {
    private fun custom(id: String, name: String, protocol: String, address: String, port: Int): VpnProfile {
        val settings = if (protocol == "vless") {
            """{"vnext":[{"address":"$address","port":$port,"users":[{"id":"00000000-0000-0000-0000-000000000000","encryption":"none"}]}]}"""
        } else {
            """{"servers":[{"address":"$address","port":$port,"password":"p"}]}"""
        }
        val raw = """{"remarks":"$name","outbounds":[{"tag":"proxy","protocol":"$protocol","settings":$settings,
            "streamSettings":{"network":"tcp","security":"tls"}},{"tag":"direct","protocol":"freedom"}]}"""
        return VpnProfile.fromRawJson(raw, id = id, addedAt = 0L, subscriptionId = "s")
    }

    private fun singBox(json: String, id: String = "sb") =
        VpnProfile.fromSingBox(SingBoxJson.servers(json).single(), id = id, addedAt = 0L, subscriptionId = "s")

    private fun servers(vararg outbounds: String) = SingBoxJson.servers("""{"outbounds":[${outbounds.joinToString(",")}]}""")

    @Test
    fun `loopback and unspecified addresses are this device, anything else is not`() {
        listOf("127.0.0.1", "127.1.2.3", "0.0.0.0", "localhost", "LOCALHOST", "::1", "[::1]", "::", " 127.0.0.1 ")
            .forEach { assertTrue(it, SingBoxOnlyServers.isLocalAddress(it)) }
        listOf("128.0.0.1", "10.0.0.1", "127.0.0", "127.0.0.256", "127.0.0.x", "1270.0.0.1", "example.com", "fe80::1", "-", "")
            .forEach { assertFalse(it, SingBoxOnlyServers.isLocalAddress(it)) }
    }

    @Test
    fun `an Xray config or share link aimed at this device is a stand-in`() {
        assertTrue(SingBoxOnlyServers.isPlaceholder(custom("a", "DE sing-box only", "vless", "127.0.0.1", 443)))
        val link = VpnProfile.fromLink(
            "vless://00000000-0000-0000-0000-000000000000@127.0.0.1:443?encryption=none&security=tls#NL", id = "l", addedAt = 0L,
        )
        assertTrue(SingBoxOnlyServers.isPlaceholder(link))
        assertFalse(SingBoxOnlyServers.isPlaceholder(custom("b", "AT", "trojan", "203.0.113.7", 993)))
    }

    @Test
    fun `a sing-box server is never a stand-in, wherever it points`() {
        val local = singBox("""{"type":"socks","tag":"local","server":"127.0.0.1","server_port":1080}""")
        assertFalse(SingBoxOnlyServers.isPlaceholder(local))
    }

    @Test
    fun `a server the first format already has is not missing, the same endpoint under another protocol is`() {
        val imported = listOf(
            custom("t", "AT", "trojan", "203.0.113.7", 993),
            custom("h", "DE sing-box only", "vless", "127.0.0.1", 443),
        )
        val missing = SingBoxOnlyServers.missing(
            imported,
            servers(
                """{"type":"trojan","tag":"AT","server":"203.0.113.7","server_port":993,"password":"p","tls":{"enabled":true}}""",
                """{"type":"anytls","tag":"AT2","server":"203.0.113.7","server_port":993,"password":"p","tls":{"enabled":true}}""",
                """{"type":"snell","tag":"DE","server":"198.51.100.2","server_port":7777,"psk":"k","version":4}""",
                """{"type":"vless","tag":"local","server":"127.0.0.1","server_port":443,"uuid":"00000000-0000-0000-0000-000000000000"}""",
            ),
        )

        assertEquals(listOf("AT2", "DE"), missing.map { it.tag })
    }

    @Test
    fun `Xray's hysteria is Hysteria 2, so only a version 1 server at the same endpoint is missing`() {
        val hy2 = VpnProfile.fromLink("hysteria2://pw@h.example:443?sni=h.example#H2", id = "h", addedAt = 0L)
        val missing = SingBoxOnlyServers.missing(
            listOf(hy2),
            servers(
                """{"type":"hysteria2","tag":"H2","server":"h.example","server_port":443,"password":"pw","tls":{"enabled":true}}""",
                """{"type":"hysteria","tag":"H1","server":"H.EXAMPLE","server_port":443,"auth_str":"pw","tls":{"enabled":true}}""",
            ),
        )
        assertEquals(listOf("H1"), missing.map { it.tag })
    }

    @Test
    fun `stand-ins are replaced one for one, in order, when the counts agree`() {
        val a = custom("a", "A sing-box only", "vless", "127.0.0.1", 443)
        val t = custom("t", "T", "trojan", "203.0.113.7", 993)
        val b = custom("b", "B sing-box only", "vless", "127.0.0.1", 443)
        val other = custom("o", "Other", "trojan", "203.0.113.9", 443)
        val ra = singBox("""{"type":"anytls","tag":"A","server":"192.0.2.1","server_port":443,"password":"p","tls":{"enabled":true}}""", id = "ra")
        val rb = singBox("""{"type":"snell","tag":"B","server":"192.0.2.2","server_port":7777,"psk":"k","version":4}""", id = "rb")
        val spliced = SingBoxOnlyServers.splice(listOf(other, a, t, b), listOf(a, b), listOf(ra, rb))
        assertEquals(listOf("o", "ra", "t", "rb"), spliced.map { it.id })
    }

    @Test
    fun `when the counts differ every replacement goes where the first stand-in was`() {
        val a = custom("a", "A sing-box only", "vless", "127.0.0.1", 443)
        val t = custom("t", "T", "trojan", "203.0.113.7", 993)
        val b = custom("b", "B sing-box only", "vless", "127.0.0.1", 443)
        val r = (1..3).map {
            singBox("""{"type":"anytls","tag":"R$it","server":"192.0.2.$it","server_port":443,"password":"p","tls":{"enabled":true}}""", id = "r$it")
        }
        assertEquals(listOf("r1", "r2", "r3", "t"), SingBoxOnlyServers.splice(listOf(a, t, b), listOf(a, b), r).map { it.id })
        assertEquals(listOf("a", "t"), SingBoxOnlyServers.splice(listOf(a, t), emptyList(), r).map { it.id })
    }
}
