package dev.cluvex.zedsecure.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import dev.cluvex.zedsecure.ui.platform.appVerticalScroll
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
        GhostEngine.updateSettings(ghost)
    }

    var status by remember { mutableStateOf(GhostEngine.debugStatus()) }
    LaunchedEffect(Unit) {
        while (true) {
            status = GhostEngine.debugStatus()
            delay(2000)
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .appVerticalScroll(rememberScrollState(), contentPadding)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SettingsPageScaffold(
            title = "GHOST MOD",
            art = SettingsArts.Monitor,
            subtitle = "ضد DPI حرفه‌ای چندفازی (الهام از zapret) – فقط بعد از اتصال",
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier,
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
                    summary = "تشخیص وای‌فای/داده + انتخاب خودکار استراتژی",
                    checked = ghost.autoMode,
                    onCheckedChange = { v -> onUpdate { it.copy(ghost = it.ghost.copy(autoMode = v)) } },
                )
            }

            if (ghost.enabled) {
                SettingsGroup("وضعیت زنده") {
                    SettingsSegment {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                when {
                                    status.running -> "● در حال کمک به تونل"
                                    status.enabled && !status.tunnelConnected -> "○ آماده – منتظر اتصال"
                                    else -> "○ متوقف"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = if (status.running) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text("تونل: ${if (status.tunnelConnected) "وصل" else "قطع"}",
                                style = MaterialTheme.typography.bodyMedium)
                            Text("استراتژی: ${status.strategy}", style = MaterialTheme.typography.bodyMedium)
                            Text("فاز: ${status.phase}", style = MaterialTheme.typography.bodySmall)
                            if (status.autoMode) {
                                Text("شبکه: ${status.networkLabel}", style = MaterialTheme.typography.bodyMedium)
                                Text(status.autoReason, style = MaterialTheme.typography.bodySmall)
                            }
                            if (status.underPressure) {
                                Text("⚠ فشار DPI – نویز افزایش یافته",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error)
                            }
                            Text("چرخه: ${status.cycles} | درخواست: ${status.requests}",
                                style = MaterialTheme.typography.bodySmall)
                            Text("موفق: ${status.successes} | ناموفق: ${status.failures}",
                                style = MaterialTheme.typography.bodySmall)
                            Text("حجم مؤثر: ${status.decoyVolumePercent}% | ${status.lastProfile}",
                                style = MaterialTheme.typography.bodySmall)
                            status.lastError?.let {
                                Text("خطا: $it", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                if (!ghost.autoMode) {
                    SettingsGroup("استراتژی ضد DPI") {
                        StrategyRow(
                            current = ghost.strategy,
                            onSelect = { key -> onUpdate { it.copy(ghost = it.ghost.copy(strategy = key)) } },
                        )
                    }

                    SettingsGroup("حالت‌های پیشرفته (zapret-style)") {
                        SettingsSwitchRow(
                            title = "Multi-Phase",
                            summary = "P0 FakeFlood → P1 Mimic → P2 Residual",
                            checked = ghost.multiphaseEnabled,
                            onCheckedChange = { v -> onUpdate { it.copy(ghost = it.ghost.copy(multiphaseEnabled = v)) } },
                        )
                        SettingsSwitchRow(
                            title = "Fake Flood",
                            summary = "پروب‌های جعلی بی‌ضرر قبل از mimic اصلی",
                            checked = ghost.fakeFloodEnabled,
                            onCheckedChange = { v -> onUpdate { it.copy(ghost = it.ghost.copy(fakeFloodEnabled = v)) } },
                        )
                        SettingsSwitchRow(
                            title = "Request Disorder",
                            summary = "به‌هم‌ریختن ترتیب درخواست‌ها",
                            checked = ghost.requestDisorder,
                            onCheckedChange = { v -> onUpdate { it.copy(ghost = it.ghost.copy(requestDisorder = v)) } },
                        )
                    }

                    SettingsGroup("پروفایل و حجم") {
                        SettingsSwitchRow(
                            title = "App Profiles",
                            summary = "mimic اپ‌های ایرانی",
                            checked = ghost.appProfilesEnabled,
                            onCheckedChange = { v -> onUpdate { it.copy(ghost = it.ghost.copy(appProfilesEnabled = v)) } },
                        )
                        if (ghost.appProfilesEnabled) {
                            ProfileChipRow(
                                current = ghost.activeProfiles,
                                onChange = { new -> onUpdate { it.copy(ghost = it.ghost.copy(activeProfiles = new)) } },
                            )
                        }
                        SettingsSwitchRow(
                            title = "Smart Decoy Engine",
                            summary = "موتور ترافیک جعلی",
                            checked = ghost.decoyEngineEnabled,
                            onCheckedChange = { v -> onUpdate { it.copy(ghost = it.ghost.copy(decoyEngineEnabled = v)) } },
                        )
                        Text("حجم: ${ghost.decoyVolumePercent}%",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                        Slider(
                            value = ghost.decoyVolumePercent.toFloat(),
                            onValueChange = { v ->
                                onUpdate { it.copy(ghost = it.ghost.copy(decoyVolumePercent = v.toInt().coerceIn(10, 85))) }
                            },
                            valueRange = 10f..85f, steps = 14,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Text("فاصله: ${ghost.decoyIntervalSeconds}s",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                        Slider(
                            value = ghost.decoyIntervalSeconds.toFloat(),
                            onValueChange = { v ->
                                onUpdate { it.copy(ghost = it.ghost.copy(decoyIntervalSeconds = v.toInt().coerceIn(4, 60))) }
                            },
                            valueRange = 4f..60f, steps = 13,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }

                    SettingsGroup("Mimicry / Adaptive / Burst") {
                        SettingsSwitchRow(
                            title = "App-Layer Mimicry",
                            summary = "UA واقعی اپ‌ها",
                            checked = ghost.appLayerMimicryEnabled,
                            onCheckedChange = { v -> onUpdate { it.copy(ghost = it.ghost.copy(appLayerMimicryEnabled = v)) } },
                        )
                        SettingsSwitchRow(
                            title = "Multi-App",
                            summary = "چند اپ همزمان",
                            checked = ghost.multiAppConcurrent,
                            onCheckedChange = { v -> onUpdate { it.copy(ghost = it.ghost.copy(multiAppConcurrent = v)) } },
                        )
                        SettingsSwitchRow(
                            title = "Adaptive + Pressure",
                            summary = "افزایش نویز زیر فشار",
                            checked = ghost.adaptiveFallbackEnabled,
                            onCheckedChange = { v ->
                                onUpdate {
                                    it.copy(
                                        ghost = it.ghost.copy(
                                            adaptiveFallbackEnabled = v,
                                            pressureDetectionEnabled = v,
                                        )
                                    )
                                }
                            },
                        )
                        SettingsSwitchRow(
                            title = "Burst",
                            summary = "موج نویز",
                            checked = ghost.burstModeEnabled,
                            onCheckedChange = { v -> onUpdate { it.copy(ghost = it.ghost.copy(burstModeEnabled = v)) } },
                        )
                        if (ghost.burstModeEnabled) {
                            Text("شدت Burst: ${ghost.burstIntensity}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                            Slider(
                                value = ghost.burstIntensity.toFloat(),
                                onValueChange = { v ->
                                    onUpdate { it.copy(ghost = it.ghost.copy(burstIntensity = v.toInt().coerceIn(1, 5))) }
                                },
                                valueRange = 1f..5f, steps = 3,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                        Text("موازی: ${ghost.maxParallelRequests}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                        Slider(
                            value = ghost.maxParallelRequests.toFloat(),
                            onValueChange = { v ->
                                onUpdate { it.copy(ghost = it.ghost.copy(maxParallelRequests = v.toInt().coerceIn(1, 4))) }
                            },
                            valueRange = 1f..4f, steps = 2,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                } else {
                    SettingsGroup("AUTO فعال") {
                        SettingsSegment {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "استراتژی، حجم، MultiPhase، FakeFlood و پروفایل‌ها " +
                                        "بر اساس شبکه و فشار DPI خودکار تنظیم می‌شوند.",
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
}

@Composable
private fun StrategyRow(current: String, onSelect: (String) -> Unit) {
    val items = listOf(
        "auto" to "Auto (پیشنهادی)",
        "balanced" to "Balanced",
        "aggressive" to "Aggressive",
        "max" to "Max Noise",
        "stealth" to "Cellular Stealth",
    )
    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        items.forEach { (key, label) ->
            SettingsSwitchRow(
                title = label,
                summary = key,
                checked = current.equals(key, ignoreCase = true) ||
                    (key == "max" && current.equals("maxnoise", true)),
                onCheckedChange = { on -> if (on) onSelect(key) },
            )
        }
    }
}

@Composable
private fun ProfileChipRow(current: String, onChange: (String) -> Unit) {
    val all = listOf(
        "rubika" to "روبیکا",
        "digikala" to "دیجیکالا",
        "neshan" to "نشان",
        "snapp" to "اسنپ",
        "aparat" to "آپارات",
        "balad" to "بلد",
        "torob" to "ترب",
        "generic" to "مرورگر",
    )
    val selected = current.split(",").map { it.trim().lowercase() }.toSet()
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        all.forEach { (key, label) ->
            SettingsSwitchRow(
                title = label,
                summary = key,
                checked = key in selected,
                onCheckedChange = { checked ->
                    val next = if (checked) selected + key else selected - key
                    onChange(next.joinToString(","))
                },
            )
        }
    }
}
