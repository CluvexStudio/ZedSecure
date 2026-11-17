package dev.cluvex.zedsecure.core

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts Aether's persistent WARP identity and WireGuard private-key files while stopped. */
internal object AetherIdentityVault {
    private const val KEY_ALIAS = "zx.aether.identity.v1"
    private const val SEALED_SUFFIX = ".sealed"
    private const val VERSION = 1
    private val identityName = Regex("^aether(?:[A-Za-z0-9._-]*)\\.toml(?:\\.corrupt)?$")

    fun unsealInto(dir: File) {
        if (!dir.exists()) return
        val blobs = dir.listFiles { file ->
            file.isFile && file.name.endsWith(SEALED_SUFFIX) &&
                identityName.matches(file.name.removeSuffix(SEALED_SUFFIX))
        } ?: return
        blobs.forEach { blob ->
            val target = File(dir, blob.name.removeSuffix(SEALED_SUFFIX))
            if (target.exists()) return@forEach
            val plain = runCatching { decrypt(blob.readBytes(), target.name) }.getOrNull()
            if (plain == null) {
                LogBus.append("W/Aether: sealed identity could not be opened on this device")
                return@forEach
            }
            runCatching {
                target.writeBytes(plain)
                restrict(target)
            }.onFailure {
                LogBus.append("W/Aether: could not restore sealed identity (" + it.javaClass.simpleName + ")")
            }
        }
    }

    fun sealAndShred(dir: File) {
        if (!dir.exists()) return
        val files = dir.listFiles { file -> file.isFile && identityName.matches(file.name) } ?: return
        files.forEach { source ->
            val encrypted = runCatching { encrypt(source.readBytes(), source.name) }.getOrNull()
            if (encrypted == null) {
                LogBus.append("W/Aether: identity remains in app-private storage because Keystore sealing failed")
                return@forEach
            }
            val target = File(dir, source.name + SEALED_SUFFIX)
            val temp = File(dir, source.name + SEALED_SUFFIX + ".tmp")
            val saved = runCatching {
                temp.writeBytes(encrypted)
                restrict(temp)
                if (target.exists() && !target.delete()) error("cannot replace sealed identity")
                if (!temp.renameTo(target)) {
                    target.writeBytes(encrypted)
                    temp.delete()
                }
                target.isFile
            }.getOrDefault(false)
            if (saved) {
                runCatching { source.delete() }
            } else {
                runCatching { temp.delete() }
                LogBus.append("W/Aether: could not seal an identity file; plaintext was retained")
            }
        }
    }

    private fun encrypt(plain: ByteArray, filename: String): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(filename.toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(plain)
        val iv = cipher.iv
        return byteArrayOf(VERSION.toByte(), iv.size.toByte()) + iv + encrypted
    }

    private fun decrypt(blob: ByteArray, filename: String): ByteArray {
        require(blob.size > 2 && blob[0].toInt() == VERSION) { "unsupported identity blob" }
        val ivSize = blob[1].toInt() and 0xff
        require(ivSize in 12..16 && blob.size > 2 + ivSize) { "invalid identity blob" }
        val iv = blob.copyOfRange(2, 2 + ivSize)
        val encrypted = blob.copyOfRange(2 + ivSize, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        cipher.updateAAD(filename.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(encrypted)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun restrict(file: File) {
        file.setReadable(false, false)
        file.setWritable(false, false)
        file.setReadable(true, true)
        file.setWritable(true, true)
    }
}
