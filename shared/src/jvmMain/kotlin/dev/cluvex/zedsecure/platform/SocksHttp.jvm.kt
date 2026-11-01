package dev.cluvex.zedsecure.platform

import dev.cluvex.zedsecure.domain.config.LocalProxyAuth
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

internal object JvmRuntime {
    val android: Boolean = System.getProperty("java.vm.name").orEmpty().contains("Dalvik", ignoreCase = true) ||
        System.getProperty("java.vendor").orEmpty().contains("Android", ignoreCase = true)

    val resolvesThroughProxy: Boolean get() = !android
}

internal class RawHttpResponse(val code: Int, val body: String, val headers: Map<String, String>)

internal fun socks5Handshake(raw: Socket, host: String, port: Int, auth: LocalProxyAuth.Credentials?) {
    val out = raw.getOutputStream()
    val inp = raw.getInputStream()
    out.write(if (auth != null) byteArrayOf(5, 2, 0, 2) else byteArrayOf(5, 1, 0))
    out.flush()
    val method = readExactly(inp, 2)
    if (method[0].toInt() != 5) throw IOException("not a SOCKS5 proxy")
    when (method[1].toInt() and 0xff) {
        0 -> Unit
        2 -> {
            val credentials = auth ?: throw IOException("the local proxy wants a password")
            val user = credentials.user.encodeToByteArray()
            val pass = credentials.pass.encodeToByteArray()
            out.write(byteArrayOf(1, user.size.toByte()) + user + byteArrayOf(pass.size.toByte()) + pass)
            out.flush()
            val reply = readExactly(inp, 2)
            if (reply[1].toInt() != 0) throw IOException("the local proxy refused the password")
        }
        else -> throw IOException("the local proxy offered no usable login")
    }
    val name = host.encodeToByteArray()
    require(name.size in 1..255) { "bad host name" }
    out.write(byteArrayOf(5, 1, 0, 3, name.size.toByte()) + name + byteArrayOf((port shr 8).toByte(), port.toByte()))
    out.flush()
    val reply = readExactly(inp, 4)
    if (reply[1].toInt() != 0) throw IOException("the tunnel could not reach $host (SOCKS ${reply[1].toInt() and 0xff})")
    when (reply[3].toInt()) {
        1 -> readExactly(inp, 4 + 2)
        3 -> readExactly(inp, (readExactly(inp, 1)[0].toInt() and 0xff) + 2)
        4 -> readExactly(inp, 16 + 2)
        else -> throw IOException("bad SOCKS reply")
    }
}

internal fun readExactly(inp: InputStream, count: Int): ByteArray {
    val bytes = ByteArray(count)
    var read = 0
    while (read < count) {
        val n = inp.read(bytes, read, count - read)
        if (n < 0) throw EOFException("the proxy closed the connection")
        read += n
    }
    return bytes
}

internal fun rawHttpViaSocks(
    method: String,
    url: String,
    headers: Map<String, String>,
    body: ByteArray?,
    socksPort: Int,
    connectTimeoutMs: Int,
    readTimeoutMs: Int,
    redirects: Int = 5,
): RawHttpResponse {
    val uri = URI(url)
    val https = uri.scheme.equals("https", ignoreCase = true)
    if (!https && !uri.scheme.equals("http", ignoreCase = true)) throw IOException("unsupported address $url")
    val host = uri.host?.removePrefix("[")?.removeSuffix("]") ?: throw IOException("no host in $url")
    val port = if (uri.port > 0) uri.port else if (https) 443 else 80
    val target = (uri.rawPath?.takeIf { it.isNotEmpty() } ?: "/") + (uri.rawQuery?.let { "?$it" } ?: "")
    val shownHost = if (host.contains(':')) "[$host]" else host
    val hostHeader = if (port == (if (https) 443 else 80)) shownHost else "$shownHost:$port"

    val raw = Socket()
    try {
        raw.tcpNoDelay = true
        raw.soTimeout = readTimeoutMs
        raw.connect(InetSocketAddress("127.0.0.1", socksPort), connectTimeoutMs)
        socks5Handshake(raw, host, port, LocalProxyAuth.current)
        val socket: Socket = if (https) tlsOver(raw, host, port, readTimeoutMs) else raw
        val out = BufferedOutputStream(socket.getOutputStream())
        val head = buildString {
            append(method).append(' ').append(target).append(" HTTP/1.1\r\n")
            append("Host: ").append(hostHeader).append("\r\n")
            headers.forEach { (key, value) ->
                val lower = key.lowercase()
                if (lower != "host" && lower != "connection" && lower != "content-length" && lower != "accept-encoding") {
                    append(key).append(": ").append(value).append("\r\n")
                }
            }
            if (headers.keys.none { it.equals("accept", ignoreCase = true) }) append("Accept: */*\r\n")
            append("Accept-Encoding: identity\r\n")
            if (body != null) append("Content-Length: ").append(body.size).append("\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(head.encodeToByteArray())
        if (body != null) out.write(body)
        out.flush()

        val inp = BufferedInputStream(socket.getInputStream())
        var code: Int
        val responseHeaders = LinkedHashMap<String, String>()
        while (true) {
            val statusLine = readHttpLine(inp) ?: throw EOFException("the server sent no response")
            code = statusLine.split(' ').getOrNull(1)?.toIntOrNull() ?: throw IOException("bad status line")
            responseHeaders.clear()
            while (true) {
                val line = readHttpLine(inp) ?: throw EOFException("the response headers were cut short")
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon <= 0) continue
                val name = line.substring(0, colon).trim().lowercase()
                val value = line.substring(colon + 1).trim()
                responseHeaders[name] = responseHeaders[name]?.let { "$it, $value" } ?: value
            }
            if (code in 100..199) continue
            break
        }

        val location = responseHeaders["location"]
        if (code in 300..399 && code != 304 && location != null && redirects > 0) {
            val keep = code == 307 || code == 308
            return rawHttpViaSocks(
                method = if (keep) method else "GET",
                url = uri.resolve(location).toString(),
                headers = headers,
                body = if (keep) body else null,
                socksPort = socksPort,
                connectTimeoutMs = connectTimeoutMs,
                readTimeoutMs = readTimeoutMs,
                redirects = redirects - 1,
            )
        }

        val bodyless = method.equals("HEAD", ignoreCase = true) || code == 204 || code == 304
        val payload = if (bodyless) ByteArray(0) else readHttpBody(inp, responseHeaders)
        return RawHttpResponse(code, payload.decodeToString(), responseHeaders)
    } finally {
        runCatching { raw.close() }
    }
}

private fun tlsOver(raw: Socket, host: String, port: Int, readTimeoutMs: Int): SSLSocket {
    val tls = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(raw, host, port, true) as SSLSocket
    tls.useClientMode = true
    tls.sslParameters = tls.sslParameters.apply {
        endpointIdentificationAlgorithm = "HTTPS"
        if (!isIpLiteral(host)) serverNames = listOf(SNIHostName(host))
    }
    tls.soTimeout = readTimeoutMs
    tls.startHandshake()
    return tls
}

private fun isIpLiteral(host: String): Boolean =
    host.contains(':') || (host.isNotEmpty() && host.all { it.isDigit() || it == '.' })

private fun readHttpLine(inp: InputStream): String? {
    val line = ByteArrayOutputStream()
    while (true) {
        val c = inp.read()
        if (c < 0) return if (line.size() == 0) null else line.toByteArray().decodeToString()
        if (c == '\n'.code) break
        line.write(c)
    }
    return line.toByteArray().decodeToString().removeSuffix("\r")
}

private fun readHttpBody(inp: InputStream, headers: Map<String, String>): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    val chunked = headers["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true
    val length = headers["content-length"]?.substringBefore(',')?.trim()?.toLongOrNull()
    when {
        chunked -> while (true) {
            val sizeLine = readHttpLine(inp) ?: throw EOFException("the chunked body was cut short")
            val size = sizeLine.substringBefore(';').trim().toLongOrNull(16) ?: throw IOException("bad chunk size")
            if (size == 0L) {
                while (true) {
                    val trailer = readHttpLine(inp) ?: break
                    if (trailer.isEmpty()) break
                }
                break
            }
            var left = size
            while (left > 0) {
                val n = inp.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                if (n < 0) throw EOFException("the chunked body was cut short")
                out.write(buffer, 0, n)
                left -= n
            }
            readHttpLine(inp)
        }
        length != null -> {
            var left = length
            while (left > 0) {
                val n = inp.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                if (n < 0) throw EOFException("the body was cut short")
                out.write(buffer, 0, n)
                left -= n
            }
        }
        else -> while (true) {
            val n = inp.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
        }
    }
    return out.toByteArray()
}
