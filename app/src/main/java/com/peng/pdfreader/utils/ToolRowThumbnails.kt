package com.peng.pdfreader.utils

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import com.peng.pdfreader.core.pdf.PdfEngine
import com.peng.pdfreader.core.widget.FileListItemView
import com.peng.pdfreader.data.Document
import com.peng.pdfreader.data.DocumentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The thumbnails of the document rows of the merge flow.
 *
 * The design of the merge shows the first page of the document with the number
 * of the pages on top of it - not the logo of the type the other lists use.
 * The page is rendered in the background, the row carries the logo of the type
 * until the picture is there.
 */
class ToolRowThumbnails(
    private val context: Context,
    private val repository: DocumentRepository,
    private val scope: CoroutineScope,
) {

    /** The pictures that were rendered already. */
    private val cache = object : LruCache<String, Drawable>(CACHE_SIZE) {
        override fun sizeOf(key: String, value: Drawable): Int = 1
    }

    /** Puts the picture of the first page of the document into the row. */
    fun load(document: Document, row: FileListItemView) {
        // The page of an encrypted document cannot be rendered, the row keeps
        // the blurred page with the lock badge of the design.
        if (document.isEncrypted) return
        val cached = cache.get(document.id)
        if (cached != null) {
            row.setThumbnail(cached)
            return
        }
        scope.launch {
            val drawable = withContext(Dispatchers.IO) { render(document) } ?: return@launch
            cache.put(document.id, drawable)
            row.setThumbnail(drawable)
        }
    }

    /** Renders the first page of the document with the page count on top. */
    private fun render(document: Document): Drawable? {
        val file = repository.fileOf(document)
        val bitmap = runCatching { PdfEngine.renderThumbnail(file, 0, THUMBNAIL_PX) }
            .getOrNull() ?: return null
        val pageCount = if (document.pageCount > 0) {
            document.pageCount
        } else {
            runCatching { PdfEngine.pageCount(file) }.getOrDefault(0)
        }
        val base = BitmapDrawable(context.resources, bitmap)
        return PageCountDrawable(base, pageCount, context)
    }

    private companion object {
        /** The width of the rendered page in pixels. */
        const val THUMBNAIL_PX = 192

        /** How many pictures the cache keeps. */
        const val CACHE_SIZE = 24
    }
}
