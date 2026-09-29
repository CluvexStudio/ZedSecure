package dev.cluvex.zedsecure.domain.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
object ZedLink {
    const val SCHEME = "zedsecure://"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "kind"
    }

    @Serializable
    data class Payload(
        @SerialName("v") val version: Int = 1,
        @SerialName("n") val name: String = "",
        @SerialName("s") val source: ProfileSource,
        @SerialName("m") val members: List<String> = emptyList(),
    )

    fun isZedLink(text: String): Boolean = text.trim().startsWith(SCHEME, ignoreCase = true)

    fun build(name: String, source: ProfileSource, members: List<String> = emptyList()): String? {
        val payload = runCatching {
            json.encodeToString(Payload.serializer(), Payload(name = name, source = source, members = members))
        }.getOrNull() ?: return null
        return SCHEME + Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
            .encode(payload.encodeToByteArray())
    }

    fun parse(uri: String): Payload? {
        val trimmed = uri.trim()
        if (!isZedLink(trimmed)) return null
        val body = trimmed.substring(SCHEME.length).substringBefore('#').trimEnd('=')
        val text = runCatching {
            Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
                .decode(body)
                .decodeToString()
        }.getOrNull() ?: return null
        return runCatching { json.decodeFromString(Payload.serializer(), text) }.getOrNull()
    }

    fun handles(source: ProfileSource): Boolean = when (source) {
        is ProfileSource.Psiphon,
        is ProfileSource.DnsTunnel,
        is ProfileSource.Tor,
        is ProfileSource.Ssh,
        is ProfileSource.MasterDns,
        is ProfileSource.OpenConnect,
        is ProfileSource.Ikev2,
        is ProfileSource.ProxyChain,
        is ProfileSource.CrossChain,
        -> true

        is ProfileSource.Link,
        is ProfileSource.RawJson,
        is ProfileSource.SniSpoof,
        is ProfileSource.Sealed,

        is ProfileSource.AutoSelect,

        is ProfileSource.SingBox,
        is ProfileSource.SingBoxConfig,
        -> false
    }
}
