package dev.cluvex.zedsecure.domain.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater

class AmneziaLinkTest {
    private val awgConf = """
        [Interface]
        PrivateKey = aGVsbG8gd29ybGQgdGhpcyBpcyBhIGtleSBmb3IgdGVzdA=
        Address = 10.8.1.2/32
        DNS = 1.1.1.1
        Jc = 4
        Jmin = 40
        Jmax = 70
        S1 = 50
        S2 = 100
        H1 = 1234567
        H2 = 2345678
        H3 = 3456789
        H4 = 4567890

        [Peer]
        PublicKey = cHVibGljIGtleSB2YWx1ZSBmb3IgdGhlIHRlc3QgY2FzZXM9
        AllowedIPs = 0.0.0.0/0
        Endpoint = vpn.example.com:51820
    """.trimIndent()

    private val plainConf = """
        [Interface]
        PrivateKey = aGVsbG8gd29ybGQgdGhpcyBpcyBhIGtleSBmb3IgdGVzdA=
        Address = 10.8.1.2/32

        [Peer]
        PublicKey = cHVibGljIGtleSB2YWx1ZSBmb3IgdGhlIHRlc3QgY2FzZXM9
        AllowedIPs = 0.0.0.0/0
        Endpoint = plain.example.com:51820
    """.trimIndent()

    private fun qCompress(text: String): ByteArray {
        val raw = text.toByteArray()
        val deflater = Deflater()
        deflater.setInput(raw)
        deflater.finish()
        val body = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!deflater.finished()) body.write(buf, 0, deflater.deflate(buf))
        deflater.end()
        val out = ByteArrayOutputStream()
        out.write((raw.size ushr 24) and 0xFF)
        out.write((raw.size ushr 16) and 0xFF)
        out.write((raw.size ushr 8) and 0xFF)
        out.write(raw.size and 0xFF)
        out.write(body.toByteArray())
        return out.toByteArray()
    }

    private fun vpnLink(text: String, compress: Boolean = true): String {
        val bytes = if (compress) qCompress(text) else text.toByteArray()
        return "vpn://" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    @Test
    fun `recognises the scheme`() {
        assertTrue(AmneziaLink.isAmneziaLink("vpn://abc"))
        assertTrue(AmneziaLink.isAmneziaLink("  VPN://abc  "))
        assertEquals(false, AmneziaLink.isAmneziaLink("vless://x@y:443"))
    }

    @Test
    fun `unwraps a compressed AmneziaWG conf`() {
        val conf = AmneziaLink.extractWireguardConf(vpnLink(awgConf))
        assertNotNull("vpn:// must decode to the conf inside", conf)
        assertTrue(conf!!.contains("[Interface]"))
        assertTrue(conf.contains("Jc = 4"))
    }

    @Test
    fun `an uncompressed payload also works`() {
        val conf = AmneziaLink.extractWireguardConf(vpnLink(awgConf, compress = false))
        assertNotNull(conf)
        assertTrue(conf!!.contains("Jmax = 70"))
    }

    @Test
    fun `obfuscation keys select AmneziaWG, and their absence does not`() {
        val awg = ConfigParser.parseWireguardConf(AmneziaLink.extractWireguardConf(vpnLink(awgConf))!!)
        assertEquals(Protocol.AMNEZIAWG, awg.protocol)
        assertEquals("vpn.example.com", awg.address)

        val plain = ConfigParser.parseWireguardConf(AmneziaLink.extractWireguardConf(vpnLink(plainConf))!!)
        assertEquals(Protocol.WIREGUARD, plain.protocol)
    }

    @Test
    fun `finds the conf nested in an Amnezia server JSON`() {
        val inner = """{"config":${quote(awgConf)},"clientId":"abc"}"""
        val server = """{"hostName":"1.2.3.4","containers":[{"container":"amnezia-awg","awg":{"last_config":${quote(inner)}}}]}"""
        val conf = AmneziaLink.extractWireguardConf(vpnLink(server))
        assertNotNull("must dig the conf out of the container JSON", conf)
        assertTrue(conf!!.contains("Jc = 4"))
    }

    @Test
    fun `an unrelated payload yields null rather than a bogus profile`() {
        assertNull(AmneziaLink.extractWireguardConf(vpnLink("""{"hostName":"1.2.3.4","containers":[]}""")))
        assertNull(AmneziaLink.extractWireguardConf("vpn://"))
    }

    private fun server(body: String) = vpnLink(body)

    private fun items(text: String): List<AmneziaLink.Item> {
        val payload = AmneziaLink.parse(text)
        assertTrue("expected configs, got $payload", payload is AmneziaLink.Payload.Configs)
        return (payload as AmneziaLink.Payload.Configs).items
    }

    private val serverIssuedAwg = """
        {"client_ip":"10.8.1.2","client_priv_key":"PRIV=","server_pub_key":"PUB=","psk_key":"PSK=",
         "port":51820,"mtu":"1280","allowed_ips":["0.0.0.0/0","::/0"],"persistent_keep_alive":"25",
         "Jc":"4","Jmin":"40","Jmax":"70","S1":"50","S2":"100","S3":"0","S4":"0",
         "H1":"1234567","H2":"2345678","H3":"3456789","H4":"4567890","I1":"<b 0xf1a2>",
         "RekeyAfterTime":"120","DisableCookies":"true","clientId":"abc"}
    """.trimIndent()

    @Test
    fun `a server-issued AmneziaWG container carries fields, not a conf, and still imports`() {
        val json = """{"containers":[{"container":"amnezia-awg","awg":{"last_config":${quote(serverIssuedAwg)},
            "port":"51820","transport_proto":"udp"}}],"defaultContainer":"amnezia-awg",
            "description":"My Server","hostName":"vpn.example.com","dns1":"1.1.1.1","dns2":"8.8.8.8"}"""

        val item = items(server(json)).single()
        assertEquals("My Server", item.name)

        val parsed = ConfigParser.parseWireguardConf(item.text)
        assertEquals(Protocol.AMNEZIAWG, parsed.protocol)
        assertEquals("vpn.example.com", parsed.address)
        assertEquals(51820, parsed.port)
        assertEquals("PRIV=", parsed.secretKey)
        assertEquals("PUB=", parsed.peerPublicKey)
        assertEquals("PSK=", parsed.preSharedKey)
        assertEquals(1280, parsed.wireguardMtu!!)
        assertEquals(listOf("10.8.1.2/32"), parsed.localAddresses)
    }

    @Test
    fun `every AmneziaWG parameter survives the rebuild, including the 1_5 ones`() {
        val json = """{"containers":[{"container":"amnezia-awg","awg":{"last_config":${quote(serverIssuedAwg)}}}],
            "defaultContainer":"amnezia-awg","hostName":"vpn.example.com"}"""

        val awg = ConfigParser.parseWireguardConf(items(server(json)).single().text).awg
        assertEquals("4", awg["jc"])
        assertEquals("40", awg["jmin"])
        assertEquals("100", awg["s2"])
        assertEquals("4567890", awg["h4"])

        assertEquals("<b 0xf1a2>", awg["i1"])
        assertEquals("120", awg["rekey_after_time"])
        assertEquals("true", awg["disable_cookies"])
    }

    @Test
    fun `the server's DNS and keepalive reach the conf`() {
        val json = """{"containers":[{"container":"amnezia-awg","awg":{"last_config":${quote(serverIssuedAwg)}}}],
            "defaultContainer":"amnezia-awg","hostName":"vpn.example.com","dns1":"1.1.1.1","dns2":"8.8.8.8"}"""

        val conf = items(server(json)).single().text
        assertTrue(conf.contains("DNS = 1.1.1.1, 8.8.8.8"))
        assertTrue(conf.contains("PersistentKeepalive = 25"))
        assertTrue(conf.contains("AllowedIPs = 0.0.0.0/0, ::/0"))
    }

    @Test
    fun `an OpenVPN container yields its profile text`() {
        val ovpn = "client\ndev tun\nproto udp\nremote 1.2.3.4 1194\n"
        val inner = """{"config":${quote(ovpn)}}"""
        val json = """{"containers":[{"container":"amnezia-openvpn","openvpn":{"last_config":${quote(inner)},
            "isThirdPartyConfig":true}}],"defaultContainer":"amnezia-openvpn","hostName":"1.2.3.4"}"""

        assertEquals(ovpn, items(server(json)).single().text)
    }

    @Test
    fun `an Xray container works in both of the shapes the client writes`() {
        val xray = """{"inbounds":[{"port":10808}],"outbounds":[{"protocol":"vless"}]}"""

        val direct = """{"containers":[{"container":"amnezia-xray","xray":{"last_config":${quote(xray)}}}],
            "defaultContainer":"amnezia-xray","hostName":"1.2.3.4"}"""
        assertTrue(items(server(direct)).single().text.contains("\"outbounds\""))

        val wrapped = """{"config":${quote(xray)},"local_port":"10808"}"""
        val nested = """{"containers":[{"container":"amnezia-xray","xray":{"last_config":${quote(wrapped)}}}],
            "defaultContainer":"amnezia-xray","hostName":"1.2.3.4"}"""
        assertEquals(xray, items(server(nested)).single().text)
    }

    @Test
    fun `a server offering several protocols imports all of them, default first`() {
        val xray = """{"inbounds":[],"outbounds":[{"protocol":"vless"}]}"""
        val json = """{"containers":[
            {"container":"amnezia-xray","xray":{"last_config":${quote(xray)}}},
            {"container":"amnezia-awg","awg":{"last_config":${quote(serverIssuedAwg)}}}],
            "defaultContainer":"amnezia-awg","description":"Multi","hostName":"vpn.example.com"}"""

        val found = items(server(json))
        assertEquals(2, found.size)

        assertTrue(found[0].text.startsWith("[Interface]"))
        assertEquals("Multi · AmneziaWG", found[0].name)
        assertEquals("Multi · Xray", found[1].name)
    }

    @Test
    fun `an IPsec container becomes an IKEv2 profile`() {
        val inner = """{"hostName":"ike.example.com","userName":"alice","password":"hunter2"}"""
        val json = """{"containers":[{"container":"amnezia-ipsec","ikev2":{"last_config":${quote(inner)}}}],
            "defaultContainer":"amnezia-ipsec","description":"IKE","hostName":"ike.example.com"}"""

        val link = items(server(json)).single().text
        val source = ZedLink.parse(link)?.source
        assertTrue("expected an IKEv2 source, got $source", source is ProfileSource.Ikev2)
        val settings = (source as ProfileSource.Ikev2).settings
        assertEquals("ike.example.com", settings.server)
        assertEquals("alice", settings.username)
        assertEquals("hunter2", settings.plainPassword())
    }

    @Test
    fun `a backup is named as one instead of failing as an unsupported config`() {
        val backup = """{"Servers/serversList":"[]","Conf/installedAppVersion":"4.8.0"}"""
        assertEquals(AmneziaLink.Payload.Backup, AmneziaLink.parse(server(backup)))
    }

    @Test
    fun `an account key is not a config, and says so`() {
        val free = """{"api_key":"abcdef","config_version":2}"""
        assertEquals(AmneziaLink.Payload.Subscription, AmneziaLink.parse(server(free)))
    }

    @Test
    fun `a Cloak container is refused rather than half-imported`() {
        val inner = """{"config":"client\ndev tun\nremote 127.0.0.1 1194\n"}"""
        val json = """{"containers":[{"container":"amnezia-openvpn-cloak","openvpn":{"last_config":${quote(inner)}},
            "cloak":{"last_config":"{}"}}],"defaultContainer":"amnezia-openvpn-cloak","hostName":"1.2.3.4"}"""

        val payload = AmneziaLink.parse(server(json))
        assertTrue("expected Unusable, got $payload", payload is AmneziaLink.Payload.Unusable)
        assertTrue((payload as AmneziaLink.Payload.Unusable).reason.contains("cloak"))
    }

    @Test
    fun `the server JSON works pasted in the clear, and as a bare QR blob`() {
        val json = """{"containers":[{"container":"amnezia-awg","awg":{"last_config":${quote(serverIssuedAwg)}}}],
            "defaultContainer":"amnezia-awg","hostName":"vpn.example.com"}"""

        assertTrue(items(json).single().text.startsWith("[Interface]"))

        val blob = Base64.getUrlEncoder().withoutPadding().encodeToString(qCompress(json))
        assertTrue(AmneziaLink.isAmneziaBlob(blob))
        assertTrue(items(blob).single().text.startsWith("[Interface]"))
    }

    @Test
    fun `an ordinary base64 subscription body is left to the path that understands it`() {
        val body = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("vless://id@host:443?type=tcp#a".toByteArray())
        assertEquals(false, AmneziaLink.isAmneziaBlob(body))
    }

    private fun quote(s: String): String = buildString {
        append('"')
        s.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(c)
            }
        }
        append('"')
    }
}
