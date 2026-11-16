package com.peng.pdfreader.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.fragment.app.Fragment
import com.peng.pdfreader.R
import com.peng.pdfreader.base.BaseActivity
import com.peng.pdfreader.base.BaseBackHandler
import com.peng.pdfreader.data.DocType
import com.peng.pdfreader.data.Document
import com.peng.pdfreader.data.AppPrefs
import com.peng.pdfreader.data.DocumentRepository
import com.peng.pdfreader.databinding.ActivityPreviewBinding
import com.peng.pdfreader.ui.fragments.OfficePreviewFragment
import com.peng.pdfreader.ui.fragments.PdfPreviewFragment

/**
 * Preview host.
 *
 * One activity serves every format: PDF and images go to the PDF reader,
 * Word / Excel / PowerPoint go to the offline Office preview.
 */
class PreviewActivity : BaseActivity() {

    private lateinit var binding: ActivityPreviewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (AppPrefs.get(this).keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        val docId = intent.getStringExtra(EXTRA_DOC_ID)
        val document = docId?.let { DocumentRepository.get(this).byId(it) }
        if (document == null) {
            finish()
            return
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.previewContainer, createFragment(document))
                .commit()
        }
    }

    override fun handleBackPressed(): Boolean {
        val current = supportFragmentManager.findFragmentById(R.id.previewContainer)
        return (current as? BaseBackHandler)?.onBackHandled() ?: false
    }

    private fun createFragment(document: Document): Fragment = when (document.type) {
        DocType.PDF, DocType.IMAGE -> PdfPreviewFragment.newInstance(document.id)
        else -> OfficePreviewFragment.newInstance(document.id)
    }

    companion object {
        private const val EXTRA_DOC_ID = "com.peng.pdfreader.extra.DOC_ID"

        /** Opens the preview of the given document. */
        fun start(context: Context, docId: String) {
            context.startActivity(
                Intent(context, PreviewActivity::class.java).putExtra(EXTRA_DOC_ID, docId),
            )
        }
    }
}
