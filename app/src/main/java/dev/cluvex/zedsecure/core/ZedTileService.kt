package dev.cluvex.zedsecure.core

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.cluvex.zedsecure.MainActivity
import dev.cluvex.zedsecure.R
import dev.cluvex.zedsecure.core.platform.LocaleManager
import dev.cluvex.zedsecure.data.settings.SettingsRepository
import dev.cluvex.zedsecure.domain.model.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ZedTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null

    override fun attachBaseContext(newBase: Context) {
        val tag = SettingsRepository.readLanguageTag(newBase)
        super.attachBaseContext(LocaleManager.wrap(newBase, tag))
    }

    override fun onStartListening() {
        super.onStartListening()

        runCatching { Ikev2Controller.resync(this) }
        watcher?.cancel()
        watcher = scope.launch {
            VpnManager.status.collectLatest { render(it.state, it.serverName) }
        }
    }

    override fun onStopListening() {
        watcher?.cancel()
        watcher = null
        super.onStopListening()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()

        val state = VpnManager.status.value.state
        if (state.isActive || state.isTransitioning) {
            runSafely {
                if (Ikev2Controller.isActive) Ikev2Controller.stop(this) else AndroidVpn.stop(this)
            }
        } else {
            connectOrOpen()
        }
    }

    private fun connectOrOpen() {
        val profile = runCatching {
            (application as dev.cluvex.zedsecure.ZedSecureApp).container.configRepository.activeProfile()
        }.getOrNull()
        val needsConsent = runCatching { android.net.VpnService.prepare(this) != null }.getOrDefault(true)
        if (profile == null || needsConsent || isLocked) {
            openAppToConnect()
            return
        }
        scope.launch {
            val plan = runCatching {
                kotlinx.coroutines.withContext(Dispatchers.IO) { StartPlanner(this@ZedTileService).plan(profile) }
            }.getOrNull()
            val started = plan is StartPlanner.Plan.Start && runCatching {
                AndroidVpn.start(
                    this@ZedTileService,
                    plan.configJson,
                    plan.remark,
                    plan.socksPort,
                    plan.kind,
                    proxyOnly = plan.proxyOnly,
                )
            }.isSuccess
            if (!started) {
                VpnManager.onDisconnected()
                openAppToConnect()
            }
        }
    }

    private fun runSafely(block: () -> Unit) {
        if (isLocked) unlockAndRun { block() } else block()
    }

    private fun openAppToConnect() {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = VpnNotifications.ACTION_CONNECT
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    REQUEST_CONNECT,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        } else {
            startActivityAndCollapseLegacy(intent)
        }
    }

    @Suppress("DEPRECATION")
    private fun startActivityAndCollapseLegacy(intent: Intent) = startActivityAndCollapse(intent)

    private fun render(state: ConnectionState, serverName: String?) {
        val tile = qsTile ?: return
        tile.state = if (state.isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.icon = Icon.createWithResource(
            this,
            R.drawable.ic_tile_zed,
        )
        tile.label = getString(R.string.app_name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                isSecure -> null
                state.isActive -> serverName
                else -> getString(R.string.state_idle)
            }
        }
        tile.updateTile()
    }

    private companion object {
        const val REQUEST_CONNECT = 4201
    }
}
