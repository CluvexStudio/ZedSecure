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

    @Test
    fun `Psiphon Chain mode configures psiphon-bind and passes binary path`() {
        val profile = AetherProfile(
            protocol = AetherProfile.PROTOCOL_MASQUE,
            psiphonMode = AetherProfile.CARRIER_CHAIN,
            psiphonRegion = "DE",
        )
        val args = AetherCoreBuilder.buildArgs(profile, socksPort = 11819, psiphonBin = "/tmp/psiphon")

        assertTrue("--psiphon" in args)
        assertTrue("--psiphon-bind" in args && args[args.indexOf("--psiphon-bind") + 1] == "127.0.0.1:11819")
        assertTrue("--bind" in args && args[args.indexOf("--bind") + 1] == "127.0.0.1:11821")
        assertTrue("--psiphon-bin" in args && args[args.indexOf("--psiphon-bin") + 1] == "/tmp/psiphon")
        assertTrue("--psiphon-region" in args && args[args.indexOf("--psiphon-region") + 1] == "DE")
    }

    @Test
    fun `Psiphon Only mode generates psiphon-only flag`() {
        val profile = AetherProfile(
            psiphonMode = AetherProfile.CARRIER_ONLY,
            psiphonTactics = "cdn",
            psiphonRegion = "US",
            psiphonCdnIps = "104.16.0.1",
        )
        val args = AetherCoreBuilder.buildArgs(profile, socksPort = 11819)

        assertTrue("--psiphon-only" in args)
        assertTrue("--psiphon-mode" in args && args[args.indexOf("--psiphon-mode") + 1] == "cdn")
        assertTrue("--psiphon-region" in args && args[args.indexOf("--psiphon-region") + 1] == "US")
        assertTrue("--psiphon-cdn-ips" in args && args[args.indexOf("--psiphon-cdn-ips") + 1] == "104.16.0.1")
    }

    @Test
    fun `Zero Trust access flags generated correctly`() {
        val profile = AetherProfile(
            teamName = "myteam.cloudflareaccess.com",
            accessClientId = "test-id",
            accessClientSecret = "test-secret",
            accessToken = "test-token",
            gateway = true,
        )
        val args = AetherCoreBuilder.buildArgs(profile, socksPort = 11819)

        assertTrue("--team" in args && args[args.indexOf("--team") + 1] == "myteam.cloudflareaccess.com")
        assertTrue("--access-id" in args && args[args.indexOf("--access-id") + 1] == "test-id")
        assertTrue("--access-secret" in args && args[args.indexOf("--access-secret") + 1] == "test-secret")
        assertTrue("--access-token" in args && args[args.indexOf("--access-token") + 1] == "test-token")
        assertTrue("--gateway" in args)
    }

    @Test
    fun `custom command overrides auto-generated flags`() {
        val profile = AetherProfile(
            customCommand = "aether --bind 127.0.0.1:1080 --protocol wg --peer 1.2.3.4:2408",
        )
        val args = AetherCoreBuilder.buildArgs(profile, socksPort = 11819)

        assertEquals(listOf("--bind", "127.0.0.1:1080", "--protocol", "wg", "--peer", "1.2.3.4:2408"), args)
    }

    @Test
    fun `AetherLink roundtrip with Zero Trust, Psiphon Only and customCommand`() {
        val original = AetherProfile(
            protocol = AetherProfile.PROTOCOL_MIM,
            transport = AetherProfile.TRANSPORT_H2,
            scanMode = AetherProfile.SCAN_VERIFIED,
            obfuscation = AetherProfile.NOISE_GFW,
            wiwOuter = "162.159.192.1:2408",
            wiwInner = "188.114.96.1:2408",
            ech = true,
            echDns = "https://1.1.1.1/dns-query",
            echDomain = "cloudflare-ech.com",
            fragment = true,
            fragmentSize = "20-40",
            fragmentDelay = "3-12",
            dns = "8.8.8.8,1.1.1.1",
            exitLoc = "DE,SE",
            psiphonMode = AetherProfile.CARRIER_ONLY,
            psiphonTactics = "cdn",
            psiphonRegion = "DE",
            teamName = "org.cloudflareaccess.com",
            accessClientId = "cid",
            accessClientSecret = "csec",
            accessToken = "tok",
            gateway = true,
            customCommand = "--custom-flag 123",
        )
        val link = AetherLink.build("FullConfig", original)
        val (name, parsed) = AetherLink.parse(link)!!

        assertEquals("FullConfig", name)
        assertEquals(AetherProfile.PROTOCOL_MIM, parsed.protocol)
        assertEquals(AetherProfile.TRANSPORT_H2, parsed.transport)
        assertEquals(AetherProfile.SCAN_VERIFIED, parsed.scanMode)
        assertEquals(AetherProfile.NOISE_GFW, parsed.obfuscation)
        assertEquals("162.159.192.1:2408", parsed.wiwOuter)
        assertEquals("188.114.96.1:2408", parsed.wiwInner)
        assertTrue(parsed.ech)
        assertEquals("https://1.1.1.1/dns-query", parsed.echDns)
        assertEquals("cloudflare-ech.com", parsed.echDomain)
        assertTrue(parsed.fragment)
        assertEquals("20-40", parsed.fragmentSize)
        assertEquals("3-12", parsed.fragmentDelay)
        assertEquals("8.8.8.8,1.1.1.1", parsed.dns)
        assertEquals("DE,SE", parsed.exitLoc)
        assertEquals(AetherProfile.CARRIER_ONLY, parsed.psiphonMode)
        assertEquals("cdn", parsed.psiphonTactics)
        assertEquals("DE", parsed.psiphonRegion)
        assertEquals("org.cloudflareaccess.com", parsed.teamName)
        assertEquals("cid", parsed.accessClientId)
        assertEquals("csec", parsed.accessClientSecret)
        assertEquals("tok", parsed.accessToken)
        assertTrue(parsed.gateway)
        assertEquals("--custom-flag 123", parsed.customCommand)
    }

    @Test
    fun `aetherTarget extracts custom server or falls back to null for auto-scan`() {
        val customProfile = VpnProfile.fromAether(
            settings = AetherProfile(server = "162.159.192.1:2408"),
            id = "p1",
            addedAt = 0L,
            name = "Aether Custom",
        )
        val (h1, p1) = dev.cluvex.zedsecure.data.net.PingService.aetherTarget(customProfile)
        assertEquals("162.159.192.1", h1)
        assertEquals(2408, p1)

        val autoProfile = VpnProfile.fromAether(
            settings = AetherProfile(server = ""),
            id = "p2",
            addedAt = 0L,
            name = "Aether Auto",
        )
        val (h2, p2) = dev.cluvex.zedsecure.data.net.PingService.aetherTarget(autoProfile)
        assertEquals(null, h2)
        assertEquals(null, p2)

        val twoHopProfile = VpnProfile.fromAether(
            settings = AetherProfile(
                protocol = AetherProfile.PROTOCOL_GOOL,
                wiwOuter = "162.159.193.1:2408",
                wiwInner = "188.114.96.1:2408",
            ),
            id = "p3",
            addedAt = 0L,
            name = "Aether TwoHop",
        )
        val (h3, p3) = dev.cluvex.zedsecure.data.net.PingService.aetherTarget(twoHopProfile)
        assertEquals("162.159.193.1", h3)
        assertEquals(2408, p3)
    }

    @Test
    fun `aetherDelay measures real latency for auto-scan and custom endpoints`() = kotlinx.coroutines.runBlocking {
        val autoProfile = VpnProfile.fromAether(
            settings = AetherProfile(server = ""),
            id = "auto-test",
            addedAt = 0L,
            name = "Auto Aether",
        )
        val delay = dev.cluvex.zedsecure.data.net.PingService.aetherDelay(autoProfile)
        assertTrue(delay > 0, "Aether auto-scan delay should be positive, got $delay")

        val realProfileDelay = dev.cluvex.zedsecure.data.net.PingService.realDelay(autoProfile)
        assertTrue(realProfileDelay > 0, "PingService.realDelay should support Aether, got $realProfileDelay")
    }
}
