package dev.cluvex.zedsecure.core

import dev.cluvex.zedsecure.domain.config.DnsTunnelEngineConfig
import dev.cluvex.zedsecure.domain.config.DnsTunnelProfile
import dev.cluvex.zedsecure.domain.config.ProfileSource
import dev.cluvex.zedsecure.domain.config.VpnProfile
import dev.cluvex.zedsecure.scan.TunnelProbe
import dev.cluvex.zedsecure.scan.TunnelTester
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI

class AndroidTunnelTester : TunnelTester {
    override fun supports(profile: VpnProfile): Boolean {
        val src = profile.source as? ProfileSource.DnsTunnel ?: return false
        return src.settings.dnsTransport != DnsTunnelProfile.TRANSPORT_DOH && src.settings.domain.isNotBlank()
    }

    override suspend fun test(profile: VpnProfile, resolver: String, full: Boolean, url: String, timeoutMs: Int): TunnelProbe =
        withContext(Dispatchers.IO) {
            val settings = (profile.source as? ProfileSource.DnsTunnel)?.settings
                ?: return@withContext TunnelProbe(ok = false, error = "unsupported")
            val one = settings.copy(resolvers = resolver, resolverMode = DnsTunnelProfile.MODE_FANOUT)
            val port = ServerSocket(0).use { it.localPort }
            val start = System.nanoTime()
            val session = try {
                zeddns.Zeddns.open(DnsTunnelEngineConfig.build(one, "127.0.0.1:$port", one.dnsAddress()))
            } catch (e: Throwable) {
                return@withContext TunnelProbe(ok = false, error = e.message ?: e.javaClass.simpleName)
            }
            try {
                Socket().use { s ->
                    s.connect(InetSocketAddress("127.0.0.1", port), 3000)
                    s.soTimeout = timeoutMs
                    val out = s.getOutputStream()
                    val inp = s.getInputStream()
                    out.write(byteArrayOf(5, 1, 0))
                    out.flush()
                    val greet = readN(inp, 2)
                    require(greet[0] == 5.toByte() && greet[1] == 0.toByte()) { "SOCKS greeting refused" }
                    val ssh = settings.sshEnabled && settings.sshHost.isNotBlank()
                    val target = if (ssh) Target(settings.sshHost, settings.sshPort, "") else targetOf(url)
                    val host = target.host.toByteArray()
                    out.write(byteArrayOf(5, 1, 0, 3, host.size.toByte()) + host + byteArrayOf((target.port shr 8).toByte(), target.port.toByte()))
                    out.flush()
                    val head = readN(inp, 4)
                    require(head[1] == 0.toByte()) { "tunnel could not connect (${head[1].toInt() and 0xFF})" }
                    when (head[3].toInt()) {
                        1 -> readN(inp, 6)
                        4 -> readN(inp, 18)
                        3 -> readN(inp, inp.read() + 2)
                    }
                    val setup = elapsed(start)
                    if (ssh) {
                        val banner = readLine(inp)
                        require(banner.startsWith("SSH-")) { "no SSH banner" }
                        return@withContext TunnelProbe(ok = true, setupMs = setup, totalMs = elapsed(start))
                    }
                    if (!full) return@withContext TunnelProbe(ok = true, setupMs = setup, totalMs = setup)
                    out.write("GET ${target.path} HTTP/1.1\r\nHost: ${target.host}\r\nUser-Agent: ZedSecure\r\nConnection: close\r\n\r\n".toByteArray())
                    out.flush()
                    val status = readLine(inp)
                    val code = status.split(' ').getOrNull(1)?.toIntOrNull() ?: 0
                    require(code in 200..399) { "HTTP $code" }
                    TunnelProbe(ok = true, setupMs = setup, totalMs = elapsed(start))
                }
            } catch (e: Throwable) {
                TunnelProbe(ok = false, totalMs = elapsed(start), error = e.message ?: e.javaClass.simpleName)
            } finally {
                runCatching { session.close() }
            }
        }

    private class Target(val host: String, val port: Int, val path: String)

    private fun targetOf(url: String): Target {
        val u = runCatching { URI(url.trim()) }.getOrNull()
        val host = u?.host?.takeIf { it.isNotBlank() } ?: "www.gstatic.com"
        val https = u?.scheme.equals("https", ignoreCase = true)
        val port = u?.port?.takeIf { it > 0 } ?: if (https) 443 else 80
        val path = u?.rawPath?.takeIf { it.isNotBlank() } ?: "/generate_204"
        return Target(host, port, path + (u?.rawQuery?.let { "?$it" } ?: ""))
    }

    private fun elapsed(start: Long): Int = ((System.nanoTime() - start) / 1_000_000).toInt()

    private fun readN(inp: InputStream, n: Int): ByteArray {
        val buf = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = inp.read(buf, off, n - off)
            if (r < 0) throw java.io.EOFException("closed")
            off += r
        }
        return buf
    }

    private fun readLine(inp: InputStream): String {
        val sb = StringBuilder()
        while (sb.length < 512) {
            val c = inp.read()
            if (c < 0 || c == '\n'.code) break
            if (c != '\r'.code) sb.append(c.toChar())
        }
        return sb.toString()
    }
}
