package dev.cluvex.zedsecure.desktop.core

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.StandardSocketOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LocalPortPickerTest {
    private val loopback = InetAddress.getByName("127.0.0.1")

    private fun freePort(): Int = ServerSocket(0, 1, loopback).use { it.localPort }

    @Test
    fun `a free port is used as it is`() {
        val port = freePort()
        assertEquals(port, LocalPortPicker.pick(port))
    }

    @Test
    fun `a port another program listens on is skipped`() {
        ServerSocket(0, 1, loopback).use { busy ->
            val picked = LocalPortPicker.pick(busy.localPort)
            assertNotEquals(busy.localPort, picked)
            assertTrue(LocalPortPicker.isFree(picked))
        }
    }

    @Test
    fun `a port shared through SO_REUSEPORT, as Xray listens, is skipped`() {
        if (Os.current == Os.WINDOWS) return
        ServerSocket().use { busy ->
            busy.setOption(StandardSocketOptions.SO_REUSEPORT, true)
            busy.bind(InetSocketAddress(loopback, 0))
            assertTrue(!LocalPortPicker.isFree(busy.localPort))
            assertNotEquals(busy.localPort, LocalPortPicker.pick(busy.localPort))
        }
    }

    @Test
    fun `ports already given out are not handed out twice`() {
        val first = freePort()
        assertNotEquals(first, LocalPortPicker.pick(first, taken = setOf(first)))
    }
}
