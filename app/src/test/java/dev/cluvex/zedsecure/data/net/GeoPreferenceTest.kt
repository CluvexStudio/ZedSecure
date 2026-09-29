package dev.cluvex.zedsecure.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeoPreferenceTest {
    private fun at(cc: String) = NetworkInfo(ipv4 = "203.0.113.7", countryCode = cc)

    @Test
    fun `the endpoints follow Hiddify's order, v2rayNG's own among them`() {
        assertEquals(
            listOf("https://ipwho.is/", "https://api.ip.sb/geoip", "https://ipapi.co/json/", "https://ipinfo.io/json"),
            NetworkInfoRepository.GEO_ENDPOINTS,
        )
    }

    @Test
    fun `the first endpoint's answer is shown as soon as it arrives`() {
        assertEquals(0, GeoPreference.choose(mapOf(0 to at("IR"))))
    }

    @Test
    fun `a later endpoint's answer waits while an earlier one may still answer`() {
        assertNull(GeoPreference.choose(mapOf(1 to at("AE"))))
        assertEquals("once it has waited long enough, the best so far", 1, GeoPreference.choose(mapOf(1 to at("AE")), waitedEnough = true))
    }

    @Test
    fun `an earlier endpoint that failed does not hold the answer back`() {
        assertEquals(1, GeoPreference.choose(mapOf(0 to null, 1 to at("DE"))))
        assertEquals(2, GeoPreference.choose(mapOf(0 to null, 1 to NetworkInfo(error = "no country"), 2 to at("DE"))))
    }

    @Test
    fun `an earlier answer wins over a later one that came first`() {
        assertEquals(0, GeoPreference.choose(mapOf(2 to at("AE"), 0 to at("IR"))))
    }

    @Test
    fun `nothing that names a place, nothing chosen`() {
        assertNull(GeoPreference.choose(mapOf(0 to null, 1 to NetworkInfo()), waitedEnough = true))
    }
}
