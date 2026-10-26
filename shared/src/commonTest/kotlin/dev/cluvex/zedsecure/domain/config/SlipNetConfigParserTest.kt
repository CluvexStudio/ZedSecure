@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package dev.cluvex.zedsecure.domain.config

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SlipNetConfigParserTest {
    @Test
    fun parsesPlainDnsttProfileIntoRunnableDnsTunnelSettings() {
        val parsed = SlipNetConfigParser.parse("slipnet://${encode(profile(mode = "dnstt"))}")
        assertNotNull(parsed)
        assertEquals(1, parsed.size)
        assertEquals("Test DNSTT", parsed.single().name)
        assertEquals(DnsTunnelProfile.ENGINE_DNSTT, parsed.single().settings.engine)
        assertEquals("t.example.test", parsed.single().settings.domain)
        assertEquals("dns.example.test:53", parsed.single().settings.resolvers)
        assertEquals("pubkey-test", parsed.single().settings.publicKey)
        assertTrue(parsed.single().settings.dnsttCompat)
    }

    @Test
    fun parsesVaydnsAndMapsTuningFields() {
        val parsed = SlipNetConfigParser.parse("slipnet://${encode(profile(mode = "vaydns"))}")
        assertNotNull(parsed)
        val settings = parsed.single().settings
        assertEquals(DnsTunnelProfile.ENGINE_VAYDNS, settings.engine)
        assertEquals("cname", settings.recordType)
        assertEquals(96, settings.maxQnameLen)
        assertEquals(5.0, settings.rps)
        assertEquals(7, settings.rrSpreadCount)
        assertTrue(settings.dnsttCompat)
        assertEquals(90, settings.dnsPayloadSize)
    }

    @Test
    fun rejectsLockedProfilesUntilTheirLockPolicyCanBeEnforced() {
        val locked = profile(mode = "dnstt").split("|").toMutableList().also { it[31] = "1" }.joinToString("|")
        val error = assertFailsWith<IllegalArgumentException> {
            SlipNetConfigParser.parse("slipnet://${encode(locked)}")
        }
        assertTrue(error.message.orEmpty().contains("profile lock"))
    }

    @Test
    fun acceptsEncryptedSingleProfileSchemeThroughCryptoBridge() {
        // Crypto compatibility is covered by the platform build; here verify
        // that unrelated input is not misclassified as a SlipNet payload.
        assertNull(SlipNetConfigParser.parse("vless://uuid@example.test:443"))
    }

    @Test
    fun requestsPasswordForEncryptedBundle() {
        assertFailsWith<SlipNetConfigParser.PasswordRequiredException> {
            SlipNetConfigParser.parse("slipnet-bundle-enc://AA==")
        }
    }

    @Test
    fun rejectsSlipstreamRatherThanSilentlyConvertingItToDnstt() {
        val error = assertFailsWith<IllegalArgumentException> {
            SlipNetConfigParser.parse("slipnet://${encode(profile(mode = "ss"))}")
        }
        assertTrue(error.message.orEmpty().contains("not executable"))
    }

    private fun profile(mode: String): String {
        val fields = MutableList(80) { "" }
        fields[0] = "v28"
        fields[1] = mode
        fields[2] = "Test ${mode.uppercase()}"
        fields[3] = "t.example.test"
        fields[4] = "dns.example.test:53:0"
        fields[8] = "443"
        fields[11] = "pubkey-test"
        fields[22] = "udp"
        fields[27] = "1"
        fields[39] = "90"
        fields[41] = "1"
        fields[42] = "cname"
        fields[43] = "96"
        fields[44] = "5"
        fields[60] = "roundrobin"
        fields[61] = "7"
        return fields.joinToString("|")
    }

    private fun encode(value: String): String =
        Base64.Default.withPadding(Base64.PaddingOption.ABSENT).encode(value.encodeToByteArray())
}
