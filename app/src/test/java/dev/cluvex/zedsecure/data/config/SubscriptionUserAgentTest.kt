package dev.cluvex.zedsecure.data.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionUserAgentTest {
    @Test
    fun `default subscription user agent carries the v2rayNG token`() {
        assertTrue(
            "DEFAULT_UA must start with the v2rayNG token, was: ${ConfigRepository.DEFAULT_UA}",
            ConfigRepository.DEFAULT_UA.startsWith(ConfigRepository.V2RAYNG_UA_PREFIX),
        )
    }

    @Test
    fun `the token must lead the header, not merely appear in it`() {
        val disguised = "ZedSecure ${ConfigRepository.V2RAYNG_UA_PREFIX}3.0.0"
        assertTrue(disguised.contains(ConfigRepository.V2RAYNG_UA_PREFIX))
        assertTrue(
            "a token that only appears mid-string must not count as satisfying the contract",
            !disguised.startsWith(ConfigRepository.V2RAYNG_UA_PREFIX),
        )
    }

    @Test
    fun `default and alternate identities are distinct and the alternate is ours`() {
        assertNotEquals(ConfigRepository.DEFAULT_UA, ConfigRepository.ALT_UA)
        assertTrue(ConfigRepository.ALT_UA.startsWith("ZedSecure/"))
    }

    @Test
    fun `a per-subscription override wins over the default`() {
        val store = InMemoryKeyValueStore()
        val repo = ConfigRepository(store)
        val custom = "MyClient/9.9"

        val overridden = repo.addSubscription("with override", "https://example.invalid/a", custom)
        assertEquals(custom, overridden.userAgent)

        val plain = repo.addSubscription("no override", "https://example.invalid/b", "   ")
        assertEquals(null, plain.userAgent)
    }

    @Test
    fun `editing a subscription can set and clear the user agent override`() {
        val repo = ConfigRepository(InMemoryKeyValueStore())
        val sub = repo.addSubscription("s", "https://example.invalid/c", null)

        repo.editSubscription(sub.id, "renamed", "Other/1.0")
        repo.subscriptions.value.first { it.id == sub.id }.let {
            assertEquals("renamed", it.name)
            assertEquals("Other/1.0", it.userAgent)
        }

        repo.editSubscription(sub.id, "renamed", "")
        assertEquals(null, repo.subscriptions.value.first { it.id == sub.id }.userAgent)
    }
}
