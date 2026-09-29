package dev.cluvex.zedsecure.crypto

expect object ZsxCrypto {
    fun seal(request: ZsxSealRequest): ByteArray
    fun peek(bytes: ByteArray): ZsxMetadata
    fun open(bytes: ByteArray, password: String?): String
}
