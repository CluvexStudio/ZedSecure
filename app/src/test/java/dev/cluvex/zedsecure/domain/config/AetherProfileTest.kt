package dev.cluvex.zedsecure.domain.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AetherProfileTest {
    @Test
    fun encodedProfileRoundTripsAndRejectsOtherConfigTypes() {
        val profile = AetherProfile(
            profileName = "Primary",
            protocol = "masque",
            http2 = true,
            fragment = true,
            apiAddress = "162.159.137.105",
        )

        val decoded = AetherProfile.decode(AetherProfile.encode(profile))

        assertEquals(profile, decoded)
        assertNull(AetherProfile.decode("""{"protocol":"vless"}"""))
    }

    @Test
    fun attemptsKeepUserSelectionFirstAndRemoveDuplicates() {
        val profile = AetherProfile(protocol = "masque", http2 = false)

        val attempts = profile.attempts()

        assertEquals(profile, attempts.first())
        assertEquals(attempts.size, attempts.distinct().size)
        assertTrue(attempts.any { it.protocol == "wg" })
        assertTrue(attempts.any { it.protocol == "masque" && it.http2 && it.fragment })
    }

    @Test
    fun argumentsIncludeLoopbackBindingAndDiagnostics() {
        val args = AetherProfile().toArgs(10991, "/data/user/0/app/files/aether")

        assertTrue(args.containsAll(listOf("--bind", "127.0.0.1:10991")))
        assertTrue(args.containsAll(listOf("--stats", "--stats-secs", "15")))
        assertTrue(args.contains("--quick-reconnect"))
        assertTrue(args.containsAll(listOf("--config", "/data/user/0/app/files/aether/aether.toml")))
        assertFalse(args.contains("--fragment"))
    }

    @Test
    fun startupBudgetAllowsNativeScanToFinish() {
        assertEquals(60_000L, AetherProfile(scanMode = "turbo").startupBudgetMs())
        assertEquals(120_000L, AetherProfile(scanMode = "balanced").startupBudgetMs())
        assertEquals(330_000L, AetherProfile(scanMode = "thorough").startupBudgetMs())
        assertEquals(330_000L, AetherProfile(scanMode = "verified").startupBudgetMs())
        assertEquals(330_000L, AetherProfile(scanMode = "ironclad").startupBudgetMs())
    }

    @Test
    fun invalidProtocolIsRejectedBeforeProcessLaunch() {
        val failure = runCatching {
            AetherProfile(protocol = "unknown").toArgs(10991, "/tmp/aether")
        }.exceptionOrNull()

        assertNotNull(failure)
        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun legacyProfileWithoutMarkerIsStillRecognizedAsAether() {
        val legacy = """{"profileName":"Aether · Smart · Mobile","protocol":"masque","scanMode":"thorough","ipVersion":"v4","noize":"firewall","http2":true}"""
        val decoded = AetherProfile.decode(legacy)
        assertNotNull(decoded)
        assertTrue(decoded!!.http2)
        assertNull(AetherProfile.decode("""{"outbounds":[],"inbounds":[],"log":{}}"""))
    }
}
