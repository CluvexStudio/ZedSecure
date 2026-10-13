package dev.cluvex.zedsecure.data.assets

import dev.cluvex.zedsecure.core.LogBus
import dev.cluvex.zedsecure.core.VpnManager
import dev.cluvex.zedsecure.domain.config.RoutingMigration
import dev.cluvex.zedsecure.domain.config.RuleValidation
import dev.cluvex.zedsecure.domain.model.AppSettings
import dev.cluvex.zedsecure.domain.model.ConnectionState
import dev.cluvex.zedsecure.domain.model.RoutingMode
import dev.cluvex.zedsecure.platform.currentTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun AppSettings.usesGeoFiles(): Boolean {
    if (blockAds || routingMode == RoutingMode.BypassIran || routingMode == RoutingMode.BypassLanAndIran) return true
    return RoutingMigration.effectiveRulesets(this).any { rule ->
        rule.enabled && (rule.domain.any(RuleValidation::isGeoDomain) || rule.ip.any(RuleValidation::isGeoIp))
    }
}

object GeoAutoFetch {
    private val _ready = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val ready: SharedFlow<Unit> = _ready.asSharedFlow()

    private val lock = Mutex()
    private var started = false
    private var lastDirect = 0L
    private var lastProxied = 0L

    fun start(scope: CoroutineScope, geo: GeoAssets, settings: () -> AppSettings) {
        if (started) return
        started = true
        scope.launch {
            VpnManager.status.map { it.state }.distinctUntilChanged().collect { state ->
                if (state == ConnectionState.Idle || state == ConnectionState.Connected) {
                    runCatching { fetchIfNeeded(geo, settings(), state == ConnectionState.Connected) }
                }
            }
        }
    }

    suspend fun fetchNow(geo: GeoAssets, settings: AppSettings): Boolean = lock.withLock { download(geo, settings) }

    private suspend fun fetchIfNeeded(geo: GeoAssets, settings: AppSettings, connected: Boolean) {
        lock.withLock {
            if (!settings.usesGeoFiles() || geo.allPresent()) return
            val now = currentTimeMillis()
            val last = if (connected) lastProxied else lastDirect
            if (now - last < RETRY_MS) return
            if (connected) lastProxied = now else lastDirect = now
            download(geo, settings)
        }
    }

    private suspend fun download(geo: GeoAssets, settings: AppSettings): Boolean {
        LogBus.append("I/GeoAssets: downloading geoip and geosite for the routing rules")
        val ok = geo.downloadAll(settings.geoFilesSource)
        if (ok) {
            LogBus.append("I/GeoAssets: geo files are ready")
            if (VpnManager.status.value.state == ConnectionState.Connected) _ready.tryEmit(Unit)
        } else {
            LogBus.append("W/GeoAssets: could not download the geo files; rules that use geosite or geoip stay off until they are in Settings › Assets")
        }
        return ok
    }

    private const val RETRY_MS = 10 * 60 * 1000L
}
