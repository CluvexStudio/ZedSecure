package dev.cluvex.zedsecure.domain.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WireguardDnsTest {
    private val conf = """
        [Interface]
        PrivateKey = AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=
        Address = 10.13.13.2/32
        DNS = 10.13.13.1, 1.1.1.1
        MTU = 1280

        [Peer]
        PublicKey = BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=
        AllowedIPs = 0.0.0.0/0, ::/0
        Endpoint = 192.0.2.10:51820
    """.trimIndent()

    @Test
    fun `the conf's resolvers are parsed`() {
        assertEquals(listOf("10.13.13.1", "1.1.1.1"), ConfigParser.parseWireguardConf(conf).dnsServers)
    }

    @Test
    fun `and survive the share link the importer converts the conf into`() {
        val link = ShareLink.build(ConfigParser.parseWireguardConf(conf))

        assertEquals(listOf("10.13.13.1", "1.1.1.1"), ConfigParser.parse(link).dnsServers)
    }

    @Test
    fun `and reach the core, ahead of the app's own resolver`() {
        val json = XrayJsonBuilder.build(ConfigParser.parseWireguardConf(conf))

        assertTrue("the config's own resolver must be in the DNS servers", json.contains("\"10.13.13.1\""))

        val own = json.indexOf("\"10.13.13.1\"")
        val global = json.indexOf("dns-query")
        assertTrue("the config's resolver must come first (own=$own global=$global)", own in 1..<global)
    }

    @Test
    fun `a config that names no resolver is left exactly as it was`() {
        val bare = conf.lines().filterNot { it.startsWith("DNS") }.joinToString("\n")
        val parsed = ConfigParser.parseWireguardConf(bare)

        assertTrue(parsed.dnsServers.isEmpty())
        assertTrue(XrayJsonBuilder.build(parsed).contains("dns-query"))
    }
}
