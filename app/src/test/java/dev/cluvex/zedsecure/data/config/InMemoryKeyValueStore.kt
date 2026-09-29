package dev.cluvex.zedsecure.data.config

import dev.cluvex.zedsecure.platform.KeyValueStore

class InMemoryKeyValueStore : KeyValueStore {
    private val map = mutableMapOf<String, String>()

    override fun getString(key: String): String? = map[key]

    override fun putString(key: String, value: String?) {
        if (value == null) map.remove(key) else map[key] = value
    }

    override fun getInt(key: String, default: Int): Int = map[key]?.toIntOrNull() ?: default

    override fun putInt(key: String, value: Int) {
        map[key] = value.toString()
    }

    override fun getBoolean(key: String, default: Boolean): Boolean =
        map[key]?.toBooleanStrictOrNull() ?: default

    override fun putBoolean(key: String, value: Boolean) {
        map[key] = value.toString()
    }

    override fun remove(key: String) {
        map.remove(key)
    }
}
