package dev.cluvex.zedsecure.scan

import java.io.BufferedWriter
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

fun interface ScanProtector {
    fun protect(fd: Int): Boolean
}

object ZedScanNative {
    @Volatile
    private var loaded: Boolean? = null

    fun ensure(load: () -> Unit): Boolean {
        loaded?.let { return it }
        synchronized(this) {
            loaded?.let { return it }
            val ok = runCatching {
                load()
                version()
            }.isSuccess
            loaded = ok
            return ok
        }
    }

    @JvmStatic
    external fun start(config: String, protector: ScanProtector?): Long

    @JvmStatic
    external fun poll(handle: Long, waitMs: Int): String?

    @JvmStatic
    external fun stop(handle: Long)

    @JvmStatic
    external fun free(handle: Long)

    @JvmStatic
    external fun countryGroups(geoip: String, codes: String): String?

    @JvmStatic
    external fun version(): String
}

class NativeScanBackend private constructor(private val protector: ScanProtector?) : ScanBackend {
    override fun start(configJson: String): Long = ZedScanNative.start(configJson, protector)

    override fun poll(handle: Long, waitMs: Int): String? = ZedScanNative.poll(handle, waitMs)

    override fun stop(handle: Long) = ZedScanNative.stop(handle)

    override fun free(handle: Long) = ZedScanNative.free(handle)

    override fun countryGroups(geoipPath: String, codes: String): String? =
        runCatching { ZedScanNative.countryGroups(geoipPath, codes) }.getOrNull()

    companion object {
        fun create(load: () -> Unit, protector: ScanProtector?): ScanBackend? =
            if (ZedScanNative.ensure(load)) NativeScanBackend(protector) else null
    }
}

class ProcessScanBackend(private val command: List<String>) : ScanBackend {
    private class Run(val process: Process, val stdin: BufferedWriter) {
        val lines = LinkedBlockingQueue<String>()
        val ended = AtomicBoolean(false)
    }

    private val runs = HashMap<Long, Run>()
    private val ids = AtomicLong(1)

    override fun start(configJson: String): Long {
        val process = ProcessBuilder(command).redirectErrorStream(false).start()
        val stdin = process.outputStream.bufferedWriter()
        stdin.write(configJson.replace('\n', ' '))
        stdin.write("\n")
        stdin.flush()
        val run = Run(process, stdin)
        Thread({
            runCatching {
                process.inputStream.bufferedReader().useLines { seq -> seq.forEach { if (it.isNotBlank()) run.lines.put(it) } }
            }
            run.ended.set(true)
        }, "zedscan-root-out").apply { isDaemon = true }.start()
        Thread({
            runCatching {
                process.errorStream.use { err ->
                    val buf = ByteArray(4096)
                    while (err.read(buf) >= 0) Unit
                }
            }
        }, "zedscan-root-err").apply { isDaemon = true }.start()
        val id = ids.getAndIncrement()
        synchronized(runs) { runs[id] = run }
        return id
    }

    override fun poll(handle: Long, waitMs: Int): String? {
        val run = synchronized(runs) { runs[handle] } ?: return null
        val first = run.lines.poll(waitMs.toLong(), TimeUnit.MILLISECONDS)
        if (first == null) {
            return if (run.ended.get() && run.lines.isEmpty()) null else "[]"
        }
        val batch = ArrayList<String>()
        batch += first
        run.lines.drainTo(batch, 4000)
        return batch.joinToString(prefix = "[", postfix = "]", separator = ",")
    }

    override fun stop(handle: Long) {
        val run = synchronized(runs) { runs[handle] } ?: return
        runCatching { run.stdin.close() }
    }

    override fun free(handle: Long) {
        val run = synchronized(runs) { runs.remove(handle) } ?: return
        runCatching { run.stdin.close() }
        if (!waitExit(run.process, 2000)) runCatching { run.process.destroy() }
    }

    companion object {
        fun waitExit(p: Process, ms: Long): Boolean {
            val end = System.currentTimeMillis() + ms
            while (System.currentTimeMillis() < end) {
                if (runCatching { p.exitValue() }.isSuccess) return true
                Thread.sleep(50)
            }
            return runCatching { p.exitValue() }.isSuccess
        }

        fun hasRoot(su: String = "su"): Boolean = runCatching {
            val p = ProcessBuilder(su, "-c", "id").redirectErrorStream(true).start()
            val done = waitExit(p, 30_000)
            if (!done) {
                p.destroy()
                false
            } else {
                p.inputStream.bufferedReader().readText().contains("uid=0")
            }
        }.getOrDefault(false)
    }
}
