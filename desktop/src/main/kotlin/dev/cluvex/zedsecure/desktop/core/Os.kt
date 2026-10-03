package dev.cluvex.zedsecure.desktop.core

enum class Os { LINUX, WINDOWS, MACOS, OTHER;
    companion object {
        val current: Os by lazy {
            val n = System.getProperty("os.name")?.lowercase().orEmpty()
            when {
                n.contains("linux") -> LINUX
                n.contains("win") -> WINDOWS
                n.contains("mac") || n.contains("darwin") -> MACOS
                else -> OTHER
            }
        }
    }
}

internal fun exec(vararg cmd: String, timeoutSec: Long = 30): Pair<Int, String> {
    return try {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val out = StringBuilder()
        val reader = Thread {
            runCatching {
                p.inputStream.bufferedReader().use { r ->
                    var line: String?
                    while (r.readLine().also { line = it } != null) {
                        out.append(line).append('\n')
                    }
                }
            }
        }.apply { isDaemon = true; start() }
        val done = p.waitFor(timeoutSec, java.util.concurrent.TimeUnit.SECONDS)
        if (!done) {
            p.destroyForcibly()
            reader.interrupt()
            return -1 to out.toString().trimEnd()
        }
        reader.join(500)
        p.exitValue() to out.toString().trimEnd()
    } catch (e: Exception) {
        -1 to (e.message ?: "exec failed")
    }
}
