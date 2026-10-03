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
        val stream = BundledBinary::class.java.getResourceAsStream(res)
        if (stream != null) {
            return runCatching {
                stream.use { input -> out.outputStream().use { input.copyTo(it) } }
                out.setExecutable(true)
                out
            }.getOrElse {
                runCatching { stream.close() }
                out.takeIf { it.isFile && it.length() > 0 }?.also { it.setExecutable(true) }
            }
        }

        // Fallback for on-demand downloaded binaries not bundled in the jar
        if (out.isFile && out.length() > 0) {
            out.setExecutable(true)
            return out
        }

        val persistentCandidates = listOf(
            File(System.getProperty("user.home"), ".config/zedsecure/bin/$name"),
            File(System.getProperty("user.home"), ".zedsecure/bin/$name"),
            File(System.getenv("APPDATA") ?: "", "ZedSecure/bin/$name"),
            File(System.getProperty("user.home"), "Library/Application Support/ZedSecure/bin/$name"),
        )
        for (candidate in persistentCandidates) {
            if (candidate.isFile && candidate.length() > 0) {
                runCatching { candidate.copyTo(out, overwrite = true) }
                if (out.isFile && out.length() > 0) {
                    out.setExecutable(true)
                    return out
                }
            }
        }

        System.err.println("bundled $name not found: $res")
        return null
    }
}
