package dev.cluvex.zedsecure.data.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportOrderAndDuplicatesTest {
    private fun link(host: String, name: String) =
        "vless://11111111-2222-3333-4444-555555555555@$host:443?encryption=none&type=ws#$name"

    @Test
    fun `a pasted config goes to the top of the list`() {
        val repo = ConfigRepository(InMemoryKeyValueStore())
        repo.importText(link("first.example.com", "First")).getOrThrow()
        repo.importText(link("second.example.com", "Second")).getOrThrow()

        assertEquals(
            "the most recently pasted config must be first",
            listOf("Second", "First"),
            repo.profiles.value.map { it.name },
        )
    }

    @Test
    fun `several links pasted at once keep their own order at the top`() {
        val repo = ConfigRepository(InMemoryKeyValueStore())
        repo.importText(link("old.example.com", "Old")).getOrThrow()
        repo.importText(link("a.example.com", "A") + "\n" + link("b.example.com", "B")).getOrThrow()

        assertEquals(listOf("A", "B", "Old"), repo.profiles.value.map { it.name })
    }

    @Test
    fun `a raw JSON config also goes to the top`() {
        val repo = ConfigRepository(InMemoryKeyValueStore())
        repo.importText(link("old.example.com", "Old")).getOrThrow()
        val json = """
            {"remarks":"Custom","outbounds":[{"tag":"proxy","protocol":"vless","settings":{"vnext":[
            {"address":"c.example.com","port":443,"users":[{"id":"11111111-2222-3333-4444-555555555555"}]}]}}]}
        """.trimIndent()
        repo.importText(json).getOrThrow()

        assertEquals("Custom", repo.profiles.value.first().name)
    }

    @Test
    fun `re-pasting the same config adds nothing and is reported as a duplicate, not a failure`() =
        kotlinx.coroutines.runBlocking {
            val repo = ConfigRepository(InMemoryKeyValueStore())
            val l = link("dup.example.com", "Dup")
            assertEquals(1, repo.importPasted(l).getOrThrow().count)

            val again = repo.importPasted(l).getOrThrow()
            assertEquals("nothing new must be added", 0, again.count)
            assertEquals("the skip must be reported so the UI can say 'already added'", 1, again.duplicates)
            assertEquals(1, repo.profiles.value.size)
        }

    @Test
    fun `a genuinely unreadable payload still reports zero duplicates`() =
        kotlinx.coroutines.runBlocking {
            val repo = ConfigRepository(InMemoryKeyValueStore())
            val r = runCatching { repo.importPasted("this is not a config at all").getOrThrow() }

            assertEquals(0, r.getOrNull()?.duplicates ?: 0)
        }

    @Test
    fun `a subscription import keeps the provider order`() {
        val repo = ConfigRepository(InMemoryKeyValueStore())
        val body = listOf("S1", "S2", "S3").joinToString("\n") { link("${it.lowercase()}.example.com", it) }
        repo.importText(body, subscriptionId = "sub-1").getOrThrow()

        assertEquals(listOf("S1", "S2", "S3"), repo.profiles.value.map { it.name })
    }

    @Test
    fun `a subscription refresh appends after existing manual servers rather than reordering them`() {
        val repo = ConfigRepository(InMemoryKeyValueStore())
        repo.importText(link("manual.example.com", "Manual")).getOrThrow()
        repo.importText(link("s1.example.com", "S1"), subscriptionId = "sub-1").getOrThrow()

        assertEquals(listOf("Manual", "S1"), repo.profiles.value.map { it.name })
        assertTrue(repo.profiles.value.first().subscriptionId.isBlank())
    }
}
