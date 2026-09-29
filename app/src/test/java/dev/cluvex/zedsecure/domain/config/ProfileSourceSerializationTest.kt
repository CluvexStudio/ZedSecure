package dev.cluvex.zedsecure.domain.config

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileSourceSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun torProfileRoundTrips() {
        val p = VpnProfile.fromTor(id = "t1", addedAt = 1L, name = "Tor")
        val encoded = json.encodeToString(listOf(p))
        val decoded = json.decodeFromString<List<VpnProfile>>(encoded)
        assertEquals(1, decoded.size)
        assertTrue(decoded[0].isTor)
        assertEquals("Tor", decoded[0].name)
    }

    @Test fun dnsTunnelRoundTrips() {
        val p = VpnProfile.fromDnsTunnel(
            settings = DnsTunnelProfile(engine = "dnstt", domain = "t.example.com", publicKey = "ab"),
            id = "d1", addedAt = 1L, name = "d",
        )
        val decoded = json.decodeFromString<List<VpnProfile>>(json.encodeToString(listOf(p)))
        assertTrue(decoded[0].isDnsTunnel)
    }
}
