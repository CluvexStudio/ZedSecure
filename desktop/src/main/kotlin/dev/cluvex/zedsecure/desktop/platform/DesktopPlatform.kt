package dev.cluvex.zedsecure.desktop.platform

import dev.cluvex.zedsecure.core.LogBus
import dev.cluvex.zedsecure.desktop.DesktopRendering
import dev.cluvex.zedsecure.ui.platform.AutomaticRendering
import dev.cluvex.zedsecure.ui.platform.FilePick
import dev.cluvex.zedsecure.ui.platform.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI
import javax.swing.JFileChooser

object DesktopPlatform : Platform {
    override val supportsSystemProxy: Boolean get() = true
    override val automaticRendering: AutomaticRendering get() = DesktopRendering.automatic
    override val supportsTun: Boolean get() = dev.cluvex.zedsecure.desktop.core.TunMode.supported()
    override val choosesTunEngine: Boolean get() = false

    override fun copyToClipboard(text: String) {
        runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        }
    }

    override fun readClipboard(): String? = runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as? String
    }.getOrNull()

    override fun shareText(text: String) {
        copyToClipboard(text)
        LogBus.append("I/Share copied to clipboard")
    }

    override fun openUri(url: String) {
        runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(url))
            }
        }
    }

    override fun lanIpv4Addresses(): List<String> = dev.cluvex.zedsecure.platform.LanAddresses.ipv4()

    override fun liveStats(): dev.cluvex.zedsecure.ui.platform.LiveStats = DesktopMonitor.sample()

    override fun cryptoAcceleration(): String = DesktopMonitor.cryptoAcceleration()

    override val isComputer: Boolean get() = true

    override fun toast(message: String) {
        LogBus.append("I/$message")
    }

    override fun shareFiles(files: List<Pair<String, ByteArray>>) {
        if (files.isEmpty()) return
        val named = uniqueNames(files.map { (name, bytes) -> dev.cluvex.zedsecure.crypto.zsxFileName(name) to bytes })
        val home = File(System.getProperty("user.home"))
        if (named.size == 1) {
            val (name, bytes) = named.first()
            val chooser = JFileChooser().apply { dialogTitle = "Save"; selectedFile = File(home, name) }
            if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return
            runCatching { chooser.selectedFile.writeBytes(bytes) }.onFailure { toast(it.message ?: "Could not save") }
            return
        }
        val chooser = JFileChooser().apply {
            dialogTitle = "Save ${named.size} files"
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            currentDirectory = home
        }
        if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return
        val dir = chooser.selectedFile
        val failed = named.count { (name, bytes) -> runCatching { File(dir, name).writeBytes(bytes) }.isFailure }
        if (failed > 0) toast("Could not save $failed of ${named.size} files")
    }

    internal fun uniqueNames(files: List<Pair<String, ByteArray>>): List<Pair<String, ByteArray>> {
        val used = mutableSetOf<String>()
        return files.map { (name, bytes) ->
            val stem = name.removeSuffix(".zsx")
            var candidate = name
            var n = 2
            while (!used.add(candidate)) candidate = "$stem-${n++}.zsx"
            candidate to bytes
        }
    }

    override suspend fun pickFileBytes(): FilePick? = withContext(Dispatchers.IO) {
        val chooser = JFileChooser()
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            val f = chooser.selectedFile
            runCatching { FilePick(f.name, f.readBytes()) }.getOrNull()
        } else {
            null
        }
    }
}
