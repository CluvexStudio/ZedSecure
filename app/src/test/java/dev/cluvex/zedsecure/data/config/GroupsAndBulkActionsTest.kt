package dev.cluvex.zedsecure.data.config

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupsAndBulkActionsTest {
    private fun repo() = ConfigRepository(InMemoryKeyValueStore())

    private val good = "vless://11111111-1111-1111-1111-111111111111@a.example:443" +
        "?encryption=none&security=tls&type=ws&host=a.example&path=%2F#Good"

    @Test
    fun `delete all removes every server and keeps the locked ones`() {
        val repo = repo()
        repo.importText(good).getOrThrow()
        repo.importText(good.replace("a.example", "b.example").replace("#Good", "#Second")).getOrThrow()
        assertEquals(2, repo.profiles.value.size)

        repo.removeAll { !it.isLocked }
        assertEquals(0, repo.profiles.value.size)
        assertNull("nothing left to be active", repo.activeId.value)
    }

    @Test
    fun `delete invalid drops a server with nowhere to dial`() {
        val repo = repo()
        repo.importText(good).getOrThrow()
        val id = repo.profiles.value.single().id

        repo.update(repo.profiles.value.single().copy(address = "", port = 0))

        assertEquals(1, repo.removeInvalid())
        assertTrue(repo.profiles.value.none { it.id == id })
    }

    @Test
    fun `delete invalid drops the servers a test could not reach`() {
        val repo = repo()
        repo.importText(good).getOrThrow()
        repo.importText(good.replace("a.example", "b.example").replace("#Good", "#Dead")).getOrThrow()
        repo.importText(good.replace("a.example", "c.example").replace("#Good", "#Untested")).getOrThrow()
        val byName = repo.profiles.value.associateBy { it.name }
        repo.setPing(byName.getValue("Good").id, 120)

        repo.setPing(byName.getValue("Dead").id, -1)

        assertEquals(1, repo.removeInvalid())
        val left = repo.profiles.value.map { it.name }.toSet()
        assertTrue("the reachable one stays", "Good" in left)
        assertTrue("a server nobody tested has no verdict yet", "Untested" in left)
        assertTrue("the unreachable one is gone", "Dead" !in left)
    }

    @Test
    fun `a group is a subscription with no url, and nothing fetches it`() = runBlocking<Unit> {
        val repo = repo()
        val group = repo.addGroup("  Iran  ")
        assertEquals("Iran", group.name)
        assertEquals("", group.url)
        assertTrue("it shows up as a tab like any subscription", repo.subscriptions.value.contains(group))

        repo.importText(good).getOrThrow()
        val server = repo.profiles.value.single()
        assertEquals("added by hand, so it starts in Manual", "", server.subscriptionId)

        repo.moveToGroup(server.id, group.id)
        assertEquals(group.id, repo.profiles.value.single().subscriptionId)
        repo.moveToGroup(server.id, "")
        assertEquals("and back to Manual", "", repo.profiles.value.single().subscriptionId)

        val updated = repo.updateSubscription(group.id)
        assertEquals(0, updated.getOrThrow())
    }
}
