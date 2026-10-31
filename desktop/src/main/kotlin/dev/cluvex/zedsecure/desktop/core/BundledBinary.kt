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
        if (BundledBinary::class.java.getResource(res) == null) {
            System.err.println("bundled $name not found: $res")
            return null
        }
        return out.takeIf { BinaryInstall.install(res, it, executable = false) && it.isFile && it.length() > 0 }
    }
}
