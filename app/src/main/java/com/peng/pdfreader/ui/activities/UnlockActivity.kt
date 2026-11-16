package com.peng.pdfreader.ui.activities

import android.content.Context
import android.content.Intent
import com.peng.pdfreader.R
import com.peng.pdfreader.base.BaseLockActivity
import com.peng.pdfreader.data.Document
import com.peng.pdfreader.ui.dialogs.BottomSheetPassword

/**
 * The unlock page: it lists the encrypted PDF files of the sandbox and removes
 * the password of the chosen one.
 */
class UnlockActivity : BaseLockActivity() {

    override val mode: BottomSheetPassword.Mode = BottomSheetPassword.Mode.UNLOCK

    override val titleRes: Int = R.string.lock_page_select

    override fun accepts(document: Document): Boolean = isVaultFile(document)

    companion object {
        /** Opens the unlock page. */
        fun start(context: Context) {
            context.startActivity(Intent(context, UnlockActivity::class.java))
        }
    }
}
