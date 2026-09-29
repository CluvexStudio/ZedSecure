package dev.cluvex.zedsecure.core

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
                val batch = ArrayList<String>(64)
                var lastFlush = SystemClock.elapsedRealtime()
                val reader = proc.inputStream.bufferedReader()
                reader.use {
                    while (isActive) {
                        val line = reader.readLine() ?: break

                        if (tagOf(line)?.let { it in LogBus.ownTags.value } == true) continue

                        if (MASTERDNS_CONSOLE in line) continue
                        batch += line
                        val now = SystemClock.elapsedRealtime()

                        if (batch.size >= 200 || now - lastFlush >= BATCH_MS || !reader.ready()) {
                            LogBus.appendAll(batch.toList())
                            batch.clear()
                            lastFlush = now
                        }
                    }
                }
                if (batch.isNotEmpty()) LogBus.appendAll(batch.toList())
            }
        }
    }

    private const val MASTERDNS_CONSOLE = "[MasterDnsVPN Client]"

    private val TAG_OF = Regex("^\\S+ \\S+ [VDIWEF]/([^(]+)\\(")

    private fun tagOf(line: String): String? = TAG_OF.find(line)?.groupValues?.get(1)?.trim()

    fun stop() {
        job?.cancel()
        job = null

        runCatching { process?.destroy() }
        process = null
    }
}
