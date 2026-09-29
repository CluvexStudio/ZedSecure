package dev.cluvex.zedsecure.data.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PingClearTest {
    private fun seeded(): Pair<ConfigRepository, List<String>> {
        val repo = ConfigRepository(InMemoryKeyValueStore())
        val links = (1..3).joinToString("\n") { i ->
            "vless://11111111-2222-3333-4444-55555555555$i@example$i.com:8443" +
                "?encryption=none&security=tls&type=tcp#S$i"
        }
        assertEquals(3, repo.importText(links).getOrThrow())
        val ids = repo.profiles.value.map { it.id }
        listOf(120, 340, 55).forEachIndexed { i, ms -> repo.setPing(ids[i], ms) }
        return repo to ids
    }

    @Test
    fun `clearPings blanks every listed server`() {
        val (repo, ids) = seeded()
        repo.clearPings(ids)
        repo.profiles.value.forEach { assertNull("${it.name} must be blank", it.lastPingMs) }
    }

    @Test
    fun `clearPings leaves servers outside the set untouched`() {
        val (repo, ids) = seeded()
        repo.clearPings(listOf(ids[0]))

        val byId = repo.profiles.value.associateBy { it.id }
        assertNull(byId[ids[0]]!!.lastPingMs)

        assertEquals(340, byId[ids[1]]!!.lastPingMs)
        assertEquals(55, byId[ids[2]]!!.lastPingMs)
    }

    @Test
    fun `a cleared value is null, never zero, so the UI renders the blank state`() {
        val (repo, ids) = seeded()
        repo.clearPings(ids)

        repo.profiles.value.forEach { assertNull(it.lastPingMs) }
    }

    @Test
    fun `clearing survives a reload, so a killed test does not resurrect old numbers`() {
        val store = InMemoryKeyValueStore()
        val repo = ConfigRepository(store)
        repo.importText(
            "vless://11111111-2222-3333-4444-555555555551@example1.com:8443" +
                "?encryption=none&security=tls&type=tcp#S1",
        ).getOrThrow()
        val id = repo.profiles.value.single().id
        repo.setPing(id, 120)

        repo.clearPings(listOf(id))

        assertNull(ConfigRepository(store).profiles.value.single().lastPingMs)
    }

    @Test
    fun `clearing an empty set is a no-op`() {
        val (repo, _) = seeded()
        repo.clearPings(emptyList())
        assertEquals(120, repo.profiles.value.first().lastPingMs)
    }
}
