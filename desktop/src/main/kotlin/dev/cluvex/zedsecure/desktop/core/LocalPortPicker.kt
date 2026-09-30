package dev.cluvex.zedsecure.desktop.core

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket

object LocalPortPicker {
    private val loopback: InetAddress = InetAddress.getByName("127.0.0.1")

    fun isFree(port: Int): Boolean = runCatching {
        ServerSocket().use {
            if (Os.current != Os.WINDOWS) it.reuseAddress = true
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
}
