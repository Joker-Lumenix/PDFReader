package com.peng.pdfreader.feature.lock.sheet

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import com.peng.pdfreader.R
import com.peng.pdfreader.appcore.pdf.PdfVault
import com.peng.pdfreader.appcore.widget.BaseBottomSheetDialog
import com.peng.pdfreader.databinding.LockSheetPasswordBinding

/**
 * The password sheet of the lock and the unlock flow.
 *
 * The sheet owns the states of the design: the neutral state, the error state
 * (wrong password) and the visible password state. It only reports the typed
 * password, the work itself happens in the host page. The sheet stays open
 * until the host confirms the operation, that way the error state of the
 * design can be rendered inside the sheet.
 */
class PasswordSheet : BaseBottomSheetDialog() {

    /** What the sheet is used for. */
    enum class Mode { LOCK, UNLOCK, ENTER }

    private var binding: LockSheetPasswordBinding? = null
    private var pendingError: String? = null
    private var passwordVisible = false

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
        applyFieldBackground()
        applyNormalColors()
        pendingError?.let { showError(it) }

        views.etPassword.transformationMethod = LockPasswordMask()
        views.ivToggle.setOnClickListener { toggleVisibility() }
        views.btnCancel.setOnClickListener { dismiss() }
        views.btnOk.setOnClickListener { submit() }

        return views.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(SCRIM_ALPHA)
        }
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
        views.tvTitle.setTextColor(color(R.color.brand_primary_light))
        views.tvSubtitle.setTextColor(color(R.color.text_accent_orange))
        views.etPassword.setTextColor(color(R.color.brand_primary_light))
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
            Mode.ENTER -> {
                views.tvTitle.setText(R.string.lock_enter_password)
                views.tvSubtitle.setText(R.string.lock_enter_password_hint)
            }
        }
    }

    /**
     * The field of the design is #f4f4f4 with a 12dp radius, the shared drawable
     * of :appcore rounds it with 16dp, so the sheet draws its own background.
     */
    private fun applyFieldBackground() {
        val views = binding ?: return
        views.etPassword.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(12).toFloat()
            setColor(color(R.color.surface_disabled_alt))
        }
    }

    private fun applyNormalColors() {
        val views = binding ?: return
        views.tvTitle.setTextColor(color(R.color.text_secondary))
        views.tvSubtitle.setTextColor(color(R.color.text_placeholder))
        views.etPassword.setTextColor(color(R.color.text_secondary))
        views.errorRow.visibility = View.INVISIBLE
    }

    /**
     * Switches between the hidden and the visible password.
     *
     * The design shows the closed eye while the password is hidden and the
     * open eye while it is shown.
     */
    private fun toggleVisibility() {
        val views = binding ?: return
        val field = views.etPassword
        passwordVisible = !passwordVisible
        field.inputType = if (passwordVisible) {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        } else {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        field.transformationMethod = if (passwordVisible) null else LockPasswordMask()
        field.setSelection(field.text?.length ?: 0)
        views.ivToggle.setImageResource(
            if (passwordVisible) R.mipmap.ic_eye_open else R.mipmap.ic_eye_close,
        )
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
        setBusy(true)
        parentFragmentManager.setFragmentResult(
            RESULT_KEY,
            bundleOf(KEY_PASSWORD to password, KEY_MODE to mode.name),
        )
    }

    private fun color(id: Int): Int = ContextCompat.getColor(requireContext(), id)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

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

        /** The scrim of the design is rgba(0,0,0,0.75). */
        private const val SCRIM_ALPHA = 0.75f

        /** Creates the sheet for the given [mode]. */
        fun newInstance(mode: Mode): PasswordSheet = PasswordSheet().apply {
            arguments = Bundle().apply { putString(ARG_MODE, mode.name) }
        }
    }
}
