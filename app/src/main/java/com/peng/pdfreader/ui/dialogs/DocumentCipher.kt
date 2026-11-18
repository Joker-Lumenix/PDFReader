package com.peng.pdfreader.ui.dialogs

import android.content.Context
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.peng.pdfreader.data.Document
import com.peng.pdfreader.data.DocumentRepository
import com.peng.pdfreader.feature.lock.VaultCipher
import com.peng.pdfreader.ui.uiData.FileActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The cipher of a document that is already known.
 *
 * The more sheet of a document knows its file: the password sheet asks for the
 * password and the file is encrypted or decrypted right away, no page in
 * between. The tools of the shell have no file, they ask for one first.
 *
 * The sheet stays open until the cipher is done, so the error of the design can
 * be rendered inside of it.
 */
class DocumentCipher(
    private val host: LifecycleOwner,
    private val manager: FragmentManager,
    private val context: Context,
) {

    /** The cipher runs in the fragment manager of the host. */
    constructor(host: Fragment) : this(host, host.childFragmentManager, host.requireContext())

    /** The document the cipher works on. */
    private var pending: Document? = null

    /**
     * Encrypts the document with the password the user types.
     */
    fun encrypt(documentId: String) = run(documentId, BottomSheetPassword.Mode.LOCK)

    /**
     * Removes the password of the document.
     */
    fun decrypt(documentId: String) = run(documentId, BottomSheetPassword.Mode.UNLOCK)

    private fun run(documentId: String, mode: BottomSheetPassword.Mode) {
        val document = DocumentRepository.get(context).byId(documentId) ?: return
        pending = document
        manager.setFragmentResultListener(
            BottomSheetPassword.RESULT_KEY,
            host,
        ) { _, bundle ->
            onPassword(bundle.getString(BottomSheetPassword.KEY_PASSWORD).orEmpty(), mode)
        }
        BottomSheetPassword.newInstance(mode)
            .show(manager, BottomSheetPassword.TAG)
    }

    private fun onPassword(password: String, mode: BottomSheetPassword.Mode) {
        val document = pending ?: return
        val context = context.applicationContext
        val sheet = currentSheet()
        host.lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                when (mode) {
                    BottomSheetPassword.Mode.LOCK -> VaultCipher.lock(context, document, password)
                    BottomSheetPassword.Mode.UNLOCK, BottomSheetPassword.Mode.ENTER ->
                        VaultCipher.unlock(context, document, password)
                }
            }
            outcome.fold(
                onSuccess = { result ->
                    sheet?.dismissAllowingStateLoss()
                    reportChanged(result)
                },
                onFailure = { error ->
                    // The sheet stays open, it shows the error of the design.
                    sheet?.setBusy(false)
                    sheet?.showError(VaultCipher.errorMessage(context, error))
                },
            )
        }
    }

    /**
     * The library changed, the list behind the sheet has to know it.
     */
    private fun reportChanged(document: Document) {
        manager.setFragmentResult(
            FileActions.REQUEST_KEY,
            bundleOf(
                FileActions.KEY_ACTION to FileActions.CHANGED,
                FileActions.KEY_DOC_ID to document.id,
            ),
        )
    }

    /** The sheet that is currently on screen, if any. */
    private fun currentSheet(): BottomSheetPassword? =
        manager.findFragmentByTag(BottomSheetPassword.TAG) as? BottomSheetPassword
}
