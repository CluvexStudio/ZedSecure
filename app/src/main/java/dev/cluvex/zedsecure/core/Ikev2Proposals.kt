package dev.cluvex.zedsecure.core

import android.net.ipsec.ike.ChildSaProposal
import android.net.ipsec.ike.IkeSaProposal
import android.net.ipsec.ike.SaProposal
import android.os.Build
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
object Ikev2Proposals {
    private val ENCRYPTION: Map<String, Pair<Int, Int>> = mapOf(
        "aes128" to (SaProposal.ENCRYPTION_ALGORITHM_AES_CBC to 128),
        "aes192" to (SaProposal.ENCRYPTION_ALGORITHM_AES_CBC to 192),
        "aes256" to (SaProposal.ENCRYPTION_ALGORITHM_AES_CBC to 256),
        "aes128ctr" to (SaProposal.ENCRYPTION_ALGORITHM_AES_CTR to 128),
        "aes192ctr" to (SaProposal.ENCRYPTION_ALGORITHM_AES_CTR to 192),
        "aes256ctr" to (SaProposal.ENCRYPTION_ALGORITHM_AES_CTR to 256),
        "aes128gcm128" to (SaProposal.ENCRYPTION_ALGORITHM_AES_GCM_16 to 128),
        "aes256gcm128" to (SaProposal.ENCRYPTION_ALGORITHM_AES_GCM_16 to 256),
        "aes128gcm16" to (SaProposal.ENCRYPTION_ALGORITHM_AES_GCM_16 to 128),
        "aes256gcm16" to (SaProposal.ENCRYPTION_ALGORITHM_AES_GCM_16 to 256),
        "aes128gcm12" to (SaProposal.ENCRYPTION_ALGORITHM_AES_GCM_12 to 128),
        "aes256gcm12" to (SaProposal.ENCRYPTION_ALGORITHM_AES_GCM_12 to 256),
        "aes128gcm8" to (SaProposal.ENCRYPTION_ALGORITHM_AES_GCM_8 to 128),
        "aes256gcm8" to (SaProposal.ENCRYPTION_ALGORITHM_AES_GCM_8 to 256),
        "chacha20poly1305" to (SaProposal.ENCRYPTION_ALGORITHM_CHACHA20_POLY1305 to 256),
    )

    private val INTEGRITY: Map<String, Int> = mapOf(
        "sha1" to SaProposal.INTEGRITY_ALGORITHM_HMAC_SHA1_96,
        "sha" to SaProposal.INTEGRITY_ALGORITHM_HMAC_SHA1_96,
        "sha256" to SaProposal.INTEGRITY_ALGORITHM_HMAC_SHA2_256_128,
        "sha384" to SaProposal.INTEGRITY_ALGORITHM_HMAC_SHA2_384_192,
        "sha512" to SaProposal.INTEGRITY_ALGORITHM_HMAC_SHA2_512_256,
        "aesxcbc" to SaProposal.INTEGRITY_ALGORITHM_AES_XCBC_96,
        "aescmac" to SaProposal.INTEGRITY_ALGORITHM_AES_CMAC_96,
    )

    private val PRF: Map<String, Int> = mapOf(
        "prfsha1" to SaProposal.PSEUDORANDOM_FUNCTION_HMAC_SHA1,
        "prfsha256" to SaProposal.PSEUDORANDOM_FUNCTION_SHA2_256,
        "prfsha384" to SaProposal.PSEUDORANDOM_FUNCTION_SHA2_384,
        "prfsha512" to SaProposal.PSEUDORANDOM_FUNCTION_SHA2_512,
        "prfaesxcbc" to SaProposal.PSEUDORANDOM_FUNCTION_AES128_XCBC,
        "prfaescmac" to SaProposal.PSEUDORANDOM_FUNCTION_AES128_CMAC,
    )

    private val DH: Map<String, Int> = mapOf(
        "modp1024" to SaProposal.DH_GROUP_1024_BIT_MODP,
        "modp1536" to SaProposal.DH_GROUP_1536_BIT_MODP,
        "modp2048" to SaProposal.DH_GROUP_2048_BIT_MODP,
        "modp3072" to SaProposal.DH_GROUP_3072_BIT_MODP,
        "modp4096" to SaProposal.DH_GROUP_4096_BIT_MODP,
        "curve25519" to SaProposal.DH_GROUP_CURVE_25519,
    )

    private val PRF_FOR_INTEGRITY: Map<Int, Int> = mapOf(
        SaProposal.INTEGRITY_ALGORITHM_HMAC_SHA1_96 to SaProposal.PSEUDORANDOM_FUNCTION_HMAC_SHA1,
        SaProposal.INTEGRITY_ALGORITHM_HMAC_SHA2_256_128 to SaProposal.PSEUDORANDOM_FUNCTION_SHA2_256,
        SaProposal.INTEGRITY_ALGORITHM_HMAC_SHA2_384_192 to SaProposal.PSEUDORANDOM_FUNCTION_SHA2_384,
        SaProposal.INTEGRITY_ALGORITHM_HMAC_SHA2_512_256 to SaProposal.PSEUDORANDOM_FUNCTION_SHA2_512,
        SaProposal.INTEGRITY_ALGORITHM_AES_XCBC_96 to SaProposal.PSEUDORANDOM_FUNCTION_AES128_XCBC,
        SaProposal.INTEGRITY_ALGORITHM_AES_CMAC_96 to SaProposal.PSEUDORANDOM_FUNCTION_AES128_CMAC,
    )

    private fun tokens(spec: String): List<String> =
        spec.lowercase().split('-', '_', ' ', ',').map { it.trim() }.filter { it.isNotEmpty() }

    fun isParsable(spec: String, child: Boolean): Boolean =
        spec.isBlank() || tokens(spec).all { classify(it, child) != null }

    private fun classify(token: String, child: Boolean): String? = when {
        ENCRYPTION.containsKey(token) -> "enc"
        INTEGRITY.containsKey(token) -> "int"
        DH.containsKey(token) -> "dh"
        !child && PRF.containsKey(token) -> "prf"

        child && token == "noesn" -> "ignore"
        else -> null
    }

    fun defaultIke(): IkeSaProposal = IkeSaProposal.Builder()
        .addEncryptionAlgorithm(SaProposal.ENCRYPTION_ALGORITHM_AES_CBC, 256)
        .addEncryptionAlgorithm(SaProposal.ENCRYPTION_ALGORITHM_AES_CBC, 128)
        .addIntegrityAlgorithm(SaProposal.INTEGRITY_ALGORITHM_HMAC_SHA2_512_256)
        .addIntegrityAlgorithm(SaProposal.INTEGRITY_ALGORITHM_HMAC_SHA2_256_128)
        .addPseudorandomFunction(SaProposal.PSEUDORANDOM_FUNCTION_SHA2_512)
        .addPseudorandomFunction(SaProposal.PSEUDORANDOM_FUNCTION_SHA2_256)
        .addDhGroup(SaProposal.DH_GROUP_2048_BIT_MODP)
        .addDhGroup(SaProposal.DH_GROUP_3072_BIT_MODP)
        .addDhGroup(SaProposal.DH_GROUP_CURVE_25519)
        .build()

    fun defaultChild(): ChildSaProposal = ChildSaProposal.Builder()
        .addEncryptionAlgorithm(SaProposal.ENCRYPTION_ALGORITHM_AES_CBC, 256)
        .addEncryptionAlgorithm(SaProposal.ENCRYPTION_ALGORITHM_AES_CBC, 128)
        .addIntegrityAlgorithm(SaProposal.INTEGRITY_ALGORITHM_HMAC_SHA2_512_256)
        .addIntegrityAlgorithm(SaProposal.INTEGRITY_ALGORITHM_HMAC_SHA2_256_128)
        .build()

    fun parseIke(spec: String): IkeSaProposal? {
        if (spec.isBlank()) return null
        val toks = tokens(spec)
        val b = IkeSaProposal.Builder()
        var enc = false
        var dh = false
        var prf: Int? = null
        var integrity: Int? = null
        for (t in toks) {
            when {
                ENCRYPTION.containsKey(t) -> ENCRYPTION.getValue(t).let { (alg, len) ->
                    b.addEncryptionAlgorithm(alg, len); enc = true
                }
                INTEGRITY.containsKey(t) -> INTEGRITY.getValue(t).let {
                    b.addIntegrityAlgorithm(it); integrity = it
                }
                DH.containsKey(t) -> { b.addDhGroup(DH.getValue(t)); dh = true }
                PRF.containsKey(t) -> { b.addPseudorandomFunction(PRF.getValue(t)); prf = PRF.getValue(t) }
                else -> return null
            }
        }

        if (prf == null) {
            val derived = integrity?.let { PRF_FOR_INTEGRITY[it] } ?: return null
            b.addPseudorandomFunction(derived)
        }
        if (!enc || !dh) return null
        return runCatching { b.build() }.getOrNull()
    }

    fun parseChild(spec: String): ChildSaProposal? {
        if (spec.isBlank()) return null
        val b = ChildSaProposal.Builder()
        var enc = false
        for (t in tokens(spec)) {
            when {
                ENCRYPTION.containsKey(t) -> ENCRYPTION.getValue(t).let { (alg, len) ->
                    b.addEncryptionAlgorithm(alg, len); enc = true
                }
                INTEGRITY.containsKey(t) -> b.addIntegrityAlgorithm(INTEGRITY.getValue(t))
                DH.containsKey(t) -> b.addDhGroup(DH.getValue(t))
                t == "noesn" -> Unit
                else -> return null
            }
        }
        if (!enc) return null
        return runCatching { b.build() }.getOrNull()
    }
}
