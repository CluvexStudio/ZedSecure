@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.cluvex.zedsecure.ui.zxps

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.cluvex.zedsecure.domain.model.AppSettings
import dev.cluvex.zedsecure.domain.model.ZxPsPalette
import dev.cluvex.zedsecure.domain.model.ZxPsPrefs
import dev.cluvex.zedsecure.domain.model.ZxPsTile
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.settings_title
import dev.cluvex.zedsecure.shared.resources.zxps_compact
import dev.cluvex.zedsecure.shared.resources.zxps_glass_crystal
import dev.cluvex.zedsecure.shared.resources.zxps_glass_frost
import dev.cluvex.zedsecure.shared.resources.zxps_glass_glass
import dev.cluvex.zedsecure.shared.resources.zxps_glass_solid
import dev.cluvex.zedsecure.shared.resources.zxps_glass_title
import dev.cluvex.zedsecure.shared.resources.zxps_group
import dev.cluvex.zedsecure.shared.resources.zxps_nav_labels
import dev.cluvex.zedsecure.shared.resources.zxps_palette_aurora
import dev.cluvex.zedsecure.shared.resources.zxps_palette_ember
import dev.cluvex.zedsecure.shared.resources.zxps_palette_graphite
import dev.cluvex.zedsecure.shared.resources.zxps_palette_mint
import dev.cluvex.zedsecure.shared.resources.zxps_palette_sakura
import dev.cluvex.zedsecure.shared.resources.zxps_palette_title
import dev.cluvex.zedsecure.shared.resources.zxps_sheen
import dev.cluvex.zedsecure.shared.resources.zxps_tile_petal
import dev.cluvex.zedsecure.shared.resources.zxps_tile_soft
import dev.cluvex.zedsecure.shared.resources.zxps_tile_squircle
import dev.cluvex.zedsecure.shared.resources.zxps_tile_title
import dev.cluvex.zedsecure.ui.onboarding.tourTarget
import dev.cluvex.zedsecure.ui.platform.appVerticalScroll
import dev.cluvex.zedsecure.ui.bento.BentoHubEntry
import dev.cluvex.zedsecure.ui.settings.SettingsGroup
import dev.cluvex.zedsecure.ui.settings.SettingsListRow
import dev.cluvex.zedsecure.ui.settings.SettingsSwitchRow
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Settings page body for ZX PS: floating glass tiles in a staggered two-column weave instead of Bento's board. */
@Composable
fun ZxPsHub(
    entries: List<BentoHubEntry>,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val c = ZxPs.colors
    val p = ZxPs.prefs
    val scroll = rememberScrollState()
    val gap = if (p.compact) 8.dp else 12.dp
    Box(modifier.fillMaxSize()) {
        ZxPsBackdrop(animate = false, intensity = 0.8f)
        Column(
            Modifier.fillMaxSize().appVerticalScroll(scroll).padding(contentPadding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            Text(
                stringResource(Res.string.settings_title),
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = c.ink,
                modifier = Modifier.padding(start = 6.dp, top = 12.dp, bottom = 4.dp),
            )
            // Rhythm: wide, pair, pair, wide ... so the page reads as a woven wall, not a grid.
            var i = 0
            var row = 0
            while (i < entries.size) {
                val wide = row % 3 == 0 || i == entries.size - 1
                if (wide) {
                    HubTile(entries[i], i, wide = true, Modifier.fillMaxWidth())
                    i += 1
                } else {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        HubTile(entries[i], i, wide = false, Modifier.weight(1f).fillMaxHeight())
                        HubTile(entries[i + 1], i + 1, wide = false, Modifier.weight(1f).fillMaxHeight())
                    }
                    i += 2
                }
                row++
            }
            Spacer(Modifier.height(110.dp))
        }
    }
}

@Composable
private fun HubTile(entry: BentoHubEntry, index: Int, wide: Boolean, modifier: Modifier) {
    val c = ZxPs.colors
    val p = ZxPs.prefs
    val tone = when (entry.art.hue % 3) { 0 -> c.a; 1 -> c.b; else -> c.c }
    val shape = zxPsTileShape(p.tile, index, p.compact)
    val base = if (entry.tourKey != null) modifier.tourTarget(entry.tourKey) else modifier
    ZxPsCard(
        shape = shape,
        tint = tone,
        modifier = base.clickable(role = Role.Button, onClick = entry.onOpen),
    ) {
        if (wide) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Badge(entry, tone)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(entry.summary, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Badge(entry, tone)
                Column {
                    Text(entry.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(entry.summary, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun Badge(entry: BentoHubEntry, tone: Color) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(Brush.linearGradient(listOf(tone.copy(alpha = 0.40f), tone.copy(alpha = 0.14f)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(entry.art.icon), null, tint = tone, modifier = Modifier.size(24.dp))
    }
}

/** Customisation shown on the UI page while ZX PS is selected. */
@Composable
fun ZxPsLookSection(settings: AppSettings, onUpdate: ((AppSettings) -> AppSettings) -> Unit) {
    val z = settings.zxPs
    fun set(f: (ZxPsPrefs) -> ZxPsPrefs) = onUpdate { it.copy(zxPs = f(it.zxPs)) }
    SettingsGroup(stringResource(Res.string.zxps_group)) {
        SettingsListRow(
            title = stringResource(Res.string.zxps_palette_title),
            options = listOf(
                ZxPsPalette.Aurora to stringResource(Res.string.zxps_palette_aurora),
                ZxPsPalette.Graphite to stringResource(Res.string.zxps_palette_graphite),
                ZxPsPalette.Sakura to stringResource(Res.string.zxps_palette_sakura),
                ZxPsPalette.Mint to stringResource(Res.string.zxps_palette_mint),
                ZxPsPalette.Ember to stringResource(Res.string.zxps_palette_ember),
            ),
            selected = z.palette,
            onSelected = { v -> set { it.copy(palette = v) } },
        )
        SettingsListRow(
            title = stringResource(Res.string.zxps_tile_title),
            options = listOf(
                ZxPsTile.Soft to stringResource(Res.string.zxps_tile_soft),
                ZxPsTile.Squircle to stringResource(Res.string.zxps_tile_squircle),
                ZxPsTile.Petal to stringResource(Res.string.zxps_tile_petal),
            ),
            selected = z.tile,
            onSelected = { v -> set { it.copy(tile = v) } },
        )
        SettingsListRow(
            title = stringResource(Res.string.zxps_glass_title),
            options = listOf(
                15 to stringResource(Res.string.zxps_glass_solid),
                45 to stringResource(Res.string.zxps_glass_frost),
                70 to stringResource(Res.string.zxps_glass_glass),
                95 to stringResource(Res.string.zxps_glass_crystal),
            ),
            selected = listOf(15, 45, 70, 95).minByOrNull { kotlin.math.abs(it - z.glass) } ?: 70,
            onSelected = { v -> set { it.copy(glass = v) } },
        )
        SettingsSwitchRow(
            title = stringResource(Res.string.zxps_nav_labels),
            checked = z.navLabels,
            onCheckedChange = { v -> set { it.copy(navLabels = v) } },
        )
        SettingsSwitchRow(
            title = stringResource(Res.string.zxps_sheen),
            checked = z.sheen,
            onCheckedChange = { v -> set { it.copy(sheen = v) } },
        )
        SettingsSwitchRow(
            title = stringResource(Res.string.zxps_compact),
            checked = z.compact,
            onCheckedChange = { v -> set { it.copy(compact = v) } },
        )
    }
}

/** Static miniature for the design picker: aurora backdrop, cookie hero, glass tiles and the glass bar. */
@Composable
fun ZxPsDesignPreview() {
    val c = ZxPsPalettes.of(ZxPsPalette.Aurora, true)
    Box(Modifier.fillMaxSize().background(c.ground)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawCircle(Brush.radialGradient(listOf(c.a.copy(alpha = 0.5f), Color.Transparent), Offset(w * 0.2f, h * 0.15f), w), w, Offset(w * 0.2f, h * 0.15f))
            drawCircle(Brush.radialGradient(listOf(c.b.copy(alpha = 0.4f), Color.Transparent), Offset(w * 0.9f, h * 0.6f), w * 0.8f), w * 0.8f, Offset(w * 0.9f, h * 0.6f))
        }
        Column(
            Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                Modifier.size(46.dp).clip(androidx.compose.material3.MaterialShapes.Cookie12Sided.toShape())
                    .background(Brush.linearGradient(listOf(c.a, c.b))),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.weight(1f).height(26.dp).clip(RoundedCornerShape(topStart = 12.dp, topEnd = 4.dp, bottomEnd = 12.dp, bottomStart = 4.dp)).background(c.a.copy(alpha = 0.30f)))
                Box(Modifier.weight(1f).height(26.dp).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 12.dp, bottomEnd = 4.dp, bottomStart = 12.dp)).background(c.b.copy(alpha = 0.30f)))
            }
            Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(8.dp)).background(c.c.copy(alpha = 0.28f)))
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth().height(16.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)))
        }
    }
}
