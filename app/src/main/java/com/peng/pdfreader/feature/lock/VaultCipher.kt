package com.peng.pdfreader.feature.lock

import android.content.Context
import com.peng.pdfreader.R
import com.peng.pdfreader.core.pdf.PdfVault
import com.peng.pdfreader.data.DocType
import com.peng.pdfreader.data.Document
import com.peng.pdfreader.data.DocumentRepository
import java.io.File

/**
 * The cipher of the documents of the vault.
 *
 * The lock and the unlock page of the shell pick a file first and ask for the
 * password afterwards, the more sheet of a document knows its file already and
 * asks for the password right away. Both use this.
 *
 * Encrypting never touches the file of the document: the encrypted document is
 * a new file in the library. Removing the password works the other way round,
 * the encrypted file becomes the plain file again and keeps its place in the
 * library.
 */
object VaultCipher {

    /** The suffix of the name of an encrypted document. */
    private const val LOCKED_SUFFIX = "_locked"

    /**
     * Encrypts the document and stores the result in the library.
     *
     * The encrypted document is a new file in the library: the file of the
     * document stays untouched.
     *
     * @return the encrypted document, or a failure carrying a [PdfVault.VaultError].
     */
    fun lock(context: Context, document: Document, password: String): Result<Document> {
        val repository = DocumentRepository.get(context)
        val source = repository.fileOf(document)
        val target = File(context.cacheDir, "lock-${System.currentTimeMillis()}.tmp")
        return PdfVault.lock(source, target, password).map { file ->
            val stored = repository.addExisting(lockedName(document.name), DocType.PDF, file)
            val result = stored.copy(
                isEncrypted = true,
                pageCount = document.pageCount,
            )
            repository.update(result)
            file.delete()
            result
        }
    }

    /**
     * Decrypts the document and stores the result in the library.
     *
     * The encrypted file becomes the plain file again: it keeps its place in
     * the library and only the password is gone, so the row of the list shows
     * the file as a normal one again.
     *
     * @return the decrypted document, or a failure carrying a [PdfVault.VaultError].
     */
    fun unlock(context: Context, document: Document, password: String): Result<Document> {
        val repository = DocumentRepository.get(context)
        val source = repository.fileOf(document)
        val target = File(context.cacheDir, "unlock-${System.currentTimeMillis()}.tmp")
        return PdfVault.unlock(source, target, password).map { plain ->
            val result = if (isInSandbox(repository, document)) {
                // The plain file takes the place of the encrypted one.
                plain.copyTo(source, overwrite = true)
                val size = source.length()
                // The name of the file loses the suffix of the vault.
                repository.rename(document, unlockedName(document.name))
                val renamed = repository.byId(document.id) ?: document
                renamed.copy(
                    isEncrypted = false,
                    sizeBytes = size,
                    modifiedAt = System.currentTimeMillis(),
                ).also { repository.update(it) }
            } else {
                // A file outside of the sandbox is never touched, the plain
                // file becomes a new document of the library.
                val stored = repository.addExisting(unlockedName(document.name), DocType.PDF, plain)
                stored.copy(
                    isEncrypted = false,
                    pageCount = document.pageCount,
                ).also { repository.update(it) }
            }
            plain.delete()
            result
        }
    }

    /**
     * Whether the file of the document carries the vault container.
     */
    fun isEncrypted(context: Context, document: Document): Boolean =
        document.isEncrypted || PdfVault.isEncrypted(File(document.path))

    /**
     * The message of a failed operation.
     */
    fun errorMessage(context: Context, error: Throwable): String = when (error) {
        is PdfVault.VaultError.WrongPassword -> context.getString(R.string.lock_wrong_password)
        is PdfVault.VaultError.NotEncrypted -> context.getString(R.string.lock_not_encrypted)
        is PdfVault.VaultError.WeakPassword ->
            error.message ?: context.getString(R.string.lock_operation_failed)
        else -> context.getString(R.string.lock_operation_failed)
    }

    /** The name of an encrypted document. */
    fun lockedName(name: String): String {
        val base = name.substringBeforeLast('.', name)
        return if (base.endsWith(LOCKED_SUFFIX)) "$base.pdf" else "$base$LOCKED_SUFFIX.pdf"
    }

    /** The name of a decrypted document. */
    fun unlockedName(name: String): String {
        val base = name.substringBeforeLast('.', name)
        val clean = if (base.endsWith(LOCKED_SUFFIX)) base.dropLast(LOCKED_SUFFIX.length) else base
        return "$clean.pdf"
    }

    /** Whether the file of the document lives in the private sandbox. */
    private fun isInSandbox(repository: DocumentRepository, document: Document): Boolean =
        document.path.startsWith(repository.sandboxDir.absolutePath + File.separator)
}
