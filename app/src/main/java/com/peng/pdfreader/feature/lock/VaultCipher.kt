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
 * Every operation works on the file of the document: the encrypted file takes
 * the place of the plain one and the plain file takes the place of the
 * encrypted one, so the library keeps one row for the document.
 */
object VaultCipher {

    /** The suffix of the name of an encrypted document. */
    private const val LOCKED_SUFFIX = "_locked"

    /**
     * Encrypts the document.
     *
     * The encrypted file takes the place of the plain one - the operation
     * works on the file of the document, it does not create a copy of it.
     *
     * @return the encrypted document, or a failure carrying a [PdfVault.VaultError].
     */
    fun lock(context: Context, document: Document, password: String): Result<Document> {
        val repository = DocumentRepository.get(context)
        val source = repository.fileOf(document)
        val target = File(context.cacheDir, "lock-${System.currentTimeMillis()}.tmp")
        return PdfVault.lock(source, target, password).map { file ->
            // The encrypted file replaces the plain one.
            file.copyTo(source, overwrite = true)
            file.delete()
            val result = document.copy(
                isEncrypted = true,
                sizeBytes = source.length(),
                modifiedAt = System.currentTimeMillis(),
            )
            repository.update(result)
            result
        }
    }

    /**
     * Decrypts the document.
     *
     * The plain file takes the place of the encrypted one: the row of the
     * list shows the document as a normal one again and keeps its name.
     *
     * @return the decrypted document, or a failure carrying a [PdfVault.VaultError].
     */
    fun unlock(context: Context, document: Document, password: String): Result<Document> {
        val repository = DocumentRepository.get(context)
        val source = repository.fileOf(document)
        val target = File(context.cacheDir, "unlock-${System.currentTimeMillis()}.tmp")
        return PdfVault.unlock(source, target, password).map { plain ->
            // The plain file replaces the encrypted one.
            plain.copyTo(source, overwrite = true)
            plain.delete()
            val result = document.copy(
                isEncrypted = false,
                sizeBytes = source.length(),
                modifiedAt = System.currentTimeMillis(),
            )
            repository.update(result)
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
}
