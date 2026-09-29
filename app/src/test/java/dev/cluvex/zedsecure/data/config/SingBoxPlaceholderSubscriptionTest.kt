package dev.cluvex.zedsecure.data.config

import com.sun.net.httpserver.HttpServer
import dev.cluvex.zedsecure.domain.config.ProfileSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.Collections
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class SingBoxPlaceholderSubscriptionTest {
    private class Provider(private val bodyFor: (userAgent: String) -> String?) : AutoCloseable {
        val userAgents: MutableList<String> = Collections.synchronizedList(mutableListOf())
        private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
            createContext("/sub") { exchange ->
                val ua = exchange.requestHeaders.getFirst("User-Agent").orEmpty()
                userAgents += ua
                val body = bodyFor(ua)?.toByteArray(Charsets.UTF_8)
                if (body == null) {
                    exchange.sendResponseHeaders(404, -1)
                } else {
                    exchange.sendResponseHeaders(200, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                }
                exchange.close()
            }
            start()
        }
        val url: String get() = "http://127.0.0.1:${server.address.port}/sub"
        override fun close() = server.stop(0)
    }

    private val providers = mutableListOf<Provider>()

    private fun provider(bodyFor: (String) -> String?) = Provider(bodyFor).also { providers += it }

    @After
    fun stopProviders() = providers.forEach { it.close() }

    private fun standIn(name: String) = """{"remarks":"$name","outbounds":[
        {"tag":"proxy","protocol":"vless","settings":{"vnext":[{"address":"127.0.0.1","port":443,
          "users":[{"id":"00000000-0000-0000-0000-000000000000","encryption":"none","flow":""}]}]},
         "streamSettings":{"network":"tcp","security":"tls","tlsSettings":{"serverName":""}}},
        {"tag":"direct","protocol":"freedom"},{"tag":"block","protocol":"blackhole"}]}"""

    private fun trojan(name: String, port: Int) = """{"remarks":"$name","outbounds":[
        {"tag":"proxy","protocol":"trojan","settings":{"servers":[{"address":"203.0.113.7","port":$port,"password":"secret"}]},
         "streamSettings":{"network":"tcp","security":"tls","tlsSettings":{"serverName":"relay.example"}}},
        {"tag":"direct","protocol":"freedom"},{"tag":"block","protocol":"blackhole"}]}"""

    private val xrayFormat = "[" + listOf(
        standIn("🇺🇦 Ukraine فقط سینگ‌باکس"),
        trojan("🇦🇹 Austria ▰▰▰▰▰", 993),
        standIn("🇫🇮 Finland فقط سینگ‌باکس"),
        trojan("🇨🇭 Switzerland ▰▰▰▰▰", 995),
        standIn("🇩🇪 Berlin فقط سینگ‌باکس"),
    ).joinToString(",") + "]"

    private val anytlsUkraine =
        """{"type":"anytls","tag":"🇺🇦 Ukraine","server":"192.0.2.10","server_port":443,"password":"secret","tls":{"enabled":true,"server_name":"relay.example","insecure":true}}"""
    private val snellFinland =
        """{"type":"snell","tag":"🇫🇮 Finland","server":"192.0.2.11","server_port":27020,"psk":"key","version":4}"""
    private val anytlsBerlin =
        """{"type":"anytls","tag":"🇩🇪 Berlin","server":"198.51.100.20","server_port":8443,"password":"secret","tls":{"enabled":true,"server_name":"relay.example","insecure":true}}"""

    private fun singBoxFormat(vararg servers: String) = """{
        "log":{"level":"info"},
        "dns":{"servers":[{"type":"local","tag":"local"}]},
        "inbounds":[{"type":"tun","tag":"tun-in","address":["172.16.0.1/30"],"auto_route":true}],
        "outbounds":[
          {"type":"selector","tag":"Service","outbounds":["🇺🇦 Ukraine","🇫🇮 Finland"]},
          ${servers.joinToString(",")},
          {"type":"direct","tag":"direct"}],
        "route":{"final":"Service"}}"""

    private val fullSingBoxFormat = singBoxFormat(
        anytlsUkraine,
        """{"type":"trojan","tag":"🇦🇹 Austria","server":"203.0.113.7","server_port":993,"password":"secret","tls":{"enabled":true,"server_name":"relay.example","insecure":true}}""",
        snellFinland,
        """{"type":"trojan","tag":"🇨🇭 Switzerland","server":"203.0.113.7","server_port":995,"password":"secret","tls":{"enabled":true,"server_name":"relay.example","insecure":true}}""",
        anytlsBerlin,
    )

    private fun byUserAgent(xray: String?, singBox: String?, other: String? = null): (String) -> String? = { ua ->
        when {
            ua.startsWith(ConfigRepository.V2RAYNG_UA_PREFIX) -> xray
            ua.startsWith("SFA/") -> singBox
            else -> other
        }
    }

    private fun ConfigRepository.servers(subscriptionId: String) =
        profiles.value.filter { it.subscriptionId == subscriptionId }

    @Test
    fun `stand-ins are replaced by the sing-box format's real servers, in the provider's order`() {
        val provider = provider(byUserAgent(xray = xrayFormat, singBox = fullSingBoxFormat))
        val repo = ConfigRepository(InMemoryKeyValueStore())
        val sub = repo.addSubscription("Provider", provider.url)

        val count = runBlocking { repo.updateSubscription(sub.id) }.getOrThrow()

        val servers = repo.servers(sub.id)
        assertEquals(5, count)
        assertEquals(
            listOf("🇺🇦 Ukraine", "🇦🇹 Austria ▰▰▰▰▰", "🇫🇮 Finland", "🇨🇭 Switzerland ▰▰▰▰▰", "🇩🇪 Berlin"),
            servers.map { it.name },
        )
        assertEquals(listOf("ANYTLS", "CUSTOM", "SNELL", "CUSTOM", "ANYTLS"), servers.map { it.protocol })
        assertTrue("no stand-in may be left", servers.none { it.address == "127.0.0.1" })

        assertTrue(servers.filter { it.protocol == "CUSTOM" }.all { it.source is ProfileSource.RawJson })
        servers.filter { it.source is ProfileSource.SingBox }.forEach {
            assertTrue(it.name, it.toXrayConfigJson().contains("\"singbox\""))
        }
        assertEquals(5, repo.subscriptions.value.single().serverCount)
        assertEquals(
            listOf(ConfigRepository.DEFAULT_UA, ConfigRepository.SING_BOX_UA),
            provider.userAgents.toList(),
        )
    }

    @Test
    fun `a refresh keeps the replaced servers' ids and the selection on one of them`() {
        val provider = provider(byUserAgent(xray = xrayFormat, singBox = fullSingBoxFormat))
        val repo = ConfigRepository(InMemoryKeyValueStore())
        val sub = repo.addSubscription("Provider", provider.url)
        runBlocking { repo.updateSubscription(sub.id) }.getOrThrow()
        val before = repo.servers(sub.id)
        val finland = before.single { it.name == "🇫🇮 Finland" }
        repo.setActive(finland.id)
        repo.setPing(finland.id, 321)

        val count = runBlocking { repo.updateSubscription(sub.id) }.getOrThrow()

        assertEquals(5, count)
        assertEquals(before.map { it.id }, repo.servers(sub.id).map { it.id })
        assertEquals(finland.id, repo.activeId.value)
        assertEquals(321, repo.profile(finland.id)!!.lastPingMs)
    }

    @Test
    fun `a stand-in selected before stand-ins were replaced hands the selection to its server`() {
        var singBox: String? = null
        val provider = provider { ua -> byUserAgent(xray = xrayFormat, singBox = singBox)(ua) }
        val repo = ConfigRepository(InMemoryKeyValueStore())
        val sub = repo.addSubscription("Provider", provider.url)

        runBlocking { repo.updateSubscription(sub.id) }.getOrThrow()
        repo.setActive(repo.servers(sub.id).single { it.name == "🇫🇮 Finland فقط سینگ‌باکس" }.id)

        singBox = fullSingBoxFormat
        runBlocking { repo.updateSubscription(sub.id) }.getOrThrow()

        assertEquals("🇫🇮 Finland", repo.activeProfile()?.name)
    }

    @Test
    fun `without a sing-box format the subscription imports exactly as the provider sent it`() {
        val provider = provider(byUserAgent(xray = xrayFormat, singBox = null))
        val repo = ConfigRepository(InMemoryKeyValueStore())
        val sub = repo.addSubscription("Provider", provider.url)

        assertEquals(5, runBlocking { repo.updateSubscription(sub.id) }.getOrThrow())
        assertEquals(3, repo.servers(sub.id).count { it.address == "127.0.0.1" })
    }

    @Test
    fun `a sing-box format that is really another format changes nothing`() {
        val provider = provider(byUserAgent(xray = xrayFormat, singBox = xrayFormat))
        val repo = ConfigRepository(InMemoryKeyValueStore())
        val sub = repo.addSubscription("Provider", provider.url)

        assertEquals(5, runBlocking { repo.updateSubscription(sub.id) }.getOrThrow())
        assertEquals(3, repo.servers(sub.id).count { it.address == "127.0.0.1" })
    }

    @Test
    fun `a subscription with no stand-ins never asks for the sing-box format`() {
        val xray = "[" + trojan("🇦🇹 Austria", 993) + "," + trojan("🇨🇭 Switzerland", 995) + "]"
        val provider = provider(byUserAgent(xray = xray, singBox = fullSingBoxFormat))
        val repo = ConfigRepository(InMemoryKeyValueStore())
        val sub = repo.addSubscription("Provider", provider.url)

        assertEquals(2, runBlocking { repo.updateSubscription(sub.id) }.getOrThrow())
        assertEquals(listOf(ConfigRepository.DEFAULT_UA), provider.userAgents.toList())
    }

    @Test
    fun `when the counts differ the real servers take the first stand-in's place`() {
        val extra = """{"type":"snell","tag":"🇳🇱 Amsterdam","server":"192.0.2.30","server_port":7777,"psk":"key","version":4}"""
        val provider = provider(byUserAgent(xray = xrayFormat, singBox = singBoxFormat(anytlsUkraine, snellFinland, anytlsBerlin, extra)))
        val repo = ConfigRepository(InMemoryKeyValueStore())
        val sub = repo.addSubscription("Provider", provider.url)

        assertEquals(6, runBlocking { repo.updateSubscription(sub.id) }.getOrThrow())
        assertEquals(
            listOf("🇺🇦 Ukraine", "🇫🇮 Finland", "🇩🇪 Berlin", "🇳🇱 Amsterdam", "🇦🇹 Austria ▰▰▰▰▰", "🇨🇭 Switzerland ▰▰▰▰▰"),
            repo.servers(sub.id).map { it.name },
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun `a subscription nothing else can read is taken from its sing-box format`() {
        val unreadable = Base64.encode("this subscription is only for sing-box".toByteArray())
        val provider = provider(byUserAgent(xray = unreadable, singBox = fullSingBoxFormat, other = unreadable))
        val repo = ConfigRepository(InMemoryKeyValueStore())
        val sub = repo.addSubscription("Provider", provider.url)

        assertEquals(5, runBlocking { repo.updateSubscription(sub.id) }.getOrThrow())
        assertTrue(repo.servers(sub.id).all { it.source is ProfileSource.SingBox })
        assertEquals(
            listOf(ConfigRepository.DEFAULT_UA, ConfigRepository.ALT_UA, ConfigRepository.SING_BOX_UA),
            provider.userAgents.toList(),
        )
    }
}
