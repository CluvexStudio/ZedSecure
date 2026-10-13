package dev.cluvex.zedsecure.desktop.core

import dev.cluvex.zedsecure.core.VpnManager
import dev.cluvex.zedsecure.data.assets.GeoAsset
import dev.cluvex.zedsecure.data.assets.GeoAssets
import dev.cluvex.zedsecure.domain.model.ConnectionState
import dev.cluvex.zedsecure.domain.model.GeoFilesSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI

object DesktopGeoAssets : GeoAssets {
    val dir: File
        get() = File(dataDir(), "assets").apply { mkdirs() }

    override fun state(): List<GeoAsset> = GeoAssets.FILES.map { name ->
        val file = File(dir, name)
        GeoAsset(name, file.isFile && file.length() > 0, if (file.isFile) file.length() else 0L, if (file.isFile) file.lastModified() else 0L)
    }

    override fun allPresent(): Boolean = state().all { it.present }

    override suspend fun downloadAll(source: GeoFilesSource): Boolean = withContext(Dispatchers.IO) {
        GeoAssets.FILES.all { name ->
            runCatching { download("https://github.com/${source.repo}/releases/latest/download/$name", File(dir, name)) }
                .onFailure { println("[geo] $name: ${it.message}") }
                .isSuccess
        }
    }

    override suspend fun importBytes(bytes: ByteArray, name: String): Boolean = withContext(Dispatchers.IO) {
        if (name !in GeoAssets.FILES || bytes.isEmpty()) return@withContext false
        runCatching {
            val temp = File(dir, "$name.part")
            temp.writeBytes(bytes)
            File(dir, name).delete()
            temp.renameTo(File(dir, name))
        }.getOrDefault(false)
    }

    private fun download(url: String, target: File) {
        val socks = VpnManager.activeSocksPort.takeIf { VpnManager.status.value.state == ConnectionState.Connected }
        if (socks != null) {
            val proxied = runCatching { download(url, target, Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("127.0.0.1", socks))) }
            if (proxied.isSuccess) return
        }
        download(url, target, Proxy.NO_PROXY)
    }

    private fun download(url: String, target: File, proxy: Proxy) {
        val connection = URI(url).toURL().openConnection(proxy) as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "ZedSecure")
        try {
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            val temp = File(target.parentFile, "${target.name}.part")
            connection.inputStream.use { input -> temp.outputStream().use { input.copyTo(it) } }
            if (temp.length() <= 0) error("empty download")
            target.delete()
            if (!temp.renameTo(target)) error("could not move ${target.name} into place")
        } finally {
            connection.disconnect()
        }
    }

    private fun dataDir(): File {
        val home = System.getProperty("user.home")
        return when (Os.current) {
            Os.WINDOWS -> File(System.getenv("APPDATA") ?: "$home\\AppData\\Roaming", "ZedSecure")
            Os.MACOS -> File("$home/Library/Application Support/ZedSecure")
            else -> File(System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.local/share", "zedsecure")
        }
    }
}
