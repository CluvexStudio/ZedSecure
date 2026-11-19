package dev.cluvex.zedsecure.ui.glass

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.action_back
import dev.cluvex.zedsecure.ui.components.LocalPageBack
import dev.cluvex.zedsecure.ui.platform.appVerticalScroll
import dev.cluvex.zedsecure.ui.settings.SettingsArt
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private class ArtLook(val color: (Boolean) -> Color, val vector: androidx.compose.ui.graphics.vector.ImageVector?)

private fun artLook(key: String): ArtLook? = when (key) {
    "support" -> ArtLook(IosColors::pink, GlassIcons.Heart)
    "appearance" -> ArtLook(IosColors::cyan, GlassIcons.Palette)
    "mode" -> ArtLook(IosColors::green, GlassIcons.Shield)
    "monitor" -> ArtLook(IosColors::red, GlassIcons.Waveform)
    "ai" -> ArtLook(IosColors::purple, GlassIcons.Sparkles)
    "tour" -> ArtLook(IosColors::teal, GlassIcons.Compass)
    "routing" -> ArtLook(IosColors::orange, GlassIcons.Routing)
    "setup" -> ArtLook(IosColors::mint, GlassIcons.Wand)
    "per-app" -> ArtLook(IosColors::purple, GlassIcons.Apps)
    "assets" -> ArtLook(IosColors::brown, GlassIcons.Archive)
    "dns" -> ArtLook(IosColors::blue, GlassIcons.Globe)
    "tor" -> ArtLook(IosColors::indigo, GlassIcons.Layers)
    "ssh" -> ArtLook(IosColors::gray, GlassIcons.Code)
    "vpn" -> ArtLook(IosColors::blue, GlassIcons.LockShield)
    "core" -> ArtLook(IosColors::indigo, GlassIcons.Chip)
    "mux" -> ArtLook(IosColors::mint, GlassIcons.Merge)
    "fragment" -> ArtLook(IosColors::yellow, GlassIcons.Scissors)
    "sing-box" -> ArtLook(IosColors::orange, GlassIcons.Box)
    "observatory" -> ArtLook(IosColors::teal, GlassIcons.Antenna)
    "advanced" -> ArtLook(IosColors::gray, GlassIcons.Sliders)
    "map" -> ArtLook(IosColors::green, GlassIcons.MapFill)
    "vault" -> ArtLook(IosColors::brown, GlassIcons.Lock)
    "speed-test" -> ArtLook(IosColors::pink, GlassIcons.Gauge)
    "scanner" -> ArtLook(IosColors::indigo, GlassIcons.Scope)
    "update" -> ArtLook(IosColors::blue, GlassIcons.Sync)
    "rate" -> ArtLook(IosColors::yellow, GlassIcons.Star)
    "privacy" -> ArtLook(IosColors::indigo, GlassIcons.Hand)
    "about" -> ArtLook(IosColors::gray, GlassIcons.Info)
    "telegram" -> ArtLook(IosColors::blue, GlassIcons.Link)
    "github" -> ArtLook(IosColors::gray, GlassIcons.Code)
    "license" -> ArtLook(IosColors::indigo, GlassIcons.Doc)
    "app-source" -> ArtLook(IosColors::green, GlassIcons.Globe)
    else -> null
}

@Composable
fun GlassArtBadge(art: SettingsArt, modifier: Modifier = Modifier, size: Dp = 30.dp) {
    val look = artLook(art.key)
    GlassBadge(
        color = look?.color?.invoke(false) ?: IosColors.gray(false),
        modifier = modifier,
        icon = look?.vector,
        drawable = if (look?.vector == null) art.icon else null,
        size = size,
    )
}

@Composable
fun GlassSettingsGroup(title: String?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) GlassSectionHeader(title) else Spacer(Modifier.height(24.dp))
        GlassGroup(content = content)
    }
}

@Composable
fun GlassSettingsHeader(title: String, modifier: Modifier = Modifier) {
    val back = LocalPageBack.current
    if (back != null) {
        GlassPageBar(
            title = title,
            onBack = back,
            backLabel = stringResource(Res.string.action_back),
            modifier = modifier.padding(top = 4.dp, bottom = 4.dp),
        )
    } else {
        GlassLargeTitle(title, modifier.padding(top = 6.dp, bottom = 2.dp))
    }
}

@Composable
fun GlassSettingsPage(
    title: String,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    scroll: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .appVerticalScroll(scroll, contentPadding)
            .padding(contentPadding),
    ) {
        Spacer(Modifier.height(8.dp))
        GlassSettingsHeader(title)
        content()
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
fun <T> GlassListRow(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    enabled: Boolean,
    onSelected: (T) -> Unit,
) {
    val c = Glass.colors
    var open by rememberSaveable(title) { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == selected }?.second ?: options.firstOrNull()?.second.orEmpty()
    Box(Modifier.fillMaxWidth()) {
        GlassRow(
            title = title,
            value = current,
            enabled = enabled,
            trailing = {
                Icon(GlassIcons.UpDown, contentDescription = null, tint = c.label2, modifier = Modifier.size(width = 13.dp, height = 16.dp))
            },
            onClick = { open = true },
        )
        Box(Modifier.align(Alignment.BottomEnd)) {
            DropdownMenu(
                expanded = open && enabled,
                onDismissRequest = { open = false },
                shape = RoundedCornerShape(24.dp),
                containerColor = c.menu,
            ) {
                options.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label, style = GlassType.body(), color = c.label) },
                        leadingIcon = {
                            if (value == selected) {
                                Icon(GlassIcons.Check, contentDescription = null, tint = c.label, modifier = Modifier.size(17.dp))
                            } else {
                                Spacer(Modifier.size(17.dp))
                            }
                        },
                        onClick = {
                            open = false
                            onSelected(value)
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun GlassChip(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Glass.colors
    val shape = GlassShapes.capsule
    Box(
        Modifier
            .heightIn(min = 36.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .clip(shape)
            .background(if (selected) c.tint else c.fill2)
            .selectable(selected, remember { MutableInteractionSource() }, glassRowIndication(), enabled, Role.RadioButton, onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = GlassType.subhead().copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            color = if (selected) Color.White else c.label,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> GlassChoices(
    options: List<Pair<T, String>>,
    selected: T,
    enabled: Boolean,
    onSelected: (T) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val measurer = rememberTextMeasurer()
        val style = GlassType.subhead().copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        val density = LocalDensity.current
        val slot = maxWidth / options.size.coerceAtLeast(1)
        val labels = options.map { it.second }
        val measured = remember(labels, style, slot, density) {
            labels.all { label ->
                with(density) { measurer.measure(label, style, maxLines = 1, softWrap = false).size.width.toDp() } + 16.dp <= slot
            }
        }
        val fits = options.size in 2..4 && measured
        if (fits) {
            GlassSegmented(
                options = labels,
                selected = options.indexOfFirst { it.first == selected }.coerceAtLeast(0),
                onSelect = { onSelected(options[it].first) },
                enabled = enabled,
            )
        } else {
            FlowRow(
                Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                options.forEach { (value, label) ->
                    GlassChip(label, value == selected, enabled) { onSelected(value) }
                }
            }
        }
    }
}

@Composable
fun <T> GlassChoiceRow(
    title: String,
    summary: String?,
    options: List<Pair<T, String>>,
    selected: T,
    enabled: Boolean,
    onSelected: (T) -> Unit,
) {
    val c = Glass.colors
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp)) {
        Text(title, style = GlassType.body(), color = c.label.copy(alpha = if (enabled) 1f else 0.4f))
        if (!summary.isNullOrEmpty()) {
            Spacer(Modifier.height(1.dp))
            Text(summary, style = GlassType.footnote(), color = c.label2)
        }
        Spacer(Modifier.height(10.dp))
        GlassChoices(options, selected, enabled, onSelected)
    }
}

@Composable
fun GlassInfoRow(title: String, body: String) {
    val c = Glass.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(22.dp).clip(GlassShapes.capsule).background(c.tint), contentAlignment = Alignment.Center) {
            Text("i", style = GlassType.footnote().copy(fontWeight = FontWeight.Bold), color = Color.White)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = GlassType.subhead().copy(fontWeight = FontWeight.SemiBold), color = c.label)
            Spacer(Modifier.height(2.dp))
            Text(body, style = GlassType.footnote(), color = c.label2)
        }
    }
}

@Composable
fun GlassRowIcon(drawable: DrawableResource, tint: Color) {
    Icon(painterResource(drawable), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
}

@Composable
fun GlassBusy(modifier: Modifier = Modifier) {
    GlassSpinner(Glass.colors.label2, modifier = modifier.width(17.dp))
}

@Composable
fun GlassBanner(title: String, subtitle: String, art: SettingsArt, onClick: () -> Unit) {
    GlassRow(
        title = title,
        subtitle = subtitle,
        leading = { GlassArtBadge(art, size = 42.dp) },
        chevron = true,
        onClick = onClick,
        modifier = Modifier.heightIn(min = 64.dp),
    )
}

