package dev.cluvex.zedsecure.qr

import android.graphics.BitmapFactory
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer

object QrImageDecoder {
    fun decode(bytes: ByteArray): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null

        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.recycle()
        val source = RGBLuminanceSource(width, height, pixels)
        val reader = MultiFormatReader().apply {
            setHints(
                mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                    DecodeHintType.TRY_HARDER to true,
                ),
            )
        }
        reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
    }.getOrNull()
}
