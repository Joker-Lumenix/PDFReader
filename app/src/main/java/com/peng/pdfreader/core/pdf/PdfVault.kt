package com.peng.pdfreader.core.pdf

import java.io.File
import java.security.SecureRandom
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Local document vault.
 *
 * A locked document is stored as a self contained container:
 *
 * ```
 * magic "PDRV1" | salt (16) | iv (12) | payload
 * ```
 *
 * The payload is the plain PDF encrypted with AES-256-GCM. The key is derived
 * from the password with PBKDF2-HMAC-SHA256, so the password itself is never
 * stored and never leaves the device.
 *
 * The container is not a PDF any more - that is on purpose: the framework
 * renderer cannot open an encrypted document, so the app keeps the cipher text
 * next to the library and decrypts it on demand.
 */
object PdfVault {

    private const val MAGIC = "PDRV1"
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12

    /** Shortest password the vault accepts. */
    const val MIN_PASSWORD_LENGTH = 4

        /** The typed password does not open the document. */
        const val CODE_WRONG_PASSWORD = "WRONG_PASSWORD"

        /** The document was not encrypted by this app. */
        const val CODE_NOT_ENCRYPTED = "NOT_ENCRYPTED"

        /** The password is shorter than [MIN_PASSWORD_LENGTH]. */
        const val CODE_WEAK_PASSWORD = "WEAK_PASSWORD"

        /** The file could not be read or written. */
        const val CODE_IO = "IO_ERROR"

    /**
     * Every way a vault operation can fail.
     *
     * The wrong password has its own case, so the UI can tell a typo apart
     * from a broken file.
     */
    sealed class VaultError(message: String, cause: Throwable? = null) : Exception(message, cause) {

        /** The password does not open the document. */
        class WrongPassword : VaultError(CODE_WRONG_PASSWORD)

        /** The file was not encrypted by this app. */
        class NotEncrypted : VaultError(CODE_NOT_ENCRYPTED)

        /** The password is too short. */
        class WeakPassword : VaultError(CODE_WEAK_PASSWORD)

        /** The file cannot be read or written. */
        class Io(val path: String, cause: Throwable?) : VaultError(CODE_IO, cause)
    }

    /**
     * True when the file carries the vault container.
     *
     * Only the magic is read, the file is not loaded into memory.
     */
    fun isEncrypted(file: File): Boolean = runCatching {
        if (!file.isFile) return false
        file.inputStream().use { input ->
            val header = ByteArray(MAGIC.length)
            if (input.read(header) != header.size) return false
            String(header, Charsets.ISO_8859_1) == MAGIC
        }
    }.getOrDefault(false)

    /**
     * Encrypts [source] into [out].
     *
     * @return the written file, or a failure carrying a [VaultError].
     */
    fun lock(source: File, out: File, password: String): Result<File> = runCatching {
        if (password.length < MIN_PASSWORD_LENGTH) throw VaultError.WeakPassword()
        if (!source.isFile) throw VaultError.Io(source.absolutePath, null)

        val random = SecureRandom()
        val salt = ByteArray(SALT_SIZE).also { random.nextBytes(it) }
        val iv = ByteArray(IV_SIZE).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
        val payload = cipher.doFinal(source.readBytes())

        out.parentFile?.let { if (!it.exists()) it.mkdirs() }
        out.outputStream().use { stream ->
            stream.write(MAGIC.toByteArray(Charsets.ISO_8859_1))
            stream.write(salt)
            stream.write(iv)
            stream.write(payload)
        }
        out
    }

    /**
     * Decrypts [source] into [out].
     *
     * A wrong password is reported as [VaultError.WrongPassword], every other
     * problem as a different [VaultError] case.
     */
    fun unlock(source: File, out: File, password: String): Result<File> = runCatching {
        if (!source.isFile) throw VaultError.Io(source.absolutePath, null)
        val bytes = source.readBytes()
        if (bytes.size < MAGIC.length + SALT_SIZE + IV_SIZE + 16) throw VaultError.NotEncrypted()
        val header = String(bytes, 0, MAGIC.length, Charsets.ISO_8859_1)
        if (header != MAGIC) throw VaultError.NotEncrypted()

        var offset = MAGIC.length
        val salt = bytes.copyOfRange(offset, offset + SALT_SIZE)
        offset += SALT_SIZE
        val iv = bytes.copyOfRange(offset, offset + IV_SIZE)
        offset += IV_SIZE
        val payload = bytes.copyOfRange(offset, bytes.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
        val plain = try {
            cipher.doFinal(payload)
        } catch (error: BadPaddingException) {
            // GCM only fails the tag check when the key does not match.
            throw VaultError.WrongPassword()
        }

        out.parentFile?.let { if (!it.exists()) it.mkdirs() }
        out.outputStream().use { it.write(plain) }
        out
    }

    /**
     * Strength of a password, 0 (empty) .. 3 (strong).
     *
     * Used by the password sheet to show a hint.
     */
    fun passwordStrength(password: String): Int {
        if (password.isEmpty()) return 0
        var score = 0
        if (password.length >= 6) score++
        if (password.length >= 10) score++
        if (password.any { it.isDigit() } && password.any { it.isLetter() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++
        return score.coerceIn(0, 3)
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }
}
