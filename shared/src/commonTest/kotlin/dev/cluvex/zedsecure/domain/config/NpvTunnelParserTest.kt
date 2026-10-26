@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package dev.cluvex.zedsecure.domain.config

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class NpvTunnelParserTest {
    @Test
    fun importsOpenNpvoExport() {
        val profile = wrapperJson()
        val parsed = NpvTunnelParser.parse("NPVO1\n{\"configs\":[$profile]}")
        assertNotNull(parsed)
        assertEquals(1, parsed.size)
        assertTrue(parsed.single().link.orEmpty().startsWith("vless://"))
    }

    @Test
    fun mapsSupportedNpvProtocolFamiliesToImportableLinks() {
        val cases = listOf(1 to "vmess", 3 to "ss", 5 to "vless", 6 to "trojan", 7 to "socks")
        cases.forEach { (configType, expectedScheme) ->
            val parsed = NpvTunnelParser.parse("""{"configs":[${wrapperJson(configType)}]}""")
            assertNotNull(parsed, "configType=$configType should be recognized")
            assertEquals(1, parsed.size, "configType=$configType should produce one profile")
            assertTrue(
                parsed.single().link.orEmpty().startsWith("$expectedScheme://"),
                "configType=$configType should map to $expectedScheme",
            )
        }
    }

    @Test
    fun preservesEmbeddedXrayJsonInsteadOfDowngradingItsFields() {
        val xray = """{"outbounds":[{"protocol":"vless","settings":{"vnext":[{"address":"example.test","port":443,"users":[{"id":"00000000-0000-4000-8000-000000000000"}]}]},"streamSettings":{"network":"grpc","security":"tls"}}]}"""
        val wrapper = """{"name":"Embedded Xray","v2rayProfile":{"configType":5,"server":"example.test","serverPort":"443","password":"00000000-0000-4000-8000-000000000000","v2rayJson":${kotlinx.serialization.json.JsonPrimitive(xray)}}}"""
        val parsed = NpvTunnelParser.parse(wrapper)
        assertNotNull(parsed)
        assertEquals(1, parsed.size)
        assertTrue(parsed.single().rawJson.orEmpty().contains("\"grpc\""))
        assertTrue(parsed.single().rawJson.orEmpty().contains("\"outbounds\""))
    }

    @Test
    fun decryptsLegacyNpvtChunksAndImportsProfile() {
        val profiles = "[${wrapperJson()}]"
        val input = "NPVT1\n" + listOf("1", profiles, "{}")
            .mapIndexed { i, value -> encryptChunk(value, i + 1) }
            .joinToString(",")
        val parsed = NpvTunnelParser.parse(input)
        assertNotNull(parsed)
        assertEquals(1, parsed.size)
        val link = parsed.single().link.orEmpty()
        assertTrue(link.startsWith("vless://"))
        assertTrue(link.contains("host=cdn.example.test"))
        assertTrue(link.contains("path=%2Fws"))
    }

    @Test
    fun importsLegacyNpvSubscriptionContainer() {
        val profiles = "[${wrapperJson()}]"
        val input = "NPVTSUB1\n" + listOf("1", profiles, "{}")
            .mapIndexed { i, value -> encryptChunk(value, i + 11) }
            .joinToString(",")
        val parsed = NpvTunnelParser.parse(input)
        assertNotNull(parsed)
        assertEquals(1, parsed.size)
        assertTrue(parsed.single().link.orEmpty().startsWith("vless://"))
    }

    @Test
    fun inspectsPasswordProtectedNpvsEnvelopeWithoutClaimingItWasDecrypted() {
        val headerLength = 169
        val bodyOffset = 9 + headerLength
        val bytes = ByteArray(bodyOffset + 16 + 32 + 64)
        bytes[0] = 'N'.code.toByte()
        bytes[1] = 'P'.code.toByte()
        bytes[2] = 'V'.code.toByte()
        bytes[3] = 'S'.code.toByte()
        bytes[4] = 5
        putUInt32(bytes, 5, headerLength)
        bytes[9] = 1
        bytes[59] = 1
        putUInt32(bytes, 9 + 53, 600_000)
        putUInt32(bytes, 9 + 133, 16)
        putUInt32(bytes, bodyOffset + 12, 32)
        val info = NpvTunnelParser.inspectNpvs(bytes)
        assertNotNull(info)
        assertEquals(5, info.version)
        assertTrue(info.unlockMethod.contains("creator passphrase"))
    }

    @Test
    fun inspectsAppKeyNpvsEnvelopeWithoutClaimingDecryption() {
        val headerLength = 169
        val bodyOffset = 9 + headerLength
        val bytes = ByteArray(bodyOffset + 16 + 32 + 64)
        bytes[0] = 'N'.code.toByte()
        bytes[1] = 'P'.code.toByte()
        bytes[2] = 'V'.code.toByte()
        bytes[3] = 'S'.code.toByte()
        bytes[4] = 5
        putUInt32(bytes, 5, headerLength)
        bytes[9] = 1
        bytes[59] = 2
        putUInt32(bytes, 9 + 131, 16)
        putUInt32(bytes, bodyOffset + 12, 32)

        val info = NpvTunnelParser.inspectNpvs(bytes)
        assertNotNull(info)
        assertEquals(5, info.version)
        assertEquals("app key", info.unlockMethod)
    }

    @Test
    fun rejectsRecipientKeyNpvsEnvelopeWithoutRecipients() {
        val headerLength = 169
        val bodyOffset = 9 + headerLength
        val bytes = ByteArray(bodyOffset + 16 + 32 + 64)
        bytes[0] = 'N'.code.toByte()
        bytes[1] = 'P'.code.toByte()
        bytes[2] = 'V'.code.toByte()
        bytes[3] = 'S'.code.toByte()
        bytes[4] = 5
        putUInt32(bytes, 5, headerLength)
        bytes[9] = 1
        bytes[59] = 0
        putUInt32(bytes, 9 + 131, 16)
        putUInt32(bytes, bodyOffset + 12, 32)

        assertFailsWith<IllegalArgumentException> {
            NpvTunnelParser.inspectNpvs(bytes)
        }
    }

    @Test
    fun rejectsMalformedNpvtRatherThanTreatingItAsARegularLink() {
        assertFailsWith<IllegalArgumentException> {
            NpvTunnelParser.parse("NPVT1\nnot-a-valid-container")
        }
    }

    private fun wrapperJson(configType: Int = 5): String = """
        {
          "name": "Synthetic NPV profile",
          "address": "example.test:443",
          "type": "V2RAY",
          "v2rayProfile": {
            "configType": ${configType},
            "remarks": "Synthetic NPV profile",
            "server": "example.test",
            "serverPort": "443",
            "password": "00000000-0000-4000-8000-000000000000",
            "method": "aes-256-gcm",
            "network": "ws",
            "security": "tls",
            "host": "cdn.example.test",
            "path": "/ws",
            "persistJson": true,
            "v2rayJson": ""
          },
          "lockConfig": {}
        }
    """.trimIndent()

    private fun encryptChunk(plain: String, nonceSeed: Int): String {
        val nonce = ByteArray(16) { i -> (nonceSeed * 17 + i).toByte() }
        val input = nonce + plain.encodeToByteArray()
        val encrypted = NpvtWhiteboxCipher.decrypt(input)
        return Base64.Default.encode(nonce + encrypted)
    }

    private fun putUInt32(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value ushr 24).toByte()
        target[offset + 1] = (value ushr 16).toByte()
        target[offset + 2] = (value ushr 8).toByte()
        target[offset + 3] = value.toByte()
    }
}
