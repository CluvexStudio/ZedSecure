package dev.cluvex.zedsecure.platform

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Application context is set by the Android app entry if available.
 * Falls back to Unknown when context is missing.
 */
object AndroidNetworkContext {
    @Volatile
    var appContext: Context? = null
}

@SuppressLint("MissingPermission")
internal actual fun detectNetworkKind(): NetworkKind {
    return try {
        val ctx = AndroidNetworkContext.appContext ?: return NetworkKind.Unknown
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return NetworkKind.Unknown
        val network = cm.activeNetwork ?: return NetworkKind.Unknown
        val caps = cm.getNetworkCapabilities(network) ?: return NetworkKind.Unknown
        when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkKind.Wifi
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkKind.Cellular
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkKind.Ethernet
            else -> NetworkKind.Unknown
        }
    } catch (_: Throwable) {
        NetworkKind.Unknown
    }
}
