package dev.cluvex.zedsecure.ui.servers

object Ikev2CertBridge {
    var pickClientCertAlias: (suspend () -> String?)? = null

    var readCaCertPem: (suspend (bytes: ByteArray) -> String?)? = null

    var validateProposal: ((spec: String, child: Boolean) -> Boolean)? = null

    var supportsAdvancedIke: (() -> Boolean)? = null
}
