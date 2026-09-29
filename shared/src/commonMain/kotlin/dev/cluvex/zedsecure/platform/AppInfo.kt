package dev.cluvex.zedsecure.platform

import kotlin.concurrent.Volatile
import kotlin.random.Random

object AppInfo {
    @Volatile
    var versionName: String = "3.0.9"

    val userAgent: String get() = "ZedSecure/$versionName"
}

object DeviceIdentity {
    @Volatile
    var id: String = ""
}

internal fun newId(): String = buildString {
    val hex = "0123456789abcdef"
    repeat(32) { append(hex[Random.nextInt(16)]) }
}
