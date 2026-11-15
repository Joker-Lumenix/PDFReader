package com.peng.pdfreader.feature.settings.permission

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
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
 */
class SetDefaultReaderSheet : BaseBottomSheetDialog() {

    private var _binding: SettingsSetDefaultSheetBinding? = null
    private val binding get() = _binding!!

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = SettingsSetDefaultSheetBinding.inflate(inflater, container, false)
        applySurfaces()
        binding.btnOk.setOnClickListener {
            openDefaultAppSettings()
            dismiss()
        }
        return binding.root
    }

    /** The illustration card is cream, the mocked system dialog is white. */
    private fun applySurfaces() {
        val density = resources.displayMetrics.density
        binding.illustrationCard.background = GradientDrawable().apply {
            cornerRadius = 16f * density
            setColor(ContextCompat.getColor(requireContext(), R.color.accent_cream))
        }
        binding.mockCard.background = GradientDrawable().apply {
            cornerRadius = 14f * density
            setColor(Color.WHITE)
        }
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
