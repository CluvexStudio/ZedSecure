package dev.cluvex.zedsecure.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedTestScheduleTest {
    private val schedule = SpeedTestEngine.SCHEDULE

    @Test
    fun `no round exceeds the public download endpoint's 100 MB cap`() {
        val cap = 100_000_000L
        schedule.filter { !it.upload }.forEach {
            assertTrue("download round of ${it.bytes} B exceeds the endpoint cap", it.bytes <= cap)
        }
    }

    @Test
    fun `the ramp climbs and alternates direction like cloudflare's`() {
        val downSizes = schedule.filter { !it.upload }.map { it.bytes }
        val upSizes = schedule.filter { it.upload }.map { it.bytes }
        assertEquals(downSizes.sorted(), downSizes)
        assertEquals(upSizes.sorted(), upSizes)
        assertTrue("both directions must be measured", downSizes.isNotEmpty() && upSizes.isNotEmpty())
    }

    @Test
    fun `the first round bypasses the finish rule so one slow request cannot end the test`() {
        val first = schedule.first()
        assertTrue("the initial estimate must bypass the finish rule", first.bypassFinishRule)
        assertTrue(
            "only the initial estimate should bypass it",
            schedule.drop(1).none { it.bypassFinishRule },
        )
    }

    @Test
    fun `the advertised data ceiling is the budget, and the schedule can actually reach it`() {
        val budget = SpeedTestEngine.TOTAL_BUDGET_BYTES
        assertEquals("the UI figure must be the budget", (budget / 1_000_000).toInt(), speedTestBudgetMb)

        val scheduleBytes = schedule.sumOf { it.bytes * it.count }
        assertTrue(
            "budget ($budget) should bind before the schedule ($scheduleBytes)",
            scheduleBytes > budget,
        )
    }

    @Test
    fun `cloudflare's aggregation constants are the ones we use`() {
        assertEquals(0.9, SpeedTestEngine.BANDWIDTH_PERCENTILE, 1e-9)
        assertEquals(0.5, SpeedTestEngine.LATENCY_PERCENTILE, 1e-9)
        assertEquals(10.0, SpeedTestEngine.MIN_REQUEST_MS, 1e-9)
        assertEquals(1_000.0, SpeedTestEngine.FINISH_REQUEST_MS, 1e-9)
        assertEquals(400L, SpeedTestEngine.LOADED_PROBE_MS)
        assertEquals(20, SpeedTestEngine.LOADED_MAX_POINTS)
    }
}
