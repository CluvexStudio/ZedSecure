package dev.cluvex.zedsecure.domain.config

import dev.cluvex.zedsecure.domain.model.AppSettings
import dev.cluvex.zedsecure.domain.model.OutboundDomainResolve
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanShareAndDnsConfigTest {
    private val tlsWithSni = ConfigParser.parse(
        "vless://11111111-1111-1111-1111-111111111111@srv.example:443" +
            "?encryption=none&security=tls&sni=cdn.example&type=tcp#A",
    )
    private val tlsWithoutSni = ConfigParser.parse(
        "vless://11111111-1111-1111-1111-111111111111@srv.example:443?encryption=none&security=tls&type=tcp#B",
    )
    private val wsNoHost = ConfigParser.parse(
        "vless://11111111-1111-1111-1111-111111111111@srv.example:80?encryption=none&security=none&type=ws&path=%2F#C",
    )

    private val lan = XrayJsonBuilder.LanShare(port = 10880, username = "zed", password = "s3cretPassw0rd", udp = true)

    private fun root(json: String) = Json.parseToJsonElement(json).jsonObject
    private fun inbounds(json: String) = root(json)["inbounds"]!!.jsonArray.map { it.jsonObject }
    private fun outbounds(json: String) = root(json)["outbounds"]!!.jsonArray.map { it.jsonObject }
    private fun rules(json: String) = root(json)["routing"]!!.jsonObject["rules"]!!.jsonArray.map { it.jsonObject }
    private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.content

    private fun build(options: XrayJsonBuilder.BuildOptions, server: ServerConfig = tlsWithSni) =
        XrayJsonBuilder.build(server, options = options)

    private fun assertNoOpenLanListener(json: String) {
        inbounds(json).filter { it.str("listen") == "0.0.0.0" }.forEach { ib ->
            val settings = ib["settings"]!!.jsonObject
            val accounts = settings["accounts"]?.jsonArray.orEmpty()
            assertTrue("0.0.0.0 inbound ${ib.str("tag")} has no accounts: $ib", accounts.isNotEmpty())
            if (ib.str("protocol") == "socks") assertEquals("password", settings.str("auth"))
        }
    }

    @Test
    fun `without sharing every listener is loopback`() {
        val json = build(XrayJsonBuilder.BuildOptions(appendHttpProxy = true))
        assertTrue(inbounds(json).all { it.str("listen") == "127.0.0.1" })
    }

    @Test
    fun `sharing adds an authenticated LAN inbound and leaves socks-in untouched`() {
        val json = build(XrayJsonBuilder.BuildOptions(lan = lan))
        val socksIn = inbounds(json).first { it.str("tag") == "socks-in" }
        assertEquals("127.0.0.1", socksIn.str("listen"))
        assertEquals(10808, socksIn.str("port")!!.toInt())
        assertEquals("noauth", socksIn["settings"]!!.jsonObject.str("auth"))
        assertEquals("true", socksIn["settings"]!!.jsonObject.str("udp"))

        val lanIn = inbounds(json).first { it.str("tag") == "socks-lan" }
        assertEquals("0.0.0.0", lanIn.str("listen"))
        assertEquals(10880, lanIn.str("port")!!.toInt())
        val settings = lanIn["settings"]!!.jsonObject
        assertEquals("password", settings.str("auth"))
        val account = settings["accounts"]!!.jsonArray.single().jsonObject
        assertEquals("zed", account.str("user"))
        assertEquals("s3cretPassw0rd", account.str("pass"))

        assertNull(settings["ip"])
        assertNoOpenLanListener(json)
    }

    @Test
    fun `incomplete credentials share nothing rather than an open proxy`() {
        listOf(lan.copy(password = ""), lan.copy(username = "  "), lan.copy(port = 10808)).forEach { bad ->
            val json = build(XrayJsonBuilder.BuildOptions(lan = bad, appendHttpProxy = true))
            assertTrue("no 0.0.0.0 listener for $bad", inbounds(json).none { it.str("listen") == "0.0.0.0" })
        }
    }

    @Test
    fun `http sharing sits on the next port with the same accounts`() {
        val json = build(XrayJsonBuilder.BuildOptions(lan = lan, appendHttpProxy = true))
        assertEquals("127.0.0.1", inbounds(json).first { it.str("tag") == "http-in" }.str("listen"))
        val httpLan = inbounds(json).first { it.str("tag") == "http-lan" }
        assertEquals(10881, httpLan.str("port")!!.toInt())
        assertEquals("zed", httpLan["settings"]!!.jsonObject["accounts"]!!.jsonArray.single().jsonObject.str("user"))
        assertNoOpenLanListener(json)
    }

    @Test
    fun `settings map to a LAN share and old port values migrate`() {
        assertEquals(LocalPorts.LAN_SOCKS, LocalPorts.lanSocksPort(10808))
        assertEquals(LocalPorts.LAN_SOCKS, LocalPorts.lanSocksPort(10879))
        assertEquals(LocalPorts.LAN_SOCKS, LocalPorts.lanSocksPort(80))
        assertEquals(20000, LocalPorts.lanSocksPort(20000))

        val settings = AppSettings(proxySharing = true, socksPort = 10808, socksUsername = "u", socksPassword = "p")
        val json = build(settings.toBuildOptions())
        assertEquals(10880, inbounds(json).first { it.str("tag") == "socks-lan" }.str("port")!!.toInt())

        val off = build(settings.copy(proxySharing = false).toBuildOptions())
        assertTrue(inbounds(off).none { it.str("listen") == "0.0.0.0" })
    }

    @Test
    fun `local dns diverts port 53 first and fakedns layers on top`() {
        val json = build(XrayJsonBuilder.BuildOptions(localDns = true, fakeDns = true, sniffing = false))
        val first = rules(json).first()
        assertEquals("dns-out", first.str("outboundTag"))
        assertEquals("53", first.str("port"))
        assertEquals(listOf("socks-in"), first["inboundTag"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertNotNull(outbounds(json).firstOrNull { it.str("tag") == "dns-out" && it.str("protocol") == "dns" })

        val pool = root(json)["fakedns"]!!.jsonArray.single().jsonObject
        assertEquals("198.18.0.0/15", pool.str("ipPool"))
        assertEquals(65535, pool.str("poolSize")!!.toInt())
        assertEquals("fakedns", root(json)["dns"]!!.jsonObject["servers"]!!.jsonArray.first().jsonPrimitive.content)

        val sniffing = inbounds(json).first { it.str("tag") == "socks-in" }["sniffing"]!!.jsonObject
        assertEquals("true", sniffing.str("enabled"))
        assertEquals(listOf("fakedns"), sniffing["destOverride"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun `local dns off leaves the config as before, and a carrier never takes over dns`() {
        val plain = build(XrayJsonBuilder.BuildOptions())
        assertNull(root(plain)["fakedns"])
        assertTrue(outbounds(plain).none { it.str("tag") == "dns-out" })

        val carrier = build(XrayJsonBuilder.BuildOptions(localDns = true, fakeDns = true, carrier = true))
        assertNull(root(carrier)["fakedns"])
        assertTrue(rules(carrier).none { it.str("outboundTag") == "dns-out" })

        val fakeOnly = build(XrayJsonBuilder.BuildOptions(fakeDns = true))
        assertNull(root(fakeOnly)["fakedns"])
    }

    private fun proxy(json: String) = outbounds(json).first { it.str("tag") == "proxy" }
    private fun sockopt(json: String) = proxy(json)["streamSettings"]?.jsonObject?.get("sockopt")?.jsonObject
    private fun address(json: String) =
        proxy(json)["settings"]!!.jsonObject["vnext"]!!.jsonArray.first().jsonObject.str("address")

    private val resolved = mapOf("srv.example" to listOf("203.0.113.7"))

    @Test
    fun `resolve mode 1 pins the server in hosts and dials with UseIP`() {
        val json = build(XrayJsonBuilder.BuildOptions(outboundDomainResolve = "1", resolvedServerHosts = resolved))
        assertEquals("203.0.113.7", root(json)["dns"]!!.jsonObject["hosts"]!!.jsonObject.str("srv.example"))
        assertEquals("UseIP", sockopt(json)!!.str("domainStrategy"))
        assertNotNull(sockopt(json)!!["happyEyeballs"])
        assertEquals("the domain itself stays in the outbound", "srv.example", address(json))
    }

    @Test
    fun `resolve mode 2 replaces the address only when that cannot change the tls name or host`() {
        val explicit = build(XrayJsonBuilder.BuildOptions(outboundDomainResolve = "2", resolvedServerHosts = resolved))
        assertEquals("203.0.113.7", address(explicit))
        assertNull("mode 2 needs no hosts pin", sockopt(explicit))

        val derivedSni = build(
            XrayJsonBuilder.BuildOptions(outboundDomainResolve = "2", resolvedServerHosts = resolved),
            server = tlsWithoutSni,
        )
        assertEquals("srv.example", address(derivedSni))

        val wsJson = build(
            XrayJsonBuilder.BuildOptions(outboundDomainResolve = "2", resolvedServerHosts = resolved),
            server = wsNoHost,
        )
        assertEquals("srv.example", address(wsJson))
    }

    @Test
    fun `nothing is resolved in mode 0 or through a carrier`() {
        val off = build(XrayJsonBuilder.BuildOptions(outboundDomainResolve = "0", resolvedServerHosts = resolved))
        assertNull(root(off)["dns"]!!.jsonObject["hosts"])
        assertNull(sockopt(off))

        val carried = build(
            XrayJsonBuilder.BuildOptions(outboundDomainResolve = "1", resolvedServerHosts = resolved, dialerSocksPort = 10830),
        )
        assertEquals(listOf("dialerProxy"), sockopt(carried)!!.keys.toList())
        assertEquals("srv.example", address(carried))
    }

    @Test
    fun `the setting enum values are what the builder expects`() {
        assertEquals("0", OutboundDomainResolve.DoNotResolve.value)
        assertEquals("1", OutboundDomainResolve.ResolveAndAddToHosts.value)
        assertEquals("2", OutboundDomainResolve.ResolveAndReplace.value)
    }

    @Test
    fun `fragment maxSplit reaches the freedom outbound`() {
        val json = build(XrayJsonBuilder.BuildOptions(fragmentEnabled = true, fragmentMaxSplit = 10))
        val fragment = outbounds(json).first { it.str("tag") == "fragment" }["settings"]!!.jsonObject["fragment"]!!.jsonObject
        assertEquals(10, fragment.str("maxSplit")!!.toInt())
        val unset = build(XrayJsonBuilder.BuildOptions(fragmentEnabled = true))
        assertFalse(outbounds(unset).first { it.str("tag") == "fragment" }["settings"]!!.jsonObject["fragment"]!!.jsonObject.containsKey("maxSplit"))
    }
}
