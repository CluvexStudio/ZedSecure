package dev.cluvex.zedsecure.desktop.core

import dev.cluvex.zedsecure.domain.config.DnsTunnelProfile
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopDnsTunnelArgsTest {
    private val key = "0".repeat(63) + "1"
    private val bin = File("/tmp/zeddns")

    private fun args(profile: DnsTunnelProfile): List<String> =
        DesktopDnsTunnel(profile, File("/tmp")).clientArgs(bin, "127.0.0.1:7300")

    private fun dnstt(tweak: (DnsTunnelProfile) -> DnsTunnelProfile = { it }) =
        tweak(DnsTunnelProfile(DnsTunnelProfile.ENGINE_DNSTT, "t.example.com", key, resolvers = "1.1.1.1"))

    private fun vaydns(tweak: (DnsTunnelProfile) -> DnsTunnelProfile = { it }) =
        tweak(DnsTunnelProfile(DnsTunnelProfile.ENGINE_VAYDNS, "t.example.com", key, resolvers = "1.1.1.1"))

    private fun List<String>.valueOf(flag: String): String? =
        indexOf(flag).takeIf { it >= 0 && it + 1 < size }?.let { this[it + 1] }

    @Test
    fun `the binary and the essentials always go on the line`() {
        val a = args(dnstt())
        assertEquals(bin.absolutePath, a.first())
        assertEquals("t.example.com", a.valueOf("-domain"))
        assertEquals(key, a.valueOf("-pubkey"))
        assertEquals("127.0.0.1:7300", a.valueOf("-listen"))
        assertEquals("1.1.1.1:53", a.valueOf("-dns-addr"))
    }

    @Test
    fun `a DNSTT profile asks for the dnstt wire, a VayDNS one does not`() {
        assertTrue("-dnstt-compat" in args(dnstt()))
        assertFalse("-dnstt-compat" in args(vaydns()))
        assertTrue(
            "-dnstt-compat" in args(vaydns { it.copy(dnsttCompat = true) }),
            "a VayDNS profile aimed at a plain dnstt server must ask for it",
        )
    }

    @Test
    fun `DNSTT-only and VayDNS-only options never cross over`() {
        val d = args(dnstt { it.copy(authoritative = true, dnsPayloadSize = 60) })
        assertTrue("-authoritative" in d)
        assertEquals("60", d.valueOf("-max-payload"))
        assertFalse("-record-type" in d || "-max-qname-len" in d, "VayDNS options leaked into a DNSTT line")

        val v = args(vaydns { it.copy(recordType = "cname", maxQnameLen = 150, rps = 200.0) })
        assertEquals("cname", v.valueOf("-record-type"))
        assertEquals("150", v.valueOf("-max-qname-len"))
        assertFalse("-authoritative" in v || "-max-payload" in v, "DNSTT options leaked into a VayDNS line")
    }

    @Test
    fun `engine defaults are left off the line so the engine keeps its own`() {
        val v = args(vaydns())
        assertFalse("-max-qname-len" in v, "101 is the engine's own default for VayDNS")
        assertFalse("-rps" in v, "0 means unlimited; the flag would say the same")
        assertFalse("-idle-timeout" in v)
        assertFalse("-keepalive" in v)
    }

    @Test
    fun `the transport prefix reaches the resolver argument`() {
        assertEquals("tcp://1.1.1.1:53", args(dnstt { it.copy(dnsTransport = DnsTunnelProfile.TRANSPORT_TCP) }).valueOf("-dns-addr"))
        assertEquals("tls://1.1.1.1:853", args(dnstt { it.copy(dnsTransport = DnsTunnelProfile.TRANSPORT_DOT) }).valueOf("-dns-addr"))
        assertEquals(
            "https://dns.example/dns-query",
            args(
                dnstt {
                    it.copy(dnsTransport = DnsTunnelProfile.TRANSPORT_DOH, dohUrl = "https://dns.example/dns-query")
                },
            ).valueOf("-dns-addr"),
        )
    }

    @Test
    fun `the resolver strategy and its spread are passed through`() {
        val a = args(
            dnstt {
                it.copy(
                    resolvers = "1.1.1.1,8.8.8.8",
                    resolverMode = DnsTunnelProfile.MODE_ROUND_ROBIN,
                    rrSpreadCount = 2,
                )
            },
        )
        assertEquals(DnsTunnelProfile.MODE_ROUND_ROBIN, a.valueOf("-resolver-mode"))
        assertEquals("2", a.valueOf("-rr-spread"))
        assertEquals("1.1.1.1:53,8.8.8.8:53", a.valueOf("-dns-addr"))
    }

    @Test
    fun `SOCKS credentials travel together or not at all`() {
        assertFalse("-socks-user" in args(dnstt()))
        val a = args(dnstt { it.copy(socksUser = "tunnel", socksPass = "s3cret") })
        assertEquals("tunnel", a.valueOf("-socks-user"))
        assertEquals("s3cret", a.valueOf("-socks-pass"))
    }

    @Test
    fun `every flag carries a value, and none is repeated`() {
        val a = args(vaydns { it.copy(recordType = "cname", maxQnameLen = 150, idleTimeout = 30, keepalive = 5) })
        val flags = a.filter { it.startsWith("-") }
        assertEquals(flags.size, flags.toSet().size, "a flag appears twice: $flags")
        flags.filter { it != "-dnstt-compat" && it != "-authoritative" }.forEach { flag ->
            assertTrue(a.valueOf(flag)?.startsWith("-") == false, "$flag has no value")
        }
    }
}
