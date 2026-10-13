package dev.cluvex.zedsecure.desktop.core

import java.io.File

object EngineCgroup {
    const val MOUNT = "/sys/fs/cgroup"
    const val NAME = "zedsecure-engines"
    private val SAFE = Regex("[A-Za-z0-9@._:+-]+(/[A-Za-z0-9@._:+-]+)*")

    fun create(own: String? = currentPath(), mount: File = File(MOUNT)): String? {
        if (!File(mount, "cgroup.controllers").isFile) return null
        val base = own?.trim()?.trim('/')?.takeIf { it.isNotEmpty() } ?: return null
        val relative = "$base/$NAME"
        if (!isSafe(relative)) return null
        val dir = File(mount, relative)
        if (!dir.isDirectory && !dir.mkdir()) return null
        return relative.takeIf { File(dir, "cgroup.procs").canWrite() }
    }

    fun currentPath(): String? = runCatching {
        File("/proc/self/cgroup").readLines().firstOrNull { it.startsWith("0::") }?.removePrefix("0::")
    }.getOrNull()

    fun isSafe(relative: String): Boolean = SAFE.matches(relative) && relative.split('/').none { it == "." || it == ".." }

    fun wrap(command: List<String>, relative: String, mount: File = File(MOUNT)): List<String> =
        listOf("/bin/sh", "-c", "echo \$\$ > \"\$1\" && shift && exec \"\$@\"", "sh", File(mount, "$relative/cgroup.procs").path) + command

    fun remove(relative: String, mount: File = File(MOUNT)) {
        runCatching { File(mount, relative).delete() }
    }
}
