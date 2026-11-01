package dev.cluvex.zedsecure.data.net

object DnsWire {
    const val TYPE_A = 1
    const val TYPE_PTR = 12

    fun query(id: Int, name: String, type: Int): ByteArray {
        val out = ArrayList<Byte>(64)
        fun u16(v: Int) {
            out.add(((v shr 8) and 0xff).toByte())
            out.add((v and 0xff).toByte())
        }
        u16(id)
        u16(0x0100)
        u16(1)
        u16(0)
        u16(0)
        u16(0)
        name.trimEnd('.').split('.').filter { it.isNotEmpty() }.forEach { label ->
            val bytes = label.encodeToByteArray()
            require(bytes.size in 1..63) { "bad DNS label" }
            out.add(bytes.size.toByte())
            bytes.forEach { out.add(it) }
        }
        out.add(0)
        u16(type)
        u16(1)
        return out.toByteArray()
    }

    class Answer(val type: Int, val data: String)

    fun answers(message: ByteArray, id: Int? = null): List<Answer> {
        if (message.size < 12) return emptyList()
        if (id != null && u16(message, 0) != id) return emptyList()
        val flags = u16(message, 2)
        if (flags and 0x8000 == 0) return emptyList()
        if (flags and 0x000f != 0) return emptyList()
        val questions = u16(message, 4)
        val count = u16(message, 6)
        var at = 12
        repeat(questions) {
            at = skipName(message, at) ?: return emptyList()
            at += 4
        }
        val result = ArrayList<Answer>()
        repeat(count) {
            at = skipName(message, at) ?: return result
            if (at + 10 > message.size) return result
            val type = u16(message, at)
            val length = u16(message, at + 8)
            val start = at + 10
            if (start + length > message.size) return result
            when (type) {
                TYPE_A -> if (length == 4) {
                    result += Answer(type, (0 until 4).joinToString(".") { (message[start + it].toInt() and 0xff).toString() })
                }
                TYPE_PTR -> readName(message, start)?.let { result += Answer(type, it) }
            }
            at = start + length
        }
        return result
    }

    fun reverseName(ip: String): String? {
        val v4 = ip.split('.')
        if (v4.size == 4 && v4.all { p -> p.toIntOrNull()?.let { it in 0..255 } == true }) {
            return v4.reversed().joinToString(".") + ".in-addr.arpa"
        }
        val groups = expandIpv6(ip) ?: return null
        return groups.joinToString("") { it.padStart(4, '0') }.reversed().toCharArray().joinToString(".") + ".ip6.arpa"
    }

    fun isFakeDnsAddress(ip: String): Boolean {
        val parts = ip.split('.').mapNotNull { it.toIntOrNull() }
        return parts.size == 4 && parts[0] == 198 && (parts[1] == 18 || parts[1] == 19)
    }

    private fun expandIpv6(ip: String): List<String>? {
        val clean = ip.substringBefore('%').trim()
        if (!clean.contains(':')) return null
        val halves = clean.split("::")
        if (halves.size > 2) return null
        val head = halves[0].split(':').filter { it.isNotEmpty() }
        val tail = if (halves.size == 2) halves[1].split(':').filter { it.isNotEmpty() } else emptyList()
        val missing = 8 - head.size - tail.size
        if (missing < 0 || (halves.size == 1 && missing != 0)) return null
        val groups = head + List(missing) { "0" } + tail
        if (groups.any { g -> g.length > 4 || g.any { it !in '0'..'9' && it.lowercaseChar() !in 'a'..'f' } }) return null
        return groups.map { it.lowercase() }
    }

    private fun u16(b: ByteArray, at: Int): Int = ((b[at].toInt() and 0xff) shl 8) or (b[at + 1].toInt() and 0xff)

    private fun skipName(b: ByteArray, start: Int): Int? {
        var at = start
        while (at < b.size) {
            val len = b[at].toInt() and 0xff
            when {
                len == 0 -> return at + 1
                len and 0xc0 == 0xc0 -> return if (at + 1 < b.size) at + 2 else null
                else -> at += len + 1
            }
        }
        return null
    }

    private fun readName(b: ByteArray, start: Int): String? {
        val labels = ArrayList<String>()
        var at = start
        var jumps = 0
        while (at < b.size) {
            val len = b[at].toInt() and 0xff
            when {
                len == 0 -> return labels.joinToString(".")
                len and 0xc0 == 0xc0 -> {
                    if (at + 1 >= b.size || ++jumps > 16) return null
                    at = ((len and 0x3f) shl 8) or (b[at + 1].toInt() and 0xff)
                }
                else -> {
                    if (at + 1 + len > b.size) return null
                    labels += b.copyOfRange(at + 1, at + 1 + len).decodeToString()
                    at += len + 1
                }
            }
        }
        return null
    }
}
