package dev.cluvex.zedsecure.data.net

import com.sun.net.httpserver.HttpServer
import dev.cluvex.zedsecure.core.CoreProbe
import dev.cluvex.zedsecure.core.VpnManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress

class ActiveConnectionHealthTest {
    private lateinit var server: HttpServer
    private val probeUrl get() = "http://127.0.0.1:${server.address.port}/generate_204"
    private val originalProbe = CoreProbe.measureDelayDetailed

    @Before
    fun startServer() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
            createContext("/generate_204") { exchange ->
                exchange.sendResponseHeaders(204, -1)
                exchange.close()
            }
            start()
        }

        CoreProbe.measureDelayDetailed = { CoreProbe.DelayOutcome(-1L, "core instance is nil") }
    }

    @After
    fun stop() {
        server.stop(0)
        CoreProbe.measureDelayDetailed = originalProbe
        VpnManager.setActiveKind(null)
        VpnManager.activeSocksPort = null
    }

    @Test
    fun `only sessions the Xray core carries are measured by it`() {
        listOf(VpnManager.KIND_XRAY, VpnManager.KIND_SNISPOOF, VpnManager.KIND_CROSS_CHAIN, null)
            .forEach { assertTrue("$it", PingService.runsXrayCore(it)) }
        listOf(
            VpnManager.KIND_OPENCONNECT, VpnManager.KIND_IKEV2, VpnManager.KIND_TOR, VpnManager.KIND_PSIPHON,
            VpnManager.KIND_SSH, VpnManager.KIND_SINGBOX, VpnManager.KIND_DNS_TUNNEL, VpnManager.KIND_MASTERDNS,
        ).forEach { assertFalse(it, PingService.runsXrayCore(it)) }
    }

    @Test
    fun `an IKEv2 or OpenConnect session is measured with a real request, not the idle core`() = runBlocking<Unit> {
        for (kind in listOf(VpnManager.KIND_IKEV2, VpnManager.KIND_OPENCONNECT)) {
            VpnManager.setActiveKind(kind)
            val health = PingService.activeConnectionHealth(probeUrl)
            assertTrue("$kind: ${health.error}", health.ok)
            assertNull(kind, health.error)
        }
    }

    @Test
    fun `an Xray session still reports what the core said`() = runBlocking<Unit> {
        VpnManager.setActiveKind(VpnManager.KIND_XRAY)
        val health = PingService.activeConnectionHealth(probeUrl)
        assertFalse(health.ok)
        assertEquals("core instance is nil", health.error)
    }

    @Test
    fun `a session without the core whose requests fail still reports a fault`() = runBlocking<Unit> {
        VpnManager.setActiveKind(VpnManager.KIND_TOR)

        VpnManager.activeSocksPort = java.net.ServerSocket(0).use { it.localPort }
        val health = PingService.activeConnectionHealth(probeUrl)
        assertFalse(health.ok)
        assertTrue("an explanation is kept", !health.error.isNullOrBlank())
    }
}
