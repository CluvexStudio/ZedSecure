package dev.cluvex.zedsecure.domain.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShadowsocksAuditTest {
    private fun build(s: ServerConfig): String = XrayJsonBuilder.build(s)

    @Test
    fun `sip002 link with a path keeps its port`() {
        val c = ConfigParser.parse(
            "ss://YWVzLTI1Ni1nY206cGFzc3dvcmQ=@192.168.100.1:8888/?plugin=obfs-local%3Bobfs%3Dhttp#X"
        )
        assertEquals("192.168.100.1", c.address)
        assertEquals(8888, c.port)
    }

    @Test
    fun `trojan link with a trailing slash keeps its port`() {
        val c = ConfigParser.parse("trojan://pw@example.com:8443/#X")
        assertEquals("example.com", c.address)
        assertEquals(8443, c.port)
    }

    @Test
    fun `hysteria2 portless link keeps a clean host`() {
        val c = ConfigParser.parse("hysteria2://pw@example.com/?insecure=1&sni=a.com#X")
        assertEquals("example.com", c.address)
    }

    @Test
    fun `vless link with a path keeps host and port`() {
        val c = ConfigParser.parse("vless://uuid-1234@example.com:2053/?type=ws&security=tls#X")
        assertEquals("example.com", c.address)
        assertEquals(2053, c.port)
    }

    @Test
    fun `base64 userinfo containing a slash is not truncated`() {
        val creds = "YWVzLTI1Ni1nY206YS9iL2M="
        val c = ConfigParser.parse("ss://$creds@1.2.3.4:9000#X")
        assertEquals("a/b/c", c.userId)
        assertEquals(9000, c.port)
    }

    @Test
    fun `none and plain ciphers are rejected on import`() {
        listOf("bm9uZTpwdw==" to "none", "cGxhaW46cHc=" to "plain").forEach { (creds, name) ->
            val e = runCatching { ConfigParser.parse("ss://$creds@1.2.3.4:8388#X") }.exceptionOrNull()
            assertTrue("$name should be rejected", e is ConfigParseException)
            assertEquals(
                ConfigParseException.Reason.UnsupportedSsCipher,
                (e as ConfigParseException).reason,
            )
        }
    }

    @Test
    fun `a link with no cipher is rejected rather than defaulted`() {
        val e = runCatching { ConfigParser.parse("ss://cGFzc3dvcmQ=@1.2.3.4:8388#X") }.exceptionOrNull()
        assertEquals(
            ConfigParseException.Reason.MissingSsCipher,
            (e as? ConfigParseException)?.reason,
        )
    }

    @Test
    fun `a real cipher is emitted verbatim`() {
        val c = ConfigParser.parse("ss://YWVzLTEyOC1nY206cHc=@1.2.3.4:8388#X")
        assertEquals("aes-128-gcm", c.shadowsocksMethod)
        assertTrue(build(c).contains("\"method\": \"aes-128-gcm\""))
    }

    @Test
    fun `legacy link keeps a password containing an at sign`() {
        val b64 = "YWVzLTI1Ni1nY206cGFAc3NAMS4yLjMuNDo4Mzg4"
        val c = ConfigParser.parse("ss://$b64#X")
        assertEquals("pa@ss", c.userId)
        assertEquals("1.2.3.4", c.address)
        assertEquals(8388, c.port)
    }

    @Test
    fun `pinned cert and verify-by-name survive link, config and re-export`() {
        val link = "trojan://pw@example.com:443?security=tls&sni=a.com" +
            "&pcs=abc123&vcn=real.example.com#X"
        val c = ConfigParser.parse(link)
        assertEquals("abc123", c.pinnedCertSha256)
        assertEquals("real.example.com", c.tls.verifyPeerCertByName)

        val json = build(c)
        assertTrue(json.contains("\"pinnedPeerCertSha256\": \"abc123\""))
        assertTrue(json.contains("\"verifyPeerCertByName\": \"real.example.com\""))

        val again = ConfigParser.parse(ShareLink.build(c))
        assertEquals("abc123", again.pinnedCertSha256)
        assertEquals("real.example.com", again.tls.verifyPeerCertByName)
    }

    @Test
    fun `reality post-quantum verify key reaches the config`() {
        val c = ConfigParser.parse(
            "vless://uuid@example.com:443?security=reality&pbk=k&sid=ab&pqv=pqkey#X"
        )
        assertEquals("pqkey", c.tls.mldsa65Verify)
        assertTrue(build(c).contains("\"mldsa65Verify\": \"pqkey\""))
    }

    @Test
    fun `mux is disabled for shadowsocks trojan socks and http`() {
        val opts = XrayJsonBuilder.BuildOptions(muxEnabled = true)
        listOf(
            "ss://YWVzLTI1Ni1nY206cHc=@1.2.3.4:8388#X",
            "trojan://pw@example.com:443#X",
            "socks://user:pw@1.2.3.4:1080#X",
            "http://user:pw@1.2.3.4:8080#X",
        ).forEach { link ->
            val json = XrayJsonBuilder.build(ConfigParser.parse(link), options = opts)
            assertTrue("$link must not enable mux", json.contains("\"enabled\": false"))
        }
    }

    @Test
    fun `mux stays available for vless`() {
        val json = XrayJsonBuilder.build(
            ConfigParser.parse("vless://uuid@example.com:443?security=tls#X"),
            options = XrayJsonBuilder.BuildOptions(muxEnabled = true),
        )
        assertTrue(json.contains("\"enabled\": true"))
    }

    @Test
    fun `xhttp extra reaches the core and survives a re-export`() {
        val extra = """{"scMaxEachPostBytes":1000000}"""
        val c = ConfigParser.parse(
            "vless://uuid@example.com:443?type=xhttp&security=tls&extra=" +
                ShareLink.build(
                    ServerConfig(
                        protocol = Protocol.VLESS, remark = "t", address = "e", port = 1,
                        userId = "u", transport = TransportConfig(network = "xhttp", xhttpExtra = extra),
                    )
                ).substringAfter("extra=").substringBefore("&") + "#X"
        )
        assertEquals(extra, c.transport.xhttpExtra)
        assertTrue(build(c).contains("\"scMaxEachPostBytes\""))
        assertEquals(extra, ConfigParser.parse(ShareLink.build(c)).transport.xhttpExtra)
    }

    @Test
    fun `obfs-local accepts the path alias v2rayNG writes`() {
        val creds = "YWVzLTI1Ni1nY206cHc="
        val c = ConfigParser.parse(
            "ss://$creds@1.2.3.4:8388?plugin=obfs-local%3Bobfs%3Dhttp%3Bobfs-host%3Dbing.com%3Bpath%3D%2Fabc#X"
        )
        assertEquals("tcp", c.transport.network)
        assertEquals("http", c.transport.headerType)
        assertEquals("bing.com", c.transport.host)
        assertEquals("/abc", c.transport.path)
    }

    @Test
    fun `v2ray-plugin maps onto websocket plus tls`() {
        val creds = "YWVzLTI1Ni1nY206cHc="
        val c = ConfigParser.parse(
            "ss://$creds@1.2.3.4:8388?plugin=v2ray-plugin%3Btls%3Bhost%3Da.com%3Bpath%3D%2Fws#X"
        )
        assertEquals("ws", c.transport.network)
        assertEquals("a.com", c.transport.host)
        assertEquals("/ws", c.transport.path)
        assertEquals("tls", c.tls.security)
    }

    @Test
    fun `plugins this core cannot express are rejected, not silently imported`() {
        val creds = "YWVzLTI1Ni1nY206cHc="
        listOf("obfs-local%3Bobfs%3Dtls", "shadow-tls", "restls").forEach { plugin ->
            val e = runCatching {
                ConfigParser.parse("ss://$creds@1.2.3.4:8388?plugin=$plugin#X")
            }.exceptionOrNull()
            assertEquals(
                "plugin $plugin",
                ConfigParseException.Reason.UnsupportedSsPlugin,
                (e as? ConfigParseException)?.reason,
            )
        }
    }

    @Test
    fun `mkcp seed and header are emitted as finalmask mkcp-legacy entries`() {
        val json = build(
            ConfigParser.parse(
                "vless://uuid@example.com:443?type=kcp&headerType=wechat-video&seed=s3cr3t#X"
            )
        )
        assertTrue(json.contains("\"finalmask\""))
        assertTrue(json.contains("\"mkcp-legacy\""))
        assertTrue(json.contains("\"value\": \"s3cr3t\""))

        assertTrue(json.contains("\"header\": \"wechat\""))

        assertFalse(json.contains("\"kcpSettings\": {\n          \"header\""))
    }

    @Test
    fun `plain mkcp still emits the legacy framing entry`() {
        val json = build(ConfigParser.parse("vless://uuid@example.com:443?type=kcp#X"))
        assertTrue(json.contains("\"mkcp-legacy\""))
    }

    @Test
    fun `pasted config with a removed cipher fails with a reason`() {
        val raw = """
            {"inbounds":[{"port":10808,"protocol":"socks","settings":{}}],
             "outbounds":[{"protocol":"shadowsocks","settings":{"servers":[
               {"address":"1.2.3.4","port":8388,"method":"plain","password":"p"}]}}]}
        """.trimIndent()
        val e = runCatching { XrayJsonBuilder.normalizeRawJson(raw) }.exceptionOrNull()
        assertEquals(
            ConfigParseException.Reason.UnsupportedSsCipher,
            (e as? ConfigParseException)?.reason,
        )
    }

    @Test
    fun `pasted trojan config has its removed flow stripped`() {
        val raw = """
            {"inbounds":[{"port":10808,"protocol":"socks","settings":{}}],
             "outbounds":[{"protocol":"trojan","settings":{"servers":[
               {"address":"1.2.3.4","port":443,"password":"p","flow":"xtls-rprx-vision"}]}}]}
        """.trimIndent()
        assertFalse(XrayJsonBuilder.normalizeRawJson(raw).contains("xtls-rprx-vision"))
    }

    @Test
    fun `pasted config using a removed transport is reported`() {
        val raw = """
            {"inbounds":[{"port":10808,"protocol":"socks","settings":{}}],
             "outbounds":[{"protocol":"vless","streamSettings":{"network":"quic"},
               "settings":{"vnext":[{"address":"a","port":443,"users":[{"id":"u"}]}]}}]}
        """.trimIndent()
        assertTrue(
            runCatching { XrayJsonBuilder.normalizeRawJson(raw) }
                .exceptionOrNull() is UnsupportedTransportException
        )
    }

    @Test
    fun `trojan no longer forces a randomized fingerprint`() {
        assertNull(ConfigParser.parse("trojan://pw@example.com:443#X").tls.fingerprint)
        assertEquals(
            "chrome",
            ConfigParser.parse("trojan://pw@example.com:443?fp=chrome#X").tls.fingerprint,
        )
    }

    @Test
    fun `a plus sign in the remark stays a plus sign`() {
        assertEquals("My+Server", ConfigParser.parse("trojan://pw@example.com:443#My%2BServer").remark)
    }

    @Test
    fun `a bare socks username is not misread as base64`() {
        val c = ConfigParser.parse("socks://user@1.2.3.4:1080#X")
        assertEquals("user", c.username)
        assertEquals("", c.userId)
    }

    @Test
    fun `custom json in the flat form shows its address and port`() {
        val raw = """
            {"outbounds":[{"protocol":"shadowsocks","settings":{
              "address":"1.2.3.4","port":8388,"method":"aes-256-gcm","password":"p"}}]}
        """.trimIndent()
        val info = CustomConfig.inspect(raw)
        assertEquals("1.2.3.4", info.address)
        assertEquals(8388, info.port)
    }
}
