package dev.cluvex.zedsecure.crypto

import dev.cluvex.zedsecure.platform.currentTimeMillis

data class ZsxMetadata(
    val passwordProtected: Boolean,
    val nameEn: String,
    val nameFa: String,
    val note: String,
    val createdAt: Long,
    val expiresAt: Long?,
) {
    val isExpired: Boolean
        get() = expiresAt != null && currentTimeMillis() > expiresAt
}

data class ZsxSealRequest(
    val configPayload: String,
    val nameEn: String,
    val nameFa: String,
    val note: String,
    val expiresAt: Long?,
    val password: String?,
)

sealed class ZsxException(message: String) : Exception(message)
class ZsxVersionException : ZsxException("Unsupported .zsx version")
class ZsxTamperException : ZsxException("File is corrupt or has been tampered with")
class ZsxWrongPasswordException : ZsxException("Incorrect password")
class ZsxExpiredException : ZsxException("This locked config has expired")
class ZsxPasswordRequiredException : ZsxException("A password is required")
class ZsxLegacyException : ZsxException("This locked config was made by an older version of ZedSecure")
