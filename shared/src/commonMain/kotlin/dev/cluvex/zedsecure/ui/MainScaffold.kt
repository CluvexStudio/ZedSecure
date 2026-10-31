package dev.cluvex.zedsecure.ui

import dev.cluvex.zedsecure.ui.platform.BackHandler
import dev.cluvex.zedsecure.ui.home.HomeFabCluster
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.cluvex.zedsecure.data.config.ConfigRepository
import dev.cluvex.zedsecure.domain.config.effectiveLocalProxyPort
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.auto_group_all
import dev.cluvex.zedsecure.shared.resources.auto_title_for
import dev.cluvex.zedsecure.shared.resources.tour_done
import dev.cluvex.zedsecure.shared.resources.tour_next
import dev.cluvex.zedsecure.shared.resources.tour_skip
import dev.cluvex.zedsecure.domain.model.AppLanguage
import dev.cluvex.zedsecure.domain.model.AppSettings
import dev.cluvex.zedsecure.ui.servers.ServersScreen
import dev.cluvex.zedsecure.ui.theme.toPersonalization
import dev.cluvex.zedsecure.ui.connection.ConnectionViewModel
import dev.cluvex.zedsecure.ui.easteregg.SpaceScreen
import dev.cluvex.zedsecure.ui.home.HomeScreen
import dev.cluvex.zedsecure.ui.motion.transitionDecoration
import dev.cluvex.zedsecure.ui.navigation.TopDestination
import dev.cluvex.zedsecure.ui.settings.SettingsScreen
import dev.cluvex.zedsecure.ui.update.NudgeHost
import dev.cluvex.zedsecure.ui.vault.VaultImportHost
import dev.cluvex.zedsecure.ui.vault.VaultScreen

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainScaffold(
    settings: AppSettings,
    configRepository: ConfigRepository,
    onToggleConnection: () -> Unit,

    onActiveServerChanged: () -> Unit = {},
    onImportZsx: () -> Unit,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onLanguage: (AppLanguage) -> Unit,

    aiBridge: dev.cluvex.zedsecure.domain.ai.AiAppBridge? = null,
    appVersion: String = "",
) {
    val platform = dev.cluvex.zedsecure.ui.platform.LocalPlatform.current
    var current by rememberSaveable { mutableStateOf(TopDestination.Home) }

    var setupDone by rememberSaveable {
        mutableStateOf(settings.onboardingVersion >= dev.cluvex.zedsecure.ui.onboarding.ONBOARDING_VERSION)
    }
    var tourStep by rememberSaveable { mutableStateOf(-1) }
    var spaceUnlocked by rememberSaveable { mutableStateOf(false) }

    var deepLinkOrigin by rememberSaveable { mutableStateOf<TopDestination?>(null) }

    var pendingSettingsPage by rememberSaveable {
        mutableStateOf(dev.cluvex.zedsecure.ui.settings.SettingsPage.Root)
    }
    var serversAddRequest by rememberSaveable { mutableStateOf(false) }
    val bento = settings.appDesign == dev.cluvex.zedsecure.domain.model.AppDesign.Bento
    var dockShown by rememberSaveable { mutableStateOf(true) }
    androidx.compose.runtime.LaunchedEffect(current) { dockShown = true }
    val autoHide = settings.bento.dockAutoHide
    val dockScroll = androidx.compose.runtime.remember(autoHide) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPostScroll(
                consumed: androidx.compose.ui.geometry.Offset,
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
            ): androidx.compose.ui.geometry.Offset {
                if (autoHide) {
                    if (consumed.y < -4f) dockShown = false else if (consumed.y > 4f || available.y > 24f) dockShown = true
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }
    val connectionVm: ConnectionViewModel = viewModel { ConnectionViewModel() }
    val homeVm: dev.cluvex.zedsecure.ui.home.HomeViewModel = viewModel { dev.cluvex.zedsecure.ui.home.HomeViewModel() }
    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    val profiles by configRepository.profiles.collectAsStateWithLifecycle()
    val subscriptions by configRepository.subscriptions.collectAsStateWithLifecycle()
    val activeId by configRepository.activeId.collectAsStateWithLifecycle()

    val activeProfile = androidx.compose.runtime.remember(profiles, subscriptions, activeId) {
        activeId?.let { configRepository.profile(it) }
    }
    val autoSession by dev.cluvex.zedsecure.core.AutoSelect.session.collectAsStateWithLifecycle()

    val lockedNote = activeProfile?.takeIf { it.isLocked }?.note

    val mtuHint = androidx.compose.runtime.remember(activeProfile) {
        val parsed = activeProfile?.linkServer
        val host = parsed?.address?.takeIf { it.isNotBlank() } ?: activeProfile?.address.orEmpty()
        dev.cluvex.zedsecure.data.net.MtuServerHint(
            host = host,
            overhead = parsed
                ?.let { dev.cluvex.zedsecure.data.net.MtuOverheads.ofServer(it) }
                ?: dev.cluvex.zedsecure.data.net.MtuOverheads.worstCase(),
        )
    }

    VaultImportHost(
        repository = configRepository,
        onImported = { current = TopDestination.Vault },
    )

    dev.cluvex.zedsecure.ui.servers.DeepLinkImportHost(
        repository = configRepository,
        onImported = { current = TopDestination.Servers },
    )

    NudgeHost(settings = settings, onUpdateSettings = onUpdateSettings)

    BackHandler(enabled = current != TopDestination.Home) { current = TopDestination.Home }

    BackHandler(enabled = tourStep >= 0) {
        tourStep = -1
        current = TopDestination.Home
    }

    if (spaceUnlocked) {
        SpaceScreen(onExit = { spaceUnlocked = false })
        return
    }

    if (!setupDone) {
        dev.cluvex.zedsecure.ui.onboarding.OnboardingFlow(
            settings = settings,
            onUpdate = onUpdateSettings,
            onLanguage = onLanguage,
            onStartTour = {
                onUpdateSettings {
                    it.copy(onboardingVersion = dev.cluvex.zedsecure.ui.onboarding.ONBOARDING_VERSION)
                }
                setupDone = true
                current = TopDestination.Home
                tourStep = 0
            },
            onFinish = {
                onUpdateSettings {
                    it.copy(onboardingVersion = dev.cluvex.zedsecure.ui.onboarding.ONBOARDING_VERSION)
                }
                setupDone = true
            },
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        bottomBar = {
            if (!bento) {
                dev.cluvex.zedsecure.ui.navigation.ZedNavBar(
                    style = settings.navBarStyle,
                    current = current,
                    onSelect = { current = it },
                )
            }
        },
    ) { scaffoldPadding ->
      val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
      val innerPadding = if (bento) {
          PaddingValues(
              start = scaffoldPadding.calculateStartPadding(layoutDirection),
              top = scaffoldPadding.calculateTopPadding(),
              end = scaffoldPadding.calculateEndPadding(layoutDirection),
              bottom = scaffoldPadding.calculateBottomPadding() + dev.cluvex.zedsecure.ui.bento.BentoDockHeight +
                  dev.cluvex.zedsecure.ui.bento.BentoDockMargin + 8.dp,
          )
      } else {
          scaffoldPadding
      }
      androidx.compose.foundation.layout.Box(
          Modifier
              .fillMaxSize()
              .then(if (bento) Modifier.nestedScroll(dockScroll) else Modifier),
      ) {
        val sign = dev.cluvex.zedsecure.ui.motion.layoutSign()
        AnimatedContent(
            targetState = current,
            transitionSpec = {
                dev.cluvex.zedsecure.ui.motion.screenTransition(
                    style = settings.screenTransition,
                    forward = targetState.ordinal >= initialState.ordinal,
                    direction = sign,
                    reduceMotion = settings.reduceMotion,
                ).using(SizeTransform(clip = false))
            },
            label = "top-destination",
            modifier = Modifier.fillMaxSize(),
        ) { dest ->

            val decoration = transitionDecoration(
                style = settings.screenTransition,
                forward = dest.ordinal >= current.ordinal,
                direction = sign,
                reduceMotion = settings.reduceMotion,
            )
            Box(decoration) {
            when (dest) {
                TopDestination.Home -> {
                  val homeName = activeProfile?.let { p ->
                      if (p.isAutoSelect) {
                          stringResource(
                              Res.string.auto_title_for,
                              dev.cluvex.zedsecure.ui.servers.autoSelectGroupLabel(p),
                          )
                      } else p.name
                  }
                  val homeDetail = activeProfile?.takeIf { it.isAutoSelect }?.let { p ->
                      autoSession?.takeIf { it.profileId == p.id }?.let { session ->
                          dev.cluvex.zedsecure.ui.servers.autoSelectLiveLine(session) { id ->
                              profiles.firstOrNull { it.id == id }?.name
                          }
                      }
                  }
                  HomeScreen(
                    connectionVm = connectionVm,
                    homeVm = homeVm,
                    activeConfigName = homeName,
                    exitGeneration = autoSession?.takeIf { it.profileId == activeProfile?.id }?.exitGeneration ?: 0,
                    activeConfigDetail = homeDetail,
                    lockedNote = lockedNote,
                    localProxyAddress = if (settings.runMode == dev.cluvex.zedsecure.domain.model.RunMode.ProxyOnly) {
                        "127.0.0.1:${dev.cluvex.zedsecure.core.VpnManager.activeSocksPort ?: settings.effectiveLocalProxyPort}"
                    } else null,
                    activeLocked = activeProfile?.isLocked == true,
                    reduceMotion = settings.reduceMotion,

                    showConnectionInfo = activeProfile?.isDnsTunnel != true,
                    ipApiUrl = settings.ipApiUrl,
                    delayTestUrl = settings.delayTestUrl,

                    personalization = settings.toPersonalization(),
                    connectStyle = settings.connectButtonStyle,
                    design = settings.appDesign,
                    contentPadding = innerPadding,
                    onToggleConnection = onToggleConnection,

                    onBrowseConfigs = {
                        current = if (activeProfile?.isLocked == true) TopDestination.Vault
                        else TopDestination.Servers
                    },
                    onSecretUnlocked = { spaceUnlocked = true },
                    bentoActive = activeProfile?.let { p ->
                        dev.cluvex.zedsecure.ui.bento.BentoActiveServer(
                            name = homeName ?: p.name,
                            detail = homeDetail ?: p.transportLabel.removeSuffix(" · sing-box"),
                            country = dev.cluvex.zedsecure.domain.config.ProfileCountry.of(p),
                            engine = dev.cluvex.zedsecure.ui.bento.bentoEngineOf(p),
                            locked = p.isLocked,
                        )
                    },
                    bentoPicks = androidx.compose.runtime.remember(profiles, activeId) {
                        profiles
                            .filter { !it.isLocked && it.id != activeId && (it.lastPingMs ?: 0) > 0 }
                            .sortedBy { it.lastPingMs }
                            .take(3)
                            .map {
                                dev.cluvex.zedsecure.ui.bento.BentoServerPick(
                                    id = it.id,
                                    name = it.name,
                                    detail = it.transportLabel.removeSuffix(" · sing-box"),
                                    country = dev.cluvex.zedsecure.domain.config.ProfileCountry.of(it),
                                    pingMs = it.lastPingMs,
                                )
                            }
                    },
                    onPickServer = { id ->
                        configRepository.setActive(id)
                        onActiveServerChanged()
                    },
                    onAddServer = {
                        serversAddRequest = true
                        current = TopDestination.Servers
                    },
                    onOpenSpeedTest = {
                        deepLinkOrigin = current
                        pendingSettingsPage = dev.cluvex.zedsecure.ui.settings.SettingsPage.SpeedTest
                        current = TopDestination.Settings
                    },
                    onOpenMap = {
                        deepLinkOrigin = current
                        pendingSettingsPage = dev.cluvex.zedsecure.ui.settings.SettingsPage.Map
                        current = TopDestination.Settings
                    },
                  )
                }
                TopDestination.Servers -> ServersScreen(
                    repository = configRepository,
                    contentPadding = innerPadding,
                    twoColumns = settings.doubleColumnDisplay,
                    showAllGroup = settings.groupAllDisplay,
                    confirmRemove = settings.confirmRemove,
                    realPingConcurrency = settings.realPingConcurrency,
                    delayTestUrl = settings.delayTestUrl,
                    autoTestAfterUpdate = settings.autoTestAfterUpdate,
                    autoRemoveInvalidAfterTest = settings.autoRemoveInvalidAfterTest,
                    autoSortAfterTest = settings.autoSortAfterTest,
                    personalization = settings.toPersonalization(),
                    reduceMotion = settings.reduceMotion,
                    onServerActivated = {
                        if (settings.homeAfterSelect) current = TopDestination.Home

                        onActiveServerChanged()
                    },
                    detectServerLocation = settings.detectServerLocation,
                    addRequest = serversAddRequest,
                    onAddRequestConsumed = { serversAddRequest = false },
                )
                TopDestination.Vault -> VaultScreen(
                    repository = configRepository,
                    contentPadding = innerPadding,
                    onImportRequested = onImportZsx,
                )
                TopDestination.Settings -> SettingsScreen(
                    settings = settings,
                    contentPadding = innerPadding,
                    onUpdate = onUpdateSettings,
                    onLanguage = onLanguage,
                    initialPage = pendingSettingsPage,
                    onDeepLinkBack = deepLinkOrigin?.let { origin ->
                        {
                            current = origin
                            deepLinkOrigin = null
                        }
                    },
                    onInitialPageConsumed = {
                        pendingSettingsPage = dev.cluvex.zedsecure.ui.settings.SettingsPage.Root
                    },

                    serverTargets = profiles
                        .filter { it.standardLink != null }
                        .map { it.id to it.name },
                    autoCountries = androidx.compose.runtime.remember(profiles) {
                        profiles.mapNotNull { dev.cluvex.zedsecure.domain.config.ProfileCountry.of(it) }.distinct()
                    },
                    groupTargets = listOf(
                        dev.cluvex.zedsecure.domain.config.AutoSelectIds.ALL to
                            stringResource(Res.string.auto_title_for, stringResource(Res.string.auto_group_all)),
                    ) + subscriptions.map { sub ->
                        dev.cluvex.zedsecure.domain.config.AutoSelectIds.of(sub.id) to stringResource(Res.string.auto_title_for, sub.name)
                    },
                    dnsTunnelActive = activeProfile?.isDnsBasedTunnel == true,

                    onReplayTour = {
                        current = TopDestination.Home
                        tourStep = 0
                    },

                    mtuHint = mtuHint,
                    ai = aiBridge?.let { bridge ->
                        dev.cluvex.zedsecure.ui.settings.AiSection(
                            settings = settings.ai,
                            onUpdate = { next -> onUpdateSettings { it.copy(ai = next) } },
                            bridge = bridge,
                            onOpenUrl = { url -> platform.openUri(url) },

                            languageName = languageEnglishName(settings.language),
                            languageNative = languageNativeName(settings.language),
                            platform = "Android",
                            appVersion = appVersion,
                        )
                    },
                )
            }
            }
        }

        val vpnStatus by dev.cluvex.zedsecure.core.VpnManager.status.collectAsStateWithLifecycle()
        val detailsOpen by homeVm.expanded.collectAsStateWithLifecycle()
        HomeFabCluster(
            visible = current == TopDestination.Home && !bento &&
                !(detailsOpen && settings.appDesign == dev.cluvex.zedsecure.domain.model.AppDesign.Pixel),
            showSpeedTest = vpnStatus.state == dev.cluvex.zedsecure.domain.model.ConnectionState.Connected &&
                activeProfile?.isDnsBasedTunnel != true,
            reduceMotion = settings.reduceMotion,
            atEnd = settings.speedFabAtEnd,
            yFraction = settings.speedFabY,
            onMove = { end, y -> onUpdateSettings { it.copy(speedFabAtEnd = end, speedFabY = y) } },
            onOpenMap = {
                deepLinkOrigin = current
                pendingSettingsPage = dev.cluvex.zedsecure.ui.settings.SettingsPage.Map
                current = TopDestination.Settings
            },
            onOpenSpeedTest = {
                deepLinkOrigin = current
                pendingSettingsPage = dev.cluvex.zedsecure.ui.settings.SettingsPage.SpeedTest
                current = TopDestination.Settings
            },
            modifier = Modifier.padding(innerPadding),
        )
      }
    }

    if (bento) {
        dev.cluvex.zedsecure.ui.bento.BentoDock(
            current = current,
            onSelect = { current = it },
            visible = dockShown || !autoHide,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    val tourSteps = dev.cluvex.zedsecure.ui.onboarding.rememberTourSteps()
    if (tourStep in tourSteps.indices) {
        val step = tourSteps[tourStep]

        val finish = {
            tourStep = -1
            current = TopDestination.Home
        }
        val advance = { if (tourStep >= tourSteps.lastIndex) finish() else tourStep += 1 }
        androidx.compose.runtime.LaunchedEffect(tourStep) {
            current = when (step.destination) {
                dev.cluvex.zedsecure.ui.onboarding.TourDestination.Home -> TopDestination.Home
                dev.cluvex.zedsecure.ui.onboarding.TourDestination.Servers -> TopDestination.Servers
                dev.cluvex.zedsecure.ui.onboarding.TourDestination.Settings -> TopDestination.Settings
            }
        }
        dev.cluvex.zedsecure.ui.onboarding.TourOverlay(
            step = step,
            index = tourStep,
            total = tourSteps.size,
            nextLabel = if (tourStep == tourSteps.lastIndex) {
                stringResource(Res.string.tour_done)
            } else {
                stringResource(Res.string.tour_next)
            },
            skipLabel = stringResource(Res.string.tour_skip),
            onNext = advance,
            onSkip = finish,

            onTargetMissing = advance,
        )
    }
    }
}

private fun languageEnglishName(language: AppLanguage): String = when (language) {
    AppLanguage.Persian -> "Persian"
    AppLanguage.Chinese -> "Chinese"
    AppLanguage.Russian -> "Russian"
    AppLanguage.Spanish -> "Spanish"
    AppLanguage.English -> "English"

    AppLanguage.System -> "the language the user writes in"
}

private fun languageNativeName(language: AppLanguage): String = when (language) {
    AppLanguage.Persian -> "فارسی"
    AppLanguage.Chinese -> "中文"
    AppLanguage.Russian -> "Русский"
    AppLanguage.Spanish -> "Español"
    AppLanguage.English -> "English"
    AppLanguage.System -> ""
}
