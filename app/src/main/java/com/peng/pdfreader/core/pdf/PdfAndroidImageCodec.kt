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

    override fun decodeJpeg(data: ByteArray, maxPixels: Int): DecodedImage? {
        if (data.isEmpty()) return null
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        }
        // A picture that is too big is scaled down while it is decoded, so the
        // memory of the decoder stays small.
        if (maxPixels > 0) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
            val pixels = bounds.outWidth.toLong() * bounds.outHeight.toLong()
            if (pixels > maxPixels) {
                var sample = 1
                while (pixels / (sample.toLong() * sample.toLong()) > maxPixels) sample *= 2
                options.inSampleSize = sample
            }
        }
        val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size, options) ?: return null
        return DecodedImage(bitmap.width, bitmap.height, bitmap)
    }

    override fun decodeRgbRows(
        width: Int,
        height: Int,
        step: Int,
        row: () -> ByteArray?,
    ): DecodedImage? {
        if (width <= 0 || height <= 0) return null
        val stride = step.coerceAtLeast(1)
        val targetWidth = (width + stride - 1) / stride
        val targetHeight = (height + stride - 1) / stride
        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        // The pixels are written row by row, so the buffer of a whole picture
        // never sits in the memory.
        val pixels = IntArray(targetWidth)
        for (y in 0 until targetHeight) {
            // The rows that are not used are read and dropped.
            var bytes: ByteArray? = null
            repeat(stride) { bytes = row() ?: return null }
            val samples = bytes ?: return null
            if (samples.size < width * 3) return null
            var x = 0
            while (x < targetWidth) {
                val sourceX = (x * stride).coerceAtMost(width - 1)
                val offset = sourceX * 3
                val red = samples[offset].toInt() and 0xFF
                val green = samples[offset + 1].toInt() and 0xFF
                val blue = samples[offset + 2].toInt() and 0xFF
                pixels[x] = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
                x++
            }
            bitmap.setPixels(pixels, 0, targetWidth, 0, y, targetWidth, 1)
        }
        return DecodedImage(targetWidth, targetHeight, bitmap)
    }

    override fun encodeJpeg(image: DecodedImage, quality: Int): ByteArray {
        val bitmap = image.payload as? Bitmap ?: return ByteArray(0)
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), output)
        // The picture is not needed any more, the memory is given back right
        // away - a document with many pictures would fill the heap otherwise.
        bitmap.recycle()
        return output.toByteArray()
    }
}
