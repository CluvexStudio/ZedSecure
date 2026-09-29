package dev.cluvex.zedsecure.desktop.core

import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BundledBinaryTest {
    private val work = Files.createTempDirectory("bundled-binary").toFile()

    @AfterTest
    fun cleanUp() {
        work.deleteRecursively()
    }

    private fun extract(companions: List<String> = listOf("fake-companion.dll")) =
        BundledBinary.extract(work, "fake-engine", "fake-engine.exe", companions)

    @Test
    fun `the engine for this OS lands in the work directory`() {
        if (Os.current == Os.OTHER) return
        val out = assertNotNull(extract())
        val expected = if (Os.current == Os.WINDOWS) "fake-engine.exe" else "fake-engine"
        assertEquals(expected, out.name)
        assertEquals(work, out.parentFile)
        val tag = when (Os.current) {
            Os.LINUX -> "linux"
            Os.MACOS -> "macos"
            else -> "windows"
        }
        assertEquals("fake-engine $tag", out.readText())
        if (Os.current != Os.WINDOWS) assertTrue(out.canExecute())
    }

    @Test
    fun `Windows companions go next to the engine and nowhere else`() {
        if (Os.current == Os.OTHER) return
        assertNotNull(extract())
        val companion = work.resolve("fake-companion.dll")
        if (Os.current == Os.WINDOWS) {
            assertEquals("fake companion", companion.readText())
        } else {
            assertFalse(companion.exists(), "a Windows DLL was copied on ${Os.current}")
        }
    }

    @Test
    fun `a missing companion still hands back the engine`() {
        if (Os.current == Os.OTHER) return
        assertNotNull(extract(companions = listOf("not-bundled.dll")))
        assertFalse(work.resolve("not-bundled.dll").exists())
    }

    @Test
    fun `an engine that is not bundled gives null`() {
        assertNull(BundledBinary.extract(work, "no-such-engine", "no-such-engine.exe"))
    }

    @Test
    fun `extracting twice overwrites the first copy`() {
        if (Os.current == Os.OTHER) return
        val first = assertNotNull(extract())
        first.writeText("stale")
        val second = assertNotNull(extract())
        assertEquals(first, second)
        assertTrue(second.readText().startsWith("fake-engine"))
    }
}
