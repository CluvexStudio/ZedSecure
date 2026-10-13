package dev.cluvex.zedsecure.data.assets

import dev.cluvex.zedsecure.core.LogBus
import dev.cluvex.zedsecure.domain.config.GeoCodes
import dev.cluvex.zedsecure.domain.model.RulesetItem
import java.io.EOFException
import java.io.File
import java.io.InputStream

object GeoCodeReader {
    private class Cached(val size: Long, val modified: Long, val codes: Set<String>)

    private val cache = HashMap<String, Cached>()
    private var lastMissing: List<String> = emptyList()

    fun read(dir: File): GeoCodes? {
        val site = codes(File(dir, "geosite.dat")) ?: return null
        val ip = codes(File(dir, "geoip.dat")) ?: return null
        return GeoCodes(site, ip)
    }

    fun readFor(dir: File, rulesets: List<RulesetItem>): GeoCodes? = read(dir)?.also { codes ->
        val missing = codes.missingIn(rulesets)
        synchronized(this) {
            if (missing.isNotEmpty() && missing != lastMissing) {
                LogBus.append("W/Routing: ${missing.joinToString()} is not in the geo files, so the rule skips it; another source in Settings › Assets may have it")
            }
            lastMissing = missing
        }
    }

    @Synchronized
    private fun codes(file: File): Set<String>? {
        val size = file.length()
        if (!file.isFile || size <= 0L) return null
        val modified = file.lastModified()
        cache[file.absolutePath]?.takeIf { it.size == size && it.modified == modified }?.let { return it.codes }
        val parsed = runCatching { file.inputStream().buffered(1 shl 16).use { parse(it, size) } }
            .getOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        cache[file.absolutePath] = Cached(size, modified, parsed)
        return parsed
    }

    private fun parse(input: InputStream, size: Long): Set<String> {
        val r = Reader(input)
        val codes = HashSet<String>()
        while (true) {
            val key = r.varintOrNull() ?: break
            if ((key and 7L) != 2L) error("not a geo list")
            val entryLength = r.varint()
            val end = r.consumed + entryLength
            if (end > size) error("truncated")
            while (r.consumed < end) {
                val field = r.varint()
                when ((field and 7L).toInt()) {
                    0 -> r.varint()
                    1 -> r.skip(8)
                    2 -> {
                        val length = r.varint()
                        if (r.consumed + length > end) error("bad length")
                        if ((field ushr 3) == 1L) {
                            codes += r.bytes(length.toInt()).decodeToString().uppercase()
                            r.skip(end - r.consumed)
                        } else {
                            r.skip(length)
                        }
                    }
                    5 -> r.skip(4)
                    else -> error("bad wire type")
                }
            }
            if (r.consumed != end) error("bad entry")
        }
        return codes
    }

    private class Reader(private val input: InputStream) {
        var consumed = 0L
            private set

        fun varintOrNull(): Long? {
            var result = 0L
            var shift = 0
            while (true) {
                val b = input.read()
                if (b < 0) {
                    if (shift == 0) return null
                    throw EOFException()
                }
                consumed++
                result = result or ((b and 0x7f).toLong() shl shift)
                if ((b and 0x80) == 0) return result
                shift += 7
                if (shift > 63) error("bad varint")
            }
        }

        fun varint(): Long = varintOrNull() ?: throw EOFException()

        fun bytes(n: Int): ByteArray {
            val out = ByteArray(n)
            var off = 0
            while (off < n) {
                val read = input.read(out, off, n - off)
                if (read < 0) throw EOFException()
                off += read
            }
            consumed += n
            return out
        }

        fun skip(n: Long) {
            if (n <= 0) return
            var left = n
            while (left > 0) {
                val skipped = input.skip(left)
                if (skipped > 0) {
                    left -= skipped
                } else {
                    if (input.read() < 0) throw EOFException()
                    left--
                }
            }
            consumed += n
        }
    }
}
