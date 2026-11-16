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

    var status by remember {
        mutableStateOf(runCatching { GhostEngine.debugStatus() }.getOrNull())
    }
    LaunchedEffect(Unit) {
        while (true) {
            status = runCatching { GhostEngine.debugStatus() }.getOrNull()
            delay(2000)
        }
    }

    SettingsPageScaffold(
        title = "GHOST MOD",
        subtitle = "ضد DPI – فقط بعد از اتصال تونل فعال می‌شود",
        contentPadding = contentPadding,
        modifier = modifier,
        art = SettingsArts.Ghost,
    ) {
        SettingsGroup(title = "فعال‌سازی") {
            SettingsSwitchRow(
                title = "فعال‌سازی GHOST",
                summary = "با Connect به تونل کمک می‌کند؛ خودکار وصل نمی‌شود",
                checked = ghost.enabled,
                onCheckedChange = { v ->
                    onUpdate { it.copy(ghost = it.ghost.copy(enabled = v)) }
                },
            )
            SettingsSwitchRow(
                title = "AUTO",
                summary = "تشخیص وای‌فای/داده + استراتژی خودکار",
                checked = ghost.autoMode,
                onCheckedChange = { v ->
                    onUpdate { it.copy(ghost = it.ghost.copy(autoMode = v)) }
                },
            )
            SettingsSwitchRow(
                title = "DARK GHOST",
                summary = "حالت فوق‌پیشرفته: هوش شبکه + Morphing + تشدید ضد DPI",
                checked = ghost.darkGhostEnabled,
                onCheckedChange = { v ->
                    onUpdate { it.copy(ghost = it.ghost.copy(darkGhostEnabled = v)) }
                },
            )
        }

        if (!ghost.enabled) return@SettingsPageScaffold

        SettingsGroup(title = "وضعیت زنده") {
            SettingsSegment {
                Column(modifier = Modifier.padding(16.dp)) {
                    val s = status
                    if (s == null) {
                        Text("در حال بارگذاری…", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(
                            text = when {
                                s.running && s.darkGhost -> "● DARK GHOST فعال – کمک به تونل"
                                s.running -> "● در حال کمک به تونل"
                                s.enabled && !s.tunnelConnected -> "○ آماده – منتظر اتصال"
                                else -> "○ متوقف"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = if (s.running) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "تونل: ${if (s.tunnelConnected) "وصل" else "قطع"}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "استراتژی: ${s.strategy} | فاز: ${s.phase}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (s.darkGhost) {
                            Text(
                                text = "Intel: ${s.intelSummary}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (s.autoMode) {
                            Text(
                                text = "شبکه: ${s.networkLabel}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(text = s.autoReason, style = MaterialTheme.typography.bodySmall)
                        }
                        if (s.underPressure) {
                            Text(
                                text = "⚠ فشار شبکه – نویز افزایش یافته",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Text(
                            text = "چرخه: ${s.cycles} | درخواست: ${s.requests} | موفق: ${s.successes}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        val err = s.lastError
                        if (err != null) {
                            Text(
                                text = "خطا: $err",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }

        if (ghost.darkGhostEnabled) {
            SettingsGroup(title = "DARK GHOST") {
                SettingsSwitchRow(
                    title = "Network Intelligence",
                    summary = "پروب امن و طبقه‌بندی شرایط شبکه (بدون ادعای DPI قطعی)",
                    checked = ghost.darkIntelligenceEnabled,
                    onCheckedChange = { v ->
                        onUpdate { it.copy(ghost = it.ghost.copy(darkIntelligenceEnabled = v)) }
                    },
                )
                SettingsSwitchRow(
                    title = "Traffic Morphing",
                    summary = "jitter / disorder / فازهای اضافی D0–D4",
                    checked = ghost.darkMorphingEnabled,
                    onCheckedChange = { v ->
                        onUpdate { it.copy(ghost = it.ghost.copy(darkMorphingEnabled = v)) }
                    },
                )
                SettingsSegment {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            text = "شدت DARK: ${ghost.darkIntensity}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Slider(
                            value = ghost.darkIntensity.toFloat().coerceIn(1f, 5f),
                            onValueChange = { v ->
                                onUpdate {
                                    it.copy(ghost = it.ghost.copy(darkIntensity = v.toInt().coerceIn(1, 5)))
                                }
                            },
                            valueRange = 1f..5f,
                            steps = 3,
                        )
                        Text(
                            text = "فاصله پروب Intel: ${ghost.darkProbeIntervalSec}s",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Slider(
                            value = ghost.darkProbeIntervalSec.toFloat().coerceIn(20f, 180f),
                            onValueChange = { v ->
                                onUpdate {
                                    it.copy(
                                        ghost = it.ghost.copy(
                                            darkProbeIntervalSec = v.toInt().coerceIn(20, 180),
                                        ),
                                    )
                                }
                            },
                            valueRange = 20f..180f,
                            steps = 15,
                        )
                        Text(
                            text = "DARK فقط پس از Connect کار می‌کند و موتورهای VPN را تغییر نمی‌دهد.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (ghost.autoMode && !ghost.darkGhostEnabled) {
            SettingsGroup(title = "AUTO فعال") {
                SettingsSegment {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "تنظیمات دستی مخفی است. AUTO بر اساس شبکه و فشار، پارامترها را می‌چیند.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            return@SettingsPageScaffold
        }

        if (!ghost.autoMode) {
            SettingsGroup(title = "استراتژی") {
                StrategyOption("Auto", "auto", ghost.strategy) { key ->
                    onUpdate { it.copy(ghost = it.ghost.copy(strategy = key)) }
                }
                StrategyOption("Balanced", "balanced", ghost.strategy) { key ->
                    onUpdate { it.copy(ghost = it.ghost.copy(strategy = key)) }
                }
                StrategyOption("Aggressive", "aggressive", ghost.strategy) { key ->
                    onUpdate { it.copy(ghost = it.ghost.copy(strategy = key)) }
                }
                StrategyOption("Max Noise", "max", ghost.strategy) { key ->
                    onUpdate { it.copy(ghost = it.ghost.copy(strategy = key)) }
                }
                StrategyOption("Cellular Stealth", "stealth", ghost.strategy) { key ->
                    onUpdate { it.copy(ghost = it.ghost.copy(strategy = key)) }
                }
            }

            SettingsGroup(title = "پیشرفته") {
                SettingsSwitchRow(
                    title = "Multi-Phase",
                    summary = "P0 Fake → P1 Mimic → P2 Residual",
                    checked = ghost.multiphaseEnabled,
                    onCheckedChange = { v ->
                        onUpdate { it.copy(ghost = it.ghost.copy(multiphaseEnabled = v)) }
                    },
                )
                SettingsSwitchRow(
                    title = "Fake Flood",
                    summary = "پروب جعلی قبل از mimic",
                    checked = ghost.fakeFloodEnabled,
                    onCheckedChange = { v ->
                        onUpdate { it.copy(ghost = it.ghost.copy(fakeFloodEnabled = v)) }
                    },
                )
                SettingsSwitchRow(
                    title = "Request Disorder",
                    summary = "به‌هم‌ریختن ترتیب درخواست‌ها",
                    checked = ghost.requestDisorder,
                    onCheckedChange = { v ->
                        onUpdate { it.copy(ghost = it.ghost.copy(requestDisorder = v)) }
                    },
                )
                SettingsSwitchRow(
                    title = "Smart Decoy",
                    summary = "موتور ترافیک جعلی",
                    checked = ghost.decoyEngineEnabled,
                    onCheckedChange = { v ->
                        onUpdate { it.copy(ghost = it.ghost.copy(decoyEngineEnabled = v)) }
                    },
                )
                SettingsSwitchRow(
                    title = "Burst",
                    summary = "موج نویز",
                    checked = ghost.burstModeEnabled,
                    onCheckedChange = { v ->
                        onUpdate { it.copy(ghost = it.ghost.copy(burstModeEnabled = v)) }
                    },
                )
            }
        }
    }
}

@Composable
private fun StrategyOption(
    label: String,
    key: String,
    current: String,
    onSelect: (String) -> Unit,
) {
    val selected = current.equals(key, ignoreCase = true) ||
        (key == "max" && current.equals("maxnoise", ignoreCase = true))
    SettingsSwitchRow(
        title = label,
        summary = key,
        checked = selected,
        onCheckedChange = { on -> if (on) onSelect(key) },
    )
}
