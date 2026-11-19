package dev.cluvex.zedsecure.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.cluvex.zedsecure.domain.model.AppSettings
import dev.cluvex.zedsecure.domain.model.GlassConnect
import dev.cluvex.zedsecure.domain.model.GlassMaterial
import dev.cluvex.zedsecure.domain.model.GlassPrefs
import dev.cluvex.zedsecure.domain.model.GlassTint
import dev.cluvex.zedsecure.domain.model.GlassWallpaper
import dev.cluvex.zedsecure.domain.model.ThemeMode
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.bento_detect_location_title
import dev.cluvex.zedsecure.shared.resources.glass_accent
import dev.cluvex.zedsecure.shared.resources.glass_appearance
import dev.cluvex.zedsecure.shared.resources.glass_automatic
import dev.cluvex.zedsecure.shared.resources.glass_background
import dev.cluvex.zedsecure.shared.resources.glass_bg_dusk
import dev.cluvex.zedsecure.shared.resources.glass_bg_graphite
import dev.cluvex.zedsecure.shared.resources.glass_bg_lagoon
import dev.cluvex.zedsecure.shared.resources.glass_bg_live
import dev.cluvex.zedsecure.shared.resources.glass_bg_plain
import dev.cluvex.zedsecure.shared.resources.glass_clear
import dev.cluvex.zedsecure.shared.resources.glass_connect_control
import dev.cluvex.zedsecure.shared.resources.glass_control_button
import dev.cluvex.zedsecure.shared.resources.glass_control_slider
import dev.cluvex.zedsecure.shared.resources.glass_control_switch
import dev.cluvex.zedsecure.shared.resources.glass_control_z
import dev.cluvex.zedsecure.shared.resources.glass_design_name
import dev.cluvex.zedsecure.shared.resources.glass_home_card
import dev.cluvex.zedsecure.shared.resources.glass_home_header
import dev.cluvex.zedsecure.shared.resources.glass_home_speed
import dev.cluvex.zedsecure.shared.resources.glass_home_timer
import dev.cluvex.zedsecure.shared.resources.glass_lists
import dev.cluvex.zedsecure.shared.resources.glass_material_foot
import dev.cluvex.zedsecure.shared.resources.glass_motion
import dev.cluvex.zedsecure.shared.resources.glass_motion_sub
import dev.cluvex.zedsecure.shared.resources.glass_reduce
import dev.cluvex.zedsecure.shared.resources.glass_render_foot
import dev.cluvex.zedsecure.shared.resources.glass_shrink
import dev.cluvex.zedsecure.shared.resources.glass_tint_blue
import dev.cluvex.zedsecure.shared.resources.glass_tint_brown
import dev.cluvex.zedsecure.shared.resources.glass_tint_cyan
import dev.cluvex.zedsecure.shared.resources.glass_tint_graphite
import dev.cluvex.zedsecure.shared.resources.glass_tint_green
import dev.cluvex.zedsecure.shared.resources.glass_tint_indigo
import dev.cluvex.zedsecure.shared.resources.glass_tint_mint
import dev.cluvex.zedsecure.shared.resources.glass_tint_orange
import dev.cluvex.zedsecure.shared.resources.glass_tint_pink
import dev.cluvex.zedsecure.shared.resources.glass_tint_purple
import dev.cluvex.zedsecure.shared.resources.glass_tint_red
import dev.cluvex.zedsecure.shared.resources.glass_tint_teal
import dev.cluvex.zedsecure.shared.resources.glass_tinted
import dev.cluvex.zedsecure.shared.resources.settings_theme_dark
import dev.cluvex.zedsecure.shared.resources.settings_theme_light
import dev.cluvex.zedsecure.ui.platform.LocalPlatform
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

fun glassWallpaperName(kind: GlassWallpaper): StringResource = when (kind) {
    GlassWallpaper.Live -> Res.string.glass_bg_live
    GlassWallpaper.Lagoon -> Res.string.glass_bg_lagoon
    GlassWallpaper.Dusk -> Res.string.glass_bg_dusk
    GlassWallpaper.Graphite -> Res.string.glass_bg_graphite
    GlassWallpaper.Plain -> Res.string.glass_bg_plain
}

private fun tintName(tint: GlassTint): StringResource = when (tint) {
    GlassTint.Blue -> Res.string.glass_tint_blue
    GlassTint.Indigo -> Res.string.glass_tint_indigo
    GlassTint.Purple -> Res.string.glass_tint_purple
    GlassTint.Pink -> Res.string.glass_tint_pink
    GlassTint.Red -> Res.string.glass_tint_red
    GlassTint.Orange -> Res.string.glass_tint_orange
    GlassTint.Green -> Res.string.glass_tint_green
    GlassTint.Mint -> Res.string.glass_tint_mint
    GlassTint.Teal -> Res.string.glass_tint_teal
    GlassTint.Cyan -> Res.string.glass_tint_cyan
    GlassTint.Brown -> Res.string.glass_tint_brown
    GlassTint.Graphite -> Res.string.glass_tint_graphite
}

@Composable
fun GlassAppearanceSection(settings: AppSettings, onUpdate: ((AppSettings) -> AppSettings) -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.themeMode) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    GlassSectionHeader(stringResource(Res.string.glass_appearance))
    GlassGroup {
        Row(
            Modifier.fillMaxWidth().selectableGroup().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(52.dp, Alignment.CenterHorizontally),
        ) {
            LookChoice(stringResource(Res.string.settings_theme_light), selected = !dark, darkPreview = false) {
                onUpdate { it.copy(themeMode = ThemeMode.Light) }
            }
            LookChoice(stringResource(Res.string.settings_theme_dark), selected = dark, darkPreview = true) {
                onUpdate { it.copy(themeMode = ThemeMode.Dark) }
            }
        }
        GlassSwitchRow(
            title = stringResource(Res.string.glass_automatic),
            checked = settings.themeMode == ThemeMode.System,
            onCheckedChange = { on ->
                onUpdate { it.copy(themeMode = if (on) ThemeMode.System else if (dark) ThemeMode.Dark else ThemeMode.Light) }
            },
        )
    }
}

@Composable
private fun PreviewChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    preview: @Composable () -> Unit,
) {
    val c = Glass.colors
    Column(
        modifier.selectable(selected, remember { MutableInteractionSource() }, null, true, Role.RadioButton, onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        preview()
        Text(label, style = GlassType.footnote().copy(fontWeight = FontWeight.Medium), color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        GlassRadio(selected)
    }
}

private fun Modifier.choiceRing(selected: Boolean, c: GlassColors, shape: Shape): Modifier =
    border(if (selected) 2.dp else 0.5.dp, if (selected) c.tint else c.separator, shape)

@Composable
private fun LookChoice(label: String, selected: Boolean, darkPreview: Boolean, onClick: () -> Unit) {
    val c = Glass.colors
    val shape = RoundedCornerShape(14.dp)
    PreviewChoice(label, selected, onClick) {
        Box(
            Modifier
                .size(width = 66.dp, height = 124.dp)
                .choiceRing(selected, c, shape)
                .padding(if (selected) 2.dp else 0.5.dp)
                .clip(shape)
                .background(if (darkPreview) Color.Black else Color(0xFFF2F2F7)),
        ) {
            val bar = if (darkPreview) Color.White.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.75f)
            val card = if (darkPreview) Color(0xFF1C1C1E) else Color.White
            val glass = if (darkPreview) Color(0xCC505054) else Color(0xE6FFFFFF)
            Bit(0.12f, 0.12f, 0.46f, 0.05f, bar, 3.dp)
            Bit(0.09f, 0.26f, 0.82f, 0.24f, card, 8.dp)
            Bit(0.09f, 0.55f, 0.82f, 0.16f, card, 8.dp)
            Bit(0.09f, 0.86f, 0.64f, 0.09f, glass, 10.dp)
            Bit(0.75f, 0.86f, 0.16f, 0.09f, glass, 10.dp)
        }
    }
}

@Composable
private fun BoxScope.Bit(x: Float, y: Float, w: Float, h: Float, color: Color, radius: Dp) {
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                drawRoundRect(
                    color,
                    topLeft = androidx.compose.ui.geometry.Offset(size.width * x, size.height * y),
                    size = androidx.compose.ui.geometry.Size(size.width * w, size.height * h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius.toPx()),
                )
            },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlassLookSection(settings: AppSettings, onUpdate: ((AppSettings) -> AppSettings) -> Unit) {
    val prefs = settings.glass
    val c = Glass.colors
    fun edit(change: (GlassPrefs) -> GlassPrefs) = onUpdate { it.copy(glass = change(it.glass)) }

    GlassSectionHeader(stringResource(Res.string.glass_design_name))
    GlassGroup {
        Row(
            Modifier.fillMaxWidth().selectableGroup().padding(start = 12.dp, end = 12.dp, top = 18.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally),
        ) {
            MaterialChoice(GlassMaterial.Clear, stringResource(Res.string.glass_clear), prefs.material == GlassMaterial.Clear) {
                edit { it.copy(material = GlassMaterial.Clear) }
            }
            MaterialChoice(GlassMaterial.Tinted, stringResource(Res.string.glass_tinted), prefs.material == GlassMaterial.Tinted) {
                edit { it.copy(material = GlassMaterial.Tinted) }
            }
        }
    }
    GlassFooter(stringResource(Res.string.glass_material_foot))

    GlassSectionHeader(stringResource(Res.string.glass_connect_control))
    GlassGroup {
        Row(
            Modifier.fillMaxWidth().selectableGroup().padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GlassConnect.entries.forEach { kind ->
                ControlChoice(
                    kind = kind,
                    label = stringResource(
                        when (kind) {
                            GlassConnect.Z -> Res.string.glass_control_z
                            GlassConnect.Button -> Res.string.glass_control_button
                            GlassConnect.Switch -> Res.string.glass_control_switch
                            GlassConnect.Slider -> Res.string.glass_control_slider
                        },
                    ),
                    selected = prefs.connect == kind,
                    modifier = Modifier.weight(1f),
                ) { edit { it.copy(connect = kind) } }
            }
        }
    }

    Spacer(Modifier.height(24.dp))
    GlassGroup {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp)) {
            Text(stringResource(Res.string.glass_accent), style = GlassType.body(), color = c.label)
            Spacer(Modifier.height(12.dp))
            FlowRow(
                Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(13.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                GlassTint.entries.forEach { tint ->
                    val color = IosColors.tint(tint, c.dark)
                    val on = prefs.tint == tint
                    val name = stringResource(tintName(tint))
                    Box(
                        Modifier
                            .size(34.dp)
                            .then(if (on) Modifier.border(2.5.dp, color, CircleShape) else Modifier)
                            .padding(5.dp)
                            .clip(CircleShape)
                            .background(color)
                            .semantics { contentDescription = name }
                            .selectable(on, remember { MutableInteractionSource() }, null, true, Role.RadioButton) {
                                edit { it.copy(tint = tint) }
                            },
                    )
                }
            }
        }
        GlassListRow(
            title = stringResource(Res.string.glass_background),
            options = GlassWallpaper.entries.map { it to stringResource(glassWallpaperName(it)) },
            selected = prefs.wallpaper,
            enabled = true,
            onSelected = { v -> edit { it.copy(wallpaper = v) } },
        )
    }

    Spacer(Modifier.height(24.dp))
    GlassGroup {
        GlassSwitchRow(
            title = stringResource(Res.string.bento_detect_location_title),
            checked = settings.detectServerLocation,
            onCheckedChange = { v -> onUpdate { it.copy(detectServerLocation = v) } },
        )
        GlassSwitchRow(
            title = stringResource(Res.string.glass_lists),
            checked = prefs.glassLists,
            enabled = !prefs.reduceTransparency,
            onCheckedChange = { v -> edit { it.copy(glassLists = v) } },
        )
        GlassSwitchRow(
            title = stringResource(Res.string.glass_shrink),
            checked = prefs.shrinkTabBar,
            onCheckedChange = { v -> edit { it.copy(shrinkTabBar = v) } },
        )
        if (!LocalPlatform.current.isComputer) {
            GlassSwitchRow(
                title = stringResource(Res.string.glass_motion),
                subtitle = stringResource(Res.string.glass_motion_sub),
                checked = prefs.motionHighlights,
                enabled = !prefs.reduceTransparency,
                onCheckedChange = { v -> edit { it.copy(motionHighlights = v) } },
            )
        }
        GlassSwitchRow(
            title = stringResource(Res.string.glass_reduce),
            checked = prefs.reduceTransparency,
            onCheckedChange = { v -> edit { it.copy(reduceTransparency = v) } },
        )
    }
    if (!LocalPlatform.current.isComputer) {
        GlassFooter(stringResource(Res.string.glass_render_foot))
    }

    GlassSectionHeader(stringResource(Res.string.glass_home_header))
    GlassGroup {
        GlassSwitchRow(
            title = stringResource(Res.string.glass_home_speed),
            checked = prefs.homeSpeed,
            onCheckedChange = { v -> edit { it.copy(homeSpeed = v) } },
        )
        GlassSwitchRow(
            title = stringResource(Res.string.glass_home_timer),
            checked = prefs.homeTimer,
            onCheckedChange = { v -> edit { it.copy(homeTimer = v) } },
        )
        GlassSwitchRow(
            title = stringResource(Res.string.glass_home_card),
            checked = prefs.homeCard,
            onCheckedChange = { v -> edit { it.copy(homeCard = v) } },
        )
    }
}

private val Photo = Brush.linearGradient(
    0f to Color(0xFFFF8D28),
    0.35f to Color(0xFFFF2D55),
    0.68f to Color(0xFF6155F5),
    1f to Color(0xFF00C3D0),
)

@Composable
private fun MaterialChoice(kind: GlassMaterial, label: String, selected: Boolean, onClick: () -> Unit) {
    val c = Glass.colors
    val shape = RoundedCornerShape(16.dp)
    val tinted = kind == GlassMaterial.Tinted
    PreviewChoice(label, selected, onClick) {
        Box(
            Modifier
                .size(width = 132.dp, height = 96.dp)
                .choiceRing(selected, c, shape)
                .padding(if (selected) 2.dp else 0.5.dp)
                .clip(shape)
                .background(Photo)
                .drawBehind {
                    val stripe = 5.dp.toPx()
                    val gap = 13.dp.toPx()
                    var x = 0f
                    while (x < size.width) {
                        drawRect(Color.White.copy(alpha = 0.28f), topLeft = androidx.compose.ui.geometry.Offset(x, 0f), size = androidx.compose.ui.geometry.Size(stripe, size.height))
                        x += gap
                    }
                },
        ) {
            val capsule = GlassShapes.capsule
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(10.dp)
                    .fillMaxWidth()
                    .height(32.dp)
                    .shadow(4.dp, capsule, clip = false)
                    .clip(capsule)
                    .background(if (tinted) Color(0xD1FFFFFF) else Color(0x24FFFFFF))
                    .glassHighlight(capsule, Color.White.copy(alpha = 0.95f))
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val ink = if (tinted) Color.Black.copy(alpha = 0.72f) else Color.White
                Box(Modifier.size(12.dp).clip(RoundedCornerShape(4.dp)).background(ink))
                Box(Modifier.size(12.dp).clip(CircleShape).background(ink))
                Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(ink))
            }
        }
    }
}

@Composable
private fun ControlChoice(kind: GlassConnect, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Glass.colors
    val shape = RoundedCornerShape(14.dp)
    val glassBit = Color.White.copy(alpha = 0.42f)
    PreviewChoice(label, selected, onClick, modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(86.dp)
                .choiceRing(selected, c, shape)
                .padding(if (selected) 2.dp else 0.5.dp)
                .clip(shape)
                .background(if (c.dark) Color(0xFF15171B) else Color(0xFFEEF1F4)),
            contentAlignment = Alignment.Center,
        ) {
            when (kind) {
                GlassConnect.Z -> GlassZMark(
                    if (c.dark) Color.White.copy(alpha = 0.85f) else Color(0xFF9EACC8),
                    Modifier.size(width = 44.dp, height = 40.dp),
                )
                GlassConnect.Button -> Box(
                    Modifier
                        .padding(horizontal = 10.dp)
                        .fillMaxWidth()
                        .height(22.dp)
                        .clip(GlassShapes.capsule)
                        .background(c.tint),
                )
                GlassConnect.Switch -> Row(
                    Modifier
                        .padding(horizontal = 7.dp)
                        .fillMaxWidth()
                        .height(32.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(glassBit)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(c.green.copy(alpha = 0.85f)))
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(width = 24.dp, height = 14.dp).clip(GlassShapes.capsule).background(c.green))
                }
                GlassConnect.Slider -> Row(
                    Modifier
                        .padding(horizontal = 7.dp)
                        .fillMaxWidth()
                        .height(26.dp)
                        .clip(GlassShapes.capsule)
                        .background(glassBit)
                        .padding(horizontal = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(20.dp).shadow(2.dp, CircleShape, clip = false).clip(CircleShape).background(Color.White))
                    Spacer(Modifier.width(7.dp))
                    Box(Modifier.weight(1f).padding(end = 8.dp).height(4.dp).clip(GlassShapes.capsule).background(c.label3))
                }
            }
        }
    }
}

@Composable
fun GlassDesignPreview() {
    val dark = Glass.colors.dark
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawGlassWallpaper(GlassWallpaper.Live, dark) },
    ) {
        val ink = if (dark) Color.White.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.75f)
        val bit = if (dark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.6f)
        Bit(0.12f, 0.09f, 0.5f, 0.05f, ink, 3.dp)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            GlassZMark(
                if (dark) Color.White.copy(alpha = 0.8f) else Color(0xFFB9C4D8),
                Modifier.padding(bottom = 18.dp).size(width = 34.dp, height = 30.dp),
            )
        }
        Bit(0.08f, 0.64f, 0.84f, 0.1f, bit, 10.dp)
        Bit(0.08f, 0.86f, 0.6f, 0.09f, bit, 12.dp)
        Bit(0.74f, 0.86f, 0.18f, 0.09f, bit, 12.dp)
    }
}
