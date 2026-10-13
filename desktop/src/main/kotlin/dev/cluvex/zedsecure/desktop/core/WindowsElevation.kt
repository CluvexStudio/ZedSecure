package dev.cluvex.zedsecure.desktop.core

import com.sun.jna.Native
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

object WindowsElevation {
    private const val LABEL = "admin"
    private const val CONNECT_WAIT_MS = 180_000L

    private class Link(val socket: Socket, val out: PrintWriter, val replies: LinkedBlockingQueue<String>)

    private val startLock = Any()
    private val requestLock = Any()
    @Volatile private var link: Link? = null
    @Volatile var onLost: ((String) -> Unit)? = null

    val isAlive: Boolean get() = link?.socket?.let { !it.isClosed && it.isConnected } == true

    fun ensure(workDir: File): Boolean = synchronized(startLock) {
        if (isAlive) return true
        close()
        val token = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val tokenFile = runCatching {
            File.createTempFile("helper-", ".token", workDir.also { it.mkdirs() }).apply { writeText(token) }
        }.getOrElse {
            println("[$LABEL] could not write the helper token: ${it.message}")
            return false
        }
        try {
            ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
                server.soTimeout = 1_000
                val (file, parameters) = launchCommand(server.localPort, tokenFile) ?: run {
                    println("[$LABEL] could not find ZedSecure's own program to run as administrator")
                    return false
                }
                val handle = try {
                    WinApi.runElevated(file, parameters, workDir.absolutePath)
                } catch (e: WinApi.ElevationDeclined) {
                    println("[$LABEL] ${e.message}")
                    return false
                } catch (e: Throwable) {
                    println("[$LABEL] ${e.message}")
                    return false
                }
                try {
                    val deadline = System.currentTimeMillis() + CONNECT_WAIT_MS
                    var socket: Socket? = null
                    while (socket == null && System.currentTimeMillis() < deadline) {
                        socket = try {
                            server.accept()
                        } catch (e: SocketTimeoutException) {
                            if (handle != null && WinApi.kernel32.WaitForSingleObject(handle, 0) == WinApi.WAIT_OBJECT_0) {
                                println("[$LABEL] the administrator helper exited before it connected")
                                return false
                            }
                            null
                        }
                    }
                    if (socket == null) {
                        println("[$LABEL] the administrator helper did not connect")
                        return false
                    }
                    socket.tcpNoDelay = true
                    socket.soTimeout = 15_000
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                    val hello = runCatching { reader.readLine() }.getOrNull()
                    if (hello != "${WindowsHelper.HELLO} $token") {
                        println("[$LABEL] an unexpected program answered instead of the helper")
                        runCatching { socket.close() }
                        return false
                    }
                    socket.soTimeout = 0
                    val replies = LinkedBlockingQueue<String>()
                    val out = PrintWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8), true)
                    link = Link(socket, out, replies)
                    Thread {
                        runCatching {
                            while (true) {
                                val line = reader.readLine() ?: break
                                when {
                                    line == "OK" || line.startsWith("ERR") -> replies.put(line)
                                    line.startsWith("DOWN ") -> onLost?.invoke(line.removePrefix("DOWN ").trim())
                                    line.isNotBlank() -> println("[$LABEL] $line")
                                }
                            }
                        }
                        runCatching { socket.close() }
                    }.apply { isDaemon = true; name = "admin-helper-reader" }.start()
                    return true
                } finally {
                    handle?.let { WinApi.kernel32.CloseHandle(it) }
                }
            }
        } finally {
            tokenFile.delete()
        }
    }

    fun tunUp(dir: File): Result<Unit> = answer(request("TUN_UP ${dir.absolutePath}", 45), "zeptun did not answer")

    fun tunDown() {
        if (isAlive) request("TUN_DOWN", 25)
    }

    fun hevUp(dir: File): Result<Unit> = answer(request("HEV_UP ${dir.absolutePath}", 60), "hev did not answer")

    fun hevDown() {
        if (isAlive) request("HEV_DOWN", 40)
    }

    fun ocUp(dir: File): Result<Unit> = answer(request("OC_UP ${dir.absolutePath}", 120), "OpenConnect did not answer")

    fun ocDown() {
        if (isAlive) request("OC_DOWN", 25)
    }

    fun trustCa(file: File): Result<Unit> = answer(request("CA_TRUST ${file.absolutePath}", 30), "the helper did not answer")

    fun close() {
        val l = link ?: return
        link = null
        runCatching { l.socket.close() }
    }

    private fun answer(reply: String?, fallback: String): Result<Unit> = when {
        reply == null -> Result.failure(IllegalStateException(fallback))
        reply == "OK" -> Result.success(Unit)
        else -> Result.failure(IllegalStateException(reply.removePrefix("ERR").trim().ifEmpty { fallback }))
    }

    private fun request(line: String, waitSec: Long): String? = synchronized(requestLock) {
        val l = link ?: return null
        if (l.socket.isClosed) return null
        l.replies.clear()
        l.out.println(line)
        if (l.out.checkError()) return null
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(waitSec)
        var reply: String? = null
        while (reply == null && !l.socket.isClosed) {
            val left = deadline - System.nanoTime()
            if (left <= 0) break
            reply = l.replies.poll(minOf(left, TimeUnit.MILLISECONDS.toNanos(500)), TimeUnit.NANOSECONDS)
        }
        reply ?: l.replies.poll().also { if (it == null) println("[$LABEL] no answer to ${line.substringBefore(' ')}") }
    }

    internal fun launchCommand(port: Int, tokenFile: File): Pair<String, String>? {
        val helperArgs = listOf(port.toString(), tokenFile.absolutePath)
        System.getProperty("jpackage.app-path")?.takeIf { File(it).isFile }?.let { exe ->
            return exe to (listOf(WindowsHelper.FLAG) + helperArgs).joinToString(" ") { DesktopOpenConnect.winArg(it) }
        }
        val java = File(System.getProperty("java.home"), "bin/java.exe").takeIf { it.isFile } ?: return null
        val classpath = listOf(WindowsHelper::class.java, Unit::class.java, Native::class.java)
            .mapNotNull { runCatching { File(it.protectionDomain.codeSource.location.toURI()).absolutePath }.getOrNull() }
            .distinct()
            .joinToString(File.pathSeparator)
        val args = listOf("-cp", classpath, "dev.cluvex.zedsecure.desktop.core.WindowsHelperKt") + helperArgs
        return java.absolutePath to args.joinToString(" ") { DesktopOpenConnect.winArg(it) }
    }
}
