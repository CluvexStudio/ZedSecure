package dev.cluvex.zedsecure.domain.config

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
object AmneziaQr {
    private const val MAGIC = 1984

    private const val HEADER = 8

    data class Chunk(val total: Int, val index: Int, val data: ByteArray) {
        override fun equals(other: Any?): Boolean =
            other is Chunk && total == other.total && index == other.index && data.contentEquals(other.data)

        override fun hashCode(): Int = (total * 31 + index) * 31 + data.contentHashCode()
    }

    fun chunk(text: String): Chunk? {
        val raw = runCatching {
            Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
                .decode(text.trim().replace('+', '-').replace('/', '_').trimEnd('='))
        }.getOrNull() ?: return null
        if (raw.size < HEADER) return null
        if (be16(raw, 0) != MAGIC) return null

        val total = raw[2].toInt() and 0xFF
        val index = raw[3].toInt() and 0xFF
        val length = be32(raw, 4)
        if (total <= 0 || index >= total) return null
        if (length < 0 || HEADER + length > raw.size) return null
        return Chunk(total, index, raw.copyOfRange(HEADER, HEADER + length))
    }

    class Assembler {
        private val frames = LinkedHashMap<Int, ByteArray>()
        private var total = 0

        val received: Int get() = frames.size
        val expected: Int get() = total

        fun offer(text: String): String? {
            val chunk = chunk(text) ?: return null
            if (chunk.total != total) {
                frames.clear()
                total = chunk.total
            }
            frames[chunk.index] = chunk.data
            if (frames.size < total) return null

            val body = ByteArray(frames.values.sumOf { it.size })
            var at = 0
            for (i in 0 until total) {
                val part = frames[i] ?: return null
                part.copyInto(body, at)
                at += part.size
            }
            return Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(body)
        }

        fun reset() {
            frames.clear()
            total = 0
        }
    }

    private fun be16(b: ByteArray, at: Int): Int =
        ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)

    private fun be32(b: ByteArray, at: Int): Int =
        ((b[at].toInt() and 0xFF) shl 24) or ((b[at + 1].toInt() and 0xFF) shl 16) or
            ((b[at + 2].toInt() and 0xFF) shl 8) or (b[at + 3].toInt() and 0xFF)
}
