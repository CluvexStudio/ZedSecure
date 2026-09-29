package dev.cluvex.zedsecure.core

import android.content.Context
import dev.cluvex.zedsecure.core.AppLog as Log
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.io.File

object XrayController {
    private const val TAG = "XrayController"

    private var controller: CoreController? = null

    @Volatile
    private var initialized = false

    @Volatile
    private var appContext: Context? = null

    var onCoreShutdown: (() -> Unit)? = null

    @Volatile
    var lastError: String? = null
        private set

    fun runtimeReport(limit: Int = 12): String =
        runCatching { Libv2ray.runtimeReport(limit.toLong()) }.getOrElse { "unavailable: ${it.message}" }

    fun cryptoAcceleration(): String =
        runCatching { Libv2ray.cryptoAcceleration() }.getOrElse { "" }

    @Synchronized
    fun init(context: Context) {
        if (initialized) return

        CoreCrashLog.install(context)
        val assets = File(context.filesDir, "assets").apply { mkdirs() }.absolutePath
        Libv2ray.initCoreEnv(assets, "")
        controller = Libv2ray.newCoreController(object : CoreCallbackHandler {
            override fun onEmitStatus(status: Long, message: String?): Long = 0
            override fun startup(): Long = 0
            override fun shutdown(): Long {
                onCoreShutdown?.invoke()
                return 0
            }
        })
        initialized = true
    }

    fun warmUp(context: Context) {
        appContext = context.applicationContext
        if (initialized) return
        val app = context.applicationContext
        Thread({ runCatching { init(app) } }, "zed-core-init").apply { isDaemon = true }.start()
    }

    private fun ready(): CoreController? {
        if (!initialized) appContext?.let { runCatching { init(it) } }
        return controller
    }

    val isRunning: Boolean
        get() = controller?.isRunning ?: false

    fun start(configJson: String): Boolean {
        val c = ready() ?: return false
        if (c.isRunning) runCatching { c.stopLoop() }
        lastError = null
        return try {
            c.startLoop(configJson, 0)
            c.isRunning
        } catch (e: Exception) {
            Log.e(TAG, "startLoop failed", e)
            lastError = e.message?.takeIf { it.isNotBlank() } ?: e.toString()
            false
        }
    }

    fun stop() {
        runCatching { if (controller?.isRunning == true) controller?.stopLoop() }
            .onFailure { Log.e(TAG, "stopLoop failed", it) }
    }

    fun readTrafficDelta(): Pair<Long, Long> {
        val raw = runCatching { controller?.queryAllOutboundTrafficStats() }.getOrNull()
            ?: return 0L to 0L
        var down = 0L
        var up = 0L

        val byTag = HashMap<String, LongArray>()
        for (entry in raw.split(';')) {
            if (entry.isEmpty()) continue
            val parts = entry.split(',')
            if (parts.size != 3) continue
            val value = parts[2].toLongOrNull() ?: continue
            if (parts[0] !in NOT_A_METER) {
                val slot = byTag.getOrPut(parts[0]) { LongArray(2) }
                when (parts[1]) {
                    "downlink" -> slot[0] += value
                    "uplink" -> slot[1] += value
                }
            }

            if (parts[0] != "proxy" && !dev.cluvex.zedsecure.domain.config.AutoSelectTags.isMember(parts[0])) continue
            when (parts[1]) {
                "downlink" -> down += value
                "uplink" -> up += value
            }
        }
        if (down == 0L && up == 0L && byTag.isNotEmpty()) {
            val busiest = byTag.values.maxByOrNull { it[0] + it[1] }
            if (busiest != null) return busiest[0] to busiest[1]
        }
        return down to up
    }

    private val NOT_A_METER = setOf("direct", "block", "blocked", "dns-out", "dns_out", "api", "fragment")

    fun measureDelay(url: String): Long =
        runCatching { ready()?.measureDelay(url) ?: -1L }.getOrDefault(-1L)

    fun measureDelayDetailed(url: String): dev.cluvex.zedsecure.core.CoreProbe.DelayOutcome {
        val c = ready()
            ?: return dev.cluvex.zedsecure.core.CoreProbe.DelayOutcome(-1L, null)
        return try {
            dev.cluvex.zedsecure.core.CoreProbe.DelayOutcome(c.measureDelay(url), null)
        } catch (e: Exception) {
            dev.cluvex.zedsecure.core.CoreProbe.DelayOutcome(-1L, e.message)
        }
    }
}
