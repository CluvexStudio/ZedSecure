package dev.cluvex.zedsecure.core

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NpvsV5DecoderTest {
    @Test
    fun decryptsSyntheticPassphraseEnvelopeAndResolvesProfileFields() {
        val bytes = syntheticEnvelope("correct horse battery staple")
        val result = NpvsV5Decoder.decrypt(bytes, "correct horse battery staple")

        assertEquals(1, result.count { it == '{' } - 1) // one profile object nested in the array
        assertTrue(result.contains("\"name\":\"Synthetic profile\""))
        assertTrue(result.contains("\"server\":\"example.test\""))
        assertTrue(result.contains("\"serverPort\":\"443\""))
        assertTrue(result.contains("\"password\":\"synthetic-credential\""))
    }

    @Test
    fun rejectsWrongPassphraseWithoutReturningPartialProfile() {
        val bytes = syntheticEnvelope("right-password")
        assertThrows(IllegalArgumentException::class.java) {
            NpvsV5Decoder.decrypt(bytes, "wrong-password")
        }
    }

    @Test
    fun rejectsTruncatedAndWrongVersionContainers() {
        assertThrows(IllegalArgumentException::class.java) {
            NpvsV5Decoder.decrypt(byteArrayOf(78, 80, 86, 83), "password")
        }
        val bytes = syntheticEnvelope("password")
        bytes[4] = 4
        assertThrows(IllegalArgumentException::class.java) {
            NpvsV5Decoder.decrypt(bytes, "password")
        }
    }

    private fun syntheticEnvelope(passphrase: String): ByteArray {
        val dek = ByteArray(32) { (it + 1).toByte() }
        val salt = ByteArray(16) { (it * 3 + 7).toByte() }
        val nonce = ByteArray(12) { (it * 5 + 11).toByte() }
        val contentId = ByteArray(32) { (it * 7 + 13).toByte() }
        val iterations = 2
        val prefix = ByteArrayOutputStream().apply {
            write(byteArrayOf(1)) // compact header revision
            write(ByteArray(16)) // config ID
            write(ByteArray(33)) // creator fingerprint/public-key slot
            write(byteArrayOf(1)) // creator passphrase method
            write(u16(0)) // recipient count
        }.toByteArray()
        check(prefix.size == 53)

        val kdk = pbkdf2(passphrase, salt, iterations, 32)
        val wrapNonce = ByteArray(12) { (it + 19).toByte() }
        val wrappedDek = aeadEncrypt(kdk, wrapNonce, dek, salt)
        val passBlock = ByteArrayOutputStream().apply {
            write(u32(iterations))
            write(salt)
            write(wrapNonce)
            write(wrappedDek)
        }.toByteArray()
        check(passBlock.size == 80)
        val metadataAadPrefix = prefix + passBlock
        val metadataKey = hkdf(dek, nonce, "NPVS-v5/metadata".toByteArray())
        val metadata = """{"issuedAt":"2026-01-01T00:00:00Z","policy":{"configVersion":1}}""".toByteArray()
        val encryptedMetadata = aeadEncrypt(metadataKey, nonce, metadata, metadataAadPrefix)
        val header = metadataAadPrefix + u32(encryptedMetadata.size) + encryptedMetadata

        val table = """{"configs":[{"name":1,"v2rayProfile":{"configType":2,"server":3,"serverPort":4,"password":5}}]}""".toByteArray()
        val values = mapOf(
            1 to "Synthetic profile",
            2 to "5",
            3 to "example.test",
            4 to "443",
            5 to "synthetic-credential",
            0xffff to table.decodeToString(),
        )
        val body = ByteArrayOutputStream().apply {
            write("NPF\u0001".toByteArray())
            write(contentId)
            write(u16(values.size))
            values.forEach { (sequence, value) ->
                val plain = value.toByteArray()
                val key = hkdf(dek, contentId, "NPV-fields-v1/field/".toByteArray() + u16(sequence))
                val aad = "NPV-fields-v1/record/".toByteArray() + contentId + u16(sequence) + u32(plain.size)
                val encrypted = aeadEncrypt(key, ByteArray(12), plain, aad)
                write(u16(sequence))
                write(u16(0)) // flags
                write(u16(encrypted.size))
                write(encrypted)
            }
        }.toByteArray()

        return ByteArrayOutputStream().apply {
            write("NPVS".toByteArray())
            write(byteArrayOf(5))
            write(u32(header.size))
            write(header)
            write(nonce)
            write(u32(body.size))
            write(body)
            write(ByteArray(64)) // signature bytes are length-checked, not verified by this reader
        }.toByteArray()
    }

    private fun pbkdf2(password: String, salt: ByteArray, iterations: Int, length: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, length * 8)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray): ByteArray {
        val extract = Mac.getInstance("HmacSHA256")
        extract.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = extract.doFinal(ikm)
        val expand = Mac.getInstance("HmacSHA256")
        expand.init(SecretKeySpec(prk, "HmacSHA256"))
        return expand.doFinal(info + byteArrayOf(1)).copyOf(32)
    }

    private fun aeadEncrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("ChaCha20-Poly1305")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "ChaCha20"), IvParameterSpec(nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(plaintext)
    }

    private fun u16(value: Int) = byteArrayOf((value ushr 8).toByte(), value.toByte())
    private fun u32(value: Int) = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value).array()
}
