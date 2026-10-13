package dev.cluvex.zedsecure.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import dev.cluvex.zedsecure.ui.platform.DesktopBack
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
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
import dev.cluvex.zedsecure.core.LogBus
import dev.cluvex.zedsecure.domain.model.AppLanguage
import dev.cluvex.zedsecure.domain.model.ConnectionState
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.state_connected
import dev.cluvex.zedsecure.shared.resources.state_connecting
import dev.cluvex.zedsecure.shared.resources.state_disconnecting
import dev.cluvex.zedsecure.shared.resources.state_error
import dev.cluvex.zedsecure.shared.resources.state_idle
import dev.cluvex.zedsecure.shared.resources.action_connect
import dev.cluvex.zedsecure.shared.resources.action_disconnect
import dev.cluvex.zedsecure.shared.resources.tray_server
import dev.cluvex.zedsecure.shared.resources.tray_mode
import dev.cluvex.zedsecure.shared.resources.tray_copy_proxy
import dev.cluvex.zedsecure.shared.resources.tray_show
import dev.cluvex.zedsecure.shared.resources.tray_hide
import dev.cluvex.zedsecure.shared.resources.tray_quit
import dev.cluvex.zedsecure.shared.resources.mode_system_proxy
import dev.cluvex.zedsecure.shared.resources.mode_socks
import dev.cluvex.zedsecure.shared.resources.mode_tun
import dev.cluvex.zedsecure.shared.resources.tray_all_servers
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
import dev.cluvex.zedsecure.domain.model.RenderingMode
import dev.cluvex.zedsecure.domain.model.RunMode
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.cluvex.zedsecure.desktop.core.Os
import dev.cluvex.zedsecure.desktop.core.TunMode
import dev.cluvex.zedsecure.desktop.platform.AdminPassword
import dev.cluvex.zedsecure.desktop.platform.DesktopKeyValueStore
import dev.cluvex.zedsecure.desktop.platform.DesktopProbe
import dev.cluvex.zedsecure.desktop.platform.DesktopPlatform
import dev.cluvex.zedsecure.desktop.platform.DesktopSettings
import dev.cluvex.zedsecure.desktop.platform.DesktopVpn
import dev.cluvex.zedsecure.domain.model.ThemeMode
import dev.cluvex.zedsecure.platform.AppInfo
import dev.cluvex.zedsecure.ui.MainScaffold
import dev.cluvex.zedsecure.ui.platform.LocalPlatform
import dev.cluvex.zedsecure.ui.theme.LocalMotionBudget
import dev.cluvex.zedsecure.ui.theme.MotionBudget
import dev.cluvex.zedsecure.ui.theme.ZedSecureTheme
import androidx.compose.ui.platform.LocalWindowInfo
import java.awt.GraphicsEnvironment

private val configRepository by lazy { ConfigRepository(DesktopKeyValueStore("configs")) }
private val desktopSettings by lazy { DesktopSettings(DesktopKeyValueStore("settings")) }

private val WIN_W = 420.dp
private val WIN_H = 860.dp
private val WIN_MIN_H = 480.dp
private val SCREEN_MARGIN = 16.dp

private fun fittedWindowHeight(): Dp {
    val usable = runCatching { GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds.height }
        .getOrNull()?.takeIf { it > 0 } ?: return WIN_H
    return minOf(WIN_H, maxOf(WIN_MIN_H, usable.dp - SCREEN_MARGIN))
}

private const val TRAY_SERVERS = 30

fun main(args: Array<String>) {
    if (args.firstOrNull() == dev.cluvex.zedsecure.desktop.core.WindowsHelper.FLAG) {
        dev.cluvex.zedsecure.desktop.core.WindowsHelper.serve(args.drop(1))
        return
    }
    if (args.firstOrNull() == "--version") {
        println("ZedSecure $BUILD_VERSION")
        return
    }
    AppInfo.versionName = BUILD_VERSION
    Runtime.getRuntime().addShutdownHook(Thread { runCatching { DesktopVpn.shutdown() } })

    val rendering = DesktopRendering.apply(
        runCatching { desktopSettings.settings.value.renderingMode }.getOrDefault(RenderingMode.Auto),
        args,
    )
    LogBus.append(
        "I/Desktop rendering: ${if (rendering.software) "software" else "graphics card"} " +
            "(${rendering.source.name.lowercase()}, automatic would pick ${rendering.automatic.name})",
    )

    runCatching {
        desktopSettings.settings.value.language.tag?.let {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag(it))
        }
    }

    val background = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    wireDesktopServices(configRepository, desktopSettings, background)

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

        val nativeTrayHost = remember {
            when (Os.current) {
                Os.LINUX -> LinuxDesktop.trayHostAvailable() && LinuxDesktop.nativeTrayLoads()
                Os.WINDOWS -> WindowsDesktop.nativeTrayLoads()
                else -> false
            }
        }
        var nativeTrayFailed by remember { mutableStateOf(false) }
        val nativeTray = nativeTrayHost && !nativeTrayFailed
        val trayAvailable = remember(nativeTray) {
            nativeTray || java.awt.SystemTray.isSupported() && runCatching {
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
                notice?.let {
                    when {
                        !nativeTray -> trayState.sendNotification(it)
                        Os.current == Os.WINDOWS -> WindowsDesktop.notify(it.title, it.message)
                        else -> LinuxDesktop.notify(it.title, it.message)
                    }
                }
            }
            val stateConnected = stringResource(Res.string.state_connected)
            val stateConnecting = stringResource(Res.string.state_connecting)
            val stateDisconnecting = stringResource(Res.string.state_disconnecting)
            val stateError = stringResource(Res.string.state_error)
            val stateIdle = stringResource(Res.string.state_idle)
            val labelConnect = stringResource(Res.string.action_connect)
            val labelDisconnect = stringResource(Res.string.action_disconnect)
            val labelServer = stringResource(Res.string.tray_server)
            val labelMode = stringResource(Res.string.tray_mode)
            val labelCopyProxy = stringResource(Res.string.tray_copy_proxy)
            val labelShow = stringResource(Res.string.tray_show)
            val labelHide = stringResource(Res.string.tray_hide)
            val labelQuit = stringResource(Res.string.tray_quit)
            val modeSystem = stringResource(Res.string.mode_system_proxy)
            val modeSocks = stringResource(Res.string.mode_socks)
            val modeTun = stringResource(Res.string.mode_tun)
            val allServers = stringResource(Res.string.tray_all_servers, profiles.size)
            val statusLine = when {
                connected -> listOfNotNull(stateConnected, status.serverName?.takeIf { it.isNotBlank() }).joinToString(" · ")
                status.state == ConnectionState.Connecting -> stateConnecting
                status.state == ConnectionState.Disconnecting -> stateDisconnecting
                status.state == ConnectionState.Error -> stateError
                else -> stateIdle
            }
            val speeds = "↓ ${humanBps(status.downloadBps)}   ↑ ${humanBps(status.uploadBps)}"
            val tip = buildString {
                append("ZedSecure · ").append(statusLine)
                if (connected) append("\n").append(speeds)
            }
            val active = profiles.firstOrNull { it.id == activeId }
            val shown = (listOfNotNull(active) + profiles.filter { it.id != activeId }).take(TRAY_SERVERS)
            val proxyPort = VpnManager.activeSocksPort ?: LocalProxy.SOCKS_PORT
            val entries = buildList {
                add(TrayEntry.Action(statusLine, enabled = false))
                if (connected) add(TrayEntry.Action(speeds, enabled = false))
                add(TrayEntry.Separator)
                add(
                    TrayEntry.Action(
                        if (connected || connecting) labelDisconnect else labelConnect,
                        enabled = status.state != ConnectionState.Disconnecting,
                    ) { DesktopVpn.toggle(configRepository) },
                )
                if (profiles.isNotEmpty()) {
                    add(
                        TrayEntry.Sub(
                            labelServer,
                            shown.map { profile ->
                                TrayEntry.Check(profile.name.take(48), checked = profile.id == activeId) {
                                    if (profile.id != activeId) {
                                        configRepository.setActive(profile.id)
                                        reconnectIfRunning()
                                    }
                                }
                            } + if (profiles.size > shown.size) {
                                listOf(
                                    TrayEntry.Separator,
                                    TrayEntry.Action(allServers) { windowVisible = true; raiseWindow++ },
                                )
                            } else {
                                emptyList()
                            },
                        ),
                    )
                }
                add(
                    TrayEntry.Sub(
                        labelMode,
                        listOfNotNull(
                            RunMode.SystemProxy to modeSystem,
                            RunMode.ProxyOnly to modeSocks,
                            (RunMode.Vpn to modeTun).takeIf { TunMode.supported() },
                        ).map { (mode, label) ->
                            TrayEntry.Check(label, checked = settings.runMode == mode) {
                                if (settings.runMode != mode) {
                                    desktopSettings.update { it.copy(runMode = mode) }
                                    reconnectIfRunning()
                                }
                            }
                        },
                    ),
                )
                add(TrayEntry.Action("$labelCopyProxy · 127.0.0.1:$proxyPort") {
                    DesktopPlatform.copyToClipboard("127.0.0.1:$proxyPort")
                })
                add(TrayEntry.Separator)
                add(TrayEntry.Action(if (windowVisible) labelHide else labelShow) {
                    windowVisible = !windowVisible
                    if (windowVisible) raiseWindow++
                })
                add(TrayEntry.Action(labelQuit) { DesktopVpn.shutdown(); exitApplication() })
            }
            val trayIconKey = when {
                connected -> "connected"
                connecting -> "connecting"
                else -> "disconnected"
            }
            val trayIcon = painterResource(
                when (trayIconKey) {
                    "connected" -> Res.drawable.ic_tray_connected
                    "connecting" -> Res.drawable.ic_tray_connecting
                    else -> Res.drawable.ic_tray_disconnected
                },
            )
            if (nativeTray) {
                NativeTray(
                    iconKey = trayIconKey,
                    icon = trayIcon,
                    tooltip = tip,
                    onOpen = { windowVisible = true; raiseWindow++ },
                    onFailure = { nativeTrayFailed = true },
                    entries = entries,
                )
            } else {
                Tray(
                    state = trayState,
                    icon = trayIcon,
                    tooltip = tip,
                    onAction = { windowVisible = true; raiseWindow++ },
                    menu = { awtEntries(entries) },
                )
            }
        }

        val windowState = rememberWindowState(width = WIN_W, height = fittedWindowHeight(), position = WindowPosition(Alignment.Center))
        Window(
            onCloseRequest = { if (trayAvailable) windowVisible = false else { DesktopVpn.shutdown(); exitApplication() } },
            visible = windowVisible,
            title = "ZedSecure",
            icon = painterResource(Res.drawable.ic_zed_mark),
            resizable = false,
            state = windowState,
            onKeyEvent = { event ->
                event.type == KeyEventType.KeyUp && event.key == Key.Escape && DesktopBack.dispatch()
            },
        ) {
            DisposableEffect(Unit) {
                val listener = java.awt.event.AWTEventListener { event ->
                    if (event is java.awt.event.MouseEvent && event.id == java.awt.event.MouseEvent.MOUSE_PRESSED &&
                        event.button == MOUSE_BACK_BUTTON
                    ) {
                        DesktopBack.dispatch()
                    }
                }
                java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(listener, java.awt.AWTEvent.MOUSE_EVENT_MASK)
                onDispose { java.awt.Toolkit.getDefaultToolkit().removeAWTEventListener(listener) }
            }
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
            val langTag = languageTagFor(settings.language)

            ProvideAppLanguage(langTag) {
                ZedSecureTheme(
                    darkTheme = dark,
                    dynamicColor = false,
                    accentColor = settings.accentColor,
                    amoledBlack = settings.amoledBlack,
                    colorStyle = settings.colorStyle,
                    colorContrast = settings.colorContrast,
                    design = settings.appDesign,
                    animateColors = !settings.reduceMotion,
                    languageTag = langTag,
                    fontScale = settings.uiFontScale.scale,
                ) {
                    val focused = LocalWindowInfo.current.isWindowFocused
                    val motion = if (windowVisible && !windowState.isMinimized && focused) {
                        MotionBudget.Throttled
                    } else {
                        MotionBudget.Paused
                    }
                    CompositionLocalProvider(LocalPlatform provides DesktopPlatform, LocalMotionBudget provides motion) {
                        MainScaffold(
                            settings = settings,
                            configRepository = configRepository,
                            onToggleConnection = { DesktopVpn.toggle(configRepository) },
                            onImportZsx = { importLockedConfig() },
                            onActiveServerChanged = { reconnectIfRunning() },
                            onUpdateSettings = { transform -> desktopSettings.update(transform) },
                            onLanguage = { lang -> desktopSettings.update { it.copy(language = lang) } },
                        )
                        val passwordRequest by AdminPassword.request.collectAsState()
                        passwordRequest?.let { request ->
                            LaunchedEffect(request) {
                                windowVisible = true
                                raiseWindow++
                            }
                            AdminPasswordDialog(
                                request.retry,
                                offerRemember = dev.cluvex.zedsecure.desktop.core.Os.current == dev.cluvex.zedsecure.desktop.core.Os.LINUX,
                            ) { AdminPassword.answer(request, it) }
                        }
                    }
                }
            }
        }
    }
}

internal fun wireDesktopServices(
    configRepository: ConfigRepository,
    desktopSettings: DesktopSettings,
    background: kotlinx.coroutines.CoroutineScope,
) {
    DesktopVpn.settingsProvider = { desktopSettings.settings.value }

    DesktopVpn.ruleOutboundsProvider = { configRepository.resolveRuleOutbounds(it) }
    DesktopVpn.ruleGroupsProvider = { configRepository.resolveRuleGroups(it) }
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

    dev.cluvex.zedsecure.data.assets.GeoAutoFetch.start(background, dev.cluvex.zedsecure.desktop.core.DesktopGeoAssets) {
        desktopSettings.settings.value
    }
    background.launch {
        dev.cluvex.zedsecure.data.assets.GeoAutoFetch.ready.collect { DesktopVpn.reconnect(configRepository) }
    }
}

private val systemLanguageTag: String =
    java.util.Locale.getDefault(java.util.Locale.Category.DISPLAY).language
        .takeIf { tag -> AppLanguage.entries.any { it.tag == tag } } ?: "en"

internal fun languageTagFor(language: AppLanguage): String = language.tag ?: systemLanguageTag

@Composable
internal fun ProvideAppLanguage(langTag: String, content: @Composable () -> Unit) {
    val locale = remember(langTag) {
        java.util.Locale.forLanguageTag(langTag).also { java.util.Locale.setDefault(it) }
    }
    val direction = if (java.awt.ComponentOrientation.getOrientation(locale).isLeftToRight) LayoutDirection.Ltr else LayoutDirection.Rtl
    CompositionLocalProvider(LocalLayoutDirection provides direction, content = content)
}

private fun humanBps(bps: Long): String {
    if (bps <= 0) return "0 B/s"
    val units = arrayOf("B/s", "KB/s", "MB/s", "GB/s")
    var v = bps.toDouble(); var i = 0
    while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
    return (if (v >= 100 || i == 0) "%.0f".format(v) else "%.1f".format(v)) + " " + units[i]
}

private const val MOUSE_BACK_BUTTON = 4
