package dev.cluvex.zedsecure.data.config

import dev.cluvex.zedsecure.platform.KeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LargeValueStoreTest {
    private class SplittingStore : KeyValueStore {
        val small = mutableMapOf<String, String>()
        val files = mutableMapOf<String, String>()
        private val ints = mutableMapOf<String, Int>()
        private val bools = mutableMapOf<String, Boolean>()

        override fun getString(key: String): String? = files[key] ?: small[key]

        override fun putString(key: String, value: String?) {
            if (value == null) { remove(key); return }
            if (value.length > LARGE) {
                files[key] = value
                small.remove(key)
            } else {
                files.remove(key)
                small[key] = value
            }
        }

        override fun getInt(key: String, default: Int) = ints[key] ?: default
        override fun putInt(key: String, value: Int) { ints[key] = value }
        override fun getBoolean(key: String, default: Boolean) = bools[key] ?: default
        override fun putBoolean(key: String, value: Boolean) { bools[key] = value }
        override fun remove(key: String) { small.remove(key); files.remove(key) }

        companion object { const val LARGE = 16 * 1024 }
    }

    private fun bigConfig(marker: String) = """
        {
          "remarks": "$marker",
          "outbounds": [
            {"protocol": "vless",
             "settings": {"vnext": [{"address": "$marker.example", "port": 443,
               "users": [{"id": "11111111-2222-3333-4444-555555555555", "encryption": "none"}]}]},
             "streamSettings": {"network": "tcp", "security": "tls",
               "tlsSettings": {"certificates": [{"certificate": "${"A".repeat(4000)}"}]}}},
            {"protocol": "freedom", "tag": "direct"}
          ]
        }
    """.trimIndent()

    @Test
    fun `a library of large configs survives being saved and reloaded`() {
        val store = SplittingStore()
        val repo = ConfigRepository(store)
        repeat(12) { i -> repo.addRawJson(bigConfig("server$i")).getOrThrow() }

        assertEquals(12, repo.profiles.value.size)

        assertTrue("the library outgrows preferences", store.files.isNotEmpty())
        assertTrue(store.getString("profiles")!!.length > SplittingStore.LARGE)

        val reopened = ConfigRepository(store)
        assertEquals(12, reopened.profiles.value.size)
        assertEquals(
            repo.profiles.value.map { it.name },
            reopened.profiles.value.map { it.name },
        )
    }

    @Test
    fun `a value that shrinks below the threshold stops being a file`() {
        val store = SplittingStore()
        store.putString("profiles", "x".repeat(SplittingStore.LARGE + 1))
        assertNotNull(store.files["profiles"])
        assertNull(store.small["profiles"])

        store.putString("profiles", "[]")
        assertNull("the stale file must not shadow the new value", store.files["profiles"])
        assertEquals("[]", store.getString("profiles"))
    }

    @Test
    fun `small settings stay where they were`() {
        val store = SplittingStore()
        store.putString("active_id", "abc-123")

        assertEquals("abc-123", store.small["active_id"])
        assertTrue(store.files.isEmpty())
        assertEquals("abc-123", store.getString("active_id"))
    }
}
