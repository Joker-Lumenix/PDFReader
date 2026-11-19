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
     * @param maxPixels the most pixels the picture may have, 0 for no limit.
     *   A bigger picture is scaled down, so the memory of the decoder stays
     *   small - the compressor writes the smaller picture back.
     * @return the decoded image, or null when the data cannot be decoded
     * (unsupported colour space, truncated file, ...).
     */
    fun decodeJpeg(data: ByteArray, maxPixels: Int = 0): DecodedImage?

    /**
     * Builds an image from raw 8 bit RGB samples that arrive row by row.
     *
     * The rows are read one after the other, so a picture of a document never
     * sits in the memory as a whole - the caller only holds one row.
     *
     * @param step the step between two rows that are kept, 2 keeps every
     *   second row - a picture that is too big for the memory is scaled down
     *   that way.
     * @param row the samples of the next row of the picture, null at the end
     * @return the decoded image, or null when the rows are not usable.
     */
    fun decodeRgbRows(
        width: Int,
        height: Int,
        step: Int,
        row: () -> ByteArray?,
    ): DecodedImage?

    /**
     * Encodes an image back to JPEG.
     *
     * @param quality JPEG quality, 1 (smallest) .. 100 (best)
     */
    fun encodeJpeg(image: DecodedImage, quality: Int): ByteArray
}
