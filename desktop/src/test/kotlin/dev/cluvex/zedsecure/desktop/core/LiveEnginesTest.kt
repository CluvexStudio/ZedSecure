package dev.cluvex.zedsecure.desktop.core

import dev.cluvex.zedsecure.desktop.platform.DesktopXray
import dev.cluvex.zedsecure.domain.config.PsiphonProfile
import dev.cluvex.zedsecure.domain.model.AppSettings
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class LiveEnginesTest {
    private val live = System.getenv("ZEDSECURE_LIVE_ENGINES") == "1"

    private fun fetchThroughFront(upstreamPort: Int): Int {
        val work = Files.createTempDirectory("live-front").toFile()
        val front = XrayCore(work)
        try {
            check(front.start(DesktopXray.frontConfig(FRONT_PORT, upstreamPort))) { "the Xray front did not start" }
            val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", FRONT_PORT))
            var last: Exception? = null
            repeat(4) {
                val connection = URI("https://www.google.com/generate_204").toURL().openConnection(proxy) as HttpURLConnection
                connection.connectTimeout = 30_000
                connection.readTimeout = 30_000
                try {
                    return connection.responseCode
                } catch (e: Exception) {
                    last = e
                    Thread.sleep(3_000)
                } finally {
                    connection.disconnect()
                }
            }
            throw last!!
        } finally {
            front.stop()
            work.deleteRecursively()
        }
    }

    @Test
    fun `tor bootstraps from the bundle and carries traffic`() {
        assumeTrue(live)
        val work = Files.createTempDirectory("live-tor").toFile()
        val bridge = System.getenv("ZEDSECURE_LIVE_TOR_BRIDGE")
        val settings = if (bridge.isNullOrBlank()) AppSettings() else AppSettings(torBridgesMode = "default", torBridgeTransport = bridge)
        val upstream = System.getenv("ZEDSECURE_LIVE_TOR_UPSTREAM")?.toIntOrNull()
        val tor = DesktopTor(settings, work, upstreamSocksPort = upstream)
        try {
            val started = tor.start(onProgress = { println("tor $it%") })
            assertEquals(null, started.exceptionOrNull()?.message)
            assertEquals(204, fetchThroughFront(tor.socksPort))
        } finally {
            tor.stop()
            work.deleteRecursively()
        }
    }

    @Test
    fun `psiphon connects from the bundle and carries traffic`() {
        assumeTrue(live)
        val work = Files.createTempDirectory("live-psiphon").toFile()
        val psiphon = DesktopPsiphon(PsiphonProfile(), work, socksPort = 18830, httpPort = 18831)
        try {
            val started = psiphon.start(onNotice = { println("psiphon ${it.type} ${it.data}") })
            assertEquals(null, started.exceptionOrNull()?.message)
            assertEquals(204, fetchThroughFront(psiphon.socksPort))
        } finally {
            psiphon.stop()
            work.deleteRecursively()
        }
    }

    private companion object {
        const val FRONT_PORT = 18808
    }
}
