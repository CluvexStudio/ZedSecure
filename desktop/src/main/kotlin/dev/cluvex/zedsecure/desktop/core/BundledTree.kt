package dev.cluvex.zedsecure.desktop.core

import java.io.File

internal object BundledTree {
    fun extract(destination: File, name: String): File? {
        val sub = when (Os.current) {
            Os.LINUX -> "linux"
            Os.MACOS -> "macos"
            Os.WINDOWS -> "windows"
            else -> return null
        }
        val base = "/bin/$sub/$name"
        val index = BundledTree::class.java.getResourceAsStream("$base/files.txt") ?: run {
            System.err.println("bundled $name not found: $base")
            return null
        }
        val files = index.bufferedReader().use { it.readLines() }
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("/") && ".." !in it.split('/') }
        if (files.isEmpty()) return null
        destination.mkdirs()
        for (relative in files) {
            val target = File(destination, relative)
            target.parentFile?.mkdirs()
            val stream = BundledTree::class.java.getResourceAsStream("$base/$relative") ?: run {
                System.err.println("bundled $name is missing $relative")
                return null
            }
            val copied = runCatching { stream.use { input -> target.outputStream().use { input.copyTo(it) } } }
            if (copied.isFailure && !(target.isFile && target.length() > 0)) {
                System.err.println("could not extract $relative: ${copied.exceptionOrNull()?.message}")
                return null
            }
            target.setExecutable(true, false)
        }
        return destination
    }
}
