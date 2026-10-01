package dev.cluvex.zedsecure.desktop.platform

import dev.cluvex.zedsecure.core.VpnManager
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class DesktopMeter(
    private val totals: () -> Pair<Long, Long>?,
    private val clock: () -> Long = System::currentTimeMillis,
    private val report: (Int, Long, Long, Long, Long) -> Unit = VpnManager::onMetrics,
) {
    private var executor: ScheduledExecutorService? = null
    private var startedAt = 0L
    private var lastDown = 0L
    private var lastUp = 0L
    private var lastAt = 0L

    @Synchronized
    fun start() {
        if (executor != null) return
        startedAt = clock()
        lastAt = startedAt
        lastDown = 0L
        lastUp = 0L
        executor = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "desktop-meter").apply { isDaemon = true }
        }.also { it.scheduleAtFixedRate({ runCatching { tick() } }, 1, 1, TimeUnit.SECONDS) }
    }

    @Synchronized
    fun stop() {
        executor?.shutdownNow()
        executor = null
    }

    internal fun tick() {
        val now = clock()
        val read = runCatching { totals() }.getOrNull()
        val (down, up) = read ?: (lastDown to lastUp)
        val seconds = (now - lastAt).coerceAtLeast(1).toDouble() / 1000.0
        val downBps = if (read == null) 0L else ((down - lastDown).coerceAtLeast(0) / seconds).toLong()
        val upBps = if (read == null) 0L else ((up - lastUp).coerceAtLeast(0) / seconds).toLong()
        lastDown = down
        lastUp = up
        lastAt = now
        report(elapsedSeconds(now), downBps, upBps, down, up)
    }

    internal fun elapsedSeconds(now: Long): Int = ((now - startedAt + 500) / 1000).coerceAtLeast(0).toInt()

    internal fun startAt(millis: Long) {
        startedAt = millis
        lastAt = millis
    }
}
