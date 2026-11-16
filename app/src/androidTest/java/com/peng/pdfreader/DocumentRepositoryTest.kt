package com.peng.pdfreader

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.peng.pdfreader.data.DocType
import com.peng.pdfreader.data.DocumentRepository
import com.peng.pdfreader.data.FileSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The document sandbox: the samples, the import, the rename and the delete.
 */
@RunWith(AndroidJUnit4::class)
class DocumentRepositoryTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val repository: DocumentRepository
        get() = DocumentRepository.get(context)

    @Test
    fun theSamplesAreInstalledOnTheFirstStart() {
        val documents = repository.all()
        assertTrue("the sandbox should not be empty", documents.isNotEmpty())
        assertTrue(
            "a sample PDF is missing",
            documents.any { it.name.endsWith(".pdf") },
        )
        assertTrue(
            "a sample Word document is missing",
            documents.any { it.name.endsWith(".docx") },
        )
    }

    @Test
    fun everySampleFileExistsInTheSandbox() {
        repository.all().forEach { document ->
            assertTrue("${document.name} is missing", File(document.path).exists())
        }
    }

    @Test
    fun aDocumentCanBeRenamed() {
        val document = repository.all().first()
        val original = document.name
        val renamed = "renamed-${System.currentTimeMillis()}.pdf"

        repository.rename(document, renamed)

        val updated = repository.byId(document.id)
        assertNotNull(updated)
        assertEquals(renamed, updated!!.name)
        assertTrue(File(updated.path).exists())

        repository.rename(updated, original)
    }

    @Test
    fun aDocumentCanBeDeleted() {
        val document = repository.all().first()
        val path = document.path

        repository.delete(document)

        assertFalse(repository.byId(document.id) != null)
        assertFalse(File(path).exists())
    }

    @Test
    fun theQueryFiltersByTypeAndKeyword() {
        val pdfs = repository.query(DocType.PDF, FileSort.MODIFIED, null)
        assertTrue(pdfs.all { it.type == DocType.PDF })

        val keyword = repository.all().first().name.take(4)
        val found = repository.query(null, FileSort.MODIFIED, keyword)
        assertTrue(found.isNotEmpty())
        assertTrue(found.all { it.name.contains(keyword, ignoreCase = true) })
    }
}
