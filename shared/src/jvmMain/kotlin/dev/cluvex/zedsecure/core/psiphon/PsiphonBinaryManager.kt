package dev.cluvex.zedsecure.core.psiphon

import dev.cluvex.zedsecure.core.LogBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object PsiphonBinaryManager {
    init {
        PsiphonDownloadBus.isAvailableCheck = { isAvailable() }
    }

    const val ESTIMATED_SIZE_BYTES = 20 * 1024 * 1024L // ~20MB
    const val ESTIMATED_SIZE_LABEL = "~20 MB"

    private val isAndroid: Boolean by lazy {
        System.getProperty("java.vendor", "").contains("Android", ignoreCase = true) ||
            System.getProperty("java.vm.vendor", "").contains("Android", ignoreCase = true)
    }

    private val osName: String by lazy {
        System.getProperty("os.name", "").lowercase()
    }

    private val osArch: String by lazy {
        System.getProperty("os.arch", "").lowercase()
    }

    val binaryName: String
        get() = if (osName.contains("win")) "psiphon.exe" else "psiphon"

    fun isAvailable(): Boolean {
        if (isAndroid) return true

        // 1. Check classpath resource
        val sub = when {
            osName.contains("linux") -> "linux"
            osName.contains("mac") -> "macos"
            osName.contains("win") -> "windows"
            else -> "linux"
        }
        val res = "/bin/$sub/$binaryName"
        if (PsiphonBinaryManager::class.java.getResource(res) != null) return true

        // 2. Check temp work directory
        val workFile = File(File(System.getProperty("java.io.tmpdir"), "zedsecure"), binaryName)
        if (workFile.isFile && workFile.length() > 1024 * 1024) return true

        // 3. Check persistent config bin directories
        val persistentFile = File(persistentBinDir(), binaryName)
        if (persistentFile.isFile && persistentFile.length() > 1024 * 1024) return true

        val homeBin = File(File(System.getProperty("user.home"), ".zedsecure/bin"), binaryName)
        if (homeBin.isFile && homeBin.length() > 1024 * 1024) return true

        return false
    }

    fun persistentBinDir(): File {
        val home = System.getProperty("user.home")
        return when {
            osName.contains("win") -> File(System.getenv("APPDATA") ?: "$home\\AppData\\Roaming", "ZedSecure/bin")
            osName.contains("mac") -> File("$home/Library/Application Support/ZedSecure/bin")
            else -> File(System.getenv("XDG_CONFIG_HOME") ?: "$home/.config", "zedsecure/bin")
        }
    }

    fun workDir(): File {
        return File(System.getProperty("java.io.tmpdir"), "zedsecure").apply { mkdirs() }
    }

    private fun downloadUrls(): List<String> {
        val isArm = osArch.contains("aarch64") || osArch.contains("arm64")
        return when {
            osName.contains("win") -> listOf(
                "https://raw.githubusercontent.com/Psiphon-Labs/psiphon-tunnel-core-binaries/master/windows/psiphon-tunnel-core-x86_64.exe",
                "https://github.com/CluvexStudio/ZedSecure/releases/download/v3.1.3/psiphon-windows.exe",
            )
            osName.contains("mac") -> if (isArm) {
                listOf(
                    "https://raw.githubusercontent.com/Psiphon-Labs/psiphon-tunnel-core-binaries/master/osx/psiphon-tunnel-core-arm64",
                    "https://github.com/CluvexStudio/ZedSecure/releases/download/v3.1.3/psiphon-macos-arm64",
                )
            } else {
                listOf(
                    "https://raw.githubusercontent.com/Psiphon-Labs/psiphon-tunnel-core-binaries/master/osx/psiphon-tunnel-core-x86_64",
                    "https://github.com/CluvexStudio/ZedSecure/releases/download/v3.1.3/psiphon-macos-x86_64",
                )
            }
            else -> listOf(
                "https://raw.githubusercontent.com/Psiphon-Labs/psiphon-tunnel-core-binaries/master/linux/psiphon-tunnel-core-x86_64",
                "https://github.com/CluvexStudio/ZedSecure/releases/download/v3.1.3/psiphon-linux",
            )
        }
    }

    suspend fun download(
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        if (isAndroid) {
            return@withContext Result.success(File("psiphon"))
        }

        val work = workDir()
        val persistent = persistentBinDir().apply { mkdirs() }
        val targetWorkFile = File(work, binaryName)
        val targetPersistentFile = File(persistent, binaryName)
        val tmpFile = File(work, "$binaryName.download.tmp")

        val urls = downloadUrls()
        var lastError: Exception? = null

        for (urlStr in urls) {
            try {
                LogBus.append("I/Psiphon downloading from $urlStr")
                val url = URL(urlStr)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20_000
                    readTimeout = 60_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "ZedSecure-Client")
                }

                val code = conn.responseCode
                if (code !in 200..299) {
                    conn.disconnect()
                    throw IllegalStateException("HTTP $code from $urlStr")
                }

                val totalLength = conn.contentLengthLong.takeIf { it > 0 } ?: ESTIMATED_SIZE_BYTES
                var downloaded = 0L

                tmpFile.parentFile?.mkdirs()
                if (tmpFile.exists()) tmpFile.delete()

                conn.inputStream.use { input ->
                    FileOutputStream(tmpFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead
                            onProgress(downloaded, maxOf(totalLength, downloaded))
                        }
                        output.flush()
                    }
                }
                conn.disconnect()

                // Validate downloaded file
                if (!tmpFile.exists() || tmpFile.length() < 1024 * 1024) {
                    tmpFile.delete()
                    throw IllegalStateException("Downloaded binary too small (${tmpFile.length()} bytes)")
                }

                // Verify not HTML error response
                val header = ByteArray(64)
                tmpFile.inputStream().use { it.read(header) }
                val headerStr = String(header).lowercase()
                if (headerStr.contains("<html") || headerStr.contains("<!doctype")) {
                    tmpFile.delete()
                    throw IllegalStateException("Received HTML document instead of binary")
                }

                // Set executable permissions
                tmpFile.setExecutable(true, false)
                tmpFile.setReadable(true, false)

                // Move to target destinations
                if (targetWorkFile.exists()) targetWorkFile.delete()
                tmpFile.copyTo(targetWorkFile, overwrite = true)
                targetWorkFile.setExecutable(true, false)

                runCatching {
                    if (targetPersistentFile.exists()) targetPersistentFile.delete()
                    targetWorkFile.copyTo(targetPersistentFile, overwrite = true)
                    targetPersistentFile.setExecutable(true, false)
                }

                // Chmod on Unix
                if (!osName.contains("win")) {
                    runCatching {
                        ProcessBuilder("chmod", "+x", targetWorkFile.absolutePath).start().waitFor()
                        ProcessBuilder("chmod", "+x", targetPersistentFile.absolutePath).start().waitFor()
                    }
                }

                tmpFile.delete()
                LogBus.append("I/Psiphon engine downloaded and ready at ${targetWorkFile.absolutePath}")
                return@withContext Result.success(targetWorkFile)
            } catch (e: Exception) {
                lastError = e
                tmpFile.delete()
                LogBus.append("W/Psiphon download failed from $urlStr: ${e.message}")
            }
        }

        Result.failure(lastError ?: IllegalStateException("All download sources failed"))
    }
}
