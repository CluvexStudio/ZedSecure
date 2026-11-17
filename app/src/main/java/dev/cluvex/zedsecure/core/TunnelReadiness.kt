package dev.cluvex.zedsecure.core

import dev.cluvex.zedsecure.core.AppLog as Log
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

object TunnelReadiness {
    private const val TAG = "TunnelReadiness"

    private const val PROBE_HOST = "cp.cloudflare.com"
    private const val PROBE_PORT = 80

    const val DNS_TUNNEL_TIMEOUT_MS = 30_000

    fun verify(
        socksPort: Int,
        timeoutMs: Int = DNS_TUNNEL_TIMEOUT_MS,
        host: String = PROBE_HOST,
        port: Int = PROBE_PORT,

        attempts: Int = 1,
    ): Boolean {
        repeat(attempts) { i ->
            if (attempt(socksPort, timeoutMs, host, port)) return true
            if (i < attempts - 1) Thread.sleep(700)
        }
        return false
    }

    /**
     * Verify a SOCKS tunnel against several independent HTTP endpoints.
     * A single probe host being filtered must not make a healthy tunnel look dead.
     */
    fun verifyAny(
        socksPort: Int,
        timeoutMs: Int = DNS_TUNNEL_TIMEOUT_MS,
        hosts: List<String> = listOf(PROBE_HOST, "www.cloudflare.com", "example.com"),
        port: Int = PROBE_PORT,
        connectTimeoutMs: Int = 2_000,
    ): Boolean {
        for (host in hosts.distinct().filter(String::isNotBlank)) {
            if (attempt(socksPort, timeoutMs, host, port, connectTimeoutMs)) {
                Log.i(TAG, "readiness probe succeeded via $host:$port")
                return true
            }
        }
        Log.w(TAG, "readiness probe failed for all ${hosts.size} endpoints on SOCKS port $socksPort")
        return false
    }
    private fun attempt(
        socksPort: Int,
        timeoutMs: Int,
        host: String,
        port: Int,
        connectTimeoutMs: Int = 5_000,
    ): Boolean = runCatching {
        Socket().use { socket ->
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress("127.0.0.1", socksPort), connectTimeoutMs.coerceAtLeast(250))
            socket.soTimeout = timeoutMs
            val out = socket.getOutputStream()
            val input = socket.getInputStream()
            if (!socks5Connect(out, input, host, port)) return@runCatching false

            out.write(
                ("HEAD / HTTP/1.1\r\nHost: $host\r\nUser-Agent: ZedSecure\r\nConnection: close\r\n\r\n")
                    .toByteArray(),
            )
            out.flush()
            input.read() >= 0
        }
    }.getOrElse {
        Log.w(TAG, "readiness probe via 127.0.0.1:$socksPort failed: ${it.message}")
        false
    }

    private fun socks5Connect(out: OutputStream, input: InputStream, host: String, port: Int): Boolean {
        val credentials = dev.cluvex.zedsecure.domain.config.LocalProxyAuth.current
        out.write(if (credentials != null) byteArrayOf(0x05, 0x02, 0x00, 0x02) else byteArrayOf(0x05, 0x01, 0x00))
        out.flush()
        val greeting = input.readExactly(2) ?: return false
        if (greeting[0] != 0x05.toByte()) return false
        when (greeting[1]) {
            0x00.toByte() -> Unit
            0x02.toByte() -> {
                val auth = credentials ?: return false
                val user = auth.user.toByteArray()
                val pass = auth.pass.toByteArray()
                out.write(byteArrayOf(0x01, user.size.toByte()) + user + byteArrayOf(pass.size.toByte()) + pass)
                out.flush()
                val reply = input.readExactly(2) ?: return false
                if (reply[1] != 0x00.toByte()) return false
            }
            else -> return false
        }

        val domain = host.toByteArray()
        val request = ByteArray(7 + domain.size)
        request[0] = 0x05
        request[1] = 0x01
        request[2] = 0x00
        request[3] = 0x03
        request[4] = domain.size.toByte()
        domain.copyInto(request, 5)
        request[5 + domain.size] = ((port shr 8) and 0xFF).toByte()
        request[6 + domain.size] = (port and 0xFF).toByte()
        out.write(request)
        out.flush()

        val head = input.readExactly(4) ?: return false
        if (head[1] != 0x00.toByte()) return false
        val skip = when (head[3]) {
            0x01.toByte() -> 4
            0x04.toByte() -> 16
            0x03.toByte() -> (input.read().takeIf { it >= 0 } ?: return false)
            else -> return false
        }
        return input.readExactly(skip + 2) != null
    }

    private fun InputStream.readExactly(n: Int): ByteArray? {
        val buf = ByteArray(n)
        var read = 0
        while (read < n) {
            val r = read(buf, read, n - read)
            if (r < 0) return null
            read += r
        }
        return buf
    }
}
