package dev.cluvex.zedsecure.ui.servers

object CertImport {
    var installToKeyChain: (suspend (bytes: ByteArray, suggestedName: String) -> CertInstallResult)? = null

    var inspect: ((bytes: ByteArray, fileName: String) -> CertFile)? = null

    var canInstallCaCert: (() -> Boolean)? = null
}

enum class CertKind {
    Pkcs12,

    Certificate,

    PrivateKey,

    Unknown,
}

data class CertFile(
    val kind: CertKind,
    val name: String,
    val description: String,

    val pem: String? = null,
)

enum class CertInstallResult {
    Installed,

    Cancelled,

    Unsupported,
}
