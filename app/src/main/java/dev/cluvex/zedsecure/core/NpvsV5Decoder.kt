package dev.cluvex.zedsecure.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.engines.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter

/**
 * Local, passphrase-only reader for the NPVS v5 compact envelope.
 *
 * Recipient-key and app-key envelopes are intentionally rejected; they need separate
 * key material/white-box support and must not be treated as successfully decrypted.
 */
internal object NpvsV5Decoder {
    private const val MAX_FILE_BYTES = 10 * 1024 * 1024
    private const val HEADER_FIXED = 135
    private const val PASS_BLOCK_SIZE = 80
    private const val BODY_MAGIC = "NPF\u0001"
    private const val SENTINEL_SEQ = 0xffff
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun decrypt(bytes: ByteArray, passphrase: String): String {
        require(bytes.size <= MAX_FILE_BYTES) { "NPVS file exceeds the 10 MB import limit" }
        require(passphrase.isNotEmpty()) { "Enter the NPVS creator passphrase" }
        require(bytes.size >= 9 && bytes.copyOfRange(0, 4).decodeToString() == "NPVS") {
            "Not an NPVS container"
        }
        require((bytes[4].toInt() and 0xff) == 5) { "Only NPVS v5 is supported by this decoder" }
        val headerLength = readUInt32(bytes, 5)
        require(headerLength >= HEADER_FIXED && 9L + headerLength <= bytes.size.toLong()) {
            "Malformed NPVS v5 header length"
        }
        val header = bytes.copyOfRange(9, 9 + headerLength)
        require((header[0].toInt() and 0xff) == 1) { "Unsupported NPVS v5 header revision" }
        val method = header[50].toInt() and 0xff
        require(method == 1) {
            when (method) {
                0 -> "This NPVS file needs the recipient's private key; passphrase import is not applicable"
                2 -> "This NPVS file uses the NPV app-key method, which is not supported by this decoder"
                else -> "Unknown NPVS unlock method"
            }
        }
        val recipients = ((header[51].toInt() and 0xff) shl 8) or (header[52].toInt() and 0xff)
        val keyEnd = 53L + recipients.toLong() * 125L
        val metaOffset = keyEnd + PASS_BLOCK_SIZE
        require(metaOffset + 4L <= header.size.toLong()) { "Truncated NPVS v5 passphrase section" }
        val iterations = readUInt32(header, keyEnd.toInt())
        require(iterations in 1..10_000_000) { "Invalid NPVS passphrase KDF iteration count" }
        val salt = header.copyOfRange(keyEnd.toInt() + 4, keyEnd.toInt() + 20)
        val wrap = header.copyOfRange(keyEnd.toInt() + 20, (keyEnd + PASS_BLOCK_SIZE).toInt())
        require(wrap.size == 60) { "Malformed NPVS passphrase key wrap" }
        val metaLength = readUInt32(header, metaOffset.toInt())
        require(metaLength >= 16 && metaOffset + 4L + metaLength <= header.size.toLong()) {
            "Malformed NPVS encrypted metadata"
        }
        val prefix = header.copyOfRange(0, metaOffset.toInt())

        val bodyOffset = 9 + headerLength
        require(bodyOffset + 16 <= bytes.size) { "Truncated NPVS v5 body header" }
        val nonce = bytes.copyOfRange(bodyOffset, bodyOffset + 12)
        val bodyLength = readUInt32(bytes, bodyOffset + 12)
        require(bodyLength >= 32 && bodyOffset.toLong() + 16L + bodyLength + 64L == bytes.size.toLong()) {
            "Malformed NPVS v5 ciphertext length"
        }
        val encryptedMetadata = header.copyOfRange(metaOffset.toInt() + 4, metaOffset.toInt() + 4 + metaLength)
        val body = bytes.copyOfRange(bodyOffset + 16, bodyOffset + 16 + bodyLength)

        val kdk = pbkdf2Sha256(passphrase.encodeToByteArray(), salt, iterations, 32)
        val dek = chachaOpen(kdk, wrap.copyOfRange(0, 12), wrap.copyOfRange(12, wrap.size), salt)
        require(dek.size == 32) { "NPVS key wrap returned an invalid data key" }

        val metadataKey = hkdfSha256(dek, nonce, "NPVS-v5/metadata".encodeToByteArray(), 32)
        // Authenticate the metadata too: a valid key must open both envelope sections.
        chachaOpen(metadataKey, nonce, encryptedMetadata, prefix)

        val fields = decryptFields(body, dek)
        val table = fields[SENTINEL_SEQ]
            ?: throw IllegalArgumentException("NPVS payload opened, but its profile table is missing")
        val root = runCatching { json.parseToJsonElement(table.decodeToString()) }
            .getOrElse { throw IllegalArgumentException("NPVS profile table is not valid JSON", it) }
        val rootObject = root as? JsonObject
            ?: throw IllegalArgumentException("NPVS profile table must be a JSON object")
        val configs = rootObject["configs"] as? JsonArray
            ?: throw IllegalArgumentException("NPVS profile table has no configs array")
        val resolved = JsonArray(configs.map { substituteSentinels(it, fields) })
        require(resolved.isNotEmpty()) { "NPVS file contains no profiles" }
        return json.encodeToString(JsonElement.serializer(), resolved)
    }

    private fun decryptFields(body: ByteArray, dek: ByteArray): Map<Int, ByteArray> {
        require(body.size >= 38 && body.copyOfRange(0, 4).decodeToString() == BODY_MAGIC) {
            "Invalid NPVS NPF payload"
        }
        val contentId = body.copyOfRange(4, 36)
        val count = readUInt16(body, 36)
        var offset = 38
        val fields = linkedMapOf<Int, ByteArray>()
        repeat(count) {
            require(offset + 6 <= body.size) { "Truncated NPVS field header" }
            val sequence = readUInt16(body, offset)
            val blobLength = readUInt16(body, offset + 4)
            offset += 6 // sequence, flags, blob length
            require(offset + blobLength <= body.size) { "Truncated NPVS encrypted field" }
            val blob = body.copyOfRange(offset, offset + blobLength)
            offset += blobLength
            if (blob.size < 16) return@repeat
            val key = hkdfSha256(
                dek,
                contentId,
                "NPV-fields-v1/field/".encodeToByteArray() + u16(sequence),
                32,
            )
            val plainLength = blob.size - 16
            val aad = "NPV-fields-v1/record/".encodeToByteArray() +
                contentId + u16(sequence) + u32(plainLength)
            val plain = runCatching { chachaOpen(key, ByteArray(12), blob, aad) }.getOrNull()
            if (plain != null) fields[sequence] = plain
        }
        require(offset == body.size) { "Unexpected trailing bytes in NPVS profile table" }
        return fields
    }

    private fun substituteSentinels(element: JsonElement, fields: Map<Int, ByteArray>): JsonElement = when (element) {
        is JsonArray -> JsonArray(element.map { substituteSentinels(it, fields) })
        is JsonObject -> JsonObject(element.mapValues { (_, value) -> substituteSentinels(value, fields) })
        is JsonPrimitive -> {
            if (element.isString) element
            else {
                val sequence = element.content.toIntOrNull()
                if (sequence == null) element
                else JsonPrimitive(fields[sequence]?.let(::fieldText).orEmpty())
            }
        }
        else -> element
    }

    private fun fieldText(bytes: ByteArray): String {
        val raw = bytes.decodeToString(throwOnInvalidSequence = false).trim().trimEnd('\u0000')
        val unquoted = if (raw.length >= 2 && raw.first() == '"' && raw.last() == '"') {
            val parsed = runCatching { json.parseToJsonElement(raw) as? JsonPrimitive }.getOrNull()
            if (parsed != null && parsed.isString) parsed.content else raw
        } else raw
        return decodeSentinels(unquoted)
    }

    private fun decodeSentinels(input: String): String {
        val prefix = "npvs1:"
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=_-"
        var remaining = input
        val out = StringBuilder()
        while (true) {
            val marker = remaining.indexOf(prefix)
            if (marker < 0) {
                out.append(remaining)
                return out.toString()
            }
            out.append(remaining.substring(0, marker))
            remaining = remaining.substring(marker + prefix.length)
            var tokenEnd = 0
            while (tokenEnd < remaining.length && remaining[tokenEnd] in alphabet) tokenEnd++
            if (tokenEnd == 0) {
                out.append(prefix)
                if (remaining.isNotEmpty()) {
                    out.append(remaining[0])
                    remaining = remaining.substring(1)
                }
                continue
            }
            val token = remaining.substring(0, tokenEnd)
            remaining = remaining.substring(tokenEnd)
            val decoded = runCatching {
                val normalized = token.replace('-', '+').replace('_', '/')
                val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
                java.util.Base64.getDecoder().decode(padded)
            }.getOrNull()
            if (decoded == null) out.append(prefix).append(token)
            else out.append(decoded.decodeToString(throwOnInvalidSequence = false))
        }
    }

    private fun chachaOpen(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray {
        try {
            // Use Bouncy Castle's lightweight implementation so NPVS v5 import also works
            // on Android API 24-27, where the platform ChaCha20-Poly1305 provider may be absent.
            val cipher = ChaCha20Poly1305()
            cipher.init(false, AEADParameters(KeyParameter(key), 128, nonce, aad))
            val output = ByteArray(cipher.getOutputSize(ciphertext.size))
            var written = cipher.processBytes(ciphertext, 0, ciphertext.size, output, 0)
            written += cipher.doFinal(output, written)
            return output.copyOf(written)
        } catch (e: Exception) {
            throw IllegalArgumentException(
                "NPVS decryption failed: wrong passphrase, damaged file, or ChaCha20-Poly1305 unavailable",
                e,
            )
        }
    }

    private fun pbkdf2Sha256(password: ByteArray, salt: ByteArray, iterations: Int, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(password, "HmacSHA256"))
        val output = ByteArrayOutputStream(length)
        var blockIndex = 1
        while (output.size() < length) {
            val first = mac.doFinal(salt + u32(blockIndex))
            var u = first
            val xor = first.copyOf()
            repeat(iterations - 1) {
                u = mac.doFinal(u)
                for (i in xor.indices) xor[i] = (xor[i].toInt() xor u[i].toInt()).toByte()
            }
            output.write(xor)
            blockIndex++
        }
        return output.toByteArray().copyOf(length)
    }

    private fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(if (salt.isEmpty()) ByteArray(32) else salt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)
        val out = ByteArrayOutputStream(length)
        var previous = ByteArray(0)
        var counter = 1
        while (out.size() < length) {
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            previous = mac.doFinal(previous + info + byteArrayOf(counter.toByte()))
            out.write(previous)
            counter++
        }
        return out.toByteArray().copyOf(length)
    }

    private fun readUInt16(bytes: ByteArray, offset: Int): Int {
        require(offset >= 0 && offset + 2 <= bytes.size) { "Truncated NPVS integer field" }
        return ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)
    }

    private fun readUInt32(bytes: ByteArray, offset: Int): Int {
        require(offset >= 0 && offset + 4 <= bytes.size) { "Truncated NPVS integer field" }
        val value = ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xffffffffL
        require(value <= Int.MAX_VALUE) { "NPVS length field is too large" }
        return value.toInt()
    }

    private fun u16(value: Int) = byteArrayOf((value ushr 8).toByte(), value.toByte())
    private fun u32(value: Int) = byteArrayOf(
        (value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte(),
    )
}
