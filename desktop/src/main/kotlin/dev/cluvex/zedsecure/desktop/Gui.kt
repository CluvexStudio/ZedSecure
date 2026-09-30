package dev.cluvex.zedsecure.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import coil3.ImageLoader
import kotlinx.coroutines.launch
import coil3.compose.setSingletonImageLoaderFactory
import coil3.svg.SvgDecoder
import dev.cluvex.zedsecure.core.VpnManager
import dev.cluvex.zedsecure.data.config.ConfigRepository
import dev.cluvex.zedsecure.domain.model.ConnectionState
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.ic_tray_connected
import dev.cluvex.zedsecure.shared.resources.ic_tray_connecting
import dev.cluvex.zedsecure.shared.resources.ic_tray_disconnected
import dev.cluvex.zedsecure.shared.resources.ic_zed_mark
import dev.cluvex.zedsecure.shared.resources.zsx_expired
import dev.cluvex.zedsecure.shared.resources.zsx_invalid
import dev.cluvex.zedsecure.shared.resources.zsx_legacy
import dev.cluvex.zedsecure.core.VaultImportBus
import dev.cluvex.zedsecure.crypto.ZsxLegacyException
import org.jetbrains.compose.resources.getString
import dev.cluvex.zedsecure.domain.config.LocalProxy
import dev.cluvex.zedsecure.domain.model.RunMode
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.painterResource
import dev.cluvex.zedsecure.desktop.platform.DesktopKeyValueStore
import dev.cluvex.zedsecure.desktop.platform.DesktopProbe
import dev.cluvex.zedsecure.desktop.platform.DesktopPlatform
import dev.cluvex.zedsecure.desktop.platform.DesktopSettings
import dev.cluvex.zedsecure.desktop.platform.DesktopVpn
import dev.cluvex.zedsecure.domain.model.ThemeMode
import dev.cluvex.zedsecure.platform.AppInfo
import dev.cluvex.zedsecure.ui.MainScaffold
import dev.cluvex.zedsecure.ui.platform.LocalPlatform
import dev.cluvex.zedsecure.ui.theme.ZedSecureTheme

private val configRepository = ConfigRepository(DesktopKeyValueStore("configs"))
private val desktopSettings = DesktopSettings(DesktopKeyValueStore("settings"))

private val WIN_W = 420.dp
private val WIN_H = 860.dp

private const val TRAY_SERVERS = 30

fun main(args: Array<String>) {
    if (args.firstOrNull() == "--version") {
        println("ZedSecure $BUILD_VERSION")
        return
    }
    AppInfo.versionName = BUILD_VERSION

    runCatching {
        desktopSettings.settings.value.language.tag?.let {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag(it))
        }
    }

    DesktopVpn.settingsProvider = { desktopSettings.settings.value }

    DesktopVpn.ruleOutboundsProvider = { configRepository.resolveRuleOutbounds(it) }
    DesktopVpn.chainConfigProvider = { profile, options ->
        runCatching { configRepository.buildChainConfig(profile, options) }.getOrNull()
    }

    dev.cluvex.zedsecure.core.CoreProbe.measureDelay = { url -> DesktopProbe.measureDelay(url) }

    dev.cluvex.zedsecure.core.AutoSelect.readStatus = {
        dev.cluvex.zedsecure.desktop.platform.DesktopStats.latestAutoSelect
    }

    VpnManager.deviceVpnProbe = { dev.cluvex.zedsecure.desktop.platform.DesktopVpnDetector.anyVpn() }
    dev.cluvex.zedsecure.core.CoreProbe.measureOutboundDelay = { cfg, url ->
        DesktopProbe.measureOutboundDelay(cfg, url)
    }

    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
        while (true) {
            val s = desktopSettings.settings.value
            if (s.autoUpdateSubscriptions) {
                runCatching { configRepository.updateDueSubscriptions(s.subscriptionUpdateIntervalHours) }
            }
            kotlinx.coroutines.delay(30 * 60 * 1000L)
        }
    }
    application {
        setSingletonImageLoaderFactory { ctx ->
            ImageLoader.Builder(ctx).components { add(SvgDecoder.Factory()) }.build()
        }

        val trayAvailable = remember {
            java.awt.SystemTray.isSupported() && runCatching {
                val st = java.awt.SystemTray.getSystemTray()
                val img = java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                val probe = java.awt.TrayIcon(img)
                st.add(probe); st.remove(probe); true
            }.getOrDefault(false)
        }
        var windowVisible by remember { mutableStateOf(true) }
        var raiseWindow by remember { mutableStateOf(0) }
        val status by VpnManager.status.collectAsState()
        val connecting = status.state == ConnectionState.Connecting || status.state == ConnectionState.Disconnecting
        val connected = status.state == ConnectionState.Connected
        val trayScope = rememberCoroutineScope()

        fun reconnectIfRunning() {
            val state = VpnManager.status.value.state
            if (state != ConnectionState.Connected && state != ConnectionState.Connecting) return
            trayScope.launch {
                DesktopVpn.stop()
                kotlinx.coroutines.withTimeoutOrNull(15_000) {
                    VpnManager.status.first { it.state == ConnectionState.Idle }
                } ?: return@launch
                DesktopVpn.toggle(configRepository)
            }
        }

        fun importLockedConfig() {
            trayScope.launch {
                val pick = DesktopPlatform.pickFileBytes() ?: return@launch
                val peeked = runCatching { configRepository.peekLocked(pick.bytes) }
                val meta = peeked.getOrNull()
                when {
                    meta == null -> DesktopPlatform.toast(
                        getString(
                            if (peeked.exceptionOrNull() is ZsxLegacyException) Res.string.zsx_legacy
                            else Res.string.zsx_invalid,
                        ),
                    )
                    meta.isExpired -> DesktopPlatform.toast(getString(Res.string.zsx_expired))
                    else -> VaultImportBus.request(pick.bytes, meta)
                }
            }
        }

        if (trayAvailable) {
            val trayState = rememberTrayState()
            val profiles by configRepository.profiles.collectAsState()
            val activeId by configRepository.activeId.collectAsState()
            val settings by desktopSettings.settings.collectAsState()
            var previous by remember { mutableStateOf(status.state) }
            LaunchedEffect(status.state) {
                val was = previous
                previous = status.state
                val notice = when {
                    status.state == ConnectionState.Connected && was != ConnectionState.Connected ->
                        Notification("ZedSecure", "Connected · ${status.serverName ?: "tunnel up"}", Notification.Type.Info)
                    status.state == ConnectionState.Error ->
                        Notification(
                            "ZedSecure",
                            "Could not connect" + (status.error?.takeIf { it.isNotBlank() }?.let { ": ${it.take(140)}" } ?: ""),
                            Notification.Type.Error,
                        )
                    status.state == ConnectionState.Idle && was == ConnectionState.Disconnecting ->
                        Notification("ZedSecure", "Disconnected", Notification.Type.None)
                    else -> null
                }
                notice?.let { trayState.sendNotification(it) }
            }
            val statusLine = when {
                connected -> "Connected · ${status.serverName ?: ""}".trimEnd(' ', '·')
                status.state == ConnectionState.Connecting -> "Connecting…"
                status.state == ConnectionState.Disconnecting -> "Disconnecting…"
                status.state == ConnectionState.Error -> "Connection failed"
                else -> "Not connected"
            }
            val tip = buildString {
                append("ZedSecure · ").append(statusLine)
                if (connected) {
                    append("\n↓ ").append(humanBps(status.downloadBps)).append("   ↑ ").append(humanBps(status.uploadBps))
                }
            }
            Tray(
                state = trayState,
                icon = painterResource(
                    when {
                        connected -> Res.drawable.ic_tray_connected
                        connecting -> Res.drawable.ic_tray_connecting
                        else -> Res.drawable.ic_tray_disconnected
                    },
                ),
                tooltip = tip,
                onAction = { windowVisible = true; raiseWindow++ },
                menu = {
                    Item("ZedSecure ${AppInfo.versionName}", enabled = false) {}
                    Item(statusLine, enabled = false) {}
                    if (connected) {
                        Item("↓ ${humanBps(status.downloadBps)}    ↑ ${humanBps(status.uploadBps)}", enabled = false) {}
                    }
                    Separator()
                    Item(
                        if (connected || connecting) "Disconnect" else "Connect",
                        enabled = status.state != ConnectionState.Disconnecting,
                    ) { DesktopVpn.toggle(configRepository) }
                    if (profiles.isNotEmpty()) {
                        val active = profiles.firstOrNull { it.id == activeId }
                        val shown = (listOfNotNull(active) + profiles.filter { it.id != activeId }).take(TRAY_SERVERS)
                        Menu("Server") {
                            shown.forEach { profile ->
                                RadioButtonItem(profile.name.take(48), selected = profile.id == activeId) {
                                    if (profile.id != activeId) {
                                        configRepository.setActive(profile.id)
                                        reconnectIfRunning()
                                    }
                                }
                            }
                            if (profiles.size > shown.size) {
                                Separator()
                                Item("All ${profiles.size} servers…") { windowVisible = true; raiseWindow++ }
                            }
                        }
                    }
                    Menu("Mode") {
                        RadioButtonItem("VPN · all traffic", selected = settings.isVpnMode) {
                            if (!settings.isVpnMode) {
                                desktopSettings.update { it.copy(runMode = RunMode.Vpn) }
                                reconnectIfRunning()
                            }
                        }
                        RadioButtonItem("System proxy", selected = !settings.isVpnMode) {
                            if (settings.isVpnMode) {
                                desktopSettings.update { it.copy(runMode = RunMode.ProxyOnly) }
                                reconnectIfRunning()
                            }
                        }
                    }
                    val proxyPort = VpnManager.activeSocksPort ?: LocalProxy.SOCKS_PORT
                    Item("Copy proxy address · 127.0.0.1:$proxyPort") {
                        DesktopPlatform.copyToClipboard("127.0.0.1:$proxyPort")
                    }
                    Separator()
                    Item(if (windowVisible) "Hide window" else "Show window") {
                        windowVisible = !windowVisible
                        if (windowVisible) raiseWindow++
                    }
                    Item("Quit ZedSecure") { DesktopVpn.stop(); exitApplication() }
                },
            )
        }

        Window(
            onCloseRequest = { if (trayAvailable) windowVisible = false else { DesktopVpn.stop(); exitApplication() } },
            visible = windowVisible,
            title = "ZedSecure",
            icon = painterResource(Res.drawable.ic_zed_mark),
            resizable = false,
            state = rememberWindowState(width = WIN_W, height = WIN_H, position = WindowPosition(Alignment.Center)),
        ) {
            LaunchedEffect(raiseWindow) {
                if (raiseWindow > 0) {
                    window.isMinimized = false
                    window.toFront()
                    window.requestFocus()
                }
            }
            val settings by desktopSettings.settings.collectAsState()
            val dark = when (settings.themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            val langTag = settings.language.tag ?: "en"

            ProvideAppLanguage(langTag, dark) {
                ZedSecureTheme(
                    darkTheme = dark,
                    dynamicColor = false,
                    accentColor = settings.accentColor,
                    amoledBlack = settings.amoledBlack,
                    languageTag = langTag,
                    fontScale = settings.uiFontScale.scale,
                ) {
                    CompositionLocalProvider(LocalPlatform provides DesktopPlatform) {
                        MainScaffold(
                            settings = settings,
                            configRepository = configRepository,
                            onToggleConnection = { DesktopVpn.toggle(configRepository) },
                            onImportZsx = { importLockedConfig() },
                            onActiveServerChanged = { reconnectIfRunning() },
                            onUpdateSettings = { transform -> desktopSettings.update(transform) },
                            onLanguage = { lang -> desktopSettings.update { it.copy(language = lang) } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProvideAppLanguage(langTag: String, dark: Boolean, content: @Composable () -> Unit) {
    remember(langTag) {
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag(langTag)); langTag
    }
    content()
}

private fun humanBps(bps: Long): String {
    if (bps <= 0) return "0 B/s"
    val units = arrayOf("B/s", "KB/s", "MB/s", "GB/s")
    var v = bps.toDouble(); var i = 0
    while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
    return (if (v >= 100 || i == 0) "%.0f".format(v) else "%.1f".format(v)) + " " + units[i]
}
