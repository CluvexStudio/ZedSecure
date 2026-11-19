package dev.cluvex.zedsecure.core

import android.net.VpnService

object VpnSocketGuard {
    @Volatile
    var service: VpnService? = null

    fun protect(fd: Int): Boolean = service?.let { runCatching { it.protect(fd) }.getOrDefault(false) } ?: true
}
