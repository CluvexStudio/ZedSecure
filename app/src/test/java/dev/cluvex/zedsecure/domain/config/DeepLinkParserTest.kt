package dev.cluvex.zedsecure.domain.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.URLEncoder

class DeepLinkParserTest {
    private val vless = "vless://11111111-1111-1111-1111-111111111111@a.example:443" +
        "?encryption=none&security=tls&sni=a.example&type=tcp"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    @Test
    fun `v2rayng install-config with an encoded subscription and a fragment name`() {
        val r = DeepLinkParser.parse("v2rayng://install-config?url=${enc("https://sub.example/api/v1?token=a1&x=2")}#My%20Sub")
        assertEquals(DeepLinkRequest.Subscription("https://sub.example/api/v1?token=a1&x=2", "My Sub"), r)
    }

    @Test
    fun `an unencoded subscription url keeps its own query string`() {
        val r = DeepLinkParser.parse("v2rayng://install-sub?url=https://sub.example/s?a=1&b=2&name=Work")
        assertEquals(DeepLinkRequest.Subscription("https://sub.example/s?a=1&b=2", "Work"), r)
    }

    @Test
    fun `an already plain url is not decoded a second time`() {
        val r = DeepLinkParser.parse("v2rayng://install-config?url=https://sub.example/s?t=a%2Bb")
        assertEquals(DeepLinkRequest.Subscription("https://sub.example/s?t=a%2Bb", null), r)
    }

    @Test
    fun `v2rayng install-config can carry a config instead of a subscription`() {
        val r = DeepLinkParser.parse("v2rayng://install-config?url=${enc("$vless#Remark")}")
        assertEquals(DeepLinkRequest.ConfigText("$vless#Remark"), r)
    }

    @Test
    fun `a remark carried outside the payload is put back on the config`() {
        val r = DeepLinkParser.parse("v2rayng://install-config?url=$vless#Outer")

        assertTrue(r is DeepLinkRequest.ConfigText)
        assertTrue((r as DeepLinkRequest.ConfigText).text.endsWith("#Outer"))
    }

    @Test
    fun `hiddify import path form with a name`() {
        val r = DeepLinkParser.parse("hiddify://import/https://sub.example/abc?token=1#Home")
        assertEquals(DeepLinkRequest.Subscription("https://sub.example/abc?token=1", "Home"), r)
    }

    @Test
    fun `hiddify install-config query form`() {
        val r = DeepLinkParser.parse("hiddify://install-config?url=${enc("https://sub.example/h")}")
        assertEquals(DeepLinkRequest.Subscription("https://sub.example/h", null), r)
    }

    @Test
    fun `sing-box remote profile`() {
        val r = DeepLinkParser.parse("sing-box://import-remote-profile?url=${enc("https://sub.example/sb")}#SB")
        assertEquals(DeepLinkRequest.Subscription("https://sub.example/sb", "SB"), r)
    }

    @Test
    fun `clash and clashmeta take the name from the query`() {
        val url = enc("https://sub.example/c")
        assertEquals(
            DeepLinkRequest.Subscription("https://sub.example/c", "Клэш"),
            DeepLinkParser.parse("clash://install-config?url=$url&name=${enc("Клэш")}"),
        )
        assertEquals(
            DeepLinkRequest.Subscription("https://sub.example/c", null),
            DeepLinkParser.parse("clashmeta://install-config?url=$url"),
        )
    }

    @Test
    fun `v2raytun and streisand import forms`() {
        assertEquals(
            DeepLinkRequest.Subscription("https://sub.example/t", null),
            DeepLinkParser.parse("v2raytun://import/https://sub.example/t"),
        )
        assertEquals(
            DeepLinkRequest.Subscription("https://sub.example/s", null),
            DeepLinkParser.parse("streisand://import/https://sub.example/s"),
        )
    }

    @Test
    fun `happ add with a config keeps the config's own remark`() {
        val r = DeepLinkParser.parse("happ://add/$vless#Happ%20Remark")
        assertEquals(DeepLinkRequest.ConfigText("$vless#Happ%20Remark"), r)
    }

    @Test
    fun `zedsecure import and payload forms`() {
        assertEquals(
            DeepLinkRequest.Subscription("https://sub.example/z", "Mine"),
            DeepLinkParser.parse("zedsecure://import?url=${enc("https://sub.example/z")}&name=Mine"),
        )
        assertEquals(
            DeepLinkRequest.Subscription("https://sub.example/z2", null),
            DeepLinkParser.parse("zedsecure://sub?url=https://sub.example/z2"),
        )
        val shared = ZedLink.build("Tor here", ProfileSource.Tor)!!
        assertEquals(DeepLinkRequest.ConfigText(shared), DeepLinkParser.parse(shared))
    }

    @Test
    fun `raw config schemes are config text`() {
        assertEquals(DeepLinkRequest.ConfigText(vless), DeepLinkParser.parse(vless))
        val ss = "ss://YWVzLTI1Ni1nY206cGFzcw@s.example:8388#SS"
        assertEquals(DeepLinkRequest.ConfigText(ss), DeepLinkParser.parse(ss))
    }

    @Test
    fun `payloads that are neither http nor a readable config are refused`() {
        assertNull(DeepLinkParser.parse("v2rayng://install-config?url=${enc("file:///data/data/x")}"))
        assertNull(DeepLinkParser.parse("v2rayng://install-config?url=javascript:alert(1)"))
        assertNull(DeepLinkParser.parse("hiddify://import/content://evil/provider"))
        assertNull(DeepLinkParser.parse("v2rayng://install-config?url="))
        assertNull(DeepLinkParser.parse("v2rayng://unknown-action?url=${enc("https://sub.example")}"))
        assertNull(DeepLinkParser.parse("happ://add/not a config"))
        assertNull(DeepLinkParser.parse("mailto://someone@example.com"))
        assertNull(DeepLinkParser.parse("https://sub.example/direct-web-link"))
    }

    @Test
    fun `preview names the subscription host and the config endpoint`() {
        val sub = DeepLinkPreview.of(DeepLinkRequest.Subscription("https://user@sub.example:8443/p?x=1", "N"))
        assertEquals("N", sub.name)
        assertEquals("sub.example:8443", sub.detail)
        val cfg = DeepLinkPreview.of(DeepLinkRequest.ConfigText("$vless#Remark"))
        assertEquals("Remark", cfg.name)
        assertTrue(cfg.detail, cfg.detail.startsWith("VLESS · a.example:443"))
    }

    @Test
    fun `manifest registers exactly the parser's schemes`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val block = manifest.substringAfter("@string/deeplink_open_label").substringBefore("</intent-filter>")
        val schemes = Regex("""android:scheme="([^"]+)"""").findAll(block).map { it.groupValues[1] }.toSet()
        assertEquals(DeepLinkParser.SCHEMES, schemes)
    }
}
