package dev.cluvex.zedsecure.di

import android.content.Context
import dev.cluvex.zedsecure.BuildConfig
import dev.cluvex.zedsecure.core.CoreProbe
import dev.cluvex.zedsecure.core.LogcatTail
import dev.cluvex.zedsecure.core.VpnManager
import dev.cluvex.zedsecure.core.XrayController
import dev.cluvex.zedsecure.data.assets.GeoAssetsRepository
import dev.cluvex.zedsecure.data.config.ConfigRepository
import dev.cluvex.zedsecure.data.settings.SettingsRepository
import dev.cluvex.zedsecure.domain.model.ConnectionState
import dev.cluvex.zedsecure.platform.AndroidKeyValueStore
import dev.cluvex.zedsecure.platform.AppInfo
import dev.cluvex.zedsecure.platform.DeviceIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import libv2ray.Libv2ray

class AppContainer(context: Context) {
    val settingsRepository: SettingsRepository = SettingsRepository(context)
    val configRepository: ConfigRepository = ConfigRepository(AndroidKeyValueStore(context, "zed_configs"))

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        AppInfo.versionName = BuildConfig.VERSION_NAME

        DeviceIdentity.id = runCatching {
            android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID,
            )
        }.getOrNull().orEmpty()

        dev.cluvex.zedsecure.data.net.AndroidMtuProbe.install(context)

        val connectivity = context.getSystemService(android.net.ConnectivityManager::class.java)
        VpnManager.deviceVpnProbe = { connectivity != null && anyVpnNetwork(connectivity) }
        CoreProbe.measureDelay = { url -> XrayController.measureDelay(url) }
        CoreProbe.measureDelayDetailed = { url -> XrayController.measureDelayDetailed(url) }
        CoreProbe.measureOutboundDelay = { cfg, url ->
            runCatching { Libv2ray.measureOutboundDelay(cfg, url) }.getOrDefault(-1L)
        }

        dev.cluvex.zedsecure.core.AutoSelect.readStatus = {
            runCatching { Libv2ray.autoSelectStatus() }.getOrNull()
        }
        dev.cluvex.zedsecure.core.AutoSelect.pinMember = { group, member ->
            runCatching { Libv2ray.autoSelectPin(group, member) }.getOrDefault(false)
        }
        dev.cluvex.zedsecure.core.AutoSelect.networkChanged = {
            runCatching { Libv2ray.autoSelectNetworkChanged() }
        }
        dev.cluvex.zedsecure.core.AutoSelect.supportsPinning = true
        TrafficAccountant(configRepository, scope)

        LogcatTail.register(scope)

        scope.launch {
            runCatching { GeoAssetsRepository(context).seedBundled(BuildConfig.VERSION_CODE.toLong()) }
        }

        runCatching {
            dev.cluvex.zedsecure.domain.config.RoutingMigration
                .seedIfFresh(settingsRepository.settings.value)
                ?.let { seeded -> settingsRepository.update { seeded } }
        }

        scope.launch {
            combine(settingsRepository.settings, configRepository.subscriptions) { s, subs -> s to subs }
                .distinctUntilChangedBy { (s, subs) ->
                    Triple(
                        s.autoUpdateSubscriptions,
                        dev.cluvex.zedsecure.domain.config.SubscriptionSchedule
                            .periodHours(subs, s.subscriptionUpdateIntervalHours),
                        subs.any { it.enabled },
                    )
                }
                .collect { (s, subs) ->
                    runCatching {
                        dev.cluvex.zedsecure.core.SubscriptionUpdateScheduler.sync(context, s, subs)
                    }
                }
        }

        scope.launch {
            var counted = -1
            VpnManager.status.collect { s ->
                if (s.state == ConnectionState.Connected && s.sessionId != counted) {
                    counted = s.sessionId
                    settingsRepository.update { it.copy(successfulConnections = it.successfulConnections + 1) }
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun anyVpnNetwork(connectivity: android.net.ConnectivityManager): Boolean =
    connectivity.allNetworks.any { network ->
        connectivity.getNetworkCapabilities(network)
            ?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true
    }

private class TrafficAccountant(
    private val repository: ConfigRepository,
    scope: CoroutineScope,
) {
    init {
        scope.launch {
            var session = -1
            var lastDown = 0L
            var lastUp = 0L
            var activeId: String? = null

            VpnManager.status.collect { s ->
                when (s.state) {
                    ConnectionState.Connected -> {
                        if (s.sessionId != session) {
                            session = s.sessionId
                            lastDown = 0
                            lastUp = 0
                            activeId = repository.activeId.value
                        }

                        val autoMember = dev.cluvex.zedsecure.core.AutoSelect.session.value
                            ?.takeIf { it.profileId == activeId }?.selectedProfileId
                        val id = autoMember ?: activeId ?: return@collect
                        val dDown = (s.totalDownload - lastDown).coerceAtLeast(0)
                        val dUp = (s.totalUpload - lastUp).coerceAtLeast(0)
                        if (dDown > 0 || dUp > 0) {
                            repository.addUsage(id, dDown, dUp, persist = false)
                            lastDown = s.totalDownload
                            lastUp = s.totalUpload
                        }
                    }
                    ConnectionState.Idle, ConnectionState.Error -> {
                        activeId?.let { repository.addUsage(it, 0, 0, persist = true) }
                        activeId = null
                        session = -1
                        lastDown = 0
                        lastUp = 0
                    }
                    else -> Unit
                }
            }
        }
    }
}
