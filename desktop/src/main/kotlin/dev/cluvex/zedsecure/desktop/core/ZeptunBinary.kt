package dev.cluvex.zedsecure.desktop.core

import java.io.File

object ZeptunBinary {
    val WINDOWS_COMPANIONS = listOf("wintun.dll")

    private val STRICT_FIREWALL = listOf(1, 1, 2)

    @Volatile private var known: Pair<String, List<Int>>? = null

    fun extract(workDir: File): File? =
        BundledBinary.extract(workDir, "zeptun", "zeptun.exe", WINDOWS_COMPANIONS)

    fun hasStrictFirewall(zeptun: File): Boolean {
        val version = version(zeptun) ?: return false
        return compareVersions(version, STRICT_FIREWALL) >= 0
    }

    private fun version(zeptun: File): List<Int>? {
        val key = "${zeptun.absolutePath}:${zeptun.length()}:${zeptun.lastModified()}"
        known?.takeIf { it.first == key }?.let { return it.second }
        val parsed = parseVersion(exec(zeptun.absolutePath, "version", timeoutSec = 10).second) ?: return null
        known = key to parsed
        return parsed
    }

    internal fun parseVersion(output: String): List<Int>? =
        Regex("""zeptun\s+(\d+)\.(\d+)\.(\d+)""").find(output)?.groupValues?.drop(1)?.map { it.toInt() }

    internal fun compareVersions(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val d = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (d != 0) return d
        }
        return 0
    }
}
