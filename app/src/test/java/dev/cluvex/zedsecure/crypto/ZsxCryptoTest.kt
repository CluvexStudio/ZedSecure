package dev.cluvex.zedsecure.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ZsxCryptoTest {
    private val sampleConfig =
        "vless://11111111-2222-3333-4444-555555555555@example.com:443?encryption=none&security=reality#Node"

    private fun request(password: String?, expiresAt: Long? = null) = ZsxSealRequest(
        configPayload = sampleConfig,
        nameEn = "Berlin Fast",
        nameFa = "برلین سریع",
        note = "Join @mychannel for updates. 50GB cap.",
        expiresAt = expiresAt,
        password = password,
    )

    @Test
    fun roundtripWithoutPassword() {
        val file = ZsxCrypto.seal(request(password = null))
        assertEquals(sampleConfig, ZsxCrypto.open(file, password = null))
    }

    @Test
    fun roundtripWithPassword() {
        val file = ZsxCrypto.seal(request(password = "correct horse battery"))
        assertEquals(sampleConfig, ZsxCrypto.open(file, password = "correct horse battery"))
    }

    @Test
    fun peekExposesMetadataButNotConfig() {
        val file = ZsxCrypto.seal(request(password = "secret"))
        val meta = ZsxCrypto.peek(file)
        assertTrue(meta.passwordProtected)
        assertEquals("Berlin Fast", meta.nameEn)
        assertEquals("برلین سریع", meta.nameFa)
        assertTrue(meta.note.contains("mychannel"))
        assertFalse(meta.isExpired)
    }

    @Test
    fun newFilesUseVersionTwo() {
        val file = ZsxCrypto.seal(request(password = null))
        assertEquals("ZSX1", String(file.copyOfRange(0, 4), Charsets.US_ASCII))
        assertEquals(2, file[4].toInt())
    }

    @Test
    fun configIsNotStoredAsPlainText() {
        val file = String(ZsxCrypto.seal(request(password = null)), Charsets.ISO_8859_1)
        assertFalse(file.contains("example.com"))
        assertFalse(file.contains("vless://"))
    }

    @Test(expected = ZsxWrongPasswordException::class)
    fun wrongPasswordIsRejected() {
        val file = ZsxCrypto.seal(request(password = "the-real-password"))
        ZsxCrypto.open(file, password = "not-the-password")
    }

    @Test(expected = ZsxPasswordRequiredException::class)
    fun passwordProtectedRequiresPassword() {
        val file = ZsxCrypto.seal(request(password = "pw"))
        ZsxCrypto.open(file, password = null)
    }

    @Test(expected = ZsxTamperException::class)
    fun tamperedFileIsRejected() {
        val file = ZsxCrypto.seal(request(password = null))
        file[file.size - 1] = (file[file.size - 1] + 1).toByte()
        ZsxCrypto.open(file, password = null)
    }

    @Test(expected = ZsxTamperException::class)
    fun truncatedFileIsRejected() {
        val file = ZsxCrypto.seal(request(password = null))
        ZsxCrypto.open(file.copyOfRange(0, file.size - 8), password = null)
    }

    @Test(expected = ZsxTamperException::class)
    fun otherFilesAreRejected() {
        ZsxCrypto.peek("{\"outbounds\":[]}".toByteArray())
    }

    @Test(expected = ZsxVersionException::class)
    fun unknownVersionIsRejected() {
        val file = ZsxCrypto.seal(request(password = null))
        file[4] = 0x7F
        ZsxCrypto.open(file, password = null)
    }

    @Test
    fun filesFromOlderVersionsAreReportedAsLegacy() {
        val file = "ZSX1".toByteArray(Charsets.US_ASCII) + byteArrayOf(1) + ByteArray(64) { it.toByte() }
        assertThrows(ZsxLegacyException::class.java) { ZsxCrypto.peek(file) }
        assertThrows(ZsxLegacyException::class.java) { ZsxCrypto.open(file, password = null) }
    }

    @Test(expected = ZsxExpiredException::class)
    fun expiredFileIsRejected() {
        val file = ZsxCrypto.seal(request(password = null, expiresAt = System.currentTimeMillis() - 1000))
        ZsxCrypto.open(file, password = null)
    }

    @Test
    fun differentSealsProduceDifferentCiphertext() {
        val a = ZsxCrypto.seal(request(password = null))
        val b = ZsxCrypto.seal(request(password = null))
        assertFalse(a.contentEquals(b))
    }
}
