package com.peng.pdfreader.feature.lock.activity

import android.content.Context
import android.content.Intent
import com.peng.pdfreader.R
import com.peng.pdfreader.data.model.Document
import com.peng.pdfreader.feature.lock.sheet.PasswordSheet

/**
 * The unlock page: it lists the encrypted PDF files of the sandbox and removes
 * the password of the chosen one.
 */
class UnlockActivity : BaseLockActivity() {

    override val mode: PasswordSheet.Mode = PasswordSheet.Mode.UNLOCK

    override val titleRes: Int = R.string.lock_page_decrypt

    override fun accepts(document: Document): Boolean = isVaultFile(document)

    companion object {
        /** Opens the unlock page. */
        fun start(context: Context) {
            context.startActivity(Intent(context, UnlockActivity::class.java))
        }
    }
}
