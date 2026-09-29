package dev.cluvex.zedsecure.domain.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedtestConfigTrimTest {
    private val raw = """
        {
          "log": { "loglevel": "warning" },
          "dns": { "servers": ["1.1.1.1"] },
          "fakedns": [ { "ipPool": "198.18.0.0/15", "poolSize": 65535 } ],
          "routing": { "rules": [ { "type": "field", "domain": ["geosite:cn"], "outboundTag": "direct" } ] },
          "inbounds": [ { "protocol": "socks", "listen": "0.0.0.0", "port": 10808 } ],
          "outbounds": [
            { "protocol": "vless",
              "mux": { "enabled": true, "concurrency": 8 },
              "settings": { "vnext": [ { "address": "a.example", "port": 443 } ] }
            }
          ]
        }
    """.trimIndent()

    private fun probe() = XrayJsonBuilder.normalizeRawJson(raw, forSpeedtest = true)
    private fun live() = XrayJsonBuilder.normalizeRawJson(raw, forSpeedtest = false)

    @Test
    fun `probe config drops what the core throws away`() {
        val c = probe()
        assertFalse("routing", c.contains("\"routing\""))
        assertFalse("dns", c.contains("\"dns\""))
        assertFalse("fakedns", c.contains("\"fakedns\""))
        assertFalse("stats", c.contains("\"stats\""))
        assertFalse("policy", c.contains("\"policy\""))
        assertFalse("mux adds a handshake to the measured number", c.contains("\"mux\""))
        assertTrue("the outbound is the whole point", c.contains("\"vless\""))
        assertTrue("no listeners", c.contains("\"inbounds\":[]"))
    }

    @Test
    fun `probe config is compact and the live one is not`() {
        assertFalse("compact for JNI", probe().contains("\n"))
        assertTrue("readable in the editor", live().contains("\n"))
    }

    @Test
    fun `the live config keeps everything it needs`() {
        val c = live()
        assertTrue("routing", c.contains("\"routing\""))
        assertTrue("dns", c.contains("\"dns\""))
        assertTrue("stats drive the traffic counters", c.contains("\"stats\""))
        assertTrue("mux is the user's choice", c.contains("\"mux\""))

        assertTrue("socks forced to loopback", c.contains("\"listen\": \"127.0.0.1\""))
    }

    @Test
    fun `a geo reference cannot break a probe even with no geo files`() {
        val c = XrayJsonBuilder.normalizeRawJson(raw, forSpeedtest = true, geoAssetsAvailable = false)
        assertFalse(c.contains("geosite"))
        assertEquals(probe(), c)
    }
}
