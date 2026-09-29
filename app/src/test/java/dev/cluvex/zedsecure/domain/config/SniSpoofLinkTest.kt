package dev.cluvex.zedsecure.domain.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SniSpoofLinkTest {
    private val link = "vless://11111111-2222-3333-4444-555555555555@edge.example.com:443?type=ws#Node"

    @Test
    fun `round-trips a share-link payload`() {
        val p = SniSpoofProfile(link = link, fakeSni = "www.hcaptcha.com", cleanIp = "104.19.230.21")
        val (name, back) = SniSpoofLink.parse(SniSpoofLink.build(p, "My spoof"))!!
        assertEquals("My spoof", name)
        assertEquals(p, back)
    }

    @Test
    fun `round-trips a custom JSON payload with its own braces and quotes`() {
        val json = """{"outbounds":[{"tag":"proxy","protocol":"vless","settings":{"vnext":[{"address":"a.example.com","port":8443}]}}]}"""
        val p = SniSpoofProfile(link = json, fakeSni = "www.speedtest.net")
        val (_, back) = SniSpoofLink.parse(SniSpoofLink.build(p, ""))!!
        assertEquals(json, back.link)
        assertEquals("www.speedtest.net", back.fakeSni)
        assertEquals("", back.cleanIp)
    }

    @Test
    fun `a non-ascii remark survives`() {
        val p = SniSpoofProfile(link = link, fakeSni = "a.example.com")
        val (name, _) = SniSpoofLink.parse(SniSpoofLink.build(p, "زن زندگی آزادی"))!!
        assertEquals("زن زندگی آزادی", name)
    }

    @Test
    fun `rejects links that are not usable`() {
        assertNull(SniSpoofLink.parse("vless://x@y:443"))
        assertNull(SniSpoofLink.parse("snispoof://"))

        assertNull(SniSpoofLink.parse(SniSpoofLink.build(SniSpoofProfile(link = link, fakeSni = ""), "n")))
    }

    @Test
    fun `the profile exposes the link so share and copy work`() {
        val p = SniSpoofProfile(link = link, fakeSni = "www.hcaptcha.com")
        val profile = VpnProfile.fromSniSpoof(p, id = "i", addedAt = 0L, name = "Spoof")
        val payload = profile.rawPayload()
        assertNotNull("SNI-spoof profiles used to expose nothing to share", payload)
        assertEquals(p, SniSpoofLink.parse(payload!!)!!.second)
    }
}
