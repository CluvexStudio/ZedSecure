package dev.cluvex.zedsecure.data.config

import dev.cluvex.zedsecure.domain.config.ProfileSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OvpnImportTest {
    private val profile = """
        client
        dev tun
        proto udp
        remote vpn.example.net 1194
        auth-user-pass
        remote-cert-tls server
        cipher AES-256-GCM
        <ca>
        -----BEGIN CERTIFICATE-----
        MIIBexample
        -----END CERTIFICATE-----
        </ca>
    """.trimIndent()

    @Test
    fun `pasting an OpenVPN profile adds one server`() {
        val repo = ConfigRepository(InMemoryKeyValueStore())
        assertEquals(1, repo.importText(profile).getOrThrow())
        val added = repo.profiles.value.single()
        assertEquals("OPENVPN", added.protocol)
        assertEquals("vpn.example.net", added.address)
        assertEquals(1194, added.port)
        assertEquals("OpenVPN · sing-box", added.transportLabel)
        assertTrue(added.source is ProfileSource.SingBox)
        val core = added.toXrayConfigJson()
        assertTrue("it runs through the core's sing-box outbound", core.contains("\"singbox\""))
        assertTrue("the endpoint type is the core's own name", core.contains("\"openvpn-client\""))
    }

    @Test
    fun `credentials asked for at import are stored with the server`() {
        val repo = ConfigRepository(InMemoryKeyValueStore())
        val added = repo.addOvpn(profile, name = "Work", username = "alice", password = "secret").getOrThrow()
        assertEquals("Work", added.name)
        val payload = added.rawPayload().orEmpty()
        assertTrue(payload.contains("\"username\": \"alice\""))
        assertTrue(payload.contains("\"password\": \"secret\""))
    }

    @Test
    fun `a profile whose certificates live in other files is refused with its reason`() {
        val repo = ConfigRepository(InMemoryKeyValueStore())
        val failure = repo.importText("client\nproto udp\nremote vpn.example.net 1194\nca ca.crt\n").exceptionOrNull()
        assertTrue("$failure", failure is dev.cluvex.zedsecure.domain.config.OvpnConfig.UnsupportedException)
        assertEquals(0, repo.profiles.value.size)
    }
}
