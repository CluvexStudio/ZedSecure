package dev.cluvex.zedsecure.data.net

import dev.cluvex.zedsecure.core.AutoSelect
import dev.cluvex.zedsecure.domain.config.AutoSelectTags
import dev.cluvex.zedsecure.ui.home.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class ExitInfoAutoSelectTest {
    private val base = nextBase.getAndAdd(1_000)

    private companion object {
        val nextBase = AtomicInteger(1_000)
    }

    private fun status(switches: Int, selected: String, delayMs: Long = 120) =
        """[{"tag":"${AutoSelectTags.GROUP}","selected":"$selected","state":"ok","switches":$switches,
            "events":[],"members":[{"tag":"$selected","state":"alive","delayMs":$delayMs}]}]"""

    private fun coreReports(switches: Int, selected: String, delayMs: Long = 120) {
        AutoSelect.readStatus = { status(switches, selected, delayMs) }
        AutoSelect.poll()
    }

    @Before
    fun startSession() {
        AutoSelect.prepare("auto:all", mapOf("proxy@0" to "a", "proxy@1" to "b"))
        AutoSelect.activate()
    }

    @After
    fun endSession() {
        AutoSelect.end()
        AutoSelect.readStatus = { null }
    }

    private fun payload(country: String) =
        """{"ip":"203.0.113.9","country_code":"$country","country":"Land $country","city":"City","isp":"ISP"}"""

    private fun repository(calls: AtomicInteger, country: () -> String, during: () -> Unit = {}) =
        NetworkInfoRepository { url ->
            if (url != NetworkInfoRepository.IPWHOIS_URL) throw IOException("unreachable")
            calls.incrementAndGet()
            during()
            payload(country())
        }

    @Test
    fun `the group's first pick is not a move, every later selection change is`() {
        assertEquals(0, AutoSelect.Session("p", emptyMap()).exitGeneration)
        fun generation(switches: Int) =
            AutoSelect.Session("p", emptyMap(), AutoSelect.parse(status(switches, "proxy@0")).single()).exitGeneration
        assertEquals(0, generation(0))
        assertEquals(0, generation(1))
        assertEquals(3, generation(4))
    }

    @Test
    fun `a move to another server is looked up again, a poll that moved nothing is not`() = runBlocking<Unit> {
        val calls = AtomicInteger()
        var country = "DE"
        val repo = repository(calls, { country })
        coreReports(switches = base, selected = "proxy@0")

        assertEquals("DE", repo.fetchInfo().countryCode)
        assertEquals("DE", repo.fetchInfo().countryCode)
        coreReports(switches = base, selected = "proxy@0", delayMs = 340)
        assertEquals("DE", repo.fetchInfo().countryCode)
        assertEquals("one lookup while the exit stayed put", 1, calls.get())

        country = "NL"
        coreReports(switches = base + 1, selected = "proxy@1")
        assertEquals("NL", repo.fetchInfo().countryCode)
        assertEquals(2, calls.get())
    }

    @Test
    fun `an answer that lands after a move is not kept for the new server`() = runBlocking<Unit> {
        val calls = AtomicInteger()
        var moveDuringLookup = true
        val repo = repository(calls, { "DE" }) {
            if (moveDuringLookup) {
                moveDuringLookup = false
                coreReports(switches = base + 1, selected = "proxy@1")
            }
        }
        coreReports(switches = base, selected = "proxy@0")

        repo.fetchInfo()
        repo.fetchInfo()
        assertEquals("the straddling answer must not have been cached", 2, calls.get())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `Home asks again after a move and ends on the new server's location`() = runBlocking<Unit> {
        Dispatchers.setMain(Dispatchers.Unconfined)
        try {
            coreReports(switches = base, selected = "proxy@0")
            var country = "DE"
            val vm = HomeViewModel(repository(AtomicInteger(), { country }))

            vm.refresh()
            withTimeout(10_000) { vm.info.first { !it.loading && it.countryCode == "DE" } }

            country = "NL"
            coreReports(switches = base + 1, selected = "proxy@1")
            vm.refresh()
            val meanwhile = vm.info.value
            assertTrue("what was known stays on screen while the new answer is on its way",
                meanwhile.countryCode == "DE" || meanwhile.countryCode == "NL")
            withTimeout(10_000) { vm.info.first { !it.loading && it.countryCode == "NL" } }
        } finally {
            Dispatchers.resetMain()
        }
    }
}
