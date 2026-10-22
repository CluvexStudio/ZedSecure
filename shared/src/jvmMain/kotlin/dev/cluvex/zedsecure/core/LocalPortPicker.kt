package dev.cluvex.zedsecure.core

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket

object LocalPortPicker {
    private val loopback: InetAddress = InetAddress.getByName("127.0.0.1")
    private val isWindows = System.getProperty("os.name").orEmpty().lowercase().contains("win")

    fun isFree(port: Int): Boolean = runCatching {
        ServerSocket().use {
            if (!isWindows) it.reuseAddress = true
            it.bind(InetSocketAddress(loopback, port))
        }
        true
    }.getOrDefault(false)

    fun pick(preferred: Int, taken: Set<Int> = emptySet(), span: Int = 40): Int {
        for (port in preferred until minOf(preferred + span, 65_536)) {
            if (port !in taken && isFree(port)) return port
        }
        while (true) {
            val port = ServerSocket(0, 1, loopback).use { it.localPort }
            if (port !in taken) return port
        }
    }

    /** [pick], but the port one above it has to be available too — Xray binds the HTTP inbound
     *  of the local proxy at `socksPort + 1` whenever `appendHttpProxy` is on, so a free SOCKS
     *  port whose neighbour is taken would just move the collision. Falls back to [pick] when no
     *  such pair exists in the span, so the caller always gets a port. */
    fun pickPair(preferred: Int, taken: Set<Int> = emptySet(), span: Int = 40): Int {
        for (port in preferred until minOf(preferred + span, 65_536)) {
            if (port !in taken && (port + 1) !in taken && isFree(port) && isFree(port + 1)) return port
        }
        return pick(preferred, taken, span)
    }
}
