package dev.cluvex.zedsecure.domain.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZedLinkTest {
    private fun roundTrip(name: String, source: ProfileSource) {
        val link = ZedLink.build(name, source)
        assertNotNull("$source must produce a link", link)
        assertTrue(ZedLink.isZedLink(link!!))
        val back = ZedLink.parse(link)
        assertNotNull("$source must parse back", back)
        assertEquals(name, back!!.name)
        assertEquals("$source must survive intact", source, back.source)
    }

    @Test
    fun `openconnect survives with every field it carries`() {
        roundTrip(
            "Work VPN",
            ProfileSource.OpenConnect(
                OpenConnectProfile(
                    server = "https://vpn.example.com:8443/group",
                    protocol = OpenConnectProfile.PROTO_ANYCONNECT,
                    username = "staffuser",
                    password = "s3cret",
                    authgroup = "staff",
                    mtu = 1400,
                    disableDtls = true,
                    formEntries = mapOf("secondary" to "1234"),
                ),
            ),
        )
    }

    @Test
    fun `ikev2 survives`() {
        roundTrip("Home", ProfileSource.Ikev2(Ikev2Profile(server = "vpn.example.com", username = "u", mtu = 1400)))
    }

    @Test
    fun `ssh survives`() {
        roundTrip(
            "Box",
            ProfileSource.Ssh(SshProfile(host = "example.com", port = 22, username = "root", password = "pw")),
        )
    }

    @Test
    fun `tor is a data object and still round-trips`() {
        roundTrip("Tor", ProfileSource.Tor)
    }

    @Test
    fun `dns tunnel survives`() {
        roundTrip(
            "DNSTT",
            ProfileSource.DnsTunnel(
                DnsTunnelProfile(
                    engine = DnsTunnelProfile.ENGINE_DNSTT,
                    domain = "t.example.com",
                    publicKey = "abc123",
                    resolvers = "8.8.8.8",
                    dnsTransport = DnsTunnelProfile.TRANSPORT_TCP,
                ),
            ),
        )
    }

    @Test
    fun `a chain carries its members inside the link`() {
        val members = listOf(
            "vless://11111111-2222-3333-4444-555555555555@a.example.com:443?type=ws#A",
            "vless://11111111-2222-3333-4444-555555555555@b.example.com:443?type=ws#B",
        )
        val link = ZedLink.build("Chain", ProfileSource.ProxyChain(listOf("id-a", "id-b")), members)!!
        val back = ZedLink.parse(link)!!

        assertEquals(members, back.members)
        assertTrue(back.source is ProfileSource.ProxyChain)
    }

    @Test
    fun `handles claims exactly the sources with no community format`() {
        listOf(
            ProfileSource.Tor,
            ProfileSource.Ssh(SshProfile(host = "h")),
            ProfileSource.Ikev2(Ikev2Profile()),
            ProfileSource.OpenConnect(OpenConnectProfile()),
            ProfileSource.MasterDns(MasterDnsProfile()),
            ProfileSource.ProxyChain(emptyList()),
        ).forEach { assertTrue("$it should be handled", ZedLink.handles(it)) }

        listOf(
            ProfileSource.Link("vless://x@y:443"),
            ProfileSource.RawJson("{}"),
            ProfileSource.Sealed("zzz"),
            ProfileSource.SniSpoof(SniSpoofProfile(link = "vless://x@y:443", fakeSni = "a.b")),
        ).forEach { assertTrue("$it should NOT be handled", !ZedLink.handles(it)) }
    }

    @Test
    fun `garbage does not parse`() {
        assertNull(ZedLink.parse("vless://x@y:443"))
        assertNull(ZedLink.parse("zedsecure://"))
        assertNull(ZedLink.parse("zedsecure://!!!not-base64!!!"))
    }

    @Test
    fun `a sealed profile exposes nothing`() {
        val p = VpnProfile(
            id = "i", name = "Locked", protocol = "VLESS", address = "a", port = 1,
            transportLabel = "", source = ProfileSource.Sealed("base64"), addedAt = 0L,
        )
        assertNull(p.rawPayload())
    }

    @Test
    fun `every non-Xray engine now has a share payload`() {
        fun profile(src: ProfileSource) = VpnProfile(
            id = "i", name = "N", protocol = "X", address = "a", port = 1,
            transportLabel = "", source = src, addedAt = 0L,
        )
        listOf(
            ProfileSource.Tor,
            ProfileSource.Ssh(SshProfile(host = "h")),
            ProfileSource.Ikev2(Ikev2Profile(server = "s")),
            ProfileSource.OpenConnect(OpenConnectProfile(server = "s")),
            ProfileSource.MasterDns(MasterDnsProfile()),
            ProfileSource.Psiphon(PsiphonProfile()),
        ).forEach {
            assertNotNull("$it had no share payload", profile(it).rawPayload())
        }
    }
}
