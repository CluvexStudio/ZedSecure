package dev.cluvex.zedsecure.desktop.core

object PhysicalInterface {
    fun detect(os: Os = Os.current): String? = runCatching {
        when (os) {
            Os.WINDOWS -> WinApi.bestInterfaceAlias(byteArrayOf(1, 1, 1, 1))?.takeIf { it != ZeptunTun.ADAPTER }
            Os.MACOS -> exec("route", "-n", "get", "default", timeoutSec = 10)
                .second.let(::macInterface)
            Os.LINUX -> exec("ip", "-o", "route", "get", "1.1.1.1", timeoutSec = 10)
                .takeIf { it.first == 0 }?.second?.let(::linuxInterface)
            else -> null
        }
    }.getOrNull()

    fun canBind(os: Os = Os.current, kernel: String = System.getProperty("os.version").orEmpty()): Boolean {
        if (os != Os.LINUX) return true
        val (major, minor) = Regex("""^(\d+)\.(\d+)""").find(kernel)?.destructured?.let { (a, b) -> a.toInt() to b.toInt() }
            ?: return false
        return major > 5 || (major == 5 && minor >= 7)
    }

    internal fun linuxInterface(output: String): String? =
        Regex("""\bdev\s+(\S+)""").find(output)?.groupValues?.get(1)?.takeIf { it != LinuxBroker.TUN_NAME && it != "lo" }

    internal fun macInterface(output: String): String? =
        output.lineSequence().map { it.trim() }
            .firstOrNull { it.startsWith("interface:") }
            ?.substringAfter(":")?.trim()
            ?.takeIf { it.isNotEmpty() && !it.startsWith("utun") }
}
