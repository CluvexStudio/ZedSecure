package dev.cluvex.zedsecure.platform

object DnsWire {
    const val HEADER_BYTES = 12

    private const val TYPE_OPT = 41

    fun buildQuery(
        id: Int,
        name: String,
        type: DnsRecordType,
        ednsPayloadSize: Int,
        dnssecOk: Boolean = false,
    ): ByteArray {
        val qname = encodeName(name)
        val edns = if (ednsPayloadSize > 0) encodeOpt(ednsPayloadSize, dnssecOk) else ByteArray(0)
        val out = ByteArray(HEADER_BYTES + qname.size + 4 + edns.size)
        var i = 0
        fun be16(v: Int) {
            out[i++] = (v ushr 8).toByte()
            out[i++] = v.toByte()
        }
        be16(id)
        be16(0x0100)
        be16(1)
        be16(0)
        be16(0)
        be16(if (edns.isEmpty()) 0 else 1)
        qname.copyInto(out, i); i += qname.size
        be16(type.value)
        be16(1)
        edns.copyInto(out, i)
        return out
    }

    fun encodeName(name: String): ByteArray {
        val trimmed = name.trim('.')
        if (trimmed.isEmpty()) return byteArrayOf(0)
        val labels = trimmed.split('.').filter { it.isNotEmpty() }
        require(labels.isNotEmpty()) { "empty name" }
        var total = 1
        labels.forEach {
            require(it.length in 1..MAX_DNS_LABEL) { "label out of range: ${it.length}" }
            total += it.length + 1
        }
        require(total <= MAX_DNS_NAME + 2) { "name too long: $total" }
        val out = ByteArray(total)
        var i = 0
        labels.forEach { label ->
            out[i++] = label.length.toByte()
            label.forEach { out[i++] = it.code.toByte() }
        }
        out[i] = 0
        return out
    }

    fun encodeOpt(payloadSize: Int, dnssecOk: Boolean = false): ByteArray {
        val size = payloadSize.coerceIn(512, 65535)
        return byteArrayOf(
            0,
            0, TYPE_OPT.toByte(),
            (size ushr 8).toByte(), size.toByte(),
            if (dnssecOk) 0x80.toByte() else 0, 0, 0, 0,
            0, 0,
        )
    }

    fun parseReply(
        bytes: ByteArray,
        length: Int,
        expectedId: Int,
        elapsedMs: Long,

        expectedName: String? = null,

        expectedType: Int = -1,
    ): DnsResult {
        if (length < HEADER_BYTES) return DnsResult.Failed(DnsFailure.Malformed)
        val id = ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
        if (expectedId >= 0 && id != expectedId) return DnsResult.Failed(DnsFailure.Mismatch)

        val flagsHigh = bytes[2].toInt() and 0xFF

        if (flagsHigh and 0x80 == 0) return DnsResult.Failed(DnsFailure.Mismatch)

        val message = DnsParser.parse(bytes, length)
            ?: return DnsResult.Failed(DnsFailure.Malformed)

        if (expectedName != null) {
            val q = message.questions.firstOrNull()
                ?: return DnsResult.Failed(DnsFailure.Mismatch)
            if (!q.name.equals(expectedName.trim('.'), ignoreCase = true)) {
                return DnsResult.Failed(DnsFailure.Mismatch)
            }
            if (expectedType >= 0 && q.type != expectedType) {
                return DnsResult.Failed(DnsFailure.Mismatch)
            }
        }

        if (message.rcode != 0) return DnsResult.Failed(DnsFailure.Refused, elapsedMs)
        return DnsResult.Ok(
            DnsReply(
                rcode = message.rcode,
                truncated = message.truncated,
                answerCount = message.answers.size,
                elapsedMs = elapsedMs,
                sizeBytes = length,
                matchingAnswers = if (expectedType >= 0) {
                    message.answers.count { it.type == expectedType }
                } else message.answers.size,
                txtBytes = message.firstTxt()?.size ?: 0,
                nsNames = message.nsNames(),
                address = message.firstAddress(),
            ),
        )
    }
}
