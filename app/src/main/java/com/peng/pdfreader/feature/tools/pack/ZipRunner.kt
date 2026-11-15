package com.peng.pdfreader.feature.tools.pack

import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.peng.pdfreader.R
import com.peng.pdfreader.data.model.DocType
import com.peng.pdfreader.data.model.Document
import com.peng.pdfreader.data.repository.DocumentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Packs a sandbox document into a zip archive.
 *
 * The archive is written into the sandbox and registered as a new document, so
 * it shows up in the file list like every other result of the tool flows.
 */
object ZipRunner {

    /** Packs the given document into a zip archive next to it. */
    fun pack(fragment: Fragment, document: Document) {
        val context = fragment.requireContext().applicationContext
        val repository = DocumentRepository.get(context)
        val source = File(document.path)

        fragment.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val outDir = File(context.cacheDir, "tool-output").apply { mkdirs() }
                val out = File(outDir, "${document.name.substringBeforeLast('.')}.zip")
                runCatching {
                    ZipOutputStream(out.outputStream().buffered()).use { zip ->
                        zip.putNextEntry(ZipEntry(document.name))
                        source.inputStream().buffered().use { input ->
                            input.copyTo(zip)
                        }
                        zip.closeEntry()
                    }
                    out
                }
            }
            result.fold(
                onSuccess = { file ->
                    val stored = repository.addExisting(file.name, DocType.OTHER, file)
                    Toast.makeText(
                        context,
                        context.getString(R.string.tools_zip_done, stored.name),
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                onFailure = { error ->
                    Toast.makeText(
                        context,
                        context.getString(R.string.tools_failed, error.message.orEmpty()),
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            )
        }
    }
}
