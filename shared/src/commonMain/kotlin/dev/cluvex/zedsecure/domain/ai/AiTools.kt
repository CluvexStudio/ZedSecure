package dev.cluvex.zedsecure.domain.ai

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

object AiTools {
    private const val OBJ = """{"type":"object","properties":{},"required":[]}"""

    private fun obj(vararg props: Pair<String, String>, required: List<String> = emptyList()): String {
        val body = props.joinToString(",") { (name, spec) -> "\"$name\":$spec" }
        val req = required.joinToString(",") { "\"$it\"" }
        return """{"type":"object","properties":{$body},"required":[$req]}"""
    }

    private fun str(description: String) = """{"type":"string","description":"$description"}"""
    private fun int(description: String) = """{"type":"integer","description":"$description"}"""

    fun forSettings(allowChanges: Boolean): List<AiTool> =
        if (allowChanges) readOnly + changing else readOnly

    private val readOnly = listOf(
        AiTool(
            "get_settings",
            "Every setting in the app with its current value, as JSON. Read this before changing " +
                "anything, so a patch is built from what is actually set rather than from a guess. " +
                "Secrets are redacted and cannot be read back.",
            OBJ,
        ),
        AiTool(
            "list_servers",
            "The saved servers: id, display name, protocol, address, port and which subscription " +
                "group each belongs to. The id is what every other server tool takes. Keys and " +
                "passwords are never included.",
            OBJ,
        ),
        AiTool(
            "get_status",
            "Whether the VPN is connected, which server and engine are in use, how long for, and " +
                "how many bytes have moved. Check this before claiming anything about the state.",
            OBJ,
        ),
        AiTool(
            "read_logs",
            "The most recent lines of the app log. Use it to explain a failure the user is seeing " +
                "rather than guessing at one; quote the line that matters instead of the whole log.",
            obj("lines" to int("How many recent lines, 20-500. Default 120.")),
        ),
        AiTool(
            "core_report",
            "What the VPN core is doing inside: goroutine count, memory, and the stacks the " +
                "goroutines are sitting in, most frequent first. This is the tool for 'the phone is " +
                "hot' or 'the battery is draining' — it shows which part of the core is busy.",
            OBJ,
        ),
        AiTool(
            "list_protocols",
            "Every engine this build can create directly, with the fields each one takes and which " +
                "are required. Read it before create_server rather than assuming a field name.",
            OBJ,
        ),
        AiTool(
            "app_map",
            "Where each feature lives in the app's navigation. Use it to answer 'where do I turn " +
                "on X' with the actual path the user must tap, not an invented one.",
            OBJ,
        ),
        AiTool(
            "ping",
            "Measures real latency to a server by connecting through it. With no id, measures every " +
                "saved server. This is a real connection, so it takes a few seconds per server.",
            obj("id" to str("A server id from list_servers. Omit to measure all of them.")),
        ),
        AiTool(
            "speed_test",
            "Downloads and uploads through the tunnel as it is configured right now and reports the " +
                "throughput. Requires an active connection. Takes about half a minute and uses real " +
                "data from the user's allowance, so say what it will cost before running it.",
            OBJ,
        ),
        AiTool(
            "test_dns",
            "Resolves a hostname through one DNS server and reports whether it answered, how fast, " +
                "and what it returned. Use it to prove a DNS setting works BEFORE writing it into " +
                "the settings, so a broken resolver is never saved.",
            obj(
                "server" to str("The resolver: an IP for plain DNS, or a URL for DoH, or a hostname for DoT."),
                "mode" to str("One of: udp, tcp, dot, doh."),
                "host" to str("The name to resolve. Default www.google.com."),
                required = listOf("server", "mode"),
            ),
        ),
        AiTool(
            "probe_mtu",
            "Finds the largest packet that survives the path to the server, by sending probes " +
                "that are not allowed to fragment, on the REAL network, not through the tunnel. " +
                "The answer is measured, not assumed. With most engines this works while the VPN " +
                "stays connected, because the app keeps its own traffic outside the tunnel. If the " +
                "tool answers that the VPN is in the way, call it again with reconnect=true: it then " +
                "drops the tunnel for about fifteen seconds, measures, and brings it back in this " +
                "same call — tell the user first, because their connection pauses. NEVER call " +
                "disconnect yourself in order to measure — on a network where your provider is " +
                "blocked your next turn would fail. Pass apply=true to write the result into the " +
                "MTU setting, so the tunnel comes back up already using it.",
            obj(
                "reconnect" to """{"type":"boolean","description":"Drop and restore the tunnel around the measurement when the VPN is up."}""",
                "apply" to """{"type":"boolean","description":"Write the recommended MTU into the settings before reconnecting."}""",
            ),
        ),
        AiTool(
            "exit_info",
            "Which country and IP address the traffic actually leaves from. Use it to confirm a " +
                "connection is really going where the user thinks it is.",
            OBJ,
        ),
    )

    private val changing = listOf(
        AiTool(
            "apply_settings",
            "Changes settings. Takes a PATCH: a JSON object containing only the keys being changed, " +
                "with the same names and value types get_settings uses. Never send the whole " +
                "settings object back. Change one thing at a time when the user is diagnosing a " +
                "problem, so the thing that helped is known. Some changes only take effect on the " +
                "next connection — say so rather than letting the user think nothing happened.",
            obj("patch" to str("A JSON object of the keys to change."), required = listOf("patch")),
        ),
        AiTool(
            "set_active_server",
            "Selects which saved server the next connection uses. Does not connect by itself.",
            obj("id" to str("A server id from list_servers."), required = listOf("id")),
        ),
        AiTool(
            "connect",
            "Starts the VPN with the currently selected server. On the first ever connection the " +
                "system shows its own VPN permission dialog, which only the user can accept.",
            OBJ,
        ),
        AiTool(
            "disconnect",
            "Stops the VPN. The user loses their tunnel the moment this runs — and on a network where " +
                "your provider is blocked, so do you: your next turn will fail. Only call it when " +
                "stopping the VPN is what the user actually wants. To measure the MTU use " +
                "probe_mtu with reconnect=true instead.",
            OBJ,
            destructive = true,
        ),
        AiTool(
            "import_config",
            "Imports a config the user pasted: a share link (vless://, vmess://, trojan://, ss://, " +
                "wireguard, vpn:// …), a subscription URL, or the text of a .conf/.ovpn/JSON file. " +
                "Adds servers; it never replaces the ones already saved.",
            obj("text" to str("The link, URL or file text to import."), required = listOf("text")),
        ),
        AiTool(
            "create_server",
            "Creates a server for an engine that has no link format to paste: tor, psiphon, ssh, " +
                "dnstunnel, masterdns, openconnect, ikev2 or chain. Call list_protocols first to " +
                "see which fields the engine takes on this build — inventing a field silently " +
                "produces a server that cannot connect. Anything with a share link (VLESS, VMess, " +
                "Trojan, Shadowsocks, WireGuard, AmneziaWG, Hysteria2, SSH) should be written as " +
                "that link and passed to import_config instead, so it goes through the same parser " +
                "as everything the user pastes themselves.",
            obj(
                "kind" to str("The engine: tor, psiphon, ssh, dnstunnel, masterdns, openconnect, ikev2, chain."),
                "name" to str("What to call it in the server list."),
                "options" to str("A JSON object of that engine's fields, from list_protocols."),
                required = listOf("kind", "name"),
            ),
        ),
        AiTool(
            "delete_server",
            "Permanently removes one saved server. There is no undo, and a server the user imported " +
                "from a paid subscription cannot be recovered from this app. Ask first, every time, " +
                "even when the user's earlier message seemed to ask for it.",
            obj("id" to str("A server id from list_servers."), required = listOf("id")),
            destructive = true,
        ),
    )

    suspend fun run(call: AiToolCall, bridge: AiAppBridge): AiToolResult {
        val args = runCatching { aiJson.parseToJsonElement(call.argumentsJson).jsonObject }
            .getOrElse { JsonObject(emptyMap()) }
        fun s(name: String, fallback: String = "") =
            args[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: fallback
        fun i(name: String, fallback: Int) = args[name]?.jsonPrimitive?.intOrNull ?: fallback
        fun b(name: String) = args[name]?.jsonPrimitive?.contentOrNull.toBoolean()

        return runCatching {
            val out = when (call.name) {
                "get_settings" -> bridge.settingsJson()
                "list_servers" -> bridge.serversJson()
                "get_status" -> bridge.statusJson()
                "read_logs" -> bridge.logsText(i("lines", 120).coerceIn(20, 500))
                "core_report" -> bridge.coreReport()
                "app_map" -> bridge.appMapJson()
                "list_protocols" -> bridge.protocolsJson()
                "ping" -> bridge.ping(s("id").takeIf { it.isNotBlank() })
                "speed_test" -> bridge.speedTest()
                "test_dns" -> bridge.testDns(s("server"), s("mode", "udp"), s("host", "www.google.com"))
                "probe_mtu" -> bridge.probeMtu(b("reconnect"), b("apply"))
                "exit_info" -> bridge.exitInfo()

                "apply_settings" -> bridge.applySettings(patchOf(args))
                "set_active_server" -> bridge.setActiveServer(s("id"))
                "connect" -> bridge.connect()
                "disconnect" -> bridge.disconnect()
                "import_config" -> bridge.importConfig(s("text"))
                "delete_server" -> bridge.deleteServer(s("id"))
                "create_server" -> bridge.createServer(s("kind"), s("name"), patchOf(args, "options"))

                else -> return AiToolResult(
                    call.id, call.name,
                    "no such tool: ${call.name}. The available ones are listed in this request.",
                    failed = true,
                )
            }
            AiToolResult(call.id, call.name, out.ifBlank { "done" })
        }.getOrElse { e ->
            AiToolResult(call.id, call.name, e.message ?: e.toString(), failed = true)
        }
    }

    private fun patchOf(args: JsonObject, key: String = "patch"): String {
        val raw = args[key] ?: return "{}"
        (raw as? kotlinx.serialization.json.JsonObject)?.let { return it.toString() }
        val text = raw.jsonPrimitive.contentOrNull.orEmpty()
        return text.ifBlank { "{}" }
    }
}
