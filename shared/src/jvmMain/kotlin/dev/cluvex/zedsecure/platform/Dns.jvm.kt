package dev.cluvex.zedsecure.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.random.Random

private const val UDP_BUFFER = 4096

actual suspend fun dnsQuery(
    server: String,
    port: Int,
    name: String,
    type: DnsRecordType,
    transport: DnsTransport,
    timeoutMs: Int,
    ednsPayloadSize: Int,
    dnssecOk: Boolean,
): DnsResult = withContext(Dispatchers.IO) {
    val id = Random.nextInt(0, 0xFFFF)
    val query = runCatching { DnsWire.buildQuery(id, name, type, ednsPayloadSize, dnssecOk) }.getOrNull()
        ?: return@withContext DnsResult.Failed(DnsFailure.Malformed)
    when (transport) {
        DnsTransport.UDP -> overUdp(server, port, id, query, timeoutMs, name, type.value)
        DnsTransport.TCP ->
            overStream(server, port, id, query, timeoutMs, false, null, name, type.value)
        DnsTransport.DOT ->
            overStream(server, port, id, query, timeoutMs, true, server, name, type.value)
        DnsTransport.DOH -> overHttps(server, id, query, timeoutMs, name, type.value)
    }
}

private fun overUdp(
    server: String,
    port: Int,
    id: Int,
    query: ByteArray,
    timeoutMs: Int,
    askedName: String,
    askedType: Int,
): DnsResult {
    var socket: DatagramSocket? = null
    return try {
        val address = InetAddress.getByName(server)
        socket = DatagramSocket().apply { soTimeout = timeoutMs }
        val started = System.nanoTime()
        socket.send(DatagramPacket(query, query.size, address, port))
        val buffer = ByteArray(UDP_BUFFER)

        val deadline = started + timeoutMs * 1_000_000L
        while (System.nanoTime() < deadline) {
            val packet = DatagramPacket(buffer, buffer.size)
            socket.receive(packet)
            val elapsed = (System.nanoTime() - started) / 1_000_000
            val parsed = DnsWire.parseReply(buffer, packet.length, id, elapsed, askedName, askedType)
            if (parsed is DnsResult.Failed && parsed.reason == DnsFailure.Mismatch) continue
            return parsed
        }
        DnsResult.Failed(DnsFailure.Timeout)
    } catch (e: java.net.SocketTimeoutException) {
        DnsResult.Failed(DnsFailure.Timeout)
    } catch (e: Exception) {
        DnsResult.Failed(DnsFailure.Unreachable)
    } finally {
        runCatching { socket?.close() }
    }
}

private fun overStream(
    server: String,
    port: Int,
    id: Int,
    query: ByteArray,
    timeoutMs: Int,
    tls: Boolean,
    sni: String?,
    askedName: String,
    askedType: Int,
): DnsResult {
    var socket: Socket? = null
    return try {
        val started = System.nanoTime()
        socket = Socket().apply {
            soTimeout = timeoutMs
            connect(InetSocketAddress(InetAddress.getByName(server), port), timeoutMs)
        }
        if (tls) {
            socket = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                .createSocket(socket, sni ?: server, port, true)
                .also { (it as SSLSocket).soTimeout = timeoutMs; it.startHandshake() }
        }
        val out = DataOutputStream(socket.getOutputStream())
        out.writeShort(query.size)
        out.write(query)
        out.flush()
        val input = DataInputStream(socket.getInputStream())
        val length = input.readUnsignedShort()
        if (length <= 0 || length > 65535) return DnsResult.Failed(DnsFailure.Malformed)
        val reply = ByteArray(length)
        input.readFully(reply)
        DnsWire.parseReply(reply, length, id, (System.nanoTime() - started) / 1_000_000, askedName, askedType)
    } catch (e: java.net.SocketTimeoutException) {
        DnsResult.Failed(DnsFailure.Timeout)
    } catch (e: Exception) {
        DnsResult.Failed(DnsFailure.Unreachable)
    } finally {
        runCatching { socket?.close() }
    }
}

private fun overHttps(
    url: String,
    id: Int,
    query: ByteArray,
    timeoutMs: Int,
    askedName: String,
    askedType: Int,
): DnsResult {
    var connection: HttpURLConnection? = null
    return try {
        val started = System.nanoTime()
        connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/dns-message")
            setRequestProperty("Accept", "application/dns-message")
            setFixedLengthStreamingMode(query.size)
        }
        connection.outputStream.use { it.write(query); it.flush() }
        if (connection.responseCode !in 200..299) return DnsResult.Failed(DnsFailure.Refused)
        val reply = connection.inputStream.use { it.readBytes() }
        DnsWire.parseReply(reply, reply.size, id, (System.nanoTime() - started) / 1_000_000, askedName, askedType)
    } catch (e: java.net.SocketTimeoutException) {
        DnsResult.Failed(DnsFailure.Timeout)
    } catch (e: Exception) {
        DnsResult.Failed(DnsFailure.Unreachable)
    } finally {
        runCatching { connection?.disconnect() }
    }
}
