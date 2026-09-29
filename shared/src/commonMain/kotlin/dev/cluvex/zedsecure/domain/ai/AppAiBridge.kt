package dev.cluvex.zedsecure.domain.ai

import dev.cluvex.zedsecure.core.LogBus
import dev.cluvex.zedsecure.core.VpnManager
import dev.cluvex.zedsecure.data.config.ConfigRepository
import dev.cluvex.zedsecure.data.net.MtuOptimizer
import dev.cluvex.zedsecure.data.net.MtuResult
import dev.cluvex.zedsecure.data.net.MtuServerHint
import dev.cluvex.zedsecure.data.net.PingService
import dev.cluvex.zedsecure.domain.config.ProfileSource
import dev.cluvex.zedsecure.domain.config.SshProfile
import dev.cluvex.zedsecure.domain.config.VpnProfile
import dev.cluvex.zedsecure.domain.model.AppSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class AppAiBridge(
    private val repository: ConfigRepository,
    private val readSettings: () -> AppSettings,
    private val writeSettings: (AppSettings) -> Unit,

    private val coreReportFn: () -> String = { AI_UNSUPPORTED },
    private val connectFn: suspend () -> String = { AI_UNSUPPORTED },
    private val disconnectFn: suspend () -> String = { AI_UNSUPPORTED },
    private val speedTestFn: suspend () -> String = { AI_UNSUPPORTED },
    private val dnsTestFn: suspend (server: String, mode: String, host: String) -> String =
        { _, _, _ -> AI_UNSUPPORTED },
    private val exitInfoFn: suspend () -> String = { AI_UNSUPPORTED },
    private val mtuHintFn: () -> MtuServerHint? = { null },
    private val chainConfig: (VpnProfile) -> String? = { null },
) : AiAppBridge {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }

    override fun settingsJson(): String = runCatching {
        val all = json.encodeToJsonElement(AppSettings.serializer(), readSettings()).jsonObject

        JsonObject(all - PRIVATE_KEYS).toString()
    }.getOrElse { "could not read the settings: ${it.message}" }

    override fun serversJson(): String {
        val active = repository.activeId.value
        val groups = repository.subscriptions.value.associate { it.id to it.name }
        return buildJsonArray {
            repository.profiles.value.forEach { p ->
                add(
                    buildJsonObject {
                        put("id", p.id)
                        put("name", p.name)
                        put("protocol", p.protocol)
                        put("transport", p.transportLabel)
                        put("address", p.address)
                        put("port", p.port)
                        put("kind", kindOf(p))
                        p.subscriptionId.takeIf { it.isNotBlank() }?.let {
                            put("group", groups[it] ?: it)
                        }
                        if (p.id == active) put("active", true)
                    },
                )
            }
        }.toString()
    }

    override fun statusJson(): String {
        val s = VpnManager.status.value
        val active = repository.activeId.value?.let { id -> repository.profiles.value.firstOrNull { it.id == id } }
        return buildJsonObject {
            put("state", s.state.name)
            put("connected", s.state.isActive)
            put("seconds", s.durationSeconds)
            put("downloaded", s.totalDownload)
            put("uploaded", s.totalUpload)
            put("downloadBps", s.downloadBps)
            put("uploadBps", s.uploadBps)
            s.serverName?.let { put("server", it) }
            s.error?.let { put("lastError", it) }
            active?.let {
                put("activeId", it.id)
                put("engine", kindOf(it))
                put("protocol", it.protocol)
            }
        }.toString()
    }

    override fun logsText(lines: Int): String {
        val all = LogBus.lines.value
        if (all.isEmpty()) return "the log is empty — nothing has been recorded since the app started"
        return all.takeLast(lines).joinToString("\n")
    }

    override fun coreReport(): String = runCatching { coreReportFn() }.getOrElse { it.message ?: AI_UNSUPPORTED }

    override fun appMapJson(): String = APP_MAP

    override fun protocolsJson(): String = PROTOCOLS

    override fun applySettings(patchJson: String): String {
        val patch = runCatching { json.parseToJsonElement(patchJson).jsonObject }.getOrNull()
            ?: return "the patch was not a JSON object: $patchJson"
        if (patch.isEmpty()) return "the patch was empty, so nothing changed"

        val current = runCatching { json.encodeToJsonElement(AppSettings.serializer(), readSettings()).jsonObject }
            .getOrElse { return "could not read the settings: ${it.message}" }

        val forbidden = patch.keys.filter { it in PRIVATE_KEYS }
        if (forbidden.isNotEmpty()) {
            return "${forbidden.joinToString(", ")} is the assistant's own configuration and can only " +
                "be changed by the user, in Settings."
        }

        val unknown = patch.keys.filterNot { it in current.keys }
        if (unknown.isNotEmpty()) {
            return "no such setting: ${unknown.joinToString(", ")}. " +
                "Call get_settings and use the names exactly as they appear there."
        }

        val merged = JsonObject(current.toMutableMap().apply { putAll(patch) })
        val next = runCatching { json.decodeFromJsonElement(AppSettings.serializer(), merged) }
            .getOrElse { return "the value did not fit the setting: ${it.message}" }

        val changed = patch.keys.map { key ->
            "$key: ${current[key]} → ${patch[key]}"
        }
        writeSettings(next)
        return "changed ${patch.size} setting(s): ${changed.joinToString("; ")}. " +
            "Anything that shapes the tunnel applies on the next connection."
    }

    override fun setActiveServer(id: String): String {
        val profile = repository.profiles.value.firstOrNull { it.id == id }
            ?: return notFound(id)
        repository.setActive(id)
        return "selected \"${profile.name}\" (${profile.protocol}). Not connected yet — call connect."
    }

    override suspend fun connect(): String = runCatching { connectFn() }.getOrElse { it.message ?: "connect failed" }

    override suspend fun disconnect(): String =
        runCatching { disconnectFn() }.getOrElse { it.message ?: "disconnect failed" }

    override suspend fun importConfig(text: String): String {
        if (text.isBlank()) return "nothing to import"
        return repository.importText(text).fold(
            onSuccess = { n -> if (n > 0) "imported $n server(s)" else "nothing new — already saved" },
            onFailure = { "could not import it: ${it.message}" },
        )
    }

    override fun deleteServer(id: String): String {
        val profile = repository.profiles.value.firstOrNull { it.id == id } ?: return notFound(id)
        repository.remove(id)
        return "deleted \"${profile.name}\". This cannot be undone from inside the app."
    }

    override suspend fun createServer(kind: String, name: String, optionsJson: String): String {
        val o = runCatching { json.parseToJsonElement(optionsJson).jsonObject }.getOrNull()
            ?: JsonObject(emptyMap())
        fun s(key: String, fallback: String = "") =
            o[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: fallback
        fun i(key: String, fallback: Int) = s(key).toIntOrNull() ?: fallback
        val label = name.ifBlank { kind }

        return runCatching {
            when (kind.lowercase()) {
                "tor" -> {
                    repository.addTor(label)
                    "created the Tor server \"$label\". Tor is slow by design; expect seconds, not milliseconds."
                }

                "ssh" -> {
                    val host = s("host")
                    if (host.isBlank()) return "ssh needs a host"
                    repository.addSsh(
                        SshProfile(
                            host = host,
                            port = i("port", 22),
                            username = s("username"),
                            password = s("password"),
                            privateKey = s("privateKey"),
                            authType = if (s("privateKey").isNotBlank()) {
                                SshProfile.AUTH_KEY
                            } else {
                                SshProfile.AUTH_PASSWORD
                            },
                        ),
                        label,
                    )
                    "created the SSH server \"$label\""
                }

                else -> "this build cannot create a \"$kind\" server directly. " +
                    "Call list_protocols for what it can, and use import_config with a share link " +
                    "for anything that has one."
            }
        }.getOrElse { "could not create it: ${it.message}" }
    }

    override suspend fun ping(id: String?): String {
        val profiles = if (id == null) {
            repository.profiles.value
        } else {
            listOfNotNull(repository.profiles.value.firstOrNull { it.id == id })
        }
        if (profiles.isEmpty()) return id?.let { notFound(it) } ?: "there are no saved servers"

        val results = profiles.map { p ->
            val ms = runCatching { PingService.realDelay(p, chainConfig = chainConfig) }.getOrDefault(-1L)
            buildJsonObject {
                put("id", p.id)
                put("name", p.name)
                if (ms > 0) put("ms", ms) else put("failed", true)
            }
        }
        return buildJsonArray { results.forEach { add(it) } }.toString()
    }

    override suspend fun speedTest(): String =
        runCatching { speedTestFn() }.getOrElse { it.message ?: "the speed test failed" }

    override suspend fun testDns(server: String, mode: String, host: String): String =
        runCatching { dnsTestFn(server, mode, host) }.getOrElse { it.message ?: "the DNS test failed" }

    override suspend fun probeMtu(reconnect: Boolean, apply: Boolean): String {
        val wasUp = runCatching { dev.cluvex.zedsecure.data.net.MtuProbe.vpnActive() }.getOrDefault(false)
        if (wasUp && !reconnect) {
            return "the VPN is up, and probes sent now would travel inside the tunnel, where the core " +
                "drops ICMP — every size would look broken. Call probe_mtu again with " +
                "reconnect=true: it drops the tunnel for about fifteen seconds, measures, and brings " +
                "it back in the same call. Do NOT call disconnect yourself for this: on a network " +
                "where your provider is blocked, your next turn would fail."
        }

        if (wasUp) {
            val down = runCatching { disconnectFn() }.getOrElse { it.message ?: "disconnect failed" }
            if (VpnManager.status.value.state.isActive) return "could not drop the tunnel to measure: $down"
        }

        val hint = mtuHintFn()
        val result = runCatching {
            MtuOptimizer.optimize(
                serverHost = hint?.host,
                overhead = hint?.overhead ?: dev.cluvex.zedsecure.data.net.MtuOverheads.worstCase(),
            )
        }.getOrElse { e ->
            if (wasUp) runCatching { connectFn() }
            return "the measurement failed: ${e.message}"
        }

        val applied = if (apply && result is MtuResult.Measured) {
            applySettings("{\"vpnMtu\":${result.recommended}}")
        } else {
            null
        }

        val back = if (wasUp) runCatching { connectFn() }.getOrElse { it.message ?: "reconnect failed" } else null

        return buildJsonObject {
            when (result) {
                is MtuResult.VpnActive -> put("result", "the tunnel was still up, so nothing could be measured")
                is MtuResult.NotMeasurable -> {
                    put("result", "this network filters ICMP, so the path MTU cannot be measured")
                    result.linkMtu?.let { put("linkMtu", it) }
                }
                is MtuResult.Measured -> {
                    put("pathMtu", result.pathMtu)
                    put("recommendedMtu", result.recommended)
                    put("probedHost", result.host)
                    put("measuredToTheServer", result.toServer)
                    put("tunnelOverhead", result.overhead.explain())
                    put("boundaryConfirmed", result.confirmed)
                    result.linkMtu?.let { put("linkMtu", it) }
                }
            }
            applied?.let { put("applied", it) }
            back?.let { put("reconnect", it) }
        }.toString()
    }

    override suspend fun exitInfo(): String =
        runCatching { exitInfoFn() }.getOrElse { it.message ?: "could not find the exit address" }

    private fun notFound(id: String) =
        "no server with the id \"$id\". Call list_servers for the current ones."

    private fun kindOf(p: VpnProfile): String = when (p.source) {
        is ProfileSource.Psiphon -> "psiphon"
        is ProfileSource.Tor -> "tor"
        is ProfileSource.Ssh -> "ssh"
        is ProfileSource.DnsTunnel -> "dnstunnel"
        is ProfileSource.MasterDns -> "masterdns"
        is ProfileSource.OpenConnect -> "openconnect"
        is ProfileSource.Ikev2 -> "ikev2"
        is ProfileSource.ProxyChain -> "chain"
        is ProfileSource.CrossChain -> "crosschain"
        is ProfileSource.SniSpoof -> "snispoof"
        is ProfileSource.AutoSelect -> "autoselect"
        is ProfileSource.SingBox, is ProfileSource.SingBoxConfig -> "singbox"
        else -> "xray"
    }

    private companion object {
        val PRIVATE_KEYS = setOf("ai")

        const val APP_MAP = """{
"Home":"connect button, active server, exit country, speed, the map",
"Servers":"the saved server list; the + button imports a link, a subscription, a file or a QR code; the header menu sorts, pings all, removes duplicates and manages groups; long-press a server to edit, share or delete it",
"Settings > VPN":"tunnel mode, MTU and the measure button, per-app proxy, bypass LAN, IPv6, kill switch",
"Settings > DNS":"remote and direct resolvers, DoH/DoT, hosts overrides, FakeDNS, the DNS scanner",
"Settings > Routing":"rule editor, policy groups, bypass lists, the geo assets",
"Settings > Core":"Xray and sing-box behaviour: mux, brutal, TLS fragment, uTLS fingerprint, tun stack",
"Settings > Appearance":"theme, accent colour, AMOLED, screen transitions, connect-button style, language",
"Settings > Live monitor":"the app's own CPU, temperature, battery and memory, live",
"Settings > Logs":"the log viewer, with filters and share",
"Settings > AI assistant":"this assistant: provider, API key, model, and whether it may change things",
"Settings > Speed test":"download and upload through the active tunnel",
"Settings > Subscriptions":"subscription URLs, update interval, groups"
}"""

        const val PROTOCOLS = """{
"createDirectly":{
 "tor":{"fields":{},"note":"no fields; bridges are configured in Settings"},
 "ssh":{"fields":{"host":"required","port":"default 22","username":"required","password":"or privateKey","privateKey":"PEM, optional"}}
},
"viaImportConfig":{
 "vless":"vless://uuid@host:port?...#name",
 "vmess":"vmess:// (base64 JSON or the standard URI form)",
 "trojan":"trojan://password@host:port?...#name",
 "shadowsocks":"ss://base64(method:password)@host:port#name",
 "hysteria2":"hysteria2://password@host:port?...#name",
 "wireguard":"the .conf text, or wireguard://key@host:port?publickey=...",
 "amneziawg":"the .conf text including Jc/Jmin/Jmax/S1-S4/H1-H4/I1-I5",
 "amnezia":"vpn:// links, and Amnezia server JSON",
 "openvpn":"the .ovpn file text",
 "ssh":"ssh://user,password,base64key@host:port#name",
 "socks":"socks://[base64(user:pass)@]host:port#name",
 "subscription":"any http(s) URL that serves a server list"
},
"note":"For anything not listed, say so rather than inventing a format."
}"""
    }
}
