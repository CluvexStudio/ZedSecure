package dev.cluvex.zedsecure.ai

import dev.cluvex.zedsecure.core.DnsProbe
import dev.cluvex.zedsecure.core.VpnManager
import dev.cluvex.zedsecure.core.XrayController
import dev.cluvex.zedsecure.data.config.ConfigRepository
import dev.cluvex.zedsecure.data.net.MtuServerHint
import dev.cluvex.zedsecure.data.net.NetworkInfoRepository
import dev.cluvex.zedsecure.data.net.SpeedTestEngine
import dev.cluvex.zedsecure.data.settings.SettingsRepository
import dev.cluvex.zedsecure.domain.ai.AppAiBridge
import dev.cluvex.zedsecure.data.net.DohProbe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object AndroidAiBridge {
    fun create(
        repository: ConfigRepository,
        settings: SettingsRepository,
        toggleConnection: () -> Unit,
        mtuHint: () -> MtuServerHint?,
    ): AppAiBridge = AppAiBridge(
        repository = repository,
        readSettings = { settings.settings.value },
        writeSettings = { next -> settings.update { next } },
        coreReportFn = { XrayController.runtimeReport(14) },
        connectFn = { connect(toggleConnection) },
        disconnectFn = { disconnect(toggleConnection) },
        speedTestFn = { speedTest() },
        dnsTestFn = { server, mode, host -> testDns(server, mode, host) },
        exitInfoFn = { exitInfo() },
        mtuHintFn = mtuHint,
    )

    private suspend fun connect(toggle: () -> Unit): String {
        val before = VpnManager.status.value
        if (before.state.isActive) return "already connected to ${before.serverName ?: "a server"}"
        toggle()
        val settled = withTimeoutOrNull(45_000) {
            VpnManager.status.first { !it.state.isTransitioning }
        } ?: return "still connecting after 45 seconds — read_logs will say what it is waiting on"
        return when {
            settled.state.isActive -> "connected to ${settled.serverName ?: "the selected server"}"
            settled.error != null -> "did not connect: ${settled.error}"
            else -> "did not connect, and the core gave no reason — read_logs for the detail"
        }
    }

    private suspend fun disconnect(toggle: () -> Unit): String {
        if (!VpnManager.status.value.state.isActive) return "it was not connected"
        toggle()
        withTimeoutOrNull(15_000) { VpnManager.status.first { !it.state.isActive } }
        return "disconnected"
    }

    private suspend fun speedTest(): String {
        if (!VpnManager.status.value.state.isActive) {
            return "there is no active connection to measure — connect first"
        }
        val engine = SpeedTestEngine(VpnManager.activeSocksPort)
        engine.run()
        val s = engine.state.value
        return buildJsonObject {
            s.downloadMbps?.let { put("downloadMbps", it) }
            s.uploadMbps?.let { put("uploadMbps", it) }
            s.pingMs?.let { put("latencyMs", it) }
            s.jitterMs?.let { put("jitterMs", it) }
            s.loadedPingMs?.let { put("latencyUnderLoadMs", it) }
            put("bytesUsed", s.bytesUsed)
            s.error?.let { put("error", it) }
        }.toString()
    }

    private suspend fun testDns(server: String, mode: String, host: String): String = when (mode.lowercase()) {
        "doh", "https" -> DohProbe.resolve(server, host).fold(
            onSuccess = { "$server resolved $host to ${it.joinToString(", ")}" },
            onFailure = { it.message ?: "$server did not answer" },
        )

        "udp", "plain", "" -> {
            val reachable = runCatching {
                DnsProbe.fastest(listOf(server), count = 1, timeoutMs = 3_000)
            }.getOrDefault(emptyList())
            if (reachable.isEmpty()) {
                "$server did not answer a query for $host within 3 seconds"
            } else {
                "$server answered — it resolves and the path to it works"
            }
        }

        else -> "this build can test plain DNS and DoH. It has no prober for \"$mode\", so it " +
            "cannot tell you whether that resolver works before you set it."
    }

    private suspend fun exitInfo(): String {
        val info = NetworkInfoRepository().fetchInfo()
        return buildJsonObject {
            info.ipv4?.let { put("ip", it) }
            info.country?.let { put("country", it) }
            info.countryCode?.let { put("countryCode", it) }
            info.city?.let { put("city", it) }
            info.isp?.let { put("isp", it) }
            put("viaTunnel", info.viaTunnel)
            info.error?.let { put("error", it) }
        }.toString()
    }
}
