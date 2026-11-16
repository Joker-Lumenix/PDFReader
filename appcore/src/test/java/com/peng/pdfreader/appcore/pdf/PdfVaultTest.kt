package com.peng.pdfreader.appcore.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The vault container and the page range parser of the PDF engine.
 */
class PdfVaultTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `a locked file carries the vault magic`() {
        val source = folder.newFile("plain.pdf")
        source.writeBytes("not a real pdf".toByteArray())
        val locked = folder.newFile("locked.pdf")

        PdfVault.lock(source, locked, "secret12")

        assertTrue(PdfVault.isEncrypted(locked))
        assertFalse(PdfVault.isEncrypted(source))
    }

    @Test
    fun `the right password restores the content`() {
        val source = folder.newFile("plain.pdf")
        val payload = "the original bytes".toByteArray()
        source.writeBytes(payload)
        val locked = folder.newFile("locked.pdf")
        val unlocked = folder.newFile("unlocked.pdf")

        PdfVault.lock(source, locked, "secret12")
        PdfVault.unlock(locked, unlocked, "secret12")

        assertEquals(String(payload), unlocked.readText())
    }

    @Test
    fun `a wrong password is reported as such`() {
        val source = folder.newFile("plain.pdf")
        source.writeBytes("payload".toByteArray())
        val locked = folder.newFile("locked.pdf")
        val unlocked = folder.newFile("unlocked.pdf")
        PdfVault.lock(source, locked, "secret12")

        val result = PdfVault.unlock(locked, unlocked, "wrong-one")

        assertTrue("expected a failure, got $result", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("expected a wrong password, got $error", error is PdfVault.VaultError.WrongPassword)
    }

    @Test
    fun `a file that was never locked is not encrypted`() {
        val plain = folder.newFile("plain.pdf")
        plain.writeBytes("payload".toByteArray())
        val unlocked = folder.newFile("unlocked.pdf")

        val result = PdfVault.unlock(plain, unlocked, "secret12")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is PdfVault.VaultError.NotEncrypted)
    }

    @Test
    fun `a short password is refused`() {
        val source = folder.newFile("plain.pdf")
        source.writeBytes("payload".toByteArray())
        val locked = folder.newFile("locked.pdf")

        val result = PdfVault.lock(source, locked, "abc")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is PdfVault.VaultError.WeakPassword)
    }
}
