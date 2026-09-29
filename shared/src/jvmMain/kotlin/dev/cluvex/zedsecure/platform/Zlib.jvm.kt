package dev.cluvex.zedsecure.platform

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

internal actual fun inflateZlib(data: ByteArray): ByteArray? = runCatching {
    val inflater = Inflater()
    try {
        inflater.setInput(data)
        val out = ByteArrayOutputStream(maxOf(data.size * 4, 1024))
        val buf = ByteArray(16 * 1024)
        while (!inflater.finished()) {
            val n = inflater.inflate(buf)
            if (n == 0) {
                if (inflater.needsInput() || inflater.needsDictionary()) return null
            } else {
                out.write(buf, 0, n)
            }
        }
        out.toByteArray().takeIf { it.isNotEmpty() }
    } finally {
        inflater.end()
    }
}.getOrNull()
