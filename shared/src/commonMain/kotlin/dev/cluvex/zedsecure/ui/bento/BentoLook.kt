package dev.cluvex.zedsecure.ui.bento

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cluvex.zedsecure.domain.model.AppSettings
import dev.cluvex.zedsecure.domain.model.BentoConnect
import dev.cluvex.zedsecure.domain.model.BentoCorners
import dev.cluvex.zedsecure.domain.model.BentoDensity
import dev.cluvex.zedsecure.domain.model.BentoDockLabels
import dev.cluvex.zedsecure.domain.model.BentoPalette
import dev.cluvex.zedsecure.domain.model.BentoPrefs
import dev.cluvex.zedsecure.domain.model.BentoTitle
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.bento_connect_button
import dev.cluvex.zedsecure.shared.resources.bento_connect_slide
import dev.cluvex.zedsecure.shared.resources.bento_connect_title
import dev.cluvex.zedsecure.shared.resources.bento_corners_leaf
import dev.cluvex.zedsecure.shared.resources.bento_corners_round
import dev.cluvex.zedsecure.shared.resources.bento_corners_soft
import dev.cluvex.zedsecure.shared.resources.bento_corners_title
import dev.cluvex.zedsecure.shared.resources.bento_density_comfortable
import dev.cluvex.zedsecure.shared.resources.bento_density_compact
import dev.cluvex.zedsecure.shared.resources.bento_density_title
import dev.cluvex.zedsecure.shared.resources.bento_dock_autohide
import dev.cluvex.zedsecure.shared.resources.bento_dock_autohide_sum
import dev.cluvex.zedsecure.shared.resources.bento_dock_labels_active
import dev.cluvex.zedsecure.shared.resources.bento_dock_labels_always
import dev.cluvex.zedsecure.shared.resources.bento_dock_labels_never
import dev.cluvex.zedsecure.shared.resources.bento_dock_labels_title
import dev.cluvex.zedsecure.shared.resources.bento_group_board
import dev.cluvex.zedsecure.shared.resources.bento_group_dock
import dev.cluvex.zedsecure.shared.resources.bento_group_home
import dev.cluvex.zedsecure.shared.resources.bento_group_look
import dev.cluvex.zedsecure.shared.resources.bento_home_chips
import dev.cluvex.zedsecure.shared.resources.bento_home_quick
import dev.cluvex.zedsecure.shared.resources.bento_home_session
import dev.cluvex.zedsecure.shared.resources.bento_home_speed
import dev.cluvex.zedsecure.shared.resources.bento_indexes
import dev.cluvex.zedsecure.shared.resources.bento_indexes_sum
import dev.cluvex.zedsecure.shared.resources.bento_live_tiles
import dev.cluvex.zedsecure.shared.resources.bento_live_tiles_sum
import dev.cluvex.zedsecure.shared.resources.bento_motifs
import dev.cluvex.zedsecure.shared.resources.bento_motifs_sum
import dev.cluvex.zedsecure.shared.resources.bento_palette_accent
import dev.cluvex.zedsecure.shared.resources.bento_palette_brand
import dev.cluvex.zedsecure.shared.resources.bento_palette_mono
import dev.cluvex.zedsecure.shared.resources.bento_palette_pastel
import dev.cluvex.zedsecure.shared.resources.bento_palette_title
import dev.cluvex.zedsecure.shared.resources.bento_palette_vivid
import dev.cluvex.zedsecure.shared.resources.bento_playful
import dev.cluvex.zedsecure.shared.resources.bento_playful_sum
import dev.cluvex.zedsecure.shared.resources.bento_reset_look
import dev.cluvex.zedsecure.shared.resources.bento_reset_tiles
import dev.cluvex.zedsecure.shared.resources.bento_title_compact
import dev.cluvex.zedsecure.shared.resources.bento_title_huge
import dev.cluvex.zedsecure.shared.resources.bento_title_large
import dev.cluvex.zedsecure.shared.resources.bento_title_title
import dev.cluvex.zedsecure.shared.resources.bento_wave
import dev.cluvex.zedsecure.shared.resources.bento_wave_sum
import dev.cluvex.zedsecure.shared.resources.ic_check
import dev.cluvex.zedsecure.ui.settings.SettingsActionRow
import dev.cluvex.zedsecure.ui.settings.SettingsChoiceRow
import dev.cluvex.zedsecure.ui.settings.SettingsGroup
import dev.cluvex.zedsecure.ui.settings.SettingsSegment
import dev.cluvex.zedsecure.ui.settings.SettingsSwitchRow
import dev.cluvex.zedsecure.ui.theme.accentSeed
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun BentoLookSection(settings: AppSettings, onUpdate: ((AppSettings) -> AppSettings) -> Unit) {
    val prefs = settings.bento
    fun edit(change: (BentoPrefs) -> BentoPrefs) = onUpdate { it.copy(bento = change(it.bento)) }

    SettingsGroup(stringResource(Res.string.bento_group_look)) {
        PalettePicker(settings, prefs.palette) { p -> edit { it.copy(palette = p) } }
        SettingsChoiceRow(
            title = stringResource(Res.string.bento_corners_title),
            summary = null,
            options = listOf(
                BentoCorners.Leaf to stringResource(Res.string.bento_corners_leaf),
                BentoCorners.Round to stringResource(Res.string.bento_corners_round),
                BentoCorners.Soft to stringResource(Res.string.bento_corners_soft),
            ),
            selected = prefs.corners,
            onSelected = { v -> edit { it.copy(corners = v) } },
        )
        SettingsChoiceRow(
            title = stringResource(Res.string.bento_title_title),
            summary = null,
            options = listOf(
                BentoTitle.Huge to stringResource(Res.string.bento_title_huge),
                BentoTitle.Large to stringResource(Res.string.bento_title_large),
                BentoTitle.Compact to stringResource(Res.string.bento_title_compact),
            ),
            selected = prefs.title,
            onSelected = { v -> edit { it.copy(title = v) } },
        )
        SettingsChoiceRow(
            title = stringResource(Res.string.bento_density_title),
            summary = null,
            options = listOf(
                BentoDensity.Comfortable to stringResource(Res.string.bento_density_comfortable),
                BentoDensity.Compact to stringResource(Res.string.bento_density_compact),
            ),
            selected = prefs.density,
            onSelected = { v -> edit { it.copy(density = v) } },
        )
        SettingsSwitchRow(
            title = stringResource(Res.string.bento_motifs),
            summary = stringResource(Res.string.bento_motifs_sum),
            checked = prefs.motifs,
            onCheckedChange = { v -> edit { it.copy(motifs = v) } },
        )
        SettingsSwitchRow(
            title = stringResource(Res.string.bento_playful),
            summary = stringResource(Res.string.bento_playful_sum),
            checked = prefs.playfulTouch,
            onCheckedChange = { v -> edit { it.copy(playfulTouch = v) } },
        )
    }

    SettingsGroup(stringResource(Res.string.bento_group_board)) {
        SettingsSwitchRow(
            title = stringResource(Res.string.bento_indexes),
            summary = stringResource(Res.string.bento_indexes_sum),
            checked = prefs.indexes,
            onCheckedChange = { v -> edit { it.copy(indexes = v) } },
        )
        SettingsSwitchRow(
            title = stringResource(Res.string.bento_live_tiles),
            summary = stringResource(Res.string.bento_live_tiles_sum),
            checked = prefs.liveTiles,
            onCheckedChange = { v -> edit { it.copy(liveTiles = v) } },
        )
        SettingsActionRow(title = stringResource(Res.string.bento_reset_tiles)) { edit { it.copy(tiles = "") } }
    }

    SettingsGroup(stringResource(Res.string.bento_group_dock)) {
        SettingsChoiceRow(
            title = stringResource(Res.string.bento_dock_labels_title),
            summary = null,
            options = listOf(
                BentoDockLabels.Active to stringResource(Res.string.bento_dock_labels_active),
                BentoDockLabels.Always to stringResource(Res.string.bento_dock_labels_always),
                BentoDockLabels.Never to stringResource(Res.string.bento_dock_labels_never),
            ),
            selected = prefs.dockLabels,
            onSelected = { v -> edit { it.copy(dockLabels = v) } },
        )
        SettingsSwitchRow(
            title = stringResource(Res.string.bento_dock_autohide),
            summary = stringResource(Res.string.bento_dock_autohide_sum),
            checked = prefs.dockAutoHide,
            onCheckedChange = { v -> edit { it.copy(dockAutoHide = v) } },
        )
    }

    SettingsGroup(stringResource(Res.string.bento_group_home)) {
        SettingsChoiceRow(
            title = stringResource(Res.string.bento_connect_title),
            summary = null,
            options = listOf(
                BentoConnect.Slide to stringResource(Res.string.bento_connect_slide),
                BentoConnect.Button to stringResource(Res.string.bento_connect_button),
            ),
            selected = prefs.connect,
            onSelected = { v -> edit { it.copy(connect = v) } },
        )
        if (prefs.connect == BentoConnect.Slide) {
            SettingsSwitchRow(
                title = stringResource(Res.string.bento_wave),
                summary = stringResource(Res.string.bento_wave_sum),
                checked = prefs.wave,
                onCheckedChange = { v -> edit { it.copy(wave = v) } },
            )
        }
        SettingsSwitchRow(
            title = stringResource(Res.string.bento_home_speed),
            checked = prefs.homeSpeed,
            onCheckedChange = { v -> edit { it.copy(homeSpeed = v) } },
        )
        SettingsSwitchRow(
            title = stringResource(Res.string.bento_home_session),
            checked = prefs.homeSession,
            onCheckedChange = { v -> edit { it.copy(homeSession = v) } },
        )
        SettingsSwitchRow(
            title = stringResource(Res.string.bento_home_quick),
            checked = prefs.homeQuick,
            onCheckedChange = { v -> edit { it.copy(homeQuick = v) } },
        )
        SettingsSwitchRow(
            title = stringResource(Res.string.bento_home_chips),
            checked = prefs.homeChips,
            onCheckedChange = { v -> edit { it.copy(homeChips = v) } },
        )
        SettingsActionRow(title = stringResource(Res.string.bento_reset_look)) {
            edit { BentoPrefs(tiles = it.tiles) }
        }
    }
}

@Composable
private fun PalettePicker(settings: AppSettings, selected: BentoPalette, onSelect: (BentoPalette) -> Unit) {
    val dark = Bento.colors.dark
    val seed = accentSeed(settings.accentColor)
    val options = listOf(
        BentoPalette.Brand to stringResource(Res.string.bento_palette_brand),
        BentoPalette.Accent to stringResource(Res.string.bento_palette_accent),
        BentoPalette.Pastel to stringResource(Res.string.bento_palette_pastel),
        BentoPalette.Vivid to stringResource(Res.string.bento_palette_vivid),
        BentoPalette.Mono to stringResource(Res.string.bento_palette_mono),
    )
    SettingsSegment {
        Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 16.dp)) {
            Text(
                stringResource(Res.string.bento_palette_title),
                style = MaterialTheme.typography.bodyLargeEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(12.dp))
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(options, key = { it.first.name }) { (palette, label) ->
                    val colors = remember(palette, dark, seed) { BentoPalettes.of(palette, dark, seed, amoled = false) }
                    PaletteSwatch(label, colors, palette == selected) { onSelect(palette) }
                }
            }
        }
    }
}

@Composable
private fun PaletteSwatch(label: String, colors: BentoColors, selected: Boolean, onClick: () -> Unit) {
    val ring = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .width(92.dp)
            .clip(shape)
            .then(if (selected) Modifier.border(2.dp, ring, shape) else Modifier)
            .semantics { this.selected = selected }
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(colors.ground)
                .padding(5.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(Modifier.weight(1f).height(26.dp).clip(RoundedCornerShape(8.dp)).background(colors.stage))
                    Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(colors.lime))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf(colors.cyan.container, colors.pink.container, colors.blue.container).forEach { tone ->
                        Box(Modifier.weight(1f).height(23.dp).clip(RoundedCornerShape(7.dp)).background(tone))
                    }
                }
            }
            if (selected) {
                Box(
                    Modifier.align(Alignment.TopEnd).size(18.dp).clip(CircleShape).background(ring),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(Res.drawable.ic_check), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(12.dp))
                }
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun BentoDesignPreview() {
    val c = Bento.colors
    Column(
        Modifier.fillMaxWidth().padding(7.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(width = 44.dp, height = 9.dp).clip(RoundedCornerShape(4.dp)).background(c.ink.copy(alpha = 0.85f)))
        Box(Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(10.dp)).background(c.stage))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(Modifier.weight(1f).height(24.dp).clip(RoundedCornerShape(8.dp)).background(c.blue.container))
            Box(Modifier.weight(1f).height(24.dp).clip(RoundedCornerShape(8.dp)).background(c.violet.container))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(6.dp)).background(c.surface))
            Box(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(6.dp)).background(c.surface))
        }
        Box(Modifier.fillMaxWidth().height(20.dp).clip(RoundedCornerShape(8.dp)).background(c.lime))
        Box(
            Modifier.align(Alignment.CenterHorizontally).size(width = 54.dp, height = 12.dp).clip(RoundedCornerShape(6.dp)).background(c.dock),
        ) {
            Box(Modifier.align(Alignment.CenterStart).padding(2.dp).size(width = 18.dp, height = 8.dp).clip(RoundedCornerShape(4.dp)).background(c.lime))
        }
    }
}
