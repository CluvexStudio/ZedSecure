package dev.cluvex.zedsecure.domain.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossChainTest {
    private val ranges = listOf(
        Triple("xray-socks", LocalPorts.XRAY_SOCKS, LocalPorts.XRAY_SOCKS),
        Triple("xray-http", LocalPorts.XRAY_HTTP, LocalPorts.XRAY_HTTP),
        Triple("sni-spoof", LocalPorts.SNI_SPOOF, LocalPorts.SNI_SPOOF),
        Triple("desktop-metrics", LocalPorts.DESKTOP_METRICS, LocalPorts.DESKTOP_METRICS),
        Triple("shim", LocalPorts.SHIM, LocalPorts.SHIM),
        Triple("psiphon-socks", LocalPorts.PSIPHON_SOCKS, LocalPorts.PSIPHON_SOCKS),
        Triple("psiphon-http", LocalPorts.PSIPHON_HTTP, LocalPorts.PSIPHON_HTTP),
        Triple("ssh", LocalPorts.SSH, LocalPorts.SSH_MAX),
        Triple("dns-tunnel", LocalPorts.DNS_TUNNEL, LocalPorts.DNS_TUNNEL_MAX),
        Triple("master-dns", LocalPorts.MASTER_DNS, LocalPorts.MASTER_DNS_MAX),
        Triple("ssh-over-dns", LocalPorts.SSH_OVER_DNS, LocalPorts.SSH_OVER_DNS_MAX),
        Triple("tor", LocalPorts.TOR_SOCKS, LocalPorts.TOR_SOCKS),
    )

    @Test
    fun `no two engine port ranges overlap`() {
        for (i in ranges.indices) {
            for (j in i + 1 until ranges.size) {
                val (an, a0, a1) = ranges[i]
                val (bn, b0, b1) = ranges[j]
                assertTrue(
                    "$an ($a0..$a1) overlaps $bn ($b0..$b1)",
                    a1 < b0 || b1 < a0,
                )
            }
        }
    }

    @Test
    fun `xray keeps 10808 because stored custom configs are rewritten to it`() {
        assertEquals(10808, LocalPorts.XRAY_SOCKS)
        assertEquals(LocalProxy.SOCKS_PORT, LocalPorts.XRAY_SOCKS)
    }

    @Test
    fun `psiphon no longer collides with xray`() {
        assertTrue(LocalPorts.PSIPHON_SOCKS != LocalPorts.XRAY_SOCKS)
        assertTrue(LocalPorts.PSIPHON_HTTP != LocalPorts.XRAY_HTTP)
    }

    @Test
    fun `a scan that exhausts its range cannot reach the next engine`() {
        assertTrue(LocalPorts.DNS_TUNNEL_MAX < LocalPorts.MASTER_DNS)
        assertTrue(LocalPorts.SSH_MAX < LocalPorts.DNS_TUNNEL)
        assertTrue(LocalPorts.MASTER_DNS_MAX < LocalPorts.SSH_OVER_DNS)
        assertTrue(LocalPorts.SHIM < LocalPorts.PSIPHON_SOCKS)
    }

    private fun profile(id: String, source: ProfileSource) = VpnProfile(
        id = id, name = id, protocol = "X", address = "a", port = 1,
        transportLabel = "t", source = source, addedAt = 0,
    )

    private val link = profile(
        "link",
        ProfileSource.Link("vless://11111111-1111-1111-1111-111111111111@a.example:443?encryption=none&security=tls&type=tcp#A"),
    )
    private val psiphon = profile("psiphon", ProfileSource.Psiphon(PsiphonProfile()))
    private val tor = profile("tor", ProfileSource.Tor)
    private val ssh = profile("ssh", ProfileSource.Ssh(SshProfile(host = "h", port = 22, username = "u")))
    private val dnstt = profile(
        "dnstt",
        ProfileSource.DnsTunnel(DnsTunnelProfile(engine = DnsTunnelProfile.ENGINE_DNSTT, domain = "d", publicKey = "k")),
    )
    private val vaydns = profile(
        "vaydns",
        ProfileSource.DnsTunnel(DnsTunnelProfile(engine = DnsTunnelProfile.ENGINE_VAYDNS, domain = "d", publicKey = "k")),
    )
    private val ikev2 = profile("ikev2", ProfileSource.Ikev2(Ikev2Profile(server = "s")))

    @Test
    fun `carriers are limited to xray, psiphon and tor`() {
        assertTrue("Xray server", link.canCarryChain)
        assertTrue("Psiphon", psiphon.canCarryChain)
        assertTrue("Tor", tor.canCarryChain)
        assertFalse("SSH is capable but not offered", ssh.canCarryChain)
        assertFalse("DNSTT is capable but not offered", dnstt.canCarryChain)
        assertFalse("VayDNS is capable but not offered", vaydns.canCarryChain)

        assertFalse("IKEv2", ikev2.canCarryChain)
    }

    @Test
    fun `exits are limited to xray, psiphon and tor`() {
        assertTrue("Psiphon has UpstreamProxyURL", psiphon.canDialThroughProxy)
        assertTrue("Tor has Socks5Proxy", tor.canDialThroughProxy)

        assertTrue("Xray via sockopt.dialerProxy", link.canDialThroughProxy)
        assertFalse("SSH is capable but not offered", ssh.canDialThroughProxy)
        assertFalse("DNSTT is capable but not offered", dnstt.canDialThroughProxy)

        assertFalse("VayDNS", vaydns.canDialThroughProxy)
        assertFalse("IKEv2", ikev2.canDialThroughProxy)
    }

    @Test
    fun `all six directions between xray, psiphon and tor are offered`() {
        val directions = listOf(
            Triple("Xray -> Psiphon", link, psiphon),
            Triple("Psiphon -> Xray", psiphon, link),
            Triple("Xray -> Tor", link, tor),
            Triple("Tor -> Xray", tor, link),
            Triple("Tor -> Psiphon", tor, psiphon),
            Triple("Psiphon -> Tor", psiphon, tor),
        )
        for ((label, inner, outer) in directions) {
            assertTrue("$label: ${outer.name} must be a valid carrier", outer.canCarryChain)
            assertTrue("$label: ${inner.name} must be a valid exit", inner.canDialThroughProxy)
        }
    }

    @Test
    fun `a proxy chain is xray-shaped and stays chainable`() {
        val chain = profile("chain", ProfileSource.ProxyChain(listOf("a", "b")))
        assertTrue(chain.canCarryChain)
        assertTrue(chain.canDialThroughProxy)
        assertTrue("its inbound is udp:true like any Xray config", chain.carrierRelaysUdp)
    }

    @Test
    fun `only Xray and Psiphon relay UDP for an exit`() {
        assertTrue("Xray inbound is udp:true", link.carrierRelaysUdp)
        assertTrue("Psiphon SOCKS does UDP ASSOCIATE", psiphon.carrierRelaysUdp)
        assertFalse("Tor SOCKS is CONNECT-only", tor.carrierRelaysUdp)
    }

    @Test
    fun `cross chain round-trips through serialization`() {
        val p = VpnProfile.fromCrossChain(
            innerId = "in", outerId = "out", id = "x", addedAt = 1L, name = "",
            innerName = "Tor", outerName = "My server",
        )
        val json = Json { ignoreUnknownKeys = true }
        val back = json.decodeFromString(VpnProfile.serializer(), json.encodeToString(VpnProfile.serializer(), p))
        assertEquals("in" to "out", back.crossChainSettings())
        assertTrue(back.isCrossChain)

        assertTrue(back.isManagedTunnel)
        assertNull(back.rawPayload())
    }

    @Test
    fun `psiphon upstream proxy is set and QUIC protocols are dropped`() {
        val base = PsiphonConfigBuilder.build(
            profile = PsiphonProfile(),
            dataDirPath = "/tmp/psi",
            socksPort = LocalPorts.PSIPHON_SOCKS,
            httpPort = LocalPorts.PSIPHON_HTTP,
        )
        val before = Json.parseToJsonElement(base).jsonObject["LimitTunnelProtocols"]!!.jsonArray
            .map { it.jsonPrimitive.content }
        assertTrue("fixture must contain QUIC to be meaningful", before.any { it.contains("QUIC") })

        val patched = PsiphonConfigBuilder.withUpstreamProxy(base, LocalPorts.XRAY_SOCKS)
        val o = Json.parseToJsonElement(patched).jsonObject
        assertEquals(
            "socks5://127.0.0.1:${LocalPorts.XRAY_SOCKS}",
            o["UpstreamProxyURL"]!!.jsonPrimitive.content,
        )
        val after = o["LimitTunnelProtocols"]!!.jsonArray.map { it.jsonPrimitive.content }

        assertTrue("QUIC must be gone", after.none { it.contains("QUIC") })
        assertTrue("non-QUIC must survive", after.isNotEmpty())

        assertEquals(
            Json.parseToJsonElement(base).jsonObject["LocalSocksProxyPort"],
            o["LocalSocksProxyPort"],
        )
    }
}
