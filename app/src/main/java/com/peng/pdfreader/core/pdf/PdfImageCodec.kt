package com.peng.pdfreader.core.pdf

/**
 * A decoded image.
 *
 * [payload] carries the platform specific bitmap. The PDF writer only needs
 * the dimensions and the ability to re-encode the pixels, so the concrete type
 * stays hidden behind this wrapper.
 */
class DecodedImage(
    val width: Int,
    val height: Int,
    val payload: Any,
)

/**
 * Platform bridge for the JPEG codec used by the PDF compressor.
 *
 * The compressor itself is plain Kotlin - only the bitmap handling is platform
 * specific - so the whole recompression pipeline can be exercised off device.
 */
interface ImageCodec {

    /**
     * Decodes JPEG bytes.
     *
     * @return the decoded image, or null when the data cannot be decoded
     * (unsupported colour space, truncated file, ...).
     */
    fun decodeJpeg(data: ByteArray): DecodedImage?

    /**
     * Builds an image from raw 8 bit RGB samples, one byte per channel.
     *
     * @return the decoded image, or null when the buffer is not usable.
     */
    fun decodeRgb(width: Int, height: Int, rgb: ByteArray): DecodedImage?

    /**
     * Encodes an image back to JPEG.
     *
     * @param quality JPEG quality, 1 (smallest) .. 100 (best)
     */
    fun encodeJpeg(image: DecodedImage, quality: Int): ByteArray
}
