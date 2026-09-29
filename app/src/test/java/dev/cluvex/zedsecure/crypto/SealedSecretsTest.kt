package dev.cluvex.zedsecure.crypto

import dev.cluvex.zedsecure.domain.ai.AiProvider
import dev.cluvex.zedsecure.domain.ai.AiSettings
import dev.cluvex.zedsecure.domain.config.Ikev2Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SealedSecretsTest {
    @Test
    fun `IKEv2 credentials are sealed and read back`() {
        val profile = Ikev2Profile(
            server = "vpn.example.com",
            password = Ikev2Profile.sealPassword("hunter2"),
            psk = Ikev2Profile.sealPsk("shared-secret"),
        )
        assertTrue(profile.password.startsWith(Ikev2Profile.SEALED_PREFIX))
        assertFalse(profile.password.contains("hunter2"))
        assertEquals("hunter2", profile.plainPassword())
        assertEquals("shared-secret", profile.plainPsk())
    }

    @Test
    fun `AI keys are sealed and read back`() {
        val settings = AiSettings().withKey(AiProvider.GEMINI, "test-api-key")
        val stored = settings.sealedKeys.getValue(AiProvider.GEMINI.name)
        assertTrue(stored.startsWith(AiSettings.SEALED_PREFIX))
        assertFalse(stored.contains("test-api-key"))
        assertEquals("test-api-key", settings.key(AiProvider.GEMINI))
    }

    @Test
    fun `values saved without sealing are read as they are`() {
        assertEquals("plain", Ikev2Profile(server = "vpn.example.com", password = "plain").plainPassword())
    }
}
