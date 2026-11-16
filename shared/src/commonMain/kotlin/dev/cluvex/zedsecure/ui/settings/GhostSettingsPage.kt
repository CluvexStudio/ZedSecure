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
            subtitle = "کمک‌کننده ضد DPI برای تونل – فقط بعد از اتصال",
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier,
        ) {
            SettingsGroup("فعال‌سازی") {
                SettingsSwitchRow(
                    title = "فعال‌سازی GHOST",
                    summary = "با زدن اتصال به تونل کمک می‌کند؛ خودکار وصل نمی‌شود",
                    checked = ghost.enabled,
                    onCheckedChange = { v -> onUpdate { it.copy(ghost = it.ghost.copy(enabled = v)) } },
                )
                SettingsSwitchRow(
                    title = "AUTO",
                    summary = "تشخیص وای‌فای / داده همراه و چیدن خودکار همه تنظیمات GHOST",
                    checked = ghost.autoMode,
                    onCheckedChange = { v -> onUpdate { it.copy(ghost = it.ghost.copy(autoMode = v)) } },
                )
                SettingsSegment {
                    Column(Modifier = Modifier.padding(16.dp)) {
                        Text(
                            "AUTO روشن باشد: نوع شبکه را بررسی می‌کند (وای‌فای یا داده همراه) " +
                                "و حجم، فاصله، پروفایل‌ها، Burst و موازی‌سازی را خودش تنظیم می‌کند. " +
                                "هنوز فقط وقتی تونل وصل است اجرا می‌شود.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (ghost.enabled) {
                SettingsGroup("وضعیت زنده") {
                    SettingsSegment {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                when {
                                    status.running -> "● در حال کمک به تونل"
                                    status.enabled && !status.tunnelConnected -> "○ آماده – منتظر اتصال تونل"
                                    else -> "○ متوقف"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = if (status.running) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text("تونل: ${if (status.tunnelConnected) "وصل" else "قطع"}",
                                style = MaterialTheme.typography.bodyMedium)
                            if (status.autoMode) {
                                Text("AUTO: ${status.networkLabel}", style = MaterialTheme.typography.bodyMedium)
                                Text(status.autoReason, style = MaterialTheme.typography.bodySmall)
                            }
                            if (status.underPressure) {
                                Text("⚠ تحت فشار – حجم decoy افزایش یافته",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error)
                            }
                            Text("چرخه‌ها: ${status.cycles}", style = MaterialTheme.typography.bodyMedium)
                            Text("درخواست: ${status.requests} | موفق: ${status.successes} | ناموفق: ${status.failures}",
                                style = MaterialTheme.typography.bodySmall)
                            Text("پروفایل فعال: ${status.activeProfiles}", style = MaterialTheme.typography.bodySmall)
                            Text("حجم مؤثر: ${status.decoyVolumePercent}% | آخرین: ${status.lastProfile}",
                                style = MaterialTheme.typography.bodySmall)
                            status.lastError?.let {
                                Text("آخرین خطا: $it", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                // Manual controls hidden when AUTO is on (AUTO owns them)
                if (!ghost.autoMode) {
                    SettingsGroup("۱. پروفایل‌های رفتاری") {
                        SettingsSwitchRow(
                            title = "App Behavior Profiles",
                            summary = "الگوی واقعی اپ‌های ایرانی",
                            checked = ghost.appProfilesEnabled,
                            onCheckedChange = { v -> onUpdate { it.copy(ghost = it.ghost.copy(appProfilesEnabled = v)) } },
                        )
                        if (ghost.appProfilesEnabled) {
                            ProfileChipRow(
                                current = ghost.activeProfiles,
                                onChange = { new -> onUpdate { it.copy(ghost = it.ghost.copy(activeProfiles = new)) } },
                            )
                        }
                    }

                    SettingsGroup("۲. موتور ترافیک جعلی") {
                        SettingsSwitchRow(
                            title = "Smart Decoy Engine",
                            summary = "تولید مداوم ترافیک جعلی",
                            checked = ghost.decoyEngineEnabled,
                            onCheckedChange = { v -> onUpdate { it.copy(ghost = it.ghost.copy(decoyEngineEnabled = v)) } },
                        )
                        if (ghost.decoyEngineEnabled) {
                            Text("حجم: ${ghost.decoyVolumePercent}%",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                            Slider(
                                value = ghost.decoyVolumePercent.toFloat(),
                                onValueChange = { v ->
                                    onUpdate { it.copy(ghost = it.ghost.copy(decoyVolumePercent = v.toInt().coerceIn(10, 80))) }
                                },
                                valueRange = 10f..80f, steps = 13,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            Text("فاصله: ${ghost.decoyIntervalSeconds} ثانیه",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                            Slider(
                                value = ghost.decoyIntervalSeconds.toFloat(),
                                onValueChange = { v ->
                                    onUpdate { it.copy(ghost = it.ghost.copy(decoyIntervalSeconds = v.toInt().coerceIn(5, 60))) }
                                },
                                valueRange = 5f..60f, steps = 10,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }

                    SettingsGroup("۳. Mimicry / Multi / Adaptive") {
                        SettingsSwitchRow(
                            title = "Application-Layer Mimicry",
                            summary = "UA و مسیر واقعی",
                            checked = ghost.appLayerMimicryEnabled,
                            onCheckedChange = { v -> onUpdate { it.copy(ghost = it.ghost.copy(appLayerMimicryEnabled = v)) } },
                        )
                        SettingsSwitchRow(
                            title = "Multi-App Concurrent",
                            summary = "چند اپ همزمان",
                            checked = ghost.multiAppConcurrent,
                            onCheckedChange = { v -> onUpdate { it.copy(ghost = it.ghost.copy(multiAppConcurrent = v)) } },
                        )
                        SettingsSwitchRow(
                            title = "Adaptive Fallback",
                            summary = "افزایش decoy زیر فشار",
                            checked = ghost.adaptiveFallbackEnabled,
                            onCheckedChange = { v -> onUpdate { it.copy(ghost = it.ghost.copy(adaptiveFallbackEnabled = v)) } },
                        )
                        SettingsSwitchRow(
                            title = "تشخیص فشار",
                            summary = "از نرخ شکست اخیر",
                            checked = ghost.pressureDetectionEnabled,
                            onCheckedChange = { v -> onUpdate { it.copy(ghost = it.ghost.copy(pressureDetectionEnabled = v)) } },
                        )
                    }

                    SettingsGroup("۷. Burst") {
                        SettingsSwitchRow(
                            title = "Burst Mode",
                            summary = "نویز بالا",
                            checked = ghost.burstModeEnabled,
                            onCheckedChange = { v -> onUpdate { it.copy(ghost = it.ghost.copy(burstModeEnabled = v)) } },
                        )
                        if (ghost.burstModeEnabled) {
                            Text("شدت: ${ghost.burstIntensity}",
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
                    }

                    SettingsGroup("۸. موازی‌سازی") {
                        Text("حداکثر موازی: ${ghost.maxParallelRequests}",
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
                    SettingsGroup("AUTO فعال است") {
                        SettingsSegment {
                            Column(Modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "تنظیمات دستی مخفی شده‌اند. AUTO بر اساس شبکه و فشار، " +
                                        "همه‌چیز را خودش می‌چیند. برای تنظیم دستی، AUTO را خاموش کن.",
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
private fun ProfileChipRow(current: String, onChange: (String) -> Unit) {
    val all = listOf(
        "rubika" to "روبیکا",
        "digikala" to "دیجیکالا",
        "neshan" to "نشان",
        "snapp" to "اسنپ",
        "aparat" to "آپارات",
        "balad" to "بلد",
        "torob" to "ترب",
        "generic" to "مرورگر عمومی",
    )
    val selected = current.split(",").map { it.trim().lowercase() }.toSet()
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("اپ‌های فعال:", style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(bottom = 6.dp))
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
