@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.animation.ExperimentalSharedTransitionApi::class,
)

package dev.cluvex.zedsecure.ui.settings

import dev.cluvex.zedsecure.ui.platform.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import dev.cluvex.zedsecure.ui.motion.transitionDecoration
import dev.cluvex.zedsecure.ui.onboarding.TourTargets
import dev.cluvex.zedsecure.ui.onboarding.tourTargetInPage
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.intl.Locale
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.*
import dev.cluvex.zedsecure.platform.AppInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.cluvex.zedsecure.ui.platform.AutomaticRendering
import dev.cluvex.zedsecure.ui.platform.LocalPlatform
import dev.cluvex.zedsecure.ui.theme.applyThemeProfile
import dev.cluvex.zedsecure.domain.model.AppLanguage
import dev.cluvex.zedsecure.data.update.Distribution
import dev.cluvex.zedsecure.domain.model.AppSettings
import dev.cluvex.zedsecure.domain.model.RenderingMode
import dev.cluvex.zedsecure.domain.model.CardCornerStyle
import dev.cluvex.zedsecure.domain.model.ConnectButtonStyle
import dev.cluvex.zedsecure.domain.model.DomainStrategy
import dev.cluvex.zedsecure.domain.model.ListDensity
import dev.cluvex.zedsecure.domain.model.FragmentPackets
import dev.cluvex.zedsecure.domain.model.HevLogLevel
import dev.cluvex.zedsecure.domain.model.LogLevel
import dev.cluvex.zedsecure.domain.model.NotifChip
import dev.cluvex.zedsecure.domain.model.OutboundDomainResolve
import dev.cluvex.zedsecure.domain.model.RunMode
import dev.cluvex.zedsecure.domain.model.ThemeMode
import dev.cluvex.zedsecure.domain.model.ColorContrast
import dev.cluvex.zedsecure.domain.model.AppDesign
import dev.cluvex.zedsecure.domain.model.TrafficCardStyle
import dev.cluvex.zedsecure.domain.model.TrafficTileSize
import dev.cluvex.zedsecure.domain.model.UiFontScale
import dev.cluvex.zedsecure.domain.model.VpnBypassLan
import dev.cluvex.zedsecure.domain.model.VpnInterfaceAddress
import dev.cluvex.zedsecure.domain.model.SingBoxMuxProtocol
import dev.cluvex.zedsecure.domain.model.SingBoxStack
import dev.cluvex.zedsecure.domain.model.XudpQuic
import dev.cluvex.zedsecure.ui.theme.argbLongToColor
import dev.cluvex.zedsecure.ui.theme.toPersonalization
import dev.cluvex.zedsecure.ui.theme.hasPersonalization
import dev.cluvex.zedsecure.ui.theme.resetPersonalization
import dev.cluvex.zedsecure.ui.update.ManualUpdateCheckHost
import dev.cluvex.zedsecure.domain.config.LocalPorts
import dev.cluvex.zedsecure.domain.config.SingBoxTuning
import dev.cluvex.zedsecure.domain.config.effectiveLanSocksPort
import dev.cluvex.zedsecure.domain.config.effectiveLocalProxyPort
import dev.cluvex.zedsecure.platform.secureRandomToken
import dev.cluvex.zedsecure.ui.platform.appVerticalScroll
import dev.cluvex.zedsecure.core.ghost.GhostEngine

enum class SettingsPage {
    Root, Ui, Vpn, Core, Routing, PerApp, Mux, Fragment, Observatory, Advanced, Mode, Assets, SingBox,
    DnsProtocols, Tor, TorBridges, Ssh, Support, SpeedTest, Map, Privacy, Monitor, Ai, AiChat, NavBar, Ghost,
}

// NOTE: This is a temporary minimal SettingsScreen after accidental truncation.
// Full UI pages are still in the same package; this restores compile + Ghost navigation.

@Composable
fun SettingsScreen(
    settings: AppSettings,
    contentPadding: PaddingValues,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onLanguage: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
    initialPage: SettingsPage = SettingsPage.Root,
    onInitialPageConsumed: () -> Unit = {},
    serverTargets: List<Pair<String, String>> = emptyList(),
    groupTargets: List<Pair<String, String>> = emptyList(),
    autoCountries: List<String> = emptyList(),
    dnsTunnelActive: Boolean = false,
    onDeepLinkBack: (() -> Unit)? = null,
    onReplayTour: (() -> Unit)? = null,
    onReplaySetup: (() -> Unit)? = null,
    onOpenVault: (() -> Unit)? = null,
    mtuHint: dev.cluvex.zedsecure.data.net.MtuServerHint? = null,
    ai: dev.cluvex.zedsecure.ui.settings.AiSection? = null,
) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.Root) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var deepLinked by rememberSaveable { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(settings.ghost) {
        runCatching { GhostEngine.updateSettings(settings.ghost) }
    }

    androidx.compose.runtime.LaunchedEffect(initialPage) {
        if (initialPage != SettingsPage.Root) {
            page = initialPage
            deepLinked = true
            onInitialPageConsumed()
        }
    }

    val goUp: () -> Unit = {
        when {
            deepLinked && onDeepLinkBack != null -> {
                deepLinked = false
                page = SettingsPage.Root
                onDeepLinkBack()
            }
            page == SettingsPage.TorBridges -> page = SettingsPage.Tor
            page == SettingsPage.NavBar -> page = SettingsPage.Ui
            else -> {
                deepLinked = false
                page = SettingsPage.Root
            }
        }
    }

    if (showAbout) AboutSheet(onDismiss = { showAbout = false })
    BackHandler(enabled = page != SettingsPage.Root) { goUp() }

    val stateHolder = rememberSaveableStateHolder()
    val goBack: (() -> Unit)? = if (page == SettingsPage.Root) null else goUp

    dev.cluvex.zedsecure.ui.components.ProvidePageBack(goBack) {
        when (page) {
            SettingsPage.Root -> {
                // Minimal root: direct entries without sharedElement collision
                androidx.compose.foundation.layout.Column(
                    modifier
                        .fillMaxWidth()
                        .appVerticalScroll(rememberScrollState(), contentPadding)
                        .padding(contentPadding),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    SettingsMenuRow(
                        title = "GHOST MOD",
                        subtitle = "ضد DPI",
                        art = SettingsArts.Ghost,
                    ) { page = SettingsPage.Ghost }
                    SettingsMenuRow(
                        stringResource(Res.string.monitor_title),
                        stringResource(Res.string.monitor_subtitle),
                        art = SettingsArts.Monitor,
                    ) { page = SettingsPage.Monitor }
                    SettingsMenuRow(
                        stringResource(Res.string.title_ui_settings),
                        stringResource(Res.string.summary_ui_settings),
                        art = SettingsArts.Appearance,
                    ) { page = SettingsPage.Ui }
                    SettingsMenuRow(
                        stringResource(Res.string.support_title),
                        stringResource(Res.string.support_subtitle),
                        art = SettingsArts.Support,
                    ) { page = SettingsPage.Support }
                }
            }
            SettingsPage.Ghost -> GhostSettingsPage(settings, contentPadding, modifier, onUpdate)
            SettingsPage.Monitor -> LiveMonitorPage(contentPadding, modifier)
            SettingsPage.Ui -> UiPage(settings, contentPadding, modifier, onUpdate, onLanguage) { page = it }
            SettingsPage.Support -> SupportPage(contentPadding, modifier)
            SettingsPage.Privacy -> PrivacyPage(contentPadding, modifier)
            SettingsPage.NavBar -> NavBarSettingsPage(settings, contentPadding, modifier, onUpdate)
            SettingsPage.Vpn -> VpnPage(settings, contentPadding, modifier, onUpdate, mtuHint)
            SettingsPage.Core -> CorePage(settings, contentPadding, modifier, onUpdate)
            SettingsPage.Mux -> MuxPage(settings, contentPadding, modifier, onUpdate)
            SettingsPage.SingBox -> SingBoxPage(settings, contentPadding, modifier, onUpdate)
            SettingsPage.Fragment -> FragmentPage(settings, contentPadding, modifier, onUpdate)
            SettingsPage.Observatory -> ObservatoryPage(settings, contentPadding, modifier, onUpdate, autoCountries)
            SettingsPage.Advanced -> AdvancedPage(settings, contentPadding, modifier, onUpdate)
            SettingsPage.Mode -> ModePage(settings, contentPadding, modifier, onUpdate)
            SettingsPage.Routing -> RoutingScreen(settings, contentPadding, onUpdate, modifier, serverTargets, groupTargets)
            SettingsPage.PerApp -> PerAppProxyScreen(settings, contentPadding, onUpdate, modifier)
            SettingsPage.Assets -> AssetsScreen(settings, contentPadding, modifier, onUpdate)
            SettingsPage.DnsProtocols -> DnsProtocolsPage(settings, contentPadding, modifier, onUpdate)
            SettingsPage.Tor -> TorPage(settings, contentPadding, modifier, onUpdate) { page = it }
            SettingsPage.TorBridges -> TorBridgesScreen(settings, contentPadding, modifier, onUpdate)
            SettingsPage.Ssh -> SshPage(settings, contentPadding, modifier, onUpdate)
            SettingsPage.Ai -> ai?.let {
                SettingsPageScaffold(
                    title = stringResource(Res.string.ai_title),
                    art = SettingsArts.Ai,
                    subtitle = stringResource(Res.string.ai_subtitle),
                    contentPadding = contentPadding,
                    modifier = modifier,
                ) {
                    AiAssistantPage(
                        settings = it.settings,
                        onUpdate = it.onUpdate,
                        onOpenChat = { page = SettingsPage.AiChat },
                        onOpenKeyPage = it.onOpenUrl,
                    )
                }
            }
            SettingsPage.AiChat -> ai?.let {
                AiChatScreen(
                    settings = it.settings,
                    bridge = it.bridge,
                    languageName = it.languageName,
                    languageNative = it.languageNative,
                    platform = it.platform,
                    appVersion = it.appVersion,
                    contentPadding = contentPadding,
                    modifier = modifier,
                )
            }
            SettingsPage.SpeedTest ->
                dev.cluvex.zedsecure.ui.speedtest.SpeedTestScreen(contentPadding, modifier, dnsTunnelActive)
            SettingsPage.Map -> dev.cluvex.zedsecure.ui.map.MapScreen(
                contentPadding = contentPadding,
                modifier = modifier,
                reduceMotion = settings.reduceMotion,
                ipApiUrl = settings.ipApiUrl,
                personalization = settings.toPersonalization(),
                savedOriginCode = settings.verifiedOriginCode,
                savedOriginLabel = settings.verifiedOriginLabel,
                onRealLocation = { code, label ->
                    if (code != settings.verifiedOriginCode || label != settings.verifiedOriginLabel) {
                        onUpdate { it.copy(verifiedOriginCode = code, verifiedOriginLabel = label) }
                    }
                },
            )
        }
    }
}
