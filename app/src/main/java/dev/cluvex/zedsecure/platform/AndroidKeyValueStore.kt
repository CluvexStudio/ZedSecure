package dev.cluvex.zedsecure.platform

import android.content.Context
import dev.cluvex.zedsecure.core.AppLog as Log
import dev.cluvex.zedsecure.platform.KeyValueStore
import java.io.File

class AndroidKeyValueStore(context: Context, prefsName: String) : KeyValueStore {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val dir = File(app.filesDir, "kv/$prefsName")

    override fun getString(key: String): String? {
        fileOf(key).takeIf { it.isFile }?.let { file ->
            runCatching { return file.readText() }
                .onFailure { Log.w(TAG, "could not read $key from its file", it) }
        }
        return prefs.getString(key, null)
    }

    override fun putString(key: String, value: String?) {
        if (value == null) {
            remove(key)
            return
        }
        if (value.length > LARGE) {
            if (writeFile(key, value)) {
                if (prefs.contains(key)) prefs.edit().remove(key).apply()
                return
            }
        }
        fileOf(key).takeIf { it.exists() }?.delete()
        prefs.edit().putString(key, value).apply()
    }

    override fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)
    override fun putInt(key: String, value: Int) { prefs.edit().putInt(key, value).apply() }
    override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    override fun putBoolean(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }

    override fun remove(key: String) {
        fileOf(key).takeIf { it.exists() }?.delete()
        prefs.edit().remove(key).apply()
    }

    private fun writeFile(key: String, value: String): Boolean = runCatching {
        if (!dir.isDirectory) dir.mkdirs()
        val target = fileOf(key)
        val tmp = File(dir, "${target.name}.tmp")
        tmp.writeText(value)
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) {
                tmp.delete()
                return@runCatching false
            }
        }
        true
    }.getOrElse {
        Log.w(TAG, "could not write $key to a file", it)
        false
    }

    private fun fileOf(key: String) = File(dir, key.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".val")

    private companion object {
        const val TAG = "AndroidKeyValueStore"

        const val LARGE = 16 * 1024
    }
}
