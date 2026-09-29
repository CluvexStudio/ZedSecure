package dev.cluvex.zedsecure.domain.config

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
class ZedLinkFieldCoverageTest {
    private val srcDir = File("../shared/src/commonMain/kotlin/dev/cluvex/zedsecure/domain/config")

    private fun fieldsOf(file: String, className: String): List<String> {
        val text = File(srcDir, file).readText()
        val start = text.indexOf("data class $className(")
        require(start >= 0) { "$className not found in $file" }
        var depth = 0
        var i = text.indexOf('(', start)
        val body = StringBuilder()
        while (i < text.length) {
            val c = text[i]
            if (c == '(') depth++
            else if (c == ')') { depth--; if (depth == 0) break }
            if (depth >= 1) body.append(c)
            i++
        }
        return Regex("""^\s*val\s+(\w+)\s*:""", RegexOption.MULTILINE)
            .findAll(body.toString()).map { it.groupValues[1] }.toList()
    }

    private fun payloadJson(source: ProfileSource): String {
        val link = ZedLink.build("n", source)!!
        val body = link.removePrefix(ZedLink.SCHEME)
        return Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
            .decode(body).decodeToString()
    }

    private fun assertAllFieldsPresent(file: String, className: String, source: ProfileSource) {
        val json = payloadJson(source)
        val fields = fieldsOf(file, className)

        assertTrue("$className: no fields parsed — the extractor is broken", fields.size >= 3)
        val missing = fields.filterNot { json.contains("\"$it\"") }
        assertTrue("$className is missing from the share link: $missing", missing.isEmpty())

        assertTrue("the check has no teeth", !json.contains("\"bogusFieldXyz\""))
    }

    @Test
    fun `openconnect carries every field`() =
        assertAllFieldsPresent("OpenConnectProfile.kt", "OpenConnectProfile", ProfileSource.OpenConnect(OpenConnectProfile()))

    @Test
    fun `ikev2 carries every field`() =
        assertAllFieldsPresent("Ikev2Profile.kt", "Ikev2Profile", ProfileSource.Ikev2(Ikev2Profile()))

    @Test
    fun `ssh carries every field`() =
        assertAllFieldsPresent("SshProfile.kt", "SshProfile", ProfileSource.Ssh(SshProfile(host = "h")))

    @Test
    fun `dns tunnel carries every field`() = assertAllFieldsPresent(
        "DnsTunnelProfile.kt", "DnsTunnelProfile",
        ProfileSource.DnsTunnel(DnsTunnelProfile(engine = DnsTunnelProfile.ENGINE_DNSTT, domain = "d", publicKey = "k")),
    )

    @Test
    fun `masterdns carries every field`() =
        assertAllFieldsPresent("MasterDnsProfile.kt", "MasterDnsProfile", ProfileSource.MasterDns(MasterDnsProfile()))

    @Test
    fun `psiphon carries every field`() =
        assertAllFieldsPresent("PsiphonProfile.kt", "PsiphonProfile", ProfileSource.Psiphon(PsiphonProfile()))

    @Test
    fun `a fully populated openconnect profile is byte-for-byte identical after a round trip`() {
        val p = OpenConnectProfile(
            server = "https://vpn.example.com:8443/grp", protocol = OpenConnectProfile.PROTO_PULSE,
            username = "u", password = "p", authgroup = "g", caCertPem = "CA", serverCertSha256 = "sha256:x",
            clientCertPem = "CERT", clientKeyPem = "KEY", clientKeyPassword = "kp", userAgent = "UA",
            reportedOs = "win", tokenMode = OpenConnectProfile.TOKEN_TOTP, tokenSecret = "seed",
            disableDtls = true, clientCertP12Base64 = "p12", sni = "front.example.com", mtu = 1300,
            reconnectTimeoutSec = 42, proxy = "socks5://127.0.0.1:1080", proxyAuth = "basic",
            disableIpv6 = true, formEntries = mapOf("a" to "b"),
        )
        val back = ZedLink.parse(ZedLink.build("n", ProfileSource.OpenConnect(p))!!)!!
        assertTrue("every field must survive", (back.source as ProfileSource.OpenConnect).settings == p)
    }
}
