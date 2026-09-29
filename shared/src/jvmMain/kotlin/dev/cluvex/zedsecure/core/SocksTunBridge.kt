package dev.cluvex.zedsecure.core

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock

class SocksTunBridge(
    private val listenHost: String,
    private val listenPort: Int,
    private val upstreamHost: String,
    private val upstreamPort: Int,
    private val dnsHost: String = "8.8.8.8",
    private val dnsPort: Int = 53,
) {
    private val running = AtomicBoolean(false)
    private var server: ServerSocket? = null

    private val bytesToUpstream = AtomicLong(0)
    private val bytesFromUpstream = AtomicLong(0)
    private var reportedUp = 0L
    private var reportedDown = 0L

    private val resolver = ResolverPool()

    val isRunning: Boolean get() = running.get()

    @Synchronized
    fun readDelta(): Pair<Long, Long> {
        val down = bytesFromUpstream.get()
        val up = bytesToUpstream.get()
        val delta = (down - reportedDown) to (up - reportedUp)
        reportedDown = down
        reportedUp = up
        return delta
    }

    fun start(): Boolean = try {
        val socket = ServerSocket()
        socket.reuseAddress = true
        socket.bind(InetSocketAddress(listenHost, listenPort))
        server = socket
        running.set(true)
        daemon("socks-bridge-accept") { acceptLoop(socket) }
        LogBus.append("I/$TAG bridging $listenHost:$listenPort -> $upstreamHost:$upstreamPort")
        true
    } catch (e: Exception) {
        LogBus.append("E/$TAG could not bind $listenPort: ${e.message}")
        false
    }

    fun stop() {
        running.set(false)
        runCatching { server?.close() }
        server = null
        resolver.shutdown()
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (running.get()) {
            val client = try {
                socket.accept()
            } catch (e: Exception) {
                if (running.get()) continue else break
            }
            daemon("socks-bridge") { serve(client) }
        }
    }

    private fun serve(client: Socket) {
        try {
            client.use { socket ->
                socket.soTimeout = HANDSHAKE_TIMEOUT_MS
                socket.tcpNoDelay = true
                val from = socket.getInputStream()
                val to = socket.getOutputStream()

                if (!greet(from, to)) return
                val request = readRequest(from, to) ?: return

                when (request.command) {
                    CMD_CONNECT -> {
                        socket.soTimeout = 0
                        proxyStream(request, socket, from, to)
                    }
                    CMD_HEV_DATAGRAM -> {
                        socket.soTimeout = 0
                        relayDatagrams(from, to)
                    }
                }
            }
        } catch (e: Exception) {
            if (running.get()) LogBus.append("D/$TAG connection ended: ${e.message}")
        }
    }

    private fun greet(from: InputStream, to: OutputStream): Boolean {
        if (from.read() != SOCKS5) return false
        val methodCount = from.read()
        if (methodCount < 0) return false
        from.readExactly(ByteArray(methodCount))
        to.writeFlush(byteArrayOf(SOCKS5.toByte(), AUTH_NONE.toByte()))
        return true
    }

    private class Request(val command: Int, val address: ByteArray, val port: ByteArray) {
        val portNumber: Int
            get() = ((port[0].toInt() and 0xFF) shl 8) or (port[1].toInt() and 0xFF)
    }

    private fun readRequest(from: InputStream, to: OutputStream): Request? {
        if (from.read() != SOCKS5) return null
        val command = from.read()
        from.read()
        if (command != CMD_CONNECT && command != CMD_HEV_DATAGRAM) {
            to.writeFlush(refusal(REPLY_COMMAND_UNSUPPORTED))
            return null
        }
        val address = readAddress(from) ?: run {
            to.writeFlush(refusal(REPLY_ADDRESS_UNSUPPORTED))
            return null
        }
        val port = ByteArray(2)
        from.readExactly(port)
        return Request(command, address, port)
    }

    private fun readAddress(from: InputStream): ByteArray? = when (val type = from.read()) {
        ADDR_IPV4 -> byteArrayOf(type.toByte()) + ByteArray(4).also { from.readExactly(it) }
        ADDR_IPV6 -> byteArrayOf(type.toByte()) + ByteArray(16).also { from.readExactly(it) }
        ADDR_DOMAIN -> {
            val length = from.read()
            if (length <= 0) null
            else byteArrayOf(type.toByte(), length.toByte()) + ByteArray(length).also { from.readExactly(it) }
        }
        else -> null
    }

    private fun proxyStream(request: Request, client: Socket, from: InputStream, to: OutputStream) {
        val upstream = Socket()
        try {
            upstream.connect(InetSocketAddress(upstreamHost, upstreamPort), CONNECT_TIMEOUT_MS)
            upstream.tcpNoDelay = true
            val fromUpstream = upstream.getInputStream()
            val toUpstream = upstream.getOutputStream()

            val status = openUpstream(fromUpstream, toUpstream, request.address, request.port)
            if (status != REPLY_OK) {
                to.writeFlush(refusal(status))
                upstream.close()
                return
            }
            to.writeFlush(refusal(REPLY_OK))

            upstream.use {
                val outbound = daemon("socks-bridge-out") {
                    runCatching { pump(from, toUpstream, bytesToUpstream) }
                    runCatching { toUpstream.close() }
                }
                runCatching { pump(fromUpstream, to, bytesFromUpstream) }
                outbound.interrupt()
            }
        } catch (e: Exception) {
            runCatching { to.writeFlush(refusal(REPLY_REFUSED)) }
            runCatching { upstream.close() }
        }
    }

    private fun openUpstream(
        from: InputStream,
        to: OutputStream,
        address: ByteArray,
        port: ByteArray,
    ): Byte {
        to.writeFlush(byteArrayOf(SOCKS5.toByte(), 0x01, AUTH_NONE.toByte()))
        val chosen = ByteArray(2)
        from.readExactly(chosen)
        if (chosen[0].toInt() != SOCKS5 || (chosen[1].toInt() and 0xFF) == AUTH_NONE_ACCEPTABLE) {
            return REPLY_REFUSED
        }

        to.writeFlush(byteArrayOf(SOCKS5.toByte(), CMD_CONNECT.toByte(), 0x00) + address + port)
        val reply = ByteArray(4)
        from.readExactly(reply)
        if (reply[1] != REPLY_OK) return reply[1]
        skipBoundAddress(from, reply[3].toInt() and 0xFF)
        return REPLY_OK
    }

    private fun skipBoundAddress(from: InputStream, addressType: Int) {
        when (addressType) {
            ADDR_IPV4 -> from.readExactly(ByteArray(4 + 2))
            ADDR_IPV6 -> from.readExactly(ByteArray(16 + 2))
            ADDR_DOMAIN -> from.readExactly(ByteArray(from.read() + 2))
        }
    }

    private fun relayDatagrams(from: InputStream, to: OutputStream) {
        to.writeFlush(refusal(REPLY_OK))
        val header = ByteArray(3)
        while (running.get()) {
            try {
                from.readExactly(header)
            } catch (e: Exception) {
                break
            }
            val payloadLength = ((header[0].toInt() and 0xFF) shl 8) or (header[1].toInt() and 0xFF)
            val addressLength = (header[2].toInt() and 0xFF) - 3
            if (payloadLength <= 0 || addressLength <= 0) break

            val address = ByteArray(addressLength)
            from.readExactly(address)
            val payload = ByteArray(payloadLength)
            from.readExactly(payload)

            val destination = ((address[addressLength - 2].toInt() and 0xFF) shl 8) or
                (address[addressLength - 1].toInt() and 0xFF)

            if (destination != 53) continue

            resolver.submit {
                val answer = resolver.lookup(payload) ?: return@submit
                val reply = byteArrayOf(
                    ((answer.size shr 8) and 0xFF).toByte(),
                    (answer.size and 0xFF).toByte(),
                    (3 + address.size).toByte(),
                )
                synchronized(to) {
                    runCatching {
                        to.write(reply)
                        to.write(address)
                        to.write(answer)
                        to.flush()
                    }
                }
            }
        }
    }

    private inner class ResolverPool {
        private val workers = Executors.newFixedThreadPool(POOL_SIZE)
        private val links = arrayOfNulls<Link>(POOL_SIZE)
        private val guards = Array(POOL_SIZE) { ReentrantLock() }
        private val turn = AtomicInteger(0)

        private inner class Link(val socket: Socket, val from: InputStream, val to: OutputStream)

        private val address: ByteArray? by lazy {
            val host = dnsHost.trim()
            val bytes = runCatching {
                if (host.isEmpty() || host.startsWith("http", ignoreCase = true)) null
                else InetAddress.getAllByName(host).firstOrNull { it is Inet4Address }?.address
            }.getOrNull()
            if (bytes == null || bytes.size != 4) {
                LogBus.append(
                    "E/$TAG remote DNS '$dnsHost' is not a usable IPv4 address — DNS through this " +
                        "tunnel will not work. Set Remote DNS to a plain IP.",
                )
                null
            } else {
                bytes
            }
        }

        fun submit(task: () -> Unit) {
            runCatching { workers.submit(task) }
        }

        fun shutdown() {
            runCatching { workers.shutdownNow() }
            for (slot in links.indices) release(slot)
        }

        fun lookup(query: ByteArray): ByteArray? {
            val slot = (turn.getAndIncrement() and Int.MAX_VALUE) % POOL_SIZE
            val guard = guards[slot]
            guard.lock()
            try {
                repeat(2) {
                    val link = links[slot]?.takeIf { !it.socket.isClosed }
                        ?: open()?.also { links[slot] = it }
                        ?: return null
                    try {
                        link.to.write(
                            byteArrayOf(
                                ((query.size shr 8) and 0xFF).toByte(),
                                (query.size and 0xFF).toByte(),
                            ),
                        )
                        link.to.write(query)
                        link.to.flush()

                        val length = ByteArray(2)
                        link.from.readExactly(length)
                        val size = ((length[0].toInt() and 0xFF) shl 8) or (length[1].toInt() and 0xFF)
                        if (size <= 0) {
                            release(slot)
                            return null
                        }
                        return ByteArray(size).also { link.from.readExactly(it) }
                    } catch (e: Exception) {
                        release(slot)
                    }
                }
                return null
            } finally {
                guard.unlock()
            }
        }

        private fun open(): Link? {
            val target = address ?: return null
            val socket = Socket()
            return try {
                socket.connect(InetSocketAddress(upstreamHost, upstreamPort), RESOLVER_CONNECT_TIMEOUT_MS)
                socket.soTimeout = RESOLVER_READ_TIMEOUT_MS
                socket.tcpNoDelay = true
                val from = socket.getInputStream()
                val to = socket.getOutputStream()
                val port = byteArrayOf(((dnsPort shr 8) and 0xFF).toByte(), (dnsPort and 0xFF).toByte())
                if (openUpstream(from, to, byteArrayOf(ADDR_IPV4.toByte()) + target, port) != REPLY_OK) {
                    socket.close()
                    return null
                }
                Link(socket, from, to)
            } catch (e: Exception) {
                runCatching { socket.close() }
                null
            }
        }

        private fun release(slot: Int) {
            runCatching { links[slot]?.socket?.close() }
            links[slot] = null
        }
    }

    private fun refusal(code: Byte) =
        byteArrayOf(SOCKS5.toByte(), code, 0x00, ADDR_IPV4.toByte(), 0, 0, 0, 0, 0, 0)

    private fun daemon(name: String, body: () -> Unit): Thread =
        Thread(body, name).apply { isDaemon = true; start() }

    private fun OutputStream.writeFlush(bytes: ByteArray) {
        write(bytes)
        flush()
    }

    private fun InputStream.readExactly(buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val read = read(buffer, offset, buffer.size - offset)
            if (read < 0) throw EOFException()
            offset += read
        }
    }

    private fun pump(from: InputStream, to: OutputStream, counter: AtomicLong) {
        val buffer = ByteArray(COPY_BUFFER)
        while (true) {
            val read = from.read(buffer)
            if (read < 0) break
            to.write(buffer, 0, read)
            to.flush()
            counter.addAndGet(read.toLong())
        }
    }

    private companion object {
        const val TAG = "SocksBridge"

        const val SOCKS5 = 0x05
        const val AUTH_NONE = 0x00
        const val AUTH_NONE_ACCEPTABLE = 0xFF

        const val CMD_CONNECT = 0x01

        const val CMD_HEV_DATAGRAM = 0x05

        const val ADDR_IPV4 = 0x01
        const val ADDR_DOMAIN = 0x03
        const val ADDR_IPV6 = 0x04

        const val REPLY_OK: Byte = 0x00
        const val REPLY_REFUSED: Byte = 0x05
        const val REPLY_COMMAND_UNSUPPORTED: Byte = 0x07
        const val REPLY_ADDRESS_UNSUPPORTED: Byte = 0x08

        const val HANDSHAKE_TIMEOUT_MS = 30_000
        const val CONNECT_TIMEOUT_MS = 30_000
        const val RESOLVER_CONNECT_TIMEOUT_MS = 15_000
        const val RESOLVER_READ_TIMEOUT_MS = 20_000
        const val COPY_BUFFER = 65536
        const val POOL_SIZE = 4
    }
}
