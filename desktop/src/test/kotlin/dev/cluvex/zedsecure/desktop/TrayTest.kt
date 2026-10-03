package dev.cluvex.zedsecure.desktop

import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals

class TrayTest {
    @Test
    fun `flags and emoji are left out of tray labels the menu cannot draw`() {
        assertEquals("Germany / REALITY", "🇩🇪 Germany / REALITY".trayText())
        assertEquals("Connected · Germany", "Connected · 🇩🇪 Germany".trayText())
        assertEquals("Fast ❤ server", "Fast ❤️ server".trayText())
        assertEquals("سرور آلمان", "سرور آلمان".trayText())
    }

    @Test
    fun `the Windows tray icon is an icon file with every size in it`() {
        val images = listOf(16, 32, 48).map { px ->
            BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB).apply { setRGB(0, 0, 0x80FF0000.toInt()) }
        }
        val ico = icoBytes(images)
        val buf = ByteBuffer.wrap(ico).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0, buf.getShort(0).toInt())
        assertEquals(1, buf.getShort(2).toInt())
        assertEquals(images.size, buf.getShort(4).toInt())
        var end = 0
        images.forEachIndexed { i, image ->
            val entry = 6 + 16 * i
            assertEquals(image.width, ico[entry].toInt() and 0xff)
            assertEquals(32, buf.getShort(entry + 6).toInt())
            val size = buf.getInt(entry + 8)
            val offset = buf.getInt(entry + 12)
            assertEquals(40, buf.getInt(offset))
            assertEquals(image.width, buf.getInt(offset + 4))
            assertEquals(image.height * 2, buf.getInt(offset + 8))
            val maskRow = ((image.width + 31) / 32) * 4
            assertEquals(40 + image.width * image.height * 4 + maskRow * image.height, size)
            val lastRowFirstPixel = offset + 40 + (image.height - 1) * image.width * 4
            assertEquals(listOf(0x00, 0x00, 0xFF, 0x80), (0 until 4).map { ico[lastRowFirstPixel + it].toInt() and 0xff })
            end = offset + size
        }
        assertEquals(ico.size, end)
    }
}
