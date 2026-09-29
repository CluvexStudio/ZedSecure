package dev.cluvex.zedsecure.platform

import java.io.IOException
import java.net.HttpURLConnection

fun HttpURLConnection.disconnectQuietly() {
    try {
        disconnect()
    } catch (_: RuntimeException) {
    }
}

fun platformHttpFailure(cause: NullPointerException): IOException = IOException("connection failed", cause)
