package dev.cluvex.zedsecure.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL

internal actual suspend fun httpGetViaSocks(
    url: String,
    socksPort: Int?,
    userAgent: String,
    connectTimeoutMs: Int,
    readTimeoutMs: Int,
    closeConnection: Boolean,
): String = withContext(Dispatchers.IO) {
    if (socksPort != null && JvmRuntime.resolvesThroughProxy) {
        val response = rawHttpViaSocks("GET", url, mapOf("User-Agent" to userAgent), null, socksPort, connectTimeoutMs, readTimeoutMs)
        if (response.code !in 200..299) throw IllegalStateException("HTTP ${response.code}")
        return@withContext response.body
    }
    val connection = if (socksPort != null) {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("127.0.0.1", socksPort))
        URL(url).openConnection(proxy) as HttpURLConnection
    } else {
        URL(url).openConnection() as HttpURLConnection
    }
    connection.apply {
        connectTimeout = connectTimeoutMs
        readTimeout = readTimeoutMs
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", userAgent)
        if (closeConnection) setRequestProperty("Connection", "close")
    }
    try {
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("HTTP ${connection.responseCode}")
        }
        connection.inputStream.bufferedReader().use { it.readText() }
    } catch (e: NullPointerException) {
        throw platformHttpFailure(e)
    } finally {
        connection.disconnectQuietly()
    }
}

internal actual suspend fun httpTimedTransfer(
    url: String,
    socksPort: Int?,
    upload: Boolean,
    uploadBytes: Int,
    connectTimeoutMs: Int,
    readTimeoutMs: Int,
): HttpTiming = withContext(Dispatchers.IO) {
    val connection = if (socksPort != null) {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("127.0.0.1", socksPort))
        URL(url).openConnection(proxy) as HttpURLConnection
    } else {
        URL(url).openConnection() as HttpURLConnection
    }
    connection.apply {
        connectTimeout = connectTimeoutMs
        readTimeout = readTimeoutMs
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "ZedSecure-SpeedTest")
        setRequestProperty("Accept-Encoding", "identity")
    }
    var bytes = 0L
    val t0 = System.nanoTime()
    try {
        val buf = ByteArray(64 * 1024)
        if (upload) {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(uploadBytes)
            connection.setRequestProperty("Content-Type", "application/octet-stream")
            connection.outputStream.use { os ->
                var remaining = uploadBytes
                while (remaining > 0) {
                    val n = minOf(buf.size, remaining)
                    os.write(buf, 0, n)
                    os.flush()
                    remaining -= n
                    bytes += n
                }
            }
        }
        val code = connection.responseCode
        val ttfb = System.nanoTime() - t0
        if (code !in 200..299) throw IllegalStateException("HTTP $code")

        connection.inputStream.use { ins ->
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                if (!upload) bytes += n
            }
        }
        val wall = System.nanoTime() - t0
        val server = parseServerTimingMillis(connection.getHeaderField("server-timing"))
        HttpTiming(bytes = bytes, wallNanos = wall, ttfbNanos = ttfb, serverMillis = server)
    } catch (e: NullPointerException) {
        throw platformHttpFailure(e)
    } finally {
        connection.disconnectQuietly()
    }
}

private fun parseServerTimingMillis(header: String?): Double {
    if (header.isNullOrBlank()) return 0.0
    val idx = header.indexOf("dur=", ignoreCase = true)
    if (idx < 0) return 0.0
    val tail = header.substring(idx + 4)
    val num = tail.takeWhile { it.isDigit() || it == '.' }
    return num.toDoubleOrNull() ?: 0.0
}

internal actual suspend fun resolveHostAddress(host: String): String? = withContext(Dispatchers.IO) {
    runCatching { InetAddress.getByName(host).hostAddress }.getOrNull()
}

internal actual suspend fun dnsOverTcp(
    server: String,
    port: Int,
    query: ByteArray,
    socksPort: Int?,
    timeoutMs: Int,
): ByteArray = withContext(Dispatchers.IO) {
    val socket = if (socksPort != null) {
        Socket(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort)))
    } else {
        Socket()
    }
    socket.use { s ->
        s.soTimeout = timeoutMs
        s.connect(InetSocketAddress(server, port), timeoutMs)
        val out = s.getOutputStream()
        out.write(byteArrayOf(((query.size shr 8) and 0xff).toByte(), (query.size and 0xff).toByte()) + query)
        out.flush()
        val inp = s.getInputStream()
        fun readFully(count: Int): ByteArray {
            val buf = ByteArray(count)
            var read = 0
            while (read < count) {
                val n = inp.read(buf, read, count - read)
                if (n < 0) throw java.io.EOFException("the DNS server closed the connection")
                read += n
            }
            return buf
        }
        val head = readFully(2)
        val length = ((head[0].toInt() and 0xff) shl 8) or (head[1].toInt() and 0xff)
        readFully(length)
    }
}

internal actual suspend fun tcpConnectMillis(host: String, port: Int, timeoutMs: Int): Long =
    withContext(Dispatchers.IO) {
        var socket: Socket? = null
        val start = System.currentTimeMillis()
        try {
            socket = Socket()
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            System.currentTimeMillis() - start
        } catch (e: Exception) {
            -1L
        } finally {
            runCatching { socket?.takeIf { !it.isClosed }?.close() }
        }
    }

