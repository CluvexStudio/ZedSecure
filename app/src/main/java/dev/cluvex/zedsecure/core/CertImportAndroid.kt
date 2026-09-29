package dev.cluvex.zedsecure.core

import android.os.Build
import dev.cluvex.zedsecure.core.AppLog as Log
import dev.cluvex.zedsecure.ui.servers.CertFile
import dev.cluvex.zedsecure.ui.servers.CertImport
import dev.cluvex.zedsecure.ui.servers.CertInstallResult
import dev.cluvex.zedsecure.ui.servers.CertKind
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

object CertImportAndroid {
    private const val TAG = "CertImport"

    var installer: (suspend (bytes: ByteArray, suggestedName: String) -> CertInstallResult)? = null

    fun install() {
        CertImport.installToKeyChain = { bytes, name ->
            installer?.invoke(bytes, name) ?: CertInstallResult.Unsupported
        }
        CertImport.inspect = { bytes, name -> inspect(bytes, name) }

        CertImport.canInstallCaCert = { Build.VERSION.SDK_INT < Build.VERSION_CODES.R }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun inspect(bytes: ByteArray, fileName: String): CertFile {
        if (bytes.isEmpty()) return CertFile(CertKind.Unknown, fileName, fileName)

        val head = bytes.take(2048).toByteArray().decodeToString()

        if (head.contains("PRIVATE KEY-----") && !head.contains("BEGIN CERTIFICATE")) {
            return CertFile(CertKind.PrivateKey, fileName, fileName)
        }

        readCertificate(bytes)?.let { cert ->
            return CertFile(
                kind = CertKind.Certificate,
                name = fileName,
                description = subjectLabel(cert) ?: fileName,
                pem = toPem(cert),
            )
        }

        val looksDer = bytes[0] == 0x30.toByte()
        val p12Extension = fileName.substringAfterLast('.', "").lowercase() in setOf("p12", "pfx")
        if (looksDer || p12Extension) {
            return CertFile(CertKind.Pkcs12, fileName, fileName)
        }

        return CertFile(CertKind.Unknown, fileName, fileName)
    }

    private fun readCertificate(bytes: ByteArray): X509Certificate? = runCatching {
        CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate
    }.getOrNull()

    private fun subjectLabel(cert: X509Certificate): String? {
        val dn = cert.subjectX500Principal.name.takeIf { it.isNotBlank() } ?: return null
        val cn = dn.split(',')
            .map { it.trim() }
            .firstOrNull { it.startsWith("CN=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.trim()
        return cn?.takeIf { it.isNotEmpty() } ?: dn
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun toPem(cert: X509Certificate): String? = runCatching {
        val b64 = Base64.encode(cert.encoded)
        buildString {
            append("-----BEGIN CERTIFICATE-----\n")
            b64.chunked(64).forEach { append(it).append('\n') }
            append("-----END CERTIFICATE-----\n")
        }
    }.onFailure { Log.e(TAG, "PEM encode failed", it) }.getOrNull()
}
