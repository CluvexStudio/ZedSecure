package dev.cluvex.zedsecure.desktop.platform

import dev.cluvex.zedsecure.core.LogBus
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
        val chooser = JFileChooser().apply { dialogTitle = "Save" }
        files.firstOrNull()?.let { (name, bytes) ->
            chooser.selectedFile = File(System.getProperty("user.home"), name)
            if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                runCatching { chooser.selectedFile.writeBytes(bytes) }
            }
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
