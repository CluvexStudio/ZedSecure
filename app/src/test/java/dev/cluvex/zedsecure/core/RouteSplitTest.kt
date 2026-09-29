package dev.cluvex.zedsecure.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteSplitTest {
    private val edge = "104.19.230.21"

    @Test
    fun `emits one route per prefix depth`() {
        assertEquals(32, RouteSplit.defaultExcluding(edge).size)
    }

    @Test
    fun `the excluded address is covered by none of them`() {
        RouteSplit.defaultExcluding(edge).forEach { route ->
            assertFalse("$route must not contain $edge", RouteSplit.covers(route, edge))
        }
    }

    @Test
    fun `every other address is still covered`() {
        val routes = RouteSplit.defaultExcluding(edge)

        listOf(
            "104.19.230.20", "104.19.230.22", "104.19.230.0", "104.19.231.21",
            "104.19.0.1", "104.18.0.1", "0.0.0.0", "255.255.255.255",
            "1.1.1.1", "8.8.8.8", "10.1.0.2", "192.168.1.1", "149.154.167.91",
        ).forEach { ip ->
            assertTrue("$ip must stay inside the tunnel", routes.any { RouteSplit.covers(it, ip) })
        }
    }

    @Test
    fun `holds for an address at either extreme`() {
        listOf("0.0.0.0", "255.255.255.255", "1.2.3.4").forEach { excluded ->
            val routes = RouteSplit.defaultExcluding(excluded)
            assertEquals(32, routes.size)
            routes.forEach { assertFalse(RouteSplit.covers(it, excluded)) }

            val other = if (excluded == "1.2.3.4") "4.3.2.1" else "1.2.3.4"
            assertTrue(routes.any { RouteSplit.covers(it, other) })
        }
    }

    @Test
    fun `a non-IPv4 input yields nothing so the caller keeps the default route`() {
        listOf("", "example.com", "2606:4700::1", "1.2.3", "1.2.3.4.5", "999.1.1.1")
            .forEach { assertTrue("$it must not produce routes", RouteSplit.defaultExcluding(it).isEmpty()) }
    }
}
