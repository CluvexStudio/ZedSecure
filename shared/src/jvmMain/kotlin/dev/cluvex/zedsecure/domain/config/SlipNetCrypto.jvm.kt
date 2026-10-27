package dev.cluvex.zedsecure.domain.config

import java.security.GeneralSecurityException
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

actual object SlipNetCrypto {
    private val legacyKey = hex("214F052025B2F949605A5429EC3D5FA80C2022C168AD946E68852D447214DBD3")
    private const val BUNDLE_ITERATIONS = 600_000
    private const val BUNDLE_SALT_BYTES = 16
    private const val GCM_NONCE_BYTES = 12
    private const val GCM_TAG_BITS = 128

    actual fun decryptLegacy(payload: String): String {
        val data = decode(payload)
        require(data.size >= 1 + GCM_NONCE_BYTES + 16) { "Encrypted SlipNet profile is truncated" }
        require(data[0].toInt() and 0xff == 1) { "Unsupported encrypted SlipNet profile version" }
        val nonce = data.copyOfRange(1, 1 + GCM_NONCE_BYTES)
        val ciphertext = data.copyOfRange(1 + GCM_NONCE_BYTES, data.size)
        return runCatching { aesGcm(legacyKey, nonce, ciphertext) }
            .getOrElse { throw IllegalArgumentException("Could not decrypt SlipNet profile; it may be damaged or locked.", it) }
    }

    actual fun decryptBundle(payload: String, password: String): String {
        require(password.isNotEmpty()) { "Enter the SlipNet bundle password" }
        val data = decode(payload)
        require(data.size >= 1 + BUNDLE_SALT_BYTES + GCM_NONCE_BYTES + 16) {
            "Encrypted SlipNet bundle is truncated"
        }
        require(data[0].toInt() and 0xff == 1) { "Unsupported SlipNet bundle version" }

        val saltStart = 1
        val nonceStart = saltStart + BUNDLE_SALT_BYTES
        val ciphertextStart = nonceStart + GCM_NONCE_BYTES
        val salt = data.copyOfRange(saltStart, nonceStart)
        val nonce = data.copyOfRange(nonceStart, ciphertextStart)
        val ciphertext = data.copyOfRange(ciphertextStart, data.size)
        val spec = PBEKeySpec(password.toCharArray(), salt, BUNDLE_ITERATIONS, 256)
        val key = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        return try {
            aesGcm(key, nonce, ciphertext)
        } catch (e: AEADBadTagException) {
            throw IllegalArgumentException("Wrong password or corrupted SlipNet bundle", e)
        } catch (e: GeneralSecurityException) {
            throw IllegalArgumentException("Could not decrypt SlipNet bundle", e)
        } finally {
            key.fill(0)
        }
    }

    private fun aesGcm(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    private fun decode(payload: String): ByteArray = try {
        Base64.getMimeDecoder().decode(payload.filterNot(Char::isWhitespace))
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("SlipNet payload is not valid Base64", e)
    }

    private fun hex(value: String): ByteArray =
        value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
