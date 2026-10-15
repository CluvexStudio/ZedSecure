package dev.cluvex.zedsecure.desktop.platform

import dev.cluvex.zedsecure.desktop.core.DesktopAether
import dev.cluvex.zedsecure.desktop.core.Os
import dev.cluvex.zedsecure.desktop.core.SystemProxy
import dev.cluvex.zedsecure.desktop.core.aetherProfileFromJson
import dev.cluvex.zedsecure.domain.config.AetherLogEvent
import dev.cluvex.zedsecure.domain.config.AetherProfile
import dev.cluvex.zedsecure.platform.KeyValueStore
import dev.cluvex.zedsecure.ui.aether.AetherBridge
import dev.cluvex.zedsecure.ui.aether.AetherUiState
import dev.cluvex.zedsecure.ui.aether.MatrixRow
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.concurrent.thread
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Aether section controller: the automatic WARP tunnel (MASQUE/WireGuard/
 * gool/mim + Tor/Psiphon overlays) as an isolated section beside the stock
 * engines. Own profile store, own state flow, own engine instances —
 * nothing here touches VpnManager, AppSettings or stock profiles.
 *
 * Routing v1 is loopback endpoints + optional system proxy (mirrors the
 * reference GUI); TUN mode can reuse TunMode later without changing this
 * controller's contract.
 */
object AetherVpn : AetherBridge {
    private const val PROFILE_KEY = "aether.profile.v1"
    private const val SYSPROXY_KEY = "aether.sysproxy"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _state = MutableStateFlow(AetherUiState())
    override val state: StateFlow<AetherUiState> = _state.asStateFlow()

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
            store.getString(PROFILE_KEY)?.let { raw ->
                aetherProfileFromJson(json.parseToJsonElement(raw) as JsonObject)
            }
        }.getOrNull()
        val sys = store.getString(SYSPROXY_KEY) == "1"
        _state.value = _state.value.copy(
            profile = saved ?: AetherProfile(),
            sysProxy = sys,
        )
    }

    override fun updateProfile(p: AetherProfile) {
        _state.value = _state.value.copy(profile = p)
        if (this::store.isInitialized) {
            runCatching { store.putString(PROFILE_KEY, p.toJson().toString()) }
        }
    }

    override fun setSysProxy(on: Boolean) {
        _state.value = _state.value.copy(sysProxy = on)
        if (this::store.isInitialized) runCatching {
            store.putString(SYSPROXY_KEY, if (on) "1" else "0")
        }
        val s = _state.value
        if (on && s.phase == "live") {
            val hp = s.httpAddr.substringAfterLast(':').toIntOrNull() ?: 1820
            SystemProxy.set("127.0.0.1", hp)
        } else if (!on) {
            SystemProxy.clear()
        }
    }

    override fun toggle() {
        if (_state.value.phase == "working" || _state.value.phase == "live") disconnect()
        else connect()
    }

    private fun connect() {
        if (_state.value.phase == "working" || _state.value.phase == "live") return
        val profile = _state.value.profile
        wantStop = false
        patch(phase = "working", statusLine = "Starting Aether…", detailLine = "")
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
            val res = eng.start(
                onEvent = { ev ->
                    when (ev) {
                        is AetherLogEvent.EngineStarted ->
                            patch(phase = "working", statusLine = "Engine up — hunting a route…")
                        is AetherLogEvent.OverlayReady ->
                            patch(phase = "working", statusLine = "Overlay ready — waiting on ports…")
                        is AetherLogEvent.ScanBudget ->
                            patch(phase = "working", statusLine = "Scanning…", detailLine = "budget ${ev.secs}s")
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
                _state.value = _state.value.copy(
                    phase = "live",
                    socksAddr = profile.primaryAddr(),
                    httpAddr = "127.0.0.1:$httpPort",
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
                patch(phase = "error", statusLine = "Connection failed", detailLine = e.message ?: "")
            }
        }
    }

    private fun patch(phase: String, statusLine: String, detailLine: String = _state.value.detailLine) {
        _state.value = _state.value.copy(phase = phase, statusLine = statusLine, detailLine = detailLine)
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

    override fun disconnect() {
        wantStop = true
        stopMatrix()
        engine?.stop()
        engine = null
        SystemProxy.clear()
        lastStatsAt = 0L
        _state.value = _state.value.copy(
            phase = "idle", statusLine = "", detailLine = "",
            torPercent = null, upRate = 0L, downRate = 0L, clients = 0,
        )
    }

    /** Best-effort LAN client count (ESTABLISHED to our ports). */
    override fun refreshClients(): Int {
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

    // ---- Connection matrix: every protocol × warp/psiphon at once ----

    private val matrixTransports = listOf(
        Triple("warp", "none", 60L),
        Triple("psiphon", "psiphon", 150L),
        Triple("psiphon-only", "psiphon_only", 150L),
    )

    private fun lanePorts(lane: Int): Triple<String, String, String> {
        val socks = 1830 + lane * 2
        return Triple("127.0.0.1:$socks", "127.0.0.1:${1900 + lane * 2}", (socks + 1).toString())
    }

    private val matrixCancel = AtomicBoolean(false)
    private val matrixProtocols = listOf("auto", "masque", "wireguard", "gool", "gool-classic", "mim")

    private data class Combo(val key: String, val proto: String, val transport: String, val budget: Long)

    override fun runMatrix() {
        if (_state.value.matrixRunning) return
        if (_state.value.phase == "working" || _state.value.phase == "live") return
        matrixCancel.set(false)
        val base = _state.value.profile.copy(quickReconnect = false)
        val combos = matrixProtocols.flatMap { p ->
            matrixTransports.map { (label, transport, budget) -> Combo("$p+$label", p, transport, budget) }
        }
        _state.value = _state.value.copy(
            matrixRunning = true,
            matrix = combos.map { MatrixRow(key = it.key, proto = it.proto, trans = it.key.substringAfter('+'), phase = "idle") },
        )
        combos.forEachIndexed { i, combo ->
            thread(name = "aether-matrix-$i", isDaemon = true) {
                runMatrixCombo(i, base, combo)
            }
        }
    }

    override fun stopMatrix() {
        matrixCancel.set(true)
        if (_state.value.matrixRunning) {
            _state.value = _state.value.copy(matrixRunning = false)
        }
    }

    private fun runMatrixCombo(lane: Int, base: AetherProfile, combo: Combo) {
        setMatrixRow(combo.key, "running", false, null, null, "…")
        val (bind, psiBind, httpPort) = lanePorts(lane)
        val profile = base.copy(
            protocol = combo.proto,
            extraTransport = combo.transport,
            bind = bind,
            psiphonBind = psiBind,
            httpPort = httpPort,
        )
        val dir = File(workDir, "matrix-slot-$lane").apply { mkdirs() }
        val eng = DesktopAether(dir, profile)
        val res = eng.start(scanBudgetSecs = combo.budget, waitExtraSecs = 0L)
        if (matrixCancel.get()) {
            eng.stop()
            setMatrixRow(combo.key, "done", false, null, null, "cancelled")
            finishMatrixIfDone()
            return
        }
        if (res.isFailure) {
            eng.stop()
            setMatrixRow(combo.key, "done", false, null, null, res.exceptionOrNull()?.message ?: "failed")
            finishMatrixIfDone()
            return
        }
        val exit = profile.primaryAddr()
        // Through-tunnel latency: tiny fetch through this lane's SOCKS.
        val ping = measureThrough(bind)
        eng.stop()
        if (matrixCancel.get()) {
            setMatrixRow(combo.key, "done", false, null, null, "cancelled")
        } else if (ping != null) {
            setMatrixRow(combo.key, "done", true, ping, exit, "")
        } else {
            setMatrixRow(combo.key, "done", false, null, exit, "connected, no traffic")
        }
        finishMatrixIfDone()
    }

    private fun setMatrixRow(key: String, phase: String, ok: Boolean, ms: Long?, exit: String?, note: String) {
        _state.value = _state.value.copy(
            matrix = _state.value.matrix.map {
                if (it.key == key) it.copy(phase = phase, ok = ok, ms = ms, exit = exit, note = note) else it
            },
        )
    }

    private fun finishMatrixIfDone() {
        // Called once per finished lane-combo; exact counting via size check.
        synchronized(this) {
            val done = _state.value.matrix.count { it.phase == "done" }
            if (done >= _state.value.matrix.size && _state.value.matrix.isNotEmpty()) {
                _state.value = _state.value.copy(matrixRunning = false)
            }
        }
    }

    private fun measureThrough(socksBind: String): Long? {
        return try {
            val port = socksBind.substringAfterLast(':').toInt()
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port))
            val url = URL("https://www.cloudflare.com/cdn-cgi/trace")
            val t = System.currentTimeMillis()
            (url.openConnection(proxy) as java.net.HttpURLConnection).run {
                connectTimeout = 12000
                readTimeout = 12000
                connect()
                inputStream.use { it.readBytes() }
                disconnect()
            }
            System.currentTimeMillis() - t
        } catch (_: Exception) {
            null
        }
    }
}
