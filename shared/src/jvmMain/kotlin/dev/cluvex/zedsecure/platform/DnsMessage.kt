package dev.cluvex.zedsecure.platform

data class DnsMessage(
    val id: Int,
    val flags: Int,
    val questions: List<DnsQuestion>,
    val answers: List<DnsRecord>,
    val authority: List<DnsRecord>,
    val additional: List<DnsRecord>,

    val sizeBytes: Int,
) {
    val isResponse: Boolean get() = flags and 0x8000 != 0
    val truncated: Boolean get() = flags and 0x0200 != 0
    val recursionAvailable: Boolean get() = flags and 0x0080 != 0
    val rcode: Int get() = flags and 0x000F

    fun answersOf(type: DnsRecordType): List<DnsRecord> = answers.filter { it.type == type.value }

    fun nsNames(): List<String> =
        (answers + authority).filter { it.type == DnsRecordType.NS.value }.mapNotNull { it.name2 }

    fun firstTxt(): ByteArray? = answers.firstOrNull { it.type == DnsRecordType.TXT.value }?.txt

    fun firstAddress(): String? =
        answers.firstOrNull { it.type == DnsRecordType.A.value || it.type == DnsRecordType.AAAA.value }?.address
}

data class DnsQuestion(val name: String, val type: Int, val qclass: Int)

data class DnsRecord(
    val name: String,
    val type: Int,
    val rclass: Int,
    val ttl: Long,
    val rdata: ByteArray,
    val name2: String? = null,
    val address: String? = null,
    val txt: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DnsRecord) return false
        return name == other.name && type == other.type && rclass == other.rclass &&
            ttl == other.ttl && rdata.contentEquals(other.rdata) && name2 == other.name2 &&
            address == other.address && (txt?.contentEquals(other.txt ?: ByteArray(0)) ?: (other.txt == null))
    }

    override fun hashCode(): Int {
        var h = name.hashCode()
        h = 31 * h + type
        h = 31 * h + rclass
        h = 31 * h + ttl.hashCode()
        h = 31 * h + rdata.contentHashCode()
        h = 31 * h + (name2?.hashCode() ?: 0)
        h = 31 * h + (address?.hashCode() ?: 0)
        h = 31 * h + (txt?.contentHashCode() ?: 0)
        return h
    }
}

object DnsParser {
    private const val HEADER = 12

    private const val MAX_POINTER_HOPS = 64

    fun parse(bytes: ByteArray, length: Int = bytes.size): DnsMessage? {
        if (length < HEADER || length > bytes.size) return null
        val id = u16(bytes, 0)
        val flags = u16(bytes, 2)
        val qd = u16(bytes, 4)
        val an = u16(bytes, 6)
        val ns = u16(bytes, 8)
        val ar = u16(bytes, 10)

        if (qd + an + ns + ar > length) return null

        var off = HEADER
        val questions = ArrayList<DnsQuestion>(qd)
        repeat(qd) {
            val (name, next) = readName(bytes, length, off) ?: return null
            if (next + 4 > length) return null
            questions += DnsQuestion(name, u16(bytes, next), u16(bytes, next + 2))
            off = next + 4
        }
        fun section(count: Int): List<DnsRecord>? {
            val out = ArrayList<DnsRecord>(count)
            repeat(count) {
                val rec = readRecord(bytes, length, off) ?: return null
                out += rec.first
                off = rec.second
            }
            return out
        }
        val answers = section(an) ?: return null
        val authority = section(ns) ?: return null
        val additional = section(ar) ?: return null
        return DnsMessage(id, flags, questions, answers, authority, additional, length)
    }

    private fun readRecord(bytes: ByteArray, length: Int, start: Int): Pair<DnsRecord, Int>? {
        val (name, afterName) = readName(bytes, length, start) ?: return null
        if (afterName + 10 > length) return null
        val type = u16(bytes, afterName)
        val rclass = u16(bytes, afterName + 2)
        val ttl = ((u16(bytes, afterName + 4).toLong() shl 16) or u16(bytes, afterName + 6).toLong())
        val rdLength = u16(bytes, afterName + 8)
        val rdStart = afterName + 10
        if (rdStart + rdLength > length) return null
        val rdata = bytes.copyOfRange(rdStart, rdStart + rdLength)

        val name2 = when (type) {
            DnsRecordType.NS.value, DnsRecordType.CNAME.value ->
                readName(bytes, length, rdStart)?.first
            else -> null
        }
        val address = when {
            type == DnsRecordType.A.value && rdLength == 4 ->
                rdata.joinToString(".") { (it.toInt() and 0xFF).toString() }
            type == DnsRecordType.AAAA.value && rdLength == 16 ->
                (0 until 8).joinToString(":") {
                    ((rdata[it * 2].toInt() and 0xFF shl 8) or (rdata[it * 2 + 1].toInt() and 0xFF))
                        .toString(16)
                }
            else -> null
        }

        val txt = if (type == DnsRecordType.TXT.value) readTxt(rdata) else null
        return DnsRecord(name, type, rclass, ttl, rdata, name2, address, txt) to (rdStart + rdLength)
    }

    private fun readTxt(rdata: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream(rdata.size)
        var i = 0
        while (i < rdata.size) {
            val n = rdata[i].toInt() and 0xFF
            i++
            if (i + n > rdata.size) break
            out.write(rdata, i, n)
            i += n
        }
        return out.toByteArray()
    }

    fun readName(bytes: ByteArray, length: Int, start: Int): Pair<String, Int>? {
        val labels = ArrayList<String>(8)
        var off = start
        var afterFirstPointer = -1
        var hops = 0
        var total = 0
        while (true) {
            if (off >= length) return null
            val len = bytes[off].toInt() and 0xFF
            when {
                len == 0 -> {
                    off++
                    val end = if (afterFirstPointer >= 0) afterFirstPointer else off
                    return labels.joinToString(".") to end
                }
                len and 0xC0 == 0xC0 -> {
                    if (off + 1 >= length) return null
                    val pointer = ((len and 0x3F) shl 8) or (bytes[off + 1].toInt() and 0xFF)

                    if (afterFirstPointer < 0) afterFirstPointer = off + 2

                    if (pointer >= off || pointer >= length) return null
                    if (++hops > MAX_POINTER_HOPS) return null
                    off = pointer
                }
                len > 63 -> return null
                else -> {
                    if (off + 1 + len > length) return null
                    total += len + 1
                    if (total > MAX_DNS_NAME + 2) return null
                    labels += String(bytes, off + 1, len, Charsets.ISO_8859_1)
                    off += 1 + len
                }
            }
        }
    }

    private fun u16(b: ByteArray, at: Int): Int =
        ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)
}
