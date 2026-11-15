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

    // Keep the engine in sync with UI state
    LaunchedEffect(ghost) {
        GhostEngine.updateSettings(ghost)
    }

    // Live debug status (refreshes every 2s)
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
            subtitle = "ترافیک جعلی هوشمند برای سخت‌تر کردن شناسایی DPI",
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier,
        ) {
            // Master switch
            SettingsGroup("فعال‌سازی") {
                SettingsSwitchRow(
                    title = "فعال‌سازی GHOST",
                    summary = "روشن کردن کل سیستم ضدشناسایی GHOST",
                    checked = ghost.enabled,
                    onCheckedChange = { enabled ->
                        onUpdate { it.copy(ghost = it.ghost.copy(enabled = enabled)) }
                    },
                )
            }

            if (ghost.enabled) {
                // Live Debug Panel
                SettingsGroup("وضعیت زنده (Debug)") {
                    SettingsSegment {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                if (status.running) "● در حال اجرا" else "○ متوقف",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (status.running)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text("چرخه‌ها: ${status.cycles}", style = MaterialTheme.typography.bodyMedium)
                            Text("درخواست‌ها: ${status.requests}  |  موفق: ${status.successes}  |  ناموفق: ${status.failures}",
                                style = MaterialTheme.typography.bodySmall)
                            Text("آخرین پروفایل: ${status.lastProfile}", style = MaterialTheme.typography.bodySmall)
                            status.lastError?.let {
                                Text("آخرین خطا: $it", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                // Additive read-only tunnel assistance monitor; existing GHOST controls remain unchanged.
                GhostAssistPanel(
                    enabled = ghost.enabled,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )

                // 1. App Behavior Profiles
                SettingsGroup("۱. پروفایل‌های رفتاری اپ‌های ایرانی") {
                    SettingsSwitchRow(
                        title = "App Behavior Profiles",
                        summary = "استفاده از الگوی واقعی روبیکا، نشان، دیجیکالا و ...",
                        checked = ghost.appProfilesEnabled,
                        onCheckedChange = { v ->
                            onUpdate { it.copy(ghost = it.ghost.copy(appProfilesEnabled = v)) }
                        },
                    )

                    if (ghost.appProfilesEnabled) {
                        ProfileChipRow(
                            current = ghost.activeProfiles,
                            onChange = { new ->
                                onUpdate { it.copy(ghost = it.ghost.copy(activeProfiles = new)) }
                            },
                        )
                    }
                }

                // 2. Smart Decoy Engine
                SettingsGroup("۲. موتور تولید ترافیک جعلی (Smart Decoy)") {
                    SettingsSwitchRow(
                        title = "Smart Decoy Engine",
                        summary = "تولید مداوم ترافیک جعلی شبیه اپ‌های داخلی",
                        checked = ghost.decoyEngineEnabled,
                        onCheckedChange = { v ->
                            onUpdate { it.copy(ghost = it.ghost.copy(decoyEngineEnabled = v)) }
                        },
                    )

                    if (ghost.decoyEngineEnabled) {
                        Text(
                            "حجم ترافیک جعلی: ${ghost.decoyVolumePercent}%",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        Slider(
                            value = ghost.decoyVolumePercent.toFloat(),
                            onValueChange = { v ->
                                onUpdate {
                                    it.copy(ghost = it.ghost.copy(decoyVolumePercent = v.toInt().coerceIn(10, 80)))
                                }
                            },
                            valueRange = 10f..80f,
                            steps = 13,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )

                        Text(
                            "فاصله بین چرخه‌ها: ${ghost.decoyIntervalSeconds} ثانیه",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        Slider(
                            value = ghost.decoyIntervalSeconds.toFloat(),
                            onValueChange = { v ->
                                onUpdate {
                                    it.copy(ghost = it.ghost.copy(decoyIntervalSeconds = v.toInt().coerceIn(5, 60)))
                                }
                            },
                            valueRange = 5f..60f,
                            steps = 10,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }

                // 3. Application-Layer Mimicry
                SettingsGroup("۳. شبیه‌سازی لایه اپلیکیشن") {
                    SettingsSwitchRow(
                        title = "Application-Layer Mimicry",
                        summary = "هدرها، User-Agent و مسیرهای واقعی اپ‌ها",
                        checked = ghost.appLayerMimicryEnabled,
                        onCheckedChange = { v ->
                            onUpdate { it.copy(ghost = it.ghost.copy(appLayerMimicryEnabled = v)) }
                        },
                    )
                }

                // 5. Multi-App Concurrent
                SettingsGroup("۵. شبیه‌سازی همزمان چند اپ") {
                    SettingsSwitchRow(
                        title = "Multi-App Concurrent Mimicry",
                        summary = "مثل کاربر واقعی چند اپ را همزمان باز کرده باشد",
                        checked = ghost.multiAppConcurrent,
                        onCheckedChange = { v ->
                            onUpdate { it.copy(ghost = it.ghost.copy(multiAppConcurrent = v)) }
                        },
                    )
                }

                // 6. Adaptive Fallback
                SettingsGroup("۶. Fallback هوشمند") {
                    SettingsSwitchRow(
                        title = "Adaptive Fallback",
                        summary = "گاهی حجم ترافیک جعلی را به‌صورت تصادفی بالا می‌برد",
                        checked = ghost.adaptiveFallbackEnabled,
                        onCheckedChange = { v ->
                            onUpdate { it.copy(ghost = it.ghost.copy(adaptiveFallbackEnabled = v)) }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileChipRow(
    current: String,
    onChange: (String) -> Unit,
) {
    val all = listOf(
        "rubika" to "روبیکا",
        "digikala" to "دیجیکالا",
        "neshan" to "نشان",
        "snapp" to "اسنپ",
        "aparat" to "آپارات",
        "balad" to "بلد",
        "torob" to "ترب",
    )
    val selected = current.split(",").map { it.trim().lowercase() }.toSet()

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            "اپ‌های فعال:",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        all.forEach { (key, label) ->
            val isOn = key in selected
            SettingsSwitchRow(
                title = label,
                summary = key,
                checked = isOn,
                onCheckedChange = { checked ->
                    val next = if (checked) selected + key else selected - key
                    onChange(next.joinToString(","))
                },
            )
        }
    }
}
