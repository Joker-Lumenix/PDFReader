package com.peng.pdfreader.feature.lock.sheet

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import com.peng.pdfreader.R
import com.peng.pdfreader.appcore.pdf.PdfVault
import com.peng.pdfreader.appcore.widget.BaseBottomSheetDialog
import com.peng.pdfreader.databinding.LockSheetPasswordBinding

/**
 * The password sheet of the lock and the unlock flow.
 *
 * The sheet owns the three states of the design: the neutral state, the error
 * state (wrong password) and the busy state while the operation runs. It only
 * reports the typed password, the work itself happens in the host page.
 */
class PasswordSheet : BaseBottomSheetDialog() {

    /** What the sheet is used for. */
    enum class Mode { LOCK, UNLOCK }

    private var binding: LockSheetPasswordBinding? = null
    private var pendingError: String? = null

    /**
     * The message of the error state.
     *
     * It can be set before the sheet is shown, the error is then rendered as
     * soon as the sheet opens.
     */
    var showErrorOnShow: String?
        get() = pendingError
        set(value) {
            pendingError = value
            value?.let { showError(it) }
        }

    /** The mode the sheet was created for. */
    val mode: Mode
        get() = arguments?.getString(ARG_MODE)
            ?.let { name -> Mode.entries.firstOrNull { it.name == name } }
            ?: Mode.LOCK

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?): View {
        val views = LockSheetPasswordBinding.inflate(inflater, container, false)
        binding = views

        applyTexts()
        applyNormalColors()
        pendingError?.let { showError(it) }

        views.etPassword.doAfterTextChanged { text ->
            views.ivClear.visibility = if (text.isNullOrEmpty()) View.INVISIBLE else View.VISIBLE
        }
        views.ivClear.setOnClickListener { views.etPassword.setText("") }
        views.ivToggle.setOnClickListener { toggleVisibility() }
        views.btnCancel.setOnClickListener { dismiss() }
        views.btnOk.setOnClickListener { submit() }

        return views.root
    }

    override fun onStart() {
        super.onStart()
        binding?.etPassword?.requestFocus()
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    /**
     * Shows the error state of the design.
     *
     * The title turns red, the subtitle turns orange and the message appears
     * under the field.
     */
    fun showError(message: String) {
        pendingError = message
        val views = binding ?: return
        views.tvTitle.setTextColor(resources.getColor(R.color.brand_primary_light, null))
        views.tvSubtitle.setTextColor(resources.getColor(R.color.text_accent_orange, null))
        views.etPassword.setTextColor(resources.getColor(R.color.brand_primary_light, null))
        views.ivError.setImageDrawable(LockErrorIconDrawable())
        views.tvError.text = message
        views.errorRow.visibility = View.VISIBLE
    }

    /** Disables the confirm button while the operation runs. */
    fun setBusy(busy: Boolean) {
        val views = binding ?: return
        views.btnOk.isEnabled = !busy
        views.btnCancel.isEnabled = !busy
        views.btnOk.alpha = if (busy) 0.6f else 1f
        views.btnOk.text = getString(if (busy) R.string.lock_working else R.string.ok)
    }

    // ------------------------------------------------------------------ internals

    private fun applyTexts() {
        val views = binding ?: return
        when (mode) {
            Mode.LOCK -> {
                views.tvTitle.setText(R.string.lock_set_password)
                views.tvSubtitle.setText(R.string.lock_set_password_hint)
            }
            Mode.UNLOCK -> {
                views.tvTitle.setText(R.string.lock_remove_password)
                views.tvSubtitle.setText(R.string.lock_remove_password_hint)
            }
        }
    }

    private fun applyNormalColors() {
        val views = binding ?: return
        views.tvTitle.setTextColor(resources.getColor(R.color.text_secondary, null))
        views.tvSubtitle.setTextColor(resources.getColor(R.color.text_placeholder, null))
        views.etPassword.setTextColor(resources.getColor(R.color.text_secondary, null))
        views.errorRow.visibility = View.GONE
        views.ivClear.visibility = View.INVISIBLE
    }

    private fun toggleVisibility() {
        val views = binding ?: return
        val field = views.etPassword
        // TYPE_MASK_VARIATION_VISIBLE_PASSWORD is hidden in the SDK, the
        // public constant of the visible variation is used instead.
        val visible = field.inputType and InputType.TYPE_MASK_VARIATION ==
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        field.inputType = if (visible) {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        } else {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }
        views.ivToggle.setImageResource(
            if (visible) R.mipmap.ic_eye_open else R.mipmap.ic_eye_close,
        )
        field.setSelection(field.text?.length ?: 0)
    }

    private fun submit() {
        val views = binding ?: return
        val password = views.etPassword.text?.toString().orEmpty()
        if (password.isEmpty()) {
            showError(getString(R.string.lock_password_hint))
            return
        }
        if (mode == Mode.LOCK && password.length < PdfVault.MIN_PASSWORD_LENGTH) {
            showError(getString(R.string.lock_password_too_short))
            return
        }
        parentFragmentManager.setFragmentResult(
            RESULT_KEY,
            bundleOf(KEY_PASSWORD to password, KEY_MODE to mode.name),
        )
        dismiss()
    }

    companion object {
        /** Tag used when the sheet is shown. */
        const val TAG = "lock_password_sheet"

        /** Key of the fragment result that carries the password. */
        const val RESULT_KEY = "com.peng.pdfreader.feature.lock.password"

        /** Key of the password inside the result bundle. */
        const val KEY_PASSWORD = "password"

        /** Key of the mode inside the result bundle. */
        const val KEY_MODE = "mode"

        private const val ARG_MODE = "lock_mode"

        /** Creates the sheet for the given [mode]. */
        fun newInstance(mode: Mode): PasswordSheet = PasswordSheet().apply {
            arguments = Bundle().apply { putString(ARG_MODE, mode.name) }
        }
    }
}
