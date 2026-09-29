package dev.cluvex.zedsecure.desktop.platform

import dev.cluvex.zedsecure.core.VpnManager
import dev.cluvex.zedsecure.desktop.core.Os
import dev.cluvex.zedsecure.desktop.core.XrayBinary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.addJsonObject
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

object DesktopProbe {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val work = File(System.getProperty("java.io.tmpdir"), "zedsecure/probe").apply { mkdirs() }
    private val seq = AtomicInteger(0)

    @Volatile private var cachedBin: File? = null
    private fun bin(): File? {
        cachedBin?.let { if (it.exists()) return it }
        synchronized(this) {
            cachedBin?.let { if (it.exists()) return it }
            return XrayBinary.extract(work.apply { mkdirs() })?.also { cachedBin = it }
        }
    }

    fun measureDelay(url: String): Long {
        val port = VpnManager.activeSocksPort ?: return -1
        return timedRequests(url, port)
    }

    fun measureOutboundDelay(configJson: String, url: String): Long {
        val bin = bin() ?: return -1
        val port = freePort() ?: return -1
        val probeConfig = withProbeInbound(configJson, port) ?: return -1
        val id = seq.incrementAndGet()
        val cfg = File(work, "probe-$id.json").apply { writeText(probeConfig) }
        var proc: Process? = null
        return try {
            val env = if (Os.current == Os.WINDOWS) null else arrayOf("XRAY_LOCATION_ASSET=${work.absolutePath}")
            proc = Runtime.getRuntime().exec(
                arrayOf(bin.absolutePath, "run", "-c", cfg.absolutePath), env, work,
            )
            drain(proc)

            if (!waitPortReady(port, proc, timeoutMs = 4_000)) return -1
            timedRequests(url, port)
        } catch (e: Exception) {
            -1
        } finally {
            runCatching { proc?.destroyForcibly() }
            runCatching { cfg.delete() }
        }
    }

    private fun drain(proc: Process) {
        Thread { runCatching { proc.inputStream.bufferedReader().forEachLine { } } }.apply { isDaemon = true }.start()
        Thread { runCatching { proc.errorStream.bufferedReader().forEachLine { } } }.apply { isDaemon = true }.start()
    }

    private fun waitPortReady(port: Int, proc: Process, timeoutMs: Int): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (!proc.isAlive) return false
            try {
                Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 200); return true }
            } catch (e: Exception) {
                try { Thread.sleep(20) } catch (ie: InterruptedException) { return false }
            }
        }
        return false
    }

    private fun timedRequests(url: String, socksPort: Int): Long {
        if (socksPort <= 0) return -1
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return -1
        val https = uri.scheme.equals("https", ignoreCase = true)
        val host = uri.host ?: return -1
        val port = if (uri.port > 0) uri.port else if (https) 443 else 80
        val path = (uri.rawPath?.takeIf { it.isNotEmpty() } ?: "/") + (uri.rawQuery?.let { "?$it" } ?: "")
        val request = ("GET $path HTTP/1.1\r\nHost: $host\r\nUser-Agent: ZedSecure\r\n" +
            "Accept: */*\r\nConnection: keep-alive\r\n\r\n").toByteArray()
        var best = -1L
        val coldStart = System.nanoTime()
        try {
            Socket().use { raw ->
                raw.connect(InetSocketAddress("127.0.0.1", socksPort), CONNECT_TIMEOUT_MS)
                raw.soTimeout = READ_TIMEOUT_MS
                if (!socksConnect(raw, host, port)) return -1
                val socket: Socket = if (https) {
                    val tls = (javax.net.ssl.SSLSocketFactory.getDefault() as javax.net.ssl.SSLSocketFactory)
                        .createSocket(raw, host, port, true) as javax.net.ssl.SSLSocket
                    tls.soTimeout = READ_TIMEOUT_MS
                    tls.startHandshake()
                    tls
                } else raw
                val input = java.io.BufferedInputStream(socket.getInputStream())
                val output = socket.getOutputStream()
                repeat(ATTEMPTS) { attempt ->
                    val started = if (attempt == 0) coldStart else System.nanoTime()
                    output.write(request)
                    output.flush()
                    val response = readResponse(input) ?: return best
                    val ms = (System.nanoTime() - started) / 1_000_000
                    if (response.code in 200..399 && (best < 0 || ms < best)) best = ms
                    if (!response.keepAlive) return best
                }
            }
        } catch (e: Exception) {
            return best
        }
        return best
    }

    private fun socksConnect(socket: Socket, host: String, port: Int): Boolean {
        val out = socket.getOutputStream()
        val input = socket.getInputStream()
        out.write(byteArrayOf(5, 1, 0))
        out.flush()
        if (input.read() != 5 || input.read() != 0) return false
        val name = host.toByteArray(Charsets.US_ASCII)
        if (name.size > 255) return false
        out.write(byteArrayOf(5, 1, 0, 3, name.size.toByte()) + name +
            byteArrayOf((port shr 8).toByte(), port.toByte()))
        out.flush()
        if (input.read() != 5 || input.read() != 0) return false
        input.read()
        val skip = when (input.read()) {
            1 -> 4
            4 -> 16
            3 -> input.read().takeIf { it >= 0 } ?: return false
            else -> return false
        }
        repeat(skip + 2) { if (input.read() < 0) return false }
        return true
    }

    private class Response(val code: Int, val keepAlive: Boolean)

    private fun readResponse(input: java.io.InputStream): Response? {
        val status = readLine(input) ?: return null
        val code = status.split(' ').getOrNull(1)?.toIntOrNull() ?: return null
        var length = -1L
        var chunked = false
        var keepAlive = !status.startsWith("HTTP/1.0")
        while (true) {
            val line = readLine(input) ?: return null
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val name = line.substring(0, colon).trim().lowercase()
            val value = line.substring(colon + 1).trim()
            when (name) {
                "content-length" -> length = value.toLongOrNull() ?: -1
                "transfer-encoding" -> chunked = value.contains("chunked", ignoreCase = true)
                "connection" -> keepAlive = !value.equals("close", ignoreCase = true)
            }
        }
        when {
            code == 204 || code == 304 || code in 100..199 -> Unit
            chunked -> while (true) {
                val size = readLine(input)?.substringBefore(';')?.trim()?.toLongOrNull(16) ?: return null
                if (size == 0L) { readLine(input); break }
                if (!skipBytes(input, size + 2)) return null
            }
            length >= 0 -> if (!skipBytes(input, length)) return null
            else -> return Response(code, keepAlive = false)
        }
        return Response(code, keepAlive)
    }

    private fun readLine(input: java.io.InputStream): String? {
        val line = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return if (line.isEmpty()) null else line.toString()
            if (b == '\n'.code) return line.toString().trimEnd('\r')
            line.append(b.toChar())
            if (line.length > 8192) return null
        }
    }

    private fun skipBytes(input: java.io.InputStream, count: Long): Boolean {
        var left = count
        while (left > 0) {
            val skipped = input.skip(left)
            if (skipped > 0) { left -= skipped; continue }
            if (input.read() < 0) return false
            left--
        }
        return true
    }

    private const val ATTEMPTS = 2
    private const val CONNECT_TIMEOUT_MS = 3_000
    private const val READ_TIMEOUT_MS = 6_000

    private fun withProbeInbound(configJson: String, port: Int): String? {
        val root = runCatching { json.parseToJsonElement(configJson).jsonObject }.getOrNull() ?: return null
        val drop = setOf("inbounds", "routing", "dns", "metrics", "stats", "policy", "log", "api", "observatory", "burstObservatory")
        val out = buildJsonObject {
            root.forEach { (k, v) -> if (k !in drop) put(k, v) }
            put("log", buildJsonObject { put("loglevel", "none") })
            putJsonArray("inbounds") {
                addJsonObject {
                    put("tag", "probe")
                    put("port", port)
                    put("protocol", "socks")
                    put("listen", "127.0.0.1")
                    put("settings", buildJsonObject { put("auth", "noauth"); put("udp", false) })
                }
            }
        }
        return out.toString()
    }

    private fun freePort(): Int? = runCatching {
        ServerSocket(0).use { it.localPort }
    }.getOrNull()
}
