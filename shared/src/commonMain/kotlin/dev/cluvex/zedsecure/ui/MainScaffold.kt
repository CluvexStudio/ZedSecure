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
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.cluvex.zedsecure.data.config.ConfigRepository
import dev.cluvex.zedsecure.shared.resources.Res
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
    val connectionVm: ConnectionViewModel = viewModel { ConnectionViewModel() }
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
        val link = (activeProfile?.source as? dev.cluvex.zedsecure.domain.config.ProfileSource.Link)?.link
        val parsed = link?.let {
            runCatching { dev.cluvex.zedsecure.domain.config.ConfigParser.parse(it) }.getOrNull()
        }
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
            dev.cluvex.zedsecure.ui.navigation.ZedNavBar(
                style = settings.navBarStyle,
                current = current,
                onSelect = { current = it },
            )
        },
    ) { innerPadding ->
      androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
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
                TopDestination.Home -> HomeScreen(
                    connectionVm = connectionVm,
                    activeConfigName = activeProfile?.let { p ->
                        if (p.isAutoSelect) {
                            stringResource(
                                Res.string.auto_title_for,
                                dev.cluvex.zedsecure.ui.servers.autoSelectGroupLabel(p),
                            )
                        } else p.name
                    },
                    exitGeneration = autoSession?.takeIf { it.profileId == activeProfile?.id }?.exitGeneration ?: 0,
                    activeConfigDetail = activeProfile?.takeIf { it.isAutoSelect }?.let { p ->
                        autoSession?.takeIf { it.profileId == p.id }?.let { session ->
                            dev.cluvex.zedsecure.ui.servers.autoSelectLiveLine(session) { id ->
                                profiles.firstOrNull { it.id == id }?.name
                            }
                        }
                    },
                    lockedNote = lockedNote,
                    activeLocked = activeProfile?.isLocked == true,
                    reduceMotion = settings.reduceMotion,

                    showConnectionInfo = activeProfile?.isDnsTunnel != true,
                    ipApiUrl = settings.ipApiUrl,
                    delayTestUrl = settings.delayTestUrl,

                    personalization = settings.toPersonalization(),
                    connectStyle = settings.connectButtonStyle,
                    contentPadding = innerPadding,
                    onToggleConnection = onToggleConnection,

                    onBrowseConfigs = {
                        current = if (activeProfile?.isLocked == true) TopDestination.Vault
                        else TopDestination.Servers
                    },
                    onSecretUnlocked = { spaceUnlocked = true },
                )
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
                    onServerActivated = {
                        if (settings.homeAfterSelect) current = TopDestination.Home

                        onActiveServerChanged()
                    },
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
                        .filter { it.source is dev.cluvex.zedsecure.domain.config.ProfileSource.Link }
                        .map { it.id to it.name },
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
        HomeFabCluster(
            visible = current == TopDestination.Home,
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
    AppLanguage.English -> "English"

    AppLanguage.System -> "the language the user writes in"
}

private fun languageNativeName(language: AppLanguage): String = when (language) {
    AppLanguage.Persian -> "فارسی"
    AppLanguage.Chinese -> "中文"
    AppLanguage.Russian -> "Русский"
    AppLanguage.English -> "English"
    AppLanguage.System -> ""
}
