package dev.cluvex.zedsecure.desktop.platform

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileLock

object SingleInstance {
    private const val SHOW = "SHOW"

    private val _showRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val showRequests: SharedFlow<Unit> = _showRequests

    @Volatile private var lock: FileLock? = null
    @Volatile private var server: ServerSocket? = null

    fun claim(): Boolean {
        val dir = runCatching { DesktopKeyValueStore.configDir().apply { mkdirs() } }.getOrNull() ?: return true
        val portFile = File(dir, "instance.port")
        val channel = runCatching { RandomAccessFile(File(dir, "instance.lock"), "rw").channel }.getOrNull() ?: return true
        val held = runCatching { channel.tryLock() }.getOrNull()
        if (held == null) {
            runCatching { channel.close() }
            return !signal(portFile)
        }
        lock = held
        val socket = runCatching { ServerSocket(0, 4, InetAddress.getLoopbackAddress()) }.getOrNull() ?: return true
        server = socket
        runCatching { portFile.writeText(socket.localPort.toString()) }
        Thread({ serve(socket) }, "single-instance").apply { isDaemon = true }.start()
        return true
    }

    private fun serve(socket: ServerSocket) {
        while (!socket.isClosed) {
            val client = runCatching { socket.accept() }.getOrNull() ?: continue
            runCatching {
                client.use { c ->
                    c.soTimeout = 2_000
                    if (c.getInputStream().bufferedReader().readLine() == SHOW) _showRequests.tryEmit(Unit)
                }
            }
        }
    }

    private fun signal(portFile: File): Boolean {
        val port = runCatching { portFile.readText().trim().toInt() }.getOrNull() ?: return false
        return runCatching {
            Socket().use { s ->
                s.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 1_500)
                s.getOutputStream().apply {
                    write("$SHOW\n".toByteArray())
                    flush()
                }
            }
            true
        }.getOrDefault(false)
    }
}
