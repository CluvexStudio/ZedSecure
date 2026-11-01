@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package dev.cluvex.zedsecure.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cluvex.zedsecure.domain.model.UiFont
import dev.cluvex.zedsecure.domain.model.UiScale
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.font_auto
import dev.cluvex.zedsecure.shared.resources.font_estedad
import dev.cluvex.zedsecure.shared.resources.font_system
import dev.cluvex.zedsecure.shared.resources.font_title
import dev.cluvex.zedsecure.shared.resources.font_vazirmatn
import dev.cluvex.zedsecure.shared.resources.scale_apply
import dev.cluvex.zedsecure.shared.resources.scale_custom
import dev.cluvex.zedsecure.shared.resources.scale_note
import dev.cluvex.zedsecure.shared.resources.scale_title
import dev.cluvex.zedsecure.ui.theme.fontFamilyOf
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private val ScalePresets = listOf(0.85f, 1f, 1.15f, 1.3f)

@Composable
fun FontPicker(selected: UiFont, onSelect: (UiFont) -> Unit) {
    SettingsSegment {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(Res.string.font_title), style = MaterialTheme.typography.bodyLargeEmphasized)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 3,
            ) {
                UiFont.entries.forEach { font ->
                    FontCard(font, font == selected, Modifier.weight(1f)) { onSelect(font) }
                }
            }
        }
    }
}

@Composable
private fun fontLabel(font: UiFont): String = when (font) {
    UiFont.Auto -> stringResource(Res.string.font_auto)
    UiFont.GoogleSans -> "Google Sans"
    UiFont.Inter -> "Inter"
    UiFont.Vazirmatn -> stringResource(Res.string.font_vazirmatn)
    UiFont.Estedad -> stringResource(Res.string.font_estedad)
    UiFont.Nunito -> "Nunito"
    UiFont.System -> stringResource(Res.string.font_system)
}

@Composable
private fun FontCard(font: UiFont, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val container = if (active) scheme.primary else scheme.surfaceContainerHigh
    val content = if (active) scheme.onPrimary else scheme.onSurface
    val family = fontFamilyOf(font) ?: MaterialTheme.typography.bodyLarge.fontFamily
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .height(88.dp)
            .clip(shape)
            .background(container)
            .then(if (active) Modifier else Modifier.border(1.dp, scheme.outlineVariant, shape))
            .semantics { this.selected = active }
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Aa ابپ", fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 21.sp, color = content, maxLines = 1)
        Text(fontLabel(font), style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1)
    }
}

@Composable
fun ScaleRow(value: Float, onChange: (Float) -> Unit) {
    val focus = LocalFocusManager.current
    var draft by remember(value) { mutableFloatStateOf(value) }
    var custom by remember(value) { mutableStateOf((value * 100).roundToInt().toString()) }
    val percent = (draft * 100).roundToInt()
    SettingsSegment {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.scale_title), style = MaterialTheme.typography.bodyLargeEmphasized, modifier = Modifier.weight(1f))
                Text("$percent%", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = draft.coerceIn(0.7f, 1.6f),
                onValueChange = { draft = (it * 20).roundToInt() / 20f },
                onValueChangeFinished = { onChange(UiScale.clamp(draft)) },
                valueRange = 0.7f..1.6f,
                steps = 17,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ScalePresets.forEach { preset ->
                    val on = kotlin.math.abs(value - preset) < 0.001f
                    val shape = RoundedCornerShape(18.dp)
                    Text(
                        "${(preset * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(shape)
                            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable(role = Role.RadioButton) { onChange(preset) }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val apply = {
                    custom.trim().toIntOrNull()?.let { onChange(UiScale.clamp(it / 100f)) }
                    focus.clearFocus()
                }
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it.filter(Char::isDigit).take(3) },
                    label = { Text(stringResource(Res.string.scale_custom)) },
                    suffix = { Text("%") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { apply() }),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(Res.string.scale_apply),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable(role = Role.Button) { apply() }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                )
            }
            Text(
                stringResource(Res.string.scale_note, "${(UiScale.MIN * 100).roundToInt()}%", "${(UiScale.MAX * 100).roundToInt()}%"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
