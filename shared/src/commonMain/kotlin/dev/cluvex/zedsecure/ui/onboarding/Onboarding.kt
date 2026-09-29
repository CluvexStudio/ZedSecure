package dev.cluvex.zedsecure.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.cluvex.zedsecure.domain.model.AppLanguage
import dev.cluvex.zedsecure.domain.model.AppSettings
import dev.cluvex.zedsecure.domain.model.ThemeMode
import dev.cluvex.zedsecure.domain.model.TrafficTileSize
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.*
import dev.cluvex.zedsecure.ui.theme.ThemeProfiles
import dev.cluvex.zedsecure.ui.theme.applyThemeProfile
import org.jetbrains.compose.resources.stringResource

const val ONBOARDING_VERSION = 1

@Composable
fun OnboardingFlow(
    settings: AppSettings,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onLanguage: (AppLanguage) -> Unit,
    onStartTour: () -> Unit,
    onFinish: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val last = 4

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 44.dp, bottom = 24.dp),
        ) {
            StepDots(step, last + 1)
            Spacer(Modifier.height(22.dp))

            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "onboarding-step",
                modifier = Modifier.weight(1f),
            ) { current ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    when (current) {
                        0 -> ThemeModeStep(settings, onUpdate)
                        1 -> ThemeProfileStep(settings, onUpdate)
                        2 -> LanguageStep(settings, onLanguage)
                        3 -> TrafficStep(settings, onUpdate)
                        else -> TourOfferStep()
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (step > 0) {
                    TextButton(onClick = { step-- }) { Text(stringResource(Res.string.onb_back)) }
                }
                Spacer(Modifier.weight(1f))
                if (step == last) {
                    TextButton(onClick = onFinish) { Text(stringResource(Res.string.onb_skip_tour)) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onStartTour,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.height(52.dp),
                    ) {
                        Text(stringResource(Res.string.onb_start_tour), fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Button(
                        onClick = { step++ },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.height(52.dp),
                    ) {
                        Text(stringResource(Res.string.onb_next), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepDots(current: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(total) { i ->
            val width by animateDpAsState(if (i == current) 26.dp else 8.dp, label = "onb-dot")
            val color by animateColorAsState(
                if (i <= current) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHighest,
                label = "onb-dot-color",
            )
            Box(Modifier.height(8.dp).width(width).clip(CircleShape).background(color))
        }
    }
}

@Composable
private fun StepHeader(title: String, subtitle: String) {
    Text(
        title,
        style = MaterialTheme.typography.displaySmallEmphasized,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        subtitle,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun ChoiceCard(
    title: String,
    description: String? = null,
    selected: Boolean,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "choice-color",
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = container,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(22.dp))
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                )
                if (description != null) {
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun ThemeModeStep(settings: AppSettings, onUpdate: ((AppSettings) -> AppSettings) -> Unit) {
    StepHeader(stringResource(Res.string.onb_theme_title), stringResource(Res.string.onb_theme_body))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(
            ThemeMode.Dark to stringResource(Res.string.settings_theme_dark),
            ThemeMode.Light to stringResource(Res.string.settings_theme_light),
            ThemeMode.System to stringResource(Res.string.settings_theme_system),
        ).forEach { (mode, label) ->
            ChoiceCard(
                title = label,
                selected = settings.themeMode == mode,
                onClick = { onUpdate { it.copy(themeMode = mode) } },
            )
        }
    }
}

@Composable
private fun ThemeProfileStep(settings: AppSettings, onUpdate: ((AppSettings) -> AppSettings) -> Unit) {
    StepHeader(stringResource(Res.string.onb_preset_title), stringResource(Res.string.onb_preset_body))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ChoiceCard(
            title = stringResource(Res.string.onb_preset_stock),
            selected = settings.themeProfileId == null,
            onClick = { onUpdate { it.copy(themeProfileId = null) } },
        )
        ThemeProfiles.forEach { profile ->
            ChoiceCard(
                title = profile.name,
                selected = settings.themeProfileId == profile.id,
                onClick = { onUpdate { it.applyThemeProfile(profile) } },
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        profile.swatches.forEach { swatch ->
                            Box(Modifier.size(22.dp).clip(CircleShape).background(swatch))
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun LanguageStep(settings: AppSettings, onLanguage: (AppLanguage) -> Unit) {
    StepHeader(stringResource(Res.string.onb_lang_title), stringResource(Res.string.onb_lang_body))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(
            AppLanguage.System to stringResource(Res.string.settings_language_system),
            AppLanguage.English to "English",
            AppLanguage.Persian to "فارسی",
            AppLanguage.Chinese to "中文",
            AppLanguage.Russian to "Русский",
        ).forEach { (lang, label) ->
            ChoiceCard(
                title = label,
                selected = settings.language == lang,
                onClick = { if (settings.language != lang) onLanguage(lang) },
            )
        }
    }
}

@Composable
private fun TrafficStep(settings: AppSettings, onUpdate: ((AppSettings) -> AppSettings) -> Unit) {
    StepHeader(stringResource(Res.string.onb_traffic_title), stringResource(Res.string.onb_traffic_body))
    ChoiceCard(
        title = stringResource(Res.string.title_pref_traffic_tiles_top),
        description = stringResource(Res.string.summary_pref_traffic_tiles_top),
        selected = settings.trafficTilesAboveHero,
        onClick = { onUpdate { it.copy(trafficTilesAboveHero = !it.trafficTilesAboveHero) } },
        trailing = {
            Switch(
                checked = settings.trafficTilesAboveHero,
                onCheckedChange = { v -> onUpdate { it.copy(trafficTilesAboveHero = v) } },
            )
        },
    )
    Spacer(Modifier.height(18.dp))
    Text(
        stringResource(Res.string.title_pref_traffic_tile_size),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(Modifier.height(10.dp))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(
            TrafficTileSize.Small to stringResource(Res.string.tile_size_small),
            TrafficTileSize.Normal to stringResource(Res.string.tile_size_normal),
            TrafficTileSize.Large to stringResource(Res.string.tile_size_large),
        ).forEach { (size, label) ->
            ChoiceCard(
                title = label,
                selected = settings.trafficTileSize == size,
                onClick = { onUpdate { it.copy(trafficTileSize = size) } },
                trailing = { TilePreview(size) },
            )
        }
    }
}

@Composable
private fun TilePreview(size: TrafficTileSize) {
    val height = when (size) {
        TrafficTileSize.Small -> 26.dp
        TrafficTileSize.Normal -> 34.dp
        TrafficTileSize.Large -> 42.dp
    }
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(2) { i ->
            Box(
                Modifier
                    .width(30.dp)
                    .height(height)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (i == 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f),
                    ),
            )
        }
    }
}

@Composable
private fun TourOfferStep() {
    StepHeader(stringResource(Res.string.onb_tour_title), stringResource(Res.string.onb_tour_body))
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Z",
                style = MaterialTheme.typography.displayLargeEmphasized,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(Res.string.onb_tour_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
