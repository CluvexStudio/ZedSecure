package dev.cluvex.zedsecure.desktop.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PolkitAgentTest {
    private fun desktop(current: String?, session: String? = null, name: String? = null) =
        PolkitAgent.desktopHasAgent(
            buildMap {
                current?.let { put("XDG_CURRENT_DESKTOP", it) }
                session?.let { put("XDG_SESSION_DESKTOP", it) }
                name?.let { put("DESKTOP_SESSION", it) }
            },
        )

    @Test
    fun `full desktops bring their own polkit agent`() {
        assertTrue(desktop("GNOME"))
        assertTrue(desktop("ubuntu:GNOME"))
        assertTrue(desktop("KDE"))
        assertTrue(desktop(null, name = "/usr/share/xsessions/plasma"))
        assertTrue(desktop("XFCE"))
        assertTrue(desktop("X-Cinnamon"))
        assertTrue(desktop("COSMIC"))
        assertTrue(desktop(null, session = "gnome-xorg"))
    }

    @Test
    fun `tiling compositors and bare window managers do not`() {
        assertFalse(desktop("Hyprland"))
        assertFalse(desktop("sway"))
        assertFalse(desktop("i3"))
        assertFalse(desktop("niri"))
        assertFalse(desktop("river", session = "river", name = "river"))
        assertFalse(desktop(null))
    }

    @Test
    fun `an agent the user started under any window manager counts`() {
        val hyprland = mapOf("XDG_CURRENT_DESKTOP" to "Hyprland")
        assertTrue(PolkitAgent.available(hyprland) { listOf("/usr/bin/Hyprland", "/usr/lib/hyprpolkitagent") })
        assertTrue(PolkitAgent.available(hyprland) { listOf("/usr/lib/polkit-gnome/polkit-gnome-authentication-agent-1") })
        assertTrue(PolkitAgent.available(hyprland) { listOf("/usr/lib/polkit-kde-authentication-agent-1") })
        assertFalse(PolkitAgent.available(hyprland) { listOf("/usr/bin/Hyprland", "/usr/bin/waybar", "/usr/lib/polkit-1/polkit-agent-helper-1") })
        assertFalse(PolkitAgent.available(hyprland) { emptyList() })
    }
}
