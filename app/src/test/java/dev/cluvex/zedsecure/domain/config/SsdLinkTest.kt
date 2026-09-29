package dev.cluvex.zedsecure.domain.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class SsdLinkTest {
    private fun ssd(json: String): String =
        "ssd://" + Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())

    private val subscription = """
        {"airport":"Test Air","port":443,"encryption":"chacha20-ietf-poly1305","password":"shared",
         "servers":[
           {"id":0,"remarks":"Tokyo","server":"jp.example.com"},
           {"id":1,"remarks":"Osaka","server":"jp2.example.com","port":8388},
           {"id":2,"server":"10.0.0.9","encryption":"aes-256-gcm","password":"own"}
         ]}
    """.trimIndent()

    @Test
    fun `recognises the scheme`() {
        assertTrue(SsdLink.isSsdLink("ssd://abc"))
        assertEquals(false, SsdLink.isSsdLink("ss://abc"))
    }

    @Test
    fun `every node becomes a link, not just the first`() {
        assertEquals(3, SsdLink.parse(ssd(subscription)).size)
        assertEquals("Test Air", SsdLink.groupName(ssd(subscription)))
    }

    @Test
    fun `nodes inherit the cipher, password and port stated once`() {
        val first = ConfigParser.parse(SsdLink.parse(ssd(subscription))[0])

        assertEquals(Protocol.SHADOWSOCKS, first.protocol)
        assertEquals("jp.example.com", first.address)
        assertEquals(443, first.port)
        assertEquals("chacha20-ietf-poly1305", first.shadowsocksMethod)
        assertEquals("shared", first.userId)
        assertEquals("Test Air - Tokyo", first.remark)
    }

    @Test
    fun `a node overrides what it states for itself`() {
        val links = SsdLink.parse(ssd(subscription))

        assertEquals(8388, ConfigParser.parse(links[1]).port)

        val third = ConfigParser.parse(links[2])
        assertEquals("aes-256-gcm", third.shadowsocksMethod)
        assertEquals("own", third.userId)

        assertEquals("Test Air - 10.0.0.9:443", third.remark)
    }

    @Test
    fun `a non-Latin group name survives the round trip`() {
        val json = """{"airport":"تهران","port":443,"encryption":"aes-256-gcm","password":"p",
            "servers":[{"remarks":"سرور","server":"1.2.3.4"}]}"""

        assertEquals("تهران - سرور", ConfigParser.parse(SsdLink.parse(ssd(json)).single()).remark)
    }

    @Test
    fun `a malformed or empty subscription yields nothing rather than a broken profile`() {
        assertTrue(SsdLink.parse("ssd://").isEmpty())
        assertTrue(SsdLink.parse("ssd://not-base64-%%%").isEmpty())
        assertTrue(SsdLink.parse(ssd("""{"airport":"x","servers":[]}""")).isEmpty())

        assertTrue(SsdLink.parse(ssd("""{"airport":"x","port":443,"servers":[{"server":"1.2.3.4"}]}""")).isEmpty())
    }
}
