package dev.cluvex.zedsecure.desktop.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

class BundledEnginesTest {
    private val binDir = File("src/main/resources/bin")

    private val required = mapOf(
        Os.LINUX to ("linux" to listOf("xray", "libcronet.so", "hev-socks5-tunnel", "zeddns")),
        Os.WINDOWS to ("windows" to listOf("xray.exe", "libcronet.dll", "hev-socks5-tunnel.exe", "zeddns.exe") + HevBinary.WINDOWS_COMPANIONS),
        Os.MACOS to ("macos" to listOf("xray", "hev-socks5-tunnel", "zeddns")),
    )

    private val neverLaunched = listOf("psiphon-client", "masterdns-client", "snispoof", "snowflake-client", "tor")

    @Test
    fun `this OS has every engine the desktop starts`() {
        val (dir, names) = required[Os.current] ?: return
        if (!File(binDir, dir).isDirectory) return
        val missing = names.filterNot { File(binDir, "$dir/$it").exists() }
        if (missing.isNotEmpty()) {
            fail("not bundled for ${Os.current}, so these do nothing at run time: ${missing.joinToString()}")
        }
    }

    @Test
    fun `the other supported OS is complete too, when its binaries are present`() {
        required.filterKeys { it != Os.current }.forEach { (os, spec) ->
            val (dir, names) = spec
            val present = names.filter { File(binDir, "$dir/$it").exists() }
            if (present.isNotEmpty() && present.size != names.size) {
                fail("$os is half-staged: has ${present.joinToString()}, missing ${(names - present.toSet()).joinToString()}")
            }
        }
    }

    @Test
    fun `nothing is bundled that no code launches`() {
        if (!binDir.isDirectory) return
        val dead = binDir.listFiles().orEmpty().filter { it.isDirectory }.flatMap { osDir ->
            neverLaunched.filter { File(osDir, it).exists() }.map { "${osDir.name}/$it" }
        }
        if (dead.isNotEmpty()) {
            fail("bundled but never launched by the desktop module: ${dead.joinToString()}")
        }
    }

    @Test
    fun `the required names are the ones the extractors ask for`() {
        assertTrue("hev-socks5-tunnel" in required.getValue(Os.LINUX).second)
        assertTrue("hev-socks5-tunnel.exe" in required.getValue(Os.WINDOWS).second)
        assertTrue("zeddns" in required.getValue(Os.LINUX).second)
        assertTrue("zeddns.exe" in required.getValue(Os.WINDOWS).second)
        assertTrue("xray" in required.getValue(Os.LINUX).second)
        assertTrue("xray.exe" in required.getValue(Os.WINDOWS).second)
        assertTrue("hev-socks5-tunnel" in required.getValue(Os.MACOS).second)
        assertTrue("zeddns" in required.getValue(Os.MACOS).second)
        assertTrue("xray" in required.getValue(Os.MACOS).second)
    }

    @Test
    fun `hev on Windows ships with the runtime and the Wintun driver it loads`() {
        val windows = required.getValue(Os.WINDOWS).second
        assertTrue("msys-2.0.dll" in HevBinary.WINDOWS_COMPANIONS, "hev's Windows build is an MSYS2 program")
        assertTrue("wintun.dll" in HevBinary.WINDOWS_COMPANIONS, "the TUN adapter comes from Wintun")
        HevBinary.WINDOWS_COMPANIONS.forEach { assertTrue(it in windows, "$it is extracted but not required") }
    }
}
