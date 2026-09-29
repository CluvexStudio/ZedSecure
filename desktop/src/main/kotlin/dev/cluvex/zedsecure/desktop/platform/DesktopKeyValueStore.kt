package dev.cluvex.zedsecure.desktop.platform

import dev.cluvex.zedsecure.platform.KeyValueStore
import java.io.File
import java.util.Properties

class DesktopKeyValueStore(name: String) : KeyValueStore {
    private val file: File = configDir().resolve("$name.properties")
    private val props = Properties().apply {
        runCatching { if (file.exists()) file.inputStream().use { load(it) } }
    }

    private fun persist() = runCatching {
        file.parentFile?.mkdirs()

        val tmp = java.io.File(file.parentFile, file.name + ".tmp")
        tmp.outputStream().use { props.store(it, "ZedSecure") }
        try {
            java.nio.file.Files.move(
                tmp.toPath(), file.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            java.nio.file.Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
    }

    override fun getString(key: String): String? = props.getProperty(key)
    override fun putString(key: String, value: String?) {
        if (value == null) props.remove(key) else props.setProperty(key, value)
        persist()
    }
    override fun getInt(key: String, default: Int): Int = props.getProperty(key)?.toIntOrNull() ?: default
    override fun putInt(key: String, value: Int) { props.setProperty(key, value.toString()); persist() }
    override fun getBoolean(key: String, default: Boolean): Boolean =
        props.getProperty(key)?.toBooleanStrictOrNull() ?: default
    override fun putBoolean(key: String, value: Boolean) { props.setProperty(key, value.toString()); persist() }
    override fun remove(key: String) { props.remove(key); persist() }

    private companion object {
        fun configDir(): File {
            val os = System.getProperty("os.name").lowercase()
            val home = System.getProperty("user.home")
            return when {
                os.contains("win") -> File(System.getenv("APPDATA") ?: "$home\\AppData\\Roaming", "ZedSecure")
                os.contains("mac") -> File("$home/Library/Application Support/ZedSecure")
                else -> File(System.getenv("XDG_CONFIG_HOME") ?: "$home/.config", "zedsecure")
            }
        }
    }
}
