package dev.cluvex.zedsecure.desktop.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopMeterTest {
    private class Sample(val seconds: Int, val downBps: Long, val totalDown: Long)

    @Test
    fun `the timer counts every second even when a read fails or runs late`() {
        var now = 1_000_000L
        val reads = ArrayDeque<Pair<Long, Long>?>(listOf(1_000L to 10L, null, 3_000L to 30L, null, null, 6_000L to 60L))
        val samples = mutableListOf<Sample>()
        val meter = DesktopMeter(
            totals = { reads.removeFirstOrNull() },
            clock = { now },
            report = { seconds, downBps, _, totalDown, _ -> samples += Sample(seconds, downBps, totalDown) },
        )
        meter.startAt(now)
        val jitter = listOf(40L, 350L, 0L, 120L, 300L, 10L)
        jitter.forEachIndexed { i, late ->
            now = 1_000_000L + (i + 1) * 1_000L + late
            meter.tick()
        }

        assertEquals(listOf(1, 2, 3, 4, 5, 6), samples.map { it.seconds })
        assertEquals(listOf(1_000L, 1_000L, 3_000L, 3_000L, 3_000L, 6_000L), samples.map { it.totalDown })
        assertEquals(0L, samples[1].downBps)
    }

    @Test
    fun `a meter without traffic still shows the time`() {
        var now = 0L
        val seen = mutableListOf<Int>()
        val meter = DesktopMeter(totals = { null }, clock = { now }, report = { s, _, _, _, _ -> seen += s })
        meter.startAt(0L)
        repeat(3) {
            now += 1_000L
            meter.tick()
        }
        assertEquals(listOf(1, 2, 3), seen)
    }
}
