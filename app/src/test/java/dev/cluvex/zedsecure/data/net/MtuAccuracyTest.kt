package dev.cluvex.zedsecure.data.net

import dev.cluvex.zedsecure.domain.config.Protocol
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MtuAccuracyTest {
    @After
    fun reset() {
        MtuProbe.linkMtu = { null }
        MtuProbe.vpnActive = { false }
        MtuProbe.probe = { _, _, _ -> MtuVerdict.Unavailable }
    }

    private fun network(limit: Int, only: String? = null, dropEvery: Int = 0) {
        var seen = 0
        MtuProbe.probe = { host, payload, _ ->
            seen++
            when {
                only != null && host != only -> MtuVerdict.NoReply
                dropEvery > 0 && seen % dropEvery == 0 -> MtuVerdict.NoReply
                payload + 28 <= limit -> MtuVerdict.Fits
                else -> MtuVerdict.TooBig
            }
        }
    }

    @Test
    fun `the probes go to the user's server, not to a public resolver`() {
        network(limit = 1400, only = "vpn.example.com")

        val r = runBlocking { MtuOptimizer.optimize("vpn.example.com", MtuOverheads.of(Protocol.VLESS)) }

        assertTrue("expected a measurement, got $r", r is MtuResult.Measured)
        r as MtuResult.Measured
        assertEquals("vpn.example.com", r.host)
        assertTrue("must be flagged as measured to the server", r.toServer)
        assertEquals(1400, r.pathMtu)
    }

    @Test
    fun `a server that ignores ICMP falls back, and says the answer is for another path`() {
        network(limit = 1400, only = "1.1.1.1")

        val r = runBlocking { MtuOptimizer.optimize("silent.example.com", MtuOverheads.of(Protocol.VLESS)) }

        r as MtuResult.Measured
        assertEquals("1.1.1.1", r.host)
        assertFalse("the user must be told this is a different path", r.toServer)
    }

    @Test
    fun `one dropped probe no longer costs a hundred bytes of MTU`() {
        network(limit = 1400, dropEvery = 3)

        val r = runBlocking { MtuOptimizer.optimize("vpn.example.com", MtuOverheads.of(Protocol.VLESS)) }

        r as MtuResult.Measured
        assertEquals("the loss must not move the boundary", 1400, r.pathMtu)
    }

    @Test
    fun `a filtered network reports that it could not be measured, rather than the floor`() {
        MtuProbe.probe = { _, _, _ -> MtuVerdict.NoReply }

        val r = runBlocking { MtuOptimizer.optimize("vpn.example.com") }

        assertTrue("expected NotMeasurable, got $r", r is MtuResult.NotMeasurable)
    }

    @Test
    fun `a live VPN makes the measurement meaningless, and it refuses to run`() {
        network(limit = 1400)
        MtuProbe.vpnActive = { true }

        assertEquals(MtuResult.VpnActive, runBlocking { MtuOptimizer.optimize("vpn.example.com") })
    }

    @Test
    fun `WireGuard framing is the exact 60 bytes it is specified to be`() {
        val wg = MtuOverheads.of(Protocol.WIREGUARD, ipv6 = false)

        assertEquals(60, wg.total)
        assertEquals(80, MtuOverheads.of(Protocol.WIREGUARD, ipv6 = true).total)

        assertEquals(1440, MtuOptimizer.recommend(1500, wg.total))
    }

    @Test
    fun `each transport is charged for what it actually adds`() {
        val plain = MtuOverheads.of(Protocol.VLESS, network = "tcp", tls = true).total
        val ws = MtuOverheads.of(Protocol.VLESS, network = "ws", tls = true).total
        val grpc = MtuOverheads.of(Protocol.VLESS, network = "grpc", tls = true).total
        val vmessWs = MtuOverheads.of(Protocol.VMESS, network = "ws", tls = true).total
        val quic = MtuOverheads.of(Protocol.HYSTERIA).total

        assertEquals("IPv4 20 + TCP 32 + TLS 21", 73, plain)
        assertTrue("a WebSocket frame is not free", ws > plain)
        assertTrue("gRPC costs more than a WebSocket frame", grpc > ws)
        assertTrue("VMess chunks cost more than VLESS, which adds nothing", vmessWs > ws)
        assertTrue("QUIC has no TCP and no TLS record layer", quic < plain)

        assertEquals(5, setOf(plain, ws, grpc, vmessWs, quic).size)
    }

    @Test
    fun `the breakdown says where every byte went`() {
        val ws = MtuOverheads.of(Protocol.VMESS, network = "ws", tls = true)

        assertEquals(ws.total, ws.parts.sumOf { it.bytes })
        assertTrue(ws.explain().contains("WebSocket"))
        assertTrue(ws.explain().contains("= ${ws.total} bytes"))
    }

    @Test
    fun `an unknown tunnel is charged the most expensive shape, not an average`() {
        val worst = MtuOverheads.worstCase().total

        listOf(
            MtuOverheads.of(Protocol.VLESS, "tcp", true),
            MtuOverheads.of(Protocol.TROJAN, "ws", true),
            MtuOverheads.of(Protocol.WIREGUARD),
            MtuOverheads.of(Protocol.HYSTERIA),
        ).forEach { assertTrue("worst case must not be beaten by ${it.explain()}", worst >= it.total) }
    }

    @Test
    fun `the recommendation never leaves what a TUN will accept`() {
        assertEquals(MtuOptimizer.MIN_MTU, MtuOptimizer.recommend(600, 80))
        assertEquals(MtuOptimizer.MAX_MTU, MtuOptimizer.recommend(9000, 80))
    }
}
