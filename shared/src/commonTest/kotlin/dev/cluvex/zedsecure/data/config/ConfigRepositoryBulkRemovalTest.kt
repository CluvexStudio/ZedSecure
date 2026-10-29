package dev.cluvex.zedsecure.data.config

import dev.cluvex.zedsecure.platform.KeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfigRepositoryBulkRemovalTest {
    @Test
    fun removingDuplicateProfileDoesNotLeaveBrokenProxyChain() {
        val repository = ConfigRepository(MemoryKeyValueStore())
        val duplicateLink =
            "vless://11111111-1111-4111-8111-111111111111@example.com:443?encryption=none&security=tls&type=ws&path=%2F#duplicate"
        val otherLink =
            "vless://22222222-2222-4222-8222-222222222222@example.net:443?encryption=none&security=tls&type=ws&path=%2F#other"

        assertEquals(1, repository.importText(duplicateLink, "subscription-a").getOrThrow())
        assertEquals(1, repository.importText(duplicateLink, "subscription-b").getOrThrow())
        assertEquals(1, repository.importText(otherLink, "subscription-c").getOrThrow())

        val duplicate = repository.profiles.value.first { it.subscriptionId == "subscription-b" }
        val other = repository.profiles.value.first { it.subscriptionId == "subscription-c" }
        repository.addProxyChain(listOf(duplicate.id, other.id), "test chain")

        assertEquals(1, repository.removeDuplicates())
        val remaining = repository.profiles.value
        val remainingIds = remaining.map { it.id }.toSet()

        assertEquals(2, remaining.count { !it.isProxyChain })
        assertFalse(remaining.any { it.isProxyChain && it.proxyChainSettings()?.any { id -> id !in remainingIds } == true })
        assertFalse(remaining.any { it.isProxyChain }, "A chain with fewer than two remaining members should be removed")
        assertTrue(remaining.any { it.subscriptionId == "subscription-a" })
    }

    private class MemoryKeyValueStore : KeyValueStore {
        private val strings = mutableMapOf<String, String>()
        private val ints = mutableMapOf<String, Int>()
        private val booleans = mutableMapOf<String, Boolean>()

        override fun getString(key: String): String? = strings[key]
        override fun putString(key: String, value: String?) {
            if (value == null) strings.remove(key) else strings[key] = value
        }
        override fun getInt(key: String, default: Int): Int = ints[key] ?: default
        override fun putInt(key: String, value: Int) { ints[key] = value }
        override fun getBoolean(key: String, default: Boolean): Boolean = booleans[key] ?: default
        override fun putBoolean(key: String, value: Boolean) { booleans[key] = value }
        override fun remove(key: String) {
            strings.remove(key)
            ints.remove(key)
            booleans.remove(key)
        }
    }
}
