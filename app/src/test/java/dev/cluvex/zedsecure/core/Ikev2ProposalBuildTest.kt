package dev.cluvex.zedsecure.core

import android.net.ipsec.ike.SaProposal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Ikev2ProposalBuildTest {
    @Test
    fun `the default IKE proposal is accepted by the platform`() {
        val p = Ikev2Proposals.defaultIke()
        assertTrue("must offer at least one cipher", p.encryptionAlgorithms.isNotEmpty())
        assertTrue("must offer at least one DH group", p.dhGroups.isNotEmpty())
        assertTrue("must offer a PRF", p.pseudorandomFunctions.isNotEmpty())
    }

    @Test
    fun `the default child proposal is accepted by the platform`() {
        assertTrue(Ikev2Proposals.defaultChild().encryptionAlgorithms.isNotEmpty())
    }

    @Test
    fun `the defaults never mix AEAD and normal ciphers`() {
        listOf(
            Ikev2Proposals.defaultIke().encryptionAlgorithms,
            Ikev2Proposals.defaultChild().encryptionAlgorithms,
        ).forEach { algos ->
            val ids = algos.map { it.first }
            val aead = ids.any { it == SaProposal.ENCRYPTION_ALGORITHM_AES_GCM_8 ||
                it == SaProposal.ENCRYPTION_ALGORITHM_AES_GCM_12 ||
                it == SaProposal.ENCRYPTION_ALGORITHM_AES_GCM_16 }
            val normal = ids.any { it == SaProposal.ENCRYPTION_ALGORITHM_AES_CBC ||
                it == SaProposal.ENCRYPTION_ALGORITHM_AES_CTR }
            assertFalse("a proposal may hold AEAD or normal ciphers, never both", aead && normal)
        }
    }

    @Test
    fun `an ordinary strongSwan string still parses`() {
        val p = Ikev2Proposals.parseIke("aes256-sha256-modp2048")
        assertNotNull("a standard strongSwan proposal must be honoured", p)
        assertTrue(p!!.dhGroups.contains(SaProposal.DH_GROUP_2048_BIT_MODP))

        assertTrue("a PRF must be derived when the string names none", p.pseudorandomFunctions.isNotEmpty())
    }

    @Test
    fun `an ecp group is refused rather than silently swapped for curve25519`() {
        assertNull(Ikev2Proposals.parseIke("aes256-sha256-ecp256"))
        assertNotNull("curve25519 itself is expressible", Ikev2Proposals.parseIke("aes256-sha256-curve25519"))
    }

    @Test
    fun `a blank or unparseable spec yields null so the caller substitutes the default`() {
        assertNull(Ikev2Proposals.parseIke(""))
        assertNull(Ikev2Proposals.parseIke("not-a-real-algorithm"))

        assertNull(Ikev2Proposals.parseIke("aes256"))
    }

    @Test
    fun `a child proposal needs no DH group`() {
        assertNotNull(Ikev2Proposals.parseChild("aes256-sha256"))
    }

    @Test
    fun `the controller's choice is non-null for every input`() {
        listOf("", "garbage", "aes256-sha256-ecp256", "aes256-sha256-modp2048").forEach { spec ->
            val chosen = Ikev2Proposals.parseIke(spec) ?: Ikev2Proposals.defaultIke()
            assertNotNull("`$spec` must still yield a proposal", chosen)
            assertTrue(chosen.encryptionAlgorithms.isNotEmpty())
        }
    }

    @Test
    fun `an IkeTunnelConnectionParams profile still accepts MTU and metering`() {
        val ike = android.net.ipsec.ike.IkeSessionParams.Builder()
            .setServerHostname("1.2.3.4")
            .setLocalIdentification(android.net.ipsec.ike.IkeFqdnIdentification("c.example.com"))
            .setRemoteIdentification(android.net.ipsec.ike.IkeFqdnIdentification("s.example.com"))
            .setAuthPsk("secret".toByteArray())
            .addIkeSaProposal(Ikev2Proposals.defaultIke())
            .build()
        val child = android.net.ipsec.ike.TunnelModeChildSessionParams.Builder()
            .addChildSaProposal(Ikev2Proposals.defaultChild())
            .addInternalAddressRequest(android.system.OsConstants.AF_INET)
            .build()
        val profile = android.net.Ikev2VpnProfile.Builder(
            android.net.ipsec.ike.IkeTunnelConnectionParams(ike, child),
        ).setMaxMtu(1280).setMetered(false).build()
        assertEquals(1280, profile.maxMtu)
    }

    @Test
    fun `the IKEv2 profile MTU default fits a sub-1500 carrier link`() {
        val mtu = dev.cluvex.zedsecure.domain.config.Ikev2Profile(server = "s").mtu
        assertTrue("default $mtu leaves no room for ESP overhead on a 1410 link", mtu <= 1400)
        assertTrue("must not go below the IPv6 minimum", mtu >= 1280)
    }

    @Test
    fun `aes-gcm parses on its own without an integrity algorithm`() {
        val p = Ikev2Proposals.parseIke("aes256gcm16-prfsha256-modp2048")
        assertNotNull(p)
        assertEquals(0, p!!.integrityAlgorithms.size)
    }
}
