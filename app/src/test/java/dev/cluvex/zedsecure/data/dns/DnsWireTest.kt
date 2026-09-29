package dev.cluvex.zedsecure.data.dns

import dev.cluvex.zedsecure.platform.DnsFailure
import dev.cluvex.zedsecure.platform.DnsRecordType
import dev.cluvex.zedsecure.platform.DnsResult
import dev.cluvex.zedsecure.platform.DnsWire
import dev.cluvex.zedsecure.platform.MAX_DNS_LABEL
import dev.cluvex.zedsecure.platform.MAX_DNS_NAME
import dev.cluvex.zedsecure.platform.paddedQueryName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DnsWireTest {
    private fun be16(b: ByteArray, at: Int) =
        ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)

    @Test
    fun `a query has the shape RFC 1035 requires`() {
        val q = DnsWire.buildQuery(0x1234, "a.example.com", DnsRecordType.TXT, 0)
        assertEquals("transaction id", 0x1234, be16(q, 0))
        assertEquals("flags: RD only", 0x0100, be16(q, 2))
        assertEquals("one question", 1, be16(q, 4))
        assertEquals("no answers in a query", 0, be16(q, 6))
        assertEquals("no additional without EDNS", 0, be16(q, 10))

        assertEquals("QTYPE=TXT", DnsRecordType.TXT.value, be16(q, q.size - 4))
        assertEquals("QCLASS=IN", 1, be16(q, q.size - 2))
    }

    @Test
    fun `labels are length-prefixed and root-terminated`() {
        val q = DnsWire.buildQuery(1, "ab.cde", DnsRecordType.A, 0)
        val name = q.copyOfRange(DnsWire.HEADER_BYTES, q.size - 4)
        assertEquals("first label length", 2, name[0].toInt())
        assertEquals('a'.code.toByte(), name[1])
        assertEquals('b'.code.toByte(), name[2])
        assertEquals("second label length", 3, name[3].toInt())
        assertEquals("root terminator", 0, name[name.size - 1].toInt())
    }

    @Test
    fun `an EDNS query carries one OPT record advertising the buffer`() {
        val q = DnsWire.buildQuery(1, "example.com", DnsRecordType.NS, 1232)
        assertEquals("ARCOUNT", 1, be16(q, 10))
        val opt = q.copyOfRange(q.size - 11, q.size)
        assertEquals("OPT root name", 0, opt[0].toInt())
        assertEquals("TYPE=OPT(41)", 41, be16(opt, 1))
        assertEquals("CLASS carries the advertised size", 1232, be16(opt, 3))
        assertEquals("RDLENGTH=0", 0, be16(opt, 9))
    }

    @Test
    fun `the root name encodes to a single zero byte`() {
        assertEquals(1, DnsWire.encodeName(".").size)
        assertEquals(0, DnsWire.encodeName(".")[0].toInt())
        assertEquals(0, DnsWire.encodeName("")[0].toInt())
        val q = DnsWire.buildQuery(7, ".", DnsRecordType.NS, 4096)
        assertEquals("QTYPE=NS", DnsRecordType.NS.value, be16(q, DnsWire.HEADER_BYTES + 1))
    }

    @Test
    fun `names that violate the RFC are rejected rather than truncated`() {
        val tooLong = "x".repeat(MAX_DNS_LABEL + 1) + ".example.com"
        assertTrue(runCatching { DnsWire.encodeName(tooLong) }.isFailure)

        val huge = (1..5).joinToString(".") { "y".repeat(MAX_DNS_LABEL) }
        assertTrue(runCatching { DnsWire.encodeName(huge) }.isFailure)
    }

    private fun reply(
        id: Int = 0x4242,
        qr: Boolean = true,
        rcode: Int = 0,
        truncated: Boolean = false,
        answers: Int = 1,
    ): ByteArray {
        val question = encodeName("a.example.com") + be16Bytes(DnsRecordType.A.value) + be16Bytes(1)
        val record = encodeName("a.example.com") + be16Bytes(DnsRecordType.A.value) +
            be16Bytes(1) + byteArrayOf(0, 0, 0, 60) + be16Bytes(4) + byteArrayOf(1, 2, 3, 4)
        val header = ByteArray(DnsWire.HEADER_BYTES)
        header[0] = (id ushr 8).toByte(); header[1] = id.toByte()
        var high = 0
        if (qr) high = high or 0x80
        if (truncated) high = high or 0x02
        header[2] = high.toByte()
        header[3] = rcode.toByte()
        header[5] = 1
        header[7] = answers.toByte()
        return header + question + (if (answers > 0) record else ByteArray(0))
    }

    private fun encodeName(name: String): ByteArray {
        val out = ArrayList<Byte>()
        name.split('.').forEach { l -> out += l.length.toByte(); l.forEach { out += it.code.toByte() } }
        out += 0
        return out.toByteArray()
    }

    private fun be16Bytes(v: Int) = byteArrayOf((v ushr 8).toByte(), v.toByte())

    @Test
    fun `a valid reply parses`() {
        val r = reply().let { DnsWire.parseReply(it, it.size, 0x4242, 12) }
        assertTrue(r is DnsResult.Ok)
        assertEquals(12, (r as DnsResult.Ok).reply.elapsedMs)
        assertEquals(1, r.reply.answerCount)
    }

    @Test
    fun `a reply carrying someone else's transaction id is refused`() {
        val r = reply(id = 0x1111).let { DnsWire.parseReply(it, it.size, 0x4242, 1) }
        assertEquals(DnsFailure.Mismatch, (r as DnsResult.Failed).reason)
    }

    @Test
    fun `a query reflected back is not mistaken for an answer`() {
        val r = reply(qr = false).let { DnsWire.parseReply(it, it.size, 0x4242, 1) }
        assertEquals(DnsFailure.Mismatch, (r as DnsResult.Failed).reason)
    }

    @Test
    fun `a refusal is reported as a refusal, not as silence`() {
        val r = reply(rcode = 3, answers = 0).let { DnsWire.parseReply(it, it.size, 0x4242, 1) }
        assertEquals(DnsFailure.Refused, (r as DnsResult.Failed).reason)
    }

    @Test
    fun `a runt packet is malformed`() {
        val r = DnsWire.parseReply(ByteArray(4), 4, 0x4242, 1)
        assertEquals(DnsFailure.Malformed, (r as DnsResult.Failed).reason)
    }

    @Test
    fun `truncation is surfaced, because it is the answer to the payload question`() {
        val r = reply(truncated = true).let { DnsWire.parseReply(it, it.size, 0x4242, 1) }
        assertTrue((r as DnsResult.Ok).reply.truncated)
    }

    @Test
    fun `a padded name hits its target length and stays encodable`() {
        val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
        listOf(64, 100, 140, 180, 220, MAX_DNS_NAME).forEach { target ->
            val name = paddedQueryName("t.example.com", target) { alphabet[Random.nextInt(alphabet.length)] }
            assertTrue("$target produced ${name.length}", name.length <= MAX_DNS_NAME)

            assertTrue("$target produced ${name.length}", name.length >= target - MAX_DNS_LABEL)
            assertTrue("must keep the tunnel domain", name.endsWith("t.example.com"))

            assertTrue(runCatching { DnsWire.encodeName(name) }.isSuccess)
            name.split('.').forEach { assertTrue("label ${it.length}", it.length <= MAX_DNS_LABEL) }
        }
    }

    @Test
    fun `padding is random, so samples are never answered from cache`() {
        val alphabet = "abcdefghijklmnopqrstuvwxyz"
        val a = paddedQueryName("t.example.com", 120) { alphabet[Random.nextInt(alphabet.length)] }
        val b = paddedQueryName("t.example.com", 120) { alphabet[Random.nextInt(alphabet.length)] }

        assertNotEquals(a, b)
    }

    @Test
    fun `a target shorter than the domain degrades instead of throwing`() {
        val name = paddedQueryName("t.example.com", 4) { 'a' }
        assertTrue(runCatching { DnsWire.encodeName(name) }.isSuccess)
    }
}
