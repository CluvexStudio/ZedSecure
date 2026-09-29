package dev.cluvex.zedsecure.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BidiTextTest {
    private val lri = '⁦'
    private val pdi = '⁩'

    @Test
    fun `ltr wraps in an isolate, not an embedding`() {
        val out = BidiText.ltr("1.2 MB/s")
        assertEquals("${lri}1.2 MB/s${pdi}", out)

        assertTrue("must not use LRE", '‪' !in out)
        assertTrue("must not use PDF", '‬' !in out)
    }

    @Test
    fun `the arrow travels inside the isolate with its value`() {
        val out = BidiText.ltr("↓ 1.2MB/s")
        assertEquals(lri, out.first())
        assertEquals(pdi, out.last())
        assertEquals('↓', out[1])
    }

    @Test
    fun `empty stays empty so no stray control characters are emitted`() {
        assertEquals("", BidiText.ltr(""))
    }

    @Test
    fun `isolates nest without interfering`() {
        val inner = BidiText.ltr("1.24 GB")
        val sentence = "این نشست: $inner دریافت"
        assertEquals(1, sentence.count { it == lri })
        assertEquals(1, sentence.count { it == pdi })
        assertTrue(sentence.indexOf(lri) < sentence.indexOf(pdi))
    }
}
