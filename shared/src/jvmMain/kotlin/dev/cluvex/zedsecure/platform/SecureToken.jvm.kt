package dev.cluvex.zedsecure.platform

import java.security.SecureRandom

private val secureRandom by lazy { SecureRandom() }

internal actual fun secureRandomToken(length: Int): String = buildString(length) {
    repeat(length) { append(TOKEN_ALPHABET[secureRandom.nextInt(TOKEN_ALPHABET.length)]) }
}
