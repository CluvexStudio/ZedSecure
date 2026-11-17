package dev.cluvex.zedsecure.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.foundation.layout.fillMaxWidth
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
 * GHOST MOD settings.
 * Pattern matches PrivacyPage / other settings pages to avoid scaffold API mismatches.
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
                title = "ضد Porn",
                summary = "DNS خانوادگی، مسدودسازی دامنه‌ها و SafeSearch در سرویس‌های پشتیبانی‌شده؛ هنگام اتصال VPN اعمال می‌شود",
                checked = ghost.adultContentProtectionEnabled,
                onCheckedChange = { v ->
                    onUpdate {
                        it.copy(ghost = it.ghost.copy(adultContentProtectionEnabled = v))
                    }
                },
            )
            Text(
                text = "این حالت از Family Filter استفاده می‌کند و SafeSearch را در سرویس‌های پشتیبانی‌شده فعال می‌کند. برای اعمال تغییر، VPN را دوباره وصل کن. DNS رمزگذاری‌شدهٔ مستقل در بعضی برنامه‌ها یا VPN دیگر ممکن است این سیاست را دور بزند؛ فیلتر تضمین صددرصدی نیست.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (ghost.adultContentProtectionEnabled) {
                androidx.compose.material3.OutlinedTextField(
                    value = ghost.adultExtraDomains,
                    onValueChange = { v ->
                        onUpdate { it.copy(ghost = it.ghost.copy(adultExtraDomains = v.take(8000))) }
                    },
                    label = { Text("دامنه‌های اضافهٔ مسدود (هر خط یکی)") },
                    placeholder = { Text("example.com") },
                    minLines = 2,
                    maxLines = 6,
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                )
                Text(
                    text = "علاوه بر لیست داخلی، این دامنه‌ها هم هنگام اتصال مسدود می‌شوند. هم‌زمان نشانی‌های DNS رمزگذاری‌شدهٔ عمومی (مثل dns.google و cloudflare-dns.com) بسته می‌شوند تا مرورگرها نتوانند فیلتر DNS را دور بزنند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SettingsSwitchRow(
                title = "AUTO",
                summary = "تشخیص وای‌فای/داده + استراتژی خودکار",
                checked = ghost.autoMode,
                onCheckedChange = { v ->
                    onUpdate { it.copy(ghost = it.ghost.copy(autoMode = v)) }
                },
            )
            SettingsSwitchRow(
                title = "Packet / Desync",
                summary = "تنظیمات Packet Layer",
                checked = ghost.packetDesyncEnabled,
                onCheckedChange = { v ->
                    onUpdate { it.copy(ghost = it.ghost.copy(packetDesyncEnabled = v)) }
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
                                s.running && s.packetDesync -> "● DESYNC فعال – کمک به تونل"
                                s.running -> "● در حال کمک به تونل"
                                s.enabled && !s.tunnelConnected -> "○ آماده – منتظر اتصال"
                                else -> "○ متوقف"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = if (s.running) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Text(
                            text = "تونل: ${if (s.tunnelConnected) "وصل" else "قطع"}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "استراتژی: ${s.strategy} | فاز: ${s.phase}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (s.packetDesync) {
                            Text(
                                text = "Desync: ${s.desyncProfile} | Native: ${s.nativeMode}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                text = s.nativeDetail,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (s.autoMode) {
                            Text(
                                text = "شبکه: ${s.networkLabel}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = s.autoReason,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (s.underPressure) {
                            Text(
                                text = "⚠ فشار DPI – نویز افزایش یافته",
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


        if (ghost.packetDesyncEnabled) {
            SettingsGroup(title = "Packet Layer") {
                SettingsSwitchRow(
                    title = "ترجیح Native nfqws",
                    summary = "در صورت نبود ماژول Native، وضعیت جایگزین گزارش می‌شود",
                    checked = ghost.preferNativeDesync,
                    onCheckedChange = { v ->
                        onUpdate { it.copy(ghost = it.ghost.copy(preferNativeDesync = v)) }
                    },
                )
                DesyncOption("Split", "split", ghost.desyncProfile) { key ->
                    onUpdate { it.copy(ghost = it.ghost.copy(desyncProfile = key)) }
                }
                DesyncOption("Disorder", "disorder", ghost.desyncProfile) { key ->
                    onUpdate { it.copy(ghost = it.ghost.copy(desyncProfile = key)) }
                }
                DesyncOption("Fake Burst", "fake", ghost.desyncProfile) { key ->
                    onUpdate { it.copy(ghost = it.ghost.copy(desyncProfile = key)) }
                }
                DesyncOption("Max", "max", ghost.desyncProfile) { key ->
                    onUpdate { it.copy(ghost = it.ghost.copy(desyncProfile = key)) }
                }
            }
        }

        if (ghost.autoMode) {
            SettingsGroup(title = "AUTO فعال") {
                SettingsSegment {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "تنظیمات دستی مخفی است. AUTO بر اساس شبکه و فشار DPI همه چیز را می‌چیند.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            return@SettingsPageScaffold
        }

        SettingsGroup(title = "استراتژی") {
            StrategyOption(
                label = "Auto",
                key = "auto",
                current = ghost.strategy,
                onSelect = { key -> onUpdate { it.copy(ghost = it.ghost.copy(strategy = key)) } },
            )
            StrategyOption(
                label = "Balanced",
                key = "balanced",
                current = ghost.strategy,
                onSelect = { key -> onUpdate { it.copy(ghost = it.ghost.copy(strategy = key)) } },
            )
            StrategyOption(
                label = "Aggressive",
                key = "aggressive",
                current = ghost.strategy,
                onSelect = { key -> onUpdate { it.copy(ghost = it.ghost.copy(strategy = key)) } },
            )
            StrategyOption(
                label = "Max Noise",
                key = "max",
                current = ghost.strategy,
                onSelect = { key -> onUpdate { it.copy(ghost = it.ghost.copy(strategy = key)) } },
            )
            StrategyOption(
                label = "Cellular Stealth",
                key = "stealth",
                current = ghost.strategy,
                onSelect = { key -> onUpdate { it.copy(ghost = it.ghost.copy(strategy = key)) } },
            )
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
                title = "App-Layer Mimicry",
                summary = "User-Agent شبیه اپ‌های ایرانی",
                checked = ghost.appLayerMimicryEnabled,
                onCheckedChange = { v ->
                    onUpdate { it.copy(ghost = it.ghost.copy(appLayerMimicryEnabled = v)) }
                },
            )
            SettingsSwitchRow(
                title = "Multi-App",
                summary = "چند اپ همزمان",
                checked = ghost.multiAppConcurrent,
                onCheckedChange = { v ->
                    onUpdate { it.copy(ghost = it.ghost.copy(multiAppConcurrent = v)) }
                },
            )
            SettingsSwitchRow(
                title = "Adaptive + Pressure",
                summary = "افزایش نویز زیر فشار DPI",
                checked = ghost.adaptiveFallbackEnabled,
                onCheckedChange = { v ->
                    onUpdate {
                        it.copy(
                            ghost = it.ghost.copy(
                                adaptiveFallbackEnabled = v,
                                pressureDetectionEnabled = v,
                            ),
                        )
                    }
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

        SettingsGroup(title = "حجم و زمان") {
            SettingsSegment {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = "حجم ترافیک جعلی: ${ghost.decoyVolumePercent}%",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = ghost.decoyVolumePercent.toFloat().coerceIn(10f, 85f),
                        onValueChange = { v ->
                            onUpdate {
                                it.copy(
                                    ghost = it.ghost.copy(
                                        decoyVolumePercent = v.toInt().coerceIn(10, 85),
                                    ),
                                )
                            }
                        },
                        valueRange = 10f..85f,
                        steps = 14,
                    )
                    Text(
                        text = "فاصله چرخه: ${ghost.decoyIntervalSeconds} ثانیه",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = ghost.decoyIntervalSeconds.toFloat().coerceIn(4f, 60f),
                        onValueChange = { v ->
                            onUpdate {
                                it.copy(
                                    ghost = it.ghost.copy(
                                        decoyIntervalSeconds = v.toInt().coerceIn(4, 60),
                                    ),
                                )
                            }
                        },
                        valueRange = 4f..60f,
                        steps = 13,
                    )
                    if (ghost.burstModeEnabled) {
                        Text(
                            text = "شدت Burst: ${ghost.burstIntensity}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Slider(
                            value = ghost.burstIntensity.toFloat().coerceIn(1f, 5f),
                            onValueChange = { v ->
                                onUpdate {
                                    it.copy(
                                        ghost = it.ghost.copy(
                                            burstIntensity = v.toInt().coerceIn(1, 5),
                                        ),
                                    )
                                }
                            },
                            valueRange = 1f..5f,
                            steps = 3,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DesyncOption(
    label: String,
    key: String,
    current: String,
    onSelect: (String) -> Unit,
) {
    SettingsSwitchRow(
        title = label,
        summary = key,
        checked = current.equals(key, ignoreCase = true),
        onCheckedChange = { selected -> if (selected) onSelect(key) },
    )
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
