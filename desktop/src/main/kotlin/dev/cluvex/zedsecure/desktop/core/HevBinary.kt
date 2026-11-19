package dev.cluvex.zedsecure.desktop.core

import java.io.File

object HevBinary {
    val WINDOWS_COMPANIONS = listOfNotNull(
        "msys-2.0.dll".takeIf { HevBinary::class.java.getResource("/bin/windows/$it") != null },
        "wintun.dll",
    )

    fun extract(workDir: File): File? =
        BundledBinary.extract(workDir, "hev-socks5-tunnel", "hev-socks5-tunnel.exe", WINDOWS_COMPANIONS)
}
