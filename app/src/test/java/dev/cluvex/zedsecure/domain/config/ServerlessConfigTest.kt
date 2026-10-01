package dev.cluvex.zedsecure.domain.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerlessConfigTest {
    private val serverless = """
        {
          "remarks": "Serverless",
          "version": { "min": "26.6.27" },
          "log": { "loglevel": "warning", "dnsLog": false, "access": "none" },
          "policy": { "levels": {
            "0": { "uplinkOnly": 0, "downlinkOnly": 0 },
            "12": { "uplinkOnly": 0, "downlinkOnly": 0, "connIdle": 12 } } },
          "dns": { "servers": [
            { "address": "fakedns", "domains": ["domain:ir", "geosite:github"] },
            { "tag": "no-filter-dns", "address": "https://cloudflare-dns.com/dns-query", "finalQuery": true },
            { "tag": "domestic-dns", "address": "localhost", "domains": ["domain:ir"], "finalQuery": true } ] },
          "inbounds": [ {
            "tag": "mixed-in", "port": 10808, "protocol": "mixed",
            "sniffing": { "enabled": true, "destOverride": ["fakedns", "tls", "http", "quic"], "routeOnly": false },
            "settings": { "udp": true, "ip": "127.0.0.1" } } ],
          "outbounds": [
            { "tag": "block", "protocol": "block" },
            { "tag": "tcp-direct", "protocol": "direct" },
            { "tag": "udp-direct", "protocol": "direct" },
            { "tag": "dns-out", "protocol": "dns", "settings": { "userLevel": 12 } },
            { "tag": "tcp-fragment-tls", "protocol": "direct", "streamSettings": { "finalmask": { "tcp": [
              { "type": "fragment", "settings": { "packets": "tlshello", "lengths": ["0", "104", "1"] } } ] } } }
          ],
          "routing": { "domainStrategy": "IPOnDemand", "rules": [
            { "outboundTag": "block", "domain": ["geosite:category-ads-all"] },
            { "outboundTag": "tcp-direct", "inboundTag": ["domestic-dns"], "network": "tcp" },
            { "outboundTag": "tcp-fragment-tls", "inboundTag": ["no-filter-dns"] },
            { "outboundTag": "dns-out", "port": 53 },
            { "outboundTag": "block", "ip": ["10.10.34.0/24"] },
            { "outboundTag": "tcp-direct", "network": "tcp", "ip": ["geoip:private", "geoip:ir"] },
            { "outboundTag": "block", "network": "udp", "port": "443", "ip": ["0.0.0.0/0", "::/0"] },
            { "outboundTag": "tcp-fragment-tls", "network": "tcp", "protocol": ["tls"], "ip": ["0.0.0.0/0", "::/0"] },
            { "outboundTag": "tcp-direct", "network": "tcp", "ip": ["0.0.0.0/0", "::/0"] },
            { "outboundTag": "block", "port": "0-65535" } ] }
        }
    """.trimIndent()

    private fun parse(json: String) = Json.parseToJsonElement(json).jsonObject

    private fun tags(root: JsonObject, key: String) =
        root[key]!!.jsonArray.map { it.jsonObject["tag"]?.jsonPrimitive?.content }

    @Test
    fun `the config's own mixed inbound carries the tunnel, so FakeDNS sniffing stays in the path`() {
        val live = parse(XrayJsonBuilder.normalizeRawJson(serverless, socksPort = 10808))
        val inbounds = live["inbounds"] as JsonArray

        assertEquals("no second listener on the same port", 1, inbounds.size)
        val inbound = inbounds.single().jsonObject
        assertEquals("mixed-in", inbound["tag"]!!.jsonPrimitive.content)
        assertEquals("127.0.0.1", inbound["listen"]!!.jsonPrimitive.content)
        assertEquals("10808", inbound["port"]!!.jsonPrimitive.content)
        val destOverride = inbound["sniffing"]!!.jsonObject["destOverride"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertTrue(destOverride.contains("fakedns"))
    }

    @Test
    fun `the config's policy levels survive next to the traffic counters`() {
        val policy = parse(XrayJsonBuilder.normalizeRawJson(serverless))["policy"]!!.jsonObject

        assertEquals("12", policy["levels"]!!.jsonObject["12"]!!.jsonObject["connIdle"]!!.jsonPrimitive.content)
        assertEquals("0", policy["levels"]!!.jsonObject["0"]!!.jsonObject["uplinkOnly"]!!.jsonPrimitive.content)
        assertEquals("true", policy["system"]!!.jsonObject["statsOutboundUplink"]!!.jsonPrimitive.content)
    }

    @Test
    fun `the ping goes out the way a TLS connection would, not through the block outbound`() {
        val probe = parse(XrayJsonBuilder.normalizeRawJson(serverless, forSpeedtest = true))

        assertEquals("tcp-fragment-tls", tags(probe, "outbounds").first())
        assertEquals(5, tags(probe, "outbounds").size)
    }

    @Test
    fun `a config that starts with its proxy keeps its order for the ping`() {
        val proxyFirst = """
            {
              "outbounds": [
                { "tag": "proxy", "protocol": "vless", "settings": { "vnext": [ { "address": "a.example", "port": 443 } ] } },
                { "tag": "direct", "protocol": "freedom" }
              ],
              "routing": { "rules": [ { "outboundTag": "direct", "network": "tcp" } ] }
            }
        """.trimIndent()
        val probe = parse(XrayJsonBuilder.normalizeRawJson(proxyFirst, forSpeedtest = true))

        assertEquals(listOf("proxy", "direct"), tags(probe, "outbounds"))
    }

    @Test
    fun `a balancer in front of the TLS route leaves the order alone`() {
        val balanced = """
            {
              "outbounds": [
                { "tag": "direct", "protocol": "freedom" },
                { "tag": "a", "protocol": "vless", "settings": { "vnext": [ { "address": "a.example", "port": 443 } ] } }
              ],
              "routing": { "balancers": [ { "tag": "b", "selector": ["a"] } ],
                "rules": [ { "balancerTag": "b", "network": "tcp" }, { "outboundTag": "a", "network": "tcp" } ] }
            }
        """.trimIndent()
        val probe = parse(XrayJsonBuilder.normalizeRawJson(balanced, forSpeedtest = true))

        assertEquals(listOf("direct", "a"), tags(probe, "outbounds"))
    }
}
