package dev.cluvex.zedsecure.data.config

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress

class PasteSubscriptionTest {
    private lateinit var server: HttpServer
    private val unreachable = "http://127.0.0.1:1/sub"

    private val link =
        "vless://11111111-2222-3333-4444-555555555555@a.example.com:443?encryption=none&type=ws#A"

    private fun url(path: String) = "http://127.0.0.1:${server.address.port}$path"

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/sub") { ex ->
            val body = link.toByteArray()
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        server.createContext("/page") { ex ->
            val body = "<html>not a subscription</html>".toByteArray()
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        server.start()
    }

    @After
    fun stop() = server.stop(0)

    @Test
    fun `a pasted subscription link is fetched and added`() = runBlocking {
        val repo = ConfigRepository(InMemoryKeyValueStore())
        val r = repo.importPasted(url("/sub")).getOrThrow()

        assertTrue(r.subscription)
        assertEquals(1, r.count)
        assertEquals(listOf(url("/sub")), repo.subscriptions.value.map { it.url })
    }

    @Test
    fun `invisible marks copied around the link are dropped`() = runBlocking {
        val repo = ConfigRepository(InMemoryKeyValueStore())
        val r = repo.importPasted("‎${url("/sub")}‏​\n").getOrThrow()

        assertEquals(1, r.count)
        assertEquals(listOf(url("/sub")), repo.subscriptions.value.map { it.url })
    }

    @Test
    fun `a message with one link in it imports that link as a subscription`() = runBlocking {
        val repo = ConfigRepository(InMemoryKeyValueStore())
        val r = repo.importPasted("Subscription link:\n${url("/sub")}.").getOrThrow()

        assertTrue(r.subscription)
        assertEquals(1, r.count)
        assertEquals(listOf(url("/sub")), repo.subscriptions.value.map { it.url })
    }

    @Test
    fun `an HTTP proxy link is still imported as a server`() = runBlocking {
        val repo = ConfigRepository(InMemoryKeyValueStore())
        val r = repo.importPasted("Proxy:\nhttp://user:pass@203.0.113.7:8080#Office").getOrThrow()

        assertFalse(r.subscription)
        assertEquals(1, r.count)
        assertEquals("Office", repo.profiles.value.single().name)
    }

    @Test
    fun `configs in the message win over a link next to them`() = runBlocking {
        val repo = ConfigRepository(InMemoryKeyValueStore())
        val r = repo.importPasted("$link\nchannel: https://t.me/example").getOrThrow()

        assertFalse(r.subscription)
        assertEquals(1, r.count)
        assertTrue(repo.subscriptions.value.isEmpty())
    }

    @Test
    fun `a link that cannot be downloaded is kept to update later`() = runBlocking {
        val repo = ConfigRepository(InMemoryKeyValueStore())
        val e = repo.importPasted(unreachable).exceptionOrNull()

        assertTrue(e is ConfigRepository.SubscriptionNotFetchedException)
        assertTrue((e as ConfigRepository.SubscriptionNotFetchedException).savedForLater)
        assertEquals(listOf(unreachable), repo.subscriptions.value.map { it.url })
    }

    @Test
    fun `a link that downloads but holds no configs is not kept`() = runBlocking {
        val repo = ConfigRepository(InMemoryKeyValueStore())
        val e = repo.importPasted(url("/page")).exceptionOrNull()

        assertTrue(e is ConfigRepository.SubscriptionNotFetchedException)
        assertFalse((e as ConfigRepository.SubscriptionNotFetchedException).savedForLater)
        assertTrue(repo.subscriptions.value.isEmpty())
    }

    @Test
    fun `an unreachable link found inside other text is not kept`() = runBlocking {
        val repo = ConfigRepository(InMemoryKeyValueStore())
        val e = repo.importPasted("see $unreachable for servers").exceptionOrNull()

        assertTrue(e is ConfigRepository.SubscriptionNotFetchedException)
        assertTrue(repo.subscriptions.value.isEmpty())
    }
}
