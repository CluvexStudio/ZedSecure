package dev.cluvex.zedsecure.desktop.core

import java.io.File

internal object BundledBinary {
    fun extract(workDir: File, unixName: String, windowsName: String, windowsCompanions: List<String> = emptyList()): File? {
        val (sub, name) = when (Os.current) {
            Os.LINUX -> "linux" to unixName
            Os.MACOS -> "macos" to unixName
            Os.WINDOWS -> "windows" to windowsName
            else -> return null
        }
        val out = copy(workDir, sub, name) ?: return null
        if (Os.current == Os.WINDOWS) {
            windowsCompanions.forEach { companion ->
                copy(workDir, sub, companion)
                    ?: System.err.println("bundled $companion missing next to $name; it will not start")
            }
        }
        out.setExecutable(true)
        return out
    }

    private fun copy(workDir: File, sub: String, name: String): File? {
        val res = "/bin/$sub/$name"
        val out = File(workDir, name)
        val stream = BundledBinary::class.java.getResourceAsStream(res) ?: run {
            System.err.println("bundled $name not found: $res")
            return null
        }
        return runCatching {
            stream.use { input -> out.outputStream().use { input.copyTo(it) } }
            out
        }.getOrElse {
            runCatching { stream.close() }
            out.takeIf { it.isFile && it.length() > 0 }
        }
    }
}
