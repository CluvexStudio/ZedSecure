package dev.cluvex.zedsecure.domain.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

object SettingsBackup {
    const val FILE_NAME = "zedsecure-settings.json"
    private const val MARK = "zedsecureSettings"
    private const val VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
        prettyPrint = true
    }

    fun export(settings: AppSettings): String = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put(MARK, VERSION)
            put("settings", json.encodeToJsonElement(AppSettings.serializer(), settings))
        },
    )

    fun read(text: String): AppSettings? = runCatching {
        val root = json.parseToJsonElement(text).jsonObject
        val body = root["settings"]?.takeIf { root.containsKey(MARK) } ?: return null
        json.decodeFromJsonElement(AppSettings.serializer(), body)
    }.getOrNull()
}
