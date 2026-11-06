package dev.cluvex.zedsecure.desktop

import dev.cluvex.zedsecure.core.AetherProcess
import dev.cluvex.zedsecure.core.LocalPortPicker
import dev.cluvex.zedsecure.data.config.ConfigRepository
import dev.cluvex.zedsecure.domain.config.*
import dev.cluvex.zedsecure.platform.KeyValueStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.*

class AetherIntegrationTest {
    @Test
    fun profilesPersistEditAndShareWithoutIdentityCredentials() {
        val store = MemoryStore()
        val repository = ConfigRepository(store)
        val settings = AetherProfile(protocol = "gool", http2 = true, fragment = true, peer = "162.159.192.1:443")
        val first = repository.addAether(settings, "وارپ من")
        val edited = repository.addAether(settings.copy(scan = "verified"), "Aether H2", first.id)
        assertEquals(first.id, edited.id)
        assertEquals(first.addedAt, edited.addedAt)
        assertEquals(1, repository.profiles.value.size)
        val reloaded = ConfigRepository(store).profiles.value.single()
        assertEquals(edited, reloaded)
        assertEquals("Aether", reloaded.engineLabel)
        assertTrue(reloaded.isManagedTunnel)
        assertFalse(reloaded.canDialThroughProxy)
        assertFalse(reloaded.canCarryChain)
        val shared = assertNotNull(reloaded.rawPayload())
        val payload = assertNotNull(ZedLink.parse(shared))
        assertEquals(ProfileSource.Aether(settings.copy(scan = "verified")), payload.source)
        val other = ConfigRepository(MemoryStore())
        assertEquals(1, other.importText(shared).getOrThrow())
        assertEquals(reloaded.aetherSettings(), other.profiles.value.single().aetherSettings())
        assertFailsWith<IllegalStateException> { reloaded.toXrayConfigJson() }
    }

    @Test
    fun everyModeProducesANoninteractiveLoopbackCommand() {
        for (protocol in AetherProfile.PROTOCOLS) {
            val settings = AetherProfile(protocol = protocol)
            val args = settings.arguments("/data/path with spaces/identity.toml", 10832)
            assertEquals("--$protocol", args.first())
            assertEquals("127.0.0.1:10832", args[args.indexOf("--bind") + 1])
            assertEquals("/data/path with spaces/identity.toml", args[args.indexOf("--config") + 1])
            assertTrue("--quick-reconnect" in args)
            assertEquals(settings.usesMasque, "--h3" in args)
            assertEquals(settings.usesMasque, "--tls-verify" in args)
            assertFalse("--http-proxy" in args)
        }
    }

    @Test
    fun validatesSharedSettingsAndEndpointsBeforeLaunching() {
        listOf("162.159.192.1:443", "[2606:4700::1]:443", "[::1]:2408").forEach {
            AetherProfile(peer = it).validate()
        }
        listOf("host.example:443", "1.2.3.999:443", "1.2.3.4:0", "1.2.3.4:65536", "[:::]:443", "[1:2:3]:443", "1.2.3.4:443 --tor-only").forEach {
            assertFailsWith<IllegalArgumentException> { AetherProfile(peer = it).validate() }
        }
        assertFailsWith<IllegalArgumentException> { AetherProfile(protocol = "masque --tor-only").validate() }
        assertFailsWith<IllegalArgumentException> { AetherProfile(scan = "unknown").validate() }
        assertFailsWith<IllegalArgumentException> { AetherProfile(protocol = "wg", http2 = true).validate() }
    }

    @Test
    fun http2AndEchAreExplicitAndNoiseNeverBecomesAShellCommand() {
        val args = AetherProfile(http2 = true, ech = true, fragment = true, noize = "gfw").arguments("identity.toml", 9000)
        assertTrue("--h2" in args)
        assertFalse("--h3" in args)
        assertEquals("auto", args[args.indexOf("--ech") + 1])
        assertTrue("--fragment" in args)
        assertFailsWith<IllegalArgumentException> { AetherProfile(noize = "gfw; exit").arguments("id", 9000) }
    }

    @Test
    fun occupiedPortCannotProduceFalseSuccess() = withRunner { binary, data ->
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { occupied ->
            var launched = false
            val runner = AetherProcess(binary, data, AetherProfile(), occupied.localPort, launch = {
                launched = true
                error("must not launch")
            })
            assertTrue(runner.start(500).isFailure)
            assertFalse(launched)
        }
    }

    @Test
    fun reservedTcpPortIsNotReadyUntilSocksAnswers() = withRunner { binary, data ->
        val port = LocalPortPicker.pick(19000)
        val process = FakeProcess(port)
        val runner = AetherProcess(binary, data, AetherProfile(), port, launch = {
            process.bind()
            process
        })
        val result = CompletableFuture.supplyAsync { runner.start(5000) }
        assertTrue(process.bound.await(2, TimeUnit.SECONDS))
        Thread.sleep(550)
        assertFalse(result.isDone, "a TCP listener without a SOCKS greeting must remain Connecting")
        process.serve.set(true)
        assertTrue(result.get(5, TimeUnit.SECONDS).isSuccess)
        assertTrue(runner.isRunning)
        runner.stop()
        runner.stop()
        assertFalse(runner.isRunning)
        assertFalse(process.isAlive())
    }

    @Test
    fun stoppingWhileConnectingCannotResurrectTheProcess() = withRunner { binary, data ->
        val port = LocalPortPicker.pick(19000)
        val process = FakeProcess(port)
        val runner = AetherProcess(binary, data, AetherProfile(), port, launch = { process.bind(); process })
        val result = CompletableFuture.supplyAsync { runner.start(5000) }
        assertTrue(process.bound.await(2, TimeUnit.SECONDS))
        runner.stop()
        assertTrue(result.get(3, TimeUnit.SECONDS).isFailure)
        assertFalse(process.isAlive())
        assertTrue(runner.start(100).isFailure)
    }

    @Test
    fun startupTimeoutReapsTheChild() = withRunner { binary, data ->
        val port = LocalPortPicker.pick(19000)
        val process = FakeProcess(port)
        val runner = AetherProcess(binary, data, AetherProfile(), port, launch = { process.bind(); process })
        assertTrue(runner.start(150).isFailure)
        assertFalse(process.isAlive())
    }

    @Test
    fun stoppedSessionCannotLaunch() = withRunner { binary, data ->
        val runner = AetherProcess(binary, data, AetherProfile(), LocalPortPicker.pick(19000), launch = { error("must not launch") })
        runner.stop()
        assertTrue(runner.start().isFailure)
    }

    private fun withRunner(test: (File, File) -> Unit) {
        val root = Files.createTempDirectory("aether-test").toFile()
        try {
            val binary = File(root, "fake-core").apply { writeText("stub"); setExecutable(true) }
            test(binary, File(root, "identity"))
        } finally { root.deleteRecursively() }
    }

    private class FakeProcess(private val port: Int) : Process() {
        val bound = CountDownLatch(1)
        val serve = AtomicBoolean(false)
        private val alive = AtomicBoolean(true)
        private val ended = CountDownLatch(1)
        private var listener: ServerSocket? = null
        fun bind() {
            listener = ServerSocket(port, 5, InetAddress.getByName("127.0.0.1")).also { it.soTimeout = 100 }
            bound.countDown()
            Thread({
                while (alive.get()) {
                    if (!serve.get()) { Thread.sleep(20); continue }
                    runCatching {
                        listener!!.accept().use { client ->
                            client.soTimeout = 100
                            val input = client.getInputStream()
                            if (input.read() == 5 && input.read() == 1 && input.read() == 0) {
                                client.getOutputStream().apply { write(byteArrayOf(5, 0)); flush() }
                            }
                        }
                    }
                }
            }, "fake-aether").apply { isDaemon = true; start() }
        }
        override fun getInputStream() = ByteArrayInputStream(byteArrayOf())
        override fun getErrorStream() = ByteArrayInputStream(byteArrayOf())
        override fun getOutputStream() = ByteArrayOutputStream()
        override fun waitFor(): Int { ended.await(); return 0 }
        override fun waitFor(timeout: Long, unit: TimeUnit) = ended.await(timeout, unit)
        override fun exitValue(): Int { check(!isAlive()); return 0 }
        override fun isAlive() = alive.get()
        override fun destroy() { alive.set(false); listener?.close(); ended.countDown() }
        override fun destroyForcibly(): Process { destroy(); return this }
    }

    private class MemoryStore : KeyValueStore {
        private val values = mutableMapOf<String, String>()
        override fun getString(key: String) = values[key]
        override fun putString(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
        override fun getInt(key: String, default: Int) = values[key]?.toIntOrNull() ?: default
        override fun putInt(key: String, value: Int) { values[key] = value.toString() }
        override fun getBoolean(key: String, default: Boolean) = values[key]?.toBooleanStrictOrNull() ?: default
        override fun putBoolean(key: String, value: Boolean) { values[key] = value.toString() }
        override fun remove(key: String) { values.remove(key) }
    }
}
