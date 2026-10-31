package dev.cluvex.zedsecure.data.net

import dev.cluvex.zedsecure.domain.config.Protocol
import dev.cluvex.zedsecure.domain.config.SecurityConfig
import dev.cluvex.zedsecure.domain.config.ServerConfig
import dev.cluvex.zedsecure.domain.config.TransportConfig
import dev.cluvex.zedsecure.domain.config.XrayJsonBuilder
import dev.cluvex.zedsecure.platform.httpJson
import dev.cluvex.zedsecure.platform.httpPatchJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.util.TimeZone
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class WarpRegistrationException(message: String) : Exception(message)

object WarpMasque {
    private const val REG_URL = "https://api.cloudflareclient.com/v0a4471/reg"
    private const val PORT = 443
    const val NAME = "Cloudflare WARP"

    private val HEADERS = mapOf(
        "User-Agent" to "WARP for Android",
        "CF-Client-Version" to "a-6.35-4471",
        "Content-Type" to "application/json; charset=UTF-8",
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun register(socksPort: Int? = null): ServerConfig {
        val random = SecureRandom()
        val keys = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"), random)
        }.generateKeyPair()

        val placeholderKey = ByteArray(32).also { random.nextBytes(it) }
        val serial = ByteArray(8).also { random.nextBytes(it) }.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        val registration = buildJsonObject {
            put("key", Base64.encode(placeholderKey))
            put("install_id", "")
            put("fcm_token", "")
            put("tos", cloudflareTime(System.currentTimeMillis()))
            put("model", "PC")
            put("serial_number", serial)
            put("os_version", "")
            put("key_type", "curve25519")
            put("tunnel_type", "wireguard")
            put("locale", "en_US")
        }
        val created = httpJson("POST", REG_URL, HEADERS, registration.toString(), socksPort = socksPort)
        if (created.code !in 200..299) throw WarpRegistrationException(errorOf(created.code, created.body))
        val account = parse(created.body)
        val id = account.text("id") ?: throw WarpRegistrationException("Cloudflare sent no device id")
        val token = account.text("token") ?: throw WarpRegistrationException("Cloudflare sent no device token")

        val enrollment = buildJsonObject {
            put("key", Base64.encode(keys.public.encoded))
            put("key_type", "secp256r1")
            put("tunnel_type", "masque")
            put("name", "ZedSecure")
        }
        val enrolled = httpPatchJson(
            "$REG_URL/$id",
            HEADERS + ("Authorization" to "Bearer $token"),
            enrollment.toString(),
            socksPort = socksPort,
        )
        if (enrolled.code !in 200..299) throw WarpRegistrationException(errorOf(enrolled.code, enrolled.body))
        return serverFrom(parse(enrolled.body), Base64.encode(keys.private.encoded))
    }

    fun serverFrom(account: JsonObject, privateKey: String): ServerConfig {
        val config = account["config"] as? JsonObject ?: throw WarpRegistrationException("Cloudflare sent no tunnel config")
        val peer = (config["peers"] as? JsonArray)?.firstOrNull() as? JsonObject
            ?: throw WarpRegistrationException("Cloudflare sent no MASQUE endpoint")
        val publicKey = peer.text("public_key")?.let(::pemBody)
            ?: throw WarpRegistrationException("Cloudflare sent no endpoint key")
        val endpoint = peer["endpoint"] as? JsonObject
        val host = endpoint?.text("v4")?.let(::hostOf)
            ?: endpoint?.text("v6")?.let(::hostOf)
            ?: throw WarpRegistrationException("Cloudflare sent no endpoint address")
        val addresses = (config["interface"] as? JsonObject)?.get("addresses") as? JsonObject
        val local = listOfNotNull(
            addresses?.text("v4")?.takeIf { it.isNotBlank() }?.let { if ('/' in it) it else "$it/32" },
            addresses?.text("v6")?.takeIf { it.isNotBlank() }?.let { if ('/' in it) it else "$it/128" },
        )
        if (local.isEmpty()) throw WarpRegistrationException("Cloudflare sent no tunnel address")
        return ServerConfig(
            protocol = Protocol.MASQUE,
            remark = NAME,
            address = host,
            port = PORT,
            secretKey = privateKey,
            peerPublicKey = publicKey,
            localAddresses = local,
            transport = TransportConfig(network = "masque"),
            tls = SecurityConfig(security = "tls", sni = XrayJsonBuilder.WARP_MASQUE_SNI, alpn = "h3"),
        )
    }

    fun hostOf(endpoint: String): String? {
        val value = endpoint.trim()
        if (value.isEmpty()) return null
        if (value.startsWith("[")) return value.substringAfter('[').substringBefore(']').takeIf { it.isNotEmpty() }
        return if (value.count { it == ':' } == 1) value.substringBefore(':') else value
    }

    fun pemBody(pem: String): String =
        pem.lineSequence()
            .map { it.trim() }
            .filterNot { it.startsWith("-----") }
            .joinToString("")
            .filterNot { it.isWhitespace() }

    fun cloudflareTime(millis: Long): String {
        val zone = TimeZone.getDefault()
        val offsetMinutes = zone.getOffset(millis) / 60_000
        val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", java.util.Locale.US).apply { timeZone = zone }
        val sign = if (offsetMinutes < 0) "-" else "+"
        val abs = kotlin.math.abs(offsetMinutes)
        return format.format(java.util.Date(millis)) + sign + (abs / 60).toString().padStart(2, '0') + ":" + (abs % 60).toString().padStart(2, '0')
    }

    private fun parse(body: String): JsonObject =
        runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
            ?: throw WarpRegistrationException("Cloudflare sent an unreadable answer")

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun errorOf(code: Int, body: String): String {
        val message = runCatching {
            val obj = json.parseToJsonElement(body) as? JsonObject
            ((obj?.get("errors") as? JsonArray)?.firstOrNull() as? JsonObject)?.text("message")
        }.getOrNull()
        return if (message.isNullOrBlank()) "Cloudflare answered HTTP $code" else "Cloudflare: $message"
    }
}
