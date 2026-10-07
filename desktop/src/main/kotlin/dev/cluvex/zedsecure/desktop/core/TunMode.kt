package dev.cluvex.zedsecure.desktop.core

import java.io.File

class TunMode(
    private val socksPort: Int,
    private val workDir: File,

    private val bypassIps: List<String> = emptyList(),

    private val udpOverTcp: Boolean = false,

    private val dnsServers: String = ZeptunTun.DEFAULT_DNS,

    private val askPassword: ((retry: Boolean) -> AdminAnswer?)? = null,
) : DesktopTun {
    @Volatile private var up = false

    override fun start(): Boolean {
        if (Os.current != Os.LINUX) return false
        if (!LinuxBroker.ensure(workDir, askPassword)) return false
        val dns = ZeptunTun.dnsAddress(dnsServers)
        val result = LinuxBroker.tunUp(socksPort, udpOverTcp, dns, bypassIps)
        result.exceptionOrNull()?.let { println("[tun] ${it.message}") }
        up = result.isSuccess
        return up
    }

    override fun stop() {
        if (!up) return
        up = false
        LinuxBroker.tunDown()
    }

    companion object {
        fun supported(os: Os = Os.current): Boolean = os == Os.LINUX || os == Os.WINDOWS || os == Os.MACOS

        fun usesZeptun(os: Os = Os.current): Boolean = os == Os.WINDOWS || os == Os.MACOS
    }

    object Factory {
        fun create(
            workDir: File,
            socksPort: Int,
            bypassIps: List<String>,
            udpOverTcp: Boolean,
            dnsServers: String,
            askPassword: ((retry: Boolean) -> AdminAnswer?)?,
            allowApps: List<File> = emptyList(),
        ): DesktopTun? = if (usesZeptun()) {
            ZeptunBinary.extract(workDir)?.let {
                ZeptunTun(it, socksPort, workDir, bypassIps = bypassIps, udpOverTcp = udpOverTcp, dnsServers = dnsServers, allowApps = allowApps)
            }
        } else {
            TunMode(socksPort, workDir, bypassIps = bypassIps, udpOverTcp = udpOverTcp, dnsServers = dnsServers, askPassword = askPassword)
        }
    }
}
