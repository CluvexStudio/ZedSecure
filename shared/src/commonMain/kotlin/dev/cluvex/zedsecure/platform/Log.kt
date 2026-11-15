package dev.cluvex.zedsecure.platform

expect fun platformLog(tag: String, message: String)


/** Compatibility facade for shared code that uses structured log levels. */
object Log {
    fun i(tag: String, message: String) = platformLog(tag, message)
    fun w(tag: String, message: String) = platformLog(tag, message)
}
