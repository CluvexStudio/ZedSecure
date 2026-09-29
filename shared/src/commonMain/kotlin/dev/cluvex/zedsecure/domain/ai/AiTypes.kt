package dev.cluvex.zedsecure.domain.ai

import dev.cluvex.zedsecure.crypto.ZsxCrypto
import dev.cluvex.zedsecure.crypto.ZsxSealRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

enum class AiProvider(

    val label: String,

    val keyUrl: String,
) {
    GEMINI("Google Gemini", "https://aistudio.google.com/apikey"),
    CLAUDE("Anthropic Claude", "https://console.anthropic.com/settings/keys"),
    OPENAI("OpenAI ChatGPT", "https://platform.openai.com/api-keys"),
}

@Serializable
data class AiModel(
    val id: String,
    val label: String = id,

    val contextTokens: Long = 0,
)

@Serializable
data class AiSettings(
    val enabled: Boolean = false,
    val provider: AiProvider = AiProvider.GEMINI,
    @SerialName("keys") val sealedKeys: Map<String, String> = emptyMap(),
    val models: Map<String, String> = emptyMap(),

    val extraPrompt: String = "",

    val allowChanges: Boolean = true,

    val keepHistory: Boolean = false,
) {
    fun key(provider: AiProvider = this.provider): String =
        sealedKeys[provider.name]?.takeIf { it.isNotBlank() }?.let { openSecret(it) }.orEmpty()

    fun hasKey(provider: AiProvider = this.provider): Boolean =
        !sealedKeys[provider.name].isNullOrBlank()

    fun model(provider: AiProvider = this.provider): String =
        models[provider.name]?.takeIf { it.isNotBlank() }.orEmpty()

    fun isReady(provider: AiProvider = this.provider): Boolean =
        enabled && hasKey(provider) && model(provider).isNotBlank()

    fun withKey(provider: AiProvider, plain: String): AiSettings = copy(
        sealedKeys = sealedKeys.toMutableMap().apply {
            if (plain.isBlank()) remove(provider.name) else put(provider.name, sealSecret(plain))
        },
    )

    fun withModel(provider: AiProvider, id: String): AiSettings =
        copy(models = models.toMutableMap().apply { put(provider.name, id) })

    companion object {
        const val SEALED_PREFIX = "zsx:"

        @OptIn(ExperimentalEncodingApi::class)
        internal fun sealSecret(plain: String): String {
            if (plain.isBlank()) return ""
            if (plain.startsWith(SEALED_PREFIX)) return plain
            return runCatching {
                val sealed = ZsxCrypto.seal(
                    ZsxSealRequest(
                        configPayload = plain,
                        nameEn = "", nameFa = "", note = "", expiresAt = null, password = null,
                    ),
                )
                SEALED_PREFIX + Base64.encode(sealed)
            }.getOrDefault(plain)
        }

        @OptIn(ExperimentalEncodingApi::class)
        internal fun openSecret(stored: String): String {
            if (!stored.startsWith(SEALED_PREFIX)) return stored
            return runCatching {
                ZsxCrypto.open(Base64.decode(stored.removePrefix(SEALED_PREFIX)), password = null)
            }.getOrDefault("")
        }
    }
}

enum class AiRole { USER, ASSISTANT, TOOL }

@Serializable
data class AiToolCall(val id: String, val name: String, val argumentsJson: String)

@Serializable
data class AiToolResult(val id: String, val name: String, val content: String, val failed: Boolean = false)

@Serializable
data class AiTurn(
    val role: AiRole,
    val text: String = "",
    val calls: List<AiToolCall> = emptyList(),
    val results: List<AiToolResult> = emptyList(),

    val error: String? = null,
)

data class AiTool(
    val name: String,
    val description: String,
    val parametersJson: String,

    val destructive: Boolean = false,
)

sealed interface AiReply {
    data class Ok(val turn: AiTurn) : AiReply

    data class Failed(val message: String, val status: Int = 0) : AiReply
}
