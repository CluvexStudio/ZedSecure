package dev.cluvex.zedsecure.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.cluvex.zedsecure.core.ghost.GhostEngine
import dev.cluvex.zedsecure.domain.model.AppSettings
import kotlinx.coroutines.delay

/**
 * GHOST settings – must use SettingsPageScaffold like other pages
 * (no extra outer scroll) and SettingsArts.Ghost (unique sharedElement key).
 */
@Composable
fun GhostSettingsPage(
    settings: AppSettings,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
) {
    val ghost = settings.ghost

    LaunchedEffect(ghost) {
        runCatching { GhostEngine.updateSettings(ghost) }
    }

    var status by remember { mutableStateOf(runCatching { GhostEngine.debugStatus() }.getOrNull()) }
    LaunchedEffect(Unit) {
        while (true) {
            status = runCatching { GhostEngine.debugStatus() }.getOrNull()
            delay(2000)
        }
    }

    SettingsPageScaffold(
        title = "GHOST MOD",
        art = SettingsArts.Ghost,
        subtitle = "ضد DPI – فقط بعد از اتصال تونل فعال می‌شود",
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        SettingsGroup("فعال‌سازی") {
            SettingsSwitchRow(
                title = "فعال‌سازی GHOST",
                summary = "با Connect به تونل کمک می‌کند؛ خودکار وصل نمی‌شود",
                checked = ghost.enabled,
                onCheckedChange = { v -> onUpdate { it.copy(ghost = it.ghost.copy(enabled = v)) } },
            )
            SettingsSwitchRow(
                title = "AUTO",
                summary = "تشخیص وای‌فای/داده + استراتژی خودکار",
                checked = ghost.autoMode,
                onCheckedChange = { v -> onUpdate { it.copy(ghost = it.ghost.copy(autoMode = v)) } },
            )
        }

        if (ghost.enabled) {
            SettingsGroup("وضعیت زنده") {
                SettingsSegment {
                    Column(Modifier = Modifier.padding(16.dp)) {
                        val s = status
                        if (s == null) {
                            Text("در حال بارگذاری…", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Text(
                                when {
                                    s.running -> "● در حال کمک به تونل"
                                    s.enabled && !s.tunnelConnected -> "○ آماده – منتظر اتصال"
                                    else -> "○ متوقف"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = if (s.running) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text("تونل: ${if (s.tunnelConnected) "وصل" else "قطع"}",
                                style = MaterialTheme.typography.bodyMedium)
                            Text("استراتژی: ${s.strategy} | فاز: ${s.phase}",
                                style = MaterialTheme.typography.bodySmall)
                            if (s.autoMode) {
                                Text("شبکه: ${s.networkLabel}", style = MaterialTheme.typography.bodyMedium)
                                Text(s.autoReason, style = MaterialTheme.typography.bodySmall)
                            }
                            if (s.underPressure) {
                                Text(
                                    "⚠ فشار DPI – نویز افزایش یافته",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            Text(
                                "چرخه: ${s.cycles} | درخواست: ${s.requests} | موفق: ${s.successes}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            s.lastError?.let {
                                Text("خطا: $it", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            if (!ghost.autoMode) {
                SettingsGroup("استراتژی") {
                    StrategyRow(
                        current = ghost.strategy,
                        onSelect = { key -> onUpdate { it.copy(ghost = it.ghost.copy(strategy = key)) } },
                    )
                }

                SettingsGroup("پیشرفته") {
                    SettingsSwitchRow(
                        title = "Multi-Phase",
                        summary = "P0 Fake → P1 Mimic → P2 Residual",
                        checked = ghost.multiphaseEnabled,
                        onCheckedChange = { v -> onUpdate { it.copy(ghost = it.ghost.copy(multiphaseEnabled = v)) } },
                    )
                    SettingsSwitchRow(
                        title = "Fake Flood",
                        summary = "پروب جعلی قبل از mimic",
                        checked = ghost.fakeFloodEnabled,
                        onCheckedChange = { v -> onUpdate { it.copy(ghost = it.ghost.copy(fakeFloodEnabled = v)) } },
                    )
                    SettingsSwitchRow(
                        title = "Request Disorder",
                        summary = "به‌هم‌ریختن ترتیب درخواست‌ها",
                        checked = ghost.requestDisorder,
                        onCheckedChange = { v -> onUpdate { it.copy(ghost = it.ghost.copy(requestDisorder = v)) } },
                    )
                    SettingsSwitchRow(
                        title = "Smart Decoy",
                        summary = "موتور ترافیک جعلی",
                        checked = ghost.decoyEngineEnabled,
                        onCheckedChange = { v -> onUpdate { it.copy(ghost = it.ghost.copy(decoyEngineEnabled = v)) } },
                    )
                    SettingsSwitchRow(
                        title = "Multi-App",
                        summary = "چند اپ همزمان",
                        checked = ghost.multiAppConcurrent,
                        onCheckedChange = { v -> onUpdate { it.copy(ghost = it.ghost.copy(multiAppConcurrent = v)) } },
                    )
                    SettingsSwitchRow(
                        title = "Burst",
                        summary = "موج نویز",
                        checked = ghost.burstModeEnabled,
                        onCheckedChange = { v -> onUpdate { it.copy(ghost = it.ghost.copy(burstModeEnabled = v)) } },
                    )
                    Text(
                        "حجم: ${ghost.decoyVolumePercent}%",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    Slider(
                        value = ghost.decoyVolumePercent.toFloat().coerceIn(10f, 85f),
                        onValueChange = { v ->
                            onUpdate { it.copy(ghost = it.ghost.copy(decoyVolumePercent = v.toInt().coerceIn(10, 85))) }
                        },
                        valueRange = 10f..85f,
                        steps = 14,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            } else {
                SettingsGroup("AUTO فعال") {
                    SettingsSegment {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "تنظیمات دستی مخفی است. AUTO بر اساس شبکه و فشار DPI همه چیز را می‌چیند.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StrategyRow(current: String, onSelect: (String) -> Unit) {
    val items = listOf(
        "auto" to "Auto",
        "balanced" to "Balanced",
        "aggressive" to "Aggressive",
        "max" to "Max Noise",
        "stealth" to "Cellular Stealth",
    )
    items.forEach { (key, label) ->
        val selected = current.equals(key, ignoreCase = true) ||
            (key == "max" && current.equals("maxnoise", true))
        SettingsSwitchRow(
            title = label,
            summary = key,
            checked = selected,
            onCheckedChange = { on -> if (on) onSelect(key) },
        )
    }
}
