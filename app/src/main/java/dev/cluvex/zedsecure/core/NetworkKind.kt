package dev.cluvex.zedsecure.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dev.cluvex.zedsecure.domain.model.RulesetItem

object NetworkKind {
    fun current(context: Context): String? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val network = cm.activeNetwork ?: return null
        return of(cm.getNetworkCapabilities(network))
    }

    fun of(caps: NetworkCapabilities?): String? {
        caps ?: return null
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> RulesetItem.NETWORK_WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> RulesetItem.NETWORK_CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> RulesetItem.NETWORK_ETHERNET
            else -> RulesetItem.NETWORK_OTHER
        }
    }
}
