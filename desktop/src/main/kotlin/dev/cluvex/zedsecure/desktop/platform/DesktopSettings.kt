package dev.cluvex.zedsecure.desktop.platform

import dev.cluvex.zedsecure.domain.model.AppSettings
import dev.cluvex.zedsecure.domain.model.RenderingMode
import dev.cluvex.zedsecure.domain.model.RunMode
import dev.cluvex.zedsecure.platform.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

class DesktopSettings(private val store: KeyValueStore) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        runCatching { store.putString(KEY, json.encodeToString(AppSettings.serializer(), next)) }
    }

    private fun load(): AppSettings {
        val raw = store.getString(KEY)
        val stored = raw?.let {
            runCatching { json.decodeFromString(AppSettings.serializer(), it) }.getOrNull()
        }?.let { settings ->
            if (settings.renderingMode == RenderingMode.Auto && hadSoftwareRendering(raw)) {
                settings.copy(renderingMode = RenderingMode.Software)
            } else {
                settings
            }
        }
        if (store.getString(MODE_KEY) != MODE_VERSION) {
            val migrated = (stored ?: AppSettings()).copy(runMode = RunMode.SystemProxy)
            runCatching {
                store.putString(KEY, json.encodeToString(AppSettings.serializer(), migrated))
                store.putString(MODE_KEY, MODE_VERSION)
            }
            return migrated
        }
        return stored ?: AppSettings(runMode = RunMode.SystemProxy)
    }

    private fun hadSoftwareRendering(raw: String): Boolean = runCatching {
        (json.parseToJsonElement(raw) as? JsonObject)?.get(LEGACY_SOFTWARE_RENDERING)?.jsonPrimitive?.booleanOrNull == true
    }.getOrDefault(false)

    private companion object {
        const val LEGACY_SOFTWARE_RENDERING = "softwareRendering"
        const val KEY = "settings"
        const val MODE_KEY = "run_mode_version"
        const val MODE_VERSION = "2"
    }
}
