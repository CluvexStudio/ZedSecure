package dev.cluvex.zedsecure.desktop.core

import java.io.File

object HevBinary {
    val WINDOWS_COMPANIONS = listOf("msys-2.0.dll", "wintun.dll")

    fun extract(workDir: File): File? =
        BundledBinary.extract(workDir, "hev-socks5-tunnel", "hev-socks5-tunnel.exe", WINDOWS_COMPANIONS)
}
