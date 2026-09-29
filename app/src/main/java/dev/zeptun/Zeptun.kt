package dev.zeptun

object Zeptun {
    external fun nativeStart(service: Any?, fd: Int, config: String?): Int

    external fun nativeStop()

    external fun nativeVersion(): String

    external fun nativeCounter(index: Int): Long

    init {
        System.loadLibrary("zeptun-jni")
    }
}
