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

    // Only sync preferences – engine starts only when tunnel is connected
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
            subtitle = "کمک‌کننده ضد DPI برای تونل – فقط بعد از اتصال فعال می‌شود",
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier,
        ) {
            SettingsGroup("فعال‌سازی") {
                SettingsSwitchRow(
                    title = "فعال‌سازی GHOST",
                    summary = "فقط ترجیح را ذخیره می‌کند. با زدن اتصال، به تونل کمک می‌کند",
                    checked = ghost.enabled,
                    onCheckedChange = { v -> onUpdate { it.copy(ghost = it.ghost.copy(enabled = v)) } },
                )
                SettingsSegment {
                    Column(Modifier = Modifier.padding(16.dp)) {
                        Text(
                            "نحوه کار: GHOST را اینجا روشن کن → بعد دکمه اتصال را بزن. " +
                                "تا وقتی تونل وصل است کار می‌کند و با قطع اتصال خاموش می‌شود. " +
                                "به موتورهای Xray / sing-box و ... دست نمی‌زند.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (ghost.enabled) {
                SettingsGroup("وضعیت زنده (Debug)") {
                    SettingsSegment {
                        Column(Modifier = Modifier.padding(16.dp)) {
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
                            Text(
                                "تونل: ${if (status.tunnelConnected) "وصل" else "قطع"}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (status.underPressure) {
                                Text(
                                    "⚠ تحت فشار – حجم decoy افزایش یافته",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            Text("چرخه‌ها: ${status.cycles}", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "درخواست: ${status.requests} | موفق: ${status.successes} | ناموفق: ${status.failures}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text("آخرین پروفایل: ${status.lastProfile}", style = MaterialTheme.typography.bodySmall)
                            if (status.burstMode) {
                                Text("حالت Burst فعال است", style = MaterialTheme.typography.bodySmall)
                            }
                            status.lastError?.let {
                                Text(
                                    "آخرین خطا: $it",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }

                SettingsGroup("۱. پروفایل‌های رفتاری اپ‌های ایرانی") {
                    SettingsSwitchRow(
                        title = "App Behavior Profiles",
                        summary = "الگوی واقعی روبیکا، نشان، دیجیکالا و ...",
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

                SettingsGroup("۲. موتور تولید ترافیک جعلی") {
                    SettingsSwitchRow(
                        title = "Smart Decoy Engine",
                        summary = "تولید مداوم ترافیک جعلی کنار تونل",
                        checked = ghost.decoyEngineEnabled,
                        onCheckedChange = { v -> onUpdate { it.copy(ghost = it.ghost.copy(decoyEngineEnabled = v)) } },
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
                            "فاصله چرخه‌ها: ${ghost.decoyIntervalSeconds} ثانیه",
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

                SettingsGroup("۳. شبیه‌سازی لایه اپلیکیشن") {
                    SettingsSwitchRow(
                        title = "Application-Layer Mimicry",
                        summary = "User-Agent و مسیرهای واقعی اپ‌ها",
                        checked = ghost.appLayerMimicryEnabled,
                        onCheckedChange = { v -> onUpdate { it.copy(ghost = it.ghost.copy(appLayerMimicryEnabled = v)) } },
                    )
                }

                SettingsGroup("۵. شبیه‌سازی همزمان چند اپ") {
                    SettingsSwitchRow(
                        title = "Multi-App Concurrent",
                        summary = "چند اپ همزمان مثل کاربر واقعی",
                        checked = ghost.multiAppConcurrent,
                        onCheckedChange = { v -> onUpdate { it.copy(ghost = it.ghost.copy(multiAppConcurrent = v)) } },
                    )
                }

                SettingsGroup("۶. Fallback هوشمند + فشار") {
                    SettingsSwitchRow(
                        title = "Adaptive Fallback",
                        summary = "وقتی نرخ خطا بالا برود حجم decoy را زیاد می‌کند",
                        checked = ghost.adaptiveFallbackEnabled,
                        onCheckedChange = { v -> onUpdate { it.copy(ghost = it.ghost.copy(adaptiveFallbackEnabled = v)) } },
                    )
                    SettingsSwitchRow(
                        title = "تشخیص فشار",
                        summary = "از نرخ شکست اخیر برای تشخیص فشار استفاده می‌کند",
                        checked = ghost.pressureDetectionEnabled,
                        onCheckedChange = { v -> onUpdate { it.copy(ghost = it.ghost.copy(pressureDetectionEnabled = v)) } },
                    )
                }

                SettingsGroup("۷. حالت Burst") {
                    SettingsSwitchRow(
                        title = "Burst / High-Noise Mode",
                        summary = "افزایش شدید تعداد درخواست‌های جعلی",
                        checked = ghost.burstModeEnabled,
                        onCheckedChange = { v -> onUpdate { it.copy(ghost = it.ghost.copy(burstModeEnabled = v)) } },
                    )
                    if (ghost.burstModeEnabled) {
                        Text(
                            "شدت Burst: ${ghost.burstIntensity}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        Slider(
                            value = ghost.burstIntensity.toFloat(),
                            onValueChange = { v ->
                                onUpdate {
                                    it.copy(ghost = it.ghost.copy(burstIntensity = v.toInt().coerceIn(1, 5)))
                                }
                            },
                            valueRange = 1f..5f,
                            steps = 3,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }

                SettingsGroup("۸. موازی‌سازی") {
                    Text(
                        "حداکثر درخواست موازی: ${ghost.maxParallelRequests}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    Slider(
                        value = ghost.maxParallelRequests.toFloat(),
                        onValueChange = { v ->
                            onUpdate {
                                it.copy(ghost = it.ghost.copy(maxParallelRequests = v.toInt().coerceIn(1, 4)))
                            }
                        },
                        valueRange = 1f..4f,
                        steps = 2,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
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
