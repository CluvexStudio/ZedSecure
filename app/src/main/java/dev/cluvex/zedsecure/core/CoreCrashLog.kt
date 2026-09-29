package dev.cluvex.zedsecure.core

import android.content.Context
import dev.cluvex.zedsecure.core.AppLog as Log
import libv2ray.Libv2ray
import java.io.File

internal object CoreCrashLog {
    private const val TAG = "CoreCrash"
    private const val FILE = "core-crash.txt"
    private const val LAST = "core-crash-last.txt"
    private const val SHOWN = 8 * 1024

    fun install(context: Context) {
        val dir = context.filesDir
        val problem = runCatching { Libv2ray.setCrashFile(File(dir, FILE).absolutePath) }
            .getOrElse { it.message ?: it.javaClass.simpleName }
        if (problem.isNotEmpty()) Log.w(TAG, "crash file unavailable: $problem")

        val previous = File(dir, "$FILE.prev")
        if (!previous.isFile || previous.length() == 0L) return
        val text = runCatching { previous.readText() }.getOrNull().orEmpty()
        runCatching {
            previous.copyTo(File(dir, LAST), overwrite = true)
            previous.delete()
        }
        if (text.isNotBlank()) Log.e(TAG, "the core failed during the last run:\n" + text.take(SHOWN))
    }
}
