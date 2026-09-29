package dev.cluvex.zedsecure.domain.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MasterDnsTest {
    private fun profile(
        domains: String = "t.example.com",
        key: String = "secret",
        resolvers: String = "8.8.8.8",
    ) = MasterDnsProfile(domains = domains, encryptionKey = key, resolvers = resolvers)

    @Test
    fun `backslash in the key does not break the TOML`() {
        val toml = profile(key = """pa\ss"wd""").toToml()

        assertTrue(toml, toml.contains("""ENCRYPTION_KEY = "pa\\ss\"wd""""))
    }

    @Test
    fun `control characters are escaped rather than emitted raw`() {
        val toml = profile(key = "a\tb\nc").toToml()
        assertTrue(toml, toml.contains("""ENCRYPTION_KEY = "a\tb\nc""""))

        assertEquals(1, toml.lines().count { it.startsWith("ENCRYPTION_KEY") })
    }

    @Test
    fun `domains are escaped too`() {
        val toml = profile(domains = """a".com, b.com""").toToml()
        assertTrue(toml, toml.contains("""DOMAINS = ["a\".com", "b.com"]"""))
    }

    @Test
    fun `log level defaults to INFO so the listener and session lines survive`() {
        assertEquals("INFO", MasterDnsProfile().logLevel)
        assertTrue(profile().toToml().contains("""LOG_LEVEL = "INFO""""))
    }

    @Test
    fun `numeric resolvers in every accepted shape are valid`() {
        listOf(
            "8.8.8.8", "1.1.1.1:53", "10.0.0.0/8", "2001:4860:4860::8888",
            "[2001:4860:4860::8888]:53", "192.168.0.0/16",
        ).forEach { assertTrue(it, MasterDnsProfile.isNumericResolver(it)) }
    }

    @Test
    fun `host names are rejected because upstream discards them`() {
        listOf("dns.google", "dns.google:53", "", "999.1.1.1", "1.1.1.1:0", "1.1.1.1:70000")
            .forEach { assertFalse(it, MasterDnsProfile.isNumericResolver(it)) }
    }

    @Test
    fun `invalidResolvers names exactly the entries that would be dropped`() {
        val bad = MasterDnsProfile.invalidResolvers("8.8.8.8, dns.google\n1.1.1.1:53\n# note\nfoo.bar")
        assertEquals(listOf("dns.google", "foo.bar"), bad)
    }

    @Test
    fun `comments and blanks are not counted as resolvers`() {
        assertEquals(listOf("8.8.8.8"), MasterDnsProfile.splitResolvers("# a comment\n\n 8.8.8.8 \n"))
    }

    @Test
    fun `a MasterDNS profile is a managed tunnel so remove-invalid keeps it`() {
        val p = VpnProfile.fromMasterDns(profile(), id = "m1", addedAt = 0L, name = "MasterDNS")
        assertTrue("must be managed or removeInvalid deletes it", p.isManagedTunnel)
        assertTrue(p.isDnsBasedTunnel)

        assertTrue(runCatching { p.toXrayConfigJson() }.isFailure)
    }
}
