package com.peng.pdfreader.feature.settings.notification

import android.Manifest
import android.content.pm.PackageManager
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
 * The custom sheet that asks for the notification permission.
 *
 * It is the fallback of the start up flow: as soon as the system refuses to
 * show its own dialog - or on the versions that do not know the runtime
 * permission at all - this sheet takes over and guides the user to the
 * notification settings of the system.
 */
class NotificationPermissionSheet : BaseBottomSheetDialog() {

    private var _binding: SettingsNotificationSheetBinding? = null
    private val binding get() = _binding!!

    /** Raised once the sheet is gone, used by the start up flow to continue. */
    var onDismissed: (() -> Unit)? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Logger.i(TAG, "notification permission granted: $granted")
            dismiss()
        }

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = SettingsNotificationSheetBinding.inflate(inflater, container, false)

        binding.ivClose.setOnClickListener { dismiss() }
        binding.btnTurnOn.setOnClickListener { onTurnOnClicked() }
        return binding.root
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        onDismissed?.invoke()
    }

    /**
     * The button of the design.
     *
     * As long as the system still shows its own dialog the app asks for the
     * permission there, otherwise the notification settings of the system are
     * opened - that is the only way left to switch the notifications on.
     */
    private fun onTurnOnClicked() {
        val context = requireContext()
        if (NotificationPermissionHelper.requiresRuntimeRequest() &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED &&
            NotificationPermissionHelper.canAskSystem(
                requireActivity(),
                NotificationPermissionHelper.wasAsked(context),
            )
        ) {
            NotificationPermissionHelper.markAsked(context)
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        NotificationPermissionHelper.openNotificationSettings(context)
        dismiss()
    }

    companion object {
        const val TAG = "NotificationPermissionSheet"

        /** Shows the sheet. */
        fun show(manager: androidx.fragment.app.FragmentManager) {
            NotificationPermissionSheet().show(manager, TAG)
        }
    }
}
