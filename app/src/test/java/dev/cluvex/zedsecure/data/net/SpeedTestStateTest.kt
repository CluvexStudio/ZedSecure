package dev.cluvex.zedsecure.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedTestStateTest {
    @Test
    fun `running is true only for the three measuring phases`() {
        val measuring = listOf(SpeedTestPhase.Ping, SpeedTestPhase.Download, SpeedTestPhase.Upload)
        for (phase in SpeedTestPhase.entries) {
            assertEquals(
                "phase $phase",
                phase in measuring,
                SpeedTestState(phase = phase).running,
            )
        }
    }

    @Test
    fun `every phase is either measuring or terminal`() {
        val terminal = setOf(
            SpeedTestPhase.Idle,
            SpeedTestPhase.Done,
            SpeedTestPhase.Stopped,
            SpeedTestPhase.Error,
        )
        for (phase in SpeedTestPhase.entries) {
            val st = SpeedTestState(phase = phase)
            assertTrue("phase $phase is neither", st.running != (phase in terminal))
        }
    }

    @Test
    fun `stopping keeps partial results`() {
        val partial = SpeedTestState(
            phase = SpeedTestPhase.Upload,
            pingMs = 21.0,
            jitterMs = 3.0,
            downloadMbps = 48.5,
        )
        val stopped = partial.copy(phase = SpeedTestPhase.Stopped, liveMbps = 0.0, progress = 1f)

        assertFalse("a stopped run is not running", stopped.running)
        assertTrue("partial numbers survive the stop", stopped.hasResults)
        assertEquals(21.0, stopped.pingMs!!, 0.001)
        assertEquals(48.5, stopped.downloadMbps!!, 0.001)
        assertEquals("upload was never measured", null, stopped.uploadMbps)
    }

    @Test
    fun `hasResults is false only before anything is measured`() {
        assertFalse(SpeedTestState().hasResults)
        assertTrue(SpeedTestState(pingMs = 12.0).hasResults)
        assertTrue(SpeedTestState(downloadMbps = 1.0).hasResults)
        assertTrue(SpeedTestState(uploadMbps = 1.0).hasResults)
    }

    @Test
    fun `bufferbloat needs both latencies and never goes negative`() {
        assertEquals(null, SpeedTestState(pingMs = 20.0).bufferbloatMs)
        assertEquals(null, SpeedTestState(loadedPingMs = 90.0).bufferbloatMs)

        val added = SpeedTestState(pingMs = 20.0, loadedPingMs = 90.0).bufferbloatMs
        assertNotNull(added)
        assertEquals(70.0, added!!, 0.001)

        assertEquals(0.0, SpeedTestState(pingMs = 40.0, loadedPingMs = 30.0).bufferbloatMs!!, 0.001)
    }

    @Test
    fun `advertised worst-case data matches the budgets`() {
        assertEquals(SpeedTestEngine.TOTAL_BUDGET_BYTES / 1_000_000L, speedTestBudgetMb.toLong())
    }
}
