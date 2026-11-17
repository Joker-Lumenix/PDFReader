package com.peng.pdfreader.core.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

/**
 * Android implementation of the image codec used by the PDF compressor.
 *
 * The framework bitmap pipeline does the heavy lifting: a JPEG is decoded
 * straight into a bitmap and written back as JPEG, so no pixel copy is needed
 * for the common case.
 */
object AndroidImageCodec : ImageCodec {

    override fun decodeJpeg(data: ByteArray): DecodedImage? {
        if (data.isEmpty()) return null
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        }
        val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size, options) ?: return null
        return DecodedImage(bitmap.width, bitmap.height, bitmap)
    }

    override fun decodeRgb(width: Int, height: Int, rgb: ByteArray): DecodedImage? {
        if (width <= 0 || height <= 0 || rgb.size != width * height * 3) return null
        val pixels = IntArray(width * height)
        for (index in pixels.indices) {
            val red = rgb[index * 3].toInt() and 0xFF
            val green = rgb[index * 3 + 1].toInt() and 0xFF
            val blue = rgb[index * 3 + 2].toInt() and 0xFF
            pixels[index] = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
        }
        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        return DecodedImage(width, height, bitmap)
    }

    override fun encodeJpeg(image: DecodedImage, quality: Int): ByteArray {
        val bitmap = image.payload as? Bitmap ?: return ByteArray(0)
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), output)
        return output.toByteArray()
    }
}
