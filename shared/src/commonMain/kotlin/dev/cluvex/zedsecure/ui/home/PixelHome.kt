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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.toPath
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
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
import dev.cluvex.zedsecure.ui.theme.LocalMotionBudget
import dev.cluvex.zedsecure.ui.theme.MotionBudget
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun PixelHome(
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

    var celebrate by remember { mutableIntStateOf(0) }
    var previous by remember { mutableStateOf(ui.state) }
    LaunchedEffect(ui.state) {
        if (ui.state == ConnectionState.Connected && (previous == ConnectionState.Connecting || previous == ConnectionState.Reconnecting)) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            if (!reduceMotion) celebrate++
        }
        previous = ui.state
    }

    Box(modifier.fillMaxSize().background(cs.surface)) {
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
                                PixelErrorCard(
                                    error = ui.error,
                                    onRetry = onToggleConnection,
                                    onOtherServer = onBrowseConfigs,
                                    onShowLogs = onShowLogs,
                                )
                            } else {
                                PixelHeroCard(
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
                                    color = cs.surfaceContainerHigh,
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
                                    color = cs.surfaceContainerHigh,
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
                    PixelServerRow(
                        name = activeConfigName,
                        detail = activeConfigDetail,
                        locked = activeLocked,
                        onClick = onBrowseConfigs,
                        modifier = Modifier.tourTarget(TourTargets.CONFIG_CARD),
                    )
                    PixelStats(
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
    }
}

@Composable
private fun PixelHeroCard(
    ui: ConnectionUiState,
    info: NetworkInfo,
    showConnectionInfo: Boolean,
    animate: Boolean,
    clock: State<Float>,
    celebrate: Int,
    compact: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
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
    val container by animateColorAsState(if (live) cs.primaryContainer else cs.surfaceContainer, effects, label = "pixel-card")
    val ink by animateColorAsState(if (live) cs.onPrimaryContainer else cs.onSurface, effects, label = "pixel-ink")
    val sub by animateColorAsState(if (live) cs.onPrimaryContainer.copy(alpha = 0.78f) else cs.onSurfaceVariant, effects, label = "pixel-sub")
    val rise = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    Surface(shape = RoundedCornerShape(44.dp), color = container, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val open = live && showConnectionInfo && details
            val full = minOf(maxWidth * 0.52f, maxHeight * 0.46f).coerceIn(116.dp, 208.dp)
            val small = minOf(maxWidth * 0.26f, maxHeight * 0.17f).coerceIn(64.dp, 100.dp)
            val door by animateDpAsState(if (open) small else full, DoorSpring, label = "pixel-door-size")
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                PixelDoor(state, ui.elapsedSeconds, door, clock, animate, celebrate, onTap, onLongPress)
                Spacer(Modifier.height(if (compact || open) 10.dp else 16.dp))
                AnimatedContent(
                    targetState = state,
                    transitionSpec = {
                        (fadeIn(tween(280)) + slideInVertically(rise) { it / 3 }) togetherWith fadeOut(tween(160))
                    },
                    label = "pixel-state",
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
                            label = "pixel-facts",
                        ) { expanded ->
                            if (expanded) {
                                Column {
                                    Spacer(Modifier.height(8.dp))
                                    PixelDetailsPanel(
                                        info = info,
                                        onClose = onCloseInfo,
                                        onRefreshPing = onRefreshPing,
                                        onRefreshPlace = onRefreshPlace,
                                        onRefreshAll = onRefreshAll,
                                    )
                                }
                            } else {
                                PixelFacts(info, onOpenInfo)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PixelDoor(
    state: ConnectionState,
    elapsed: Int,
    doorSize: Dp,
    clock: State<Float>,
    animate: Boolean,
    celebrate: Int,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val searching = state == ConnectionState.Connecting || state == ConnectionState.Reconnecting
    val live = state == ConnectionState.Connected
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(searching, animate) {
        if (!searching || !animate) return@LaunchedEffect
        snapshotFlow { (clock.value / SEARCH_STEP_SECONDS).toInt() }.collect { step = it }
    }
    val target: RoundedPolygon = when {
        live -> MaterialShapes.SoftBurst
        searching -> SearchShapes[step.mod(SearchShapes.size)]
        else -> MaterialShapes.Cookie12Sided
    }
    var from by remember { mutableStateOf(MaterialShapes.Cookie12Sided) }
    var to by remember { mutableStateOf(MaterialShapes.Cookie12Sided) }
    var progress by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(target) {
        if (target === to) return@LaunchedEffect
        Snapshot.withMutableSnapshot {
            from = if (progress >= 0.5f) to else from
            to = target
            progress = 0f
        }
        animate(0f, 1f, animationSpec = spring(dampingRatio = 0.6f, stiffness = 260f)) { value, _ -> progress = value }
    }
    val morph = remember(from, to) { Morph(from, to) }

    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val squash by animateFloatAsState(if (pressed) 0.9f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "pixel-door-squash")
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val start by animateColorAsState(if (state == ConnectionState.Disconnecting) cs.primary.copy(alpha = 0.55f) else cs.primary, effects, label = "pixel-door-a")
    val end by animateColorAsState(
        when {
            live || searching -> cs.tertiary
            state == ConnectionState.Disconnecting -> cs.primary.copy(alpha = 0.55f)
            else -> cs.primary
        },
        effects,
        label = "pixel-door-b",
    )
    val actionLabel = stringResource(
        when {
            state == ConnectionState.Disconnecting -> Res.string.state_disconnecting
            state.isTransitioning -> Res.string.action_cancel
            state.isActive -> Res.string.action_disconnect
            else -> Res.string.action_connect
        },
    )
    val work = remember { Path() }
    val density = LocalDensity.current

    Box(
        Modifier
            .size(doorSize)
            .tourTarget(TourTargets.CORE)
            .graphicsLayer {
                scaleX = squash
                scaleY = squash
            }
            .semantics { contentDescription = actionLabel }
            .combinedClickable(
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onLongClick = onLongPress,
                onClick = onTap,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            work.rewind()
            morph.toPath(progress.coerceIn(0f, 1f), work)
            work.transform(Matrix().apply { scale(size.width, size.height) })
            val bounds = work.getBounds()
            work.translate(center - bounds.center)
            val turn = when {
                searching && animate -> clock.value * 150f
                live && animate -> clock.value * 9f
                else -> 0f
            }
            val brush = if (searching) {
                Brush.sweepGradient(listOf(start, end, start), center)
            } else {
                Brush.linearGradient(listOf(start, end), Offset.Zero, Offset(size.width, size.height))
            }
            rotate(turn) { drawPath(work, brush) }
        }
        AnimatedContent(
            targetState = live,
            transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(200)) },
            label = "pixel-door-center",
        ) { open ->
            if (open) {
                Text(
                    text = tabularDigits(formatElapsed(elapsed)),
                    style = MaterialTheme.typography.headlineSmallEmphasized.copy(
                        fontSize = with(density) { (doorSize * 0.14f).toSp() },
                    ),
                    color = cs.onPrimary,
                    maxLines = 1,
                    softWrap = false,
                )
            } else {
                Icon(
                    painterResource(Res.drawable.ic_fill_power),
                    contentDescription = null,
                    tint = cs.onPrimary,
                    modifier = Modifier.size(doorSize * 0.32f),
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
private fun PixelFacts(info: NetworkInfo, onOpen: (InfoFocus) -> Unit) {
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
private fun PixelErrorCard(
    error: String?,
    onRetry: () -> Unit,
    onOtherServer: () -> Unit,
    onShowLogs: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val fault = error?.takeIf { it.isNotBlank() }?.let { ConnectionFault.classify(it) }
    val known = fault != null && fault.cause != ConnectionFault.Cause.Unknown
    val shake = remember { Animatable(0f) }
    LaunchedEffect(error) {
        for (x in listOf(12f, -10f, 7f, -4f, 2f, 0f)) shake.animateTo(x, tween(55))
    }
    val density = LocalDensity.current
    Surface(
        shape = RoundedCornerShape(44.dp),
        color = cs.errorContainer,
        contentColor = cs.onErrorContainer,
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
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
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
private fun PixelServerRow(
    name: String?,
    detail: String?,
    locked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val top by animateDpAsState(if (pressed) 18.dp else 28.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "pixel-row-top")
    val bottom by animateDpAsState(if (pressed) 14.dp else 6.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "pixel-row-bottom")
    val turn by animateFloatAsState(if (pressed) 45f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "pixel-row-turn")
    Surface(
        onClick = onClick,
        interactionSource = source,
        shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom),
        color = cs.surfaceContainerHigh,
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
                        .clip(MaterialShapes.Cookie4Sided.toShape())
                        .background(cs.primary),
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
private fun PixelStats(
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
        PixelStat(
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
        PixelStat(
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
private fun PixelStat(
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
    val turn by animateFloatAsState(if (busy) 45f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "pixel-stat-turn")
    Surface(shape = shape, color = cs.surfaceContainerHigh, modifier = modifier) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer { rotationZ = turn }
                        .clip(MaterialShapes.Cookie4Sided.toShape())
                        .background(badge),
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
