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
        subtitle = "ضد DPI – فقط بعد از اتصال تونل",
        contentPadding = contentPadding,
        modifier = modifier,
        art = SettingsArts.Ghost,
    ) {
        SettingsGroup(title = "فعال‌سازی") {
            SettingsSwitchRow(
                title = "فعال‌سازی GHOST",
                summary = "با Connect به تونل کمک می‌کند",
                checked = ghost.enabled,
                onCheckedChange = { v -> onUpdate { it.copy(ghost = it.ghost.copy(enabled = v)) } },
            )
            SettingsSwitchRow(
                title = "AUTO",
                summary = "تنظیم خودکار بر اساس شبکه",
                checked = ghost.autoMode,
                onCheckedChange = { v -> onUpdate { it.copy(ghost = it.ghost.copy(autoMode = v)) } },
            )
            SettingsSwitchRow(
                title = "DARK GHOST",
                summary = "هوش شبکه + Morphing پیشرفته",
                checked = ghost.darkGhostEnabled,
                onCheckedChange = { v -> onUpdate { it.copy(ghost = it.ghost.copy(darkGhostEnabled = v)) } },
            )
            SettingsSwitchRow(
                title = "Packet / Desync (zapret-style)",
                summary = "الگوی Split/Disorder/Fake – لایه اپ مگر native موجود باشد",
                checked = ghost.packetDesyncEnabled,
                onCheckedChange = { v -> onUpdate { it.copy(ghost = it.ghost.copy(packetDesyncEnabled = v)) } },
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
                                s.running && s.darkGhost -> "● DARK GHOST فعال"
                                s.running -> "● GHOST فعال"
                                s.enabled && !s.tunnelConnected -> "○ آماده – منتظر اتصال"
                                else -> "○ متوقف"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = if (s.running) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text("تونل: ${if (s.tunnelConnected) "وصل" else "قطع"}", style = MaterialTheme.typography.bodyMedium)
                        Text("استراتژی: ${s.strategy}", style = MaterialTheme.typography.bodySmall)
                        Text("فاز: ${s.phase}", style = MaterialTheme.typography.bodySmall)
                        if (s.packetDesync) {
                            Text("Desync: ${s.desyncProfile} | Native: ${s.nativeMode}", style = MaterialTheme.typography.bodySmall)
                            Text(s.nativeDetail, style = MaterialTheme.typography.bodySmall)
                        }
                        if (s.darkGhost) {
                            Text("Intel: ${s.intelSummary}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (s.underPressure) {
                            Text("⚠ فشار شبکه", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        }
                        Text("چرخه ${s.cycles} | درخواست ${s.requests} | موفق ${s.successes}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (ghost.packetDesyncEnabled) {
            SettingsGroup(title = "Packet Layer (zapret-inspired)") {
                SettingsSwitchRow(
                    title = "ترجیح Native nfqws",
                    summary = "اگر ماژول native نباشد، خودکار لایه اپ",
                    checked = ghost.preferNativeDesync,
                    onCheckedChange = { v -> onUpdate { it.copy(ghost = it.ghost.copy(preferNativeDesync = v)) } },
                )
                DesyncOption("Split", "split", ghost.desyncProfile) { k ->
                    onUpdate { it.copy(ghost = it.ghost.copy(desyncProfile = k)) }
                }
                DesyncOption("Disorder", "disorder", ghost.desyncProfile) { k ->
                    onUpdate { it.copy(ghost = it.ghost.copy(desyncProfile = k)) }
                }
                DesyncOption("Fake Burst", "fake", ghost.desyncProfile) { k ->
                    onUpdate { it.copy(ghost = it.ghost.copy(desyncProfile = k)) }
                }
                DesyncOption("Max (ترکیبی)", "max", ghost.desyncProfile) { k ->
                    onUpdate { it.copy(ghost = it.ghost.copy(desyncProfile = k)) }
                }
                SettingsSegment {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "توجه: desync واقعی zapret/nfqws روی کرنل لینوکس با NFQUEUE است. " +
                                "در اندروید بدون root معمولاً فقط الگوی لایه اپ اجرا می‌شود. " +
                                "IPsec/QUIC کامل داخل GHOST پیاده نشده؛ از هسته VPN/Xray استفاده کنید.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (ghost.darkGhostEnabled) {
            SettingsGroup(title = "DARK GHOST") {
                SettingsSwitchRow(
                    title = "Network Intelligence",
                    summary = "پروب و طبقه‌بندی شرایط شبکه",
                    checked = ghost.darkIntelligenceEnabled,
                    onCheckedChange = { v -> onUpdate { it.copy(ghost = it.ghost.copy(darkIntelligenceEnabled = v)) } },
                )
                SettingsSwitchRow(
                    title = "Traffic Morphing",
                    summary = "فازهای D0–D4",
                    checked = ghost.darkMorphingEnabled,
                    onCheckedChange = { v -> onUpdate { it.copy(ghost = it.ghost.copy(darkMorphingEnabled = v)) } },
                )
            }
        }
    }
}

@Composable
private fun DesyncOption(label: String, key: String, current: String, onSelect: (String) -> Unit) {
    SettingsSwitchRow(
        title = label,
        summary = key,
        checked = current.equals(key, ignoreCase = true),
        onCheckedChange = { on -> if (on) onSelect(key) },
    )
}
