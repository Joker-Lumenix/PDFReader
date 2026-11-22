package java.awt.image

import java.awt.Image

/**
 * The images of Java, as a stand in for Android.
 *
 * The reader of POI knows the pictures of a legacy document as the images of
 * Java. Android does not know them, so the app carries the few types that
 * the reader of POI needs.
 *
 * The classes of this file are a part of the compatibility layer of the app,
 * the app itself does not use them.
 */

/** An image of Java that is drawn somewhere. */
interface RenderedImage {
    fun getWidth(): Int

    fun getHeight(): Int
}

/** The colours of the pixels of an image of Java. */
open class ColorModel {
    open fun getNumComponents(): Int = 3

    open fun getPixelSize(): Int = 24
}

/** The pixels of an image of Java. */
abstract class Raster {
    abstract fun getWidth(): Int

    abstract fun getHeight(): Int
}

/** The pixels of an image of Java that can be changed. */
abstract class WritableRaster : Raster()

/** The image of Java that is kept in the memory. */
class BufferedImage : Image, RenderedImage {

    private val imageWidth: Int
    private val imageHeight: Int

    constructor(width: Int, height: Int, type: Int) {
        imageWidth = width
        imageHeight = height
    }

    override fun getWidth(): Int = imageWidth

    override fun getHeight(): Int = imageHeight

    override fun getGraphics(): java.awt.Graphics? = null

    fun getRaster(): Raster = object : Raster() {
        override fun getWidth(): Int = imageWidth

        override fun getHeight(): Int = imageHeight
    }

    fun createGraphics(): java.awt.Graphics2D? = null

    fun getColorModel(): ColorModel = ColorModel()

    companion object {
        const val TYPE_INT_RGB = 1
        const val TYPE_INT_ARGB = 2
        const val TYPE_INT_ARGB_PRE = 3
        const val TYPE_3BYTE_BGR = 4
        const val TYPE_4BYTE_ABGR = 6
        const val TYPE_BYTE_GRAY = 10
        const val TYPE_USHORT_565_RGB = 13
    }
}
