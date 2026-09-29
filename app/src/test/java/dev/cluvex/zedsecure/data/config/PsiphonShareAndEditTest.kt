package dev.cluvex.zedsecure.data.config

import dev.cluvex.zedsecure.domain.config.PsiphonConfigBuilder
import dev.cluvex.zedsecure.domain.config.PsiphonProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PsiphonShareAndEditTest {
    private val fronted = PsiphonProfile(
        country = "NL",
        mode = PsiphonConfigBuilder.MODE_CDN,
        cdnIps = "104.16.35.164\n104.17.2.81",
        cdnSni = "cdn.example.com",
    )

    @Test
    fun `editing keeps the same server, with the new settings`() {
        val repo = ConfigRepository(InMemoryKeyValueStore())
        val added = repo.addPsiphon(PsiphonProfile(country = "US"), "Psiphon")
        repo.setActive(added.id)

        val edited = repo.addPsiphon(fronted, "Psiphon NL", id = added.id)

        assertEquals("the same profile, not a second one", 1, repo.profiles.value.size)
        assertEquals(added.id, edited.id)
        assertEquals(added.id, repo.activeId.value)
        assertEquals("Psiphon NL", repo.profiles.value.single().name)
        assertEquals(fronted, repo.profiles.value.single().psiphonSettings())
    }

    @Test
    fun `a share link carries the region and the CDN fronting`() {
        val repo = ConfigRepository(InMemoryKeyValueStore())
        val added = repo.addPsiphon(fronted, "Psiphon NL")

        val link = repo.shareLinkOf(added)
        assertNotNull("a Psiphon profile is shareable", link)
        assertTrue(link!!.startsWith("zedsecure://"))

        val other = ConfigRepository(InMemoryKeyValueStore())
        assertEquals(1, other.importText(link).getOrThrow())
        val received = other.profiles.value.single()
        assertEquals("Psiphon NL", received.name)
        assertEquals(fronted, received.psiphonSettings())
    }
}
