package dev.cluvex.zedsecure.platform

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

internal actual fun encodeQr(text: String): QrMatrix? = runCatching {
    if (text.isBlank()) return null

    val hints = mapOf(
        EncodeHintType.CHARACTER_SET to "UTF-8",

        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
        EncodeHintType.MARGIN to 1,
    )
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
    val size = matrix.width
    if (size <= 0 || matrix.height != size) return null
    val dark = BooleanArray(size * size)
    for (y in 0 until size) for (x in 0 until size) dark[y * size + x] = matrix.get(x, y)
    QrMatrix(size, dark)
}.getOrNull()
