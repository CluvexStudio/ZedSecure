package dev.cluvex.zedsecure.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedTestMathTest {
    @Test
    fun `percentile interpolates like cloudflare rather than truncating`() {
        val vals = (1..10).map { it.toDouble() }
        assertEquals(9.1, SpeedTestMath.percentile(vals, 0.9), 1e-9)
        assertTrue(
            "p90 must not return the maximum sample",
            SpeedTestMath.percentile(vals, 0.9) < vals.max(),
        )
    }

    @Test
    fun `percentile handles exact indices, single values and empty input`() {
        assertEquals(5.5, SpeedTestMath.percentile((1..10).map { it.toDouble() }, 0.5), 1e-9)
        assertEquals(1.0, SpeedTestMath.percentile(listOf(1.0, 2.0, 3.0), 0.0), 1e-9)
        assertEquals(3.0, SpeedTestMath.percentile(listOf(1.0, 2.0, 3.0), 1.0), 1e-9)
        assertEquals(7.0, SpeedTestMath.percentile(listOf(7.0), 0.9), 1e-9)
        assertEquals(0.0, SpeedTestMath.percentile(emptyList(), 0.9), 1e-9)
    }

    @Test
    fun `percentile does not care about input order`() {
        val ordered = listOf(1.0, 2.0, 3.0, 4.0, 5.0)
        assertEquals(
            SpeedTestMath.percentile(ordered, 0.9),
            SpeedTestMath.percentile(ordered.reversed(), 0.9),
            1e-9,
        )
    }

    @Test
    fun `jitter is the mean absolute difference between consecutive samples`() {
        assertEquals(10.0, SpeedTestMath.jitter(listOf(100.0, 110.0, 100.0, 110.0))!!, 1e-9)

        assertEquals(0.0, SpeedTestMath.jitter(listOf(50.0, 50.0, 50.0))!!, 1e-9)
    }

    @Test
    fun `jitter is null below two samples rather than a misleading zero`() {
        assertNull(SpeedTestMath.jitter(emptyList()))
        assertNull(SpeedTestMath.jitter(listOf(42.0)))
        assertNotNull(SpeedTestMath.jitter(listOf(42.0, 43.0)))
    }

    @Test
    fun `jitter cannot exceed the spread of its own samples`() {
        val samples = listOf(200.0, 260.0, 210.0, 250.0, 243.0)
        val jitter = SpeedTestMath.jitter(samples)!!
        assertTrue(
            "jitter $jitter must be within the sample spread",
            jitter <= samples.max() - samples.min(),
        )
    }

    private fun sample(durationMs: Double, bps: Double) =
        BandwidthSample(bytes = 1_000_000, durationMs = durationMs, bps = bps, ping = 10.0)

    @Test
    fun `bandwidth excludes samples shorter than the minimum request duration`() {
        val samples = listOf(
            sample(5.0, 900_000_000.0),
            sample(500.0, 10_000_000.0),
            sample(600.0, 11_000_000.0),
        )
        val bps = SpeedTestMath.bandwidthBps(
            samples,
            perc = SpeedTestEngine.BANDWIDTH_PERCENTILE,
            minDurationMs = SpeedTestEngine.MIN_REQUEST_MS,
        )!!
        assertTrue("noise sample leaked into the aggregate: $bps", bps < 12_000_000.0)
    }

    @Test
    fun `bandwidth is null when nothing usable was measured`() {
        assertNull(
            SpeedTestMath.bandwidthBps(emptyList(), 0.9, SpeedTestEngine.MIN_REQUEST_MS),
        )

        assertNull(
            SpeedTestMath.bandwidthBps(
                listOf(sample(1.0, 5.0), sample(2.0, 6.0)),
                0.9,
                SpeedTestEngine.MIN_REQUEST_MS,
            ),
        )

        assertNull(
            SpeedTestMath.bandwidthBps(listOf(sample(500.0, 0.0)), 0.9, SpeedTestEngine.MIN_REQUEST_MS),
        )
    }
}
