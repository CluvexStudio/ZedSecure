@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package dev.cluvex.zedsecure.domain.config

import dev.cluvex.zedsecure.crypto.ZsxCrypto
import dev.cluvex.zedsecure.crypto.ZsxSealRequest
import kotlin.io.encoding.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Ikev2Profile(

    val server: String = "",

    val remoteId: String = "",

    val localId: String = "",

    val authType: Ikev2Auth = Ikev2Auth.EAP_MSCHAPV2,

    val username: String = "",

    val password: String = "",

    val psk: String = "",

    val caCertPem: String = "",

    val userCertAlias: String = "",

    val ikeProposal: String = "",

    val espProposal: String = "",

    val mtu: Int = 1280,
) {
    val effectiveRemoteId: String get() = remoteId.ifBlank { server }

    val effectiveAuth: Ikev2Auth
        get() = if (authType == Ikev2Auth.EAP_TLS) Ikev2Auth.CERTIFICATE else authType

    val isValid: Boolean
        get() = server.isNotBlank() && when (effectiveAuth) {
            Ikev2Auth.EAP_MSCHAPV2 -> username.isNotBlank() && password.isNotBlank()
            Ikev2Auth.CERTIFICATE -> userCertAlias.isNotBlank()
            Ikev2Auth.EAP_TLS -> false
            Ikev2Auth.PSK -> psk.isNotBlank()
        }

    fun plainPassword(): String = openSecret(password)

    fun plainPsk(): String = openSecret(psk)

    companion object {
        const val SEALED_PREFIX = "zsx:"

        fun sealPassword(plain: String): String = sealSecret(plain)

        fun sealPsk(plain: String): String = sealSecret(plain)

        private fun sealSecret(plain: String): String {
            if (plain.isBlank()) return ""
            if (plain.startsWith(SEALED_PREFIX)) return plain
            return runCatching {
                val sealed = ZsxCrypto.seal(
                    ZsxSealRequest(
                        configPayload = plain,
                        nameEn = "", nameFa = "", note = "", expiresAt = null, password = null,
                    ),
                )
                SEALED_PREFIX + Base64.encode(sealed)
            }.getOrDefault(plain)
        }

        private fun openSecret(stored: String): String {
            if (!stored.startsWith(SEALED_PREFIX)) return stored
            val b64 = stored.removePrefix(SEALED_PREFIX)
            return runCatching { ZsxCrypto.open(Base64.decode(b64), password = null) }.getOrDefault("")
        }
    }
}

@Serializable
enum class Ikev2Auth {
    @SerialName("eap_mschapv2")
    EAP_MSCHAPV2,

    @SerialName("certificate")
    CERTIFICATE,

    @SerialName("eap_tls")
    EAP_TLS,

    @SerialName("psk")
    PSK,
}
