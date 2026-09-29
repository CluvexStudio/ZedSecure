package dev.cluvex.zedsecure.data.config

import dev.cluvex.zedsecure.domain.config.CustomConfig
import dev.cluvex.zedsecure.domain.config.Jsonc
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import dev.cluvex.zedsecure.domain.config.SingBoxJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonEditorSaveGateTest {
    private val singBoxConfig = """
        {
          "outbounds": [
            {"type": "hysteria2", "tag": "NL", "server": "nl.example", "server_port": 443,
             "password": "p", "tls": {"enabled": true}},
            {"type": "direct", "tag": "direct"}
          ],
          "route": {"final": "NL"}
        }
    """.trimIndent()

    private val xrayConfig = """
        {
          "remarks": "Berlin",
          "outbounds": [
            {"protocol": "vless",
             "settings": {"vnext": [{"address": "1.2.3.4", "port": 443,
               "users": [{"id": "11111111-2222-3333-4444-555555555555", "encryption": "none"}]}]},
             "streamSettings": {"network": "tcp", "security": "tls"}},
            {"protocol": "freedom", "tag": "direct"}
          ]
        }
    """.trimIndent()

    private fun saveEnabledForSingBox(text: String) = SingBoxJson.shapeOf(text) != SingBoxJson.Shape.None

    private fun saveEnabledForXray(text: String, allowList: Boolean = false): Boolean {
        val parsed = runCatching { Json.parseToJsonElement(Jsonc.strip(text)) }.getOrNull() ?: return false
        return CustomConfig.looksLikeCustomJson(text) && (allowList || parsed is JsonObject)
    }

    @Test
    fun `a sing-box config the repository accepts is one the editor lets you save`() {
        val repo = ConfigRepository(InMemoryKeyValueStore())
        val added = repo.addSingBoxConfig(singBoxConfig, name = "Provider").getOrThrow()

        val edited = singBoxConfig.replace("nl.example", "nl2.example")

        assertFalse(
            "the Xray rule rejects sing-box JSON, which is why Save was dead",
            CustomConfig.looksLikeCustomJson(edited),
        )
        assertTrue("the sing-box gate must accept it", saveEnabledForSingBox(edited))

        val result = repo.updateSingBox(added.id, edited)
        assertTrue("and the repository must accept the same text: ${result.exceptionOrNull()}", result.isSuccess)
        assertTrue(repo.profiles.value.single().rawPayload().orEmpty().contains("nl2.example"))
    }

    @Test
    fun `a sing-box server edits and saves as its own fragment`() {
        val repo = ConfigRepository(InMemoryKeyValueStore())
        assertEquals(1, repo.importText(singBoxConfig).getOrThrow())
        val server = repo.profiles.value.single()

        val fragment = server.rawPayload().orEmpty()
        assertTrue("the editor opens with the fragment", fragment.isNotBlank())
        assertTrue("and must be able to save it", saveEnabledForSingBox(fragment))

        val edited = fragment.replace("443", "8443")
        assertTrue(repo.updateSingBox(server.id, edited).isSuccess)
        assertEquals(8443, repo.profiles.value.single().port)
    }

    @Test
    fun `a pasted list of configs adds them all, but cannot replace one of them`() {
        val list = "[$xrayConfig, ${xrayConfig.replace("1.2.3.4", "5.6.7.8")}]"

        assertTrue("adding accepts a list", saveEnabledForXray(list, allowList = true))
        assertFalse("editing one config does not", saveEnabledForXray(list))

        val repo = ConfigRepository(InMemoryKeyValueStore())
        assertEquals("both are imported", 2, repo.importText(list).getOrThrow())
    }

    @Test
    fun `the Xray gate still guards Xray configs`() {
        assertTrue(saveEnabledForXray(xrayConfig))
        assertFalse("a share link is not a config", saveEnabledForXray("vless://uuid@host:443"))
        assertFalse("nor is a config with no outbounds", saveEnabledForXray("""{"inbounds":[]}"""))
        assertFalse("and sing-box JSON must not be saved as Xray JSON", saveEnabledForXray(singBoxConfig))
    }
}
