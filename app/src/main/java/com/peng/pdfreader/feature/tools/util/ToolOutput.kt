package com.peng.pdfreader.feature.tools.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The scratch directory the tools write their results into.
 *
 * The files are temporary: as soon as a tool is done the result is copied
 * into the sandbox of the document repository.
 */
object ToolOutput {

    /** The directory the tools write into. */
    fun dir(context: Context): File =
        File(context.cacheDir, "tool-output").apply { if (!exists()) mkdirs() }

    /** A file inside the scratch directory. */
    fun file(context: Context, name: String): File = File(dir(context), name)

    /** A name that is unique for the current moment. */
    fun uniqueName(prefix: String, suffix: String): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "${prefix}_$stamp$suffix"
    }
}
