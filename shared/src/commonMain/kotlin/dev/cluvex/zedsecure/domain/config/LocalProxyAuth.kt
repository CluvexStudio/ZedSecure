package dev.cluvex.zedsecure.domain.config

import dev.cluvex.zedsecure.platform.installLocalProxyAuthenticator
import dev.cluvex.zedsecure.platform.secureRandomToken
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlin.concurrent.Volatile

object LocalProxyAuth {
    data class Credentials(val user: String, val pass: String)

    private const val XRAY_SOCKS_TAG = "socks-in"

    @Volatile
    var current: Credentials? = null
        private set

    fun begin(): Credentials {
        val credentials = Credentials(user = "z" + secureRandomToken(11), pass = secureRandomToken(28))
        current = credentials
        installLocalProxyAuthenticator()
        return credentials
    }

    fun end() {
        current = null
    }

    fun secureXray(json: String, credentials: Credentials): String {
        val root = runCatching { Json.parseToJsonElement(json) as? JsonObject }.getOrNull() ?: return json
        val inbounds = root["inbounds"] as? JsonArray ?: return json
        var changed = false
        val secured = buildJsonArray {
            inbounds.forEach { element ->
                val inbound = element as? JsonObject
                val tag = (inbound?.get("tag") as? JsonPrimitive)?.contentOrNull
                val protocol = (inbound?.get("protocol") as? JsonPrimitive)?.contentOrNull
                if (inbound != null && tag == XRAY_SOCKS_TAG && protocol == "socks") {
                    val settings = inbound["settings"] as? JsonObject ?: JsonObject(emptyMap())
                    add(JsonObject(inbound + ("settings" to JsonObject(settings + mapOf(
                        "auth" to JsonPrimitive("password"),
                        "accounts" to buildJsonArray {
                            add(buildJsonObject {
                                put("user", credentials.user)
                                put("pass", credentials.pass)
                            })
                        },
                    )))))
                    changed = true
                } else {
                    add(element)
                }
            }
        }
        return if (changed) JsonObject(root + ("inbounds" to secured)).toString() else json
    }

    fun secureSingBox(json: String, credentials: Credentials): String {
        val root = runCatching { SingBoxJson.parse(json) as? JsonObject }.getOrNull() ?: return json
        val inbounds = root["inbounds"] as? JsonArray ?: return json
        var changed = false
        val secured = buildJsonArray {
            inbounds.forEach { element ->
                val inbound = element as? JsonObject
                val tag = (inbound?.get("tag") as? JsonPrimitive)?.contentOrNull
                if (inbound != null && tag == SingBoxConfigs.LOCAL_TAG) {
                    add(JsonObject(inbound + ("users" to buildJsonArray {
                        add(buildJsonObject {
                            put("username", credentials.user)
                            put("password", credentials.pass)
                        })
                    })))
                    changed = true
                } else {
                    add(element)
                }
            }
        }
        return if (changed) JsonObject(root + ("inbounds" to secured)).toString() else json
    }
}
