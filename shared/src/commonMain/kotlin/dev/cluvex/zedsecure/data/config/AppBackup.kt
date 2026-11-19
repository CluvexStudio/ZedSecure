package dev.cluvex.zedsecure.data.config

import dev.cluvex.zedsecure.domain.config.Subscription
import dev.cluvex.zedsecure.domain.config.VpnProfile
import dev.cluvex.zedsecure.domain.model.AppSettings
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

object AppBackup {
    const val FILE_NAME = "zedsecure-backup.json"
    private const val MARK = "zedsecureBackup"
    private const val VERSION = 1

    class Contents(
        val settings: AppSettings?,
        val subscriptions: List<Subscription>,
        val profiles: List<VpnProfile>,
        val activeId: String?,
    )

    @Serializable
    private class Payload(
        @SerialName(MARK) val version: Int = 0,
        val settings: JsonElement? = null,
        val subscriptions: List<Subscription> = emptyList(),
        val profiles: List<VpnProfile> = emptyList(),
        val activeId: String? = null,
    )

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val settingsJson = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    fun export(
        settings: AppSettings,
        subscriptions: List<Subscription>,
        profiles: List<VpnProfile>,
        activeId: String?,
    ): String = buildString {
        append("{\"").append(MARK).append("\":").append(VERSION)
        append(",\"settings\":").append(settingsJson.encodeToString(AppSettings.serializer(), settings))
        append(",\"subscriptions\":").append(json.encodeToString(ListSerializer(Subscription.serializer()), subscriptions))
        append(",\"profiles\":").append(json.encodeToString(ListSerializer(VpnProfile.serializer()), profiles))
        if (activeId != null) append(",\"activeId\":").append(json.encodeToString(String.serializer(), activeId))
        append('}')
    }

    fun read(text: String): Contents? {
        if (!text.contains("\"$MARK\"")) return null
        val payload = runCatching { json.decodeFromString(Payload.serializer(), text) }.getOrNull()
        if (payload != null) {
            if (payload.version <= 0) return null
            return Contents(settingsOf(payload.settings), payload.subscriptions, payload.profiles, payload.activeId)
        }
        return runCatching {
            val root = json.parseToJsonElement(text).jsonObject
            if (((root[MARK] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0) <= 0) return null
            Contents(
                settings = settingsOf(root["settings"]),
                subscriptions = itemsOf(root["subscriptions"], Subscription.serializer()),
                profiles = itemsOf(root["profiles"], VpnProfile.serializer()),
                activeId = (root["activeId"] as? JsonPrimitive)?.contentOrNull,
            )
        }.getOrNull()
    }

    private fun settingsOf(element: JsonElement?): AppSettings? = element?.let {
        runCatching { settingsJson.decodeFromJsonElement(AppSettings.serializer(), it) }.getOrNull()
    }

    private fun <T> itemsOf(element: JsonElement?, serializer: KSerializer<T>): List<T> =
        (element as? JsonArray).orEmpty().mapNotNull { runCatching { json.decodeFromJsonElement(serializer, it) }.getOrNull() }
}
