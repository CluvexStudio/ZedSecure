@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.cluvex.zedsecure.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cluvex.zedsecure.core.LogBus
import dev.cluvex.zedsecure.core.LogEntry
import dev.cluvex.zedsecure.core.LogSource
import dev.cluvex.zedsecure.core.VpnManager
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.action_cancel
import dev.cluvex.zedsecure.shared.resources.ic_content_paste
import dev.cluvex.zedsecure.shared.resources.ic_delete
import dev.cluvex.zedsecure.shared.resources.ic_ios_share
import dev.cluvex.zedsecure.shared.resources.ic_keyboard_arrow_down
import dev.cluvex.zedsecure.shared.resources.ic_search
import dev.cluvex.zedsecure.shared.resources.logs_clear
import dev.cluvex.zedsecure.shared.resources.logs_copied
import dev.cluvex.zedsecure.shared.resources.logs_copy
import dev.cluvex.zedsecure.shared.resources.logs_empty
import dev.cluvex.zedsecure.shared.resources.logs_jump_latest
import dev.cluvex.zedsecure.shared.resources.logs_no_match
import dev.cluvex.zedsecure.shared.resources.logs_search
import dev.cluvex.zedsecure.shared.resources.logs_share
import dev.cluvex.zedsecure.shared.resources.logs_source_all
import dev.cluvex.zedsecure.shared.resources.logs_source_app
import dev.cluvex.zedsecure.shared.resources.logs_title
import dev.cluvex.zedsecure.ui.glass.Glass
import dev.cluvex.zedsecure.ui.glass.GlassGroupChip
import dev.cluvex.zedsecure.ui.glass.GlassIcons
import dev.cluvex.zedsecure.ui.glass.GlassShapes
import dev.cluvex.zedsecure.ui.glass.GlassToolGroup
import dev.cluvex.zedsecure.ui.glass.GlassToolItem
import dev.cluvex.zedsecure.ui.glass.GlassType
import dev.cluvex.zedsecure.ui.glass.glassModule
import dev.cluvex.zedsecure.ui.platform.LocalPlatform
import dev.cluvex.zedsecure.ui.platform.draggableHorizontalScroll
import dev.cluvex.zedsecure.ui.platform.mouseDragScrollVertical
import dev.cluvex.zedsecure.ui.platform.rememberAppSheetState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private fun sourceOfKind(kind: String?): LogSource? = when (kind) {
    VpnManager.KIND_XRAY -> LogSource.Xray
    VpnManager.KIND_SINGBOX -> LogSource.SingBox
    VpnManager.KIND_TOR -> LogSource.Tor
    VpnManager.KIND_PSIPHON -> LogSource.Psiphon
    VpnManager.KIND_DNS_TUNNEL -> LogSource.DnsTunnel
    VpnManager.KIND_MASTERDNS -> LogSource.MasterDns
    VpnManager.KIND_SSH -> LogSource.Ssh
    VpnManager.KIND_OPENCONNECT -> LogSource.OpenConnect
    VpnManager.KIND_IKEV2 -> LogSource.Ikev2
    VpnManager.KIND_SNISPOOF -> LogSource.SniSpoof
    else -> null
}

@Composable
private fun sourceName(source: LogSource): String = when (source) {
    LogSource.Xray -> "Xray"
    LogSource.SingBox -> "sing-box"
    LogSource.Tor -> "Tor"
    LogSource.Psiphon -> "Psiphon"
    LogSource.DnsTunnel -> "DNSTT · VayDNS"
    LogSource.MasterDns -> "MasterDNS"
    LogSource.Ssh -> "SSH"
    LogSource.OpenConnect -> "OpenConnect"
    LogSource.Ikev2 -> "IKEv2"
    LogSource.SniSpoof -> "SNI-Spoof"
    LogSource.Tun -> "TUN"
    LogSource.App -> stringResource(Res.string.logs_source_app)
}

private fun sourceColor(source: LogSource, dark: Boolean): Color = when (source) {
    LogSource.Xray -> if (dark) Color(0xFF6D7CFF) else Color(0xFF6155F5)
    LogSource.SingBox -> if (dark) Color(0xFFFF9230) else Color(0xFFE07A10)
    LogSource.Tor -> if (dark) Color(0xFFDB34F2) else Color(0xFFB028C8)
    LogSource.Psiphon -> if (dark) Color(0xFF30D158) else Color(0xFF248A3D)
    LogSource.DnsTunnel -> if (dark) Color(0xFF00D2E0) else Color(0xFF0097A7)
    LogSource.MasterDns -> if (dark) Color(0xFF00DAC3) else Color(0xFF00897B)
    LogSource.Ssh -> if (dark) Color(0xFFB0B0B5) else Color(0xFF636366)
    LogSource.OpenConnect -> if (dark) Color(0xFF0091FF) else Color(0xFF0071D6)
    LogSource.Ikev2 -> if (dark) Color(0xFF3CD3FE) else Color(0xFF0085B2)
    LogSource.SniSpoof -> if (dark) Color(0xFFFF375F) else Color(0xFFD70040)
    LogSource.Tun -> if (dark) Color(0xFFB78A66) else Color(0xFF8B6748)
    LogSource.App -> if (dark) Color(0xFF98989D) else Color(0xFF6E6E73)
}

@Composable
fun LogSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberAppSheetState()) {
        LogPanel(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 20.dp), fill = false)
    }
}

@Composable
fun LogPanel(modifier: Modifier, fill: Boolean, showTitle: Boolean = true) {
    val platform = LocalPlatform.current
    val glass = Glass.active
    val logsCopied = stringResource(Res.string.logs_copied)
    val entries by LogBus.entries.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        LogBus.attach()
        onDispose { LogBus.detach() }
    }
    val activeKind by VpnManager.activeKind.collectAsStateWithLifecycle()
    val sessionSource = sourceOfKind(activeKind)
    var picked by rememberSaveable { mutableStateOf(sessionSource?.name ?: "") }
    var query by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val counts = remember(entries) { entries.groupingBy { it.source }.eachCount() }
    val sources = remember(counts, sessionSource) {
        LogSource.entries.filter { (counts[it] ?: 0) > 0 || it == sessionSource }
    }
    val selected = LogSource.entries.firstOrNull { it.name == picked }
    val shown = remember(entries, selected, query) {
        val needle = query.trim()
        entries.filter { e ->
            (selected == null || e.source == selected) && (needle.isEmpty() || e.text.contains(needle, ignoreCase = true))
        }
    }
    val atBottom by remember { derivedStateOf { !listState.canScrollForward } }
    LaunchedEffect(shown.size, picked) {
        if (shown.isNotEmpty() && atBottom) listState.scrollToItem(shown.lastIndex)
    }
    val exportText = { shown.joinToString("\n") { LogBus.render(it) } }

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showTitle) {
                Text(
                    stringResource(Res.string.logs_title),
                    style = if (glass) GlassType.title2() else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (glass) Glass.colors.label else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            val copyLabel = stringResource(Res.string.logs_copy)
            val shareLabel = stringResource(Res.string.logs_share)
            val clearLabel = stringResource(Res.string.logs_clear)
            val onCopy = {
                platform.copyToClipboard(exportText())
                platform.toast(logsCopied)
            }
            val onShare = { platform.shareText(exportText()) }
            val onClear = { LogBus.clear() }
            if (glass) {
                GlassToolGroup(
                    listOf(
                        GlassToolItem(GlassIcons.Copy, copyLabel, onCopy),
                        GlassToolItem(GlassIcons.Share, shareLabel, onShare),
                        GlassToolItem(GlassIcons.Trash, clearLabel, onClear),
                    ),
                )
            } else {
                FilledTonalIconButton(onClick = onCopy) { Icon(painterResource(Res.drawable.ic_content_paste), copyLabel, Modifier.size(18.dp)) }
                FilledTonalIconButton(onClick = onShare) { Icon(painterResource(Res.drawable.ic_ios_share), shareLabel, Modifier.size(18.dp)) }
                FilledTonalIconButton(onClick = onClear) { Icon(painterResource(Res.drawable.ic_delete), clearLabel, Modifier.size(18.dp)) }
            }
        }

        SearchBox(query, { query = it }, glass)

        Row(
            Modifier.fillMaxWidth().draggableHorizontalScroll(rememberScrollState()).padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SourceTab(stringResource(Res.string.logs_source_all), entries.size, selected == null, glass) { picked = "" }
            sources.forEach { src ->
                SourceTab(sourceName(src), counts[src] ?: 0, selected == src, glass) { picked = src.name }
            }
        }

        Box(if (fill) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().height(440.dp)) {
            val dark = if (glass) Glass.colors.dark else MaterialTheme.colorScheme.background.let { (it.red + it.green + it.blue) / 3f < 0.5f }
            val container = Modifier
                .fillMaxSize()
                .then(
                    if (glass) {
                        Modifier.glassModule(Glass.colors, GlassShapes.cell)
                    } else {
                        Modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    },
                )
            androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Box(container) {
                    if (shown.isEmpty()) {
                        Text(
                            stringResource(if (entries.isEmpty()) Res.string.logs_empty else Res.string.logs_no_match),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (glass) Glass.colors.label2 else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().mouseDragScrollVertical(listState).padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            itemsIndexed(shown, key = { i, e -> "$i:${e.time}:${e.text.hashCode()}" }) { _, entry ->
                                LogRow(entry, showSource = selected == null, dark = dark, glass = glass)
                            }
                        }
                    }
                }
            }
            if (!atBottom && shown.isNotEmpty()) {
                Row(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (glass) Glass.colors.tint else MaterialTheme.colorScheme.secondaryContainer)
                        .clickable(role = Role.Button) { scope.launch { listState.scrollToItem(shown.lastIndex) } }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val fg = if (glass) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
                    Icon(painterResource(Res.drawable.ic_keyboard_arrow_down), null, tint = fg, modifier = Modifier.size(16.dp))
                    Text(stringResource(Res.string.logs_jump_latest), style = MaterialTheme.typography.labelMedium, color = fg)
                }
            }
        }
    }
}

@Composable
private fun SearchBox(query: String, onQuery: (String) -> Unit, glass: Boolean) {
    val label = if (glass) Glass.colors.label else MaterialTheme.colorScheme.onSurface
    val muted = if (glass) Glass.colors.label2 else MaterialTheme.colorScheme.onSurfaceVariant
    val placeholder = stringResource(Res.string.logs_search)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 42.dp)
            .then(
                if (glass) {
                    Modifier.glassModule(Glass.colors, GlassShapes.capsule)
                } else {
                    Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                },
            )
            .padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (glass) {
            Icon(GlassIcons.Search, contentDescription = null, tint = muted, modifier = Modifier.size(17.dp))
        } else {
            Icon(painterResource(Res.drawable.ic_search), contentDescription = null, tint = muted, modifier = Modifier.size(18.dp))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = muted, maxLines = 1)
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = label),
                cursorBrush = SolidColor(if (glass) Glass.colors.tint else MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { onQuery("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    GlassIcons.ClearFill,
                    contentDescription = stringResource(Res.string.action_cancel),
                    tint = muted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun SourceTab(label: String, count: Int, selected: Boolean, glass: Boolean, onClick: () -> Unit) {
    if (glass) {
        GlassGroupChip(label = label, count = count, selected = selected, onClick = onClick)
    } else {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = { Text(if (count > 0) "$label  $count" else label) },
        )
    }
}

@Composable
private fun LogRow(entry: LogEntry, showSource: Boolean, dark: Boolean, glass: Boolean) {
    val base = if (glass) Glass.colors.label else MaterialTheme.colorScheme.onSurface
    val muted = if (glass) Glass.colors.label2 else MaterialTheme.colorScheme.onSurfaceVariant
    val error = if (glass) Glass.colors.red else MaterialTheme.colorScheme.error
    val warn = if (glass) Glass.colors.orange else MaterialTheme.colorScheme.tertiary
    val colour = when (entry.level) {
        'E', 'F' -> error
        'W' -> warn
        'D', 'V' -> muted
        else -> base
    }
    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.5.sp, lineHeight = 16.sp)
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
        Text(LogBus.clock(entry.time).substring(0, 8), style = mono, color = muted, maxLines = 1)
        Spacer(Modifier.width(8.dp))
        if (showSource) {
            val tone = sourceColor(entry.source, dark)
            Text(
                sourceName(entry.source),
                style = mono.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                color = tone,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.width(62.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(entry.text, style = mono, color = colour)
    }
}
