package dev.cluvex.zedsecure.desktop.core

object PhysicalInterface {
    fun detect(os: Os = Os.current): String? = runCatching {
        when (os) {
            Os.WINDOWS -> exec(
                "powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                "Find-NetRoute -RemoteIPAddress 1.1.1.1 -ErrorAction SilentlyContinue | " +
                    "Where-Object { \$_.InterfaceAlias -and \$_.InterfaceAlias -ne '${ZeptunTun.ADAPTER}' } | " +
                    "Select-Object -First 1 -ExpandProperty InterfaceAlias",
                timeoutSec = 20,
            ).takeIf { it.first == 0 }?.second?.let(::firstLine)
            Os.MACOS -> exec("route", "-n", "get", "default", timeoutSec = 10)
                .second.let(::macInterface)
            else -> null
        }
    }.getOrNull()

    internal fun firstLine(output: String): String? =
        output.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }

    internal fun macInterface(output: String): String? =
        output.lineSequence().map { it.trim() }
            .firstOrNull { it.startsWith("interface:") }
            ?.substringAfter(":")?.trim()
            ?.takeIf { it.isNotEmpty() && !it.startsWith("utun") }
}
