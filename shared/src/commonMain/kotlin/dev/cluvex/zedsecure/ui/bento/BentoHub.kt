@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package dev.cluvex.zedsecure.ui.bento

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.bento_add_tile
import dev.cluvex.zedsecure.shared.resources.bento_add_tile_empty
import dev.cluvex.zedsecure.shared.resources.bento_all_settings
import dev.cluvex.zedsecure.shared.resources.bento_done
import dev.cluvex.zedsecure.shared.resources.bento_edit_hint
import dev.cluvex.zedsecure.shared.resources.bento_edit_tiles
import dev.cluvex.zedsecure.shared.resources.bento_hidden_title
import dev.cluvex.zedsecure.shared.resources.bento_hide_tile
import dev.cluvex.zedsecure.shared.resources.bento_move_earlier
import dev.cluvex.zedsecure.shared.resources.bento_move_later
import dev.cluvex.zedsecure.shared.resources.bento_no_results
import dev.cluvex.zedsecure.shared.resources.bento_reset
import dev.cluvex.zedsecure.shared.resources.bento_resize_tile
import dev.cluvex.zedsecure.shared.resources.bento_search_settings
import dev.cluvex.zedsecure.shared.resources.bento_size_small
import dev.cluvex.zedsecure.shared.resources.bento_size_tall
import dev.cluvex.zedsecure.shared.resources.bento_size_wide
import dev.cluvex.zedsecure.shared.resources.ic_add
import dev.cluvex.zedsecure.shared.resources.ic_check
import dev.cluvex.zedsecure.shared.resources.ic_chevron_right
import dev.cluvex.zedsecure.shared.resources.ic_edit
import dev.cluvex.zedsecure.shared.resources.ic_open_in_full
import dev.cluvex.zedsecure.shared.resources.ic_remove
import dev.cluvex.zedsecure.shared.resources.ic_restart_alt
import dev.cluvex.zedsecure.shared.resources.settings_title
import dev.cluvex.zedsecure.ui.onboarding.tourTarget
import dev.cluvex.zedsecure.ui.platform.appVerticalScroll
import dev.cluvex.zedsecure.ui.platform.rememberAppSheetState
import dev.cluvex.zedsecure.ui.settings.SettingsArt
import dev.cluvex.zedsecure.ui.settings.sharedArt
import kotlinx.coroutines.isActive
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@Immutable
data class BentoHubEntry(
    val key: String,
    val title: String,
    val summary: String,
    val art: SettingsArt,
    val keywords: List<String> = emptyList(),
    val tourKey: String? = null,
    val onOpen: () -> Unit,
)

@Composable
fun BentoHub(
    entries: List<BentoHubEntry>,
    layoutCode: String,
    onLayoutChange: (String) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val c = Bento.colors
    val haptics = LocalHapticFeedback.current
    val byKey = remember(entries) { entries.associateBy { it.key } }
    val available = remember(entries) { entries.map { it.key } }
    val saved = remember(layoutCode, available) { BentoTiles.decode(layoutCode, available) }
    var working by remember(saved) { mutableStateOf(saved) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    var viewportTop by remember { mutableFloatStateOf(0f) }
    var viewportHeight by remember { mutableFloatStateOf(0f) }

    fun commit(next: BentoTileLayout) {
        working = next
        onLayoutChange(BentoTiles.encode(next))
    }

    dev.cluvex.zedsecure.ui.platform.BackHandler(enabled = editing) { editing = false }

    Column(
        modifier
            .fillMaxSize()
            .onGloballyPositioned {
                viewportTop = it.positionInRoot().y
                viewportHeight = it.size.height.toFloat()
            }
            .appVerticalScroll(scroll, contentPadding)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BentoTopBar(title = stringResource(Res.string.settings_title)) {
            if (editing) {
                DonePill { editing = false }
            } else {
                MotifButton(
                    shape = androidx.compose.material3.MaterialShapes.Clover4Leaf,
                    container = c.stage,
                    content = c.onStage,
                    icon = Res.drawable.ic_edit,
                    label = stringResource(Res.string.bento_edit_tiles),
                    size = 52.dp,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        query = ""
                        editing = true
                    },
                )
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = BentoMetrics.gutter), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AnimatedVisibility(!editing) {
                BentoSearchField(query, { query = it }, stringResource(Res.string.bento_search_settings))
            }
            if (editing) {
                Text(
                    stringResource(Res.string.bento_edit_hint),
                    style = BentoType.body(),
                    color = c.muted,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            } else if (query.isBlank()) {
                BentoCaption(stringResource(Res.string.bento_all_settings))
            }
        }

        if (query.isNotBlank() && !editing) {
            val needle = query.trim().lowercase()
            val found = entries.filter { e ->
                e.title.lowercase().contains(needle) || e.summary.lowercase().contains(needle) ||
                    e.keywords.any { it.lowercase().contains(needle) }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = BentoMetrics.gutter), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (found.isEmpty()) {
                    Text(
                        stringResource(Res.string.bento_no_results),
                        style = BentoType.body(),
                        color = c.muted,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 12.dp),
                    )
                }
                found.forEachIndexed { i, entry ->
                    val hit = entry.keywords.firstOrNull { it.lowercase().contains(needle) }
                        ?.takeUnless { entry.title.lowercase().contains(needle) }
                    EntryRow(entry, i, found.size, subtitle = hit ?: entry.summary) { entry.onOpen() }
                }
            }
        } else {
            TileBoard(
                layout = working,
                entries = byKey,
                editing = editing,
                scroll = scroll,
                viewportTop = { viewportTop },
                viewportHeight = { viewportHeight },
                bottomInset = contentPadding.calculateBottomPadding(),
                onReorder = { working = it },
                onCommit = { commit(it) },
                onEnterEdit = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    query = ""
                    editing = true
                },
            )
            if (editing) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = BentoMetrics.gutter, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlineAction(stringResource(Res.string.bento_add_tile), Res.drawable.ic_add, Modifier.weight(1f)) { adding = true }
                    OutlineAction(stringResource(Res.string.bento_reset), Res.drawable.ic_restart_alt, Modifier) {
                        commit(BentoTiles.decode("", available))
                    }
                }
            } else if (working.hidden.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = BentoMetrics.gutter), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BentoCaption(stringResource(Res.string.bento_hidden_title))
                    val hiddenEntries = working.hidden.mapNotNull { byKey[it] }
                    hiddenEntries.forEachIndexed { i, entry ->
                        EntryRow(entry, i, hiddenEntries.size, subtitle = entry.summary) { entry.onOpen() }
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }

    if (adding) {
        ModalBottomSheet(onDismissRequest = { adding = false }, sheetState = rememberAppSheetState(), containerColor = c.ground) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = BentoMetrics.gutter, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    stringResource(Res.string.bento_add_tile),
                    style = BentoType.title(24.sp),
                    color = c.ink,
                    modifier = Modifier.padding(start = 6.dp, bottom = 10.dp),
                )
                val hiddenEntries = working.hidden.mapNotNull { byKey[it] }
                if (hiddenEntries.isEmpty()) {
                    Text(
                        stringResource(Res.string.bento_add_tile_empty),
                        style = BentoType.body(),
                        color = c.muted,
                        modifier = Modifier.padding(6.dp),
                    )
                }
                hiddenEntries.forEachIndexed { i, entry ->
                    EntryRow(entry, i, hiddenEntries.size, subtitle = entry.summary, trailingIcon = Res.drawable.ic_add) {
                        commit(working.with(entry.key))
                        if (working.hidden.isEmpty()) adding = false
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun TileBoard(
    layout: BentoTileLayout,
    entries: Map<String, BentoHubEntry>,
    editing: Boolean,
    scroll: androidx.compose.foundation.ScrollState,
    viewportTop: () -> Float,
    viewportHeight: () -> Float,
    bottomInset: Dp,
    onReorder: (BentoTileLayout) -> Unit,
    onCommit: (BentoTileLayout) -> Unit,
    onEnterEdit: () -> Unit,
) {
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val haptics = LocalHapticFeedback.current
    var boardTop by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = BentoMetrics.gutter)
            .onGloballyPositioned { boardTop = it.positionInRoot().y },
    ) {
        val columns = if (maxWidth >= 600.dp) 4 else 2
        val gap = BentoMetrics.gap()
        val rowHeight = BentoMetrics.tileRow()
        val gapPx = with(density) { gap.toPx() }
        val rowPx = with(density) { rowHeight.toPx() }
        val colPx = (with(density) { maxWidth.toPx() } - gapPx * (columns - 1)) / columns
        val placed = remember(layout, columns) { BentoTiles.pack(layout.visible, columns) }
        val rows = BentoTiles.rowsOf(placed)
        val height = with(density) { (rowPx * rows + gapPx * (rows - 1).coerceAtLeast(0)).toDp() }
        val placedNow by rememberUpdatedState(placed)
        val layoutNow by rememberUpdatedState(layout)

        var dragKey by remember { mutableStateOf<String?>(null) }
        var dragPos by remember { mutableStateOf(Offset.Zero) }

        fun originOf(p: BentoPlaced) = Offset((colPx + gapPx) * p.col, (rowPx + gapPx) * p.row)
        fun sizeOf(p: BentoPlaced) = Offset(colPx * p.cols + gapPx * (p.cols - 1), rowPx * p.rows + gapPx * (p.rows - 1))

        LaunchedEffect(dragKey) {
            if (dragKey == null) return@LaunchedEffect
            val edge = with(density) { 96.dp.toPx() }
            val bottomPad = with(density) { bottomInset.toPx() }
            while (isActive && dragKey != null) {
                withFrameNanos { }
                val key = dragKey ?: break
                val p = placedNow.firstOrNull { it.slot.key == key } ?: continue
                val viewport = viewportHeight()
                val centerY = boardTop - viewportTop() + dragPos.y + sizeOf(p).y / 2f
                val speed = when {
                    centerY < edge -> -((edge - centerY) / edge).coerceIn(0f, 1f) * 28f
                    centerY > viewport - bottomPad - edge -> ((centerY - (viewport - bottomPad - edge)) / edge).coerceIn(0f, 1f) * 28f
                    else -> 0f
                }
                if (speed != 0f) {
                    val moved = scroll.scrollBy(speed)
                    if (moved != 0f) dragPos = dragPos.copy(y = dragPos.y + moved)
                }
            }
        }

        Box(Modifier.fillMaxWidth().height(height)) {
            placed.forEachIndexed { index, p ->
                val entry = entries[p.slot.key] ?: return@forEachIndexed
                key(p.slot.key) {
                    val target = originOf(p)
                    val animated by animateIntOffsetAsState(
                        IntOffset(target.x.roundToInt(), target.y.roundToInt()),
                        spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
                        label = "tile-move",
                    )
                    val size = sizeOf(p)
                    val dragged = dragKey == p.slot.key
                    val lift by animateFloatAsState(if (dragged) 1.04f else 1f, spring(dampingRatio = 0.6f), label = "tile-lift")
                    val order = layout.visible.indexOfFirst { it.key == p.slot.key }
                    val earlier = stringResource(Res.string.bento_move_earlier)
                    val later = stringResource(Res.string.bento_move_later)
                    Box(
                        Modifier
                            .zIndex(if (dragged) 2f else 0f)
                            .offset {
                                if (dragged) IntOffset(dragPos.x.roundToInt(), dragPos.y.roundToInt()) else animated
                            }
                            .size(with(density) { size.x.toDp() }, with(density) { size.y.toDp() })
                            .graphicsLayer {
                                scaleX = lift
                                scaleY = lift
                            }
                            .then(
                                if (editing) {
                                    Modifier
                                        .semantics {
                                            customActions = listOf(
                                                CustomAccessibilityAction(earlier) {
                                                    if (order > 0) onCommit(layoutNow.moved(p.slot.key, order - 1))
                                                    true
                                                },
                                                CustomAccessibilityAction(later) {
                                                    if (order < layoutNow.visible.lastIndex) onCommit(layoutNow.moved(p.slot.key, order + 1))
                                                    true
                                                },
                                            )
                                        }
                                        .pointerInput(p.slot.key) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    val me = placedNow.firstOrNull { it.slot.key == p.slot.key } ?: return@detectDragGesturesAfterLongPress
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    dragPos = originOf(me)
                                                    dragKey = p.slot.key
                                                },
                                                onDrag = { change, amount ->
                                                    change.consume()
                                                    dragPos += Offset(if (rtl) -amount.x else amount.x, amount.y)
                                                    val me = placedNow.firstOrNull { it.slot.key == p.slot.key } ?: return@detectDragGesturesAfterLongPress
                                                    val mySize = sizeOf(me)
                                                    val center = dragPos + Offset(mySize.x / 2f, mySize.y / 2f)
                                                    val over = placedNow.firstOrNull { q ->
                                                        if (q.slot.key == p.slot.key) return@firstOrNull false
                                                        val o = originOf(q)
                                                        val s = sizeOf(q)
                                                        center.x in o.x..(o.x + s.x) && center.y in o.y..(o.y + s.y)
                                                    }
                                                    if (over != null) {
                                                        val to = layoutNow.visible.indexOfFirst { it.key == over.slot.key }
                                                        if (to >= 0) {
                                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            onReorder(layoutNow.moved(p.slot.key, to))
                                                        }
                                                    }
                                                },
                                                onDragEnd = {
                                                    dragKey = null
                                                    onCommit(layoutNow)
                                                },
                                                onDragCancel = {
                                                    dragKey = null
                                                    onCommit(layoutNow)
                                                },
                                            )
                                        }
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        HubTile(
                            entry = entry,
                            slot = p.slot,
                            index = BentoTiles.ordinalOf(entry.key),
                            editing = editing,
                            onOpen = entry.onOpen,
                            onLongPress = onEnterEdit,
                            onHide = { onCommit(layoutNow.without(entry.key)) },
                            onResize = { onCommit(layoutNow.resized(entry.key)) },
                            modifier = Modifier.fillMaxSize().then(entry.tourKey?.let { Modifier.tourTarget(it) } ?: Modifier),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HubTile(
    entry: BentoHubEntry,
    slot: BentoTileSlot,
    index: Int,
    editing: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onHide: () -> Unit,
    onResize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Bento.colors
    val prefs = Bento.prefs
    val prominent = BentoTiles.isProminent(entry.key)
    val tone = c.tone(BentoTiles.toneOf(entry.key))
    val accent = if (prominent) c.lime else tone.content
    val accentInk = if (prominent) c.onLime else tone.container
    val big = prominent || slot.size != BentoTileSize.Small
    val shape = if (editing) RoundedCornerShape(16.dp) else BentoShapes.tile(prominent)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val press by animateFloatAsState(
        if (pressed && !editing) (if (prefs.playfulTouch) 0.955f else 0.98f) else 1f,
        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "tile-press",
    )
    val spin by animateFloatAsState(
        if (pressed && !editing && prefs.playfulTouch) 22f else 0f,
        spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
        label = "tile-badge-spin",
    )
    val sizeName = stringResource(
        when (slot.size) {
            BentoTileSize.Small -> Res.string.bento_size_small
            BentoTileSize.Wide -> Res.string.bento_size_wide
            BentoTileSize.Tall -> Res.string.bento_size_tall
        },
    )
    val rotation = if (index % 2 == 0) 14f else -18f
    Box(
        modifier
            .graphicsLayer {
                scaleX = press
                scaleY = press
            }
            .clip(shape)
            .background(tone.container)
            .then(if (editing) Modifier.border(2.dp, tone.content.copy(alpha = 0.2f), shape) else Modifier)
            .bentoMotif(entry.art.shape, tone.content, if (big) 168.dp else 128.dp, rotation, enabled = prefs.motifs, alpha = 0.1f)
            .then(
                if (editing) {
                    Modifier.semantics { contentDescription = "${entry.title}, $sizeName" }
                } else {
                    Modifier.combinedClickable(
                        interactionSource = source,
                        indication = ripple(color = tone.content),
                        role = Role.Button,
                        onClickLabel = entry.title,
                        onLongClick = onLongPress,
                        onClick = onOpen,
                    )
                },
            ),
    ) {
        val compact = prefs.density == dev.cluvex.zedsecure.domain.model.BentoDensity.Compact
        var titleHeight by remember { androidx.compose.runtime.mutableIntStateOf(0) }
        androidx.compose.runtime.CompositionLocalProvider(LocalContentColor provides tone.content) {
            Column(
                Modifier.fillMaxSize().padding(
                    if (prominent) PaddingValues(horizontal = 18.dp, vertical = if (compact) 12.dp else 16.dp) else PaddingValues(BentoMetrics.pad()),
                ),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    if (prefs.indexes && !editing) BentoIndex(index, tone.content) else Spacer(Modifier.size(1.dp))
                    BentoBadge(
                        shape = entry.art.shape,
                        container = accent,
                        content = accentInk,
                        icon = entry.art.icon,
                        size = when {
                            prominent -> if (compact) 38.dp else 44.dp
                            compact -> 32.dp
                            else -> 38.dp
                        },
                        rotation = (if (index % 2 == 0) 10f else -10f) + spin,
                        modifier = if (editing) Modifier else Modifier.sharedArt(entry.art),
                    )
                }
                BoxWithConstraints(
                    Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp, end = if (editing) 34.dp else 0.dp),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    val density = LocalDensity.current
                    val bodyStyle = BentoType.body().copy(fontSize = 12.5.sp, lineHeight = 17.sp)
                    val lineHeight = with(density) { 17.sp.toPx() }
                    val gap = with(density) { 3.dp.toPx() }
                    val room = if (titleHeight <= 0) 0 else ((constraints.maxHeight - titleHeight - gap) / lineHeight).toInt()
                    val lines = room.coerceIn(0, if (slot.size == BentoTileSize.Tall) 4 else 3)
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            entry.title,
                            style = BentoType.title(
                                when {
                                    prominent -> if (compact) 22.sp else 26.sp
                                    slot.size == BentoTileSize.Small -> if (compact) 17.sp else 19.sp
                                    else -> if (compact) 19.sp else 21.sp
                                },
                            ),
                            color = tone.content,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { titleHeight = it.size.height },
                        )
                        if (lines > 0) {
                            Text(
                                if (editing) sizeName else entry.summary,
                                style = bodyStyle,
                                color = tone.content.copy(alpha = 0.82f),
                                maxLines = lines,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = editing,
            enter = scaleIn(spring(dampingRatio = 0.6f)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        ) {
            BentoIconButton(
                Res.drawable.ic_remove,
                stringResource(Res.string.bento_hide_tile, entry.title),
                onHide,
                container = c.danger,
                content = c.onDanger,
                size = 32.dp,
            )
        }
        AnimatedVisibility(
            visible = editing,
            enter = scaleIn(spring(dampingRatio = 0.6f)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(c.ground)
                    .clickable(role = Role.Button, onClick = onResize),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(Res.drawable.ic_open_in_full), contentDescription = stringResource(Res.string.bento_resize_tile, entry.title), tint = c.ink, modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
private fun EntryRow(
    entry: BentoHubEntry,
    index: Int,
    count: Int,
    subtitle: String,
    trailingIcon: org.jetbrains.compose.resources.DrawableResource = Res.drawable.ic_chevron_right,
    onClick: () -> Unit,
) {
    val c = Bento.colors
    val tone = c.tone(BentoTiles.toneOf(entry.key))
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = BentoMetrics.rowMin())
            .clip(BentoShapes.row(index, count))
            .background(c.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BentoBadge(entry.art.shape, tone.container, tone.content, entry.art.icon, 40.dp, rotation = 0f)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(entry.title, style = BentoType.rowTitle(), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = BentoType.body().copy(fontSize = 12.5.sp), color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(painterResource(trailingIcon), contentDescription = null, tint = c.muted, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun DonePill(onClick: () -> Unit) {
    val c = Bento.colors
    Row(
        Modifier
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(c.lime)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(painterResource(Res.drawable.ic_check), contentDescription = null, tint = c.onLime, modifier = Modifier.size(18.dp))
        Text(
            stringResource(Res.string.bento_done),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
            color = c.onLime,
        )
    }
}

@Composable
private fun OutlineAction(
    label: String,
    icon: org.jetbrains.compose.resources.DrawableResource,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val c = Bento.colors
    val shape = RoundedCornerShape(25.dp)
    Row(
        modifier
            .height(50.dp)
            .clip(shape)
            .border(1.dp, c.line, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = c.ink, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = c.ink, maxLines = 1)
    }
}
