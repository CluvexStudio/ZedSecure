package dev.cluvex.zedsecure.domain.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater

class AmneziaQrTest {
    private fun frame(total: Int, index: Int, data: ByteArray, magic: Int = 1984): String {
        val out = ByteArrayOutputStream()
        out.write((magic ushr 8) and 0xFF)
        out.write(magic and 0xFF)
        out.write(total)
        out.write(index)
        out.write((data.size ushr 24) and 0xFF)
        out.write((data.size ushr 16) and 0xFF)
        out.write((data.size ushr 8) and 0xFF)
        out.write(data.size and 0xFF)
        out.write(data)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
    }

    private fun qCompress(text: String): ByteArray {
        val raw = text.toByteArray()
        val deflater = Deflater()
        deflater.setInput(raw)
        deflater.finish()
        val body = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!deflater.finished()) body.write(buf, 0, deflater.deflate(buf))
        deflater.end()
        return ByteArrayOutputStream().apply {
            write((raw.size ushr 24) and 0xFF)
            write((raw.size ushr 16) and 0xFF)
            write((raw.size ushr 8) and 0xFF)
            write(raw.size and 0xFF)
            write(body.toByteArray())
        }.toByteArray()
    }

    @Test
    fun `an ordinary single-code payload is not mistaken for a fragment`() {
        assertNull(AmneziaQr.chunk("vless://id@host:443#a"))
        assertNull(AmneziaQr.chunk(Base64.getUrlEncoder().withoutPadding().encodeToString("hello there".toByteArray())))

        assertNull(AmneziaQr.chunk(frame(total = 2, index = 0, data = "abcd".toByteArray(), magic = 1985)))
    }

    @Test
    fun `frames arriving out of order and twice still assemble in order`() {
        val assembler = AmneziaQr.Assembler()

        assertNull(assembler.offer(frame(3, 1, "world".toByteArray())))
        assertNull(assembler.offer(frame(3, 1, "world".toByteArray())))
        assertEquals(1, assembler.received)
        assertEquals(3, assembler.expected)
        assertNull(assembler.offer(frame(3, 2, "!".toByteArray())))

        val whole = assembler.offer(frame(3, 0, "hello ".toByteArray()))
        val bytes = Base64.getUrlDecoder().decode(whole)
        assertEquals("hello world!", String(bytes))
    }

    @Test
    fun `a complete series imports as the config it carries`() {
        val inner = """{\"client_ip\":\"10.8.1.2\",\"client_priv_key\":\"PRIV=\",""" +
            """\"server_pub_key\":\"PUB=\",\"port\":51820,\"Jc\":\"4\",\"H1\":\"1\"}"""
        val json = """{"containers":[{"container":"amnezia-awg","awg":{"last_config":"$inner"}}],""" +
            """"defaultContainer":"amnezia-awg","hostName":"vpn.example.com"}"""
        val payload = qCompress(json)

        val assembler = AmneziaQr.Assembler()
        val parts = payload.toList().chunked(64).map { it.toByteArray() }
        var whole: String? = null
        parts.forEachIndexed { i, part -> whole = assembler.offer(frame(parts.size, i, part)) ?: whole }

        val result = AmneziaLink.parse(whole!!)
        assertTrue("expected configs, got $result", result is AmneziaLink.Payload.Configs)
        assertTrue((result as AmneziaLink.Payload.Configs).items.single().text.startsWith("[Interface]"))
    }

    @Test
    fun `pointing the camera at a different series starts that one instead of mixing the two`() {
        val assembler = AmneziaQr.Assembler()
        assertNull(assembler.offer(frame(4, 0, "old".toByteArray())))

        assertNull(assembler.offer(frame(2, 0, "new ".toByteArray())))
        assertEquals(1, assembler.received)
        val whole = assembler.offer(frame(2, 1, "one".toByteArray()))
        assertEquals("new one", String(Base64.getUrlDecoder().decode(whole)))
    }

    @Test
    fun `a truncated frame is refused rather than half-read`() {
        val good = frame(2, 0, "abcdefgh".toByteArray())
        val raw = Base64.getUrlDecoder().decode(good)
        val cut = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.copyOfRange(0, raw.size - 3))

        assertNull(AmneziaQr.chunk(cut))
        assertNull(AmneziaQr.chunk(Base64.getUrlEncoder().withoutPadding().encodeToString(byteArrayOf(7, -64))))

        assertNull(AmneziaQr.chunk(frame(total = 2, index = 5, data = "x".toByteArray())))
    }
}
