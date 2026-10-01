package dev.cluvex.zedsecure.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.MenuScope
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import com.kdroid.composetray.tray.impl.LinuxTrayInitializer
import dev.cluvex.zedsecure.desktop.core.exec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.concurrent.thread

sealed interface TrayEntry {
    data class Action(val label: String, val enabled: Boolean = true, val onClick: () -> Unit = {}) : TrayEntry
    data class Check(val label: String, val checked: Boolean, val onPick: () -> Unit) : TrayEntry
    data class Sub(val label: String, val entries: List<TrayEntry>) : TrayEntry
    data object Separator : TrayEntry
}

@Composable
fun MenuScope.awtEntries(entries: List<TrayEntry>) {
    entries.forEach { entry ->
        when (entry) {
            is TrayEntry.Action -> Item(entry.label, enabled = entry.enabled, onClick = entry.onClick)
            is TrayEntry.Check -> CheckboxItem(entry.label, checked = entry.checked) { _ -> entry.onPick() }
            is TrayEntry.Sub -> Menu(entry.label) { awtEntries(entry.entries) }
            TrayEntry.Separator -> Separator()
        }
    }
}

fun TrayMenuBuilder.nativeEntries(entries: List<TrayEntry>) {
    entries.forEach { entry ->
        when (entry) {
            is TrayEntry.Action -> Item(entry.label.forDbus(), isEnabled = entry.enabled, onClick = entry.onClick)
            is TrayEntry.Check -> CheckableItem(entry.label.forDbus(), checked = entry.checked, onCheckedChange = { entry.onPick() })
            is TrayEntry.Sub -> SubMenu(entry.label.forDbus()) { nativeEntries(entry.entries) }
            TrayEntry.Separator -> Divider()
        }
    }
}

fun String.forDbus(): String =
    filterNot { it.isSurrogate() }.replace(Regex("[ \\t]{2,}"), " ").trim()

private const val LINUX_TRAY_ID = "zedsecure"
private const val LINUX_TRAY_PX = 64

private fun List<TrayEntry>.signature(): String = joinToString("|") { entry ->
    when (entry) {
        is TrayEntry.Action -> "a:${entry.label}:${entry.enabled}"
        is TrayEntry.Check -> "c:${entry.label}:${entry.checked}"
        is TrayEntry.Sub -> "s:${entry.label}[${entry.entries.signature()}]"
        TrayEntry.Separator -> "-"
    }
}

private fun trayPng(key: String, icon: Painter): String {
    val file = File(System.getProperty("java.io.tmpdir"), "zedsecure-tray-$key.png")
    val image = icon.toAwtImage(Density(1f), LayoutDirection.Ltr, Size(LINUX_TRAY_PX.toFloat(), LINUX_TRAY_PX.toFloat()))
    val argb = BufferedImage(LINUX_TRAY_PX, LINUX_TRAY_PX, BufferedImage.TYPE_INT_ARGB)
    argb.createGraphics().apply { drawImage(image, 0, 0, null); dispose() }
    ImageIO.write(argb, "png", file)
    return file.absolutePath
}

@Composable
fun LinuxNativeTray(
    iconKey: String,
    icon: Painter,
    tooltip: String,
    onOpen: () -> Unit,
    onFailure: () -> Unit,
    entries: List<TrayEntry>,
) {
    val iconPath = remember(iconKey) { trayPng(iconKey, icon) }
    DisposableEffect(Unit) {
        onDispose { runCatching { LinuxTrayInitializer.dispose(LINUX_TRAY_ID) } }
    }
    LaunchedEffect(iconPath, tooltip, entries.signature()) {
        withContext(Dispatchers.IO) {
            runCatching {
                LinuxTrayInitializer.initialize(
                    LINUX_TRAY_ID,
                    iconPath,
                    tooltip.forDbus(),
                    onLeftClick = onOpen,
                    menuContent = { nativeEntries(entries) },
                )
            }.onFailure {
                System.err.println("tray: ${it.message}")
                onFailure()
            }
        }
    }
}

object LinuxDesktop {
    fun nativeTrayLoads(): Boolean = runCatching {
        Class.forName(NATIVE_TRAY_BRIDGE, true, LinuxTrayInitializer::class.java.classLoader)
    }.onFailure { System.err.println("tray: native tray unavailable: ${it.message ?: it}") }.isSuccess

    private const val NATIVE_TRAY_BRIDGE = "com.kdroid.composetray.lib.linux.LinuxNativeBridge"

    fun trayHostAvailable(): Boolean {
        val gdbus = exec(
            "gdbus", "call", "--session",
            "--dest", "org.freedesktop.DBus",
            "--object-path", "/org/freedesktop/DBus",
            "--method", "org.freedesktop.DBus.NameHasOwner", "org.kde.StatusNotifierWatcher",
            timeoutSec = 5,
        )
        if (gdbus.first == 0) return "true" in gdbus.second
        val dbusSend = exec(
            "dbus-send", "--session", "--print-reply", "--dest=org.freedesktop.DBus", "/org/freedesktop/DBus",
            "org.freedesktop.DBus.NameHasOwner", "string:org.kde.StatusNotifierWatcher",
            timeoutSec = 5,
        )
        return dbusSend.first == 0 && "boolean true" in dbusSend.second
    }

    fun notify(title: String, body: String) {
        thread(name = "desktop-notify", isDaemon = true) {
            exec("notify-send", "--app-name=ZedSecure", "--icon=network-vpn", title, body, timeoutSec = 5)
        }
    }
}
