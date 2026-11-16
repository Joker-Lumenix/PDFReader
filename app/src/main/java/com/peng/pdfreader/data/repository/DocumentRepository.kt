package com.peng.pdfreader.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.peng.pdfreader.appcore.util.Logger
import com.peng.pdfreader.data.SampleDocuments
import com.peng.pdfreader.data.model.DocType
import com.peng.pdfreader.data.model.Document
import com.peng.pdfreader.data.prefs.FileSort
import com.peng.pdfreader.data.storage.SharedStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * The single source of truth for the on-device document sandbox.
 *
 * Every file lives in the app private sandbox (filesDir/documents); nothing is
 * ever uploaded and no external storage is touched. Metadata (favourites,
 * reading progress, annotations, bookmarks) is persisted as JSON inside the
 * sandbox so the app keeps working with zero network access.
 */
class DocumentRepository private constructor(private val appContext: Context) {

    private val documents = mutableListOf<Document>()
    private val annotations = mutableMapOf<String, MutableList<Annotation>>()
    private val bookmarks = mutableMapOf<String, MutableList<Bookmark>>()

    /** When the shared storage was scanned the last time. */
    private var lastSharedSyncAt = 0L

    lateinit var sandboxDir: File
        private set
    lateinit var cacheDir: File
        private set
    private lateinit var stateFile: File

    // ---------------------------------------------------------------- lifecycle

    fun init(context: Context) {
        sandboxDir = File(context.filesDir, "documents").apply { mkdirs() }
        cacheDir = File(context.cacheDir, "thumbs").apply { mkdirs() }
        stateFile = File(context.filesDir, "library.json")
        loadState()
        installSamples(context)
    }

    /**
     * Puts the bundled samples into the sandbox on the first start so that every
     * preview has something to show.
     */
    private fun installSamples(context: Context) {
        val added = SampleDocuments.install(context, sandboxDir).toMutableList()
        SampleDocuments.createDemoPdf(sandboxDir)?.let { added += it }
        if (added.isEmpty()) return
        added.forEach { file ->
            if (documents.any { it.name == file.name }) return@forEach
            val now = System.currentTimeMillis()
            documents.add(
                Document(
                    id = UUID.randomUUID().toString(),
                    name = file.name,
                    type = SampleDocuments.typeOf(file.name),
                    path = file.absolutePath,
                    sizeBytes = file.length(),
                    createdAt = now,
                    modifiedAt = now,
                ),
            )
        }
        persist()
    }

    // ---------------------------------------------------------------- documents

    /** All documents, newest first. */
    fun all(): List<Document> = documents.sortedByDescending { it.modifiedAt }

    fun byId(id: String): Document? = documents.firstOrNull { it.id == id }

    fun fileOf(doc: Document): File = File(doc.path)

    /**
     * Copies a document picked with the system file picker into the sandbox.
     *
     * @return the stored document, or null when the source cannot be read.
     */
    suspend fun importFromUri(context: Context, uri: Uri): Document? = withContext(Dispatchers.IO) {
        runCatching {
            val name = displayName(context, uri) ?: "document"
            val type = DocType.of(name)
            val id = UUID.randomUUID().toString()
            val target = File(sandboxDir, "${id}_$name")
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching null
            val now = System.currentTimeMillis()
            val doc = Document(
                id = id,
                name = name,
                type = type,
                path = target.absolutePath,
                sizeBytes = target.length(),
                createdAt = now,
                modifiedAt = now,
            )
            documents.add(0, doc)
            persist()
            doc
        }.getOrNull()
    }

    /** Registers an already existing file (used by the generators of the tools). */
    fun addExisting(name: String, type: DocType, source: File): Document {
        val id = UUID.randomUUID().toString()
        val target = File(sandboxDir, "${id}_$name")
        source.copyTo(target, overwrite = true)
        val now = System.currentTimeMillis()
        val doc = Document(
            id = id,
            name = name,
            type = type,
            path = target.absolutePath,
            sizeBytes = target.length(),
            createdAt = now,
            modifiedAt = now,
        )
        documents.add(0, doc)
        persist()
        return doc
    }

    // ---------------------------------------------------------------- shared storage

    /**
     * Brings the documents of the shared storage into the list.
     *
     * The files stay where the user put them, the app only remembers them, so
     * the preview and the tools work on the original file. Files that are gone
     * in the meantime leave the list again.
     *
     * @param context the context the permission is checked with
     * @return the number of files that were added to the list
     */
    fun syncSharedDocuments(context: Context): Int {
        val granted = SharedStorage.canRead(context)
        if (!granted) {
            Logger.i(TAG, "the shared storage is not accessible yet, the permission is missing")
            return 0
        }
        val now = System.currentTimeMillis()
        if (now - lastSharedSyncAt < SHARED_SYNC_THROTTLE_MS) return 0
        lastSharedSyncAt = now

        val found = SharedStorage.scan()
        Logger.i(TAG, "shared storage: ${found.size} files found, ${documents.size} documents known")
        val known = documents.associateBy { it.path }
        var added = 0
        var changed = false

        found.forEach { shared ->
            val path = shared.file.absolutePath
            val size = shared.file.length()
            val modified = shared.file.lastModified()
            val existing = known[path]
            if (existing == null) {
                documents.add(
                    Document(
                        id = UUID.randomUUID().toString(),
                        name = shared.file.name,
                        type = shared.type,
                        path = path,
                        sizeBytes = size,
                        createdAt = modified,
                        modifiedAt = modified,
                    ),
                )
                added++
                changed = true
            } else if (existing.sizeBytes != size || existing.modifiedAt != modified) {
                // The file changed outside of the app, the list follows it.
                val index = documents.indexOfFirst { it.id == existing.id }
                if (index >= 0) {
                    documents[index] = existing.copy(
                        sizeBytes = size,
                        modifiedAt = modified,
                    )
                    changed = true
                }
            }
        }

        // Files of the shared storage that are gone - or that the app does not
        // support anymore - must not stay in the list. The documents of the
        // sandbox are never touched by the scan.
        val paths = found.mapTo(mutableSetOf()) { it.file.absolutePath }
        val removed = documents.removeAll { doc ->
            !isInSandbox(doc.path) && doc.path !in paths
        }
        if (removed) changed = true

        if (changed) persist()
        Logger.i(TAG, "shared storage: $added files added, ${documents.size} documents known")
        return added
    }

    /** Whether a path points into the private sandbox of the app. */
    private fun isInSandbox(path: String): Boolean =
        path.startsWith(sandboxDir.absolutePath + File.separator) ||
            path == sandboxDir.absolutePath

    fun update(doc: Document) {
        val index = documents.indexOfFirst { it.id == doc.id }
        if (index >= 0) {
            documents[index] = doc
            persist()
        }
    }

    fun rename(doc: Document, newName: String) {
        val source = File(doc.path)
        // A document of the shared storage keeps its place, only the sandbox
        // stores its files with the id of the document.
        val target = if (isInSandbox(doc.path)) {
            File(sandboxDir, "${doc.id}_$newName")
        } else {
            File(source.parentFile ?: sandboxDir, newName)
        }
        if (source.exists() && source.absolutePath != target.absolutePath) {
            // The record has to follow the file, otherwise the document would
            // point at a path that no longer exists.
            if (!source.renameTo(target)) {
                source.copyTo(target, overwrite = true)
                source.delete()
            }
        }
        update(
            doc.copy(
                name = newName,
                path = target.absolutePath,
                sizeBytes = target.length().takeIf { it > 0L } ?: doc.sizeBytes,
                modifiedAt = System.currentTimeMillis(),
            ),
        )
    }

    fun delete(doc: Document) {
        File(doc.path).delete()
        documents.removeAll { it.id == doc.id }
        annotations.remove(doc.id)
        bookmarks.remove(doc.id)
        persist()
    }

    fun toggleFavorite(doc: Document) = update(doc.copy(isFavorite = !doc.isFavorite))

    fun setProgress(doc: Document, page: Int) {
        update(
            doc.copy(
                lastPage = page,
                lastReadAt = System.currentTimeMillis(),
                pageCount = if (doc.pageCount > 0) doc.pageCount else page,
            ),
        )
    }

    fun markRead(doc: Document) = update(doc.copy(lastReadAt = System.currentTimeMillis()))

    // ---------------------------------------------------------------- querying

    /** Filters and sorts the document list the way the file screen needs it. */
    fun query(filter: DocType?, sort: FileSort, keyword: String?): List<Document> {
        val result = documents
            .filter { filter == null || it.type == filter }
            .filter { doc ->
                keyword.isNullOrBlank() ||
                    doc.name.contains(keyword.trim(), ignoreCase = true)
            }
        return when (sort) {
            FileSort.NAME -> result.sortedBy { it.name.lowercase() }
            FileSort.MODIFIED -> result.sortedByDescending { it.modifiedAt }
            FileSort.CREATED -> result.sortedByDescending { it.createdAt }
            FileSort.SIZE -> result.sortedByDescending { it.sizeBytes }
        }
    }

    /** Bytes currently used by the sandbox. */
    fun storageUsed(): Long = documents.sumOf { it.sizeBytes }

    // ---------------------------------------------------------------- annotations

    fun annotationsOf(docId: String): List<Annotation> =
        annotations.getOrPut(docId) { mutableListOf() }.toList()

    fun addAnnotation(annotation: Annotation) {
        annotations.getOrPut(annotation.docId) { mutableListOf() }.add(annotation)
        persist()
    }

    fun removeAnnotation(docId: String, annotationId: String) {
        annotations[docId]?.removeAll { it.id == annotationId }
        persist()
    }

    // ---------------------------------------------------------------- bookmarks

    fun bookmarksOf(docId: String): List<Bookmark> =
        bookmarks.getOrPut(docId) { mutableListOf() }.toList()

    fun addBookmark(docId: String, page: Int, title: String): Bookmark {
        val bookmark = Bookmark(UUID.randomUUID().toString(), docId, page, title)
        bookmarks.getOrPut(docId) { mutableListOf() }.add(bookmark)
        persist()
        return bookmark
    }

    fun removeBookmark(docId: String, bookmarkId: String) {
        bookmarks[docId]?.removeAll { it.id == bookmarkId }
        persist()
    }

    // ---------------------------------------------------------------- persistence

    fun persist() {
        val root = JSONObject()
        val docs = JSONArray()
        documents.forEach { doc ->
            docs.put(
                JSONObject()
                    .put("id", doc.id)
                    .put("name", doc.name)
                    .put("type", doc.type.name)
                    .put("path", doc.path)
                    .put("size", doc.sizeBytes)
                    .put("created", doc.createdAt)
                    .put("modified", doc.modifiedAt)
                    .put("favorite", doc.isFavorite)
                    .put("encrypted", doc.isEncrypted)
                    .put("pages", doc.pageCount)
                    .put("lastPage", doc.lastPage)
                    .put("lastRead", doc.lastReadAt),
            )
        }
        root.put("documents", docs)

        val ann = JSONArray()
        annotations.forEach { (docId, list) ->
            list.forEach { a ->
                ann.put(
                    JSONObject()
                        .put("docId", docId)
                        .put("id", a.id)
                        .put("page", a.page)
                        .put("type", a.type.name)
                        .put("color", a.color)
                        .put("text", a.text)
                        .put("created", a.createdAt),
                )
            }
        }
        root.put("annotations", ann)

        val marks = JSONArray()
        bookmarks.forEach { (docId, list) ->
            list.forEach { b ->
                marks.put(
                    JSONObject()
                        .put("docId", docId)
                        .put("id", b.id)
                        .put("page", b.page)
                        .put("title", b.title)
                        .put("created", b.createdAt),
                )
            }
        }
        root.put("bookmarks", marks)

        runCatching { stateFile.writeText(root.toString()) }
    }

    private fun loadState() {
        if (!stateFile.exists()) return
        runCatching {
            val root = JSONObject(stateFile.readText())
            val docs = root.optJSONArray("documents") ?: JSONArray()
            for (i in 0 until docs.length()) {
                val o = docs.getJSONObject(i)
                documents.add(
                    Document(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        type = DocType.valueOf(o.getString("type")),
                        path = o.getString("path"),
                        sizeBytes = o.optLong("size"),
                        createdAt = o.optLong("created"),
                        modifiedAt = o.optLong("modified"),
                        isFavorite = o.optBoolean("favorite"),
                        isEncrypted = o.optBoolean("encrypted"),
                        pageCount = o.optInt("pages"),
                        lastPage = o.optInt("lastPage"),
                        lastReadAt = o.optLong("lastRead"),
                    ),
                )
            }
            val ann = root.optJSONArray("annotations") ?: JSONArray()
            for (i in 0 until ann.length()) {
                val o = ann.getJSONObject(i)
                annotations.getOrPut(o.getString("docId")) { mutableListOf() }.add(
                    Annotation(
                        id = o.getString("id"),
                        docId = o.getString("docId"),
                        page = o.optInt("page"),
                        type = AnnotationType.valueOf(o.getString("type")),
                        color = o.optLong("color").toInt(),
                        text = o.optString("text"),
                        createdAt = o.optLong("created"),
                    ),
                )
            }
            val marks = root.optJSONArray("bookmarks") ?: JSONArray()
            for (i in 0 until marks.length()) {
                val o = marks.getJSONObject(i)
                bookmarks.getOrPut(o.getString("docId")) { mutableListOf() }.add(
                    Bookmark(
                        id = o.getString("id"),
                        docId = o.getString("docId"),
                        page = o.optInt("page"),
                        title = o.optString("title"),
                        createdAt = o.optLong("created"),
                    ),
                )
            }
        }
    }

    private fun displayName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                return cursor.getString(index)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    companion object {
        /** The tag of the log of the repository. */
        private const val TAG = "DocumentRepository"

        /**
         * The shortest time between two scans of the shared storage.
         *
         * The scan walks the folders of the device, so it does not run on
         * every reload of a list.
         */
        private const val SHARED_SYNC_THROTTLE_MS = 2_000L

        @Volatile
        private var instance: DocumentRepository? = null

        fun get(context: Context): DocumentRepository =
            instance ?: synchronized(this) {
                instance ?: DocumentRepository(context.applicationContext).also {
                    it.init(context.applicationContext)
                    instance = it
                }
            }
    }
}

/** Kind of a highlight stored on a page. */
enum class AnnotationType {
    HIGHLIGHT,
    UNDERLINE,
    STRIKETHROUGH,
    DRAW,
    NOTE,
    SIGNATURE,
}

/** A highlight / note / signature stored on a page. */
data class Annotation(
    val id: String,
    val docId: String,
    val page: Int,
    val type: AnnotationType,
    val color: Int,
    val text: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

/** A bookmark pointing at a page. */
data class Bookmark(
    val id: String,
    val docId: String,
    val page: Int,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
)
