package dev.cluvex.zedsecure.domain.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class SubscriptionHeadersTest {
    @Test
    fun `standard userinfo`() {
        val info = SubscriptionHeaders.parseUserInfo("upload=455727941; download=6174315083; total=1073741824000; expire=1671815872")
        assertEquals(455727941L, info.upload)
        assertEquals(6174315083L, info.download)
        assertEquals(1073741824000L, info.total)
        assertEquals(1671815872000L, info.expireAt)
    }

    @Test
    fun `zero total is unlimited and zero expire is never`() {
        val info = SubscriptionHeaders.parseUserInfo("upload=0; download=10; total=0; expire=0")
        assertEquals(0L, info.upload)
        assertEquals(10L, info.download)
        assertNull(info.total)
        assertNull(info.expireAt)
    }

    @Test
    fun `loose formatting is tolerated`() {
        val info = SubscriptionHeaders.parseUserInfo(" Download = 1.5E9 ;UPLOAD=2,  total=3e9;junk; expire = abc")
        assertEquals(2L, info.upload)
        assertEquals(1_500_000_000L, info.download)
        assertEquals(3_000_000_000L, info.total)
        assertNull("an unparseable expiry is simply not reported", info.expireAt)
    }

    @Test
    fun `an expiry already in milliseconds is not multiplied again`() {
        assertEquals(1735689600000L, SubscriptionHeaders.parseUserInfo("expire=1735689600000").expireAt)
    }

    @Test
    fun `header names match case-insensitively`() {
        val meta = SubscriptionHeaders.parse(
            mapOf(
                "Subscription-Userinfo" to "upload=1; download=2; total=10",
                "Profile-Update-Interval" to "6",
                "SUPPORT-URL" to "https://t.me/provider",
                "profile-web-page-url" to "https://panel.example/me",
            ),
        )
        assertEquals(3L, (meta.upload ?: 0) + (meta.download ?: 0))
        assertEquals(10L, meta.total)
        assertEquals(6, meta.updateIntervalHours)
        assertEquals("https://t.me/provider", meta.supportUrl)
        assertEquals("https://panel.example/me", meta.webPageUrl)
        assertFalse(meta.isEmpty)
    }

    @Test
    fun `only http links are accepted from provider headers`() {
        val meta = SubscriptionHeaders.parse(
            mapOf("support-url" to "javascript:alert(1)", "profile-web-page-url" to "tg://resolve?domain=x"),
        )
        assertNull(meta.supportUrl)
        assertNull(meta.webPageUrl)
        assertTrue(meta.isEmpty)
    }

    @Test
    fun `titles in base64 and plain utf-8 are decoded`() {
        val b64 = Base64.getEncoder().encodeToString("سرویس من".toByteArray())
        assertEquals("سرویس من", SubscriptionHeaders.decodeTitle("base64:$b64"))
        assertEquals("Plain Name", SubscriptionHeaders.decodeTitle("  Plain   Name "))

        val mojibake = String("پرووایدر".toByteArray(Charsets.UTF_8), Charsets.ISO_8859_1)
        assertEquals("پرووایدر", SubscriptionHeaders.decodeTitle(mojibake))
        assertNull(SubscriptionHeaders.decodeTitle("base64:%%%not-base64%%%"))
        assertNull(SubscriptionHeaders.decodeTitle("   "))
    }

    @Test
    fun `interval is clamped to a week and must be positive`() {
        assertEquals(168, SubscriptionHeaders.parse(mapOf("profile-update-interval" to "9999")).updateIntervalHours)
        assertNull(SubscriptionHeaders.parse(mapOf("profile-update-interval" to "0")).updateIntervalHours)
        assertEquals(12, SubscriptionHeaders.parse(mapOf("profile-update-interval" to "12.5")).updateIntervalHours)
    }

    private fun sub(lastUpdated: Long, interval: Int? = null, enabled: Boolean = true) = Subscription(
        id = "s", name = "s", url = "https://sub.example", enabled = enabled,
        lastUpdated = lastUpdated, updateIntervalHours = interval,
    )

    @Test
    fun `a never-updated subscription is due, a fresh one is not`() {
        val now = 1_000_000_000_000L
        assertTrue(SubscriptionSchedule.isDue(sub(0), 12, now))
        assertFalse(SubscriptionSchedule.isDue(sub(now - 3_600_000L), 12, now))
        assertTrue(SubscriptionSchedule.isDue(sub(now - 12 * 3_600_000L), 12, now))
        assertFalse("disabled subscriptions are never refreshed", SubscriptionSchedule.isDue(sub(0, enabled = false), 12, now))
    }

    @Test
    fun `the provider interval wins over the global one`() {
        val now = 1_000_000_000_000L
        val sixHoursAgo = now - 6 * 3_600_000L
        assertTrue(SubscriptionSchedule.isDue(sub(sixHoursAgo, interval = 6), 24, now))
        assertFalse(SubscriptionSchedule.isDue(sub(sixHoursAgo, interval = 48), 1, now))
    }

    @Test
    fun `the job period is the shortest interval any enabled subscription needs`() {
        val subs = listOf(sub(0, interval = 6), sub(0), sub(0, interval = 1, enabled = false))
        assertEquals(6, SubscriptionSchedule.periodHours(subs, 12))
        assertEquals(12, SubscriptionSchedule.periodHours(emptyList(), 12))
        assertEquals(168, SubscriptionSchedule.periodHours(emptyList(), 1000))
    }
}
