package dev.cluvex.zedsecure.data.net

import dev.cluvex.zedsecure.domain.config.LocalProxyAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.coroutines.coroutineContext

internal actual fun openSpeedLink(host: String, socksPort: Int?): SpeedLink = JvmSpeedLink(host, socksPort)

private class JvmSpeedLink(private val host: String, private val socksPort: Int?) : SpeedLink {
    private var socket: SSLSocket? = null
    private var input: BufferedInputStream? = null
    private var output: BufferedOutputStream? = null

    override suspend fun get(path: String, keepBody: Boolean, onBytes: (Long) -> Boolean): SpeedResponse =
        exchange("GET", path, 0L, keepBody, onBytes)

    override suspend fun post(path: String, bytes: Long, onBytes: (Long) -> Boolean): SpeedResponse =
        exchange("POST", path, bytes, false, onBytes)

    override fun close() {
        runCatching { socket?.close() }
        socket = null
        input = null
        output = null
    }

    private suspend fun exchange(
        method: String,
        path: String,
        uploadBytes: Long,
        keepBody: Boolean,
        onBytes: (Long) -> Boolean,
    ): SpeedResponse = withContext(Dispatchers.IO) {
        val job = coroutineContext[Job]
        val cancelHandle = job?.invokeOnCompletion { if (job.isCancelled) close() }
        try {
            val reused = socket != null
            try {
                attempt(method, path, uploadBytes, keepBody, onBytes)
            } catch (e: StaleConnectionException) {
                close()
                if (!reused) throw e.cause ?: e
                attempt(method, path, uploadBytes, keepBody, onBytes)
            }
        } finally {
            cancelHandle?.dispose()
        }
    }

    private fun attempt(
        method: String,
        path: String,
        uploadBytes: Long,
        keepBody: Boolean,
        onBytes: (Long) -> Boolean,
    ): SpeedResponse {
        ensureOpen()
        val out = output ?: throw IOException("speed link is closed")
        val inp = input ?: throw IOException("speed link is closed")
        val head = buildString {
            append(method).append(' ').append(path).append(" HTTP/1.1\r\n")
            append("Host: ").append(host).append("\r\n")
            append("User-Agent: ZedSecure-SpeedTest\r\n")
            append("Accept: */*\r\n")
            append("Accept-Encoding: identity\r\n")
            append("Origin: https://").append(host).append("\r\n")
            append("Connection: keep-alive\r\n")
            if (method == "POST") {
                append("Content-Type: text/plain;charset=UTF-8\r\n")
                append("Content-Length: ").append(uploadBytes).append("\r\n")
            }
            append("\r\n")
        }.encodeToByteArray()

        val requestStart = System.nanoTime()
        var firstByte: Int
        try {
            out.write(head)
            if (uploadBytes > 0) {
                val chunk = ByteArray(UPLOAD_CHUNK) { '0'.code.toByte() }
                var left = uploadBytes
                while (left > 0) {
                    val n = minOf(chunk.size.toLong(), left).toInt()
                    out.write(chunk, 0, n)
                    left -= n
                    if (!onBytes(n.toLong())) throw SpeedAbort()
                }
            }
            out.flush()
            firstByte = inp.read()
        } catch (e: SpeedAbort) {
            close()
            throw e
        } catch (e: IOException) {
            throw StaleConnectionException(e)
        }
        if (firstByte < 0) throw StaleConnectionException(EOFException("server closed the connection"))

        var responseStart = System.nanoTime()
        var headerBytes = 0L
        var status: Int
        val headers = LinkedHashMap<String, String>()
        val timing = ArrayList<String>()
        var pending: Int = firstByte
        while (true) {
            val statusLine = readLine(inp, pending)
            pending = -1
            headerBytes += statusLine.length + 2
            status = statusLine.split(' ').getOrNull(1)?.toIntOrNull() ?: throw IOException("bad status line")
            headers.clear()
            timing.clear()
            while (true) {
                val line = readLine(inp, -1)
                headerBytes += line.length + 2
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon <= 0) continue
                val name = line.substring(0, colon).trim().lowercase()
                val value = line.substring(colon + 1).trim()
                if (name == "server-timing") timing += value
                headers[name] = headers[name]?.let { "$it, $value" } ?: value
            }
            if (status in 100..199) {
                val next = inp.read()
                if (next < 0) throw IOException("no final response")
                responseStart = System.nanoTime()
                pending = next
                continue
            }
            break
        }

        val keep = if (keepBody) java.io.ByteArrayOutputStream() else null
        var body = 0L
        val chunked = headers["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true
        val length = headers["content-length"]?.toLongOrNull()
        val buffer = ByteArray(READ_CHUNK)
        fun take(n: Int) {
            body += n
            keep?.write(buffer, 0, n)
            if (!onBytes(n.toLong())) {
                close()
                throw SpeedAbort()
            }
        }
        val bodyless = status == 204 || status == 304
        when {
            bodyless -> Unit
            chunked -> while (true) {
                val sizeLine = readLine(inp, -1)
                val size = sizeLine.substringBefore(';').trim().toLongOrNull(16) ?: throw IOException("bad chunk size")
                if (size == 0L) {
                    while (readLine(inp, -1).isNotEmpty()) Unit
                    break
                }
                var left = size
                while (left > 0) {
                    val n = inp.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                    if (n < 0) throw EOFException("chunk cut short")
                    left -= n
                    take(n)
                }
                readLine(inp, -1)
            }
            length != null -> {
                var left = length
                while (left > 0) {
                    val n = inp.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                    if (n < 0) throw EOFException("body cut short")
                    left -= n
                    take(n)
                }
            }
            else -> while (true) {
                val n = inp.read(buffer)
                if (n < 0) break
                take(n)
            }
        }
        val responseEnd = System.nanoTime()
        val closing = !bodyless && length == null && !chunked || headers["connection"]?.equals("close", ignoreCase = true) == true
        if (closing) close()

        return SpeedResponse(
            status = status,
            ttfbMs = (responseStart - requestStart) / 1_000_000.0,
            payloadMs = (responseEnd - responseStart) / 1_000_000.0,
            headerBytes = headerBytes,
            bodyBytes = body,
            serverTiming = timing.takeIf { it.isNotEmpty() }?.joinToString(", "),
            headers = headers,
            body = keep?.toByteArray()?.decodeToString(),
        )
    }

    private fun ensureOpen() {
        if (socket?.isClosed == false && input != null && output != null) return
        close()
        val raw = Socket()
        raw.tcpNoDelay = true
        raw.soTimeout = READ_TIMEOUT_MS
        try {
            if (socksPort != null) {
                raw.connect(InetSocketAddress("127.0.0.1", socksPort), CONNECT_TIMEOUT_MS)
                dev.cluvex.zedsecure.platform.socks5Handshake(raw, host, 443, LocalProxyAuth.current)
            } else {
                raw.connect(InetSocketAddress(host, 443), CONNECT_TIMEOUT_MS)
            }
            val tls = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(raw, host, 443, true) as SSLSocket
            tls.useClientMode = true
            tls.sslParameters = tls.sslParameters.apply {
                endpointIdentificationAlgorithm = "HTTPS"
                serverNames = listOf(SNIHostName(host))
            }
            tls.soTimeout = READ_TIMEOUT_MS
            tls.startHandshake()
            socket = tls
            input = BufferedInputStream(tls.inputStream, READ_CHUNK)
            output = BufferedOutputStream(tls.outputStream, UPLOAD_CHUNK)
        } catch (e: Exception) {
            runCatching { raw.close() }
            throw e
        }
    }

    private fun readLine(inp: InputStream, first: Int): String {
        val line = StringBuilder()
        var c = if (first >= 0) first else inp.read()
        while (c >= 0) {
            if (c == '\r'.code) {
                val next = inp.read()
                if (next == '\n'.code || next < 0) break
                line.append('\r')
                c = next
                continue
            }
            if (c == '\n'.code) break
            line.append(c.toChar())
            if (line.length > MAX_LINE) throw IOException("header line too long")
            c = inp.read()
        }
        if (c < 0 && line.isEmpty()) throw EOFException("connection closed")
        return line.toString()
    }

    private class StaleConnectionException(cause: Throwable) : IOException(cause)

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 20_000
        private const val READ_CHUNK = 64 * 1024
        private const val UPLOAD_CHUNK = 64 * 1024
        private const val MAX_LINE = 16 * 1024
    }
}

class SpeedAbort : IOException("speed test stopped")
