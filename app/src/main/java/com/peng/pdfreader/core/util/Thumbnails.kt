package com.peng.pdfreader.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import java.io.File

/**
 * Small thumbnails for the document lists.
 *
 * Only raster documents are decoded here, the bitmap is scaled down to the size
 * the list needs so a long list stays cheap.
 */
object Thumbnails {

    private val cache = object : LruCache<String, Bitmap>(48) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    /**
     * Loads the thumbnail of an image file.
     *
     * @param file the image file
     * @param maxSize the wanted edge length in pixels
     * @return the bitmap wrapped in a drawable, or null when it cannot be read.
     */
    fun load(context: Context, file: File, maxSize: Int = 160): Drawable? {
        if (!file.exists() || file.length() == 0L) return null
        val key = "${file.absolutePath}@$maxSize"
        cache.get(key)?.let { return BitmapDrawable(context.resources, it) }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > maxSize || bounds.outHeight / sample > maxSize) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        cache.put(key, bitmap)
        return BitmapDrawable(context.resources, bitmap)
    }
}
