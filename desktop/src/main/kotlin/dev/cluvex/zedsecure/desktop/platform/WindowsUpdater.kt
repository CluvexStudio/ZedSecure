package dev.cluvex.zedsecure.desktop.platform

import dev.cluvex.zedsecure.core.LogBus
import dev.cluvex.zedsecure.core.VpnManager
import dev.cluvex.zedsecure.data.update.UpdateInfo
import dev.cluvex.zedsecure.desktop.core.Os
import dev.cluvex.zedsecure.domain.model.ConnectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.security.MessageDigest
import kotlin.system.exitProcess

object WindowsUpdater {
    private const val SUMS = "SHA256SUMS-windows.txt"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 60_000
    private const val BUFFER = 64 * 1024

    fun supports(info: UpdateInfo): Boolean =
        installedFromMsi() && msiName(info.versionName) in info.assets && SUMS in info.assets

    suspend fun install(info: UpdateInfo, progress: (Float) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val name = msiName(info.versionName)
            val expected = expectedHash(info.assets.getValue(SUMS), name)
            val dir = File(System.getProperty("java.io.tmpdir"), "zedsecure-update").apply { mkdirs() }
            val msi = File(dir, name)
            download(info.assets.getValue(name), msi, progress)
            progress(1f)
            if (sha256(msi) != expected) {
                msi.delete()
                error("the download does not match its checksum")
            }
            LogBus.append("I/Update starting the installer for ${info.versionName}")
            ProcessBuilder("msiexec.exe", "/i", msi.absolutePath, "/passive", "/norestart").start()
            exitProcess(0)
        }
    }

    fun installedFromMsi(): Boolean {
        if (Os.current != Os.WINDOWS) return false
        val exe = System.getProperty("jpackage.app-path")?.let(::File)?.takeIf { it.isFile } ?: return false
        val path = runCatching { exe.canonicalPath }.getOrDefault(exe.absolutePath).lowercase()
        return listOf("ProgramFiles", "ProgramW6432", "ProgramFiles(x86)")
            .mapNotNull { key -> System.getenv(key)?.takeIf { it.isNotBlank() } }
            .map { root -> runCatching { File(root).canonicalPath }.getOrDefault(root).lowercase().trimEnd('\\') + "\\" }
            .any { path.startsWith(it) }
    }

    private fun msiName(version: String) = "ZedSecure-$version-x86_64.msi"

    private fun expectedHash(url: String, name: String): String {
        val lines = withProxyFallback { proxy ->
            val connection = connect(url, proxy)
            try {
                if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
                connection.inputStream.bufferedReader().readLines()
            } finally {
                connection.disconnect()
            }
        }
        return lines.map { it.trim() }.firstOrNull { it.endsWith(name) }?.substringBefore(' ')?.lowercase()
            ?: error("the checksum list has no entry for $name")
    }

    private fun download(url: String, target: File, progress: (Float) -> Unit) = withProxyFallback { proxy ->
        val connection = connect(url, proxy)
        try {
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            val total = connection.contentLengthLong
            val part = File(target.parentFile, "${target.name}.part")
            connection.inputStream.use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER)
                    var done = 0L
                    var reported = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) {
                            val percent = (done * 100 / total).toInt()
                            if (percent != reported) {
                                reported = percent
                                progress(done.toFloat() / total)
                            }
                        }
                    }
                }
            }
            target.delete()
            if (!part.renameTo(target)) error("could not keep the download")
        } finally {
            connection.disconnect()
        }
    }

    private fun <T> withProxyFallback(block: (Proxy) -> T): T {
        val socks = VpnManager.activeSocksPort.takeIf { VpnManager.status.value.state == ConnectionState.Connected }
        if (socks != null) {
            val proxied = runCatching { block(Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("127.0.0.1", socks))) }
            if (proxied.isSuccess) return proxied.getOrThrow()
        }
        return block(Proxy.NO_PROXY)
    }

    private fun connect(url: String, proxy: Proxy): HttpURLConnection =
        (URI(url).toURL().openConnection(proxy) as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "ZedSecure")
        }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
