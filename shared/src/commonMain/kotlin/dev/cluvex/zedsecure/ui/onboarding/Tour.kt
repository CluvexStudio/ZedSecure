package dev.cluvex.zedsecure.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max
import kotlin.math.roundToInt

object TourTargets {
    private val bounds = mutableStateMapOf<String, Rect>()

    private val owners = mutableMapOf<String, Any>()

    private val scrollers = mutableStateMapOf<String, BringIntoViewRequester>()

    internal fun put(key: String, owner: Any, rect: Rect) {
        owners[key] = owner

        if (bounds[key] != rect) bounds[key] = rect
    }

    internal fun forget(key: String, owner: Any) {
        if (owners[key] === owner) {
            owners.remove(key)
            bounds.remove(key)
        }
    }

    operator fun get(key: String): Rect? = bounds[key]

    internal fun putScroller(key: String, requester: BringIntoViewRequester) {
        scrollers[key] = requester
    }

    internal fun removeScroller(key: String, requester: BringIntoViewRequester) {
        if (scrollers[key] === requester) scrollers.remove(key)
    }

    internal fun scroller(key: String): BringIntoViewRequester? = scrollers[key]

    fun nav(destination: String): String = "nav-$destination"

    const val CORE = "core"
    const val TRAFFIC = "traffic"
    const val CONFIG_CARD = "config-card"
    const val HOME_TOOLS = "home-tools"
    const val NAV_SERVERS = "nav-Servers"
    const val NAV_SETTINGS = "nav-Settings"
    const val SERVERS_ADD = "servers-add"
    const val SERVERS_SUBS = "servers-subs"
    const val SERVERS_PING = "servers-ping"
    const val SERVERS_MORE = "servers-more"
    const val SERVERS_GROUPS = "servers-groups"
    const val SERVER_CARD = "server-card"
    const val SETTINGS_SUPPORT = "settings-support"
    const val SETTINGS_LOOK = "settings-look"
    const val SETTINGS_ROUTING = "settings-routing"
    const val SETTINGS_TUNNELS = "settings-tunnels"
    const val SETTINGS_CORE = "settings-core"
    const val SETTINGS_TOOLS = "settings-tools"
    const val SETTINGS_ABOUT = "settings-about"
}

fun Modifier.tourTarget(key: String): Modifier = this then TourTargetElement(key)

private data class TourTargetElement(val key: String) : ModifierNodeElement<TourTargetNode>() {
    override fun create() = TourTargetNode(key)

    override fun update(node: TourTargetNode) {
        node.setKey(key)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "tourTarget"
        properties["key"] = key
    }
}

private class TourTargetNode(private var key: String) :
    Modifier.Node(), GlobalPositionAwareModifierNode {
    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        TourTargets.put(key, this, coordinates.boundsInRoot())
    }

    override fun onDetach() {
        TourTargets.forget(key, this)
    }

    fun setKey(next: String) {
        if (next != key) {
            TourTargets.forget(key, this)
            key = next
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.tourTargetInPage(key: String): Modifier {
    val requester = remember(key) { BringIntoViewRequester() }
    DisposableEffect(key, requester) {
        TourTargets.putScroller(key, requester)
        onDispose { TourTargets.removeScroller(key, requester) }
    }
    return this.bringIntoViewRequester(requester).tourTarget(key)
}

data class TourStep(
    val target: String,
    val title: String,
    val body: String,

    val destination: TourDestination,
)

enum class TourDestination { Home, Servers, Settings }

private const val TARGET_WAIT_MS = 1_600L

private const val SETTLE_MS = 450L

@Composable
fun TourOverlay(
    step: TourStep,
    index: Int,
    total: Int,
    nextLabel: String,
    skipLabel: String,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onTargetMissing: () -> Unit,
) {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val accent = MaterialTheme.colorScheme.primary
    val scrim = Color.Black.copy(alpha = 0.74f)

    var origin by remember { mutableStateOf(Offset.Zero) }
    var overlaySize by remember { mutableStateOf(IntSize.Zero) }
    var captionSize by remember { mutableStateOf(IntSize.Zero) }

    val sideMargin = with(density) { 18.dp.toPx() }
    val holePad = with(density) { 10.dp.toPx() }
    val holeRadius = with(density) { 22.dp.toPx() }
    val armLength = with(density) { 46.dp.toPx() }

    val live = TourTargets[step.target]
        ?.takeIf { it.width > 1f && it.height > 1f }
        ?.translate(-origin)

    var anchor by remember { mutableStateOf<Rect?>(null) }
    var anchorFrom by remember { mutableStateOf(Rect.Zero) }
    var anchorScreen by remember { mutableStateOf<TourDestination?>(null) }
    val travel = remember { Animatable(1f) }

    LaunchedEffect(live, step.destination) {
        val next = live ?: return@LaunchedEffect
        val shown = anchor?.let { lerp(anchorFrom, it, travel.value) }
        val jump = shown == null || anchorScreen != step.destination
        anchorFrom = shown ?: next
        anchor = next
        anchorScreen = step.destination
        if (jump) {
            travel.snapTo(1f)
        } else {
            travel.snapTo(0f)
            travel.animateTo(1f, tween(340))
        }
    }

    val missing by rememberUpdatedState(onTargetMissing)
    LaunchedEffect(step.target) {
        suspend fun await(ms: Long) = withTimeoutOrNull(ms) {
            snapshotFlow { TourTargets[step.target] }
                .first { it != null && it.width > 1f && it.height > 1f }
        }

        val scroller = withTimeoutOrNull(SETTLE_MS) {
            snapshotFlow { TourTargets.scroller(step.target) }.filterNotNull().first()
        }
        if (scroller != null) {
            @OptIn(ExperimentalFoundationApi::class)
            runCatching { scroller.bringIntoView() }
        }

        if (await(SETTLE_MS) == null) {
            anchor = null
            if (await(TARGET_WAIT_MS - SETTLE_MS) == null) missing()
        }
    }

    val hole = anchor?.let { lerp(anchorFrom, it, travel.value).inflate(holePad) }

    val safe = WindowInsets.safeDrawing.asPaddingValues()
    val topLimit = with(density) { safe.calculateTopPadding().toPx() } + sideMargin
    val bottomLimit = overlaySize.height -
        with(density) { safe.calculateBottomPadding().toPx() } - sideMargin
    val captionHeight = captionSize.height.toFloat()

    val placement = tourCaptionPlacement(
        hole = hole,
        captionHeight = captionHeight,
        overlayHeight = overlaySize.height.toFloat(),
        topLimit = topLimit,
        bottomLimit = bottomLimit,
        arm = armLength,
    )
    val captionBelow = placement.below
    val captionTop = placement.top

    val captionRect = remember { mutableStateOf(Rect.Zero) }
    val measuredCaption = Rect(
        left = sideMargin,
        top = captionTop,
        right = max(sideMargin, overlaySize.width - sideMargin),
        bottom = captionTop + captionHeight,
    )
    SideEffect { captionRect.value = measuredCaption }
    val advance by rememberUpdatedState(onNext)

    val pulse by rememberInfiniteTransition(label = "tour-pulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "tour-pulse-value",
    )
    val nudge by rememberInfiniteTransition(label = "tour-arrow").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(820), RepeatMode.Reverse),
        label = "tour-arrow-value",
    )

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                origin = it.positionInRoot()
                overlaySize = it.size
            }

            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val start = down.position
                    down.consume()
                    var dragged = false
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { change ->
                            if ((change.position - start).getDistance() > viewConfiguration.touchSlop) {
                                dragged = true
                            }
                            change.consume()
                        }
                        if (event.changes.none { it.pressed }) break
                    }
                    if (!dragged && !captionRect.value.contains(start)) advance()
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (hole == null) {
                drawRect(scrim)
                return@Canvas
            }
            val full = Path().apply { addRect(Rect(Offset.Zero, size)) }
            val cut = Path().apply {
                addRoundRect(RoundRect(hole, CornerRadius(holeRadius, holeRadius)))
            }
            drawPath(Path.combine(PathOperation.Difference, full, cut), scrim)

            drawRoundRect(
                color = accent.copy(alpha = 0.45f + 0.35f * pulse),
                topLeft = hole.topLeft,
                size = hole.size,
                cornerRadius = CornerRadius(holeRadius, holeRadius),
                style = Stroke(width = (2f + 1.5f * pulse) * density.density),
            )

            if (captionSize != IntSize.Zero) {
                val x = hole.center.x.coerceIn(
                    sideMargin + holeRadius,
                    max(sideMargin + holeRadius, size.width - sideMargin - holeRadius),
                )
                val wobble = 4f * density.density * nudge
                val tailY: Float
                val tipY: Float
                if (captionBelow) {
                    tailY = captionTop - 6f
                    tipY = hole.bottom + 6f + wobble
                } else {
                    tailY = captionTop + captionHeight + 6f
                    tipY = hole.top - 6f - wobble
                }
                if (kotlin.math.abs(tipY - tailY) > 8f) {
                    drawTourArrow(x, tailY, tipY, accent, density.density)
                }
            }
        }

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .offset { IntOffset(0, captionTop.roundToInt()) }
                        .fillMaxWidth()
                        .padding(horizontal = with(density) { sideMargin.toDp() })
                        .onSizeChanged { captionSize = it }

                        .graphicsLayer { alpha = if (captionSize == IntSize.Zero) 0f else 1f },
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides direction) {
                        TourCaption(
                            step = step,
                            index = index,
                            total = total,
                            nextLabel = nextLabel,
                            skipLabel = skipLabel,
                            accent = accent,
                            onNext = onNext,
                            onSkip = onSkip,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TourCaption(
    step: TourStep,
    index: Int,
    total: Int,
    nextLabel: String,
    skipLabel: String,
    accent: Color,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    val progress by animateFloatAsState(
        targetValue = if (total <= 0) 0f else (index + 1).toFloat() / total,
        animationSpec = tween(340),
        label = "tour-progress",
    )
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))

                Text(
                    text = "${index + 1}/$total",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = step.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(accent),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onSkip) { Text(skipLabel) }
                Spacer(Modifier.width(6.dp))
                Button(onClick = onNext, shape = RoundedCornerShape(16.dp)) {
                    Text(nextLabel, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

data class TourCaptionPlacement(val top: Float, val below: Boolean)

fun tourCaptionPlacement(
    hole: Rect?,
    captionHeight: Float,
    overlayHeight: Float,
    topLimit: Float,
    bottomLimit: Float,
    arm: Float,
): TourCaptionPlacement {
    val lowest = max(topLimit, bottomLimit - captionHeight)
    if (hole == null) {
        return TourCaptionPlacement(
            top = ((overlayHeight - captionHeight) / 2f).coerceIn(topLimit, lowest),
            below = true,
        )
    }
    val roomBelow = bottomLimit - (hole.bottom + arm)
    val roomAbove = (hole.top - arm) - topLimit
    val below = roomBelow >= captionHeight || roomBelow >= roomAbove
    val top = if (below) hole.bottom + arm else hole.top - arm - captionHeight
    return TourCaptionPlacement(top = top.coerceIn(topLimit, lowest), below = below)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTourArrow(
    x: Float,
    tailY: Float,
    tipY: Float,
    color: Color,
    scale: Float,
) {
    val down = tipY > tailY
    val bow = 9f * scale * if (down) 1f else -1f
    val head = 9f * scale
    val stop = tipY + if (down) -head else head
    val shaft = Path().apply {
        moveTo(x, tailY)
        quadraticTo(x + bow, (tailY + stop) / 2f, x, stop)
    }
    drawPath(
        path = shaft,
        color = color,
        style = Stroke(width = 3.5f * scale, cap = StrokeCap.Round),
    )
    drawPath(
        path = Path().apply {
            moveTo(x, tipY)
            lineTo(x - head * 0.8f, stop)
            lineTo(x + head * 0.8f, stop)
            close()
        },
        color = color,
    )
    drawCircle(color = color, radius = 3f * scale, center = Offset(x, tailY))
}
