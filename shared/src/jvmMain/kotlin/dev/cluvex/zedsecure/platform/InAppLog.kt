package dev.cluvex.zedsecure.platform

import dev.cluvex.zedsecure.core.LogBus
import dev.cluvex.zedsecure.core.LogEntry
import dev.cluvex.zedsecure.core.LogSource

object InAppLog {
    fun write(level: Char, tag: String, message: String, error: Throwable?, pid: Int) {
        LogBus.noteOwnTag(tag)
        val source = LogBus.sourceOfTag(tag)
        val now = System.currentTimeMillis()
        val prefix = if (source == LogSource.App) "$tag: " else ""
        val entries = buildList {
            message.lines().filter { it.isNotBlank() }.forEach { add(LogEntry(now, source, level, LogBus.clean(prefix + it))) }
            error?.let { t ->
                add(LogEntry(now, source, level, prefix + t.toString()))
                t.stackTrace.take(6).forEach { add(LogEntry(now, source, level, "    at $it")) }
            }
        }
        LogBus.add(entries)
    }
}
