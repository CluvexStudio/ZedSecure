package dev.cluvex.zedsecure.domain.config

import kotlinx.serialization.Serializable

@Serializable
data class SshProfile(
    val host: String,
    val port: Int = 22,
    val username: String = "",

    val authType: String = AUTH_PASSWORD,
    val password: String = "",

    val privateKey: String = "",
    val keyPassphrase: String = "",
) {
    companion object {
        const val AUTH_PASSWORD = "password"
        const val AUTH_KEY = "key"
    }
}
