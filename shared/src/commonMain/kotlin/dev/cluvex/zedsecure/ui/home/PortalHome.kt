@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.cluvex.zedsecure.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import dev.cluvex.zedsecure.data.net.NetworkInfo
import dev.cluvex.zedsecure.domain.model.ConnectionState
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.*
import dev.cluvex.zedsecure.ui.components.NoteText
import dev.cluvex.zedsecure.ui.connection.ConnectionUiState
import dev.cluvex.zedsecure.ui.format.formatBytes
import dev.cluvex.zedsecure.ui.format.formatElapsed
import dev.cluvex.zedsecure.ui.format.formatRate
import dev.cluvex.zedsecure.ui.onboarding.TourTargets
import dev.cluvex.zedsecure.ui.onboarding.tourTarget
import dev.cluvex.zedsecure.ui.theme.LocalMotionBudget
import dev.cluvex.zedsecure.ui.theme.MotionBudget
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

internal val SearchShapes: List<RoundedPolygon> = listOf(
    MaterialShapes.SoftBurst,
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Pentagon,
    MaterialShapes.Pill,
    MaterialShapes.Sunny,
    MaterialShapes.Cookie4Sided,
    MaterialShapes.Oval,
)

internal const val SEARCH_STEP_SECONDS = 0.72f
private const val TUNNEL_RINGS = 6
private const val TAU = (2.0 * PI).toFloat()

@Composable
internal fun PortalHome(
    ui: ConnectionUiState,
    info: NetworkInfo,
    infoExpanded: Boolean,
    onToggleInfo: () -> Unit,
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
    val clock = rememberPortalClock(animate && (live || searching || ui.state == ConnectionState.Disconnecting))
    val haptics = LocalHapticFeedback.current

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
                        stageMin = if (compact) 180.dp else 220.dp,
                        modifier = Modifier.fillMaxWidth().scrollFade(scroll).verticalScroll(scroll),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            PortalTopBar(ui.state, animate, onSecretUnlocked, onShowLogs)
                            val fault = when {
                                ui.state == ConnectionState.Error ->
                                    ui.error?.takeIf { it.isNotBlank() }
                                        ?.let { dev.cluvex.zedsecure.core.ConnectionFault.classify(it) }
                                live -> info.fault
                                else -> null
                            }
                            AnimatedVisibility(visible = fault != null) {
                                fault?.let {
                                    Box(Modifier.padding(horizontal = 20.dp)) {
                                        FaultBanner(fault = it, connected = live, onShowLogs = onShowLogs)
                                    }
                                }
                            }
                        }

                        Box(Modifier.fillMaxWidth().layoutId(STAGE_ID)) {
                            PortalStage(
                                state = ui.state,
                                elapsed = ui.elapsedSeconds,
                                downloadBps = ui.downloadBps,
                                uploadBps = ui.uploadBps,
                                clock = clock,
                                animate = animate,
                                onTap = {
                                    if (ui.state != ConnectionState.Disconnecting) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onToggleConnection()
                                    }
                                },
                                onLongPress = onBurst,
                            )
                        }

                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            PortalCaption(ui.state, compact, onStatusTap)
                            AnimatedVisibility(visible = live && showConnectionInfo) {
                                Column {
                                    Spacer(Modifier.height(14.dp))
                                    ConnectionInfoPill(
                                        info = info,
                                        expanded = infoExpanded,
                                        onToggle = onToggleInfo,
                                        onRefresh = onRefreshInfo,
                                    )
                                }
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
                                Spacer(Modifier.height(14.dp))
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
                            Spacer(Modifier.height(if (compact) 10.dp else 18.dp))
                        }
                    }
                }
                FlowMeter(
                    live = live,
                    downloadBps = ui.downloadBps,
                    uploadBps = ui.uploadBps,
                    totalDownload = ui.totalDownload,
                    totalUpload = ui.totalUpload,
                    modifier = Modifier.padding(horizontal = 20.dp).tourTarget(TourTargets.TRAFFIC),
                )
                Spacer(Modifier.height(8.dp))
                PortalTicket(
                    name = activeConfigName,
                    detail = activeConfigDetail,
                    locked = activeLocked,
                    onClick = onBrowseConfigs,
                    modifier = Modifier.padding(horizontal = 20.dp).tourTarget(TourTargets.CONFIG_CARD),
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
internal fun rememberPortalClock(running: Boolean): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val base = time.floatValue
        var start = -1L
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                if (start < 0L) start = now
                time.floatValue = base + (now - start) / 1_000_000_000f
            }
        }
    }
    return time
}

@Composable
internal fun PortalTopBar(
    state: ConnectionState,
    animate: Boolean,
    onSecretUnlocked: () -> Unit,
    onShowLogs: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var taps by remember { mutableIntStateOf(0) }
    var lastTap by remember { mutableLongStateOf(0L) }
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusPill(state, animate)
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(Res.string.app_name),
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = cs.onSurface,
            maxLines = 1,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    val now = System.currentTimeMillis()
                    taps = if (now - lastTap < PORTAL_TAP_WINDOW_MS) taps + 1 else 1
                    lastTap = now
                    if (taps >= PORTAL_TAPS_TO_UNLOCK) {
                        taps = 0
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSecretUnlocked()
                    } else if (taps > 1) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
        IconButton(onClick = onShowLogs) {
            Icon(
                painterResource(Res.drawable.ic_description),
                contentDescription = stringResource(Res.string.logs_title),
                tint = cs.onSurfaceVariant,
            )
        }
    }
}

private const val PORTAL_TAPS_TO_UNLOCK = 5
private const val PORTAL_TAP_WINDOW_MS = 1_200L

@Composable
private fun StatusPill(state: ConnectionState, animate: Boolean) {
    val cs = MaterialTheme.colorScheme
    val label = stringResource(
        when (state) {
            ConnectionState.Connected -> Res.string.state_connected
            ConnectionState.Connecting, ConnectionState.Reconnecting -> Res.string.state_connecting
            ConnectionState.Error -> Res.string.state_error
            ConnectionState.Disconnecting -> Res.string.state_disconnecting
            ConnectionState.Idle -> Res.string.state_idle
        },
    )
    val dot by animateColorAsState(
        when (state) {
            ConnectionState.Connected -> cs.primary
            ConnectionState.Connecting, ConnectionState.Reconnecting -> cs.tertiary
            ConnectionState.Error -> cs.error
            else -> cs.outline
        },
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "status-dot",
    )
    val searching = state == ConnectionState.Connecting || state == ConnectionState.Reconnecting
    val pulse = if (searching && animate) {
        rememberInfiniteTransition(label = "status-pulse").animateFloat(
            initialValue = 1f,
            targetValue = 1.7f,
            animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
            label = "status-pulse",
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            Modifier.padding(start = 12.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .graphicsLayer {
                        scaleX = pulse.value
                        scaleY = pulse.value
                    }
                    .clip(CircleShape)
                    .background(dot),
            )
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = cs.onSurface, maxLines = 1)
        }
    }
}

@Composable
private fun PortalStage(
    state: ConnectionState,
    elapsed: Int,
    downloadBps: Long,
    uploadBps: Long,
    clock: State<Float>,
    animate: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val door = (minOf(maxWidth, maxHeight) * 0.56f).coerceIn(124.dp, 272.dp)
        WallOfLight(
            state = state,
            doorRadius = door / 2,
            downloadBps = downloadBps,
            uploadBps = uploadBps,
            clock = clock,
            animate = animate,
            modifier = Modifier.matchParentSize(),
        )
        PortalDoor(state, elapsed, door, clock, animate, onTap, onLongPress)
    }
}

private class WallSky {
    var width = -1f
    var height = -1f
    var spacing = -1f
    var baseX = FloatArray(0)
    var baseY = FloatArray(0)
    val layers = Array(4) { FloatArray(0) }
    val fill = IntArray(4)
    val paint = Paint().apply {
        strokeCap = StrokeCap.Round
        isAntiAlias = true
    }
    val angle = FloatArray(MAX_PARTICLES)
    val reach = FloatArray(MAX_PARTICLES)
    val speed = FloatArray(MAX_PARTICLES)
    val inbound = BooleanArray(MAX_PARTICLES)
    val alive = BooleanArray(MAX_PARTICLES)
    var lastTime = -1f
    var downDebt = 0f
    var upDebt = 0f
    var moonA = 0f
    var moonB = 0f
    val random = Random(19)

    fun ensure(w: Float, h: Float, s: Float) {
        if (w == width && h == height && s == spacing) return
        width = w
        height = h
        spacing = s
        val cols = floor(w / s).toInt() + 3
        val rows = floor(h / s).toInt() + 3
        val n = cols * rows
        baseX = FloatArray(n)
        baseY = FloatArray(n)
        val ox = (w - (cols - 1) * s) / 2f
        val oy = (h - (rows - 1) * s) / 2f
        var i = 0
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                baseX[i] = ox + c * s + if (r % 2 == 1) s / 2f else 0f
                baseY[i] = oy + r * s
                i++
            }
        }
        for (k in layers.indices) layers[k] = FloatArray(n * 2)
    }

    fun spawn(inward: Boolean) {
        val slot = alive.indexOfFirst { !it }
        if (slot < 0) return
        alive[slot] = true
        inbound[slot] = inward
        angle[slot] = random.nextFloat() * TAU
        reach[slot] = if (inward) 2.9f + random.nextFloat() * 0.4f else 1.02f
        speed[slot] = (0.75f + random.nextFloat() * 0.8f) * if (inward) -1f else 1f
    }

    fun step(time: Float, down: Float, up: Float, flowing: Boolean) {
        val dt = if (lastTime < 0f) 0f else (time - lastTime).coerceIn(0f, 0.1f)
        lastTime = time
        if (dt == 0f) return
        moonA += dt * (0.35f + down * 3.4f)
        moonB -= dt * (0.28f + up * 2.8f)
        if (flowing) {
            downDebt += dt * down * 22f
            upDebt += dt * up * 16f
            while (downDebt >= 1f) {
                spawn(true)
                downDebt -= 1f
            }
            while (upDebt >= 1f) {
                spawn(false)
                upDebt -= 1f
            }
        }
        for (i in 0 until MAX_PARTICLES) {
            if (!alive[i]) continue
            reach[i] += speed[i] * dt
            if (reach[i] < 1.0f || reach[i] > 3.4f) alive[i] = false
        }
    }
}

private const val MAX_PARTICLES = 72

@Composable
private fun WallOfLight(
    state: ConnectionState,
    doorRadius: Dp,
    downloadBps: Long,
    uploadBps: Long,
    clock: State<Float>,
    animate: Boolean,
    modifier: Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val opening by animateFloatAsState(
        when (state) {
            ConnectionState.Connected -> 1f
            ConnectionState.Connecting, ConnectionState.Reconnecting -> 0.34f
            ConnectionState.Disconnecting -> 0.12f
            else -> 0f
        },
        spring(dampingRatio = 0.7f, stiffness = 80f),
        label = "wall-opening",
    )
    val glow by animateFloatAsState(
        when (state) {
            ConnectionState.Connected -> 1f
            ConnectionState.Connecting, ConnectionState.Reconnecting -> 0.7f
            ConnectionState.Error -> 0.75f
            ConnectionState.Disconnecting -> 0.3f
            ConnectionState.Idle -> 0f
        },
        tween(900),
        label = "wall-glow",
    )
    val searching = state == ConnectionState.Connecting || state == ConnectionState.Reconnecting
    val wave by animateFloatAsState(if (searching) 1f else 0f, tween(700), label = "wall-wave")
    val error = state == ConnectionState.Error
    val glowInner by animateColorAsState(if (error) cs.error else cs.primary, tween(700), label = "glow-inner")
    val glowOuter by animateColorAsState(if (error) cs.errorContainer else cs.tertiary, tween(700), label = "glow-outer")
    val dotColor = cs.onSurface
    val accent = cs.primary
    val downColor = cs.tertiary
    val upColor = cs.primary
    val down = rateFraction(downloadBps)
    val up = rateFraction(uploadBps)
    val flowing = state == ConnectionState.Connected && animate
    val sky = remember { WallSky() }
    val density = LocalDensity.current
    val spacing = with(density) { 19.dp.toPx() }
    val dotSize = with(density) { 2.4.dp.toPx() }
    val ringStroke = with(density) { 1.2.dp.toPx() }
    val dash = with(density) { floatArrayOf(2.dp.toPx(), 7.dp.toPx()) }
    val moonSize = with(density) { 4.dp.toPx() }
    val particleSize = with(density) { 2.2.dp.toPx() }

    Canvas(modifier) {
        val t = clock.value
        sky.ensure(size.width, size.height, spacing)
        sky.step(t, down, up, flowing)
        val c = center
        val r = doorRadius.toPx()
        val reachMax = sqrt(c.x * c.x + c.y * c.y)

        if (glow > 0.01f) {
            val breath = when {
                searching -> 0.82f + 0.18f * sin(t * 3.1f)
                state == ConnectionState.Connected -> 0.9f + 0.1f * sin(t * 1.3f)
                else -> 1f
            }
            val g = glow * breath
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(glowInner.copy(alpha = 0.40f * g), glowOuter.copy(alpha = 0.14f * g), Color.Transparent),
                    center = c,
                    radius = r * 2.7f,
                ),
                radius = r * 2.7f,
                center = c,
            )
        }

        for (k in 0 until 4) sky.fill[k] = 0
        val n = sky.baseX.size
        val gain = 0.55f + 0.45f * opening
        for (i in 0 until n) {
            val dx = sky.baseX[i] - c.x
            val dy = sky.baseY[i] - c.y
            val d = sqrt(dx * dx + dy * dy)
            val q = d / r
            val nd = d + opening * r * 0.78f / (1f + q * q * q)
            if (nd < r * 1.04f) continue
            val swirl = opening * 0.42f * (r / (nd + r * 0.6f)) * sin(t * 0.38f + q * 0.55f)
            val a = atan2(dy, dx) + swirl
            val fade = (1f - nd / reachMax).coerceIn(0f, 1f)
            val ripple = wave * (0.5f + 0.5f * sin(nd / r * 3.3f - t * 5.2f))
            val alpha = fade * gain * (0.8f + 0.9f * ripple)
            val layer = when {
                alpha > 0.62f -> 0
                alpha > 0.40f -> 1
                alpha > 0.20f -> 2
                alpha > 0.05f -> 3
                else -> continue
            }
            val arr = sky.layers[layer]
            val at = sky.fill[layer] * 2
            arr[at] = c.x + cos(a) * nd
            arr[at + 1] = c.y + sin(a) * nd
            sky.fill[layer]++
        }
        val tint = lerp(dotColor, accent, (opening * 0.85f + wave * 0.35f).coerceAtMost(1f))
        val layerAlpha = floatArrayOf(0.52f, 0.36f, 0.22f, 0.10f)
        drawIntoCanvas { canvas ->
            sky.paint.strokeWidth = dotSize
            for (k in 0 until 4) {
                val count = sky.fill[k]
                if (count == 0) continue
                val arr = sky.layers[k]
                for (j in count * 2 until arr.size) arr[j] = -10_000f
                sky.paint.color = tint.copy(alpha = layerAlpha[k])
                canvas.drawRawPoints(PointMode.Points, arr, sky.paint)
            }
        }

        val ringAlpha = 0.10f + 0.16f * opening + 0.08f * wave
        val ringColor = lerp(dotColor, accent, opening)
        val solid = opening > 0.6f
        val effect = if (solid) null else PathEffect.dashPathEffect(dash, if (searching) -t * 60f else 0f)
        drawCircle(ringColor.copy(alpha = ringAlpha), r * 1.30f, c, style = Stroke(ringStroke, pathEffect = effect))
        drawCircle(ringColor.copy(alpha = ringAlpha * 0.7f), r * 1.62f, c, style = Stroke(ringStroke, pathEffect = effect))

        if (opening > 0.5f) {
            val moonAlpha = ((opening - 0.5f) * 2f).coerceIn(0f, 1f)
            for (trail in 0 until 5) {
                val lag = trail * 0.09f
                val fadeOut = moonAlpha * (1f - trail / 5f)
                drawCircle(
                    downColor.copy(alpha = fadeOut),
                    moonSize * (1f - trail * 0.14f),
                    Offset(c.x + cos(sky.moonA - lag) * r * 1.30f, c.y + sin(sky.moonA - lag) * r * 1.30f),
                )
                drawCircle(
                    upColor.copy(alpha = fadeOut),
                    moonSize * 0.8f * (1f - trail * 0.14f),
                    Offset(c.x + cos(sky.moonB + lag) * r * 1.62f, c.y + sin(sky.moonB + lag) * r * 1.62f),
                )
            }
        }

        for (i in 0 until MAX_PARTICLES) {
            if (!sky.alive[i]) continue
            val dist = sky.reach[i]
            val life = if (sky.inbound[i]) ((dist - 1f) / 2.2f).coerceIn(0f, 1f).let { 1f - (1f - it) * (1f - it) }
            else (1f - (dist - 1f) / 2.4f).coerceIn(0f, 1f)
            val fadeNear = ((dist - 1f) / 0.25f).coerceIn(0f, 1f)
            drawCircle(
                (if (sky.inbound[i]) downColor else upColor).copy(alpha = 0.85f * life * fadeNear),
                particleSize,
                Offset(c.x + cos(sky.angle[i]) * dist * r, c.y + sin(sky.angle[i]) * dist * r),
            )
        }
    }
}

@Composable
private fun PortalDoor(
    state: ConnectionState,
    elapsed: Int,
    doorSize: Dp,
    clock: State<Float>,
    animate: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val searching = state == ConnectionState.Connecting || state == ConnectionState.Reconnecting
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(searching, animate) {
        if (!searching || !animate) return@LaunchedEffect
        snapshotFlow { (clock.value / SEARCH_STEP_SECONDS).toInt() }.collect { step = it }
    }
    val target: RoundedPolygon = when (state) {
        ConnectionState.Connected -> MaterialShapes.Circle
        ConnectionState.Error -> MaterialShapes.Burst
        ConnectionState.Connecting, ConnectionState.Reconnecting -> SearchShapes[step.mod(SearchShapes.size)]
        ConnectionState.Idle, ConnectionState.Disconnecting -> MaterialShapes.Cookie12Sided
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
        animate(0f, 1f, animationSpec = spring(dampingRatio = 0.62f, stiffness = 240f)) { value, _ -> progress = value }
    }
    val morph = remember(from, to) { Morph(from, to) }

    val shake = remember { Animatable(0f) }
    LaunchedEffect(state == ConnectionState.Error) {
        if (state != ConnectionState.Error) return@LaunchedEffect
        for (x in listOf(14f, -11f, 8f, -5f, 2f, 0f)) shake.animateTo(x, tween(55))
    }

    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val squash by animateFloatAsState(
        if (pressed) 0.92f else 1f,
        MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "door-squash",
    )
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val c1 by animateColorAsState(
        when {
            state == ConnectionState.Connected -> cs.primaryContainer
            searching -> cs.primary
            state == ConnectionState.Error -> cs.errorContainer
            else -> cs.surfaceContainerHighest
        },
        effects,
        label = "door-c1",
    )
    val c2 by animateColorAsState(
        when {
            state == ConnectionState.Connected -> cs.primary
            searching -> cs.tertiary
            state == ConnectionState.Error -> cs.errorContainer
            else -> cs.surfaceContainerHighest
        },
        effects,
        label = "door-c2",
    )
    val c3 by animateColorAsState(
        when {
            state == ConnectionState.Connected -> cs.tertiary
            searching -> cs.primary
            state == ConnectionState.Error -> cs.errorContainer
            else -> cs.surfaceContainerHighest
        },
        effects,
        label = "door-c3",
    )
    val rim by animateColorAsState(
        when (state) {
            ConnectionState.Idle, ConnectionState.Disconnecting -> cs.outlineVariant
            ConnectionState.Error -> cs.error
            else -> Color.Transparent
        },
        effects,
        label = "door-rim",
    )
    val ink by animateColorAsState(
        when {
            state == ConnectionState.Error -> cs.onErrorContainer
            state.isActive -> cs.onPrimary
            else -> cs.onSurface
        },
        effects,
        label = "door-ink",
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
    val rimWidth = with(density) { 1.6.dp.toPx() }
    val sealed by animateFloatAsState(
        if (state == ConnectionState.Idle || state == ConnectionState.Disconnecting) 1f else 0f,
        tween(500),
        label = "door-sealed",
    )
    val tunnel by animateFloatAsState(
        if (state == ConnectionState.Connected) 1f else 0f,
        tween(900),
        label = "door-tunnel",
    )

    Box(
        Modifier
            .size(doorSize)
            .tourTarget(TourTargets.CORE)
            .graphicsLayer {
                translationX = shake.value * density.density
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
                state == ConnectionState.Connected && animate -> clock.value * 8f
                else -> 0f
            }
            val brush = if (searching) {
                Brush.sweepGradient(listOf(c1, c2, c3, c1), center)
            } else {
                Brush.radialGradient(listOf(c1, c2, c3), center, size.minDimension / 2f)
            }
            rotate(turn) {
                drawPath(work, brush)
                if (rim.alpha > 0.01f) drawPath(work, rim, style = Stroke(rimWidth))
                if (sealed > 0.01f) {
                    scale(0.78f, center) {
                        drawPath(work, rim.copy(alpha = rim.alpha * 0.55f * sealed), style = Stroke(rimWidth / 0.78f))
                    }
                }
            }
            if (tunnel > 0.01f) {
                val radius = size.minDimension / 2f
                val drift = if (animate) clock.value * 0.22f else 0f
                for (k in 0 until TUNNEL_RINGS) {
                    val phase = (drift + k.toFloat() / TUNNEL_RINGS) % 1f
                    val ringRadius = radius * (1f - phase) * 0.92f
                    val life = 4f * phase * (1f - phase)
                    drawCircle(
                        ink.copy(alpha = 0.30f * life * tunnel),
                        ringRadius,
                        center,
                        style = Stroke(rimWidth * (0.6f + (1f - phase) * 0.9f)),
                    )
                }
            }
        }
        AnimatedContent(
            targetState = state == ConnectionState.Connected,
            transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(200)) },
            label = "door-center",
        ) { open ->
            if (open) {
                Text(
                    text = tabularDigits(formatElapsed(elapsed)),
                    style = MaterialTheme.typography.headlineMediumEmphasized.copy(
                        fontSize = with(density) { (doorSize * 0.15f).toSp() },
                    ),
                    color = ink,
                    maxLines = 1,
                    softWrap = false,
                )
            } else {
                Icon(
                    painterResource(Res.drawable.ic_zed_glyph),
                    contentDescription = null,
                    tint = ink,
                    modifier = Modifier.size(doorSize * 0.36f),
                )
            }
        }
    }
}

@Composable
private fun PortalCaption(state: ConnectionState, compact: Boolean, onStatusTap: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val rise = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    AnimatedContent(
        targetState = state,
        transitionSpec = {
            (fadeIn(tween(320)) + slideInVertically(rise) { it / 3 }) togetherWith
                (fadeOut(tween(180)) + slideOutVertically(tween(220)) { -it / 4 })
        },
        label = "portal-caption",
    ) { s ->
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            val headline = if (compact) MaterialTheme.typography.headlineMediumEmphasized else MaterialTheme.typography.displaySmallEmphasized
            Text(
                text = stringResource(s.labelRes()),
                style = if (s == ConnectionState.Connected) {
                    headline.copy(brush = Brush.linearGradient(listOf(cs.primary, cs.tertiary)))
                } else {
                    headline
                },
                color = if (s == ConnectionState.Error) cs.error else cs.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onStatusTap,
                ),
            )
            if (s == ConnectionState.Idle) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(Res.string.portal_hint),
                    style = MaterialTheme.typography.labelLarge,
                    color = cs.primary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun FlowMeter(
    live: Boolean,
    downloadBps: Long,
    uploadBps: Long,
    totalDownload: Long,
    totalUpload: Long,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        FlowHalf(
            icon = Res.drawable.ic_download,
            label = stringResource(Res.string.home_download),
            bps = downloadBps,
            total = totalDownload,
            live = live,
            badge = MaterialTheme.colorScheme.tertiaryContainer,
            onBadge = MaterialTheme.colorScheme.onTertiaryContainer,
            shape = RoundedCornerShape(topStart = 26.dp, bottomStart = 26.dp, topEnd = 6.dp, bottomEnd = 6.dp),
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        FlowHalf(
            icon = Res.drawable.ic_upload,
            label = stringResource(Res.string.home_upload),
            bps = uploadBps,
            total = totalUpload,
            live = live,
            badge = MaterialTheme.colorScheme.primaryContainer,
            onBadge = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 26.dp, bottomEnd = 26.dp),
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

@Composable
private fun FlowHalf(
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
    val badgeTurn by animateFloatAsState(if (busy) 45f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "flow-turn")
    Surface(shape = shape, color = cs.surfaceContainerHigh, modifier = modifier) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer { rotationZ = badgeTurn }
                        .clip(MaterialShapes.Cookie4Sided.toShape())
                        .background(badge),
                )
                Icon(painterResource(icon), contentDescription = null, tint = onBadge, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        tabularDigits(value),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        color = if (busy) cs.onSurface else cs.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        unit,
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
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
private fun PortalTicket(
    name: String?,
    detail: String?,
    locked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val corner by animateDpAsState(if (pressed) 18.dp else 30.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "ticket-corner")
    val turn by animateFloatAsState(if (pressed) 60f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "ticket-turn")
    Surface(
        onClick = onClick,
        interactionSource = source,
        shape = RoundedCornerShape(corner),
        color = cs.primaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer { rotationZ = turn }
                        .clip(MaterialShapes.Cookie9Sided.toShape())
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
                    color = cs.onPrimaryContainer,
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
                    color = cs.onPrimaryContainer.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(cs.onPrimaryContainer.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(Res.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = cs.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
