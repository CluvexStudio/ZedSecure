package dev.cluvex.zedsecure.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.core.graphics.drawable.toBitmap
import android.graphics.Bitmap
import dev.cluvex.zedsecure.core.platform.ZsxSharing
import dev.cluvex.zedsecure.data.assets.GeoAssets
import dev.cluvex.zedsecure.data.update.Distribution
import dev.cluvex.zedsecure.data.update.GitHubReleases
import dev.cluvex.zedsecure.data.update.PlayStore
import dev.cluvex.zedsecure.data.assets.GeoAssetsRepository
import dev.cluvex.zedsecure.ui.platform.FilePick
import dev.cluvex.zedsecure.ui.platform.InstalledApp
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.cluvex.zedsecure.ui.platform.Platform
import java.io.ByteArrayOutputStream

class AndroidPlatform(
    private val context: Context,
    private val filePicker: suspend () -> FilePick?,
    private val qrScanner: ((String?) -> Unit) -> Unit = { it(null) },

    private val imagePicker: suspend () -> ByteArray? = { null },
) : Platform {
    override fun scanQrCode(onResult: (String?) -> Unit) = qrScanner(onResult)

    override fun liveStats(): dev.cluvex.zedsecure.ui.platform.LiveStats {
        val cpu = cpuSample()
        val battery = batterySample()
        return dev.cluvex.zedsecure.ui.platform.LiveStats(
            cpuPercent = cpu?.first,
            coreCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
            memoryBytes = memorySample(),
            threads = runCatching { java.io.File("/proc/self/task").list()?.size }.getOrNull(),
            tempC = battery.tempC,
            batteryPercent = battery.percent,
            charging = battery.charging,
            currentMilliAmps = currentSample(),
            thermalStatus = thermalSample(),
            thermalHeadroom = headroomSample(),
            processUptimeMs = cpu?.second,
        )
    }

    private var lastCpuTicks: Long = -1
    private var lastCpuAtMs: Long = 0

    private fun cpuSample(): Pair<Float?, Long?>? = runCatching {
        val stat = java.io.File("/proc/self/stat").readText()

        val fields = stat.substring(stat.lastIndexOf(')') + 2).split(' ')
        val ticks = fields[11].toLong() + fields[12].toLong()
        val startTicks = fields[19].toLong()
        val hz = runCatching {
            android.system.Os.sysconf(android.system.OsConstants._SC_CLK_TCK)
        }.getOrDefault(100L).coerceAtLeast(1L)
        val now = android.os.SystemClock.elapsedRealtime()
        val uptimeMs = now - startTicks * 1000 / hz
        val percent = if (lastCpuTicks >= 0 && now > lastCpuAtMs) {
            val seconds = (now - lastCpuAtMs) / 1000f
            ((ticks - lastCpuTicks).toFloat() / hz / seconds * 100f).coerceAtLeast(0f)
        } else {
            null
        }
        lastCpuTicks = ticks
        lastCpuAtMs = now
        percent to uptimeMs
    }.getOrNull()

    private fun memorySample(): Long? = runCatching {
        java.io.File("/proc/self/status").useLines { lines ->
            lines.firstOrNull { it.startsWith("VmRSS:") }
                ?.filter { it.isDigit() }
                ?.toLongOrNull()
                ?.times(1024)
        }
    }.getOrNull()

    private data class BatterySample(val tempC: Float?, val percent: Int?, val charging: Boolean)

    private fun batterySample(): BatterySample = runCatching {
        val intent = context.registerReceiver(
            null,
            android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED),
        ) ?: return@runCatching BatterySample(null, null, false)
        val tenths = intent.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
        BatterySample(
            tempC = if (tenths == Int.MIN_VALUE) null else tenths / 10f,
            percent = if (level >= 0 && scale > 0) level * 100 / scale else null,
            charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                status == android.os.BatteryManager.BATTERY_STATUS_FULL,
        )
    }.getOrElse { BatterySample(null, null, false) }

    private fun currentSample(): Int? = runCatching {
        val bm = context.getSystemService(android.os.BatteryManager::class.java) ?: return@runCatching null
        val raw = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        if (raw == Int.MIN_VALUE || raw == 0) return@runCatching null
        val magnitude = if (raw < 0) -raw else raw
        if (magnitude >= 5_000) raw / 1000 else raw
    }.getOrNull()

    private var lastHeadroom: Float? = null
    private var lastHeadroomAtMs = 0L

    private fun headroomSample(): Float? = runCatching {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return@runCatching null
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastHeadroomAtMs < 3_000L) return@runCatching lastHeadroom
        lastHeadroomAtMs = now
        val pm = context.getSystemService(android.os.PowerManager::class.java) ?: return@runCatching null
        val value = pm.getThermalHeadroom(0)

        lastHeadroom = if (value.isNaN() || value.isInfinite()) null else value.coerceIn(0f, 2f)
        lastHeadroom
    }.getOrNull()

    private fun thermalSample(): Int? = runCatching {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return@runCatching null
        context.getSystemService(android.os.PowerManager::class.java)?.currentThermalStatus
    }.getOrNull()

    override fun cryptoAcceleration(): String =
        runCatching { dev.cluvex.zedsecure.core.XrayController.cryptoAcceleration() }.getOrDefault("")

    override fun scanQrFromImage(onResult: (String?) -> Unit) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            val bytes = runCatching { imagePicker() }.getOrNull()
            if (bytes == null) {
                onResult(null)
                return@launch
            }
            val text = withContext(kotlinx.coroutines.Dispatchers.Default) {
                dev.cluvex.zedsecure.qr.QrImageDecoder.decode(bytes)
            }
            onResult(text)
        }
    }

    override val supportsDynamicColor: Boolean
        get() = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S

    override val hasStatusNotification: Boolean get() = true

    override val simCountry: String? get() = telephonyCountry { it.simCountryIso }

    override val mobileNetworkCountry: String? get() = telephonyCountry { it.networkCountryIso }

    private fun telephonyCountry(pick: (android.telephony.TelephonyManager) -> String?): String? = runCatching {
        context.getSystemService(android.telephony.TelephonyManager::class.java)
            ?.let(pick)
            ?.trim()
            ?.uppercase()
            ?.takeIf { it.length == 2 && it.all { ch -> ch in 'A'..'Z' } }
    }.getOrNull()

    override val supportsQrScan: Boolean
        get() = context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)

    override fun copyToClipboard(text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("ZedSecure", text))
    }

    override fun readClipboard(): String? {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        return runCatching {
            cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
        }.getOrNull()
    }

    override fun shareText(text: String) {
        if (text.length > INLINE_SHARE_LIMIT) {
            ZsxSharing.shareText(context, "zedsecure-export.txt", text)
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(intent, null).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        runCatching { context.startActivity(chooser) }
            .onFailure { ZsxSharing.shareText(context, "zedsecure-export.txt", text) }
    }

    override fun openUri(url: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    override fun toast(message: String) {
        mainHandler.post { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private val INLINE_SHARE_LIMIT = 64 * 1024

    override fun shareFiles(files: List<Pair<String, ByteArray>>) {
        Thread({ runCatching { ZsxSharing.shareMany(context, files) } }, "zsx-share").start()
    }

    override fun saveFile(name: String, mime: String, bytes: ByteArray) {
        ZsxSharing.shareAs(context, name, mime, bytes)
    }

    override val supportsPerAppProxy: Boolean get() = true

    override fun lanIpv4Addresses(): List<String> = LanAddresses.ipv4()

    override fun installedApps(): List<InstalledApp> {
        val pm = context.packageManager
        val packages = runCatching {
            pm.getInstalledPackages(0)
        }.getOrElse { return emptyList() }

        val launchable = runCatching {
            pm.queryIntentActivities(
                android.content.Intent(android.content.Intent.ACTION_MAIN)
                    .addCategory(android.content.Intent.CATEGORY_LAUNCHER),
                0,
            ).mapNotNull { it.activityInfo?.packageName }.toHashSet()
        }.getOrDefault(hashSetOf())
        return packages.mapNotNull { pkg ->
            val info = pkg.applicationInfo ?: return@mapNotNull null

            if (pkg.packageName == context.packageName) return@mapNotNull null
            val system = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val updated = (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            InstalledApp(
                packageName = pkg.packageName,
                label = runCatching { pm.getApplicationLabel(info).toString() }
                    .getOrDefault(pkg.packageName),
                isSystem = system && !updated && pkg.packageName !in launchable,
            )
        }.sortedBy { it.label.lowercase() }
    }

    override fun appIcon(packageName: String): ByteArray? = runCatching {
        context.packageManager.getApplicationIcon(packageName)
            .toBitmap(width = ICON_PX, height = ICON_PX)
            .toPng()
    }.getOrNull()

    override val geoAssets: GeoAssets get() = GeoAssetsRepository(context)

    private val scanner by lazy {
        dev.cluvex.zedsecure.scan.NativeScanBackend.create(
            load = { System.loadLibrary("zedscan") },
            protector = { fd -> dev.cluvex.zedsecure.core.VpnSocketGuard.protect(fd) },
        )
    }

    override val dnsScanner: dev.cluvex.zedsecure.scan.ScanBackend? get() = scanner

    private val rootCli: java.io.File
        get() = java.io.File(context.applicationInfo.nativeLibraryDir, "libzedscan_cli.so")

    override val rootScanner: dev.cluvex.zedsecure.scan.ScanBackend?
        get() = rootCli.takeIf { it.isFile }?.let {
            dev.cluvex.zedsecure.scan.ProcessScanBackend(listOf("su", "-c", "${it.absolutePath} --stdin"))
        }

    @Volatile
    private var rootChecked: Boolean? = null

    override fun hasRoot(): Boolean {
        if (rootChecked == true) return true
        val ok = rootCli.isFile && dev.cluvex.zedsecure.scan.ProcessScanBackend.hasRoot()
        rootChecked = ok
        return ok
    }

    private val tester by lazy { dev.cluvex.zedsecure.core.AndroidTunnelTester() }

    override val tunnelTester: dev.cluvex.zedsecure.scan.TunnelTester get() = tester

    private fun underlyingNetworks(): List<Pair<android.net.Network, android.net.NetworkCapabilities>> = runCatching {
        val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
        @Suppress("DEPRECATION")
        cm.allNetworks.mapNotNull { n ->
            val caps = cm.getNetworkCapabilities(n) ?: return@mapNotNull null
            if (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)) return@mapNotNull null
            if (!caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)) return@mapNotNull null
            n to caps
        }
    }.getOrDefault(emptyList())

    override fun systemDnsServers(): List<String> = runCatching {
        val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
        underlyingNetworks().flatMap { (n, _) ->
            cm.getLinkProperties(n)?.dnsServers.orEmpty().mapNotNull { it.hostAddress?.substringBefore('%') }
        }.distinct()
    }.getOrDefault(emptyList())

    override fun otherVpnActive(): Boolean = runCatching {
        if (dev.cluvex.zedsecure.core.VpnSocketGuard.service != null) return@runCatching false
        val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
        val active = cm.activeNetwork ?: return@runCatching false
        cm.getNetworkCapabilities(active)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true
    }.getOrDefault(false)

    override val networkLabel: String?
        get() = runCatching {
            val caps = underlyingNetworks().firstOrNull()?.second ?: return@runCatching null
            when {
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ->
                    context.getSystemService(android.telephony.TelephonyManager::class.java)?.networkOperatorName?.takeIf { it.isNotBlank() } ?: "Mobile"
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> null
            }
        }.getOrNull()

    override val geoipFile: String?
        get() = java.io.File(GeoAssetsRepository(context).assetDir, GeoAssets.GEOIP).takeIf { it.isFile && it.length() > 0 }?.absolutePath

    override suspend fun pickFileBytes(): FilePick? = filePicker()

    override val supportsBatteryOptimization: Boolean get() = true

    override val isIgnoringBatteryOptimizations: Boolean
        get() = runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(context.packageName)
        }.getOrDefault(true)

    override fun openBatteryOptimizationSettings() {
        val candidates = listOf(
            Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(
                AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            ),
            Intent(AndroidSettings.ACTION_SETTINGS),
        )
        for (intent in candidates) {
            val ok = runCatching {
                context.startActivity(intent.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            }.isSuccess
            if (ok) return
        }
    }

    override val supportsNetworkTypeRules: Boolean get() = true

    override val distribution: Distribution by lazy {
        if (signingCertSha256() == PlayStore.SIGNING_CERT_SHA256) Distribution.PlayStore else Distribution.GitHub
    }

    override val deviceAbis: List<String> get() = android.os.Build.SUPPORTED_ABIS.toList()

    @Suppress("DEPRECATION")
    private fun signingCertSha256(): String? = runCatching {
        val pm = context.packageManager
        val cert = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
            if (info == null) null
            else if (info.hasMultipleSigners()) info.apkContentsSigners.firstOrNull()
            else info.signingCertificateHistory.lastOrNull()
        } else {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
        } ?: return@runCatching null
        java.security.MessageDigest.getInstance("SHA-256").digest(cert.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }.getOrNull()

    override fun openStorePage() {
        if (distribution != Distribution.PlayStore) {
            openUri(GitHubReleases.PROJECT_URL)
            return
        }
        val opened = runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(PlayStore.MARKET_URL)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }.isSuccess
        if (!opened) openUri(PlayStore.WEB_URL)
    }

    private fun Bitmap.toPng(): ByteArray = ByteArrayOutputStream().use {
        compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray()
    }

    private companion object {
        const val ICON_PX = 128
    }
}
