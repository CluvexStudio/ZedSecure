package dev.cluvex.zedsecure.data.config

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

object ConfigArchive {
    private const val MAX_ENTRIES = 5_000
    private const val MAX_ENTRY_BYTES = 1 shl 20
    private const val MAX_TOTAL_BYTES = 64 shl 20

    private val SKIPPED = setOf(
        "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "pdf", "apk", "exe", "dll", "so", "zip", "rar",
        "7z", "gz", "tgz", "tar", "xz", "mp3", "mp4", "mkv", "doc", "docx", "xls", "xlsx",
    )

    class Entry(val path: String, val text: String) {
        val baseName: String get() = path.substringAfterLast('/').substringBeforeLast('.').trim()
        val isOvpn: Boolean get() = path.endsWith(".ovpn", ignoreCase = true)
    }

    fun isZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()

    fun entries(bytes: ByteArray): List<Entry> {
        val utf8 = runCatching { read(bytes, Charsets.UTF_8) }.getOrNull()
        if (utf8 != null) return utf8
        return runCatching { read(bytes, Charsets.ISO_8859_1) }.getOrDefault(emptyList())
    }

    private fun read(bytes: ByteArray, names: Charset): List<Entry> {
        val out = ArrayList<Entry>()
        var total = 0L
        ZipInputStream(bytes.inputStream(), names).use { zip ->
            while (out.size < MAX_ENTRIES) {
                val entry = try {
                    zip.nextEntry ?: break
                } catch (e: IllegalArgumentException) {
                    throw e
                } catch (_: Exception) {
                    break
                }
                if (entry.isDirectory) continue
                val path = entry.name.replace('\\', '/')
                val name = path.substringAfterLast('/')
                if (name.isEmpty() || name.startsWith(".") || path.startsWith("__MACOSX/")) continue
                if (name.substringAfterLast('.', "").lowercase() in SKIPPED) continue
                val data = runCatching { readCapped(zip, MAX_ENTRY_BYTES) }.getOrNull() ?: continue
                total += data.size
                if (total > MAX_TOTAL_BYTES) break
                val text = data.decodeToString().trimStart('﻿').trim()
                if (text.isEmpty() || text.indexOf('\u0000') >= 0) continue
                out += Entry(path, text)
            }
        }
        return out
    }

    private fun readCapped(input: InputStream, cap: Int): ByteArray? {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        while (true) {
            val n = input.read(chunk)
            if (n < 0) break
            if (buffer.size() + n > cap) return null
            buffer.write(chunk, 0, n)
        }
        return buffer.toByteArray()
    }
}
