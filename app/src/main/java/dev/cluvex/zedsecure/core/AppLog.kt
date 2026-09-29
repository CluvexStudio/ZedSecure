package dev.cluvex.zedsecure.core

import dev.cluvex.zedsecure.platform.InAppLog

object AppLog {
    const val VERBOSE = android.util.Log.VERBOSE
    const val DEBUG = android.util.Log.DEBUG
    const val INFO = android.util.Log.INFO
    const val WARN = android.util.Log.WARN
    const val ERROR = android.util.Log.ERROR

    private val pid = android.os.Process.myPid()

    fun d(tag: String, msg: String, tr: Throwable? = null): Int = emit(DEBUG, tag, msg, tr)
    fun i(tag: String, msg: String, tr: Throwable? = null): Int = emit(INFO, tag, msg, tr)
    fun w(tag: String, msg: String, tr: Throwable? = null): Int = emit(WARN, tag, msg, tr)
    fun w(tag: String, tr: Throwable?): Int = emit(WARN, tag, tr?.toString().orEmpty(), tr)
    fun e(tag: String, msg: String, tr: Throwable? = null): Int = emit(ERROR, tag, msg, tr)
    fun println(priority: Int, tag: String, msg: String): Int = emit(priority, tag, msg, null)

    private fun emit(priority: Int, tag: String, msg: String, tr: Throwable?): Int {
        val written = when (priority) {
            VERBOSE -> android.util.Log.v(tag, msg, tr)
            DEBUG -> android.util.Log.d(tag, msg, tr)
            INFO -> android.util.Log.i(tag, msg, tr)
            WARN -> android.util.Log.w(tag, msg, tr)
            else -> android.util.Log.e(tag, msg, tr)
        }
        val level = when (priority) {
            VERBOSE -> 'V'; DEBUG -> 'D'; INFO -> 'I'; WARN -> 'W'; else -> 'E'
        }
        InAppLog.write(level, tag, msg, tr, pid)
        return written
    }
}
