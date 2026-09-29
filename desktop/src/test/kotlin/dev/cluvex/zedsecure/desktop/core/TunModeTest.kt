package dev.cluvex.zedsecure.desktop.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TunModeTest {
    @Test
    fun `VPN mode runs only where hev gets both its routes`() {
        assertTrue(TunMode.supported(Os.LINUX), "Linux routes the server around the tunnel and everything else into it")
        assertFalse(TunMode.supported(Os.WINDOWS), "no routes are installed on Windows, so traffic would bypass the tunnel")
        assertFalse(TunMode.supported(Os.MACOS), "the server is not routed around the tunnel on macOS, so it loops")
        assertFalse(TunMode.supported(Os.OTHER))
    }
}
