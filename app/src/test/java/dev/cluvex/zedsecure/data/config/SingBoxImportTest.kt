package dev.cluvex.zedsecure.data.config

import dev.cluvex.zedsecure.domain.config.ProfileSource
import dev.cluvex.zedsecure.domain.config.VpnProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SingBoxImportTest {
    private val subscription = """
        {
          "outbounds": [
            {"type": "selector", "tag": "proxy", "outbounds": ["DE", "NL"]},
            {"type": "tuic", "tag": "DE", "server": "de.example", "server_port": 443, "uuid": "u", "password": "p",
             "tls": {"enabled": true}},
            {"type": "hysteria2", "tag": "NL", "server": "nl.example", "server_port": 443, "password": "p",
             "tls": {"enabled": true}},
            {"type": "direct", "tag": "direct"}
          ],
          "route": {"final": "proxy"}
        }
    """.trimIndent()

    @Test
    fun `a sing-box subscription body becomes one sing-box server per outbound`() {
        val repo = ConfigRepository(InMemoryKeyValueStore())
        val sub = repo.addSubscription("Provider", "https://sub.example/singbox")
        assertEquals(2, repo.importText(subscription, subscriptionId = sub.id).getOrThrow())
        val profiles = repo.profiles.value
        assertEquals(listOf("DE", "NL"), profiles.map { it.name })
        assertTrue(profiles.all { it.source is ProfileSource.SingBox })
        assertTrue("nothing may be imported as an Xray custom config", profiles.none { it.isCustom })
        assertEquals(listOf("TUIC · sing-box", "Hysteria2 · sing-box"), profiles.map { it.transportLabel })
        profiles.forEach { assertTrue(it.toXrayConfigJson().contains("\"singbox\"")) }
    }

    @Test
    fun `refreshing the same subscription adds nothing and never falls back to Xray JSON`() {
        val repo = ConfigRepository(InMemoryKeyValueStore())
        val sub = repo.addSubscription("Provider", "https://sub.example/singbox")
        repo.importText(subscription, subscriptionId = sub.id).getOrThrow()
        assertEquals(0, repo.importText(subscription, subscriptionId = sub.id).getOrThrow())
        assertEquals(2, repo.profiles.value.size)
    }

    @Test
    fun `a full config pasted by hand is kept whole for the sing-box engine`() {
        val repo = ConfigRepository(InMemoryKeyValueStore())
        val full = subscription.replace("\"route\"", "\"inbounds\": [{\"type\": \"tun\"}], \"route\"")
        assertEquals(1, repo.importText(full).getOrThrow())
        val profile = repo.profiles.value.single()
        assertTrue(profile.source is ProfileSource.SingBoxConfig)
        assertTrue(profile.isManagedTunnel)
        assertEquals(full, profile.rawPayload())
    }

    @Test
    fun `sing-box-only links import as sing-box servers and share back as the same link`() {
        val repo = ConfigRepository(InMemoryKeyValueStore())
        val tuic = "tuic://u:p@de.example:443?congestion_control=bbr#DE"
        val socks4 = "socks4://alice@10.0.0.1:1080#S4"
        assertEquals(2, repo.importText("$tuic\n$socks4").getOrThrow())
        val byName = repo.profiles.value.associateBy { it.name }
        assertTrue(byName.getValue("DE").source is ProfileSource.SingBox)
        assertEquals(tuic, repo.shareLinkOf(byName.getValue("DE")))
        assertEquals("SOCKS · sing-box", byName.getValue("S4").transportLabel)
    }

    @Test
    fun `an older saved socks4 link runs through sing-box instead of the SOCKS5 client`() {
        val saved = VpnProfile(
            id = "old",
            name = "S4",
            protocol = "SOCKS",
            address = "10.0.0.1",
            port = 1080,
            transportLabel = "SOCKS",
            source = ProfileSource.Link("socks4://alice@10.0.0.1:1080#S4"),
            addedAt = 0,
        )
        val config = saved.toXrayConfigJson()
        assertTrue(config.contains("\"singbox\""))
        assertTrue(config.contains("\"version\": \"4\""))
    }

    @Test
    fun `editing a sing-box server keeps its place and name`() {
        val repo = ConfigRepository(InMemoryKeyValueStore())
        repo.importText("anytls://p@nl.example:443#NL").getOrThrow()
        val before = repo.profiles.value.single()
        val edited = repo.updateSingBox(
            before.id,
            """{"outbounds":[{"type":"anytls","tag":"NL","server":"nl2.example","server_port":8443,"password":"p2"}]}""",
        ).getOrThrow()
        assertEquals(before.id, edited.id)
        assertEquals("NL", edited.name)
        assertEquals("nl2.example", edited.address)
        assertTrue(repo.updateSingBox(before.id, "not json").isFailure)
        assertEquals("a failed edit changes nothing", "nl2.example", repo.profiles.value.single().address)
    }

    @Test
    fun `sing-box servers join auto-select groups`() {
        val repo = ConfigRepository(InMemoryKeyValueStore())
        val sub = repo.addSubscription("Provider", "https://sub.example/singbox")
        repo.importText(subscription, subscriptionId = sub.id).getOrThrow()
        assertEquals(2, repo.autoSelectMembers(sub.id).size)
    }
}
