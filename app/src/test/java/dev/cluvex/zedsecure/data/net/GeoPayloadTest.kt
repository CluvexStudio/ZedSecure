package dev.cluvex.zedsecure.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeoPayloadTest {
    private val ipSb = """{"organization":"Hetzner Online","region":"Bavaria","isp":"Hetzner Online",
        "city":"Nuremberg","asn_organization":"Hetzner Online GmbH","asn":24940,"ip":"203.0.113.7",
        "country":"Germany","country_code":"DE"}"""

    private val ipwhoIs = """{"ip":"203.0.113.7","success":true,"type":"IPv4","country":"Germany",
        "country_code":"DE","city":"Nuremberg",
        "connection":{"asn":24940,"org":"Hetzner Online GmbH","isp":"Hetzner Online GmbH","domain":"hetzner.de"}}"""

    private val ipapiCo = """{"ip":"203.0.113.7","city":"Nuremberg","country":"DE","country_name":"Germany",
        "country_code":"DE","asn":"AS24940","org":"Hetzner Online GmbH"}"""

    private val ifconfigCo = """{"ip":"203.0.113.7","country":"Germany","country_iso":"DE","city":"Nuremberg",
        "asn":"AS24940","asn_org":"Hetzner Online GmbH","user_agent":{"product":"ZedSecure"}}"""

    private val cloudflare = """{"clientIp":"203.0.113.7","asn":24940,"asOrganization":"Hetzner Online GmbH",
        "colo":"FRA","country":"DE","city":"Nuremberg"}"""

    private val ipinfo = """{"ip":"203.0.113.7","hostname":"static.example","city":"Nuremberg","region":"Bavaria",
        "country":"DE","loc":"49.4542,11.0775","org":"AS24940 Hetzner Online GmbH","postal":"90051",
        "timezone":"Europe/Berlin","readme":"https://ipinfo.io/missingauth"}"""

    private val freeipapi = """{"ipVersion":4,"ipAddress":"203.0.113.7","latitude":49.4543,"longitude":11.0746,
        "countryName":"Germany","countryCode":"DE","zipCode":"90455","cityName":"Nuremberg","regionName":"Bavaria",
        "regionCode":null,"continent":"Europe","asn":"24940","asnOrganization":"Hetzner Online GmbH","isProxy":false}"""

    private val geojs = """{"accuracy":20,"asn":24940,"city":"Nuremberg","country":"Germany","country_code":"DE",
        "ip":"203.0.113.7","organization":"AS24940 Hetzner Online GmbH","organization_name":"Hetzner Online GmbH",
        "region":"Bavaria"}"""

    @Test
    fun `the services the exit lookup asks, and other shapes a user may set, read in full`() {
        listOf(ipwhoIs, ipSb, ipapiCo, ipinfo, freeipapi, geojs).forEach { body ->
            val info = GeoPayload.parse(body)
            assertEquals(body, "203.0.113.7", info.ipv4)
            assertEquals(body, "DE", info.countryCode)
            assertEquals(body, "Nuremberg", info.city)
            assertEquals(body, true, info.isp?.startsWith("Hetzner Online"))
        }
        assertEquals("freeipapi names the country in countryName", "Germany", GeoPayload.parse(freeipapi).country)
    }

    @Test
    fun `every provider yields the ISP`() {
        assertEquals("Hetzner Online", GeoPayload.parse(ipSb).isp)
        assertEquals("nested under connection", "Hetzner Online GmbH", GeoPayload.parse(ipwhoIs).isp)
        assertEquals("Hetzner Online GmbH", GeoPayload.parse(ipapiCo).isp)
        assertEquals("named asn_org", "Hetzner Online GmbH", GeoPayload.parse(ifconfigCo).isp)
        assertEquals("Hetzner Online GmbH", GeoPayload.parse(cloudflare).isp)
    }

    @Test
    fun `a country code in the country field never hides the country name`() {
        val ipapi = GeoPayload.parse(ipapiCo)
        assertEquals("Germany", ipapi.country)
        assertEquals("DE", ipapi.countryCode)

        val cf = GeoPayload.parse(cloudflare)
        assertNull("Cloudflare sends only the code", cf.country)
        assertEquals("DE", cf.countryCode)

        assertEquals("DE", GeoPayload.parse(ifconfigCo).countryCode)
    }

    @Test
    fun `an AS number in front of the organisation is dropped`() {
        val ipinfo = """{"ip":"203.0.113.7","city":"Nuremberg","country":"DE","org":"AS24940 Hetzner Online GmbH"}"""
        assertEquals("Hetzner Online GmbH", GeoPayload.parse(ipinfo).isp)
    }

    @Test
    fun `the reported address is filed under its own family`() {
        val v6 = GeoPayload.parse("""{"ip":"2001:db8::7","country":"Germany","country_code":"DE"}""")
        assertNull(v6.ipv4)
        assertEquals("2001:db8::7", v6.ipv6)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a provider that reports failure is a miss`() {
        GeoPayload.parse("""{"success":false,"message":"Reserved range"}""")
    }

    @Test
    fun `a fast but incomplete winner is completed from answers about the same address`() {
        val winner = GeoPayload.parse(cloudflare)
        val completed = GeoPayload.complete(winner, GeoPayload.parse(ipwhoIs))
        assertEquals("Germany", completed.country)
        assertEquals("Hetzner Online GmbH", completed.isp)
        assertEquals("the winner's own values stay", "Nuremberg", completed.city)

        val noIsp = GeoPayload.parse(ifconfigCo).copy(isp = null)
        assertEquals("Hetzner Online", GeoPayload.complete(noIsp, GeoPayload.parse(ipSb)).isp)
    }

    @Test
    fun `answers about another address, or placing it elsewhere, add nothing`() {
        val winner = GeoPayload.parse(cloudflare).copy(isp = null)
        val otherAddress = GeoPayload.parse(ipSb.replace("203.0.113.7", "198.51.100.9"))
        assertEquals(winner, GeoPayload.complete(winner, otherAddress))

        val noCity = GeoPayload.parse(ipSb).copy(city = null)
        val elsewhere = GeoPayload.parse(
            """{"ip":"203.0.113.7","city":"Amsterdam","country":"Netherlands","country_code":"NL","isp":"X"}""",
        )
        val merged = GeoPayload.complete(noCity, elsewhere)
        assertNull("a city from a database that disagrees on the country is not borrowed", merged.city)
    }

    @Test
    fun `complete means everything the screens show is there`() {
        assertEquals(true, GeoPayload.isComplete(GeoPayload.parse(ipSb)))
        assertEquals(false, GeoPayload.isComplete(GeoPayload.parse(cloudflare)))
    }
}
