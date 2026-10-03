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
import com.kdroid.composetray.tray.impl.WindowsTrayInitializer
import dev.cluvex.zedsecure.desktop.core.Os
import dev.cluvex.zedsecure.desktop.core.exec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
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
fun MenuScope.awtEntries(entries: List<TrayEntry>, plainText: Boolean = Os.current == Os.WINDOWS) {
    fun label(text: String) = if (plainText) text.trayText() else text
    entries.forEach { entry ->
        when (entry) {
            is TrayEntry.Action -> Item(label(entry.label), enabled = entry.enabled, onClick = entry.onClick)
            is TrayEntry.Check -> CheckboxItem(label(entry.label), checked = entry.checked) { _ -> entry.onPick() }
            is TrayEntry.Sub -> Menu(label(entry.label)) { awtEntries(entry.entries, plainText) }
            TrayEntry.Separator -> Separator()
        }
    }
}

fun TrayMenuBuilder.nativeEntries(entries: List<TrayEntry>) {
    entries.forEach { entry ->
        when (entry) {
            is TrayEntry.Action -> Item(entry.label.trayText(), isEnabled = entry.enabled, onClick = entry.onClick)
            is TrayEntry.Check -> CheckableItem(entry.label.trayText(), checked = entry.checked, onCheckedChange = { entry.onPick() })
            is TrayEntry.Sub -> SubMenu(entry.label.trayText()) { nativeEntries(entry.entries) }
            TrayEntry.Separator -> Divider()
        }
    }
}

fun String.trayText(): String =
    filterNot { it.isSurrogate() || it == '\uFE0F' || it == '\u200D' }.replace(Regex("[ \\t]{2,}"), " ").trim()

private const val TRAY_ID = "zedsecure"
private const val LINUX_TRAY_PX = 64
private val WINDOWS_ICON_SIZES = listOf(16, 20, 24, 32, 40, 48, 64)

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
    ImageIO.write(render(icon, LINUX_TRAY_PX), "png", file)
    return file.absolutePath
}

private fun trayIco(key: String, icon: Painter): String {
    val file = File(System.getProperty("java.io.tmpdir"), "zedsecure-tray-$key.ico")
    file.writeBytes(icoBytes(WINDOWS_ICON_SIZES.map { render(icon, it) }))
    return file.absolutePath
}

private fun render(icon: Painter, px: Int): BufferedImage {
    val image = icon.toAwtImage(Density(1f), LayoutDirection.Ltr, Size(px.toFloat(), px.toFloat()))
    return BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB).apply {
        createGraphics().apply { drawImage(image, 0, 0, null); dispose() }
    }
}

internal fun icoBytes(images: List<BufferedImage>): ByteArray {
    val bitmaps = images.map(::dib)
    val out = ByteArrayOutputStream()
    fun u16(v: Int) { out.write(v and 0xff); out.write((v ushr 8) and 0xff) }
    fun u32(v: Int) { u16(v and 0xffff); u16((v ushr 16) and 0xffff) }
    u16(0); u16(1); u16(images.size)
    var offset = 6 + 16 * images.size
    images.zip(bitmaps).forEach { (image, bytes) ->
        out.write(if (image.width >= 256) 0 else image.width)
        out.write(if (image.height >= 256) 0 else image.height)
        out.write(0); out.write(0)
        u16(1); u16(32)
        u32(bytes.size); u32(offset)
        offset += bytes.size
    }
    bitmaps.forEach { out.write(it) }
    return out.toByteArray()
}

private fun dib(image: BufferedImage): ByteArray {
    val w = image.width
    val h = image.height
    val maskRow = ((w + 31) / 32) * 4
    val out = ByteArrayOutputStream()
    fun u16(v: Int) { out.write(v and 0xff); out.write((v ushr 8) and 0xff) }
    fun u32(v: Int) { u16(v and 0xffff); u16((v ushr 16) and 0xffff) }
    u32(40); u32(w); u32(h * 2); u16(1); u16(32); u32(0); u32(w * h * 4 + maskRow * h)
    u32(0); u32(0); u32(0); u32(0)
    for (y in h - 1 downTo 0) {
        for (x in 0 until w) {
            val argb = image.getRGB(x, y)
            out.write(argb and 0xff)
            out.write((argb ushr 8) and 0xff)
            out.write((argb ushr 16) and 0xff)
            out.write((argb ushr 24) and 0xff)
        }
    }
    repeat(maskRow * h) { out.write(0) }
    return out.toByteArray()
}

@Composable
fun NativeTray(
    iconKey: String,
    icon: Painter,
    tooltip: String,
    onOpen: () -> Unit,
    onFailure: () -> Unit,
    entries: List<TrayEntry>,
) {
    val windows = Os.current == Os.WINDOWS
    val iconPath = remember(iconKey) { if (windows) trayIco(iconKey, icon) else trayPng(iconKey, icon) }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { if (windows) WindowsTrayInitializer.dispose(TRAY_ID) else LinuxTrayInitializer.dispose(TRAY_ID) }
        }
    }
    LaunchedEffect(iconPath, tooltip, entries.signature()) {
        withContext(Dispatchers.IO) {
            runCatching {
                if (windows) {
                    WindowsTrayInitializer.initialize(
                        TRAY_ID,
                        iconPath,
                        tooltip.trayText(),
                        onLeftClick = onOpen,
                        menuContent = { nativeEntries(entries) },
                    )
                } else {
                    LinuxTrayInitializer.initialize(
                        TRAY_ID,
                        iconPath,
                        tooltip.trayText(),
                        onLeftClick = onOpen,
                        menuContent = { nativeEntries(entries) },
                    )
                }
            }.onFailure {
                System.err.println("tray: ${it.message}")
                onFailure()
            }
        }
    }
}

object WindowsDesktop {
    fun nativeTrayLoads(): Boolean = runCatching {
        Class.forName(NATIVE_TRAY_BRIDGE, true, WindowsTrayInitializer::class.java.classLoader)
    }.onFailure { System.err.println("tray: native tray unavailable: ${it.message ?: it}") }.isSuccess

    private const val NATIVE_TRAY_BRIDGE = "com.kdroid.composetray.lib.windows.WindowsNativeBridge"

    private const val TOAST_APP_ID = "{1AC14E77-02E7-4E5D-B744-2EB1AE5198B7}\\WindowsPowerShell\\v1.0\\powershell.exe"

    internal val toastScript = """
        |[Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] | Out-Null
        |${'$'}xml = [Windows.UI.Notifications.ToastNotificationManager]::GetTemplateContent([Windows.UI.Notifications.ToastTemplateType]::ToastText02)
        |${'$'}text = ${'$'}xml.GetElementsByTagName('text')
        |${'$'}text.Item(0).AppendChild(${'$'}xml.CreateTextNode(${'$'}env:ZEDSECURE_TOAST_TITLE)) | Out-Null
        |${'$'}text.Item(1).AppendChild(${'$'}xml.CreateTextNode(${'$'}env:ZEDSECURE_TOAST_BODY)) | Out-Null
        |[Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier('$TOAST_APP_ID').Show([Windows.UI.Notifications.ToastNotification]::new(${'$'}xml))
        |""".trimMargin()

    fun notify(title: String, body: String) {
        thread(name = "desktop-notify", isDaemon = true) {
            runCatching {
                val encoded = java.util.Base64.getEncoder().encodeToString(toastScript.toByteArray(Charsets.UTF_16LE))
                val process = ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-EncodedCommand", encoded,
                ).redirectErrorStream(true).apply {
                    environment()["ZEDSECURE_TOAST_TITLE"] = title
                    environment()["ZEDSECURE_TOAST_BODY"] = body
                }.start()
                process.inputStream.bufferedReader().readText()
                process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)
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
