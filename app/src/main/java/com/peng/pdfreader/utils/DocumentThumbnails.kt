package com.peng.pdfreader.utils

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.peng.pdfreader.core.util.Thumbnails
import com.peng.pdfreader.data.DocType
import com.peng.pdfreader.data.Document
import com.peng.pdfreader.data.DocumentRepository

/**
 * The thumbnails of the document lists.
 *
 * A raster document shows its own picture, every other type shows the logo of
 * the type with the number of the pages on top of it. The result is cached, so
 * the list is not rebuilt on every scroll.
 */
class DocumentThumbnails(
    private val repository: DocumentRepository,
) {

    private val cache = mutableMapOf<String, Drawable>()

    /** The thumbnail of a document, null when it cannot be built. */
    fun of(context: Context, document: Document): Drawable? {
        if (document.type == DocType.IMAGE) {
            val key = "image_${document.path}"
            val cached = cache[key]
            if (cached != null) return cached
            val picture = Thumbnails.load(context, repository.fileOf(document))
            if (picture != null) {
                cache[key] = picture
                return picture
            }
        }
        val key = "${document.type.name}_${document.pageCount}"
        val cached = cache[key]
        if (cached != null) return cached
        val logo = ContextCompat.getDrawable(context, document.type.logoRes) ?: return null
        val drawable = if (document.pageCount > 0) {
            PageCountDrawable(logo, document.pageCount, context)
        } else {
            logo
        }
        cache[key] = drawable
        return drawable
    }
}
