package dev.cluvex.zedsecure.data.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionCustomImportE2ETest {
    private val body: String =
        javaClass.classLoader!!.getResourceAsStream("sub_sample.json")!!
            .bufferedReader().use { it.readText() }

    @Test
    fun `the whole array imports as custom configs, named from remarks, socks rewritten`() {
        val repo = ConfigRepository(InMemoryKeyValueStore())
        val count = repo.importText(body, subscriptionId = "sub-1").getOrThrow()
        val profiles = repo.profiles.value

        assertEquals("every array element must become one profile", 24, count)
        assertEquals(24, profiles.size)

        profiles.forEach {
            assertEquals("CUSTOM", it.protocol)
            assertTrue("${it.name} must keep its raw JSON", it.isCustom)
            assertEquals("sub-1", it.subscriptionId)
        }

        assertTrue(
            "names must come from remarks, got: ${profiles.take(3).map { it.name }}",
            profiles.none { it.name.isBlank() },
        )

        val core = profiles.first().toXrayConfigJson()
        assertTrue("socks inbound must be forced to loopback:\n$core", core.contains("\"listen\": \"127.0.0.1\""))
        assertTrue(core.contains("\"port\": 10808"))
        assertTrue(core.contains("outbounds"))

        println("=== IMPORT EVIDENCE ===")
        println("imported: $count profiles, all CUSTOM")
        profiles.take(3).forEach { println("  name: ${it.name}") }
        println("  socks inbound of profile[0] (was 0.0.0.0:10808 in the subscription):")
        core.lines()
            .filter { it.contains("\"listen\"") || it.contains("\"port\"") || it.contains("\"protocol\": \"socks\"") }
            .take(4)
            .forEach { println("   ${it.trim()}") }
    }
}
