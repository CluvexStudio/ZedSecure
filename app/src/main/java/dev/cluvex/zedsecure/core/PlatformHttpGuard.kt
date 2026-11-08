package dev.cluvex.zedsecure.core

import android.os.Looper
import dev.cluvex.zedsecure.core.AppLog as Log

object PlatformHttpGuard {
    private const val TAG = "PlatformHttpGuard"

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            if (thread !== Looper.getMainLooper().thread && isPlatformHttpBug(error)) {
                runCatching { Log.w(TAG, "ignored a platform HTTP failure on ${thread.name}", error) }
            } else {
                previous?.uncaughtException(thread, error)
            }
        }
    }

    private fun isPlatformHttpBug(error: Throwable): Boolean =
        error is NullPointerException && error.stackTrace.any { it.className.startsWith("com.android.okhttp.") }
}
