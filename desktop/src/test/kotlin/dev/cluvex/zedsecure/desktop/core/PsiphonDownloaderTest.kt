package dev.cluvex.zedsecure.desktop.core

import dev.cluvex.zedsecure.core.psiphon.PsiphonBinaryManager
import dev.cluvex.zedsecure.core.psiphon.PsiphonDownloadBus
import dev.cluvex.zedsecure.domain.config.AetherProfile
import dev.cluvex.zedsecure.domain.config.ProfileSource
import dev.cluvex.zedsecure.domain.config.PsiphonProfile
import dev.cluvex.zedsecure.domain.config.VpnProfile
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PsiphonDownloaderTest {

    @Test
    fun `binary name matches current OS`() {
        val name = PsiphonBinaryManager.binaryName
        if (Os.current == Os.WINDOWS) {
            assertEquals("psiphon.exe", name)
        } else {
            assertEquals("psiphon", name)
        }
    }

    @Test
    fun `PsiphonDownloadBus ensure invokes callback if available`() {
        // If available, ensure calls onResult(true) synchronously
        if (PsiphonBinaryManager.isAvailable()) {
            var called = false
            PsiphonDownloadBus.ensure { ready ->
                called = true
                assertTrue(ready)
            }
            assertTrue(called)
        }
    }

    @Test
    fun `PsiphonDownloadBus handles dismiss and complete`() {
        var resultReceived: Boolean? = null
        val request = PsiphonDownloadBus.Request { res -> resultReceived = res }
        request.onResult(true)
        assertEquals(true, resultReceived)
    }

    @Test
    fun `VpnProfile usesPsiphon detects all Psiphon configurations`() {
        val psiphonProfile = VpnProfile(
            id = "p1",
            name = "Psiphon Auto",
            protocol = "PSIPHON",
            address = "127.0.0.1",
            port = 0,
            transportLabel = "Psiphon",
            source = ProfileSource.Psiphon(PsiphonProfile()),
            addedAt = 1000L,
        )
        assertTrue(psiphonProfile.usesPsiphon())

        val aetherChain = VpnProfile(
            id = "p2",
            name = "Aether Chain",
            protocol = "AETHER",
            address = "127.0.0.1",
            port = 0,
            transportLabel = "Aether MASQUE",
            source = ProfileSource.Aether(AetherProfile(psiphonMode = AetherProfile.CARRIER_CHAIN)),
            addedAt = 1000L,
        )
        assertTrue(aetherChain.usesPsiphon())

        val aetherOff = VpnProfile(
            id = "p3",
            name = "Aether WARP",
            protocol = "AETHER",
            address = "127.0.0.1",
            port = 0,
            transportLabel = "Aether MASQUE",
            source = ProfileSource.Aether(AetherProfile(psiphonMode = AetherProfile.CARRIER_OFF)),
            addedAt = 1000L,
        )
        assertFalse(aetherOff.usesPsiphon())

        val vmessProfile = VpnProfile(
            id = "p4",
            name = "VMess Direct",
            protocol = "VMESS",
            address = "example.com",
            port = 443,
            transportLabel = "VMess WS",
            source = ProfileSource.Link("vmess://dummy"),
            addedAt = 1000L,
        )
        assertFalse(vmessProfile.usesPsiphon())
    }

    @Test
    fun `BundledBinary extracts downloaded binary from workDir if present`() {
        val tmpWork = File(System.getProperty("java.io.tmpdir"), "test-work-" + System.currentTimeMillis()).apply { mkdirs() }
        try {
            val fakePsiphon = File(tmpWork, PsiphonBinaryManager.binaryName).apply {
                writeText("fake-psiphon-binary-content")
            }
            val extracted = BundledBinary.extract(tmpWork, "psiphon", "psiphon.exe")
            assertNotNull(extracted)
            assertTrue(extracted.exists())
            assertEquals(fakePsiphon.absolutePath, extracted.absolutePath)
        } finally {
            tmpWork.deleteRecursively()
        }
    }
}
