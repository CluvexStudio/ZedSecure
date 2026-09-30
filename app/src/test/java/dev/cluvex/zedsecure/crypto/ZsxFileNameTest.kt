package dev.cluvex.zedsecure.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZsxFileNameTest {
    @Test
    fun `names become safe file names ending in zsx`() {
        assertEquals("Berlin_Fast.zsx", zsxFileName("Berlin Fast"))
        assertEquals("config.zsx", zsxFileName("  "))
        assertEquals("node.zsx", zsxFileName("node.zsx"))
        assertEquals("_____.zsx", zsxFileName("برلین"))
    }

    @Test
    fun `long names are cut before the extension`() {
        val name = zsxFileName("x".repeat(80))
        assertTrue(name.endsWith(".zsx"))
        assertEquals(48 + 4, name.length)
    }
}
