package com.peng.pdfreader.feature.lock.activity

import android.content.Context
import android.content.Intent
import com.peng.pdfreader.R
import com.peng.pdfreader.data.model.Document
import com.peng.pdfreader.feature.lock.sheet.PasswordSheet

/**
 * The lock page: it lists the PDF files of the sandbox and encrypts the
 * chosen one with a password.
 */
class LockActivity : BaseLockActivity() {

    override val mode: PasswordSheet.Mode = PasswordSheet.Mode.LOCK

    // The design shows a file picker, the title is "Select file" (design:
    // lock PDF).
    override val titleRes: Int = R.string.lock_page_select

    override fun accepts(document: Document): Boolean = !isVaultFile(document)

    companion object {
        /** Opens the lock page. */
        fun start(context: Context) {
            context.startActivity(Intent(context, LockActivity::class.java))
        }
    }
}
