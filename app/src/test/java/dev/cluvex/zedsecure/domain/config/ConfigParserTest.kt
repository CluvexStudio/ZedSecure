package dev.cluvex.zedsecure.domain.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class ConfigParserTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parsesVlessRealityGrpc() {
        val link = "vless://11111111-2222-3333-4444-555555555555@example.com:8443" +
            "?encryption=none&security=reality&type=grpc&pbk=PUBKEY123&sid=ab12&fp=chrome" +
            "&serviceName=mygrpc&flow=xtls-rprx-vision#My%20Server"
        val c = ConfigParser.parse(link)

        assertEquals(Protocol.VLESS, c.protocol)
        assertEquals("example.com", c.address)
        assertEquals(8443, c.port)
        assertEquals("11111111-2222-3333-4444-555555555555", c.userId)
        assertEquals("My Server", c.remark)
        assertEquals("xtls-rprx-vision", c.flow)
        assertEquals("grpc", c.transport.network)
        assertEquals("mygrpc", c.transport.serviceName)
        assertEquals("reality", c.tls.security)
        assertEquals("PUBKEY123", c.tls.publicKey)
        assertEquals("ab12", c.tls.shortId)
        assertEquals("chrome", c.tls.fingerprint)
    }

    @Test
    fun parsesTrojanWithoutFlow() {
        val link = "trojan://mypassword@trojan.example.com:443?security=tls&type=tcp&sni=trojan.example.com#Trj"
        val c = ConfigParser.parse(link)
        assertEquals(Protocol.TROJAN, c.protocol)
        assertEquals("mypassword", c.userId)
        assertEquals("trojan.example.com", c.address)
        assertEquals(443, c.port)
        assertEquals("tls", c.tls.security)
        assertNull("Trojan flow must never be set", c.flow)
    }

    @Test
    @OptIn(ExperimentalEncodingApi::class)
    fun parsesVmessBase64() {
        val payload = """{"v":"2","ps":"VM Node","add":"1.2.3.4","port":"443","id":"aaaa-bbbb","aid":"0","net":"ws","type":"none","host":"cdn.example.com","path":"/ray","tls":"tls","scy":"auto"}"""
        val link = "vmess://" + Base64.Default.encode(payload.encodeToByteArray())
        val c = ConfigParser.parse(link)
        assertEquals(Protocol.VMESS, c.protocol)
        assertEquals("VM Node", c.remark)
        assertEquals("1.2.3.4", c.address)
        assertEquals(443, c.port)
        assertEquals("aaaa-bbbb", c.userId)
        assertEquals(0, c.alterId)
        assertEquals("ws", c.transport.network)
        assertEquals("/ray", c.transport.path)
        assertEquals("cdn.example.com", c.transport.host)
        assertEquals("tls", c.tls.security)
        assertEquals("", c.encryption)
    }

    @Test
    @OptIn(ExperimentalEncodingApi::class)
    fun parsesShadowsocksSip002() {
        val creds = Base64.Default.encode("aes-256-gcm:s3cr3t".encodeToByteArray())
        val link = "ss://$creds@ss.example.com:8388#SS%20Node"
        val c = ConfigParser.parse(link)
        assertEquals(Protocol.SHADOWSOCKS, c.protocol)
        assertEquals("aes-256-gcm", c.shadowsocksMethod)
        assertEquals("s3cr3t", c.userId)
        assertEquals("ss.example.com", c.address)
        assertEquals(8388, c.port)
        assertEquals("SS Node", c.remark)
    }

    @Test(expected = ConfigParseException::class)
    fun rejectsUnknownScheme() {
        ConfigParser.parse("ftp://not-a-proxy.example.com")
    }

    @Test
    fun parsesHttpProxyLink() {
        val c = ConfigParser.parse("http://user:pass@proxy.example.com:8080#Work")
        assertEquals("proxy.example.com", c.address)
        assertEquals(8080, c.port)
        assertEquals("Work", c.remark)
    }

    @Test
    fun parsesSocksLink() {
        val c = ConfigParser.parse("socks://user:pass@127.0.0.1:1080#Local")
        assertEquals("127.0.0.1", c.address)
        assertEquals(1080, c.port)
    }

    @Test
    fun keepsGrpcAuthorityAndKcpTuning() {
        val link = "vless://id@h.example.com:443?encryption=none&security=tls&type=grpc" +
            "&serviceName=svc&authority=auth.example.com#G"
        val c = ConfigParser.parse(link)
        assertEquals("auth.example.com", c.transport.authority)
        assertEquals("svc", c.transport.serviceName)
    }

    @Test
    fun omitsGeoReferencesWhenDataFilesAreMissing() {
        val link = "vless://id@h.example.com:443?encryption=none&security=tls&type=tcp#N"
        val cfg = XrayJsonBuilder.build(
            ConfigParser.parse(link),
            options = XrayJsonBuilder.BuildOptions(
                bypassLan = true,
                bypassIran = true,
                blockAds = true,
                geoAssetsAvailable = false,
                customDirectRules = listOf("geosite:category-ir", "example.com"),
            ),
        )
        assertTrue("must not emit geoip: without geoip.dat", !cfg.contains("geoip:"))
        assertTrue("must not emit geosite: without geosite.dat", !cfg.contains("geosite:"))

        assertTrue("LAN bypass must survive", cfg.contains("192.168.0.0/16"))

        assertTrue("plain user rule must survive", cfg.contains("example.com"))
    }

    @Test
    fun usesGeoReferencesWhenDataFilesArePresent() {
        val link = "vless://id@h.example.com:443?encryption=none&security=tls&type=tcp#N"
        val cfg = XrayJsonBuilder.build(
            ConfigParser.parse(link),
            options = XrayJsonBuilder.BuildOptions(
                bypassLan = true,
                geoAssetsAvailable = true,
            ),
        )
        assertTrue("should prefer geoip:private when available", cfg.contains("geoip:private"))
    }

    @Test
    fun speedtestConfigHasNoInboundToBindOn() {
        val link = "vless://id@h.example.com:443?encryption=none&security=tls&type=tcp#N"
        val cfg = XrayJsonBuilder.build(ConfigParser.parse(link), forSpeedtest = true)
        assertTrue("speedtest config must not open a local port", !cfg.contains("\"socks\""))
        assertTrue("speedtest config needs no stats", !cfg.contains("\"stats\""))
    }

    @Test
    fun allowInsecureIsTranslatedNotEmitted() {
        val on = "vless://id@h.example.com:443?encryption=none&security=tls&type=tcp&sni=cdn.example.com&insecure=1#X"
        val onCfg = XrayJsonBuilder.build(ConfigParser.parse(on))
        assertTrue(
            "allowInsecure must never reach this core — it hard-errors on it",
            !onCfg.contains("allowInsecure"),
        )
        assertTrue(
            "an insecure link with an SNI must translate to verifyPeerCertByName",
            onCfg.contains("verifyPeerCertByName") && onCfg.contains("cdn.example.com"),
        )

        val noSni = "vless://id@h.example.com:443?encryption=none&security=tls&type=tcp&insecure=1#X"
        val noSniCfg = XrayJsonBuilder.build(ConfigParser.parse(noSni))
        assertTrue("allowInsecure must never be emitted", !noSniCfg.contains("allowInsecure"))
        assertTrue(
            "without an SNI there is nothing to pin the relaxation to",
            !noSniCfg.contains("verifyPeerCertByName"),
        )

        val off = "vless://id@h.example.com:443?encryption=none&security=tls&type=tcp&sni=cdn.example.com#X"
        val offCfg = XrayJsonBuilder.build(ConfigParser.parse(off))
        assertTrue("allowInsecure must not be emitted by default", !offCfg.contains("allowInsecure"))
        assertTrue(
            "certificate verification must not be relaxed by default",
            !offCfg.contains("verifyPeerCertByName"),
        )
    }

    @Test
    fun buildsValidVlessCoreConfig() {
        val link = "vless://uuid-abc@host.example.com:443?encryption=none&security=reality&type=tcp&pbk=KEY&sid=99#N"
        val cfg = XrayJsonBuilder.build(ConfigParser.parse(link))
        val root = json.parseToJsonElement(cfg).jsonObject

        val inbound = (root["inbounds"] as JsonArray)[0].jsonObject
        assertEquals("socks", inbound["protocol"]!!.jsonPrimitive.content)
        assertEquals(LocalProxy.SOCKS_PORT, inbound["port"]!!.jsonPrimitive.content.toInt())

        val outbounds = root["outbounds"] as JsonArray
        val proxy = outbounds[0].jsonObject
        assertEquals("vless", proxy["protocol"]!!.jsonPrimitive.content)
        val user = proxy["settings"]!!.jsonObject["vnext"]!!.jsonArray[0].jsonObject["users"]!!
            .jsonArray[0].jsonObject
        assertEquals("uuid-abc", user["id"]!!.jsonPrimitive.content)

        val stream = proxy["streamSettings"]!!.jsonObject
        assertEquals("tcp", stream["network"]!!.jsonPrimitive.content)
        assertEquals("reality", stream["security"]!!.jsonPrimitive.content)
        assertEquals("KEY", stream["realitySettings"]!!.jsonObject["publicKey"]!!.jsonPrimitive.content)

        assertEquals("direct", outbounds[1].jsonObject["tag"]!!.jsonPrimitive.content)
        assertEquals("block", outbounds[2].jsonObject["tag"]!!.jsonPrimitive.content)
    }

    @Test
    fun buildsTrojanConfigWithoutFlow() {
        val link = "trojan://pw@t.example.com:443?security=tls#T"
        val cfg = XrayJsonBuilder.build(ConfigParser.parse(link))
        assertTrue(cfg.contains("\"protocol\": \"trojan\""))
        assertTrue("Trojan config must not contain a flow field", !cfg.contains("\"flow\""))
    }

    @Test
    fun neverEmitsAllowInsecure() {
        val tls = "vless://id@h.example.com:443?security=tls&type=ws&host=h.example.com&path=/x#N"
        val reality = "vless://id@h.example.com:443?security=reality&type=tcp&pbk=K&sid=1#N"
        listOf(tls, reality).forEach { link ->
            val cfg = XrayJsonBuilder.build(ConfigParser.parse(link))
            assertFalse("allowInsecure must never appear", cfg.contains("allowInsecure"))
        }
    }

    @Test
    fun websocketUsesTopLevelHostAndPath() {
        val link = "vless://id@h.example.com:443?security=tls&type=ws&host=cdn.example.com&path=%2Fray#N"
        val cfg = XrayJsonBuilder.build(ConfigParser.parse(link))
        val ws = json.parseToJsonElement(cfg).jsonObject["outbounds"]!!.jsonArray[0]
            .jsonObject["streamSettings"]!!.jsonObject["wsSettings"]!!.jsonObject
        assertEquals("cdn.example.com", ws["host"]!!.jsonPrimitive.content)
        assertEquals("/ray", ws["path"]!!.jsonPrimitive.content)
    }

    @Test
    fun removedTransportsAreRejectedClearly() {
        listOf("h2", "http", "quic").forEach { net ->
            val link = "vless://id@h.example.com:443?security=tls&type=$net#N"
            try {
                XrayJsonBuilder.build(ConfigParser.parse(link))
                throw AssertionError("expected $net to be rejected")
            } catch (e: UnsupportedTransportException) {
                assertEquals(net, e.transport)
            }
        }
    }

    @Test
    fun kcpOmitsRemovedHeaderAndSeed() {
        val link = "vless://id@h.example.com:443?type=kcp&headerType=srtp&seed=abc#N"
        val cfg = XrayJsonBuilder.build(ConfigParser.parse(link))
        val kcp = json.parseToJsonElement(cfg).jsonObject["outbounds"]!!.jsonArray[0]
            .jsonObject["streamSettings"]!!.jsonObject["kcpSettings"]!!.jsonObject
        assertNull(kcp["header"])
        assertNull(kcp["seed"])
    }

    @Test
    fun invalidFlowAndFingerprintAreDropped() {
        val link = "vless://id@h.example.com:443?security=tls&type=tcp&flow=xtls-rprx-direct&fp=bogusfp#N"
        val cfg = XrayJsonBuilder.build(ConfigParser.parse(link))
        assertFalse("legacy XTLS flow must be dropped", cfg.contains("xtls-rprx-direct"))
        assertFalse("unknown fingerprint must be dropped", cfg.contains("bogusfp"))
    }

    @Test
    fun visionFlowIsKept() {
        val link = "vless://id@h.example.com:443?security=reality&type=tcp&pbk=K&flow=xtls-rprx-vision#N"
        val cfg = XrayJsonBuilder.build(ConfigParser.parse(link))
        assertTrue(cfg.contains("xtls-rprx-vision"))
    }

    @Test
    fun statsArePresentSoSpeedsCanBeRead() {
        val link = "vless://id@h.example.com:443?security=tls&type=tcp#N"
        val root = json.parseToJsonElement(XrayJsonBuilder.build(ConfigParser.parse(link))).jsonObject
        assertNotNull(root["stats"])
        val system = root["policy"]!!.jsonObject["system"]!!.jsonObject
        assertTrue(system["statsOutboundUplink"]!!.jsonPrimitive.content.toBoolean())
        assertTrue(system["statsOutboundDownlink"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun normalizeRawJsonAddsSocksInbound() {
        val raw = """{"outbounds":[{"protocol":"freedom","tag":"direct"}]}"""
        val out = XrayJsonBuilder.normalizeRawJson(raw)
        val root = json.parseToJsonElement(out).jsonObject
        val inbounds = root["inbounds"] as JsonArray
        assertTrue(inbounds.any { it.jsonObject["protocol"]?.jsonPrimitive?.content == "socks" })
    }
}
