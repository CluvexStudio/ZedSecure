package dev.cluvex.zedsecure.data.dns

import dev.cluvex.zedsecure.platform.DnsParser
import dev.cluvex.zedsecure.platform.DnsRecordType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsParserTest {
    private fun msg(
        id: Int = 0x1234,
        flags: Int = 0x8180,
        question: ByteArray = ByteArray(0),
        qdCount: Int = 0,
        answers: ByteArray = ByteArray(0),
        anCount: Int = 0,
    ): ByteArray {
        val h = ByteArray(12)
        h[0] = (id ushr 8).toByte(); h[1] = id.toByte()
        h[2] = (flags ushr 8).toByte(); h[3] = flags.toByte()
        h[4] = (qdCount ushr 8).toByte(); h[5] = qdCount.toByte()
        h[6] = (anCount ushr 8).toByte(); h[7] = anCount.toByte()
        return h + question + answers
    }

    private fun name(vararg labels: String): ByteArray {
        val out = ArrayList<Byte>()
        labels.forEach { l -> out += l.length.toByte(); l.forEach { out += it.code.toByte() } }
        out += 0
        return out.toByteArray()
    }

    private fun be16(v: Int) = byteArrayOf((v ushr 8).toByte(), v.toByte())

    @Test
    fun `a question is decoded`() {
        val q = name("a", "example", "com") + be16(DnsRecordType.TXT.value) + be16(1)
        val m = DnsParser.parse(msg(question = q, qdCount = 1))
        assertNotNull(m)
        assertEquals("a.example.com", m!!.questions.single().name)
        assertEquals(DnsRecordType.TXT.value, m.questions.single().type)
        assertTrue(m.isResponse)
        assertEquals(0, m.rcode)
    }

    @Test
    fun `a TXT answer's strings are concatenated`() {
        val q = name("t", "example", "com") + be16(16) + be16(1)

        val rdata = byteArrayOf(2, 'a'.code.toByte(), 'b'.code.toByte(),
            3, 'c'.code.toByte(), 'd'.code.toByte(), 'e'.code.toByte())
        val an = name("t", "example", "com") + be16(16) + be16(1) +
            byteArrayOf(0, 0, 0, 60) + be16(rdata.size) + rdata
        val m = DnsParser.parse(msg(question = q, qdCount = 1, answers = an, anCount = 1))!!
        assertEquals("abcde", String(m.firstTxt()!!))
    }

    @Test
    fun `an A record becomes a dotted address`() {
        val q = name("h", "example", "com") + be16(1) + be16(1)
        val an = name("h", "example", "com") + be16(1) + be16(1) +
            byteArrayOf(0, 0, 0, 60) + be16(4) + byteArrayOf(93.toByte(), 184.toByte(), 216.toByte(), 34)
        val m = DnsParser.parse(msg(question = q, qdCount = 1, answers = an, anCount = 1))!!
        assertEquals("93.184.216.34", m.firstAddress())
    }

    @Test
    fun `a compressed NS target is decompressed against the whole message`() {
        val q = name("t", "example", "com") + be16(2) + be16(1)
        val rdata = name("ns1") .dropLast(1).toByteArray() + byteArrayOf(0xC0.toByte(), 14)
        val an = byteArrayOf(0xC0.toByte(), 12) + be16(2) + be16(1) +
            byteArrayOf(0, 0, 0, 60) + be16(rdata.size) + rdata
        val m = DnsParser.parse(msg(question = q, qdCount = 1, answers = an, anCount = 1))!!

        assertEquals(listOf("ns1.example.com"), m.nsNames())

        assertEquals("t.example.com", m.answers.single().name)
    }

    @Test
    fun `a pointer loop is refused instead of hanging`() {
        val bad = msg(qdCount = 1) + byteArrayOf(0xC0.toByte(), 12) + be16(1) + be16(1)
        assertNull(DnsParser.parse(bad))
    }

    @Test
    fun `a forward pointer is refused`() {
        val bad = msg(qdCount = 1) + byteArrayOf(0xC0.toByte(), 20) + be16(1) + be16(1) +
            ByteArray(8)
        assertNull(DnsParser.parse(bad))
    }

    @Test
    fun `rdata running past the end is refused`() {
        val q = name("a", "b") + be16(1) + be16(1)

        val an = name("a", "b") + be16(1) + be16(1) + byteArrayOf(0, 0, 0, 60) + be16(40)
        assertNull(DnsParser.parse(msg(question = q, qdCount = 1, answers = an, anCount = 1)))
    }

    @Test
    fun `an absurd record count is refused before allocating`() {
        val h = msg(anCount = 60000)
        assertNull(DnsParser.parse(h))
    }

    @Test
    fun `a truncated header is refused`() {
        assertNull(DnsParser.parse(ByteArray(11)))
    }
}
