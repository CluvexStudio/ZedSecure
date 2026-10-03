@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package dev.cluvex.zedsecure.ui.home

import org.jetbrains.compose.resources.DrawableResource

import org.jetbrains.compose.resources.StringResource

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.mutableFloatStateOf
import dev.cluvex.zedsecure.ui.theme.MotionBudget
import dev.cluvex.zedsecure.ui.theme.LocalMotionBudget
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isFinite
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.*
import dev.cluvex.zedsecure.domain.model.ConnectButtonStyle
import dev.cluvex.zedsecure.domain.model.ConnectionState
import dev.cluvex.zedsecure.ui.onboarding.TourTargets
import dev.cluvex.zedsecure.ui.onboarding.tourTarget
import dev.cluvex.zedsecure.ui.components.MorphingBlob
import dev.cluvex.zedsecure.ui.components.NoteText
import dev.cluvex.zedsecure.ui.components.OrganicSurface
import dev.cluvex.zedsecure.ui.connection.ConnectionViewModel
import dev.cluvex.zedsecure.ui.format.formatBytes
import dev.cluvex.zedsecure.ui.format.formatElapsed
import dev.cluvex.zedsecure.ui.format.formatRate
import dev.cluvex.zedsecure.ui.theme.readableOn
import dev.cluvex.zedsecure.domain.model.TrafficCardStyle
import dev.cluvex.zedsecure.domain.model.TrafficTileSize
import dev.cluvex.zedsecure.ui.theme.Personalization
import dev.cluvex.zedsecure.ui.theme.ZedGradients
import dev.cluvex.zedsecure.ui.theme.ZedCyan
import dev.cluvex.zedsecure.ui.theme.ZedHotPink
import dev.cluvex.zedsecure.ui.theme.ZedLime
import dev.cluvex.zedsecure.ui.theme.ZedViolet
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun HomeScreen(
    connectionVm: ConnectionViewModel,
    activeConfigName: String?,
    contentPadding: PaddingValues,

    activeConfigDetail: String? = null,

    exitGeneration: Int = 0,
    onToggleConnection: () -> Unit,
    onBrowseConfigs: () -> Unit,
    onSecretUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
    lockedNote: String? = null,
    localProxyAddress: String? = null,
    activeLocked: Boolean = false,
    reduceMotion: Boolean = false,

    showConnectionInfo: Boolean = true,

    ipApiUrl: String = "",

    delayTestUrl: String = "",

    personalization: Personalization = Personalization.Default,

    connectStyle: ConnectButtonStyle = ConnectButtonStyle.Pill,
    homeVm: HomeViewModel = viewModel { HomeViewModel() },
) {
    val ui by connectionVm.ui.collectAsStateWithLifecycle()
    val live = ui.state == ConnectionState.Connected
    val info by homeVm.info.collectAsStateWithLifecycle()
    val infoExpanded by homeVm.expanded.collectAsStateWithLifecycle()

    LaunchedEffect(live, ui.sessionId, exitGeneration, showConnectionInfo, ipApiUrl, delayTestUrl) {
        homeVm.preferredIpApiUrl = ipApiUrl.ifBlank { null }
        homeVm.preferredDelayUrl = delayTestUrl.ifBlank { null }
        if (live && showConnectionInfo) homeVm.refresh(settleDelayMs = 300) else homeVm.clear()
    }

    var burst by remember { mutableIntStateOf(0) }

    var laser by remember { mutableIntStateOf(0) }

    var statusTaps by remember { mutableIntStateOf(0) }
    var statusLastTap by remember { mutableLongStateOf(0L) }
    var showLogs by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        DecorativeBackdrop(reduceMotion = reduceMotion)

        ConnectingBackdrop(state = ui.state, sessionId = ui.sessionId, reduceMotion = reduceMotion)

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 22.dp),
        ) {
            val heroSize = minOf(216.dp, maxHeight * 0.29f)

            val compact = maxHeight < 620.dp

            val upperScroll = rememberScrollState()
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                StageColumn(
                    viewport = maxHeight,
                    stageMin = STAGE_MIN,
                    modifier = Modifier.fillMaxWidth().scrollFade(upperScroll).verticalScroll(upperScroll),
                ) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                BrandHeader(
                    state = ui.state,
                    onSecretUnlocked = onSecretUnlocked,
                    onShowLogs = { showLogs = true },
                )

                val fault = when {
                    ui.state == ConnectionState.Error ->
                        ui.error?.takeIf { it.isNotBlank() }
                            ?.let { dev.cluvex.zedsecure.core.ConnectionFault.classify(it) }
                    ui.state == ConnectionState.Connected -> info.fault
                    else -> null
                }
                AnimatedVisibility(visible = fault != null) {
                    fault?.let {
                        FaultBanner(
                            fault = it,
                            connected = ui.state == ConnectionState.Connected,
                            onShowLogs = { showLogs = true },
                        )
                    }
                }

                AnimatedVisibility(visible = live && showConnectionInfo) {
                    Column {
                        Spacer(Modifier.height(10.dp))
                        ConnectionInfoPill(
                            info = info,
                            expanded = infoExpanded,
                            onToggle = homeVm::toggleExpanded,
                            onRefresh = homeVm::refresh,
                        )
                    }
                }

                if (personalization.showTrafficTiles && personalization.trafficTilesAboveHero) {
                    Spacer(Modifier.height(10.dp))

                    Box(Modifier.tourTarget(TourTargets.TRAFFIC)) {
                      TrafficPanel(
                        live = live,
                        downloadBps = ui.downloadBps,
                        uploadBps = ui.uploadBps,
                        totalDownload = ui.totalDownload,
                        totalUpload = ui.totalUpload,
                        personalization = personalization,
                      )
                    }
                }
                }

                BoxWithConstraints(modifier = Modifier.fillMaxWidth().layoutId(STAGE_ID)) {
                  val stage = maxHeight
                  val tight = stage < 260.dp
                  val stageHero = minOf(heroSize, stage * 0.58f)
                  Column(
                    modifier = Modifier.fillMaxSize().clipToBounds(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                  ) {
                    Hero(
                        state = ui.state,
                        elapsed = ui.elapsedSeconds,
                        sessionId = ui.sessionId,
                        size = stageHero,
                        onLongPress = {
                            burst++
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },

                        onTap = if (connectStyle == ConnectButtonStyle.Hero) {
                            {
                                if (ui.state != ConnectionState.Disconnecting) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onToggleConnection()
                                }
                            }
                        } else null,
                    )

                    Spacer(Modifier.height(if (tight) 8.dp else 20.dp))

                    AnimatedContent(
                        targetState = ui.state,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "status-text",
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            val now = System.currentTimeMillis()
                            statusTaps = if (now - statusLastTap < TAP_WINDOW_MS) statusTaps + 1 else 1
                            statusLastTap = now
                            if (statusTaps >= 3) {
                                statusTaps = 0
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                laser++
                            }
                        },
                    ) { s ->

                        Text(
                            text = stringResource(s.labelRes()),
                            style = if (tight) {
                                MaterialTheme.typography.headlineMediumEmphasized
                            } else {
                                MaterialTheme.typography.displaySmallEmphasized
                            },
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            color = when {
                                s == ConnectionState.Error -> MaterialTheme.colorScheme.error

                                s.isActive || s == ConnectionState.Disconnecting -> Color.White
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }

                    if (localProxyAddress != null && live) {
                        val platform = dev.cluvex.zedsecure.ui.platform.LocalPlatform.current
                        val copied = stringResource(Res.string.copied)
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            onClick = {
                                platform.copyToClipboard(localProxyAddress)
                                platform.toast(copied)
                            },
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Text(
                                text = stringResource(Res.string.home_local_proxy, localProxyAddress),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }

                    if (lockedNote != null) {
                        val noteScroll = rememberScrollState()
                        Spacer(Modifier.height(16.dp))
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            NoteText(
                                text = lockedNote,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 140.dp)
                                    .verticalScroll(noteScroll)
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                    }
                  }
                }

                if (personalization.showTrafficTiles && !personalization.trafficTilesAboveHero) {
                    Box(Modifier.tourTarget(TourTargets.TRAFFIC)) {
                      TrafficPanel(
                        live = live,
                        downloadBps = ui.downloadBps,
                        uploadBps = ui.uploadBps,
                        totalDownload = ui.totalDownload,
                        totalUpload = ui.totalUpload,
                        personalization = personalization,
                      )
                    }
                    Spacer(Modifier.height(10.dp))
                }
                }
              }
                ActiveConfigCard(
                    modifier = Modifier.tourTarget(TourTargets.CONFIG_CARD),
                    name = activeConfigName,
                    detail = activeConfigDetail,
                    locked = activeLocked,
                    onClick = onBrowseConfigs,
                    personalization = personalization,
                )
                Spacer(Modifier.height(if (compact) 8.dp else 14.dp))
                when (connectStyle) {
                    ConnectButtonStyle.Hero -> Unit
                    ConnectButtonStyle.Pill -> ConnectButton(
                        state = ui.state,
                        onToggle = onToggleConnection,
                        customColor = personalization.connectColor,
                        customActiveColor = personalization.connectedColor,
                        compact = compact,
                    )
                    else -> AltConnectControl(
                        style = connectStyle,
                        state = ui.state,
                        onToggle = onToggleConnection,
                        customColor = personalization.connectColor,
                        customActiveColor = personalization.connectedColor,
                        compact = compact,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        ParticleBurst(trigger = burst)

        dev.cluvex.zedsecure.ui.easteregg.LaserBounce(
            trigger = laser,
            color = personalization.connectColor ?: MaterialTheme.colorScheme.primary,
            reduceMotion = reduceMotion,
        )
    }

    if (showLogs) LogSheet(onDismiss = { showLogs = false })
}

private const val STAGE_ID = "home-stage"

private val STAGE_MIN = 148.dp

private fun Modifier.scrollFade(state: ScrollState, length: Dp = 28.dp): Modifier = this
    .graphicsLayer {
        compositingStrategy = if (state.canScrollForward || state.canScrollBackward) {
            CompositingStrategy.Offscreen
        } else {
            CompositingStrategy.Auto
        }
    }
    .drawWithContent {
        drawContent()
        val fade = length.toPx()
        if (state.canScrollForward) {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.Black, Color.Transparent),
                    startY = size.height - fade,
                    endY = size.height,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
        if (state.canScrollBackward) {
            drawRect(
                brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = 0f, endY = fade),
                blendMode = BlendMode.DstIn,
            )
        }
    }

private const val MAX_STAGE_PX = 16_384

@Composable
private fun StageColumn(
    viewport: Dp,
    stageMin: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content, modifier) { measurables, constraints ->

        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth

        val natural = Constraints(maxWidth = width)
        val placeables = arrayOfNulls<Placeable>(measurables.size)
        var stageIndex = -1
        var fixed = 0
        measurables.forEachIndexed { i, child ->
            if (child.layoutId == STAGE_ID) {
                stageIndex = i
            } else {
                placeables[i] = child.measure(natural).also { fixed += it.height }
            }
        }

        val viewportPx = if (viewport.isFinite) viewport.roundToPx().coerceIn(0, MAX_STAGE_PX) else 0
        val stagePx = if (stageIndex < 0) 0 else maxOf(stageMin.roundToPx(), viewportPx - fixed).coerceIn(0, MAX_STAGE_PX)
        if (stageIndex >= 0) {
            placeables[stageIndex] = measurables[stageIndex].measure(Constraints.fixed(width, stagePx))
        }
        layout(width, maxOf(viewportPx, fixed + stagePx)) {
            var y = 0
            placeables.forEach { placeable ->
                if (placeable == null) return@forEach
                placeable.placeRelative((width - placeable.width) / 2, y)
                y += placeable.height
            }
        }
    }
}

@Composable
private fun DecorativeBackdrop(reduceMotion: Boolean) {
    val budget = LocalMotionBudget.current
    if (budget != MotionBudget.Full) {
        OrganicSurface(
            brush = ZedGradients.idle,
            modifier = Modifier
                .size(300.dp)
                .offset(x = 180.dp, y = (-130).dp),
        )
        return
    }
    val spin by rememberInfiniteTransition(label = "backdrop").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(120_000), RepeatMode.Restart),
        label = "spin",
    )
    OrganicSurface(
        brush = ZedGradients.idle,
        modifier = Modifier
            .size(300.dp)
            .offset(x = 180.dp, y = (-130).dp)
            .rotate(if (reduceMotion) 0f else spin),
    )
}

@Composable
private fun coreTextSize(core: Dp, ratio: Float) = with(LocalDensity.current) {
    (core.toPx() * ratio).toSp()
}

@Composable
private fun Hero(
    state: ConnectionState,
    elapsed: Int,
    sessionId: Int,
    size: Dp,
    onLongPress: () -> Unit,

    onTap: (() -> Unit)? = null,
) {
    val morph by animateFloatAsState(
        targetValue = if (state.isActive) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "morph",
    )
    val budget = LocalMotionBudget.current
    val transitional = state == ConnectionState.Connecting || state == ConnectionState.Reconnecting
    val breatheState = if (budget == MotionBudget.Full || budget == MotionBudget.Throttled && transitional) {
        rememberInfiniteTransition(label = "breathe").animateFloat(
            initialValue = 0.97f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(tween(2800), RepeatMode.Reverse),
            label = "breathe",
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }
    val breathe by breatheState
    val brush = when (state) {
        ConnectionState.Connected -> ZedGradients.forSession(sessionId)
        ConnectionState.Connecting, ConnectionState.Reconnecting -> ZedGradients.connecting
        else -> ZedGradients.idle
    }

    MorphingBlob(
        progress = morph,
        brush = brush,
        modifier = Modifier
            .size(size)
            .tourTarget(TourTargets.CORE)
            .then(
                if (budget == MotionBudget.Full) {
                    Modifier.scale(if (state.isActive) breathe else 1f)
                } else {
                    Modifier.graphicsLayer {
                        val s = if (state.isActive) breatheState.value else 1f
                        scaleX = s
                        scaleY = s
                    }
                },
            )

            .pointerInput(onTap) {
                detectTapGestures(
                    onLongPress = { onLongPress() },
                    onTap = onTap?.let { handler -> { _ -> handler() } },
                )
            },
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "hero-center",
        ) { s ->
            when {
                s.isTransitioning -> ContainedLoadingIndicator(
                    modifier = Modifier.size(size * 0.39f),
                    containerColor = Color.Transparent,
                )

                s == ConnectionState.Connected -> Text(
                    text = formatElapsed(elapsed),
                    fontSize = coreTextSize(size, 0.194f),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    maxLines = 1,
                    softWrap = false,
                )
                else -> Text(
                    text = "Z",
                    fontSize = coreTextSize(size, 0.315f),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.White,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun BrandHeader(
    state: ConnectionState,
    onSecretUnlocked: () -> Unit,
    onShowLogs: () -> Unit,
) {
    var taps by remember { mutableIntStateOf(0) }
    var lastTap by remember { mutableLongStateOf(0L) }
    val haptics = LocalHapticFeedback.current

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.weight(1f, fill = false), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f, fill = false)) { StatusChip(state) }
            Spacer(Modifier.width(4.dp))

            androidx.compose.material3.IconButton(onClick = onShowLogs) {
                Icon(
                    painterResource(Res.drawable.ic_description),
                    contentDescription = stringResource(Res.string.logs_title),
                    tint = if (state.isActive || state == ConnectionState.Disconnecting) Color.White
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = stringResource(Res.string.app_name),

            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,

            color = if (state.isActive || state == ConnectionState.Disconnecting) Color.White
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    val now = System.currentTimeMillis()
                    taps = if (now - lastTap < TAP_WINDOW_MS) taps + 1 else 1
                    lastTap = now
                    if (taps >= TAPS_TO_UNLOCK) {
                        taps = 0
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSecretUnlocked()
                    } else if (taps > 1) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }

                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun FaultBanner(
    fault: dev.cluvex.zedsecure.core.ConnectionFault,
    connected: Boolean,
    onShowLogs: () -> Unit,
) {
    var expanded by remember(fault.raw) { mutableStateOf(false) }
    val known = fault.cause != dev.cluvex.zedsecure.core.ConnectionFault.Cause.Unknown
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clickable { expanded = !expanded },
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(
                    if (connected) Res.string.fault_heading_health else Res.string.fault_heading_connect,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
            )
            if (known) {
                Text(
                    stringResource(faultTitle(fault.cause)),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    stringResource(faultHint(fault.cause)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            if (fault.raw.isNotBlank()) {
                Text(
                    fault.raw,
                    style = if (known) MaterialTheme.typography.labelSmall.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    ) else MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = if (known) 0.75f else 1f),

                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Text(
                stringResource(Res.string.fault_open_logs),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.clickable(onClick = onShowLogs).padding(top = 2.dp),
            )
        }
    }
}

private fun faultTitle(c: dev.cluvex.zedsecure.core.ConnectionFault.Cause) = when (c) {
    dev.cluvex.zedsecure.core.ConnectionFault.Cause.Timeout -> Res.string.fault_timeout
    dev.cluvex.zedsecure.core.ConnectionFault.Cause.ServerClosed -> Res.string.fault_server_closed
    dev.cluvex.zedsecure.core.ConnectionFault.Cause.Refused -> Res.string.fault_refused
    dev.cluvex.zedsecure.core.ConnectionFault.Cause.Reset -> Res.string.fault_reset
    dev.cluvex.zedsecure.core.ConnectionFault.Cause.Unreachable -> Res.string.fault_unreachable
    dev.cluvex.zedsecure.core.ConnectionFault.Cause.DnsFailed -> Res.string.fault_dns
    dev.cluvex.zedsecure.core.ConnectionFault.Cause.TlsHandshake -> Res.string.fault_tls
    dev.cluvex.zedsecure.core.ConnectionFault.Cause.Certificate -> Res.string.fault_certificate
    dev.cluvex.zedsecure.core.ConnectionFault.Cause.Reality -> Res.string.fault_reality
    dev.cluvex.zedsecure.core.ConnectionFault.Cause.Auth -> Res.string.fault_auth
    dev.cluvex.zedsecure.core.ConnectionFault.Cause.Transport -> Res.string.fault_transport
    dev.cluvex.zedsecure.core.ConnectionFault.Cause.Unknown -> Res.string.fault_unknown
}

private fun faultHint(c: dev.cluvex.zedsecure.core.ConnectionFault.Cause) = when (c) {
    dev.cluvex.zedsecure.core.ConnectionFault.Cause.Timeout -> Res.string.fault_timeout_hint
    dev.cluvex.zedsecure.core.ConnectionFault.Cause.ServerClosed -> Res.string.fault_server_closed_hint
    dev.cluvex.zedsecure.core.ConnectionFault.Cause.Refused -> Res.string.fault_refused_hint
    dev.cluvex.zedsecure.core.ConnectionFault.Cause.Reset -> Res.string.fault_reset_hint
    dev.cluvex.zedsecure.core.ConnectionFault.Cause.Unreachable -> Res.string.fault_unreachable_hint
    dev.cluvex.zedsecure.core.ConnectionFault.Cause.DnsFailed -> Res.string.fault_dns_hint
    dev.cluvex.zedsecure.core.ConnectionFault.Cause.TlsHandshake -> Res.string.fault_tls_hint
    dev.cluvex.zedsecure.core.ConnectionFault.Cause.Certificate -> Res.string.fault_certificate_hint
    dev.cluvex.zedsecure.core.ConnectionFault.Cause.Reality -> Res.string.fault_reality_hint
    dev.cluvex.zedsecure.core.ConnectionFault.Cause.Auth -> Res.string.fault_auth_hint
    dev.cluvex.zedsecure.core.ConnectionFault.Cause.Transport -> Res.string.fault_transport_hint
    dev.cluvex.zedsecure.core.ConnectionFault.Cause.Unknown -> Res.string.fault_unknown
}

private const val TAPS_TO_UNLOCK = 5

private const val TAP_WINDOW_MS = 1_200L

@Composable
private fun StatusChip(state: ConnectionState) {
    val label = when (state) {
        ConnectionState.Connected -> stringResource(Res.string.state_connected)
        ConnectionState.Connecting, ConnectionState.Reconnecting ->
            stringResource(Res.string.state_connecting)
        ConnectionState.Error -> stringResource(Res.string.state_error)
        else -> stringResource(Res.string.state_idle)
    }

    val scope = rememberCoroutineScope()
    val dragX = remember { Animatable(0f) }
    val dragY = remember { Animatable(0f) }
    val haptics = LocalHapticFeedback.current

    val stretchX = (abs(dragX.value) / 90f).coerceAtMost(0.30f)
    val stretchY = (abs(dragY.value) / 90f).coerceAtMost(0.30f)

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .graphicsLayer {
                translationX = dragX.value
                translationY = dragY.value
                scaleX = 1f + stretchX - stretchY * 0.5f
                scaleY = 1f + stretchY - stretchX * 0.5f
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        scope.launch {
                            val pull = 1f / (1f + hypot(dragX.value, dragY.value) / 240f)
                            dragX.snapTo(dragX.value + drag.x * pull)
                            dragY.snapTo(dragY.value + drag.y * pull)
                        }
                    },
                    onDragEnd = {
                        val spec = spring<Float>(dampingRatio = 0.32f, stiffness = Spring.StiffnessMediumLow)
                        scope.launch { dragX.animateTo(0f, spec) }
                        scope.launch { dragY.animateTo(0f, spec) }
                    },
                )
            },
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,

                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TrafficPanel(
    live: Boolean,
    downloadBps: Long,
    uploadBps: Long,
    totalDownload: Long,
    totalUpload: Long,
    personalization: Personalization = Personalization.Default,
) {
    val down = personalization.downloadColor ?: MaterialTheme.colorScheme.primary
    val up = personalization.uploadColor ?: MaterialTheme.colorScheme.tertiary
    when (personalization.trafficCardStyle) {
        TrafficCardStyle.Duo -> DuoTrafficCard(live, downloadBps, uploadBps, totalDownload, totalUpload, down, up, personalization)
        TrafficCardStyle.Rings -> RingTrafficPanel(live, downloadBps, uploadBps, totalDownload, totalUpload, down, up, personalization)
        TrafficCardStyle.Minimal -> MinimalTrafficRow(live, downloadBps, uploadBps, down, up)
        TrafficCardStyle.Graph -> GraphTrafficPanel(live, downloadBps, uploadBps, totalDownload, totalUpload, down, up, personalization)
        TrafficCardStyle.Cards -> CardsTrafficPanel(live, downloadBps, uploadBps, totalDownload, totalUpload, personalization)
    }
}

@Composable
private fun CardsTrafficPanel(
    live: Boolean,
    downloadBps: Long,
    uploadBps: Long,
    totalDownload: Long,
    totalUpload: Long,
    personalization: Personalization,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(personalization.density.gapDp.dp),
    ) {
        TrafficTile(
            size = personalization.trafficTileSize,
            icon = Res.drawable.ic_download,
            badge = MaterialShapes.Cookie7Sided,
            label = stringResource(Res.string.home_download),
            bytesPerSecond = downloadBps,
            total = totalDownload,
            accent = personalization.downloadColor ?: MaterialTheme.colorScheme.primary,

            alwaysTint = personalization.downloadColor != null,
            containerOverride = personalization.downloadTileColor,
            live = live,
            modifier = Modifier.weight(1f),
        )
        TrafficTile(
            size = personalization.trafficTileSize,
            icon = Res.drawable.ic_upload,
            badge = MaterialShapes.Cookie4Sided,
            label = stringResource(Res.string.home_upload),
            bytesPerSecond = uploadBps,
            total = totalUpload,
            accent = personalization.uploadColor ?: MaterialTheme.colorScheme.tertiary,
            alwaysTint = personalization.uploadColor != null,
            containerOverride = personalization.uploadTileColor,
            live = live,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TrafficTile(
    icon: DrawableResource,
    badge: RoundedPolygon,
    label: String,
    bytesPerSecond: Long,
    total: Long,
    accent: Color,
    live: Boolean,
    modifier: Modifier = Modifier,
    alwaysTint: Boolean = false,
    containerOverride: Color? = null,
    size: TrafficTileSize = TrafficTileSize.Normal,
) {
    val flowing = live && bytesPerSecond > 0
    val activity = if (live) rateFraction(bytesPerSecond) else 0f
    val (value, unit) = formatRate(bytesPerSecond)

    val container by animateColorAsState(
        targetValue = containerOverride
            ?: if (live) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "tile-container",
    )

    val onContainer = containerOverride?.readableOn() ?: MaterialTheme.colorScheme.onSurfaceVariant
    val valueColor by animateColorAsState(
        targetValue = if (flowing || alwaysTint) accent else onContainer,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "tile-value",
    )

    val amplitude by animateFloatAsState(
        targetValue = activity,
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "tile-amplitude",
    )

    Surface(
        color = container,
        shape = MaterialTheme.shapes.large,
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = size.paddingDp.dp, vertical = (size.paddingDp - 2).dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(badge.toShape())
                        .background(accent.copy(alpha = if (live || alwaysTint) 0.20f else 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = if (live || alwaysTint) accent else onContainer,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = onContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(size.gapDp.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,

                    style = if (size == TrafficTileSize.Large) {
                        MaterialTheme.typography.headlineMediumEmphasized
                    } else {
                        MaterialTheme.typography.headlineSmallEmphasized
                    },
                    color = valueColor,
                    maxLines = 1,
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = onContainer,
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }

            Spacer(Modifier.height(size.gapDp.dp))

            val motion = LocalMotionBudget.current
            LinearWavyProgressIndicator(
                progress = { activity },
                amplitude = { amplitude },
                color = if (flowing) accent else MaterialTheme.colorScheme.outlineVariant,
                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth(),
                waveSpeed = if (motion == MotionBudget.Full || flowing && motion == MotionBudget.Throttled) {
                    WavyProgressIndicatorDefaults.LinearDeterminateWavelength
                } else {
                    0.dp
                },
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = formatBytes(total),
                style = MaterialTheme.typography.labelSmall,
                color = onContainer,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DirectionReading(
    icon: DrawableResource,
    label: String,
    bytesPerSecond: Long,
    accent: Color,
    live: Boolean,
    onContainer: Color,
    big: Boolean = true,
) {
    val (value, unit) = formatRate(bytesPerSecond)
    val flowing = live && bytesPerSecond > 0
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = if (flowing) accent else onContainer,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = onContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = if (big) MaterialTheme.typography.headlineSmallEmphasized
                else MaterialTheme.typography.titleMediumEmphasized,
                color = if (flowing) accent else onContainer,
                maxLines = 1,
            )
            Spacer(Modifier.width(3.dp))
            Text(
                unit,
                style = MaterialTheme.typography.labelSmall,
                color = onContainer,
                maxLines = 1,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
    }
}

@Composable
private fun DuoTrafficCard(
    live: Boolean,
    downloadBps: Long,
    uploadBps: Long,
    totalDownload: Long,
    totalUpload: Long,
    down: Color,
    up: Color,
    personalization: Personalization,
) {
    val pad = personalization.trafficTileSize.paddingDp.dp
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = pad, vertical = pad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                DirectionReading(
                    Res.drawable.ic_download,
                    stringResource(Res.string.home_download),
                    downloadBps,
                    down,
                    live,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    formatBytes(totalDownload),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            Box(
                Modifier
                    .padding(horizontal = 12.dp)
                    .width(1.dp)
                    .height(46.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            )
            Column(Modifier.weight(1f)) {
                DirectionReading(
                    Res.drawable.ic_upload,
                    stringResource(Res.string.home_upload),
                    uploadBps,
                    up,
                    live,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    formatBytes(totalUpload),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun RingTrafficPanel(
    live: Boolean,
    downloadBps: Long,
    uploadBps: Long,
    totalDownload: Long,
    totalUpload: Long,
    down: Color,
    up: Color,
    personalization: Personalization,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(personalization.density.gapDp.dp),
    ) {
        TrafficRing(
            Res.drawable.ic_download, stringResource(Res.string.home_download),
            downloadBps, totalDownload, down, live, Modifier.weight(1f),
        )
        TrafficRing(
            Res.drawable.ic_upload, stringResource(Res.string.home_upload),
            uploadBps, totalUpload, up, live, Modifier.weight(1f),
        )
    }
}

@Composable
private fun TrafficRing(
    icon: DrawableResource,
    label: String,
    bytesPerSecond: Long,
    total: Long,
    accent: Color,
    live: Boolean,
    modifier: Modifier = Modifier,
) {
    val fraction = if (live) rateFraction(bytesPerSecond) else 0f
    val animated by animateFloatAsState(fraction, label = "ring-fraction")
    val (value, unit) = formatRate(bytesPerSecond)
    val onContainer = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        modifier = modifier,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(
                    progress = { animated.coerceIn(0f, 1f) },
                    color = if (live && bytesPerSecond > 0) accent else MaterialTheme.colorScheme.outlineVariant,
                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    strokeWidth = 6.dp,
                    modifier = Modifier.size(84.dp),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        value,
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        color = if (live && bytesPerSecond > 0) accent else onContainer,
                        maxLines = 1,
                    )
                    Text(unit, style = MaterialTheme.typography.labelSmall, color = onContainer, maxLines = 1)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    tint = onContainer,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    formatBytes(total),
                    style = MaterialTheme.typography.labelSmall,
                    color = onContainer,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun MinimalTrafficRow(
    live: Boolean,
    downloadBps: Long,
    uploadBps: Long,
    down: Color,
    up: Color,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MinimalReading(Res.drawable.ic_download, downloadBps, down, live, muted)
        Text(
            "  ·  ",
            style = MaterialTheme.typography.titleMedium,
            color = muted.copy(alpha = 0.5f),
        )
        MinimalReading(Res.drawable.ic_upload, uploadBps, up, live, muted)
    }
}

@Composable
private fun MinimalReading(
    icon: DrawableResource,
    bytesPerSecond: Long,
    accent: Color,
    live: Boolean,
    muted: Color,
) {
    val (value, unit) = formatRate(bytesPerSecond)
    val flowing = live && bytesPerSecond > 0
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = if (flowing) accent else muted,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = if (flowing) accent else muted,
            maxLines = 1,
        )
        Spacer(Modifier.width(2.dp))
        Text(unit, style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 1)
    }
}

@Composable
private fun GraphTrafficPanel(
    live: Boolean,
    downloadBps: Long,
    uploadBps: Long,
    totalDownload: Long,
    totalUpload: Long,
    down: Color,
    up: Color,
    personalization: Personalization,
) {
    val downHistory = remember { mutableStateListOf<Float>() }
    val upHistory = remember { mutableStateListOf<Float>() }
    LaunchedEffect(downloadBps, uploadBps, live) {
        fun push(list: MutableList<Float>, bps: Long) {
            list.add(if (live) rateFraction(bps) else 0f)
            while (list.size > HISTORY_POINTS) list.removeAt(0)
        }
        push(downHistory, downloadBps)
        push(upHistory, uploadBps)
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(personalization.density.gapDp.dp),
    ) {
        GraphTile(
            Res.drawable.ic_download, stringResource(Res.string.home_download),
            downloadBps, totalDownload, down, live, downHistory, personalization, Modifier.weight(1f),
        )
        GraphTile(
            Res.drawable.ic_upload, stringResource(Res.string.home_upload),
            uploadBps, totalUpload, up, live, upHistory, personalization, Modifier.weight(1f),
        )
    }
}

private const val HISTORY_POINTS = 30

@Composable
private fun GraphTile(
    icon: DrawableResource,
    label: String,
    bytesPerSecond: Long,
    total: Long,
    accent: Color,
    live: Boolean,
    history: List<Float>,
    personalization: Personalization,
    modifier: Modifier = Modifier,
) {
    val onContainer = MaterialTheme.colorScheme.onSurfaceVariant
    val pad = personalization.trafficTileSize.paddingDp.dp
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = pad, vertical = pad)) {
            DirectionReading(icon, label, bytesPerSecond, accent, live, onContainer)
            Spacer(Modifier.height(8.dp))
            Canvas(Modifier.fillMaxWidth().height(28.dp)) {
                if (history.size < 2) return@Canvas
                val step = size.width / (HISTORY_POINTS - 1)
                val path = Path()
                val fill = Path()
                history.forEachIndexed { i, v ->
                    val x = i * step
                    val y = size.height - (v.coerceIn(0f, 1f) * size.height)
                    if (i == 0) {
                        path.moveTo(x, y)
                        fill.moveTo(x, size.height)
                        fill.lineTo(x, y)
                    } else {
                        path.lineTo(x, y)
                        fill.lineTo(x, y)
                    }
                }
                fill.lineTo((history.size - 1) * step, size.height)
                fill.close()
                drawPath(fill, accent.copy(alpha = 0.18f))
                drawPath(path, accent, style = Stroke(width = 2.5f))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                formatBytes(total),
                style = MaterialTheme.typography.labelSmall,
                color = onContainer,
                maxLines = 1,
            )
        }
    }
}

private fun rateFraction(bytesPerSecond: Long): Float {
    if (bytesPerSecond <= 0L) return 0f
    val kb = bytesPerSecond / 1024.0
    return (ln(1.0 + kb) / FULL_SCALE_LN).toFloat().coerceIn(0f, 1f)
}

private val FULL_SCALE_LN = ln(1.0 + 10 * 1024.0)

@Composable
private fun ActiveConfigCard(
    name: String?,
    detail: String? = null,
    locked: Boolean,
    onClick: () -> Unit,
    personalization: Personalization = Personalization.Default,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "config-scale",
    )
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = MaterialTheme.shapes.largeIncreased,

        color = personalization.configCardColor ?: MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier.fillMaxWidth().scale(scale),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(MaterialShapes.Cookie9Sided.toShape())

                    .background(
                        personalization.configCardTextColor
                            ?: MaterialTheme.colorScheme.primary,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(if (locked) Res.drawable.ic_encrypted else Res.drawable.ic_public),
                    contentDescription = null,
                    tint = personalization.configCardColor
                        ?: MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = name ?: stringResource(Res.string.home_select_config),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = personalization.configCardTextColor
                        ?: MaterialTheme.colorScheme.onPrimaryContainer,
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

                    color = personalization.tapHintColor
                        ?: (personalization.configCardTextColor
                            ?: MaterialTheme.colorScheme.onPrimaryContainer).copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                painter = painterResource(Res.drawable.ic_chevron_right),
                contentDescription = null,
                tint = personalization.configCardTextColor
                    ?: MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun ConnectButton(
    state: ConnectionState,
    onToggle: () -> Unit,
    customColor: Color? = null,
    customActiveColor: Color? = null,

    compact: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val targetRadius = when {
        pressed -> 44.dp
        state.isTransitioning -> 36.dp
        state.isActive -> 20.dp
        else -> 30.dp
    }
    val radius by animateDpAsState(
        targetValue = targetRadius,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "connect-radius",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "connect-scale",
    )

    val idleContainer = customColor ?: MaterialTheme.colorScheme.primary
    val activeContainer = customActiveColor ?: MaterialTheme.colorScheme.secondaryContainer
    val container by animateColorAsState(
        targetValue = when {
            state == ConnectionState.Error -> MaterialTheme.colorScheme.errorContainer
            state.isActive -> activeContainer
            else -> idleContainer
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "connect-container",
    )

    val idleContent = if (customColor == null) MaterialTheme.colorScheme.onPrimary
    else if (customColor.luminance() > 0.5f) Color(0xFF10131A) else Color.White
    val activeContent = if (customActiveColor == null) MaterialTheme.colorScheme.onSecondaryContainer
    else if (customActiveColor.luminance() > 0.5f) Color(0xFF10131A) else Color.White
    val content = when {
        state == ConnectionState.Error -> MaterialTheme.colorScheme.onErrorContainer
        state.isActive -> activeContent
        else -> idleContent
    }

    Surface(
        onClick = onToggle,

        enabled = state != ConnectionState.Disconnecting,
        shape = RoundedCornerShape(radius),
        color = container,
        contentColor = content,
        interactionSource = interaction,
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 56.dp else 72.dp)
            .scale(scale),
    ) {
        Box(contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = state.isTransitioning,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "connect-label",
            ) { busy ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (busy) {
                        ContainedLoadingIndicator(
                            modifier = Modifier.size(28.dp),
                            containerColor = Color.Transparent,
                        )
                    } else {
                        Icon(
                            painterResource(
                                if (state.isActive) Res.drawable.ic_close else Res.drawable.ic_bolt
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(
                            when {
                                state == ConnectionState.Disconnecting -> Res.string.state_disconnecting

                                busy -> Res.string.action_cancel
                                state.isActive -> Res.string.action_disconnect
                                else -> Res.string.action_connect
                            }
                        ),
                        style = if (compact) {
                            MaterialTheme.typography.titleMediumEmphasized
                        } else {
                            MaterialTheme.typography.titleLargeEmphasized
                        },
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectingBackdrop(state: ConnectionState, sessionId: Int, reduceMotion: Boolean) {
    val intensity by animateFloatAsState(
        targetValue = when (state) {
            ConnectionState.Connecting, ConnectionState.Reconnecting -> 0.72f
            ConnectionState.Connected -> 1f
            ConnectionState.Disconnecting -> 0.35f
            else -> 0f
        },
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "backdrop-intensity",
    )

    val budget = LocalMotionBudget.current
    val pulseRuns = !reduceMotion && when (budget) {
        MotionBudget.Full -> true
        MotionBudget.Throttled -> state == ConnectionState.Connecting || state == ConnectionState.Reconnecting
        MotionBudget.Paused -> false
    }
    val pulse by if (!pulseRuns) {
        remember { mutableStateOf(0f) }
    } else {
        rememberInfiniteTransition(label = "backdrop-life").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Restart),
            label = "pulse",
        )
    }

    val stops = if (state == ConnectionState.Connected) {
        ZedGradients.connectedStops(sessionId)
    } else {
        ZedGradients.connectingStops
    }

    Canvas(Modifier.fillMaxSize()) {
        if (intensity <= 0.002f) return@Canvas
        val w = size.width
        val h = size.height

        drawRect(
            brush = Brush.verticalGradient(colors = stops, startY = 0f, endY = h),
            alpha = 0.9f * intensity,
        )

        val center = Offset(w / 2f, h * 0.42f)
        val ringCount = 4
        for (i in 0 until ringCount) {
            val p = (pulse + i.toFloat() / ringCount) % 1f
            drawCircle(
                color = Color.White.copy(alpha = 0.14f * (1f - p) * intensity),
                radius = h * 0.06f + p * h * 0.42f,
                center = center,
                style = Stroke(width = 2.5f),
            )
        }
    }
}

private class BurstParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Color,
    val radius: Float,
    val square: Boolean,
    var life: Float = 1f,
)

@Composable
private fun ParticleBurst(trigger: Int) {
    val colors = listOf(ZedViolet, ZedHotPink, ZedLime, ZedCyan)
    val particles = remember { mutableStateListOf<BurstParticle>() }
    var tick by remember { mutableLongStateOf(0L) }
    var canvas by remember { mutableStateOf(Size.Zero) }

    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect

        var guard = 0
        while (canvas == Size.Zero && guard < 5) {
            withInfiniteAnimationFrameNanos { }
            guard++
        }
        if (canvas == Size.Zero) return@LaunchedEffect
        val cx = canvas.width / 2f
        val cy = canvas.height * 0.42f
        repeat(56) {
            val angle = Random.nextFloat() * (2f * Math.PI.toFloat())
            val speed = 8f + Random.nextFloat() * 20f
            particles += BurstParticle(
                x = cx,
                y = cy,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed - 8f,
                color = colors[Random.nextInt(colors.size)],
                radius = 7f + Random.nextFloat() * 11f,
                square = Random.nextBoolean(),
            )
        }
        var last = 0L
        while (particles.isNotEmpty()) {
            withInfiniteAnimationFrameNanos { now ->
                val step = if (last == 0L) 1f else ((now - last) / 16_666_666f).coerceIn(0f, 3f)
                last = now
                particles.forEach { p ->
                    p.vy += 0.9f * step
                    p.vx *= 1f - 0.02f * step
                    p.x += p.vx * step
                    p.y += p.vy * step
                    p.life -= 0.014f * step
                }
                particles.removeAll { it.life <= 0f || it.y > canvas.height + 60f }
                tick = now
            }
        }
    }

    Canvas(
        Modifier
            .fillMaxSize()
            .onSizeChanged { canvas = Size(it.width.toFloat(), it.height.toFloat()) },
    ) {
        tick
        particles.forEach { p ->
            val color = p.color.copy(alpha = p.life.coerceIn(0f, 1f))
            if (p.square) {
                drawRect(
                    color = color,
                    topLeft = Offset(p.x - p.radius / 2f, p.y - p.radius / 2f),
                    size = Size(p.radius, p.radius),
                )
            } else {
                drawCircle(color = color, radius = p.radius / 2f, center = Offset(p.x, p.y))
            }
        }
    }
}

private fun ConnectionState.labelRes(): StringResource = when (this) {
    ConnectionState.Idle -> Res.string.state_idle
    ConnectionState.Connecting -> Res.string.state_connecting
    ConnectionState.Connected -> Res.string.state_connected
    ConnectionState.Reconnecting -> Res.string.state_reconnecting
    ConnectionState.Disconnecting -> Res.string.state_disconnecting
    ConnectionState.Error -> Res.string.state_error
}
