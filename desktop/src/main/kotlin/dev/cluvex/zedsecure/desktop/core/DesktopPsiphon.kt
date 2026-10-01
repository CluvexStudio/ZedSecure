package dev.cluvex.zedsecure.desktop.core

import dev.cluvex.zedsecure.domain.config.PsiphonConfigBuilder
import dev.cluvex.zedsecure.domain.config.PsiphonProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class DesktopPsiphon(
    private val profile: PsiphonProfile,
    private val workDir: File,
    val socksPort: Int,
    private val httpPort: Int,
) {
    @Volatile private var process: Process? = null

    fun start(onNotice: (Notice) -> Unit = {}, waitSec: Long = CONNECT_WAIT_SEC): Result<Unit> {
        val bin = BundledBinary.extract(workDir, "psiphon", "psiphon.exe")
            ?: return Result.failure(IllegalStateException("Psiphon is not bundled for ${Os.current}"))
        val dataDir = File(workDir, "psiphon-data").apply { mkdirs() }
        val config = File(workDir, "psiphon.config").apply {
            writeText(PsiphonConfigBuilder.build(profile, dataDir.absolutePath, socksPort, httpPort))
        }
        val started = try {
            ProcessBuilder(bin.absolutePath, "-config", config.absolutePath)
                .redirectErrorStream(true)
                .directory(workDir)
                .start()
        } catch (e: Exception) {
            return Result.failure(IllegalStateException("Psiphon failed to start: ${e.message}"))
        }
        process = started

        val connected = AtomicBoolean(false)
        val settled = CountDownLatch(1)
        Thread {
            runCatching {
                started.inputStream.bufferedReader().forEachLine { line ->
                    val notice = Notice.parse(line) ?: run {
                        if (line.isNotBlank()) println("[psiphon] $line")
                        return@forEachLine
                    }
                    if (notice.type in LOGGED_NOTICES) println("[psiphon] ${notice.type} ${notice.data}")
                    runCatching { onNotice(notice) }
                    if (notice.tunnels > 0 && connected.compareAndSet(false, true)) settled.countDown()
                }
            }
            settled.countDown()
        }.apply { isDaemon = true; name = "psiphon-output" }.start()

        settled.await(waitSec, TimeUnit.SECONDS)
        if (connected.get()) return Result.success(Unit)
        val reason = if (started.isAlive) "Psiphon did not connect in $waitSec seconds" else "Psiphon exited before connecting"
        stop()
        return Result.failure(IllegalStateException(reason))
    }

    fun stop() {
        val running = process ?: return
        process = null
        XrayCore.stopProcess(running)
    }

    data class Notice(val type: String, val data: JsonObject) {
        val tunnels: Int
            get() = if (type == "Tunnels") data["count"]?.jsonPrimitive?.intOrNull ?: 0 else 0

        fun string(key: String): String? = runCatching { data[key]?.jsonPrimitive?.content }.getOrNull()

        companion object {
            private val json = Json { ignoreUnknownKeys = true; isLenient = true }

            fun parse(line: String): Notice? {
                val text = line.trim()
                if (!text.startsWith("{")) return null
                val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return null
                val type = runCatching { root["noticeType"]?.jsonPrimitive?.content }.getOrNull() ?: return null
                return Notice(type, root["data"] as? JsonObject ?: JsonObject(emptyMap()))
            }
        }
    }

    private companion object {
        const val CONNECT_WAIT_SEC = 120L
        val LOGGED_NOTICES = setOf(
            "Tunnels", "ConnectedServerRegion", "ActiveTunnel", "ListeningSocksProxyPort",
            "ListeningHttpProxyPort", "Alert", "Error", "UpstreamProxyError", "SocksProxyPortInUse",
            "HttpProxyPortInUse",
        )
    }
}
