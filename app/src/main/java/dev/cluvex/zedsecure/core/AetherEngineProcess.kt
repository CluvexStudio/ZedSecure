package dev.cluvex.zedsecure.core

import java.io.File

/**
 * Process adapter for the separately packaged Aether executable.
 *
 * This deliberately does not own VpnService, TUN, profile selection, or any
 * existing ZX engine lifecycle. The service integration must explicitly create
 * and own this process after a dedicated Aether profile is selected.
 */
internal class AetherEngineProcess(
    private val nativeLibraryDir: File,
    private val workingDirectory: File,
) {
    @Volatile private var process: Process? = null
    private val recent = java.util.ArrayDeque<String>()

    /** True once the engine's scanner has reported at least one reachable gateway candidate. */
    @Volatile var candidateSeen: Boolean = false
        private set

    /** Last few engine output lines, for a readable error when the engine dies or never connects. */
    fun lastLines(): String = synchronized(recent) { recent.joinToString(" | ") }

    @Synchronized
    fun start(arguments: List<String>, environment: Map<String, String> = emptyMap()) {
        check(process?.let(::isProcessAlive) != true) { "Aether is already running" }
        AetherIdentityVault.sealAndShred(workingDirectory)
        AetherIdentityVault.unsealInto(workingDirectory)
        val executable = File(nativeLibraryDir, "libaether.so")
        check(executable.isFile) { "Aether engine is not packaged for this device ABI" }
        check(executable.canExecute()) { "Aether engine is not executable" }
        check(workingDirectory.exists() || workingDirectory.mkdirs()) {
            "Could not create Aether working directory"
        }

        val builder = ProcessBuilder(buildList {
            add(executable.absolutePath)
            addAll(arguments)
        }).directory(workingDirectory).redirectErrorStream(true)
        builder.environment().apply {
            putAll(environment)
            put("HOME", workingDirectory.absolutePath)
            put("TMPDIR", workingDirectory.absolutePath)
            put(
                "LD_LIBRARY_PATH",
                listOf(nativeLibraryDir.absolutePath, get("LD_LIBRARY_PATH").orEmpty())
                    .filter(String::isNotBlank).joinToString(":"),
            )
        }
        val child = builder.start()
        process = child
        Thread({
            runCatching {
                child.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach {
                        LogBus.append("I/Aether: " + it.take(MAX_LOG_LINE))
                        if (it.contains("candidate ok")) candidateSeen = true
                        synchronized(recent) {
                            recent.addLast(it.take(160))
                            while (recent.size > 4) recent.removeFirst()
                        }
                    }
                }
            }.onFailure { LogBus.append("W/Aether: output reader stopped (" + it.javaClass.simpleName + ")") }
        }, "ZX-Aether-logs").apply { isDaemon = true; start() }
    }

    @Synchronized
    fun isAlive(): Boolean = process?.let(::isProcessAlive) == true

    private fun isProcessAlive(child: Process): Boolean = try {
        child.exitValue()
        false
    } catch (_: IllegalThreadStateException) {
        true
    }

    fun awaitExit(): Int? = process?.waitFor()

    @Synchronized
    fun stop() {
        val child = process
        if (child != null) {
            child.destroy()
            val deadline = System.currentTimeMillis() + STOP_TIMEOUT_MS
            while (isProcessAlive(child) && System.currentTimeMillis() < deadline) {
                runCatching { Thread.sleep(30) }
            }
            if (isProcessAlive(child)) {
                // destroyForcibly was added after ZX's minSdk. Reflect it when
                // present, while keeping the normal path compatible with API 24+.
                runCatching { Process::class.java.getMethod("destroyForcibly").invoke(child) }
            }
            runCatching { child.waitFor() }
            process = null
        }
        AetherIdentityVault.sealAndShred(workingDirectory)
    }

    companion object {
        private const val TAG = "ZX-Aether"
        private const val MAX_LOG_LINE = 2048
        private const val STOP_TIMEOUT_MS = 1500L
    }
}
