package dev.cluvex.zedsecure.data.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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

    private fun linkMtu(context: Context): Int? = runCatching {
        val manager = cm(context) ?: return@runCatching null
        val network = manager.activeNetwork ?: return@runCatching null
        manager.getLinkProperties(network)?.mtu?.takeIf { it in 576..9000 }
    }.getOrNull()

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

        val output = runCatching {
            val text = process.inputStream.bufferedReader().use { it.readText() }

            if (!process.waitFor(seconds + 2L, TimeUnit.SECONDS)) process.destroy()
            text
        }.getOrElse {
            runCatching { process.destroy() }
            return MtuVerdict.Unavailable
        }

        return classify(output)
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
