package dev.cluvex.zedsecure.domain.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionCustomImportTest {
    private val body: String =
        javaClass.classLoader!!.getResourceAsStream("sub_sample.json")!!
            .bufferedReader().use { it.readText() }

    private val lenient = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `body is detected as custom json, not links`() {
        assertTrue("must look like a custom config", CustomConfig.looksLikeCustomJson(body))

        assertTrue(SubscriptionParser.extractLinks(body).isEmpty())

        assertTrue(runCatching { ConfigParser.decodeBase64Utf8(body) }.getOrNull().isNullOrBlank() ||
            runCatching { ConfigParser.decodeBase64Utf8(body) }.getOrNull()?.contains("vless://") != true)
    }

    @Test
    fun `every element imports as a CUSTOM profile that builds a valid core config`() {
        val clean = Jsonc.strip(body)
        val element = lenient.parseToJsonElement(clean)
        val objects = (element as JsonArray).mapNotNull { it as? JsonObject }
        assertEquals(24, objects.size)

        objects.forEachIndexed { i, obj ->
            val raw = Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), obj)
            val profile = VpnProfile.fromRawJson(raw, id = "id$i", addedAt = 0L)
            assertEquals("entry $i must be CUSTOM", "CUSTOM", profile.protocol)
            assertTrue("entry $i must be editable as raw JSON", profile.isCustom)

            val core = profile.toXrayConfigJson()
            assertTrue("entry $i core config must have outbounds", core.contains("outbounds"))
        }
    }
}
