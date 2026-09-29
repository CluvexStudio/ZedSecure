package dev.cluvex.zedsecure.platform

data class QrMatrix(val size: Int, val dark: BooleanArray) {
    operator fun get(x: Int, y: Int): Boolean = dark[y * size + x]

    override fun equals(other: Any?): Boolean =
        this === other || (other is QrMatrix && size == other.size && dark.contentEquals(other.dark))

    override fun hashCode(): Int = 31 * size + dark.contentHashCode()
}

internal expect fun encodeQr(text: String): QrMatrix?
