package dev.cluvex.zedsecure.domain.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmneziaWgTest {
    private val conf = """
        [Interface]
        PrivateKey = aPrivateKeyValue000000000000000000000000000=
        Address = 10.8.0.2/32
        MTU = 1280
        Jc = 4
        Jmin = 40
        Jmax = 70
        S1 = 50
        S2 = 100
        H1 = 1234567
        H2 = 7654321
        H3 = 1122334
        H4 = 4433221
        I1 = <b 0xf1f2>
        DisableCookies = true

        [Peer]
        PublicKey = aPeerPublicKeyValue00000000000000000000000=
        PresharedKey = aPresharedKeyValue0000000000000000000000000=
        Endpoint = awg.example.com:51820
        AllowedIPs = 0.0.0.0/0
    """.trimIndent()

    @Test
    fun `conf file parses as AmneziaWG with every obfuscation key`() {
        assertTrue(ConfigParser.looksLikeWireguardConf(conf))
        val c = ConfigParser.parseWireguardConf(conf)
        assertEquals(Protocol.AMNEZIAWG, c.protocol)
        assertEquals("awg.example.com", c.address)
        assertEquals(51820, c.port)
        assertEquals("10.8.0.2/32", c.localAddresses.single())
        assertEquals(1280, c.wireguardMtu)
        assertEquals("aPeerPublicKeyValue00000000000000000000000=", c.peerPublicKey)
        assertEquals("aPresharedKeyValue0000000000000000000000000=", c.preSharedKey)
        assertEquals("4", c.awg["jc"])
        assertEquals("40", c.awg["jmin"])
        assertEquals("70", c.awg["jmax"])
        assertEquals("50", c.awg["s1"])
        assertEquals("100", c.awg["s2"])
        assertEquals("1234567", c.awg["h1"])
        assertEquals("4433221", c.awg["h4"])
        assertEquals("<b 0xf1f2>", c.awg["i1"])
        assertEquals("true", c.awg["disable_cookies"])
    }

    @Test
    fun `share link round-trips every field twice`() {
        val first = ConfigParser.parseWireguardConf(conf)
        val link1 = ShareLink.build(first)
        assertTrue("AmneziaWG must use its own scheme", link1.startsWith("awg://"))

        val second = ConfigParser.parse(link1)
        val link2 = ShareLink.build(second)

        assertEquals("re-saving must not change the link", link1, link2)
        assertEquals(first.protocol, second.protocol)
        assertEquals(first.secretKey, second.secretKey)
        assertEquals(first.peerPublicKey, second.peerPublicKey)
        assertEquals(first.preSharedKey, second.preSharedKey)
        assertEquals(first.localAddresses, second.localAddresses)
        assertEquals(first.wireguardMtu, second.wireguardMtu)
        assertEquals(first.awg.values, second.awg.values)
    }

    @Test
    fun `plain wireguard stays plain and emits no awg block`() {
        val plain = ConfigParser.parse(
            "wireguard://key@wg.example.com:51820?publickey=pub&address=10.0.0.2/32",
        )
        assertEquals(Protocol.WIREGUARD, plain.protocol)
        assertTrue(plain.awg.isEmpty)
        val json = XrayJsonBuilder.build(plain)
        assertFalse("plain WireGuard must not gain an awg block", json.contains("\"awg\""))
    }

    @Test
    fun `core config carries only the keys that are set`() {
        val c = ConfigParser.parseWireguardConf(conf)
        val json = XrayJsonBuilder.build(c)
        assertTrue(json.contains("\"awg\""))

        assertTrue(json.contains("\"Jc\""))
        assertTrue(json.contains("\"H4\""))
        assertTrue(json.contains("\"DisableCookies\""))
        assertFalse("the canonical spelling binds to no field in the core", json.contains("disable_cookies"))

        assertFalse(json.contains("\"S3\""))
        assertFalse(json.contains("\"S4\""))
        assertFalse(json.contains("\"I2\""))

        assertTrue(json.contains("\"protocol\": \"wireguard\"") || json.contains("\"protocol\":\"wireguard\""))
    }

    @Test
    fun `a plain WireGuard peer is unchanged by the AmneziaWG work`() {
        val plain = ConfigParser.parse(
            "wireguard://cHJpdmF0ZUtleQ==@wg.example.com:51820" +
                "?publickey=cHViS2V5&address=10.0.0.2/32&mtu=1420#Plain",
        )
        assertEquals(Protocol.WIREGUARD, plain.protocol)
        assertTrue(plain.awg.isEmpty)

        val json = XrayJsonBuilder.build(plain)
        assertFalse("no awg block", json.contains("\"awg\""))

        for (k in AwgConfig.KEYS) {
            assertFalse("plain WireGuard must not emit $k", json.contains("\"$k\""))
            assertFalse("plain WireGuard must not emit $k", json.contains("\"${AwgConfig.CONF_NAME[k]}\""))
        }
        assertTrue(json.contains("\"protocol\": \"wireguard\""))
        assertTrue(json.contains("\"endpoint\": \"wg.example.com:51820\""))
        assertTrue(json.contains("\"mtu\": 1420"))

        assertTrue(ShareLink.build(plain).startsWith("wireguard://"))
    }

    @Test
    fun `an AmneziaWG peer carries every parameter it was given`() {
        val c = ConfigParser.parseWireguardConf(conf)
        val json = XrayJsonBuilder.build(c)
        assertTrue("the config under test must actually set keys", c.awg.entries.isNotEmpty())
        for ((k, v) in c.awg.entries) {
            val emitted = AwgConfig.CONF_NAME.getValue(k)
            assertTrue("$k must be emitted as $emitted", json.contains("\"$emitted\""))
            assertTrue("$k must keep the value $v", json.contains("\"$v\""))
        }

        val unset = AwgConfig.KEYS - c.awg.entries.map { it.first }.toSet()
        for (k in unset) {
            assertFalse("$k was never set and must not appear", json.contains("\"${AwgConfig.CONF_NAME[k]}\""))
        }
    }

    @Test
    fun `a wireguard link is promoted only when it carries obfuscation keys`() {
        val plain = ConfigParser.parse("wireguard://k@h.example:51820?publickey=p&address=10.0.0.2/32")
        assertEquals(Protocol.WIREGUARD, plain.protocol)

        val obfuscated =
            ConfigParser.parse("wireguard://k@h.example:51820?publickey=p&address=10.0.0.2/32&jc=4&h1=123")
        assertEquals(Protocol.AMNEZIAWG, obfuscated.protocol)
        assertEquals("4", obfuscated.awg["jc"])
        assertEquals("123", obfuscated.awg["h1"])
    }

    @Test
    fun `the two protocols are distinguishable in the list`() {
        val plain = ConfigParser.parse("wireguard://k@h.example:51820?publickey=p&address=10.0.0.2/32")
        val awg = ConfigParser.parseWireguardConf(conf)
        assertEquals("WireGuard", plain.transportLabel)
        assertEquals("AmneziaWG · obfs", awg.transportLabel)

        assertEquals(plain.protocol.id, awg.protocol.id)
    }

    @Test
    fun `switching protocol back to WireGuard clears the obfuscation`() {
        val awg = ConfigParser.parseWireguardConf(conf)
        val switched = awg.copy(protocol = Protocol.WIREGUARD, awg = AwgConfig())
        assertTrue(switched.awg.isEmpty)
        val link = ShareLink.build(switched)
        assertTrue(link.startsWith("wireguard://"))
        for (k in AwgConfig.KEYS) assertFalse(link.contains("$k="))
        assertFalse(XrayJsonBuilder.build(switched).contains("\"awg\""))
    }

    @Test
    fun `alternate spellings all map to the canonical key`() {
        assertEquals("jc", AwgConfig.canonicalKey("Jc"))
        assertEquals("h1", AwgConfig.canonicalKey("H1"))
        assertEquals("header_protection_key", AwgConfig.canonicalKey("HeaderProtectionKey"))
        assertEquals("header_protection_key", AwgConfig.canonicalKey("header-protection-key"))
        assertEquals("max_handshake_attempts", AwgConfig.canonicalKey("MaxHandshakeAttempts"))
        assertEquals(null, AwgConfig.canonicalKey("NotAThing"))
    }
}
