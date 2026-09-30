package dev.cluvex.zedsecure.ui.platform

import androidx.compose.runtime.staticCompositionLocalOf
import dev.cluvex.zedsecure.data.assets.GeoAssets

data class LiveStats(

    val cpuPercent: Float? = null,

    val coreCount: Int = 1,

    val memoryBytes: Long? = null,

    val threads: Int? = null,

    val tempC: Float? = null,

    val thermalHeadroom: Float? = null,

    val batteryPercent: Int? = null,
    val charging: Boolean = false,

    val currentMilliAmps: Int? = null,

    val thermalStatus: Int? = null,

    val processUptimeMs: Long? = null,
)

interface Platform {
    fun copyToClipboard(text: String)
    fun readClipboard(): String?
    fun shareText(text: String)
    fun openUri(url: String)

    fun toast(message: String)

    fun shareFiles(files: List<Pair<String, ByteArray>>) {}

    fun liveStats(): LiveStats = LiveStats()

    val isComputer: Boolean get() = false

    fun cryptoAcceleration(): String = ""

    fun installedApps(): List<InstalledApp> = emptyList()

    fun appIcon(packageName: String): ByteArray? = null

    fun scanQrCode(onResult: (String?) -> Unit) { onResult(null) }

    fun scanQrFromImage(onResult: (String?) -> Unit) { onResult(null) }

    val supportsQrScan: Boolean get() = false

    val supportsSystemProxy: Boolean get() = false

    fun lanIpv4Addresses(): List<String> = emptyList()

    val supportsPerAppProxy: Boolean get() = false

    val supportsDynamicColor: Boolean get() = false

    val geoAssets: GeoAssets? get() = null

    suspend fun pickFileBytes(): FilePick? = null

    val isIgnoringBatteryOptimizations: Boolean get() = true

    val supportsBatteryOptimization: Boolean get() = false

    fun openBatteryOptimizationSettings() {}

    fun openStorePage() {
        openUri(dev.cluvex.zedsecure.data.update.PlayStore.WEB_URL)
    }
}

data class FilePick(val name: String, val bytes: ByteArray)

data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val iconPng: ByteArray? = null,
)

val LocalPlatform = staticCompositionLocalOf<Platform> {
    error("LocalPlatform not provided — wrap the UI in a Platform provider")
}
