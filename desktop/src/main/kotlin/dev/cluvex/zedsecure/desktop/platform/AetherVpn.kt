package dev.cluvex.zedsecure.desktop.platform

import dev.cluvex.zedsecure.desktop.core.AetherLogEvent
import dev.cluvex.zedsecure.desktop.core.DesktopAether
import dev.cluvex.zedsecure.desktop.core.AetherProfile
import dev.cluvex.zedsecure.desktop.core.Os
import dev.cluvex.zedsecure.desktop.core.SystemProxy
import dev.cluvex.zedsecure.desktop.core.parseAetherLogLine
import dev.cluvex.zedsecure.platform.KeyValueStore
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlin.concurrent.thread

/**
 * Aether section controller: the automatic WARP tunnel (MASQUE/WireGuard/
 * gool/mim + Tor/Psiphon overlays) as an isolated section beside the stock
 * engines. Own profile store, own state flow, own engine instance —
 * nothing here touches VpnManager, AppSettings or stock profiles.
 *
 * Routing v1 is loopback endpoints + optional system proxy (mirrors the
 * reference GUI); TUN mode can reuse TunMode later without changing this
 * controller's contract.
 */
object AetherVpn {
    private const val PROFILE_KEY = "aether.profile.v1"
    private const val SYSPROXY_KEY = "aether.sysproxy"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    enum class State { IDLE, WORKING, LIVE, ERROR }

    data class UiState(
        val state: State = State.IDLE,
        val profile: AetherProfile = AetherProfile(),
        val socksAddr: String = "127.0.0.1:1819",
        val httpAddr: String = "127.0.0.1:1820",
        val torExit: String? = null,
        val psiExit: String? = null,
        val statusLine: String = "",
        val detailLine: String = "",
        val torPercent: Int? = null,
        val upBytes: Long = 0L,
        val downBytes: Long = 0L,
        val upRate: Long = 0L,
        val downRate: Long = 0L,
        val clients: Int = 0,
        val sysProxy: Boolean = false,
        val logTail: List<String> = emptyList(),
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private lateinit var store: KeyValueStore
    private lateinit var workDir: File
    @Volatile private var engine: DesktopAether? = null
    @Volatile private var wantStop = false
    private var lastStatsAt = 0L
    private var lastUp = 0L
    private var lastDown = 0L

    fun configure(store: KeyValueStore, workDir: File) {
        if (this::store.isInitialized) return
        this.store = store
        this.workDir = workDir
        val saved = runCatching {
            store.getString(PROFILE_KEY)?.let { json.decodeFromString<AetherProfile>(it) }
        }.getOrNull()
        val sys = store.getString(SYSPROXY_KEY) == "1"
        _state.value = _state.value.copy(
            profile = saved ?: AetherProfile(),
            sysProxy = sys,
        )
    }

    fun updateProfile(p: AetherProfile) {
        _state.value = _state.value.copy(profile = p)
        if (this::store.isInitialized) {
            runCatching { store.putString(PROFILE_KEY, json.encodeToString(AetherProfile.serializer(), p)) }
        }
    }

    fun setSysProxy(on: Boolean) {
        _state.value = _state.value.copy(sysProxy = on)
        if (this::store.isInitialized) runCatching {
            store.putString(SYSPROXY_KEY, if (on) "1" else "0")
        }
        val s = _state.value
        if (on && s.state == State.LIVE) {
            val hp = s.httpAddr.substringAfterLast(':').toIntOrNull() ?: 1820
            SystemProxy.set("127.0.0.1", hp)
        } else if (!on) {
            SystemProxy.clear()
        }
    }

    fun toggle() {
        if (_state.value.state == State.WORKING || _state.value.state == State.LIVE) disconnect()
        else connect()
    }

    fun connect() {
        if (_state.value.state == State.WORKING || _state.value.state == State.LIVE) return
        val profile = _state.value.profile
        wantStop = false
        updateStatus(State.WORKING, "Starting Aether…", "")
        thread(name = "aether-section", isDaemon = true) {
            val eng = DesktopAether(workDir, profile)
            engine = eng
            val events = mutableListOf<String>()
            fun pushLog(line: String) {
                synchronized(events) {
                    events.add(line)
                    if (events.size > 200) events.removeAt(0)
                    _state.value = _state.value.copy(logTail = events.toList())
                }
            }
            val attemptStart = System.currentTimeMillis()
            val res = eng.start(
                onEvent = { ev ->
                    when (ev) {
                        is AetherLogEvent.EngineStarted ->
                            updateStatus(State.WORKING, "Engine up — hunting a route…", "")
                        is AetherLogEvent.OverlayReady ->
                            updateStatus(State.WORKING, "Overlay ready — waiting on ports…", "")
                        is AetherLogEvent.ScanBudget ->
                            updateStatus(State.WORKING, "Scanning…", "budget ${ev.secs}s")
                        is AetherLogEvent.TorPercent -> _state.value =
                            _state.value.copy(torPercent = ev.pct, detailLine = "Tor bootstrap ${ev.pct}%")
                        is AetherLogEvent.Traffic -> onTraffic(ev.upBytes, ev.downBytes)
                    }
                },
                onLogLine = { pushLog(it) },
            )
            if (wantStop) return@thread
            res.onSuccess {
                val httpPort = profile.frontendHttpPort()
                val httpHost = "127.0.0.1"
                _state.value = _state.value.copy(
                    state = State.LIVE,
                    socksAddr = profile.primaryAddr(),
                    httpAddr = "$httpHost:$httpPort",
                    torExit = profile.torExit(),
                    psiExit = profile.psiExit(),
                    statusLine = "Tunnel live",
                    detailLine = "",
                    torPercent = null,
                )
                if (_state.value.sysProxy) {
                    SystemProxy.set("127.0.0.1", httpPort)
                }
            }.onFailure { e ->
                engine = null
                updateStatus(State.ERROR, "Connection failed", e.message ?: "")
            }
        }
    }

    private fun onTraffic(up: Long, down: Long) {
        val now = System.currentTimeMillis()
        val dt = ((now - lastStatsAt).coerceAtLeast(1)) / 1000.0
        val s = _state.value
        val upRate = if (lastStatsAt == 0L) 0L else ((up - lastUp).coerceAtLeast(0) / dt).toLong()
        val downRate = if (lastStatsAt == 0L) 0L else ((down - lastDown).coerceAtLeast(0) / dt).toLong()
        lastStatsAt = now
        lastUp = up
        lastDown = down
        _state.value = s.copy(upBytes = up, downBytes = down, upRate = upRate, downRate = downRate)
    }

    private fun updateStatus(state: State, line: String, detail: String) {
        _state.value = _state.value.copy(state = state, statusLine = line, detailLine = detail)
    }

    fun disconnect() {
        wantStop = true
        engine?.stop()
        engine = null
        SystemProxy.clear()
        lastStatsAt = 0L
        _state.value = _state.value.copy(
            state = State.IDLE, statusLine = "", detailLine = "",
            torPercent = null, upRate = 0L, downRate = 0L, clients = 0,
        )
    }

    /** Best-effort LAN client count (ESTABLISHED to our ports). */
    fun refreshClients(): Int {
        val ports = setOf(
            _state.value.socksAddr.substringAfterLast(':').toIntOrNull(),
            _state.value.httpAddr.substringAfterLast(':').toIntOrNull(),
            _state.value.torExit?.substringAfterLast(':')?.toIntOrNull(),
            _state.value.psiExit?.substringAfterLast(':')?.toIntOrNull(),
        ).filterNotNull().toSet()
        if (ports.isEmpty()) return 0
        val n = runCatching {
            val pb = ProcessBuilder(
                if (Os.current == Os.WINDOWS) {
                    listOf("cmd", "/c", "netstat -an -p TCP")
                } else {
                    listOf("sh", "-c", "netstat -an -p TCP 2>/dev/null || ss -Htn")
                },
            ).redirectErrorStream(true).start()
            val out = pb.inputStream.bufferedReader().readText()
            pb.waitFor()
            val ips = mutableSetOf<String>()
            out.lines().forEach { line ->
                val parts = line.trim().split(Regex("\\s+"))
                // netstat: Proto Recv-Q Send-Q Local ... Foreign ... (State)
                // ss -Htn: State Recv-Q Send-Q Local:port Peer:port
                val local = parts.getOrNull(3) ?: return@forEach
                val remote = parts.getOrNull(4) ?: parts.getOrNull(5) ?: return@forEach
                val lport = local.substringAfterLast(':').toIntOrNull() ?: return@forEach
                if (lport !in ports) return@forEach
                if (!("ESTABLISHED" in line.uppercase())) return@forEach
                val rip = remote.substringBeforeLast(':').trim('[', ']')
                if (rip.isNotBlank() && rip != "127.0.0.1" && rip != "::1") ips.add(rip)
            }
            ips.size
        }.getOrDefault(0)
        _state.value = _state.value.copy(clients = n)
        return n
    }
}
