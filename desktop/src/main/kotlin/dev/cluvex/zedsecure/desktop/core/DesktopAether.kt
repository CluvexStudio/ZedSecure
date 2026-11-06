package dev.cluvex.zedsecure.desktop.core

import dev.cluvex.zedsecure.core.AetherProcess
import dev.cluvex.zedsecure.core.LogBus
import dev.cluvex.zedsecure.desktop.platform.DesktopKeyValueStore
import dev.cluvex.zedsecure.domain.config.AetherProfile
import java.io.File

class DesktopAether(
    private val profile: AetherProfile,
    private val workDir: File,
    val socksPort: Int,
    private val engineCgroup: String?,
) {
    @Volatile private var runner: AetherProcess? = null
    @Volatile private var stopped = false
    val isRunning: Boolean get() = !stopped && runner?.isRunning == true

    fun start(): Result<Unit> {
        val process = synchronized(this) {
            if (stopped) return Result.failure(IllegalStateException("Aether connection cancelled"))
            val binary = BundledBinary.extract(workDir, "aether", "aether.exe")
                ?: return Result.failure(IllegalStateException("Aether is not bundled for ${Os.current}"))
            AetherProcess(
                binary, File(DesktopKeyValueStore.configDir(), "aether"), profile, socksPort,
                launch = { EngineCgroup.start(it, engineCgroup) },
                log = { LogBus.append("I/Aether $it") },
            ).also { runner = it }
        }
        return process.start()
    }

    @Synchronized
    fun stop() {
        stopped = true
        runner?.stop()
        runner = null
    }
}
