package dev.cluvex.zedsecure.platform

interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String?)
    fun getInt(key: String, default: Int): Int
    fun putInt(key: String, value: Int)
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun remove(key: String)
}
