package dev.cluvex.zedsecure.domain.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DnsTunnelEngineConfigTest {
    private val key = "0".repeat(63) + "1"

    private fun dnstt(vararg tweak: (DnsTunnelProfile) -> DnsTunnelProfile) =
        tweak.fold(DnsTunnelProfile(DnsTunnelProfile.ENGINE_DNSTT, "t.example.com", key, resolvers = "1.1.1.1")) { p, f -> f(p) }

    private fun vaydns(vararg tweak: (DnsTunnelProfile) -> DnsTunnelProfile) =
        tweak.fold(DnsTunnelProfile(DnsTunnelProfile.ENGINE_VAYDNS, "t.example.com", key, resolvers = "1.1.1.1")) { p, f -> f(p) }

    private val samples: Map<String, Pair<DnsTunnelProfile, DnsTunnelEngineConfig.Via?>> = linkedMapOf(
        "dnstt_default" to (dnstt() to null),
        "dnstt_rotate_authoritative" to (dnstt({
            it.copy(
                resolvers = "1.1.1.1,8.8.8.8", resolverMode = DnsTunnelProfile.MODE_ROUND_ROBIN,
                rrSpreadCount = 2, authoritative = true, dnsPayloadSize = 60,
            )
        }) to null),
        "dnstt_exit_auth" to (dnstt({ it.copy(socksUser = "tunnel", socksPass = "s3cret") }) to null),
        "dnstt_chained" to (dnstt() to DnsTunnelEngineConfig.Via("127.0.0.1:1080")),
        "dnstt_tcp" to (dnstt({ it.copy(dnsTransport = DnsTunnelProfile.TRANSPORT_TCP) }) to null),
        "dnstt_dot" to (dnstt({ it.copy(dnsTransport = DnsTunnelProfile.TRANSPORT_DOT) }) to null),
        "dnstt_doh" to (dnstt({
            it.copy(dnsTransport = DnsTunnelProfile.TRANSPORT_DOH, dohUrl = "https://dns.example/dns-query")
        }) to null),
        "vaydns_default" to (vaydns() to null),
        "vaydns_tuned" to (vaydns({
            it.copy(recordType = "cname", maxQnameLen = 150, rps = 200.0, idleTimeout = 30, keepalive = 5, udpTimeout = 800)
        }) to null),
        "vaydns_dnstt_compat" to (vaydns({ it.copy(dnsttCompat = true) }) to null),
    )

    private fun document(name: String): JsonObject {
        val (profile, via) = samples.getValue(name)
        return Json.parseToJsonElement(DnsTunnelEngineConfig.build(profile, "127.0.0.1:1", profile.dnsAddress(), via)).jsonObject
    }

    private fun JsonObject.str(k: String) = getValue(k).jsonPrimitive.content

    @Test
    fun `every sample is written out for the engine's own parser`() {
        val dir = File("build/zeddns-contract").apply { deleteRecursively(); mkdirs() }
        samples.forEach { (name, sample) ->
            val (profile, via) = sample
            File(dir, "$name.json").writeText(DnsTunnelEngineConfig.build(profile, "127.0.0.1:1", profile.dnsAddress(), via))
        }
        assertEquals(samples.size, dir.listFiles()!!.count { it.name.endsWith(".json") })
    }

    @Test
    fun `a DNSTT profile asks for the dnstt wire and its own options only`() {
        val d = document("dnstt_default")
        assertEquals("dnstt", d.str("wire"))
        assertEquals("100", d.str("payloadLimit"))
        assertEquals("false", d.str("authoritative"))
        assertEquals("all", d.str("strategy"))
        assertEquals(listOf("1.1.1.1:53"), d.getValue("resolvers").jsonArray.map { it.jsonPrimitive.content })
        assertFalse("VayDNS-only keys leak into a DNSTT document", "record" in d || "nameLimit" in d)
    }

    @Test
    fun `a VayDNS profile speaks its own wire unless told to reach a dnstt server`() {
        val plain = document("vaydns_default")
        assertEquals("vaydns", plain.str("wire"))
        assertEquals("txt", plain.str("record"))
        assertFalse("the default name limit would override dnstt's 253", "nameLimit" in plain)
        assertFalse("DNSTT-only keys leak into a VayDNS document", "payloadLimit" in plain || "authoritative" in plain)
        assertEquals("dnstt", document("vaydns_dnstt_compat").str("wire"))
    }

    @Test
    fun `transports, rotation, credentials and the chain all reach the document`() {
        assertEquals("tcp://1.1.1.1:53", document("dnstt_tcp").getValue("resolvers").jsonArray[0].jsonPrimitive.content)
        assertEquals("tls://1.1.1.1:853", document("dnstt_dot").getValue("resolvers").jsonArray[0].jsonPrimitive.content)
        assertEquals("https://dns.example/dns-query", document("dnstt_doh").getValue("resolvers").jsonArray[0].jsonPrimitive.content)

        val rotate = document("dnstt_rotate_authoritative")
        assertEquals("rotate", rotate.str("strategy"))
        assertEquals("2", rotate.str("perQuery"))
        assertEquals(2, rotate.getValue("resolvers").jsonArray.size)

        assertEquals("tunnel", document("dnstt_exit_auth").getValue("exitAuth").jsonObject.str("user"))
        assertEquals("127.0.0.1:1080", document("dnstt_chained").getValue("via").jsonObject.str("address"))

        val tuned = document("vaydns_tuned")
        assertEquals("cname", tuned.str("record"))
        assertEquals("150", tuned.str("nameLimit"))
        assertTrue(tuned.str("queriesPerSecond").toDouble() == 200.0)
    }
}
