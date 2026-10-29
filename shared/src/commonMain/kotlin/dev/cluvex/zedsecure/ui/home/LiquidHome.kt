@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package dev.cluvex.zedsecure.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalUriHandler
import dev.cluvex.zedsecure.data.net.PingService
import dev.cluvex.zedsecure.platform.httpGetResponse
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.cluvex.zedsecure.core.ConnectionFault
import dev.cluvex.zedsecure.data.net.NetworkInfo
import dev.cluvex.zedsecure.domain.model.ConnectionState
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.*
import dev.cluvex.zedsecure.ui.components.FlagBadge
import dev.cluvex.zedsecure.ui.components.NoteText
import dev.cluvex.zedsecure.ui.connection.ConnectionUiState
import dev.cluvex.zedsecure.ui.format.formatBytes
import dev.cluvex.zedsecure.ui.format.formatElapsed
import dev.cluvex.zedsecure.ui.format.formatRate
import dev.cluvex.zedsecure.ui.format.ltrIsolate
import dev.cluvex.zedsecure.ui.onboarding.TourTargets
import dev.cluvex.zedsecure.ui.onboarding.tourTarget
import dev.cluvex.zedsecure.ui.theme.LiquidBackdrop
import dev.cluvex.zedsecure.ui.theme.LocalMotionBudget
import dev.cluvex.zedsecure.ui.theme.MotionBudget
import dev.cluvex.zedsecure.ui.theme.liquidBorder
import dev.cluvex.zedsecure.ui.theme.liquidFill
import dev.cluvex.zedsecure.ui.theme.liquidTint
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun LiquidHome(
    ui: ConnectionUiState,
    info: NetworkInfo,
    infoExpanded: Boolean,
    onOpenInfo: (InfoFocus) -> Unit,
    onCloseInfo: () -> Unit,
    onRefreshPing: () -> Unit,
    onRefreshPlace: () -> Unit,
    onRefreshInfo: () -> Unit,
    activeConfigName: String?,
    activeConfigDetail: String?,
    activeLocked: Boolean,
    lockedNote: String?,
    localProxyAddress: String?,
    showConnectionInfo: Boolean,
    reduceMotion: Boolean,
    contentPadding: PaddingValues,
    onToggleConnection: () -> Unit,
    onBrowseConfigs: () -> Unit,
    onOpenConfigFinder: () -> Unit,
    onSecretUnlocked: () -> Unit,
    onShowLogs: () -> Unit,
    onBurst: () -> Unit,
    onStatusTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val live = ui.state == ConnectionState.Connected
    val searching = ui.state == ConnectionState.Connecting || ui.state == ConnectionState.Reconnecting
    val budget = LocalMotionBudget.current
    val animate = !reduceMotion && when (budget) {
        MotionBudget.Full -> true
        MotionBudget.Throttled -> searching
        MotionBudget.Paused -> false
    }
    val clock = rememberPortalClock(animate && (live || searching))
    val haptics = LocalHapticFeedback.current
    var quickHubOpen by remember { mutableStateOf(false) }
    var quickHubPage by remember { mutableStateOf<LiquidQuickHubPage?>(null) }

    var celebrate by remember { mutableIntStateOf(0) }
    var previous by remember { mutableStateOf(ui.state) }
    LaunchedEffect(ui.state) {
        if (ui.state == ConnectionState.Connected && (previous == ConnectionState.Connecting || previous == ConnectionState.Reconnecting)) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            if (!reduceMotion) celebrate++
        }
        previous = ui.state
    }

    val auroraClock = rememberPortalClock(!reduceMotion && budget == MotionBudget.Full)
    Box(modifier.fillMaxSize().background(cs.surface)) {
        LiquidBackdrop(auroraClock)
        BoxWithConstraints(Modifier.fillMaxSize().padding(contentPadding)) {
            val compact = maxHeight < 640.dp
            Column(
                Modifier.fillMaxSize().widthIn(max = 560.dp).align(Alignment.TopCenter),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    val scroll = rememberScrollState()
                    StageColumn(
                        viewport = maxHeight,
                        stageMin = if (compact) 300.dp else 340.dp,
                        modifier = Modifier.fillMaxWidth().scrollFade(scroll).verticalScroll(scroll),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            PortalTopBar(ui.state, animate, onSecretUnlocked, onShowLogs)
                            val health = if (live) info.fault else null
                            AnimatedVisibility(visible = health != null) {
                                health?.let {
                                    Box(Modifier.padding(horizontal = 16.dp)) {
                                        FaultBanner(fault = it, connected = true, onShowLogs = onShowLogs)
                                    }
                                }
                            }
                        }

                        Box(Modifier.fillMaxWidth().layoutId(STAGE_ID).padding(horizontal = 16.dp, vertical = 8.dp)) {
                            if (ui.state == ConnectionState.Error) {
                                LiquidErrorCard(
                                    error = ui.error,
                                    onRetry = onToggleConnection,
                                    onOtherServer = onBrowseConfigs,
                                    onShowLogs = onShowLogs,
                                )
                            } else {
                                LiquidHeroCard(
                                    ui = ui,
                                    info = info,
                                    showConnectionInfo = showConnectionInfo,
                                    animate = animate,
                                    clock = clock,
                                    celebrate = celebrate,
                                    compact = compact,
                                    onTap = {
                                        if (ui.state != ConnectionState.Disconnecting) {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onToggleConnection()
                                        }
                                    },
                                    onLongPress = onBurst,
                                    onOpenQuickHub = { quickHubOpen = true },
                                    onStatusTap = onStatusTap,
                                    details = infoExpanded,
                                    onOpenInfo = onOpenInfo,
                                    onCloseInfo = onCloseInfo,
                                    onRefreshPing = onRefreshPing,
                                    onRefreshPlace = onRefreshPlace,
                                    onRefreshAll = onRefreshInfo,
                                )
                            }
                        }

                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (localProxyAddress != null && live) {
                                val platform = dev.cluvex.zedsecure.ui.platform.LocalPlatform.current
                                val copied = stringResource(Res.string.copied)
                                Spacer(Modifier.height(8.dp))
                                Surface(
                                    onClick = {
                                        platform.copyToClipboard(localProxyAddress)
                                        platform.toast(copied)
                                    },
                                    shape = CircleShape,
                                    color = liquidFill(),
                                    border = liquidBorder(),
                                ) {
                                    Text(
                                        text = stringResource(Res.string.home_local_proxy, localProxyAddress),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = cs.onSurfaceVariant,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    )
                                }
                            }
                            if (lockedNote != null) {
                                val noteScroll = rememberScrollState()
                                Spacer(Modifier.height(8.dp))
                                Surface(
                                    shape = MaterialTheme.shapes.large,
                                    color = liquidFill(),
                                    border = liquidBorder(),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    NoteText(
                                        text = lockedNote,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = cs.onSurfaceVariant,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 140.dp)
                                            .verticalScroll(noteScroll)
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    LiquidServerRow(
                        name = activeConfigName,
                        detail = activeConfigDetail,
                        locked = activeLocked,
                        onClick = onBrowseConfigs,
                        modifier = Modifier.tourTarget(TourTargets.CONFIG_CARD),
                    )
                    LiquidStats(
                        live = live,
                        downloadBps = ui.downloadBps,
                        uploadBps = ui.uploadBps,
                        totalDownload = ui.totalDownload,
                        totalUpload = ui.totalUpload,
                        modifier = Modifier.tourTarget(TourTargets.TRAFFIC),
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        if (quickHubOpen) {
            LiquidQuickHubOverlay(
                page = quickHubPage,
                networkInfo = info,
                localProxyAddress = localProxyAddress,
                activeConfigName = activeConfigName,
                proxyActive = live,
                onRefreshNetworkInfo = onRefreshInfo,
                onOpenConfigFinder = {
                    quickHubOpen = false
                    quickHubPage = null
                    onOpenConfigFinder()
                },
                onOpenPage = { page ->
                    if (page == LiquidQuickHubPage.Config) {
                        quickHubOpen = false
                        quickHubPage = null
                        onOpenConfigFinder()
                    } else {
                        quickHubPage = page
                    }
                },
                onBack = { quickHubPage = null },
                onClose = {
                    quickHubOpen = false
                    quickHubPage = null
                },
            )
        }
    }
}

private enum class LiquidQuickHubPage(
    val title: String,
    val icon: String,
    val description: String,
) {
    Proxy("پروکسی‌یاب", "🛰️", "پیدا کردن و بررسی مسیرهای پراکسی"),
    Config("کانفیگ‌یاب", "✦", "کشف و بررسی کانفیگ‌های قابل استفاده"),
    Ip("IP‌یاب", "🌐", "نمایش و بررسی اطلاعات اتصال و IP"),
}

@Composable
private fun LiquidQuickHubOverlay(
    page: LiquidQuickHubPage?,
    networkInfo: NetworkInfo,
    localProxyAddress: String?,
    activeConfigName: String?,
    proxyActive: Boolean,
    onRefreshNetworkInfo: () -> Unit,
    onOpenConfigFinder: () -> Unit,
    onOpenPage: (LiquidQuickHubPage) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.66f)),
        contentAlignment = Alignment.Center,
    ) {
        // A softly animated liquid ring stays behind the three exclusive shortcuts.
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * 0.29f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(cs.primary.copy(alpha = 0.24f), cs.tertiary.copy(alpha = 0.12f), Color.Transparent),
                    center = c,
                    radius = radius * 1.6f,
                ),
                radius = radius * 1.6f,
                center = c,
            )
            drawCircle(
                color = cs.primary.copy(alpha = 0.32f),
                radius = radius,
                center = c,
                style = Stroke(width = 1.5.dp.toPx()),
            )
            drawCircle(
                color = cs.tertiary.copy(alpha = 0.25f),
                radius = radius * 0.76f,
                center = c,
                style = Stroke(width = 1.dp.toPx()),
            )
        }

        if (page == null) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val bubbleSize = minOf(maxWidth * 0.25f, 104.dp).coerceAtLeast(76.dp)
                // Orbit positions create a three-point radial launcher around the liquid core.
                LiquidQuickHubBubble(
                    icon = LiquidQuickHubPage.Proxy.icon,
                    title = LiquidQuickHubPage.Proxy.title,
                    tint = cs.primary,
                    modifier = Modifier.align(Alignment.Center).offset(y = -maxHeight * 0.235f).size(bubbleSize),
                    onClick = { onOpenPage(LiquidQuickHubPage.Proxy) },
                )
                LiquidQuickHubBubble(
                    icon = LiquidQuickHubPage.Config.icon,
                    title = LiquidQuickHubPage.Config.title,
                    tint = cs.tertiary,
                    modifier = Modifier.align(Alignment.Center).offset(x = -maxWidth * 0.245f, y = maxHeight * 0.105f).size(bubbleSize),
                    onClick = { onOpenPage(LiquidQuickHubPage.Config) },
                )
                LiquidQuickHubBubble(
                    icon = LiquidQuickHubPage.Ip.icon,
                    title = LiquidQuickHubPage.Ip.title,
                    tint = cs.secondary,
                    modifier = Modifier.align(Alignment.Center).offset(x = maxWidth * 0.245f, y = maxHeight * 0.105f).size(bubbleSize),
                    onClick = { onOpenPage(LiquidQuickHubPage.Ip) },
                )
                Surface(
                    shape = CircleShape,
                    color = liquidFill(),
                    border = liquidBorder(),
                    modifier = Modifier.align(Alignment.Center).size(94.dp),
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("LIQUID", style = MaterialTheme.typography.labelMedium, color = cs.primary, fontWeight = FontWeight.Black)
                        Text("HUB", style = MaterialTheme.typography.headlineSmallEmphasized, color = cs.onSurface)
                    }
                }
                Text(
                    "۵ ثانیه نگه داشتی؛ میان‌برهای مخفی باز شدند",
                    modifier = Modifier.align(Alignment.Center).offset(y = maxHeight * 0.285f).padding(horizontal = 28.dp),
                    color = Color.White.copy(alpha = 0.86f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Surface(
                    onClick = onClose,
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.12f),
                    modifier = Modifier.align(Alignment.TopEnd).padding(20.dp).size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("×", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                    }
                }
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp).heightIn(min = 360.dp, max = 520.dp),
                shape = RoundedCornerShape(36.dp),
                color = liquidFill(),
                border = liquidBorder(),
            ) {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 472.dp).verticalScroll(rememberScrollState()).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            onClick = onBack,
                            shape = CircleShape,
                            color = cs.surface.copy(alpha = 0.72f),
                            modifier = Modifier.size(44.dp),
                        ) { Box(contentAlignment = Alignment.Center) { Text("‹", color = cs.onSurface, style = MaterialTheme.typography.headlineLarge) } }
                        Spacer(Modifier.weight(1f))
                        Text("LIQUID EXCLUSIVE", color = cs.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Surface(
                            onClick = onClose,
                            shape = CircleShape,
                            color = cs.surface.copy(alpha = 0.72f),
                            modifier = Modifier.size(44.dp),
                        ) { Box(contentAlignment = Alignment.Center) { Text("×", color = cs.onSurface, style = MaterialTheme.typography.headlineMedium) } }
                    }
                    Spacer(Modifier.height(32.dp))
                    Box(
                        Modifier.size(112.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(cs.primary, cs.tertiary, cs.secondary))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(page.icon, style = MaterialTheme.typography.displaySmall, color = Color.White)
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(page.title, style = MaterialTheme.typography.headlineMediumEmphasized, color = cs.onSurface)
                    Spacer(Modifier.height(10.dp))
                    Text(page.description, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    when (page) {
                        LiquidQuickHubPage.Ip -> {
                            Surface(shape = RoundedCornerShape(20.dp), color = cs.surface.copy(alpha = 0.65f), border = liquidBorder(), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("IP فعلی", style = MaterialTheme.typography.titleMedium, color = cs.primary, fontWeight = FontWeight.Bold)
                                    LiquidHubInfoRow("IPv4", networkInfo.ipv4 ?: "در دسترس نیست")
                                    LiquidHubInfoRow("IPv6", networkInfo.ipv6 ?: "در دسترس نیست")
                                    LiquidHubInfoRow("کشور", networkInfo.country ?: "نامشخص")
                                    LiquidHubInfoRow("شهر / ارائه‌دهنده", listOfNotNull(networkInfo.city, networkInfo.isp).joinToString(" · ").ifBlank { "نامشخص" })
                                    LiquidHubInfoRow("مسیر", if (networkInfo.underVpn || networkInfo.viaTunnel) "عبور از تونل گزارش شده" else "عبور از VPN تأیید نشده")
                                    networkInfo.error?.let { Text("وضعیت بررسی: $it", style = MaterialTheme.typography.bodySmall, color = cs.error) }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            FilledTonalButton(onClick = onRefreshNetworkInfo, enabled = !networkInfo.loading && !networkInfo.placeLoading, shapes = ButtonDefaults.shapes()) {
                                Text(if (networkInfo.loading || networkInfo.placeLoading) "در حال بررسی…" else "بررسی دوبارهٔ IP")
                            }
                        }
                        LiquidQuickHubPage.Proxy -> {
                            Surface(shape = RoundedCornerShape(20.dp), color = cs.surface.copy(alpha = 0.65f), border = liquidBorder(), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("وضعیت اتصال فعلی", style = MaterialTheme.typography.titleMedium, color = cs.primary, fontWeight = FontWeight.Bold)
                                    LiquidHubInfoRow("پروفایل انتخاب‌شده", activeConfigName ?: "پروفایلی انتخاب نشده")
                                    LiquidHubInfoRow("پروکسی محلی", localProxyAddress ?: "آدرس پروکسی محلی در دسترس نیست")
                                    LiquidHubInfoRow("VPN دستگاه", if (proxyActive) "اتصال VPN فعال گزارش شده" else "اتصال فعال تأیید نشده")
                                    Text("پروکسی تلگرام فقط مسیر خود تلگرام را تنظیم می‌کند؛ تست TCP به‌تنهایی اتصال واقعی MTProto یا امنیت اپراتور را تأیید نمی‌کند.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            TelegramProxyFinder()
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = onOpenConfigFinder, shapes = ButtonDefaults.shapes()) { Text("رفتن به کانفیگ‌یاب VPN") }
                        }
                        LiquidQuickHubPage.Config -> Unit
                    }
                    Spacer(Modifier.height(16.dp))
                    FilledTonalButton(onClick = onBack, shapes = ButtonDefaults.shapes()) {
                        Text("بازگشت به حلقهٔ میان‌برها")
                    }
                }
            }
        }
    }
}

private data class TelegramProxyCandidate(
    val link: String,
    val host: String,
    val port: Int,
    val secret: String,
    val source: String,
    val tcpMs: Long? = null,
    val tcpTested: Boolean = false,
)

private data class TelegramProxySource(val name: String, val url: String)

private val telegramProxySources = listOf(
    TelegramProxySource("mtproto-proxy-pro · فهرست به‌روزشونده", "https://zakky8.github.io/mtproto-proxy-pro/all_proxies.txt"),
    TelegramProxySource("mtproto-proxy-pro · فهرست مقاوم‌تر", "https://zakky8.github.io/mtproto-proxy-pro/censorship_resistant.txt"),
    TelegramProxySource("shablin/mtproto-proxy", "https://raw.githubusercontent.com/shablin/mtproto-proxy/main/data/valid_proxy.txt"),
    TelegramProxySource("کانال عمومی hddify", "https://t.me/s/hddify"),
    TelegramProxySource("کانال عمومی mitivpn", "https://t.me/s/mitivpn"),
)

private fun isPublicTelegramProxyHost(rawHost: String): Boolean {
    val host = rawHost.trim().trim('[', ']').lowercase()
    if (host.isBlank() || host == "localhost" || host.endsWith(".localhost") ||
        host.endsWith(".local") || host.endsWith(".internal") || host.endsWith(".lan")) return false
    if (!Regex("^[a-z0-9.-]{1,253}$").matches(host) || host.startsWith(".") || host.endsWith(".")) return false
    val parts = host.split(".")
    val looksLikeIpv4 = parts.size == 4 && parts.all { part -> part.isNotEmpty() && part.all { it.isDigit() } }
    if (looksLikeIpv4 && parts.any { it.toIntOrNull()?.let { octet -> octet in 0..255 } != true }) return false
    if (looksLikeIpv4) {
        val octets = parts.map { it.toInt() }
        val a = octets[0]
        val b = octets[1]
        if (a == 0 || a == 10 || a == 127 || a >= 224 ||
            (a == 169 && b == 254) || (a == 172 && b in 16..31) ||
            (a == 192 && b == 168) || (a == 100 && b in 64..127)) return false
    }
    return true
}

private fun decodeTelegramProxyValue(raw: String): String = buildString {
    var i = 0
    while (i < raw.length) {
        if (raw[i] == '%' && i + 2 < raw.length) {
            val value = raw.substring(i + 1, i + 3).toIntOrNull(16)
            if (value != null) {
                append(value.toChar())
                i += 3
                continue
            }
        }
        append(raw[i])
        i++
    }
}

private fun parseTelegramProxyCandidates(body: String, sourceName: String): List<TelegramProxyCandidate> {
    val normalized = body.replace("&amp;", "&", ignoreCase = true).replace("&#38;", "&", ignoreCase = true)
    val linkRegex = Regex("""(?i)(?:tg://proxy\?[^\s<>"']+|https?://t\.me/proxy\?[^\s<>"']+)""")
    val fromLinks = linkRegex.findAll(normalized).mapNotNull { match ->
        val link = match.value.trimEnd(',', ';', '.', ')', ']', '}', '"', '\'')
        val params = link.substringAfter('?', "").substringBefore('#').split('&').mapNotNull { part ->
            val key = part.substringBefore('=', "").lowercase()
            val value = decodeTelegramProxyValue(part.substringAfter('=', ""))
            if (key.isBlank() || value.isBlank()) null else key to value
        }.toMap()
        val host = params["server"]?.trim()?.trimEnd('.') ?: return@mapNotNull null
        val port = params["port"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return@mapNotNull null
        val secret = params["secret"]?.trim() ?: return@mapNotNull null
        if (!isPublicTelegramProxyHost(host) || secret.length !in 16..512 ||
            secret.any { it.isWhitespace() || it == '&' || it == '#' || it == '?' }) return@mapNotNull null
        TelegramProxyCandidate("https://t.me/proxy?server=$host&port=$port&secret=$secret", host, port, secret, sourceName)
    }.toList()
    val fromLines = normalized.lineSequence().mapNotNull { line ->
        val match = Regex("""^\s*([A-Za-z0-9.-]{1,253}):(\d{1,5}):([A-Za-z0-9_+./=-]{16,512})\s*$""")
            .matchEntire(line.trim()) ?: return@mapNotNull null
        val host = match.groupValues[1]
        val port = match.groupValues[2].toIntOrNull()?.takeIf { it in 1..65535 } ?: return@mapNotNull null
        val secret = match.groupValues[3]
        if (!isPublicTelegramProxyHost(host) || secret.length !in 16..512) return@mapNotNull null
        TelegramProxyCandidate("https://t.me/proxy?server=$host&port=$port&secret=$secret", host, port, secret, sourceName)
    }.toList()
    return fromLinks + fromLines
}

@Composable
private fun TelegramProxyFinder() {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var candidates by remember { mutableStateOf<List<TelegramProxyCandidate>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var manualInput by remember { mutableStateOf("") }
    var onlyReachable by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testProgress by remember { mutableIntStateOf(0) }
    var testTotal by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("برای دریافت فهرست تازه، «جست‌وجوی پروکسی‌ها» را بزن.") }
    var sourcesOk by remember { mutableIntStateOf(0) }
    var sourcesFailed by remember { mutableIntStateOf(0) }
    val visible = candidates.filter {
        (it.host.contains(query, true) || it.source.contains(query, true)) &&
            (!onlyReachable || it.tcpMs != null)
    }

    Surface(shape = RoundedCornerShape(24.dp), color = cs.surface.copy(alpha = 0.72f), border = liquidBorder(), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Telegram Proxy Finder", style = MaterialTheme.typography.titleLarge, color = cs.primary, fontWeight = FontWeight.Bold)
            Text("کشف MTProto از چند منبع عمومی، حذف تکراری‌ها، مرتب‌سازی بر اساس تأخیر TCP و بازکردن مستقیم در تلگرام.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    enabled = !loading && !testing,
                    onClick = {
                        scope.launch {
                            loading = true
                            status = "در حال دریافت فهرست‌ها…"
                            sourcesOk = 0
                            sourcesFailed = 0
                            try {
                                val fetched = coroutineScope {
                                    telegramProxySources.map { source -> async {
                                        runCatching {
                                            val response = withContext(Dispatchers.IO) {
                                                httpGetResponse(source.url, "ZX-VPN-TelegramProxyFinder/1.1", connectTimeoutMs = 7000, readTimeoutMs = 9000)
                                            }
                                            if (response.code !in 200..299) error("HTTP ${response.code}")
                                            source to response.body.take(1024 * 1024)
                                        }.getOrNull()
                                    } }.awaitAll()
                                }
                                sourcesOk = fetched.count { it != null }
                                sourcesFailed = fetched.size - sourcesOk
                                val parsed = fetched.filterNotNull().flatMap { (source, body) ->
                                    parseTelegramProxyCandidates(body, source.name)
                                }.distinctBy { "${it.host.lowercase()}:${it.port}:${it.secret.lowercase()}" }
                                candidates = parsed
                                status = if (parsed.isEmpty()) "منبع‌ها پاسخ دادند، اما پروکسی قابل‌خواندن پیدا نشد. بعداً دوباره تلاش کن." else "${parsed.size} پروکسی یکتا از ${sourcesOk} منبع دریافت شد؛ هنوز اتصال تلگرام تأیید نشده است."
                            } catch (e: Exception) {
                                status = "دریافت فهرست ناموفق بود: ${e.message ?: "خطای شبکه"}"
                            } finally { loading = false }
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(if (loading) "در حال جست‌وجو…" else "جست‌وجوی پروکسی‌ها") }
                FilledTonalButton(
                    enabled = candidates.isNotEmpty() && !loading && !testing,
                    onClick = {
                        scope.launch {
                            testing = true
                            testProgress = 0
                            testTotal = minOf(candidates.size, 80)
                            status = "در حال سنجش دسترسی TCP؛ این تست، handshake تلگرام نیست…"
                            try {
                                val semaphore = Semaphore(4)
                                val checked = coroutineScope {
                                    candidates.take(80).map { candidate -> async {
                                        semaphore.withPermit {
                                            val ms = runCatching { PingService.tcpPing(candidate.host, candidate.port, timeoutMs = 2500) }.getOrDefault(-1L)
                                            testProgress += 1
                                            candidate.copy(tcpMs = ms.takeIf { it > 0 }, tcpTested = true)
                                        }
                                    } }.awaitAll()
                                }
                                val checkedIds = checked.associateBy { "${it.host.lowercase()}:${it.port}:${it.secret.lowercase()}" }
                                candidates = candidates.map { checkedIds["${it.host.lowercase()}:${it.port}:${it.secret.lowercase()}"] ?: it }
                                    .sortedWith(compareBy<TelegramProxyCandidate>({ if (it.tcpMs != null) 0 else if (!it.tcpTested) 1 else 2 }, { it.tcpMs ?: Long.MAX_VALUE }))
                                val reachable = checked.count { (it.tcpMs ?: -1L) > 0 }
                                status = "تست TCP تمام شد: ${reachable} از ${checked.size} میزبان پاسخ دادند. handshake واقعی MTProto در این نسخه انجام نشده است."
                            } catch (e: Exception) {
                                status = "تست TCP کامل نشد: ${e.message ?: "خطای شبکه"}"
                            } finally { testing = false }
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(if (testing) "در حال تست…" else "تست TCP") }
            }
            if (loading || testing) {
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth(), color = cs.primary)
                if (testing) Text("پیشرفت تست: $testProgress از $testTotal", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
            OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("فیلتر بر اساس دامنه یا منبع") })
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !onlyReachable, onClick = { onlyReachable = false }, label = { Text("همه") })
                FilterChip(selected = onlyReachable, onClick = { onlyReachable = true }, label = { Text("TCP پاسخ‌گو") })
                Text("${visible.size} نتیجه", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            }
            OutlinedTextField(value = manualInput, onValueChange = { manualInput = it }, modifier = Modifier.fillMaxWidth(), minLines = 2, label = { Text("لینک پروکسی یا host:port:secret") })
            FilledTonalButton(
                enabled = manualInput.isNotBlank() && !loading && !testing,
                onClick = {
                    val parsed = parseTelegramProxyCandidates(manualInput, "ورودی دستی")
                    if (parsed.isEmpty()) {
                        status = "لینک MTProto معتبر پیدا نشد؛ فرمت t.me/proxy یا host:port:secret را وارد کن."
                    } else {
                        val combined = (candidates + parsed).distinctBy { "${it.host.lowercase()}:${it.port}:${it.secret.lowercase()}" }
                        candidates = combined
                        status = "${parsed.size} ورودی بررسی شد؛ موارد تکراری حذف شدند."
                        manualInput = ""
                    }
                },
                shapes = ButtonDefaults.shapes(),
            ) { Text("افزودن ورودی دستی") }
            Text("$status", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            if (sourcesFailed > 0) Text("منابع ناموفق: $sourcesFailed از ${telegramProxySources.size}", style = MaterialTheme.typography.labelSmall, color = cs.error)
            Column(Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                visible.forEachIndexed { index, item ->
                    Surface(shape = RoundedCornerShape(16.dp), color = cs.surfaceVariant.copy(alpha = 0.45f), border = androidx.compose.foundation.BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.5f)), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${index + 1}. ${item.host}:${item.port}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(if (!item.tcpTested) "تست‌نشده" else if ((item.tcpMs ?: -1L) > 0) "TCP قابل‌دسترسی" else "TCP ناموفق", style = MaterialTheme.typography.labelSmall, color = if (item.tcpMs != null) cs.primary else cs.onSurfaceVariant)
                            }
                            Text(item.source, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                            Text(if (item.tcpMs != null) "تأخیر TCP: ${item.tcpMs} ms" else if (item.tcpTested) "پاسخی در مهلت تست دریافت نشد" else "تأخیر هنوز اندازه‌گیری نشده", style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { runCatching { uriHandler.openUri(item.link) }.onFailure { status = "بازکردن لینک ممکن نشد: ${it.message ?: "خطا"}" } }, shapes = ButtonDefaults.shapes()) { Text("بازکردن در تلگرام") }
                                TextButton(onClick = { status = "لینک انتخاب شد؛ برای امنیت، پیش از فعال‌سازی به اپراتور پروکسی اعتماد کن. این فهرست عمومی است." }) { Text("نکته امنیتی") }
                            }
                        }
                    }
                }
                if (visible.isEmpty()) Text("هنوز نتیجه‌ای نیست. جست‌وجو را اجرا کن یا فیلتر را پاک کن.", modifier = Modifier.padding(8.dp), color = cs.onSurfaceVariant)
            }
            Text("مهم: «TCP قابل‌دسترسی» فقط بازبودن مسیر TCP را نشان می‌دهد؛ برای تأیید واقعی Telegram/MTProto به handshake کلاینت تلگرام یا TDLib نیاز است. پروکسی عمومی ممکن است هر لحظه از کار بیفتد.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun LiquidHubInfoRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun LiquidQuickHubBubble(
    icon: String,
    title: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = tint.copy(alpha = 0.22f),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, tint.copy(alpha = 0.8f)),
        modifier = modifier,
    ) {
        Column(
            Modifier.fillMaxSize().padding(5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Brush.linearGradient(listOf(tint, tint.copy(alpha = 0.62f)))),
                contentAlignment = Alignment.Center,
            ) {
                Text(icon, color = Color.White, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(5.dp))
            Text(title, color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}

@Composable
private fun LiquidHeroCard(
    ui: ConnectionUiState,
    info: NetworkInfo,
    showConnectionInfo: Boolean,
    animate: Boolean,
    clock: State<Float>,
    celebrate: Int,
    compact: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onOpenQuickHub: () -> Unit,
    onStatusTap: () -> Unit,
    details: Boolean,
    onOpenInfo: (InfoFocus) -> Unit,
    onCloseInfo: () -> Unit,
    onRefreshPing: () -> Unit,
    onRefreshPlace: () -> Unit,
    onRefreshAll: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val state = ui.state
    val live = state == ConnectionState.Connected
    val searching = state == ConnectionState.Connecting || state == ConnectionState.Reconnecting
    val effects = MaterialTheme.motionScheme.slowEffectsSpec<Color>()
    val container by animateColorAsState(if (live) liquidTint(cs.primaryContainer) else liquidFill(), effects, label = "liquid-card")
    val ink by animateColorAsState(if (live) cs.onPrimaryContainer else cs.onSurface, effects, label = "liquid-ink")
    val sub by animateColorAsState(if (live) cs.onPrimaryContainer.copy(alpha = 0.78f) else cs.onSurfaceVariant, effects, label = "liquid-sub")
    val rise = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    Surface(
        shape = RoundedCornerShape(48.dp),
        color = container,
        border = liquidBorder(),
        modifier = Modifier.fillMaxSize(),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val open = live && showConnectionInfo && details
            val full = minOf(maxWidth * 0.52f, maxHeight * 0.46f).coerceIn(116.dp, 208.dp)
            val small = minOf(maxWidth * 0.26f, maxHeight * 0.17f).coerceIn(64.dp, 100.dp)
            val door by animateDpAsState(if (open) small else full, DoorSpring, label = "liquid-door-size")
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                LiquidOrb(state, ui.elapsedSeconds, door, clock, animate, celebrate, onTap, onLongPress, onOpenQuickHub)
                Spacer(Modifier.height(if (compact || open) 10.dp else 16.dp))
                AnimatedContent(
                    targetState = state,
                    transitionSpec = {
                        (fadeIn(tween(280)) + slideInVertically(rise) { it / 3 }) togetherWith fadeOut(tween(160))
                    },
                    label = "liquid-state",
                ) { s ->
                    Text(
                        text = stringResource(s.labelRes()),
                        style = if (compact) MaterialTheme.typography.headlineSmallEmphasized else MaterialTheme.typography.headlineMediumEmphasized,
                        color = ink,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onStatusTap,
                        ),
                    )
                }
                Spacer(Modifier.height(8.dp))
                when {
                    state == ConnectionState.Idle -> Text(
                        stringResource(Res.string.portal_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = sub,
                        textAlign = TextAlign.Center,
                    )
                    searching || state == ConnectionState.Disconnecting -> {
                        LinearWavyProgressIndicator(
                            modifier = Modifier.width(156.dp),
                            color = cs.primary,
                            trackColor = cs.primary.copy(alpha = 0.18f),
                        )
                        if (searching) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(Res.string.pixel_tap_cancel),
                                style = MaterialTheme.typography.bodySmall,
                                color = sub,
                            )
                        }
                    }
                    live -> if (showConnectionInfo) {
                        AnimatedContent(
                            targetState = open,
                            transitionSpec = {
                                (fadeIn(tween(220, delayMillis = 60)) + scaleIn(PanelSpring, initialScale = 0.92f)) togetherWith
                                    (fadeOut(tween(120)) + scaleOut(tween(160), targetScale = 0.96f)) using
                                    SizeTransform(clip = false) { _, _ -> PanelSizeSpring }
                            },
                            contentAlignment = Alignment.TopCenter,
                            label = "liquid-facts",
                        ) { expanded ->
                            if (expanded) {
                                Column {
                                    Spacer(Modifier.height(8.dp))
                                    LiquidDetailsPanel(
                                        info = info,
                                        onClose = onCloseInfo,
                                        onRefreshPing = onRefreshPing,
                                        onRefreshPlace = onRefreshPlace,
                                        onRefreshAll = onRefreshAll,
                                    )
                                }
                            } else {
                                LiquidFacts(info, onOpenInfo)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiquidOrb(
    state: ConnectionState,
    elapsed: Int,
    doorSize: Dp,
    clock: State<Float>,
    animate: Boolean,
    celebrate: Int,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onOpenQuickHub: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val searching = state == ConnectionState.Connecting || state == ConnectionState.Reconnecting
    val live = state == ConnectionState.Connected
    val disconnecting = state == ConnectionState.Disconnecting

    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val squash by animateFloatAsState(
        if (pressed) 0.9f else 1f,
        spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "liquid-orb-squash",
    )
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val start by animateColorAsState(if (disconnecting) cs.primary.copy(alpha = 0.55f) else cs.primary, effects, label = "liquid-orb-a")
    val end by animateColorAsState(
        when {
            live || searching -> cs.tertiary
            disconnecting -> cs.primary.copy(alpha = 0.55f)
            else -> cs.secondary
        },
        effects,
        label = "liquid-orb-b",
    )
    val glow by animateFloatAsState(if (live) 1f else if (searching) 0.7f else 0.35f, MaterialTheme.motionScheme.slowEffectsSpec(), label = "liquid-orb-glow")
    val actionLabel = stringResource(
        when {
            state == ConnectionState.Disconnecting -> Res.string.state_disconnecting
            state.isTransitioning -> Res.string.action_cancel
            state.isActive -> Res.string.action_disconnect
            else -> Res.string.action_connect
        },
    )
    val density = LocalDensity.current

    Box(
        Modifier
            .size(doorSize)
            .tourTarget(TourTargets.CORE)
            .graphicsLayer {
                val breathe = if (live && animate) 1f + 0.018f * sin(clock.value * 1.6f) else 1f
                scaleX = squash * breathe
                scaleY = squash * breathe
            }
            .semantics { contentDescription = actionLabel }
            .pointerInput(onTap, onLongPress, onOpenQuickHub) {
                coroutineScope {
                    val gestureScope = this
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var longTriggered = false
                        val burstJob = gestureScope.launch {
                            delay(600L)
                            longTriggered = true
                            onLongPress()
                        }
                        val hubJob = gestureScope.launch {
                            delay(5_000L)
                            longTriggered = true
                            onOpenQuickHub()
                        }
                        waitForUpOrCancellation()
                        burstJob.cancel()
                        hubJob.cancel()
                        if (!longTriggered) onTap()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val t = clock.value
            val c = center
            val r = size.minDimension * 0.34f

            // Soft halo that breathes with the connection state.
            drawCircle(
                Brush.radialGradient(
                    listOf(start.copy(alpha = 0.55f * glow), end.copy(alpha = 0.18f * glow), Color.Transparent),
                    c,
                    r * 1.75f,
                ),
                r * 1.75f,
                c,
            )

            // Ripples travelling outwards while live or searching.
            if (animate && (live || searching)) {
                for (i in 0 until 3) {
                    val phase = ((t * if (searching) 0.9f else 0.35f) + i / 3f) % 1f
                    drawCircle(
                        start.copy(alpha = (1f - phase) * 0.38f),
                        r * (1.05f + 0.55f * phase),
                        c,
                        style = Stroke(width = 2.dp.toPx() * (1f - phase * 0.6f)),
                    )
                }
            }

            // Glass body: bright core up-left, deep colour down-right.
            val hi = Offset(c.x - r * 0.32f, c.y - r * 0.38f)
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.92f).compositeOver(start), start, end),
                    hi,
                    r * 1.7f,
                ),
                r,
                c,
            )

            // Inner shade ring for depth.
            drawCircle(
                Brush.radialGradient(
                    0.72f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.22f),
                    center = c,
                    radius = r,
                ),
                r,
                c,
            )

            // Rim light.
            drawCircle(
                Brush.sweepGradient(
                    listOf(
                        Color.White.copy(alpha = 0.85f),
                        Color.White.copy(alpha = 0.08f),
                        Color.White.copy(alpha = 0.45f),
                        Color.White.copy(alpha = 0.08f),
                        Color.White.copy(alpha = 0.85f),
                    ),
                    c,
                ),
                r - 0.75.dp.toPx(),
                c,
                style = Stroke(1.5.dp.toPx()),
            )

            // Specular highlight that slowly wanders while connected.
            val sway = if (animate && live) sin(t * 0.7f) * 0.06f * r else 0f
            rotate(-18f, c) {
                drawOval(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0f)),
                        startY = c.y - r * 0.92f,
                        endY = c.y - r * 0.2f,
                    ),
                    topLeft = Offset(c.x - r * 0.62f + sway, c.y - r * 0.92f),
                    size = Size(r * 1.0f, r * 0.62f),
                )
            }

            // Searching: a bright arc orbits the orb.
            if (searching) {
                val sweep = Brush.sweepGradient(
                    0f to Color.Transparent,
                    0.7f to end.copy(alpha = 0.2f),
                    1f to Color.White.copy(alpha = 0.95f),
                    center = c,
                )
                rotate(if (animate) t * 260f else 0f, c) {
                    drawArc(
                        sweep,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(c.x - r * 1.22f, c.y - r * 1.22f),
                        size = Size(r * 2.44f, r * 2.44f),
                        style = Stroke(4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round),
                    )
                }
            }
        }
        AnimatedContent(
            targetState = live,
            transitionSpec = { (fadeIn(tween(260)) + scaleIn(tween(260), 0.8f)) togetherWith (fadeOut(tween(160)) + scaleOut(tween(160), 0.8f)) },
            label = "liquid-orb-center",
        ) { open ->
            if (open) {
                Text(
                    text = tabularDigits(formatElapsed(elapsed)),
                    style = MaterialTheme.typography.headlineSmallEmphasized.copy(
                        fontSize = with(density) { (doorSize * 0.11f).toSp() },
                    ),
                    color = Color.White,
                    maxLines = 1,
                    softWrap = false,
                )
            } else {
                Icon(
                    painterResource(Res.drawable.ic_fill_power),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(doorSize * 0.22f),
                )
            }
        }
        CelebrationBurst(celebrate, Modifier.matchParentSize())
    }
}

private const val BURST_BITS = 16

@Composable
private fun CelebrationBurst(trigger: Int, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val colors = listOf(cs.primary, cs.tertiary, cs.secondary, cs.tertiaryContainer, cs.secondaryContainer)
    val progress = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(950, easing = FastOutSlowInEasing))
    }
    Canvas(modifier) {
        val p = progress.value
        if (p >= 1f) return@Canvas
        val bit = 5.dp.toPx()
        val reach = size.minDimension * 0.9f
        for (i in 0 until BURST_BITS) {
            val a = i * (2f * PI.toFloat() / BURST_BITS) + 0.35f
            val d = size.minDimension * 0.42f + reach * p
            val pos = Offset(center.x + cos(a) * d, center.y + sin(a) * d)
            val color = colors[i % colors.size].copy(alpha = (1f - p).coerceIn(0f, 1f))
            val s = bit * (1f - p * 0.35f)
            if (i % 2 == 0) {
                drawCircle(color, s, pos)
            } else {
                drawRoundRect(color, pos - Offset(s, s), Size(s * 2f, s * 2f), CornerRadius(s * 0.6f))
            }
        }
    }
}

@Composable
private fun LiquidFacts(info: NetworkInfo, onOpen: (InfoFocus) -> Unit) {
    val place = listOfNotNull(info.city, localCountry(info.countryCode) ?: info.country).joinToString(", ")
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.widthIn(max = FACTS_MAX_WIDTH),
    ) {
        FactChip({ onOpen(InfoFocus.Place) }) {
            if (info.countryCode != null) {
                FlagBadge(countryCode = info.countryCode, size = 16.dp)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                place.ifBlank { stringResource(Res.string.info_no_ip) },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        info.pingMs?.let { FactChip({ onOpen(InfoFocus.Ping) }) { Text("%d ms".format(it), maxLines = 1) } }
        info.ipv4?.let { FactChip({ onOpen(InfoFocus.Place) }) { Text(ltrIsolate(it), maxLines = 1) } }
    }
}

private val FACTS_MAX_WIDTH = 264.dp

@Composable
private fun FactChip(onClick: () -> Unit, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = cs.onPrimaryContainer.copy(alpha = 0.09f),
        contentColor = cs.onPrimaryContainer,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            CompositionLocalProvider(androidx.compose.material3.LocalTextStyle provides MaterialTheme.typography.labelLarge) {
                content()
            }
        }
    }
}

@Composable
private fun LiquidErrorCard(
    error: String?,
    onRetry: () -> Unit,
    onOtherServer: () -> Unit,
    onShowLogs: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val fault = error?.takeIf { it.isNotBlank() }?.let { ConnectionFault.classify(it) }
    val known = fault != null && fault.cause != ConnectionFault.Cause.Unknown
    val shake = remember { Animatable(0f) }
    var rawOpen by remember(error) { mutableStateOf(false) }
    LaunchedEffect(error) {
        for (x in listOf(12f, -10f, 7f, -4f, 2f, 0f)) shake.animateTo(x, tween(55))
    }
    val density = LocalDensity.current
    Surface(
        shape = RoundedCornerShape(48.dp),
        color = liquidTint(cs.errorContainer, 1.25f),
        contentColor = cs.onErrorContainer,
        border = liquidBorder(),
        modifier = Modifier.fillMaxSize().graphicsLayer { translationX = shake.value * density.density },
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(96.dp).clip(MaterialShapes.Burst.toShape()).background(cs.error),
                contentAlignment = Alignment.Center,
            ) {
                Text("!", style = MaterialTheme.typography.displaySmallEmphasized, color = cs.onError)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(if (known) faultTitle(fault!!.cause) else Res.string.state_error),
                style = MaterialTheme.typography.headlineSmallEmphasized,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(if (known) faultHint(fault!!.cause) else Res.string.fault_unknown),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = cs.onErrorContainer.copy(alpha = 0.85f),
            )
            if (fault != null && fault.raw.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    fault.raw,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = cs.onErrorContainer.copy(alpha = 0.7f),
                    maxLines = if (rawOpen) 10 else 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.clickable { rawOpen = !rawOpen },
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRetry, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.action_retry)) }
                FilledTonalButton(onClick = onOtherServer, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(Res.string.pixel_other_server))
                }
            }
            TextButton(onClick = onShowLogs) { Text(stringResource(Res.string.fault_open_logs)) }
        }
    }
}

@Composable
private fun LiquidServerRow(
    name: String?,
    detail: String?,
    locked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val top by animateDpAsState(if (pressed) 18.dp else 28.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "liquid-row-top")
    val bottom by animateDpAsState(if (pressed) 14.dp else 6.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "liquid-row-bottom")
    val turn by animateFloatAsState(if (pressed) 45f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "liquid-row-turn")
    Surface(
        onClick = onClick,
        interactionSource = source,
        shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom),
        color = liquidFill(),
        border = liquidBorder(),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer { rotationZ = turn }
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(cs.primary, cs.tertiary))),
                )
                Icon(
                    painterResource(if (locked) Res.drawable.ic_encrypted else Res.drawable.ic_public),
                    contentDescription = null,
                    tint = cs.onPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = name ?: stringResource(Res.string.home_select_config),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = detail ?: stringResource(
                        when {
                            name == null -> Res.string.home_no_config_body
                            locked -> Res.string.home_tap_to_change_vault
                            else -> Res.string.home_tap_to_change
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(cs.onSurface.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(Res.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun LiquidStats(
    live: Boolean,
    downloadBps: Long,
    uploadBps: Long,
    totalDownload: Long,
    totalUpload: Long,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        LiquidStat(
            icon = Res.drawable.ic_download,
            label = stringResource(Res.string.home_download),
            bps = downloadBps,
            total = totalDownload,
            live = live,
            badge = cs.tertiaryContainer,
            onBadge = cs.onTertiaryContainer,
            shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomEnd = 6.dp, bottomStart = 28.dp),
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        LiquidStat(
            icon = Res.drawable.ic_upload,
            label = stringResource(Res.string.home_upload),
            bps = uploadBps,
            total = totalUpload,
            live = live,
            badge = cs.primaryContainer,
            onBadge = cs.onPrimaryContainer,
            shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 6.dp, bottomEnd = 28.dp),
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

@Composable
private fun LiquidStat(
    icon: DrawableResource,
    label: String,
    bps: Long,
    total: Long,
    live: Boolean,
    badge: Color,
    onBadge: Color,
    shape: RoundedCornerShape,
    modifier: Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val (value, unit) = formatRate(if (live) bps else 0L)
    val busy = live && bps > 0L
    val turn by animateFloatAsState(if (busy) 45f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "liquid-stat-turn")
    Surface(shape = shape, color = liquidFill(), border = liquidBorder(), modifier = modifier) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer { rotationZ = turn }
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(badge, badge.copy(alpha = 0.55f)))),
                )
                Icon(painterResource(icon), contentDescription = null, tint = onBadge, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.Bottom) {
                    RollingText(
                        value,
                        MaterialTheme.typography.titleLargeEmphasized,
                        if (busy) cs.onSurface else cs.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(unit, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, maxLines = 1, modifier = Modifier.padding(bottom = 3.dp))
                }
                Text(
                    tabularDigits(formatBytes(total)),
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun RollingText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val roll = MaterialTheme.motionScheme.fastSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    val digitStyle = remember(style) { style.copy(fontFeatureSettings = TABULAR) }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(modifier) {
            text.forEachIndexed { index, char ->
                key(index) {
                    AnimatedContent(
                        targetState = char,
                        transitionSpec = {
                            val up = targetState > initialState
                            (slideInVertically(roll) { if (up) it else -it } + fadeIn(tween(120))) togetherWith
                                (slideOutVertically(roll) { if (up) -it else it } + fadeOut(tween(120))) using
                                SizeTransform(clip = true)
                        },
                        label = "rolling-char",
                    ) { c ->
                        Text(c.toString(), style = if (c.isDigit()) digitStyle else style, color = color, maxLines = 1)
                    }
                }
            }
        }
    }
}

private const val TABULAR = "tnum"

private val DoorSpring = spring<Dp>(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)

private val PanelSpring = spring<Float>(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)

private val PanelSizeSpring = spring<IntSize>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)

internal fun tabularDigits(text: String): AnnotatedString = buildAnnotatedString {
    var start = 0
    while (start < text.length) {
        val digits = text[start].isDigit()
        var end = start + 1
        while (end < text.length && text[end].isDigit() == digits) end++
        val from = length
        append(text, start, end)
        if (digits) addStyle(SpanStyle(fontFeatureSettings = TABULAR), from, length)
        start = end
    }
}
