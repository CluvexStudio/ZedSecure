package dev.cluvex.zedsecure.core

import android.content.Context
import dev.cluvex.zedsecure.core.AppLog as Log
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray

object FrontCore {
    private const val TAG = "FrontCore"

    @Volatile
    private var controller: CoreController? = null

    @Volatile
    private var stoppingOnPurpose = false

    @Volatile
    var onLost: (() -> Unit)? = null

    @Volatile
    var lastError: String? = null
        private set

    val isRunning: Boolean
        get() = runCatching { controller?.isRunning == true }.getOrDefault(false)

    @Synchronized
    fun start(context: Context, configJson: String): Boolean {
        XrayController.init(context)
        stopLocked()
        lastError = null
        val c = Libv2ray.newCoreController(object : CoreCallbackHandler {
            override fun onEmitStatus(status: Long, message: String?): Long = 0
            override fun startup(): Long = 0
            override fun shutdown(): Long {
                if (!stoppingOnPurpose) onLost?.invoke()
                return 0
            }
        })
        return try {
            c.startLoop(configJson, 0)
            controller = c
            c.isRunning
        } catch (e: Exception) {
            Log.e(TAG, "startLoop failed", e)
            lastError = e.message?.takeIf { it.isNotBlank() } ?: e.toString()
            false
        }
    }

    @Synchronized
    fun stop() = stopLocked()

    private fun stopLocked() {
        val c = controller ?: return
        controller = null
        stoppingOnPurpose = true
        try {
            runCatching { c.stopLoop() }.onFailure { Log.e(TAG, "stopLoop failed", it) }
        } finally {
            stoppingOnPurpose = false
        }
    }

    fun readTrafficDelta(): Pair<Long, Long> =
        XrayController.trafficDeltaOf(runCatching { controller?.queryAllOutboundTrafficStats() }.getOrNull())
}
