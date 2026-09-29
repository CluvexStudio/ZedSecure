package dev.cluvex.zedsecure.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSelectStatusTest {
    private val sample = """
        [{"tag":"proxy","selected":"proxy@1","state":"ok","switches":2,
          "events":[{"from":"","to":"proxy@0","reason":"startup","atMs":1000},
                    {"from":"proxy@0","to":"proxy@1","reason":"failover","atMs":2000}],
          "members":[{"tag":"proxy@0","state":"dead","delayMs":-1,"lastDelayMs":-1,"jitterMs":0,"samples":0,
                      "score":-1,"failRatio":0.5,"connections":0,"lastProbeAgoMs":1930,"lastSuccessAgoMs":-1,
                      "lastError":"dial tcp 1.2.3.4:443: i/o timeout"},
                     {"tag":"proxy@1","state":"alive","delayMs":84,"lastDelayMs":90,"jitterMs":6,"samples":4,
                      "score":87,"failRatio":0,"connections":3,"lastProbeAgoMs":500,"lastSuccessAgoMs":500}]}]
    """.trimIndent()

    @After
    fun reset() {
        AutoSelect.end()
        AutoSelect.clearPrepared()
        AutoSelect.readStatus = { null }
    }

    @Test
    fun `parses the core's status`() {
        val s = AutoSelect.parse(sample).single()
        assertEquals(AutoSelect.Phase.Ok, s.phase)
        assertEquals("proxy@1", s.selected)
        assertEquals(AutoSelect.Health.Dead, s.member("proxy@0")!!.health)
        assertEquals(84L, s.member("proxy@1")!!.delayMs)
        assertEquals(AutoSelect.Reason.Failover, s.events.last().kind)
    }

    @Test
    fun `garbage never throws`() {
        assertTrue(AutoSelect.parse("not json").isEmpty())
        assertTrue(AutoSelect.parse(null).isEmpty())
        assertTrue(AutoSelect.parse("[]").isEmpty())
    }

    @Test
    fun `a session only goes live once started, and polls report each switch once`() {
        AutoSelect.prepare("auto:all", mapOf("proxy@0" to "a", "proxy@1" to "b"))
        assertEquals(null, AutoSelect.session.value)
        assertTrue(AutoSelect.activate())

        var raw = sample.replace(
            """{"from":"proxy@0","to":"proxy@1","reason":"failover","atMs":2000}""",
            """{"from":"","to":"proxy@0","reason":"startup","atMs":1000}""",
        ).replace("\"selected\":\"proxy@1\"", "\"selected\":\"proxy@0\"")
        AutoSelect.readStatus = { raw }
        assertTrue("the first read establishes the baseline", AutoSelect.poll().isEmpty())
        assertEquals("a", AutoSelect.session.value!!.selectedProfileId)

        raw = sample
        val events = AutoSelect.poll()
        assertEquals(listOf(AutoSelect.Reason.Failover), events.map { it.kind })
        assertEquals("b", AutoSelect.session.value!!.selectedProfileId)
        assertTrue("nothing new", AutoSelect.poll().isEmpty())
    }

    @Test
    fun `a start that was planned but never happened does not leak into the next one`() {
        AutoSelect.prepare("auto:all", mapOf("proxy@0" to "a"))
        AutoSelect.clearPrepared()
        assertEquals(false, AutoSelect.activate())
    }
}
