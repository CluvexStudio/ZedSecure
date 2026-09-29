package dev.cluvex.zedsecure.domain.ai

import dev.cluvex.zedsecure.platform.HttpTextResponse
import dev.cluvex.zedsecure.platform.httpJson
import dev.cluvex.zedsecure.core.VpnManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

interface AiClient {
    val provider: AiProvider

    suspend fun models(): Result<List<AiModel>>

    suspend fun chat(model: String, system: String, history: List<AiTurn>, tools: List<AiTool>): AiReply

    companion object {
        fun of(provider: AiProvider, apiKey: String): AiClient = when (provider) {
            AiProvider.GEMINI -> GeminiClient(apiKey)
            AiProvider.CLAUDE -> ClaudeClient(apiKey)
            AiProvider.OPENAI -> OpenAiClient(apiKey)
        }
    }
}

private suspend fun aiHttp(method: String, url: String, headers: Map<String, String>, body: String?) =
    httpJson(method, url, headers, body, socksPort = VpnManager.activeSocksPort)

internal val aiJson = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = false }

internal fun schemaOf(tool: AiTool): JsonObject =
    runCatching { aiJson.parseToJsonElement(tool.parametersJson).jsonObject }
        .getOrElse { buildJsonObject { put("type", "object"); putJsonObject("properties") {} } }

internal fun failureOf(response: HttpTextResponse): AiReply.Failed {
    val body = runCatching { aiJson.parseToJsonElement(response.body).jsonObject }.getOrNull()
    val message = body?.get("error")?.let { err ->
        (err as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull
            ?: (err as? JsonPrimitive)?.contentOrNull
    }
        ?: body?.get("message")?.jsonPrimitive?.contentOrNull
        ?: response.body.take(300).ifBlank { "HTTP ${response.code}" }
    return AiReply.Failed(message, response.code)
}

private class GeminiClient(private val apiKey: String) : AiClient {
    override val provider = AiProvider.GEMINI

    private val base = "https://generativelanguage.googleapis.com/v1beta"
    private fun headers() = mapOf("x-goog-api-key" to apiKey, "Content-Type" to "application/json")

    override suspend fun models(): Result<List<AiModel>> = runCatching {
        val response = aiHttp("GET", "$base/models?pageSize=1000", headers(), null)
        if (response.code !in 200..299) throw IllegalStateException(failureOf(response).message)
        aiJson.parseToJsonElement(response.body).jsonObject["models"]?.jsonArray.orEmpty()
            .mapNotNull { it as? JsonObject }

            .filter { m ->
                m["supportedGenerationMethods"]?.jsonArray.orEmpty()
                    .any { it.jsonPrimitive.contentOrNull == "generateContent" }
            }
            .map { m ->
                val name = m["name"]?.jsonPrimitive?.contentOrNull.orEmpty().removePrefix("models/")
                AiModel(
                    id = name,
                    label = m["displayName"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: name,
                    contextTokens = m["inputTokenLimit"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0,
                )
            }
            .filter { it.id.isNotBlank() }
    }

    override suspend fun chat(model: String, system: String, history: List<AiTurn>, tools: List<AiTool>): AiReply {
        val body = buildJsonObject {
            if (system.isNotBlank()) {
                putJsonObject("systemInstruction") { putJsonArray("parts") { add(textPart(system)) } }
            }
            putJsonArray("contents") { history.forEach { turn -> contentsOf(turn).forEach { add(it) } } }
            if (tools.isNotEmpty()) {
                putJsonArray("tools") {
                    addJsonObject {
                        putJsonArray("functionDeclarations") {
                            tools.forEach { t ->
                                addJsonObject {
                                    put("name", t.name)
                                    put("description", t.description)
                                    put("parameters", schemaOf(t))
                                }
                            }
                        }
                    }
                }
            }
        }
        val response = aiHttp("POST", "$base/models/$model:generateContent", headers(), body.toString())
        if (response.code !in 200..299) return failureOf(response)

        val parsed = runCatching { aiJson.parseToJsonElement(response.body).jsonObject }.getOrNull()
            ?: return AiReply.Failed("unreadable response", response.code)
        val parts = parsed["candidates"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray.orEmpty()

        val text = StringBuilder()
        val calls = mutableListOf<AiToolCall>()
        parts.mapNotNull { it as? JsonObject }.forEachIndexed { i, part ->
            part["text"]?.jsonPrimitive?.contentOrNull?.let { text.append(it) }
            (part["functionCall"] as? JsonObject)?.let { fc ->
                val name = fc["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val args = (fc["args"] as? JsonObject)?.toString() ?: "{}"
                if (name.isNotBlank()) calls += AiToolCall(id = "$name#$i", name = name, argumentsJson = args)
            }
        }
        if (text.isBlank() && calls.isEmpty()) {
            val reason = parsed["candidates"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("finishReason")?.jsonPrimitive?.contentOrNull
            if (reason != null && reason != "STOP") return AiReply.Failed("stopped: $reason", response.code)
        }
        return AiReply.Ok(AiTurn(AiRole.ASSISTANT, text.toString(), calls))
    }

    private fun contentsOf(turn: AiTurn): List<JsonObject> = when (turn.role) {
        AiRole.USER -> listOf(
            buildJsonObject {
                put("role", "user")
                putJsonArray("parts") { add(textPart(turn.text)) }
            },
        )

        AiRole.ASSISTANT -> listOf(
            buildJsonObject {
                put("role", "model")
                putJsonArray("parts") {
                    if (turn.text.isNotBlank()) add(textPart(turn.text))
                    turn.calls.forEach { c ->
                        addJsonObject {
                            putJsonObject("functionCall") {
                                put("name", c.name)
                                put("args", runCatching { aiJson.parseToJsonElement(c.argumentsJson).jsonObject }
                                    .getOrElse { buildJsonObject {} })
                            }
                        }
                    }
                }
            },
        )

        AiRole.TOOL -> listOf(
            buildJsonObject {
                put("role", "user")
                putJsonArray("parts") {
                    turn.results.forEach { r ->
                        addJsonObject {
                            putJsonObject("functionResponse") {
                                put("name", r.name)

                                putJsonObject("response") { put("result", r.content) }
                            }
                        }
                    }
                }
            },
        )
    }

    private fun textPart(text: String) = buildJsonObject { put("text", text) }
}

private class ClaudeClient(private val apiKey: String) : AiClient {
    override val provider = AiProvider.CLAUDE

    private val base = "https://api.anthropic.com/v1"
    private fun headers() = mapOf(
        "x-api-key" to apiKey,
        "anthropic-version" to "2023-06-01",
        "Content-Type" to "application/json",
    )

    override suspend fun models(): Result<List<AiModel>> = runCatching {
        val response = aiHttp("GET", "$base/models?limit=200", headers(), null)
        if (response.code !in 200..299) throw IllegalStateException(failureOf(response).message)
        aiJson.parseToJsonElement(response.body).jsonObject["data"]?.jsonArray.orEmpty()
            .mapNotNull { it as? JsonObject }
            .map { m ->
                val id = m["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                AiModel(id, m["display_name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: id)
            }
            .filter { it.id.isNotBlank() }
    }

    override suspend fun chat(model: String, system: String, history: List<AiTurn>, tools: List<AiTool>): AiReply {
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", 8192)
            if (system.isNotBlank()) put("system", system)
            putJsonArray("messages") { history.forEach { add(messageOf(it)) } }
            if (tools.isNotEmpty()) {
                putJsonArray("tools") {
                    tools.forEach { t ->
                        addJsonObject {
                            put("name", t.name)
                            put("description", t.description)
                            put("input_schema", schemaOf(t))
                        }
                    }
                }
            }
        }
        val response = aiHttp("POST", "$base/messages", headers(), body.toString())
        if (response.code !in 200..299) return failureOf(response)

        val parsed = runCatching { aiJson.parseToJsonElement(response.body).jsonObject }.getOrNull()
            ?: return AiReply.Failed("unreadable response", response.code)
        val text = StringBuilder()
        val calls = mutableListOf<AiToolCall>()
        parsed["content"]?.jsonArray.orEmpty().mapNotNull { it as? JsonObject }.forEach { block ->
            when (block["type"]?.jsonPrimitive?.contentOrNull) {
                "text" -> block["text"]?.jsonPrimitive?.contentOrNull?.let { text.append(it) }
                "tool_use" -> calls += AiToolCall(
                    id = block["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    name = block["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    argumentsJson = (block["input"] as? JsonObject)?.toString() ?: "{}",
                )
            }
        }
        return AiReply.Ok(AiTurn(AiRole.ASSISTANT, text.toString(), calls.filter { it.name.isNotBlank() }))
    }

    private fun messageOf(turn: AiTurn): JsonObject = when (turn.role) {
        AiRole.USER -> buildJsonObject {
            put("role", "user")
            put("content", turn.text)
        }

        AiRole.ASSISTANT -> buildJsonObject {
            put("role", "assistant")
            putJsonArray("content") {
                if (turn.text.isNotBlank()) {
                    addJsonObject { put("type", "text"); put("text", turn.text) }
                }
                turn.calls.forEach { c ->
                    addJsonObject {
                        put("type", "tool_use")
                        put("id", c.id)
                        put("name", c.name)
                        put("input", runCatching { aiJson.parseToJsonElement(c.argumentsJson).jsonObject }
                            .getOrElse { buildJsonObject {} })
                    }
                }
            }
        }

        AiRole.TOOL -> buildJsonObject {
            put("role", "user")
            putJsonArray("content") {
                turn.results.forEach { r ->
                    addJsonObject {
                        put("type", "tool_result")
                        put("tool_use_id", r.id)
                        put("content", r.content)
                        if (r.failed) put("is_error", true)
                    }
                }
            }
        }
    }
}

private class OpenAiClient(private val apiKey: String) : AiClient {
    override val provider = AiProvider.OPENAI

    private val base = "https://api.openai.com/v1"
    private fun headers() = mapOf(
        "Authorization" to "Bearer $apiKey",
        "Content-Type" to "application/json",
    )

    override suspend fun models(): Result<List<AiModel>> = runCatching {
        val response = aiHttp("GET", "$base/models", headers(), null)
        if (response.code !in 200..299) throw IllegalStateException(failureOf(response).message)
        aiJson.parseToJsonElement(response.body).jsonObject["data"]?.jsonArray.orEmpty()
            .mapNotNull { it as? JsonObject }
            .mapNotNull { it["id"]?.jsonPrimitive?.contentOrNull }

            .filterNot { id ->
                listOf("embedding", "tts", "whisper", "dall-e", "moderation", "audio", "image", "realtime", "search")
                    .any { id.contains(it, ignoreCase = true) }
            }
            .sorted()
            .map { AiModel(it) }
    }

    override suspend fun chat(model: String, system: String, history: List<AiTurn>, tools: List<AiTool>): AiReply {
        val body = buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                if (system.isNotBlank()) {
                    addJsonObject { put("role", "system"); put("content", system) }
                }
                history.forEach { turn -> messagesOf(turn).forEach { add(it) } }
            }
            if (tools.isNotEmpty()) {
                putJsonArray("tools") {
                    tools.forEach { t ->
                        addJsonObject {
                            put("type", "function")
                            putJsonObject("function") {
                                put("name", t.name)
                                put("description", t.description)
                                put("parameters", schemaOf(t))
                            }
                        }
                    }
                }
                put("tool_choice", "auto")
            }
        }
        val response = aiHttp("POST", "$base/chat/completions", headers(), body.toString())
        if (response.code !in 200..299) return failureOf(response)

        val parsed = runCatching { aiJson.parseToJsonElement(response.body).jsonObject }.getOrNull()
            ?: return AiReply.Failed("unreadable response", response.code)
        val message = parsed["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
            ?: return AiReply.Failed("empty response", response.code)
        val text = message["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val calls = message["tool_calls"]?.jsonArray.orEmpty().mapNotNull { it as? JsonObject }.map { c ->
            val fn = c["function"]?.jsonObject
            AiToolCall(
                id = c["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                name = fn?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty(),

                argumentsJson = fn?.get("arguments")?.jsonPrimitive?.contentOrNull ?: "{}",
            )
        }
        return AiReply.Ok(AiTurn(AiRole.ASSISTANT, text, calls.filter { it.name.isNotBlank() }))
    }

    private fun messagesOf(turn: AiTurn): List<JsonObject> = when (turn.role) {
        AiRole.USER -> listOf(buildJsonObject { put("role", "user"); put("content", turn.text) })

        AiRole.ASSISTANT -> listOf(
            buildJsonObject {
                put("role", "assistant")
                if (turn.text.isNotBlank()) put("content", turn.text)
                if (turn.calls.isNotEmpty()) {
                    putJsonArray("tool_calls") {
                        turn.calls.forEach { c ->
                            addJsonObject {
                                put("id", c.id)
                                put("type", "function")
                                putJsonObject("function") {
                                    put("name", c.name)
                                    put("arguments", c.argumentsJson)
                                }
                            }
                        }
                    }
                }
            },
        )

        AiRole.TOOL -> turn.results.map { r ->
            buildJsonObject {
                put("role", "tool")
                put("tool_call_id", r.id)
                put("content", r.content)
            }
        }
    }
}

private inline fun kotlinx.serialization.json.JsonArrayBuilder.addJsonObject(
    build: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit,
) {
    add(buildJsonObject(build))
}

private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
