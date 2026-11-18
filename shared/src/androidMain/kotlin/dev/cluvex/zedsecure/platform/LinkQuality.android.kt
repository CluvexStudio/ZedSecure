package dev.cluvex.zedsecure.platform

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.TelephonyManager

@SuppressLint("MissingPermission")
internal actual fun detectLinkQuality(): LinkQuality? = try {
    val ctx = AndroidNetworkContext.appContext
    val cm = ctx?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
    if (ctx == null || cm == null || caps == null) {
        null
    } else {
        val cellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        LinkQuality(
            downKbps = caps.linkDownstreamBandwidthKbps,
            upKbps = caps.linkUpstreamBandwidthKbps,
            signalDbm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                caps.signalStrength.takeIf { it != Int.MIN_VALUE && it < 0 }
            } else null,
            metered = cm.isActiveNetworkMetered,
            validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            generation = if (cellular) generationOf(ctx) else null,
        )
    }
} catch (_: Throwable) {
    null
}

@SuppressLint("MissingPermission")
private fun generationOf(ctx: Context): String? = try {
    val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    when (tm?.dataNetworkType) {
        TelephonyManager.NETWORK_TYPE_GPRS, TelephonyManager.NETWORK_TYPE_EDGE,
        TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_1xRTT,
        TelephonyManager.NETWORK_TYPE_IDEN, TelephonyManager.NETWORK_TYPE_GSM -> "2G"
        TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_EVDO_0,
        TelephonyManager.NETWORK_TYPE_EVDO_A, TelephonyManager.NETWORK_TYPE_HSDPA,
        TelephonyManager.NETWORK_TYPE_HSUPA, TelephonyManager.NETWORK_TYPE_HSPA,
        TelephonyManager.NETWORK_TYPE_EVDO_B, TelephonyManager.NETWORK_TYPE_EHRPD,
        TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "3G"
        TelephonyManager.NETWORK_TYPE_LTE, TelephonyManager.NETWORK_TYPE_IWLAN -> "4G"
        TelephonyManager.NETWORK_TYPE_NR -> "5G"
        else -> null
    }
} catch (_: Throwable) {
    null
}
