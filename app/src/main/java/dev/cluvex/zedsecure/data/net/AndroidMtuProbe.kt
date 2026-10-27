package dev.cluvex.zedsecure.data.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.SystemClock
import dev.cluvex.zedsecure.data.net.MtuProbe
import dev.cluvex.zedsecure.data.net.MtuVerdict
import java.util.concurrent.TimeUnit

object AndroidMtuProbe {
    fun install(context: Context) {
        val app = context.applicationContext
        MtuProbe.linkMtu = { linkMtu(app) }
        MtuProbe.vpnActive = { vpnActive(app) }
        MtuProbe.probe = { host, payload, timeoutMs -> ping(host, payload, timeoutMs) }
    }

    private fun cm(context: Context): ConnectivityManager? =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private fun linkMtu(context: Context): Int? {
        // LinkProperties.mtu was added in API 29; keep API 24–28 devices safe.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching {
            val manager = cm(context) ?: return@runCatching null
            val network = manager.activeNetwork ?: return@runCatching null
            manager.getLinkProperties(network)?.mtu?.takeIf { it in 576..9000 }
        }.getOrNull()
    }

    private fun vpnActive(context: Context): Boolean = runCatching {
        val manager = cm(context) ?: return@runCatching false
        val network = manager.activeNetwork ?: return@runCatching false
        val caps = manager.getNetworkCapabilities(network) ?: return@runCatching false
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }.getOrDefault(false)

    private fun ping(host: String, payload: Int, timeoutMs: Int): MtuVerdict {
        val seconds = (timeoutMs / 1000).coerceAtLeast(1)
        val process = runCatching {
            ProcessBuilder(
                "/system/bin/ping",
                "-M", "do",
                "-s", payload.toString(),
                "-c", "1",
                "-W", seconds.toString(),
                host,
            ).redirectErrorStream(true).start()
        }.getOrNull() ?: return MtuVerdict.Unavailable

        val finished = runCatching {
            waitForProcess(process, (seconds + 2L) * 1000L)
        }.getOrDefault(false)

        if (!finished) {
            // Process.destroy() is available on the app's full supported API range.
            runCatching { process.destroy() }
            return MtuVerdict.Unavailable
        }

        val output = runCatching {
            process.inputStream.bufferedReader().use { it.readText() }
        }.getOrElse {
            runCatching { process.destroy() }
            return MtuVerdict.Unavailable
        }

        return classify(output)
    }

    private fun waitForProcess(process: Process, timeoutMs: Long): Boolean {
        // The timed Process.waitFor overload is API 26+. Use a portable polling
        // fallback on API 24–25 rather than invoking a newer API on older devices.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        }

        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            try {
                process.exitValue()
                return true
            } catch (_: IllegalThreadStateException) {
                SystemClock.sleep(50L)
            }
        }

        return try {
            process.exitValue()
            true
        } catch (_: IllegalThreadStateException) {
            false
        }
    }

    internal fun classify(output: String): MtuVerdict {
        val lower = output.lowercase()
        return when {
            "message too long" in lower -> MtuVerdict.TooBig
            "frag needed" in lower -> MtuVerdict.TooBig
            RECEIVED.find(lower)?.groupValues?.get(1)?.toIntOrNull()?.let { it > 0 } == true ->
                MtuVerdict.Fits

            else -> MtuVerdict.NoReply
        }
    }

    private val RECEIVED = Regex("(\\d+)\\s+received")
}
