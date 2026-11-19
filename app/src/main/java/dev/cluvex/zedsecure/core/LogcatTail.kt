package dev.cluvex.zedsecure.core

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

object LogcatTail {
    private const val SEED_LINES = 400

    private const val BATCH_MS = 250L

    private var job: Job? = null
    private var process: java.lang.Process? = null

    fun register(scope: CoroutineScope) {
        LogBus.source = { active -> if (active) start(scope) else stop() }
    }

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            runCatching {
                val proc = ProcessBuilder(
                    "logcat", "-v", "time", "-T", SEED_LINES.toString(), "--pid=${android.os.Process.myPid()}",
                ).redirectErrorStream(true).start()
                process = proc
                val batch = ArrayList<LogEntry>(64)
                var lastFlush = SystemClock.elapsedRealtime()
                val reader = proc.inputStream.bufferedReader()
                reader.use {
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        parse(line)?.let { batch += it }
                        val now = SystemClock.elapsedRealtime()
                        if (batch.size >= 200 || now - lastFlush >= BATCH_MS || !reader.ready()) {
                            if (batch.isNotEmpty()) LogBus.add(batch.toList())
                            batch.clear()
                            lastFlush = now
                        }
                    }
                }
                if (batch.isNotEmpty()) LogBus.add(batch.toList())
            }
        }
    }

    private const val MASTERDNS_CONSOLE = "[MasterDnsVPN Client]"

    private val SYSTEM_TAGS = setOf("AndroidRuntime", "System.err", "libc", "DEBUG", "CRASH")

    private val LINE = Regex("^(\\d\\d)-(\\d\\d) (\\d\\d):(\\d\\d):(\\d\\d)\\.(\\d{3}) ([VDIWEF])/([^(]+)\\(\\s*\\d+\\): ?(.*)$")

    private val LOGRUS = Regex("^time=\"[^\"]*\" level=(\\w+) msg=(?:\"((?:[^\"\\\\]|\\\\.)*)\"|(\\S+))(.*)$")

    private val XRAY_LEVEL = Regex("\\[(Debug|Info|Warning|Error)\\]")

    private val XRAY_STAMP = Regex("^\\d{4}/\\d\\d/\\d\\d \\d\\d:\\d\\d:\\d\\d(\\.\\d+)? ")

    private fun parse(line: String): LogEntry? {
        if (line.isBlank() || line.startsWith("---------")) return null
        val m = LINE.find(line) ?: return LogEntry(System.currentTimeMillis(), LogSource.App, 'I', LogBus.clean(line))
        val (month, day, hour, minute, second, millis, level, rawTag, message) = m.destructured
        val tag = rawTag.trim()
        if (tag in LogBus.ownTags.value) return null
        val time = stamp(month.toInt(), day.toInt(), hour.toInt(), minute.toInt(), second.toInt(), millis.toInt())
        if (tag == "GoLog") {
            if (MASTERDNS_CONSOLE in message) return null
            LOGRUS.find(message)?.let { r ->
                val lv = when (r.groupValues[1].lowercase()) {
                    "trace", "debug" -> 'D'
                    "warning", "warn" -> 'W'
                    "error", "fatal", "panic" -> 'E'
                    else -> 'I'
                }
                val text = r.groupValues[2].ifEmpty { r.groupValues[3] }.replace("\\\"", "\"") + r.groupValues[4]
                return LogEntry(time, LogSource.DnsTunnel, lv, LogBus.clean(text.trim()))
            }
            val lv = when (XRAY_LEVEL.find(message)?.groupValues?.get(1)) {
                "Debug" -> 'D'
                "Warning" -> 'W'
                "Error" -> 'E'
                "Info" -> 'I'
                else -> level.first()
            }
            return LogEntry(time, LogSource.Xray, lv, LogBus.clean(XRAY_STAMP.replace(message, "")))
        }
        val source = LogBus.sourceOfTag(tag)
        if (source == LogSource.App && tag !in SYSTEM_TAGS) return null
        val text = if (source == LogSource.App) "$tag: $message" else message
        return LogEntry(time, source, level.first(), LogBus.clean(text))
    }

    private fun stamp(month: Int, day: Int, hour: Int, minute: Int, second: Int, millis: Int): Long {
        val cal = Calendar.getInstance()
        val nowMonth = cal.get(Calendar.MONTH) + 1
        if (month > nowMonth) cal.add(Calendar.YEAR, -1)
        cal.set(Calendar.MONTH, month - 1)
        cal.set(Calendar.DAY_OF_MONTH, day)
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, second)
        cal.set(Calendar.MILLISECOND, millis)
        return cal.timeInMillis
    }

    fun stop() {
        job?.cancel()
        job = null

        runCatching { process?.destroy() }
        process = null
    }
}
