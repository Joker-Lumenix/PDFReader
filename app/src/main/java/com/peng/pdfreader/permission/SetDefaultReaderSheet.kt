package com.peng.pdfreader.permission

import android.content.DialogInterface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.peng.pdfreader.R
import com.peng.pdfreader.core.util.Logger
import com.peng.pdfreader.base.BaseBottomSheetDialog
import com.peng.pdfreader.databinding.SettingsSetDefaultSheetBinding

/**
 * Asks the user to make the app the default reader of the documents.
 *
 * The wording does not say "set as default" - the user knows the word too
 * well. It asks a question and offers the way out. The sheet only shows up
 * after an effective read (see [DefaultAppPrompt]), the settings tab always
 * carries the entry for the users who want to do it by hand.
 */
class SetDefaultReaderSheet : BaseBottomSheetDialog(dismissOnClickOutside = false) {

    private var _binding: SettingsSetDefaultSheetBinding? = null
    private val binding get() = _binding!!

    /**
     * The kind of document the prompt is about, such as "PDF".
     *
     * It lives in the arguments of the sheet, so it survives a recreation of
     * the page.
     */
    var documentLabel: String
        get() = arguments?.getString(ARG_DOCUMENT_LABEL).orEmpty()
        set(value) {
            arguments = (arguments ?: Bundle()).apply { putString(ARG_DOCUMENT_LABEL, value) }
        }

    /** Raised once the sheet is gone. */
    var onDismissed: (() -> Unit)? = null

    /** Raised when the user asked for the default app settings of the system. */
    var onAction: (() -> Unit)? = null

    /** Raised when the user pushed the prompt away. */
    var onLater: (() -> Unit)? = null

    init {
        // The prompt stays on the screen until the user picks one of its two
        // buttons: the back button does not close it either.
        arguments = Bundle().apply {
            putBoolean(ARG_DISMISS_ON_BACK, false)
        }
    }

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = SettingsSetDefaultSheetBinding.inflate(inflater, container, false)
        applyIllustrationSurface()
        val appName = getString(R.string.app_name)
        binding.tvTitle.text = getString(R.string.default_prompt_title, documentLabel, appName)
        binding.tvSubtitle.text = getString(R.string.default_prompt_subtitle, documentLabel)
        binding.btnOk.setText(R.string.default_prompt_go)
        binding.btnOk.setOnClickListener {
            openDefaultAppSettings()
            onAction?.invoke()
            dismiss()
        }
        binding.btnLater.visibility = View.VISIBLE
        binding.btnLater.setOnClickListener {
            onLater?.invoke()
            dismiss()
        }
        return binding.root
    }

    /** The illustration of the design sits on a peach card with a 16dp radius. */
    private fun applyIllustrationSurface() {
        val density = resources.displayMetrics.density
        binding.ivIllustration.background = GradientDrawable().apply {
            cornerRadius = 16f * density
            setColor(ContextCompat.getColor(requireContext(), R.color.accent_cream))
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissed?.invoke()
    }

    private fun openDefaultAppSettings() {
        DefaultReader.openDefaultAppSettings(requireContext())
        Logger.d(TAG, "the default app settings are opened")
    }

    companion object {
        const val TAG = "SetDefaultReaderSheet"

        /** The key of the kind of document in the arguments of the sheet. */
        private const val ARG_DOCUMENT_LABEL = "com.peng.pdfreader.core.DOCUMENT_LABEL"
    }
}
