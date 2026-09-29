package dev.cluvex.zedsecure.platform

import dev.cluvex.zedsecure.core.LogBus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object InAppLog {
    private val time = ThreadLocal.withInitial { SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US) }

    fun write(level: Char, tag: String, message: String, error: Throwable?, pid: Int) {
        LogBus.noteOwnTag(tag)
        val head = "${time.get()!!.format(Date())} $level/$tag(${pid.toString().padStart(5)}): "
        val lines = buildList {
            message.lines().forEach { add(head + it) }

            error?.let { t ->
                add(head + t.toString())
                t.stackTrace.take(6).forEach { add("$head\tat $it") }
            }
        }
        LogBus.appendAll(lines)
    }
}
