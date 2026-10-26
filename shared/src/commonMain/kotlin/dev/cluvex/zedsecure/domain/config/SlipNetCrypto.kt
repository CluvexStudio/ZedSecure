package dev.cluvex.zedsecure.domain.config

/**
 * Platform crypto for SlipNet's published export envelope formats.
 * Passwords and file contents are processed locally and are never uploaded.
 */
expect object SlipNetCrypto {
    fun decryptLegacy(payload: String): String
    fun decryptBundle(payload: String, password: String): String
}
