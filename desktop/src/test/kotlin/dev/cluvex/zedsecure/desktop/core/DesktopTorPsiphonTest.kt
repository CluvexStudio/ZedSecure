package dev.cluvex.zedsecure.desktop.core

import dev.cluvex.zedsecure.desktop.platform.DesktopXray
import dev.cluvex.zedsecure.domain.model.AppSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopTorPsiphonTest {
    private fun bundle(windows: Boolean): File {
        val root = Files.createTempDirectory("tor-bundle").toFile()
        val suffix = if (windows) ".exe" else ""
        File(root, "pt").mkdirs()
        File(root, "pt/lyrebird$suffix").writeText("")
        File(root, "pt/conjure-client$suffix").writeText("")
        File(root, "bridges_default.lst").writeText("obfs4 192.0.2.1:443 AAAA cert=x iat-mode=0\n")
        return root
    }

    @Test
    fun `lyrebird carries obfs4 and snowflake and conjure gets its registration URL`() {
        val root = bundle(windows = false)
        val t = DesktopTor.transports(root, windows = false)

        assertEquals(File(root, "pt/lyrebird").absolutePath, t.obfs)
        assertEquals(t.obfs, t.snowflake)
        assertTrue(t.conjure!!.endsWith("conjure-client -registerURL https://registration.refraction.network/api"))
        assertNull(t.dnstt)
        root.deleteRecursively()
    }

    @Test
    fun `the Windows bundle uses the exe names`() {
        val root = bundle(windows = true)
        val t = DesktopTor.transports(root, windows = true)

        assertEquals(File(root, "pt/lyrebird.exe").absolutePath, t.obfs)
        assertTrue(t.conjure!!.contains("conjure-client.exe -registerURL"))
        root.deleteRecursively()
    }

    @Test
    fun `the torrc points every transport at the bundle and keeps tor's socks port`() {
        val windows = Os.current == Os.WINDOWS
        val root = bundle(windows)
        val lyrebird = File(root, "pt/lyrebird" + if (windows) ".exe" else "").absolutePath
        val torrc = DesktopTor.torrc(
            AppSettings(torBridgesMode = "default", torBridgeTransport = "obfs4"),
            root,
            File(root, "data"),
        )

        assertTrue("SocksPort 127.0.0.1:9250" in torrc, torrc)
        assertTrue("ClientTransportPlugin obfs4,obfs3,scramblesuit,meek_lite,webtunnel exec $lyrebird" in torrc, torrc)
        assertTrue("ClientTransportPlugin snowflake exec $lyrebird" in torrc, torrc)
        assertTrue("Bridge obfs4 192.0.2.1:443" in torrc, torrc)
        assertTrue("dnstt" !in torrc, torrc)
        root.deleteRecursively()
    }

    @Test
    fun `psiphon notices are read whatever order their fields come in`() {
        val up = DesktopPsiphon.Notice.parse("""{"data":{"count":1},"noticeType":"Tunnels","timestamp":"2026-10-01T08:51:49Z"}""")
        val down = DesktopPsiphon.Notice.parse("""{"noticeType":"Tunnels","data":{"count":0}}""")
        val region = DesktopPsiphon.Notice.parse("""{"data":{"region":"DE"},"noticeType":"ConnectedServerRegion"}""")

        assertEquals(1, up!!.tunnels)
        assertEquals(0, down!!.tunnels)
        assertEquals("DE", region!!.string("region"))
        assertEquals(0, region.tunnels)
        assertNull(DesktopPsiphon.Notice.parse("not json"))
        assertNull(DesktopPsiphon.Notice.parse("""{"no":"type"}"""))
    }

    @Test
    fun `the front takes socks and http on the app port and hands everything to the engine`() {
        val root = Json.parseToJsonElement(DesktopXray.frontConfig(10808, 9250)).jsonObject
        val inbound = root["inbounds"]!!.jsonArray.single().jsonObject
        val outbound = root["outbounds"]!!.jsonArray.single().jsonObject
        val server = outbound["settings"]!!.jsonObject["servers"]!!.jsonArray.single().jsonObject

        assertEquals("socks", inbound["protocol"]!!.jsonPrimitive.content)
        assertEquals("127.0.0.1", inbound["listen"]!!.jsonPrimitive.content)
        assertEquals(10808, inbound["port"]!!.jsonPrimitive.int)
        assertEquals("proxy", outbound["tag"]!!.jsonPrimitive.content)
        assertEquals(9250, server["port"]!!.jsonPrimitive.int)
    }
}
