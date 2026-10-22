package dev.cluvex.zedsecure.core

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket

object LocalPortPicker {
    private val loopback: InetAddress = InetAddress.getByName("127.0.0.1")
    private val windows = System.getProperty("os.name").orEmpty().startsWith("Windows")

    fun isFree(port: Int): Boolean = runCatching {
        ServerSocket().use {
            if (!windows) it.reuseAddress = true
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

    fun pickPair(preferred: Int, taken: Set<Int> = emptySet(), span: Int = 200): Int {
        var port = preferred
        while (port < minOf(preferred + span, 65_535)) {
            if (port !in taken && port + 1 !in taken && isFree(port) && isFree(port + 1)) return port
            port += 2
        }
        while (true) {
            val port = ServerSocket(0, 1, loopback).use { it.localPort }
            if (port < 65_535 && port !in taken && port + 1 !in taken && isFree(port + 1)) return port
        }
    }
}
