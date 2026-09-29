package dev.cluvex.zedsecure.core.tor

import dev.cluvex.zedsecure.domain.config.LocalPorts
import dev.cluvex.zedsecure.domain.model.AppSettings
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TorUpstreamProxyTest {
    private val dir = File("/tmp/zed-tor-test")

    private fun torrc(settings: AppSettings, port: Int?): String =
        TorConfigBuilder.build(settings, "/lib", dir, dir, port)

    @Test
    fun `no upstream port means no Socks5Proxy line`() {
        assertFalse(torrc(AppSettings(), null).contains("Socks5Proxy"))
    }

    @Test
    fun `upstream proxy is emitted without bridges`() {
        val out = torrc(AppSettings(torBridgesMode = "none"), LocalPorts.XRAY_SOCKS)
        assertTrue(out.contains("Socks5Proxy 127.0.0.1:${LocalPorts.XRAY_SOCKS}"))
    }

    @Test
    fun `upstream proxy is emitted for a proxy-capable transport`() {
        val out = torrc(
            AppSettings(torBridgesMode = "default", torBridgeTransport = "obfs4"),
            LocalPorts.PSIPHON_SOCKS,
        )
        assertTrue(out.contains("Socks5Proxy 127.0.0.1:${LocalPorts.PSIPHON_SOCKS}"))
    }

    @Test
    fun `upstream proxy is suppressed for a transport that refuses it`() {
        val settings = AppSettings(torBridgesMode = "default", torBridgeTransport = "dnstt")
        assertFalse(TorConfigBuilder.supportsUpstreamProxy(settings))
        assertFalse(torrc(settings, LocalPorts.XRAY_SOCKS).contains("Socks5Proxy"))
    }

    @Test
    fun `tor still listens on its own socks port when chained`() {
        val out = torrc(AppSettings(torBridgesMode = "none"), LocalPorts.XRAY_SOCKS)
        assertTrue(out.contains("SocksPort 127.0.0.1:${LocalPorts.TOR_SOCKS}"))
    }
}
