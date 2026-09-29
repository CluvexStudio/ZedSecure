package dev.cluvex.zedsecure.data.config

import dev.cluvex.zedsecure.domain.config.AutoSelectIds
import dev.cluvex.zedsecure.domain.config.AutoSelectTuning
import dev.cluvex.zedsecure.domain.config.XrayJsonBuilder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSelectRepositoryTest {
    private fun link(host: String) =
        "vless://11111111-2222-3333-4444-555555555555@$host:443?encryption=none&type=ws#$host"

    @Test
    fun `an auto id resolves to a profile over the right servers`() {
        val repo = ConfigRepository(InMemoryKeyValueStore())
        repo.importText(link("a.example") + "\n" + link("b.example")).getOrThrow()
        val auto = repo.profile(AutoSelectIds.ALL)
        assertNotNull(auto)
        assertTrue(auto!!.isAutoSelect)
        assertEquals(2, repo.autoSelectMembers(null).size)
        assertEquals(2, repo.autoSelectMembers("").size)
        assertNull("stored list is untouched", repo.profiles.value.firstOrNull { it.isAutoSelect })
    }

    @Test
    fun `the active auto entry survives its servers being replaced, and goes with its subscription`() {
        val repo = ConfigRepository(InMemoryKeyValueStore())
        val sub = repo.addSubscription("Provider", "https://sub.example/x")
        repo.importText(link("a.example") + "\n" + link("b.example"), subscriptionId = sub.id).getOrThrow()
        val autoId = AutoSelectIds.of(sub.id)
        repo.setActive(autoId)
        assertEquals("Provider", repo.activeProfile()?.name)

        repo.removeAll { it.subscriptionId == sub.id }
        assertEquals("the selection must not jump to another server during a refresh", autoId, repo.activeId.value)

        repo.importText(link("c.example"), subscriptionId = sub.id).getOrThrow()
        repo.removeSubscription(sub.id)
        assertTrue("a deleted group can no longer be active", repo.activeId.value != autoId)
    }

    @Test
    fun `locked and managed profiles never join a group`() {
        val repo = ConfigRepository(InMemoryKeyValueStore())
        repo.importText(link("a.example")).getOrThrow()
        repo.addTor("Tor")
        assertEquals(listOf("a.example"), repo.autoSelectMembers(null).map { it.name })
    }

    @Test
    fun `a reconnect starts on the last server the group used, else on the fastest ping`() {
        val repo = ConfigRepository(InMemoryKeyValueStore())
        repo.importText(link("a.example") + "\n" + link("b.example") + "\n" + link("c.example")).getOrThrow()
        val (a, b, c) = repo.profiles.value
        repo.setPing(a.id, 300)
        repo.setPing(b.id, 90)
        repo.setPing(c.id, -1)
        val auto = repo.profile(AutoSelectIds.ALL)!!

        fun initialTag(): String? {
            val build = repo.buildAutoSelectConfig(auto, XrayJsonBuilder.BuildOptions(), AutoSelectTuning())
            val group = Json.parseToJsonElement(build.json).jsonObject["outbounds"]!!.jsonArray.first().jsonObject
            val tag = group["settings"]!!.jsonObject["initial"]?.jsonPrimitive?.content
            return tag?.let { build.memberProfiles[it] }
        }
        assertEquals("fastest stored ping", b.id, initialTag())
        repo.rememberAutoSelectPick(auto.id, c.id)
        assertEquals("the last server Auto actually used wins", c.id, initialTag())
    }
}
