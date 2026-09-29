package dev.cluvex.zedsecure.core

import android.content.Context
import dev.cluvex.zedsecure.core.AppLog as Log
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class SniSpoofController(
    private val context: Context,
    private val connectTarget: String,
    private val fakeSni: String,
    private val listenPort: Int,
    private val listenHost: String = "127.0.0.1",

    private val utls: String = "firefox",
) {
    private val active = AtomicBoolean(false)
    private var process: Process? = null

    @Volatile
    var ackTimeouts: Int = 0
        private set

    val isRunning: Boolean get() = active.get()

    fun start(): Boolean {
        if (!rootAvailable()) {
            Log.e(TAG, "root not available")
            return false
        }

        clearLeftovers()
        for (injector in listOf("active", "passive")) {
            if (launch(injector)) {
                Log.i(TAG, "sni-spoof running ($injector): $listenHost:$listenPort → $connectTarget " +
                    "(fake=$fakeSni utls=$utls)")
                return true
            }
            Log.w(TAG, "sni-spoof did not come up with the $injector injector")
            active.set(false)
            runCatching { process?.destroy() }
            process = null
            clearLeftovers()
        }
        Log.e(TAG, "listener never bound on $listenHost:$listenPort with either injector")
        return false
    }

    private fun launch(injector: String): Boolean = try {
        val bin = "${context.applicationInfo.nativeLibraryDir}/libsnispoof.so"

        val command = listOf(
            bin,
            "-listen", "$listenHost:$listenPort",
            "-connect", connectTarget,
            "-fake-sni", fakeSni,
            "-utls", utls,
            "-injector", injector,

            "-config", "/dev/null",
        ).joinToString(" ") { shellQuote(it) }
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", command))

        runCatching { p.outputStream.close() }
        process = p
        active.set(true)
        pump(p.inputStream)
        pump(p.errorStream)

        awaitListener()
    } catch (e: Exception) {
        Log.e(TAG, "failed to start ($injector)", e)
        false
    }

    private fun clearLeftovers() {
        val edge = connectTarget.substringBeforeLast(':').trim('[', ']')
        val script = "pkill -f 'libsnispoo[f].so'; " +
            "i=0; while pgrep -f 'libsnispoo[f].so' >/dev/null && [ \$i -lt 25 ]; do sleep 0.2; i=\$((i+1)); done; " +
            "pkill -9 -f 'libsnispoo[f].so'; " +

            "for c in OUTPUT INPUT; do iptables -w -S \$c 2>/dev/null | grep -F -- ${shellQuote("$edge/32")} | " +
            "grep -F -- '-j NFQUEUE' | sed 's/^-A /-D /' | while read -r r; do iptables -w \$r; done; done"
        runRoot(script, timeoutMs = 10_000)
    }

    private fun runRoot(script: String, timeoutMs: Long) {
        runCatching {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", script))
            runCatching { p.outputStream.close() }
            pumpQuietly(p.inputStream)
            pumpQuietly(p.errorStream)
            val waiter = Thread { runCatching { p.waitFor() } }.apply { isDaemon = true }
            waiter.start()
            waiter.join(timeoutMs)
            if (waiter.isAlive) runCatching { p.destroy() }
        }
    }

    private fun pumpQuietly(stream: java.io.InputStream) {
        Thread {
            runCatching { stream.use { val buf = ByteArray(4096); while (it.read(buf) >= 0) Unit } }
        }.apply { isDaemon = true }.start()
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private fun awaitListener(): Boolean {
        val deadline = System.currentTimeMillis() + BIND_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val p = process
            if (p != null && !p.isAlive()) return false
            val ok = runCatching {
                Socket().use { it.connect(InetSocketAddress(listenHost, listenPort), 1000); true }
            }.getOrDefault(false)
            if (ok) return true
            Thread.sleep(200)
        }
        return false
    }

    private val ACK_TIMEOUT_MARKER = "ACK timeout"

    private fun Process.isAlive(): Boolean = runCatching { exitValue(); false }.getOrElse { true }

    private fun pump(stream: java.io.InputStream) {
        Thread {
            var logged = 0
            var suppressed = 0L
            runCatching {
                stream.bufferedReader().forEachLine { line ->
                    if (line.contains(ACK_TIMEOUT_MARKER)) ackTimeouts++
                    if (logged < MAX_LOGGED_LINES) {
                        Log.i(TAG, line)
                        logged++
                        if (logged == MAX_LOGGED_LINES) {
                            Log.i(TAG, "further sni-spoof output suppressed (see the forwarder itself)")
                        }
                    } else {
                        suppressed++
                    }
                }
            }
            if (suppressed > 0) Log.i(TAG, "sni-spoof suppressed $suppressed further lines")
        }.apply { isDaemon = true }.start()
    }

    fun stop() {
        active.set(false)

        runRoot("pkill -f 'libsnispoo[f].so'", timeoutMs = 4_000)
        runCatching { process?.destroy() }
        process = null
    }

    companion object {
        private const val TAG = "SniSpoof"

        private const val BIND_TIMEOUT_MS = 6_000L

        private const val MAX_LOGGED_LINES = 50

        @Volatile private var cachedRoot = false

        fun rootAvailable(): Boolean {
            if (cachedRoot) return true
            return runCatching {
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
                val out = StringBuilder()
                Thread { runCatching { p.inputStream.bufferedReader().forEachLine { out.append(it) } } }
                    .apply { isDaemon = true }.start()

                val watchdog = Thread { runCatching { p.waitFor() } }.apply { isDaemon = true }
                watchdog.start()
                watchdog.join(4000)
                if (watchdog.isAlive) { runCatching { p.destroy() }; return false }
                val ok = out.toString().contains("uid=0")
                if (ok) cachedRoot = true
                ok
            }.getOrDefault(false)
        }
    }
}
