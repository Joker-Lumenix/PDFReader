package com.peng.pdfreader.feature.settings.permission

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.peng.pdfreader.R
import com.peng.pdfreader.appcore.util.Logger
import com.peng.pdfreader.base.BaseActivity
import com.peng.pdfreader.databinding.SettingsPermissionActivityBinding

/**
 * Asks the user for the "access all files" permission and opens the system
 * screen where it can be granted.
 */
class StoragePermissionActivity : BaseActivity() {

    private lateinit var binding: SettingsPermissionActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SettingsPermissionActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.llPermissionCard.setOnClickListener { openAllFilesSettings() }
        binding.btnAllow.setOnClickListener { openAllFilesSettings() }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionState()
    }

    /** The switch mirrors the state of the all files access permission. */
    private fun updatePermissionState() {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            // Below Android 11 the permission is granted at install time.
            true
        }

        binding.switchPermission.isChecked = granted
        binding.switchPermission.thumbTintList = ColorStateList.valueOf(
            ContextCompat.getColor(
                this,
                if (granted) R.color.brand_primary else R.color.surface_neutral_strong,
            ),
        )
        binding.switchPermission.trackTintList = ColorStateList.valueOf(
            ContextCompat.getColor(
                this,
                if (granted) R.color.brand_primary_container else R.color.surface_border_subtle,
            ),
        )
    }

    private fun openAllFilesSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:$packageName"),
            )
        } else {
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        }

        try {
            startActivity(intent)
        } catch (error: ActivityNotFoundException) {
            Logger.w(TAG, "all files access screen is not available")
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName"),
                ),
            )
        }
    }

    companion object {
        private const val TAG = "StoragePermission"
    }
}
