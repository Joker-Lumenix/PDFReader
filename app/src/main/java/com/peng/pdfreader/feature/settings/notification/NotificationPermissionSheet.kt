package com.peng.pdfreader.feature.settings.notification

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.peng.pdfreader.R
import com.peng.pdfreader.appcore.util.Logger
import com.peng.pdfreader.appcore.widget.BaseBottomSheetDialog
import com.peng.pdfreader.databinding.SettingsNotificationSheetBinding

/**
 * Asks for the notification permission.
 *
 * The runtime permission only exists on Android 13 (API 33) and above; on
 * older versions the sheet simply closes again.
 */
class NotificationPermissionSheet : BaseBottomSheetDialog() {

    private var _binding: SettingsNotificationSheetBinding? = null
    private val binding get() = _binding!!

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Logger.i(TAG, "notification permission granted: $granted")
            dismiss()
        }

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = SettingsNotificationSheetBinding.inflate(inflater, container, false)

        binding.ivClose.setOnClickListener { dismiss() }
        binding.btnTurnOn.setOnClickListener { requestNotificationPermission() }
        return binding.root
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            dismiss()
            return
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            dismiss()
            return
        }
        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    companion object {
        const val TAG = "NotificationPermissionSheet"
    }
}
