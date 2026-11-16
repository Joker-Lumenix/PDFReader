package com.peng.pdfreader.permission

import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.peng.pdfreader.R
import com.peng.pdfreader.appcore.util.Logger
import com.peng.pdfreader.appcore.widget.BaseBottomSheetDialog
import com.peng.pdfreader.databinding.SettingsSetDefaultSheetBinding

/**
 * Explains how to make the app the default reader of PDF files and opens the
 * system screen where the default app is picked.
 *
 * The illustration of the design is a picture, so the sheet only carries the
 * title, the subtitle and the button.
 */
class SetDefaultReaderSheet : BaseBottomSheetDialog() {

    private var _binding: SettingsSetDefaultSheetBinding? = null
    private val binding get() = _binding!!

    /** Raised once the sheet is gone, used by the start up flow to continue. */
    var onDismissed: (() -> Unit)? = null

    /** Raised when the user asked for the default app settings of the system. */
    var onAction: (() -> Unit)? = null

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = SettingsSetDefaultSheetBinding.inflate(inflater, container, false)
        applyIllustrationSurface()
        binding.tvTitle.setText(R.string.set_default_title)
        binding.tvSubtitle.setText(R.string.set_default_subtitle)
        binding.btnOk.setOnClickListener {
            openDefaultAppSettings()
            onAction?.invoke()
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
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        } catch (error: ActivityNotFoundException) {
            Logger.w(TAG, "default apps screen is not available")
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${requireContext().packageName}"),
                ),
            )
        }
    }

    companion object {
        const val TAG = "SetDefaultReaderSheet"
    }
}
