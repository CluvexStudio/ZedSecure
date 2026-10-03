package dev.cluvex.zedsecure.desktop.core

import dev.cluvex.zedsecure.domain.config.AetherCoreBuilder
import dev.cluvex.zedsecure.domain.config.AetherLink
import dev.cluvex.zedsecure.domain.config.AetherProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopAetherArgsTest {

    @Test
    fun `default MASQUE profile generates correct CLI flags`() {
        val profile = AetherProfile(
            protocol = AetherProfile.PROTOCOL_MASQUE,
            transport = AetherProfile.TRANSPORT_H3,
            scanMode = AetherProfile.SCAN_BALANCED,
            obfuscation = AetherProfile.NOISE_FIREWALL,
        )
        val args = AetherCoreBuilder.buildArgs(profile, socksPort = 11819)

        assertTrue("--bind" in args)
        assertTrue("127.0.0.1:11819" in args)
        assertTrue("--protocol" in args && args[args.indexOf("--protocol") + 1] == "masque")
        assertTrue("--scan" in args && args[args.indexOf("--scan") + 1] == "balanced")
        assertTrue("--noize" in args && args[args.indexOf("--noize") + 1] == "firewall")
        assertTrue("--h3" in args)
        assertTrue("--quick-reconnect" in args)
    }

    @Test
    fun `H2 MASQUE with ECH and fragmentation generates matching flags`() {
        val profile = AetherProfile(
            protocol = AetherProfile.PROTOCOL_MASQUE,
            transport = AetherProfile.TRANSPORT_H2,
            ech = true,
            echDns = "1.1.1.1",
            echDomain = "cloudflare.com",
            fragment = true,
            fragmentSize = "10-20",
            fragmentDelay = "5-15",
        )
        val args = AetherCoreBuilder.buildArgs(profile, socksPort = 11819)

        assertTrue("--h2" in args)
        assertTrue("--ech" in args)
        assertTrue("--ech-dns" in args && args[args.indexOf("--ech-dns") + 1] == "1.1.1.1")
        assertTrue("--ech-domain" in args && args[args.indexOf("--ech-domain") + 1] == "cloudflare.com")
        assertTrue("--fragment" in args)
        assertTrue("--fragment-size" in args && args[args.indexOf("--fragment-size") + 1] == "10-20")
        assertTrue("--fragment-delay" in args && args[args.indexOf("--fragment-delay") + 1] == "5-15")
    }

    @Test
    fun `WARP-in-WARP nested mode generates two-hop flags`() {
        val profile = AetherProfile(
            protocol = AetherProfile.PROTOCOL_GOOL,
            wiwOuter = "162.159.192.1:2408",
            wiwInner = "188.114.96.1:2408",
        )
        val args = AetherCoreBuilder.buildArgs(profile, socksPort = 11819)

        assertTrue("--protocol" in args && args[args.indexOf("--protocol") + 1] == "gool")
        assertTrue("--wiw-outer" in args && args[args.indexOf("--wiw-outer") + 1] == "162.159.192.1:2408")
        assertTrue("--wiw-inner" in args && args[args.indexOf("--wiw-inner") + 1] == "188.114.96.1:2408")
    }

    @Test
    fun `AetherLink roundtrip serialization matches`() {
        val original = AetherProfile(
            protocol = AetherProfile.PROTOCOL_MASQUE,
            transport = AetherProfile.TRANSPORT_H3,
            scanMode = AetherProfile.SCAN_TURBO,
            obfuscation = AetherProfile.NOISE_BALANCED,
            server = "162.159.192.1:2408",
            torMode = AetherProfile.CARRIER_CHAIN,
        )
        val link = AetherLink.build("TestProfile", original)
        assertTrue(link.startsWith("aether://"))

        val (name, parsed) = AetherLink.parse(link)!!
        assertEquals("TestProfile", name)
        assertEquals(AetherProfile.PROTOCOL_MASQUE, parsed.protocol)
        assertEquals(AetherProfile.TRANSPORT_H3, parsed.transport)
        assertEquals(AetherProfile.SCAN_TURBO, parsed.scanMode)
        assertEquals(AetherProfile.NOISE_BALANCED, parsed.obfuscation)
        assertEquals("162.159.192.1:2408", parsed.server)
        assertEquals(AetherProfile.CARRIER_CHAIN, parsed.torMode)
    }

    @Test
    fun `aetherBypass includes Cloudflare CIDRs and custom profile endpoints`() {
        val profile = AetherProfile(
            server = "162.159.192.1:2408",
            wiwOuter = "162.159.193.1:2408",
            wiwInner = "188.114.96.1:2408",
            psiphonCdnIps = "104.16.1.1, 172.64.1.1",
        )
        val bypass = dev.cluvex.zedsecure.desktop.platform.DesktopVpn.aetherBypass(profile)

        assertTrue("162.159.192.0/24" in bypass)
        assertTrue("162.159.193.0/24" in bypass)
        assertTrue("162.159.195.0/24" in bypass)
        assertTrue("188.114.96.0/22" in bypass)
        assertTrue("104.16.0.0/12" in bypass)
        assertTrue("172.64.0.0/13" in bypass)
        assertTrue("162.159.192.1" in bypass)
        assertTrue("162.159.193.1" in bypass)
        assertTrue("188.114.96.1" in bypass)
        assertTrue("104.16.1.1" in bypass)
        assertTrue("172.64.1.1" in bypass)
    }
}
