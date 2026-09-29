package dev.cluvex.zedsecure.core

import dev.cluvex.zedsecure.core.AppLog as Log
import dev.cluvex.zedsecure.domain.config.DnsTunnelEngineConfig
import dev.cluvex.zedsecure.domain.config.DnsTunnelProfile
import java.net.InetSocketAddress
import java.net.ServerSocket

class DnsTunnelController(
    private val settings: DnsTunnelProfile,
    private val listenPort: Int,

    private val maxListenPort: Int = listenPort + 9,
    private val listenHost: String = "127.0.0.1",

    private val pool: List<String> = emptyList(),

    private val poolFullVerification: Boolean = false,

    private val upstreamSocks: String? = null,
    private val upstreamSocksUser: String = "",
    private val upstreamSocksPass: String = "",
) {
    private var session: zeddns.Session? = null
    @Volatile private var boundPort: Int = 0

    val isRunning: Boolean get() = runCatching { session?.active() }.getOrNull() == true

    @Volatile var lastError: String? = null

    @Synchronized
    fun start(): Int {
        lastError = null
        if (settings.domain.isBlank() || settings.publicKey.isBlank()) {
            lastError = "domain and public key are required"
            Log.e(TAG, lastError!!)
            return -1
        }
        val port = firstFreePort(listenPort)
        if (port < 0) {
            lastError = "no free local port near $listenPort"
            Log.e(TAG, lastError!!)
            return -1
        }
        val listenAddr = "$listenHost:$port"

        val effective = if (pool.isNotEmpty()) {
            val fastest = DnsProbe.fastest(
                pool,
                count = 3,

                tunnelDomain = settings.domain,
                fullVerification = poolFullVerification,
            )
            if (fastest.isNotEmpty()) {
                Log.i(TAG, "DNS pool picked: ${fastest.joinToString()}")
                settings.copy(resolvers = fastest.joinToString(","))
            } else settings
        } else settings

        val dnsAddr = resolveResolverHosts(effective).dnsAddress()
        return try {
            val via = upstreamSocks?.let { DnsTunnelEngineConfig.Via(it, upstreamSocksUser, upstreamSocksPass) }
            session = zeddns.Zeddns.open(DnsTunnelEngineConfig.build(settings, listenAddr, dnsAddr, via))
            boundPort = port
            Log.i(TAG, "${settings.engine} started on $listenAddr (resolver=$dnsAddr, domain=${settings.domain})")
            port
        } catch (e: Throwable) {
            lastError = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
            Log.e(TAG, "${settings.engine} failed to start", e)
            stop()
            -1
        }
    }

    @Synchronized
    fun stop() {
        val s = session
        session = null
        boundPort = 0

        if (s != null) runCatching { s.close() }
    }

    private fun resolveResolverHosts(p: DnsTunnelProfile): DnsTunnelProfile {
        if (p.dnsTransport == DnsTunnelProfile.TRANSPORT_DOH || p.resolvers.isBlank()) return p
        val resolved = p.resolvers.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }.map { hp ->
            val ipv6 = hp.startsWith("[")
            val sep = if (ipv6) hp.indexOf("]:") else hp.lastIndexOf(':')
            val host = if (sep >= 0) (if (ipv6) hp.substring(1, sep) else hp.substring(0, sep)) else hp.trim('[', ']')
            val portSuffix = if (sep >= 0) hp.substring(if (ipv6) sep + 1 else sep) else ""
            if (isNumericHost(host)) hp
            else runCatching { java.net.InetAddress.getByName(host).hostAddress }.getOrNull()?.let { "$it$portSuffix" } ?: hp
        }
        return p.copy(resolvers = resolved.joinToString(","))
    }

    private fun isNumericHost(h: String): Boolean =
        h.isNotEmpty() && (h.all { it.isDigit() || it == '.' } || h.contains(':'))

    private fun firstFreePort(preferred: Int): Int {
        if (!isPortInUse(preferred)) return preferred
        for (p in (preferred + 1)..maxListenPort) if (!isPortInUse(p)) return p
        return -1
    }

    private fun isPortInUse(port: Int): Boolean = try {
        ServerSocket().use { it.reuseAddress = true; it.bind(InetSocketAddress("127.0.0.1", port)); false }
    } catch (e: Exception) {
        true
    }

    private companion object {
        const val TAG = "DnsTunnel"
    }
}
