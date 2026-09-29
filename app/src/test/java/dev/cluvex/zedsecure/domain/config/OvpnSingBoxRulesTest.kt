package dev.cluvex.zedsecure.domain.config

import dev.cluvex.zedsecure.data.config.ConfigRepository
import dev.cluvex.zedsecure.data.config.InMemoryKeyValueStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OvpnSingBoxRulesTest {
    private fun endpoint(json: String): JsonObject =
        Json.parseToJsonElement(json).jsonObject["endpoints"]!!.jsonArray.single().jsonObject

    private fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.content

    private val tlsProfile = """
        client
        dev tun
        proto udp
        remote vpn.example.net 1194
        auth-user-pass
        cipher AES-256-CBC
        auth SHA1
        <ca>
        -----BEGIN CERTIFICATE-----
        MIIBcaLine1
        -----END CERTIFICATE-----
        </ca>
    """.trimIndent()

    private val multiRemote = """
        client
        dev tun
        proto udp
        remote a.example.net 1194
        remote b.example.net 443 tcp
        <ca>
        -----BEGIN CERTIFICATE-----
        MIIBcaLine1
        -----END CERTIFICATE-----
        </ca>
    """.trimIndent()

    private val staticKeyProfile = """
        dev tun
        proto udp
        remote sk.example.net 1194
        ifconfig 10.8.0.2 10.8.0.1
        cipher AES-256-CBC
        key-direction 1
        <secret>
        -----BEGIN OpenVPN Static key V1-----
        0123456789abcdef
        -----END OpenVPN Static key V1-----
        </secret>
    """.trimIndent()

    @Test
    fun `TLS mode never sends cipher, which the core refuses outright`() {
        val e = endpoint(OvpnConfig.toSingBoxFragment(tlsProfile))

        assertNull("this single field stopped every import from connecting", e["cipher"])

        assertEquals("AES-256-CBC", e.str("data_ciphers_fallback"))
    }

    @Test
    fun `an explicit data-ciphers list wins over the legacy cipher line`() {
        val profile = tlsProfile + "\ndata-ciphers AES-256-GCM:CHACHA20-POLY1305\n" +
            "data-ciphers-fallback AES-128-GCM\n"
        val e = endpoint(OvpnConfig.toSingBoxFragment(profile))

        assertEquals(
            listOf("AES-256-GCM", "CHACHA20-POLY1305"),
            e["data_ciphers"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals("AES-128-GCM", e.str("data_ciphers_fallback"))
        assertNull(e["cipher"])
    }

    @Test
    fun `TLS mode always carries a tls object, even an empty one`() {
        val bare = """
            client
            dev tun
            remote bare.example.net 1194
            auth-user-pass
        """.trimIndent()

        assertNotNull(endpoint(OvpnConfig.toSingBoxFragment(bare))["tls"])
        assertNotNull(endpoint(OvpnConfig.toSingBoxFragment(tlsProfile))["tls"])
    }

    @Test
    fun `one remote uses server, several use servers, and never both`() {
        val single = endpoint(OvpnConfig.toSingBoxFragment(tlsProfile))
        assertEquals("vpn.example.net", single.str("server"))
        assertNull(single["servers"])

        val many = endpoint(OvpnConfig.toSingBoxFragment(multiRemote))
        assertNull("both together is refused before the first packet", many["server"])
        assertNull(many["server_port"])
        val servers = many["servers"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("a.example.net", "b.example.net"), servers.map { it.str("server") })
        assertEquals(listOf("udp", "tcp"), servers.map { it.str("network") })
    }

    @Test
    fun `static-key mode sends what that mode allows, and nothing else`() {
        val e = endpoint(OvpnConfig.toSingBoxFragment(staticKeyProfile, username = "u", password = "p"))

        assertEquals("static_key", e.str("mode"))
        assertNotNull(e["static_key"])
        assertEquals("client", e.str("key_direction"))

        assertEquals("AES-256-CBC", e.str("cipher"))

        assertEquals(listOf("10.8.0.2/32"), e["address"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("10.8.0.1", e.str("peer_address"))

        assertNull(e["tls"])
        assertNull(e["username"])
        assertNull(e["password"])
        assertNull(e["data_ciphers"])
        assertNull(e["data_ciphers_fallback"])
        assertNull(e["renegotiate_interval"])
        assertNull(e["renegotiate_disabled"])
    }

    @Test
    fun `mssfix zero switches it off instead of disappearing`() {
        val e = endpoint(OvpnConfig.toSingBoxFragment(tlsProfile + "\nmssfix 0\n"))

        assertTrue(e["mss_fix_disabled"]!!.jsonPrimitive.content.toBoolean())
        assertNull(e["mss_fix"])
    }

    @Test
    fun `key_direction stays out of TLS mode, where the wrap carries it instead`() {
        val withAuth = tlsProfile + """

            key-direction 1
            <tls-auth>
            -----BEGIN OpenVPN Static key V1-----
            0123456789abcdef
            -----END OpenVPN Static key V1-----
            </tls-auth>
        """.trimIndent()
        val e = endpoint(OvpnConfig.toSingBoxFragment(withAuth))

        assertNull(e["key_direction"])
        val wrap = e["tls"]!!.jsonObject["control_wrap"]!!.jsonObject
        assertEquals("tls_auth", wrap.str("type"))
        assertEquals("client", wrap.str("direction"))
    }

    @Test
    fun `the endpoint carries no field the core does not define`() {
        val known = setOf(
            "type", "tag", "detour", "server", "server_port", "servers", "network", "remote_random",
            "mode", "address", "peer_address", "peer_address_ipv6", "topology", "username", "password",
            "auth_retry", "static_challenge", "static_challenge_echo", "static_key", "key_direction",
            "tls", "cipher", "data_ciphers", "data_ciphers_fallback", "auth", "mss_fix",
            "mss_fix_disabled", "mss_fix_mode", "fragment", "replay_window", "replay_window_time",
            "compression", "compression_lzo", "allow_compression", "route_no_pull", "pull_filters",
            "routes", "route_gateway", "route_metric", "redirect_gateway", "redirect_gateway_flags",
            "redirect_private", "block_ipv6", "ping_interval", "ping_restart", "ping_restart_disabled",
            "renegotiate_interval", "renegotiate_disabled", "renegotiate_bytes", "renegotiate_packets",
            "tls_timeout", "handshake_window", "explicit_exit_notify", "udp_timeout", "mtu", "name",
            "system", "udp_mapping", "udp_filtering", "udp_nat_max",
        )
        val tlsKnown = setOf(
            "server_name", "server_name_type", "certificate", "certificate_path", "client_certificate",
            "client_certificate_path", "client_key", "client_key_path", "peer_fingerprint", "crl_path",
            "remote_certificate_ku", "remote_certificate_eku", "remote_certificate_tls",
            "certificate_profile", "ns_certificate_type", "version_min", "version_max", "cipher",
            "groups", "control_wrap",
        )
        for (profile in listOf(tlsProfile, multiRemote, staticKeyProfile)) {
            val e = endpoint(OvpnConfig.toSingBoxFragment(profile, username = "u", password = "p"))
            assertTrue("unknown endpoint fields: ${e.keys - known}", (e.keys - known).isEmpty())
            (e["tls"] as? JsonObject)?.let { tls ->
                assertTrue("unknown tls fields: ${tls.keys - tlsKnown}", (tls.keys - tlsKnown).isEmpty())
            }
        }
    }

    @Test
    fun `a profile that used to be refused now passes every rule at once`() {
        val real = """
            # Example VPN - Frankfurt
            client
            dev tun
            proto udp
            remote de1.example.com 1194
            remote de2.example.com 1194
            remote de3.example.com 443 tcp
            remote-random
            resolv-retry infinite
            nobind
            persist-key
            persist-tun
            auth-user-pass
            remote-cert-tls server
            cipher AES-256-CBC
            auth SHA512
            comp-lzo no
            keepalive 10 60
            reneg-sec 0
            key-direction 1
            verb 3
            <ca>
            -----BEGIN CERTIFICATE-----
            MIIBcaLine1
            -----END CERTIFICATE-----
            </ca>
            <tls-auth>
            -----BEGIN OpenVPN Static key V1-----
            0123456789abcdef
            -----END OpenVPN Static key V1-----
            </tls-auth>
        """.trimIndent()
        val e = endpoint(OvpnConfig.toSingBoxFragment(real, username = "bob", password = "secret"))

        assertNull(e["cipher"])
        assertNull(e["server"])
        assertNull(e["key_direction"])
        assertEquals(3, e["servers"]!!.jsonArray.size)
        assertEquals("AES-256-CBC", e.str("data_ciphers_fallback"))
        assertEquals("bob", e.str("username"))
        assertEquals("secret", e.str("password"))
        assertTrue(e["renegotiate_disabled"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("server", e["tls"]!!.jsonObject.str("remote_certificate_tls"))
        assertFalse(e["tls"]!!.jsonObject.keys.isEmpty())
    }

    @Test
    fun `the endpoint type is the one the core registers`() {
        val e = endpoint(OvpnConfig.toSingBoxFragment(tlsProfile))
        assertEquals("openvpn-client", e.str("type"))
        assertEquals("openvpn-client", SingBoxJson.OPENVPN_TYPE)
    }

    @Test
    fun `a profile saved with the old type name is repaired on load`() {
        val store = InMemoryKeyValueStore()
        val fragment = OvpnConfig.toSingBoxFragment(tlsProfile)
            .replace("\"openvpn-client\"", "\"openvpn\"")
        assertTrue("the fixture must look like the broken build's output", fragment.contains("\"openvpn\""))

        ConfigRepository(store).addSingBoxConfig(fragment, name = "old").getOrThrow()

        val reopened = ConfigRepository(store)
        val stored = (reopened.profiles.value.single().source as ProfileSource.SingBoxConfig).json
        assertTrue("the stored fragment is corrected in place", stored.contains("\"openvpn-client\""))
        assertFalse(stored.contains("\"type\": \"openvpn\""))
    }
}
