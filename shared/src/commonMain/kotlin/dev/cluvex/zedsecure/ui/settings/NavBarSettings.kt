@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package dev.cluvex.zedsecure.ui.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.cluvex.zedsecure.domain.model.AppDesign
import dev.cluvex.zedsecure.domain.model.AppSettings
import dev.cluvex.zedsecure.domain.model.BentoDockLabels
import dev.cluvex.zedsecure.domain.model.NavBarStyle
import dev.cluvex.zedsecure.domain.model.NavTab
import dev.cluvex.zedsecure.domain.model.NavTabs
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.bento_dock_autohide
import dev.cluvex.zedsecure.shared.resources.bento_dock_autohide_sum
import dev.cluvex.zedsecure.shared.resources.bento_dock_labels_active
import dev.cluvex.zedsecure.shared.resources.bento_dock_labels_always
import dev.cluvex.zedsecure.shared.resources.bento_dock_labels_never
import dev.cluvex.zedsecure.shared.resources.bento_dock_labels_title
import dev.cluvex.zedsecure.shared.resources.bento_move_earlier
import dev.cluvex.zedsecure.shared.resources.bento_move_later
import dev.cluvex.zedsecure.shared.resources.ic_add
import dev.cluvex.zedsecure.shared.resources.ic_drag_handle
import dev.cluvex.zedsecure.shared.resources.ic_remove
import dev.cluvex.zedsecure.shared.resources.nav_style_compact
import dev.cluvex.zedsecure.shared.resources.nav_style_dock
import dev.cluvex.zedsecure.shared.resources.nav_style_expressive
import dev.cluvex.zedsecure.shared.resources.nav_style_floating
import dev.cluvex.zedsecure.shared.resources.nav_style_full
import dev.cluvex.zedsecure.shared.resources.nav_style_minimal
import dev.cluvex.zedsecure.shared.resources.nav_style_title
import dev.cluvex.zedsecure.shared.resources.nav_style_underline
import dev.cluvex.zedsecure.shared.resources.navbar_add
import dev.cluvex.zedsecure.shared.resources.navbar_count
import dev.cluvex.zedsecure.shared.resources.navbar_fixed
import dev.cluvex.zedsecure.shared.resources.navbar_page_sub
import dev.cluvex.zedsecure.shared.resources.navbar_remove
import dev.cluvex.zedsecure.shared.resources.navbar_style
import dev.cluvex.zedsecure.shared.resources.navbar_tabs
import dev.cluvex.zedsecure.ui.bento.BentoDock
import dev.cluvex.zedsecure.ui.navigation.TopDestination
import dev.cluvex.zedsecure.ui.navigation.ZedNavBar
import dev.cluvex.zedsecure.ui.platform.LocalPlatform
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

fun availableNavTabs(perApp: Boolean): Set<NavTab> =
    NavTab.entries.filter { it != NavTab.Apps || perApp }.toSet()

@Composable
fun navTabsSummary(settings: AppSettings): String {
    val perApp = LocalPlatform.current.supportsPerAppProxy
    return NavTabs.parse(settings.navTabs, availableNavTabs(perApp))
        .map { stringResource(TopDestination.of(it).labelRes) }
        .joinToString(" · ")
}

@Composable
fun NavBarSettingsPage(
    settings: AppSettings,
    contentPadding: PaddingValues,
    modifier: Modifier,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
) {
    val bento = settings.appDesign == AppDesign.Bento
    val perApp = LocalPlatform.current.supportsPerAppProxy
    val available = remember(perApp) { availableNavTabs(perApp) }
    val tabs = remember(settings.navTabs, available) { NavTabs.parse(settings.navTabs, available) }
    val style = if (bento) settings.bento.navStyle else settings.navBarStyle

    fun saveTabs(next: List<NavTab>) = onUpdate { it.copy(navTabs = NavTabs.encode(next)) }
    fun saveStyle(next: NavBarStyle) = onUpdate {
        if (bento) it.copy(bento = it.bento.copy(navStyle = next)) else it.copy(navBarStyle = next)
    }

    SettingsPageScaffold(
        title = stringResource(Res.string.nav_style_title),
        subtitle = stringResource(Res.string.navbar_page_sub),
        art = SettingsArts.Mode,
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        NavPreview(tabs.map { TopDestination.of(it) }, style, bento)

        SettingsGroup(title = stringResource(Res.string.navbar_tabs)) {
            SettingsSegment {
                TabList(tabs, onReorder = ::saveTabs, onRemove = { tab -> saveTabs(tabs - tab) })
                Text(
                    stringResource(Res.string.navbar_count, tabs.size, NavTabs.MAX),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                )
            }
            val addable = NavTab.entries.filter { it in available && it !in tabs }
            if (addable.isNotEmpty()) {
                SettingsSegment {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(Res.string.navbar_add), style = MaterialTheme.typography.bodyLargeEmphasized)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val room = NavTabs.canAdd(tabs)
                            addable.forEach { tab ->
                                AddChip(TopDestination.of(tab), enabled = room) {
                                    val settingsAt = tabs.indexOf(NavTab.Settings).takeIf { it >= 0 } ?: tabs.size
                                    saveTabs(tabs.toMutableList().apply { add(settingsAt, tab) })
                                }
                            }
                        }
                    }
                }
            }
        }

        SettingsGroup(title = stringResource(Res.string.navbar_style)) {
            val styles = buildList {
                if (bento) add(NavBarStyle.Dock to stringResource(Res.string.nav_style_dock))
                add(NavBarStyle.FloatingPill to stringResource(Res.string.nav_style_floating))
                add(NavBarStyle.CompactDock to stringResource(Res.string.nav_style_compact))
                add(NavBarStyle.ExpressivePill to stringResource(Res.string.nav_style_expressive))
                add(NavBarStyle.FullBar to stringResource(Res.string.nav_style_full))
                add(NavBarStyle.Underline to stringResource(Res.string.nav_style_underline))
                add(NavBarStyle.Minimal to stringResource(Res.string.nav_style_minimal))
            }
            SettingsListRow(
                title = stringResource(Res.string.navbar_style),
                options = styles,
                selected = style,
                onSelected = ::saveStyle,
            )
            if (bento && style == NavBarStyle.Dock) {
                SettingsChoiceRow(
                    title = stringResource(Res.string.bento_dock_labels_title),
                    summary = null,
                    options = listOf(
                        BentoDockLabels.Active to stringResource(Res.string.bento_dock_labels_active),
                        BentoDockLabels.Always to stringResource(Res.string.bento_dock_labels_always),
                        BentoDockLabels.Never to stringResource(Res.string.bento_dock_labels_never),
                    ),
                    selected = settings.bento.dockLabels,
                    onSelected = { v -> onUpdate { it.copy(bento = it.bento.copy(dockLabels = v)) } },
                )
                SettingsSwitchRow(
                    title = stringResource(Res.string.bento_dock_autohide),
                    summary = stringResource(Res.string.bento_dock_autohide_sum),
                    checked = settings.bento.dockAutoHide,
                    onCheckedChange = { v -> onUpdate { it.copy(bento = it.bento.copy(dockAutoHide = v)) } },
                )
            }
        }
    }
}

@Composable
private fun NavPreview(tabs: List<TopDestination>, style: NavBarStyle, bento: Boolean) {
    var current by remember(tabs) { mutableStateOf(tabs.first()) }
    if (current !in tabs) current = tabs.first()
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .consumeWindowInsets(WindowInsets.navigationBars)
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (bento && style == NavBarStyle.Dock) {
            BentoDock(tabs = tabs, current = current, onSelect = { current = it }, visible = true)
        } else {
            ZedNavBar(style = style, tabs = tabs, current = current, onSelect = { current = it })
        }
    }
}

@Composable
private fun TabList(
    tabs: List<NavTab>,
    onReorder: (List<NavTab>) -> Unit,
    onRemove: (NavTab) -> Unit,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val rowHeight = 64.dp
    val rowPx = with(density) { rowHeight.toPx() }
    var order by remember { mutableStateOf(tabs) }
    var dragging by remember { mutableStateOf<NavTab?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    androidx.compose.runtime.LaunchedEffect(tabs) { if (dragging == null) order = tabs }
    val tabsNow by rememberUpdatedState(order)
    val earlier = stringResource(Res.string.bento_move_earlier)
    val later = stringResource(Res.string.bento_move_later)

    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        order.forEachIndexed { index, tab ->
            key(tab) {
                val destination = TopDestination.of(tab)
                val dragged = dragging == tab
                val lift by animateFloatAsState(if (dragged) 1.03f else 1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium), label = "tab-lift")
                Row(
                    Modifier
                        .zIndex(if (dragged) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (dragged) offset else 0f
                            scaleX = lift
                            scaleY = lift
                        }
                        .fillMaxWidth()
                        .height(rowHeight)
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (dragged) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0f))
                        .semantics {
                            customActions = buildList {
                                if (index > 0) add(CustomAccessibilityAction(earlier) { onReorder(tabsNow.moved(tab, -1)); true })
                                if (index < order.lastIndex) add(CustomAccessibilityAction(later) { onReorder(tabsNow.moved(tab, 1)); true })
                            }
                        }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_drag_handle),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(40.dp)
                            .pointerInput(tab) {
                                detectDragGestures(
                                    onDragStart = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        dragging = tab
                                        offset = 0f
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        offset += amount.y
                                        val list = order
                                        val at = list.indexOf(tab)
                                        val steps = (offset / rowPx).roundToInt()
                                        if (at >= 0 && steps != 0) {
                                            val target = (at + steps).coerceIn(0, list.lastIndex)
                                            if (target != at) {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                order = list.toMutableList().apply { add(target, removeAt(at)) }
                                                offset -= (target - at) * rowPx
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        dragging = null
                                        offset = 0f
                                        onReorder(order)
                                    },
                                    onDragCancel = {
                                        dragging = null
                                        offset = 0f
                                        onReorder(order)
                                    },
                                )
                            }
                            .padding(8.dp),
                    )
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(destination.icon), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(destination.labelRes), style = MaterialTheme.typography.bodyLargeEmphasized)
                        if (!NavTabs.canRemove(tab)) {
                            Text(stringResource(Res.string.navbar_fixed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (NavTabs.canRemove(tab)) {
                        val label = stringResource(Res.string.navbar_remove, stringResource(destination.labelRes))
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .clickable(role = Role.Button, onClickLabel = label) { onRemove(tab) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(painterResource(Res.drawable.ic_remove), contentDescription = label, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun List<NavTab>.moved(tab: NavTab, by: Int): List<NavTab> {
    val at = indexOf(tab)
    val target = (at + by).coerceIn(0, lastIndex)
    if (at < 0 || target == at) return this
    return toMutableList().apply { add(target, removeAt(at)) }
}

@Composable
private fun AddChip(destination: TopDestination, enabled: Boolean, onClick: () -> Unit) {
    val label = stringResource(destination.labelRes)
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(painterResource(Res.drawable.ic_add), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Icon(painterResource(destination.icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(2.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
