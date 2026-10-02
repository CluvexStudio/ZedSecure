package dev.cluvex.zedsecure.desktop

import dev.cluvex.zedsecure.desktop.core.Os
import dev.cluvex.zedsecure.domain.model.RenderingMode
import dev.cluvex.zedsecure.ui.platform.AutomaticRendering
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopRenderingTest {
    private val debianNvidia = "NVRM version: NVIDIA UNIX x86_64 Kernel Module  550.163.01  Thu Apr 24 2025\nGCC version:  gcc version 14.2.0 (Debian 14.2.0-19)"
    private val newNvidia = "NVRM version: NVIDIA UNIX Open Kernel Module for x86_64  580.82.07  Release Build"
    private val wayland = mapOf("XDG_SESSION_TYPE" to "wayland", "WAYLAND_DISPLAY" to "wayland-0")
    private val x11 = mapOf("XDG_SESSION_TYPE" to "x11", "DISPLAY" to ":0")

    private fun decide(
        mode: RenderingMode = RenderingMode.Auto,
        args: Array<String> = emptyArray(),
        env: Map<String, String> = emptyMap(),
        skiko: String? = null,
        os: Os = Os.LINUX,
        nvidia: String? = null,
    ) = DesktopRendering.decide(mode, args, env, skiko, os, nvidia)

    @Test
    fun `NVIDIA 550 under Wayland draws in software, as on Debian 13 KDE`() {
        val d = decide(env = wayland, nvidia = debianNvidia)
        assertTrue(d.software)
        assertEquals(AutomaticRendering.SoftwareNvidiaWayland, d.automatic)
    }

    @Test
    fun `NVIDIA with explicit sync, X11 sessions and Mesa keep the graphics card`() {
        assertFalse(decide(env = wayland, nvidia = newNvidia).software)
        assertFalse(decide(env = x11, nvidia = debianNvidia).software)
        assertFalse(decide(env = wayland).software)
        assertFalse(decide(os = Os.MACOS).software)
    }

    @Test
    fun `Windows draws in software unless the graphics card is chosen`() {
        assertTrue(decide(os = Os.WINDOWS).software)
        assertFalse(decide(os = Os.WINDOWS, mode = RenderingMode.Gpu).software)
        assertFalse(decide(os = Os.WINDOWS, args = arrayOf("--gpu-rendering")).software)
    }

    @Test
    fun `an explicit choice wins over the automatic one, and Skiko's own switch over everything`() {
        assertTrue(decide(mode = RenderingMode.Software).software)
        assertTrue(decide(args = arrayOf("--software-rendering"), mode = RenderingMode.Gpu).software)
        assertTrue(decide(env = mapOf("ZEDSECURE_RENDERING" to "software")).software)
        assertTrue(decide(env = mapOf("ZEDSECURE_SOFTWARE_RENDERING" to "1")).software)
        assertFalse(decide(env = wayland + ("ZEDSECURE_RENDERING" to "gpu"), nvidia = debianNvidia).software)
        val external = decide(env = mapOf("SKIKO_RENDER_API" to "OPENGL"), os = Os.WINDOWS, mode = RenderingMode.Software)
        assertFalse(external.software)
        assertEquals(DesktopRendering.Source.External, external.source)
        assertTrue(decide(skiko = "SOFTWARE_COMPAT").software)
    }

    @Test
    fun `an unreadable driver version counts as an old one`() {
        assertTrue(DesktopRendering.driverBefore555("NVRM version: something odd"))
        assertTrue(DesktopRendering.driverBefore555(debianNvidia))
        assertFalse(DesktopRendering.driverBefore555(newNvidia))
    }
}
