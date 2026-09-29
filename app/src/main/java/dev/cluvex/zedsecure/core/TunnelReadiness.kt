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

    private fun attempt(
        socksPort: Int,
        timeoutMs: Int,
        host: String,
        port: Int,
    ): Boolean = runCatching {
        Socket().use { socket ->
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress("127.0.0.1", socksPort), 5_000)
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
        out.write(byteArrayOf(0x05, 0x01, 0x00))
        out.flush()
        val greeting = input.readExactly(2) ?: return false
        if (greeting[0] != 0x05.toByte() || greeting[1] != 0x00.toByte()) return false

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
