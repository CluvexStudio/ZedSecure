package dev.cluvex.zedsecure.ui.glass

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.backdrop.shadow.Shadow
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.action_clear
import dev.cluvex.zedsecure.shared.resources.action_search
import dev.cluvex.zedsecure.ui.navigation.TopDestination
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@Stable
class GlassSearchState {
    var active by mutableStateOf(false)
    var query by mutableStateOf("")

    fun close() {
        active = false
        query = ""
    }
}

val LocalGlassSearch = staticCompositionLocalOf<GlassSearchState?> { null }

val GlassTabBarHeight = 62.dp
val GlassTabBarMargin = 22.dp

fun glassTabIcon(destination: TopDestination): ImageVector = when (destination) {
    TopDestination.Home -> GlassIcons.House
    TopDestination.Servers -> GlassIcons.Server
    TopDestination.Subscriptions -> GlassIcons.Cloud
    TopDestination.Vault -> GlassIcons.Lock
    TopDestination.SpeedTest -> GlassIcons.Gauge
    TopDestination.Map -> GlassIcons.MapFill
    TopDestination.Logs -> GlassIcons.Logs
    TopDestination.Routing -> GlassIcons.Routing
    TopDestination.Apps -> GlassIcons.Apps
    TopDestination.Settings -> GlassIcons.Gear
}

private enum class BarMode { Full, Mini, Search }

@Composable
fun GlassTabBar(
    tabs: List<TopDestination>,
    current: TopDestination,
    onSelect: (TopDestination) -> Unit,
    backdrop: Backdrop?,
    minimized: Boolean,
    onExpand: () -> Unit,
    search: GlassSearchState,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
    tilt: State<Float>? = null,
) {
    val mode = when {
        search.active -> BarMode.Search
        minimized -> BarMode.Mini
        else -> BarMode.Full
    }
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(start = 16.dp, end = 16.dp, bottom = GlassTabBarMargin)
            .height(GlassTabBarHeight),
    ) {
        AnimatedContent(
            targetState = mode,
            transitionSpec = {
                (fadeIn() + scaleIn(initialScale = 0.9f)) togetherWith (fadeOut() + scaleOut(targetScale = 0.9f))
            },
            label = "glass-tab-mode",
        ) { shown ->
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                when (shown) {
                    BarMode.Full -> {
                        TabPill(tabs, current, onSelect, backdrop, tilt, Modifier.weight(1f))
                        Spacer(Modifier.width(10.dp))
                        SearchCircle(backdrop, tilt, onOpenSearch)
                    }
                    BarMode.Mini -> {
                        CurrentTabCircle(current, backdrop, tilt, onExpand)
                        Spacer(Modifier.weight(1f))
                        SearchCircle(backdrop, tilt, onOpenSearch)
                    }
                    BarMode.Search -> {
                        CurrentTabCircle(current, backdrop, tilt) {
                            search.close()
                            onExpand()
                        }
                        Spacer(Modifier.width(10.dp))
                        SearchCapsule(search, backdrop, tilt, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchCircle(backdrop: Backdrop?, tilt: State<Float>?, onClick: () -> Unit) {
    GlassCircleButton(
        icon = GlassIcons.Search,
        label = stringResource(Res.string.action_search),
        onClick = onClick,
        size = GlassTabBarHeight,
        iconSize = 24.dp,
        backdrop = backdrop,
        tilt = tilt,
    )
}

@Composable
private fun CurrentTabCircle(current: TopDestination, backdrop: Backdrop?, tilt: State<Float>?, onClick: () -> Unit) {
    GlassCircleButton(
        icon = glassTabIcon(current),
        label = stringResource(current.labelRes),
        onClick = onClick,
        size = GlassTabBarHeight,
        iconSize = 26.dp,
        tint = Glass.colors.tint,
        backdrop = backdrop,
        tilt = tilt,
    )
}

@Composable
private fun SearchCapsule(search: GlassSearchState, backdrop: Backdrop?, tilt: State<Float>?, modifier: Modifier) {
    val c = Glass.colors
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val placeholder = stringResource(Res.string.action_search)
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(
        modifier
            .fillMaxHeight()
            .glassChrome(c, GlassShapes.capsule, backdrop, tilt = tilt)
            .padding(start = 18.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(GlassIcons.Search, contentDescription = null, tint = c.label2, modifier = Modifier.size(21.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (search.query.isEmpty()) {
                Text(placeholder, style = GlassType.body(), color = c.label2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BasicTextField(
                value = search.query,
                onValueChange = { search.query = it },
                singleLine = true,
                textStyle = GlassType.body().copy(color = c.label),
                cursorBrush = SolidColor(c.tint),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .semantics { contentDescription = placeholder },
            )
        }
        if (search.query.isNotEmpty()) {
            val source = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .semantics { contentDescription = "" }
                    .clickable(source, null, role = Role.Button) { search.query = "" },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(c.label3), contentAlignment = Alignment.Center) {
                    Icon(GlassIcons.Close, contentDescription = stringResource(Res.string.action_clear), tint = if (c.dark) Color.Black else Color.White, modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

@Composable
private fun TabPill(
    tabs: List<TopDestination>,
    current: TopDestination,
    onSelect: (TopDestination) -> Unit,
    backdrop: Backdrop?,
    tilt: State<Float>?,
    modifier: Modifier,
) {
    if (tabs.isEmpty()) return
    val c = Glass.colors
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val haptics = LocalHapticFeedback.current
    val exported = rememberLayerBackdrop()
    val select by rememberUpdatedState(onSelect)
    var holding by remember { mutableStateOf(false) }
    var holdX by remember { mutableFloatStateOf(0f) }
    val currentIndex = tabs.indexOf(current).coerceAtLeast(0)
    BoxWithConstraints(modifier.fillMaxHeight()) {
        val pad = 4.dp
        val tabWidth = (maxWidth - pad * 2) / tabs.size
        val tabPx = with(density) { tabWidth.toPx() }.coerceAtLeast(1f)
        val padPx = with(density) { pad.toPx() }
        val pillWidth = maxWidth
        val widthPx = with(density) { pillWidth.toPx() }
        fun indexAt(x: Float): Int {
            val slot = ((x - padPx) / tabPx).toInt().coerceIn(0, tabs.lastIndex)
            return if (rtl) tabs.lastIndex - slot else slot
        }
        val hover = if (holding) indexAt(holdX) else -1
        val barScale by animateFloatAsState(
            if (holding) 1.03f else 1f,
            spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
            label = "glass-bar-scale",
        )
        val platterX by animateDpAsState(
            tabWidth * currentIndex,
            spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
            label = "glass-platter",
        )
        val platterAlpha by animateFloatAsState(if (holding) 0f else 1f, label = "glass-platter-alpha")
        val shape = GlassShapes.capsule
        val surface = if (backdrop != null && isRenderEffectSupported()) c.chrome else c.chromeSolid.copy(alpha = 0.95f)
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = barScale
                    scaleY = barScale
                }
                .then(
                    if (backdrop != null && !c.reduced) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { shape },
                            effects = {
                                vibrancy()
                                blur(12.dp.toPx())
                                lens(14.dp.toPx(), 26.dp.toPx())
                            },
                            highlight = { glassHighlightOf(tilt) },
                            shadow = { Shadow(radius = 24.dp, color = c.shadow) },
                            exportedBackdrop = exported,
                            onDrawSurface = { drawRect(surface) },
                        )
                    } else {
                        Modifier.glassChrome(c, shape)
                    },
                )
                .pointerInput(tabs, rtl, tabPx, padPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var cancelled = false
                        val up = withTimeoutOrNull(180L) {
                            waitForUpOrCancellation().also { if (it == null) cancelled = true }
                        }
                        if (cancelled) return@awaitEachGesture
                        if (up != null) {
                            up.consume()
                            select(tabs[indexAt(up.position.x)])
                            return@awaitEachGesture
                        }
                        holdX = down.position.x
                        holding = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    select(tabs[indexAt(change.position.x)])
                                    break
                                }
                                holdX = change.position.x
                                change.consume()
                            }
                        } finally {
                            holding = false
                        }
                    }
                },
        ) {
            Box(
                Modifier
                    .offset(x = pad + platterX, y = pad)
                    .width(tabWidth)
                    .fillMaxHeight()
                    .padding(bottom = pad * 2)
                    .graphicsLayer { alpha = platterAlpha }
                    .clip(shape)
                    .background(c.platter),
            )
            Row(Modifier.fillMaxSize().padding(pad)) {
                tabs.forEachIndexed { index, dest ->
                    val on = dest == current
                    val label = stringResource(dest.labelRes)
                    val color = when {
                        holding -> if (index == hover) c.tint else c.label
                        on -> c.tint
                        else -> c.label
                    }
                    TabCell(
                        dest = dest,
                        color = color,
                        modifier = Modifier.semantics {
                            role = Role.Tab
                            selected = on
                            contentDescription = label
                            onClick { onSelect(dest); true }
                        },
                    )
                }
            }
        }
        if (holding) {
            val bubbleWidth = tabWidth + 14.dp
            val bubblePx = with(density) { bubbleWidth.toPx() }
            val lift = with(density) { 5.dp.toPx() }
            val left = (holdX - bubblePx / 2f).coerceIn(-8f * density.density, widthPx - bubblePx + 8f * density.density)
            val bubbleShape = GlassShapes.capsule
            val lensBackdrop = if (backdrop != null && !c.reduced) rememberCombinedBackdrop(backdrop, exported) else null
            val bubble = Modifier
                .align(AbsoluteAlignment.TopLeft)
                .absoluteOffset { IntOffset(left.roundToInt(), -lift.roundToInt()) }
                .size(bubbleWidth, GlassTabBarHeight + 10.dp)
            Box(
                bubble.then(
                    if (lensBackdrop != null) {
                        Modifier.drawBackdrop(
                            backdrop = lensBackdrop,
                            shape = { bubbleShape },
                            effects = { lens(12.dp.toPx(), 24.dp.toPx(), chromaticAberration = true) },
                            highlight = { Highlight(width = 1.dp, alpha = 1f) },
                            shadow = { Shadow(radius = 22.dp, offset = DpOffset(0.dp, 8.dp), color = Color.Black.copy(alpha = 0.2f)) },
                            onDrawBackdrop = { drawIt -> scale(1.2f, 1.2f, center) { drawIt() } },
                            onDrawSurface = { drawRect(Color.White.copy(alpha = if (c.dark) 0.05f else 0.14f)) },
                        )
                    } else {
                        Modifier.glassChrome(c, bubbleShape, surface = c.thumb)
                    },
                ),
            )
            Box(bubble.clip(bubbleShape)) {
                Row(
                    Modifier
                        .wrapContentSize(AbsoluteAlignment.TopLeft, unbounded = true)
                        .absoluteOffset { IntOffset((-left).roundToInt(), lift.roundToInt()) }
                        .size(pillWidth, GlassTabBarHeight)
                        .graphicsLayer {
                            scaleX = 1.2f
                            scaleY = 1.2f
                            transformOrigin = TransformOrigin((holdX / widthPx).coerceIn(0f, 1f), 0.5f)
                        }
                        .padding(pad),
                ) {
                    tabs.forEachIndexed { index, dest ->
                        TabCell(dest = dest, color = if (index == hover) c.tint else c.label)
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.TabCell(dest: TopDestination, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .weight(1f)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
    ) {
        Icon(glassTabIcon(dest), contentDescription = null, tint = color, modifier = Modifier.size(25.dp))
        Text(stringResource(dest.labelRes), style = GlassType.tabLabel(), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
