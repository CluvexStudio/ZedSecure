package dev.cluvex.zedsecure.data.config

import dev.cluvex.zedsecure.domain.config.CustomConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RawJsonEditTest {
    private fun config(remarks: String, address: String, port: Int) = """
        {
          "remarks": "$remarks",
          "outbounds": [
            {
              "protocol": "vless",
              "settings": {
                "vnext": [
                  {
                    "address": "$address",
                    "port": $port,
                    "users": [{ "id": "11111111-2222-3333-4444-555555555555", "encryption": "none" }]
                  }
                ]
              },
              "streamSettings": { "network": "tcp", "security": "tls" }
            },
            { "protocol": "freedom", "tag": "direct" }
          ]
        }
    """.trimIndent()

    @Test
    fun `an edited config replaces the old one and survives a reload`() {
        val store = InMemoryKeyValueStore()
        val repo = ConfigRepository(store)
        val added = repo.addRawJson(config("Berlin", "1.2.3.4", 443)).getOrThrow()

        val edited = config("Berlin", "9.8.7.6", 8443)
        assertTrue("the Save button must be enabled for it", CustomConfig.looksLikeCustomJson(edited))

        val result = repo.updateRawJson(added.id, edited)
        assertTrue("saving must succeed: ${result.exceptionOrNull()}", result.isSuccess)

        assertEquals("still one profile", 1, repo.profiles.value.size)
        assertEquals("same entry, not a new one", added.id, repo.profiles.value.single().id)
        assertEquals("9.8.7.6", repo.profiles.value.single().address)
        assertEquals(8443, repo.profiles.value.single().port)

        val reopened = ConfigRepository(store)
        assertEquals("9.8.7.6", reopened.profiles.value.single().address)
        assertTrue(reopened.profiles.value.single().rawPayload().orEmpty().contains("9.8.7.6"))
    }

    @Test
    fun `a renamed config keeps its name when the JSON carries no remarks`() {
        val store = InMemoryKeyValueStore()
        val repo = ConfigRepository(store)
        val noRemarks = config("x", "1.2.3.4", 443).replace("\"remarks\": \"x\",", "")
        val added = repo.addRawJson(noRemarks).getOrThrow()
        repo.rename(added.id, "My server")

        repo.updateRawJson(added.id, noRemarks.replace("1.2.3.4", "5.6.7.8")).getOrThrow()

        assertEquals("My server", repo.profiles.value.single().name)
    }

    @Test
    fun `the active config stays active after its JSON is edited`() {
        val store = InMemoryKeyValueStore()
        val repo = ConfigRepository(store)
        val added = repo.addRawJson(config("Berlin", "1.2.3.4", 443)).getOrThrow()
        repo.setActive(added.id)

        repo.updateRawJson(added.id, config("Berlin", "1.2.3.4", 2053)).getOrThrow()

        assertEquals(added.id, repo.activeId.value)
        assertEquals(2053, repo.profiles.value.single().port)
    }
}
